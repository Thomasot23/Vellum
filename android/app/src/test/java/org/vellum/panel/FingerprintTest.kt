// SPDX-License-Identifier: AGPL-3.0-or-later
package org.vellum.panel

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.vellum.panel.data.PanelJson
import org.vellum.panel.data.Snapshot
import org.vellum.panel.data.Stage
import org.vellum.panel.enroll.Fingerprint
import org.vellum.panel.ui.EnrollmentScreen

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w1280dp-h800dp-land")
class FingerprintTest {
    @get:Rule val compose = createComposeRule()

    private val fixture =
        PanelJson.parseToJsonElement(javaClass.getResource("/key-fingerprint.json")!!.readText()).jsonObject
    private val publicKey = fixture["publicKey"]!!.jsonPrimitive.content
    private val expected = fixture["fingerprint"]!!.jsonPrimitive.content

    @Test fun matchesTheFixtureSharedWithTheServer() {
        assertEquals(expected, Fingerprint.of(publicKey))
        assertNull(Fingerprint.of("AAAA"))
        assertNull(Fingerprint.of("not base64!"))
    }

    @Test fun waitingScreenShowsTheFingerprintToCompare() {
        compose.setContent {
            EnrollmentScreen(Snapshot(Stage.PENDING, mac = "02AABBCCDDEE"), null, expected, {}, {})
        }
        compose.onNodeWithText(expected).assertIsDisplayed()
        compose.onNodeWithText("Device ID: 02AABBCCDDEE").assertIsDisplayed()
    }

    @Test fun activePanelsDoNotShowIt() {
        compose.setContent {
            EnrollmentScreen(Snapshot(Stage.UNASSIGNED, mac = "02AABBCCDDEE"), null, expected, {}, {})
        }
        compose.onNodeWithText("Device ID: 02AABBCCDDEE").assertIsDisplayed()
        assertEquals(0, compose.onAllNodesWithTextCount(expected))
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTextCount(text: String) =
        onAllNodes(
            androidx.compose.ui.test
                .hasText(text),
        ).fetchSemanticsNodes().size
}
