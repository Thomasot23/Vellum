// SPDX-License-Identifier: AGPL-3.0-or-later
package org.vellum.panel.data

import org.vellum.panel.enroll.atomicWrite
import java.io.File
import java.time.Instant

/**
 * The panel's own warnings and errors, for the console's diagnostics card.
 *
 * Redaction is by construction: an entry is a fixed code plus, at most, a short
 * detail restricted to identifier characters (an exception's class name or an
 * HTTP status). Tokens, keys, URLs, room names and booking content cannot be
 * expressed in it. Identical consecutive entries are folded into one with a
 * repeat count, so a panel that fails every minute does not flood the ring.
 */
class PanelLog(
    private val sequence: SequenceStore,
    private val now: () -> Instant = Instant::now,
    private val capacity: Int = 200,
) {
    private data class Entry(
        val at: Instant,
        val level: Char,
        val code: String,
        val detail: String?,
        var repeats: Int = 1,
    )

    private val entries = ArrayDeque<Entry>()
    private var inFlight: Batch? = null
    private var lastAttempt: Instant? = null

    data class Batch(
        val seq: Long,
        val lines: String,
        val size: Int,
    )

    @Synchronized
    fun warn(
        code: String,
        detail: String? = null,
    ) = record('W', code, detail)

    @Synchronized
    fun error(
        code: String,
        detail: String? = null,
    ) = record('E', code, detail)

    private fun record(
        level: Char,
        code: String,
        detail: String?,
    ) {
        require(CODE.matches(code)) { "Log codes are constants" }
        val clean = detail?.filter { it.isLetterOrDigit() || it in "_.:-" }?.take(64)?.ifEmpty { null }
        val last = entries.lastOrNull()
        if (last != null && last.level == level && last.code == code && last.detail == clean) {
            last.repeats++
            return
        }
        entries.addLast(Entry(now(), level, code, clean))
        while (entries.size > capacity) entries.removeFirst()
    }

    /**
     * The batch to upload now, or null. A batch keeps its sequence number until
     * the server acknowledges it; the number is reserved before the first attempt,
     * so a restart can never reuse it for different lines.
     */
    @Synchronized
    fun nextBatch(): Batch? {
        inFlight?.let { return it }
        if (entries.isEmpty()) return null
        val time = now()
        val last = lastAttempt
        if (last != null && time.toEpochMilli() - last.toEpochMilli() < INTERVAL_MS) return null
        val lines =
            entries.joinToString("\n") { entry ->
                buildString {
                    append(entry.at)
                        .append(' ')
                        .append(entry.level)
                        .append(' ')
                        .append(entry.code)
                    entry.detail?.let { append(' ').append(it) }
                    if (entry.repeats > 1) append(" x").append(entry.repeats)
                }
            }
        return Batch(sequence.reserve(), lines.takeLast(MAX_BYTES), entries.size).also { inFlight = it }
    }

    @Synchronized
    fun attempted(
        batch: Batch,
        acknowledged: Boolean,
    ) {
        lastAttempt = now()
        if (!acknowledged || inFlight != batch) return
        repeat(minOf(batch.size, entries.size)) { entries.removeFirst() }
        inFlight = null
    }

    companion object {
        const val INTERVAL_MS = 5 * 60_000L

        /** Matches the server's batch limit. Lines are ASCII, so characters are bytes. */
        const val MAX_BYTES = 32_768
        private val CODE = Regex("[A-Z][A-Z0-9_]{1,40}")
    }
}

interface SequenceStore {
    /** The next sequence number, already persisted. */
    fun reserve(): Long
}

class FileSequenceStore(
    private val file: File,
) : SequenceStore {
    @Synchronized
    override fun reserve(): Long {
        val current =
            try {
                file.readText().trim().toLong()
            } catch (_: Exception) {
                0L
            }
        val next = if (current in 0 until 2_000_000_000) current + 1 else 1
        atomicWrite(file, next.toString().toByteArray())
        return next
    }
}
