// SPDX-License-Identifier: AGPL-3.0-or-later
package org.vellum.panel

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.vellum.panel.data.RoomState

class ContractTest {
    @Test fun parsesSharedServerFixture() {
        val text = javaClass.getResource("/room-state.json")!!.readText()
        val state = Json.decodeFromString<RoomState>(text)
        assertEquals(1, state.version)
        assertEquals("Meeting Room", state.room.name)
        assertEquals("Planning", state.events.first().subject)
        assertFalse(state.state.isRoomFree)
    }
}
