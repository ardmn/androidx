/*
 * Copyright 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
@file:Suppress("RestrictedApiAndroidX")

package androidx.wear.compose.remote.material3

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.remote.creation.compose.action.hostAction
import androidx.compose.remote.creation.compose.capture.RemoteCreationDisplayInfo
import androidx.compose.remote.creation.compose.layout.RemoteAlignment
import androidx.compose.remote.creation.compose.layout.RemoteBox
import androidx.compose.remote.creation.compose.modifier.RemoteModifier
import androidx.compose.remote.creation.compose.modifier.fillMaxSize
import androidx.compose.remote.creation.compose.modifier.height
import androidx.compose.remote.creation.compose.state.rc
import androidx.compose.remote.creation.compose.state.rdp
import androidx.compose.remote.creation.compose.state.rs
import androidx.compose.remote.player.compose.RcPlayerTestRule
import androidx.compose.remote.player.core.state.StateUpdater
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsEqualTo
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.test.core.app.ApplicationProvider
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.EdgeButtonSize
import androidx.wear.compose.remote.material3.util.TestProfiles
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import kotlin.math.abs
import kotlin.math.roundToInt
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config

/** The side of the square screen, the size of a large round watch (454 px at xhdpi). */
private const val ScreenSizeDp = 227

/**
 * Checks that [RemoteEdgeButton] follows the layout of the Wear Material3 [EdgeButton]: its
 * container (the clickable and drawn part) has the same bounds and pixels, the same gap to the
 * bottom edge of the screen, and, in the embedded
 * [RcPlayer][androidx.compose.remote.player.compose.embedded.RcPlayer], the gap takes no clicks.
 *
 * Expected values come from the Wear Material3 [EdgeButton] rendered in the same place at the same
 * size, never from the code under test.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w${ScreenSizeDp}dp-h${ScreenSizeDp}dp-xhdpi")
class RemoteEdgeButtonVerticalPaddingTest(private val buttonSize: ButtonSize) {
    @get:Rule val playerRule = RcPlayerTestRule()

    @Test
    fun bottomGap_matchesWearMaterial3EdgeButton() {
        val showWearMaterial3 = playerRule.setEdgeButtonContent()
        val expectedGap =
            ScreenSize - playerRule.wearMaterial3ContainerBounds(showWearMaterial3).bottom

        val actualGap = ScreenSize - playerRule.containerBounds().bottom

        assertWithMessage("Wear Material3 bottom gap").that(expectedGap).isGreaterThan(0.dp)
        assertDpEqual("Bottom gap", actualGap, expectedGap)
    }

    @Test
    fun containerBounds_matchWearMaterial3EdgeButton() {
        val showWearMaterial3 = playerRule.setEdgeButtonContent()
        val expected = playerRule.wearMaterial3ContainerBounds(showWearMaterial3)

        val actual = playerRule.containerBounds()

        assertDpRectEqual("Container bounds", actual, expected)
    }

    @Test
    fun containerPixels_matchWearMaterial3EdgeButton() {
        val columnFractions = listOf(0.3f, 0.5f, 0.7f)
        val showWearMaterial3 = playerRule.setEdgeButtonContent()
        showWearMaterial3.value = true
        playerRule.waitForIdle()
        val expected = columnFractions.map { playerRule.containerPixelRows(it) }

        showWearMaterial3.value = false
        playerRule.waitForIdle()
        val actual = columnFractions.map { playerRule.containerPixelRows(it) }

        for ((index, fraction) in columnFractions.withIndex()) {
            assertRowsMatch("Container rows at x = $fraction", actual[index], expected[index])
        }
    }

    @Test
    fun tapOnBottomGap_doesNotClick() {
        val clicks = mutableListOf<String>()
        val showWearMaterial3 =
            playerRule.setEdgeButtonContent(onNamedAction = { name, _, _ -> clicks += name })
        val wearMaterial3Bottom = playerRule.wearMaterial3ContainerBounds(showWearMaterial3).bottom

        playerRule.tapAtCenterX((wearMaterial3Bottom + ScreenSize) / 2)

        assertWithMessage("Wear Material3 bottom gap")
            .that(ScreenSize - wearMaterial3Bottom)
            .isGreaterThan(0.dp)
        assertThat(clicks).isEmpty()
    }

    @Test
    fun tapOnContainer_clicks() {
        val clicks = mutableListOf<String>()
        val showWearMaterial3 =
            playerRule.setEdgeButtonContent(onNamedAction = { name, _, _ -> clicks += name })
        val wearMaterial3Container = playerRule.wearMaterial3ContainerBounds(showWearMaterial3)

        playerRule.tapAtCenterX((wearMaterial3Container.top + wearMaterial3Container.bottom) / 2)

        assertThat(clicks).containsExactly(ClickActionName)
    }

    @Test
    fun callerHeight_includesVerticalPaddingLikeWearMaterial3() {
        // A height such as one used to animate the button in.
        val callerHeight = 40.dp
        val showWearMaterial3 =
            playerRule.setEdgeButtonContent(
                remoteModifier = RemoteModifier.height(callerHeight.value.rdp),
                wearMaterial3Modifier = Modifier.height(callerHeight),
            )
        val expected = playerRule.wearMaterial3ContainerBounds(showWearMaterial3)

        val actual = playerRule.containerBounds()

        assertDpRectEqual("Container bounds", actual, expected)
    }

    @Test
    fun callerHeightBelowTwiceVerticalPadding_leavesNoContainerLikeWearMaterial3() {
        val callerHeight = 4.dp
        val showWearMaterial3 =
            playerRule.setEdgeButtonContent(
                remoteModifier = RemoteModifier.height(callerHeight.value.rdp),
                wearMaterial3Modifier = Modifier.height(callerHeight),
            )
        val expected = playerRule.wearMaterial3ContainerBounds(showWearMaterial3)

        val actual = playerRule.containerBounds()

        assertDpEqual("Wear Material3 container height", expected.height, 0.dp)
        assertDpEqual("Container height", actual.height, expected.height)
        assertThat(playerRule.containerPixelRows()).isEmpty()
    }

    /** A Wear Material3 [EdgeButtonSize] and the [RemoteEdgeButtonSize] that mirrors it. */
    class ButtonSize(
        private val name: String,
        val remote: RemoteEdgeButtonSize,
        val wearMaterial3: EdgeButtonSize,
    ) {
        override fun toString(): String = name
    }

    private val context: Context = ApplicationProvider.getApplicationContext()

    /**
     * Shows a [RemoteEdgeButton] of [buttonSize], with a [ContainerColor] container, at the bottom
     * of a [ScreenSize] screen in
     * [RcPlayer][androidx.compose.remote.player.compose.embedded.RcPlayer]. Set the returned state
     * to `true` to show the Wear Material3 [EdgeButton] of the same size in its place, and to
     * `false` to show the remote button again.
     *
     * @param remoteModifier The modifier passed to [RemoteEdgeButton].
     * @param wearMaterial3Modifier The modifier passed to [EdgeButton], on top of its alignment.
     * @param onNamedAction Receives the click of the remote button, which runs a host action named
     *   [ClickActionName].
     */
    private fun RcPlayerTestRule.setEdgeButtonContent(
        remoteModifier: RemoteModifier = RemoteModifier,
        wearMaterial3Modifier: Modifier = Modifier,
        onNamedAction: (name: String, value: Any?, stateUpdater: StateUpdater) -> Unit =
            { _, _, _ ->
            },
    ): MutableState<Boolean> {
        val showWearMaterial3 = mutableStateOf(false)
        val density = context.resources.displayMetrics.density
        val screenPx = (ScreenSize.value * density).roundToInt()
        setRemoteContent(
            profile = TestProfiles.wearWidgetsWithCoreText,
            onNamedAction = onNamedAction,
            remoteCreationDisplayInfo =
                RemoteCreationDisplayInfo(
                    width = screenPx,
                    height = screenPx,
                    densityDpi = context.resources.displayMetrics.densityDpi,
                ),
            playComposableWrapper = { remoteContent ->
                Box(modifier = Modifier.size(ScreenSize).testTag(ScreenTag)) {
                    if (showWearMaterial3.value) {
                        EdgeButton(
                            onClick = {},
                            modifier = wearMaterial3Modifier.align(Alignment.BottomCenter),
                            buttonSize = buttonSize.wearMaterial3,
                            colors = ButtonDefaults.buttonColors(containerColor = ContainerColor),
                        ) {}
                    } else {
                        remoteContent()
                    }
                }
            },
        ) {
            RemoteMaterialTheme {
                RemoteBox(
                    modifier = RemoteModifier.fillMaxSize(),
                    contentAlignment = RemoteAlignment.BottomCenter,
                ) {
                    RemoteEdgeButton(
                        onClick = hostAction(ClickActionName.rs),
                        modifier = remoteModifier,
                        buttonSize = buttonSize.remote,
                        colors =
                            RemoteEdgeButtonDefaults.buttonColors(
                                containerColor = ContainerColor.rc
                            ),
                    ) {}
                }
            }
        }
        waitForIdle()
        return showWearMaterial3
    }

    /** The bounds of the container of the button shown, the node with a click action. */
    private fun RcPlayerTestRule.containerBounds(): DpRect =
        onNode(hasClickAction()).getBoundsInRoot()

    /**
     * Shows the Wear Material3 [EdgeButton] in place of the remote button, returns the bounds of
     * its container, and shows the remote button again.
     */
    private fun RcPlayerTestRule.wearMaterial3ContainerBounds(
        showWearMaterial3: MutableState<Boolean>
    ): DpRect {
        showWearMaterial3.value = true
        waitForIdle()
        val bounds = containerBounds()
        showWearMaterial3.value = false
        waitForIdle()
        return bounds
    }

    /** Taps the screen at its horizontal centre, [y] below its top. */
    private fun RcPlayerTestRule.tapAtCenterX(y: Dp) {
        onNodeWithTag(ScreenTag).performTouchInput {
            click(Offset(centerX, y.toPx()))
        }
        waitForIdle()
    }

    /**
     * The rows, from top to bottom, of the screen's pixel column at [xFraction] of its width that
     * show [ContainerColor], within [ColorChannelTolerance] on each channel.
     */
    private fun RcPlayerTestRule.containerPixelRows(xFraction: Float = 0.5f): List<Int> {
        val pixels = onNodeWithTag(ScreenTag).captureToImage().toPixelMap()
        val x = (pixels.width * xFraction).toInt()
        return (0 until pixels.height).filter { y ->
            val pixel = pixels[x, y]
            abs(pixel.red - ContainerColor.red) < ColorChannelTolerance &&
                abs(pixel.green - ContainerColor.green) < ColorChannelTolerance &&
                abs(pixel.blue - ContainerColor.blue) < ColorChannelTolerance
        }
    }

    private fun assertDpEqual(
        what: String,
        actual: Dp,
        expected: Dp,
        tolerance: Dp = PixelRoundingTolerance,
    ) = actual.assertIsEqualTo(expected, "$what of the $buttonSize button", tolerance)

    private fun assertDpRectEqual(
        what: String,
        actual: DpRect,
        expected: DpRect,
        tolerance: Dp = PixelRoundingTolerance,
    ) {
        assertDpEqual("$what top", actual.top, expected.top, tolerance)
        assertDpEqual("$what bottom", actual.bottom, expected.bottom, tolerance)
        assertDpEqual("$what left", actual.left, expected.left, tolerance)
        assertDpEqual("$what right", actual.right, expected.right, tolerance)
    }

    /**
     * Asserts that both row lists are non-empty and that their first and last rows are within
     * [toleranceRows] of each other.
     */
    private fun assertRowsMatch(
        what: String,
        actual: List<Int>,
        expected: List<Int>,
        toleranceRows: Int = AntiAliasedEdgeRows,
    ) {
        val message = "$what of the $buttonSize button"
        assertWithMessage("$message (Wear Material3)").that(expected).isNotEmpty()
        assertWithMessage(message).that(actual).isNotEmpty()
        assertWithMessage("$message, top row")
            .that(abs(actual.first() - expected.first()))
            .isAtMost(toleranceRows)
        assertWithMessage("$message, bottom row")
            .that(abs(actual.last() - expected.last()))
            .isAtMost(toleranceRows)
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun data(): List<ButtonSize> =
            listOf(
                ButtonSize(
                    name = "ExtraSmall",
                    remote = RemoteEdgeButtonSize.ExtraSmall,
                    wearMaterial3 = EdgeButtonSize.ExtraSmall,
                ),
                ButtonSize(
                    name = "Small",
                    remote = RemoteEdgeButtonSize.Small,
                    wearMaterial3 = EdgeButtonSize.Small,
                ),
                ButtonSize(
                    name = "Medium",
                    remote = RemoteEdgeButtonSize.Medium,
                    wearMaterial3 = EdgeButtonSize.Medium,
                ),
                ButtonSize(
                    name = "Large",
                    remote = RemoteEdgeButtonSize.Large,
                    wearMaterial3 = EdgeButtonSize.Large,
                ),
            )

        private val ScreenSize = ScreenSizeDp.dp

        /**
         * Half a pixel at this test's xhdpi density (2 px per dp). Compose lays out in whole pixels
         * and the bounds are read back in dp as floats, so an edge that is not on a whole pixel can
         * be off by up to half a pixel. With the whole-dp sizes used here the measured difference
         * is 0. A missing or doubled vertical padding moves an edge by 12 times this tolerance.
         */
        private val PixelRoundingTolerance = 0.25.dp

        /**
         * One pixel row. The curved corners are anti-aliased, and the two buttons are drawn by
         * different renderers (Compose for Wear Material3, the remote player for the remote
         * button), so the first or last row of a column can fall on either side of the colour
         * threshold. The measured difference here is 0. A missing vertical padding is 6 rows.
         */
        private const val AntiAliasedEdgeRows = 1

        private const val ScreenTag = "screen"
        private const val ClickActionName = "edgeButtonClick"
        private val ContainerColor = Color.Red

        /**
         * Half of a colour channel. Wear Material3 darkens the container edges with a radial
         * gradient and both renderers anti-alias the shape, so container pixels are not exactly
         * [ContainerColor]. [containerPixels_matchWearMaterial3EdgeButton] asserts that both
         * containers are found with it.
         */
        private const val ColorChannelTolerance = 0.5f
    }
}
