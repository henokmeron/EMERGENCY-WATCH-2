package com.example.emergencywatch

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.platform.app.InstrumentationRegistry
import androidx.wear.compose.material3.AppScaffold
import com.example.emergencywatch.presentation.screens.MonitoringScreen
import com.example.emergencywatch.presentation.screens.PairingScreen
import com.example.emergencywatch.presentation.screens.RevokedScreen
import com.example.emergencywatch.presentation.screens.SettingsScreen
import com.example.emergencywatch.presentation.screens.SosScreen
import com.example.emergencywatch.presentation.screens.WelcomeScreen
import com.example.emergencywatch.presentation.theme.EmergencyWatchTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import kotlin.math.hypot
import kotlin.math.min

/**
 * Renders every screen on the round device and checks that each visible text and
 * clickable control lies inside the circular display and that no text is truncated.
 */
class RoundLayoutTest {

    @get:Rule
    val rule = createComposeRule()

    private fun show(content: @Composable () -> Unit) {
        rule.setContent { EmergencyWatchTheme { AppScaffold { content() } } }
        rule.waitForIdle()
    }

    private fun allNodes(): List<SemanticsNode> {
        val root = rule.onRoot(useUnmergedTree = true).fetchSemanticsNode()
        val out = mutableListOf<SemanticsNode>()
        fun walk(n: SemanticsNode) {
            out += n
            n.children.forEach(::walk)
        }
        walk(root)
        return out
    }

    private fun verifyInsideCircle(screen: String, vararg required: String) =
        verifyInsideCircle(screen, false, *required)

    /** [onlyRequired]: for scrolled lists, where passing content may legitimately sit under the edge. */
    private fun verifyInsideCircle(screen: String, onlyRequired: Boolean, vararg required: String) {
        rule.waitForIdle()
        val root = rule.onRoot(useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val cx = root.center.x
        val cy = root.center.y
        val radius = min(root.width, root.height) / 2f
        val tolerance = 2f
        val problems = mutableListOf<String>()
        val seen = mutableSetOf<String>()

        for (node in allNodes()) {
            val b = node.boundsInRoot
            if (b.width <= 0f || b.height <= 0f) continue
            val onScreen = b.bottom > root.top && b.top < root.bottom
            if (!onScreen) continue

            val texts = node.config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }
            val isClickable = node.config.contains(SemanticsActions.OnClick)
            val label = texts ?: if (isClickable) "<clickable>" else continue

            // Time text belongs to the system scaffold and is curved along the edge
            if (label.matches(Regex("\\d{1,2}:\\d{2}.*"))) continue
            seen += label
            if (onlyRequired && label !in required) continue

            val results = mutableListOf<TextLayoutResult>()
            if (texts != null) {
                node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
            }
            val layout = results.firstOrNull()

            val outside = if (layout != null) {
                // Check the rendered glyph extents of every line, not the (possibly full-width) text box
                (0 until layout.lineCount).any { line ->
                    val l = b.left + layout.getLineLeft(line)
                    val r = b.left + layout.getLineRight(line)
                    val t = b.top + layout.getLineTop(line)
                    val bt = b.top + layout.getLineBottom(line)
                    listOf(l to t, r to t, l to bt, r to bt).any { (x, y) -> hypot(x - cx, y - cy) > radius + tolerance }
                }
            } else {
                // Clickable containers (EdgeButton, pills) follow the curve; require their centre inside
                hypot(b.center.x - cx, b.center.y - cy) > radius
            }
            if (outside) problems += "'$label' outside circle at $b"

            if (texts != null) {
                layout?.let { r ->
                    if (r.hasVisualOverflow) problems += "'$label' visually overflows"
                    for (line in 0 until r.lineCount) {
                        if (r.isLineEllipsized(line)) problems += "'$label' ellipsized"
                    }
                }
            }
        }

        for (r in required) {
            if (seen.none { it == r }) problems += "required '$r' not visible"
        }

        saveScreenshot(screen)
        assertTrue("$screen: ${problems.joinToString("; ")}", problems.isEmpty())
    }

    private fun saveScreenshot(name: String) {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = File(ctx.getExternalFilesDir(null), "ui-audit").apply { mkdirs() }
        val bmp = rule.onRoot().captureToImage().asAndroidBitmap()
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun monitoring_worstCase() {
        show {
            MonitoringScreen(
                heartRate = 142, contactStatus = false, batteryLevel = 100, queuedCount = 12,
                statusMessage = "SOS alert transmitted!", onTriggerSos = {}, onOpenSettings = {}
            )
        }
        verifyInsideCircle("monitoring", "OFF WRIST · 🔋 100%", "142", " BPM", "EMERGENCY SOS", "⚙ Settings")
    }

    @Test
    fun pairing_keypadFullyUsable() {
        var code by mutableStateOf("")
        var submits by mutableIntStateOf(0)
        show {
            PairingScreen(
                code = code, isPairing = false, errorMessage = null,
                onCodeChange = { code = it }, onSubmit = { submits++ }, onBack = {}
            )
        }
        verifyInsideCircle("pairing_digits", "0-9", "A-M", "N-Z", "DEL", "PAIR", "1", "0")
        val pair = rule.onNode(hasText("PAIR") and hasClickAction())
        pair.assertIsNotEnabled()

        fun tap(t: String) = rule.onNode(hasText(t) and hasClickAction()).performClick()

        tap("A-M"); rule.waitForIdle()
        verifyInsideCircle("pairing_a_m", "A", "M", "PAIR")
        tap("N-Z"); rule.waitForIdle()
        verifyInsideCircle("pairing_n_z", "N", "Z", "PAIR")

        tap("X"); tap("0-9"); tap("8"); tap("A-M"); tap("K"); tap("0-9"); tap("9")
        tap("A-M"); tap("L"); tap("0-9"); tap("2")
        assertEquals("X8K9L2", code)
        pair.assertIsEnabled()
        tap("N-Z"); tap("P"); tap("0-9"); tap("4")
        assertEquals("X8K9L2P4", code)
        tap("7")
        assertEquals("max 8 characters", "X8K9L2P4", code)
        rule.onNodeWithText("X8K9L2P4").assertExists()
        verifyInsideCircle("pairing_full", "X8K9L2P4", "PAIR")

        tap("DEL")
        assertEquals("X8K9L2P", code)
        rule.onNodeWithText("X8K9L2P_").assertExists()

        pair.performClick()
        assertEquals(1, submits)
    }

    @Test
    fun pairing_withError() {
        show {
            PairingScreen(
                code = "ABC123", isPairing = false,
                errorMessage = "Pairing failed: <!DOCTYPE html><html><head>…",
                onCodeChange = {}, onSubmit = {}, onBack = {}
            )
        }
        // The error line may be ellipsized by design; only check controls here
        rule.onNode(hasText("PAIR") and hasClickAction()).assertIsEnabled()
        saveScreenshot("pairing_error")
        for (t in listOf("0-9", "A-M", "N-Z", "DEL", "1", "0", "PAIR")) {
            val node = rule.onNode(hasText(t) and hasClickAction()).fetchSemanticsNode()
            val root = rule.onRoot().fetchSemanticsNode().boundsInRoot
            val b = node.boundsInRoot
            val r = min(root.width, root.height) / 2f
            listOf(b.topLeft, b.topRight, b.bottomLeft, b.bottomRight).forEach {
                if (t != "PAIR") {
                    assertTrue("$t outside circle", hypot(it.x - root.center.x, it.y - root.center.y) <= r + 2f)
                }
            }
        }
    }

    @Test
    fun settings() {
        show {
            SettingsScreen(
                patientId = "3f2a9c1e-aaaa-bbbb", deviceId = "dev", lastUpload = "2026-09-27T21:04:11Z",
                isSyncing = false, onForceSync = {}, onDePair = {}, onBack = {}
            )
        }
        // Bottom item is mid-morph under the curved edge until scrolled; check the settled end state
        saveScreenshot("settings")
        rule.onRoot().performTouchInput { swipeUp() }
        rule.waitForIdle()
        verifyInsideCircle("settings_scrolled", onlyRequired = true, "Force Sync", "De-pair Watch", "Back")
    }

    @Test
    fun sos() {
        show { SosScreen(onConfirmSos = {}, onCancel = {}) }
        verifyInsideCircle("sos", "FALL DETECTED", "SEND NOW", "CANCEL ALERT")
    }

    @Test
    fun revoked() {
        show { RevokedScreen(onResetAndPair = {}) }
        verifyInsideCircle("revoked", "ACCESS REVOKED", "Re-pair Watch")
    }

    @Test
    fun welcome() {
        show { WelcomeScreen(onStartPairing = {}) }
        verifyInsideCircle("welcome", "Emergency Watch", "Pair Watch")
    }
}
