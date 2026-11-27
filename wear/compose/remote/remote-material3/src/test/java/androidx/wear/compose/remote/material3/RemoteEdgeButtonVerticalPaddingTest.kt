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

/**
 * Checks that [RemoteEdgeButton] follows the layout rules of the Wear Material3 [EdgeButton]: the
 * button is [ButtonSize.specHeight] plus [SpecVerticalPadding] above and below, the container (the
 * clickable and drawn part) sits [SpecVerticalPadding] above the bottom edge of the screen, and, in
 * the embedded [RcPlayer][androidx.compose.remote.player.compose.embedded.RcPlayer], the padding
 * takes no clicks.
 *
 * Expected values come from Wear Material3, never from the code under test: either the spec values
 * in [WearMaterial3EdgeButtonSpec], or the Wear Material3 [EdgeButton] rendered in the same place
 * at the same size.
 */
/** The side of the square screen, the size of a large round watch (454 px at xhdpi). */
private const val ScreenSizeDp = 227

@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w${ScreenSizeDp}dp-h${ScreenSizeDp}dp-xhdpi")
class RemoteEdgeButtonVerticalPaddingTest(private val buttonSize: ButtonSize) {
    @get:Rule val playerRule = RcPlayerTestRule()

    @Test
    fun container_endsVerticalPaddingAboveBottomEdge() {
        // Given
        playerRule.setEdgeButtonContent()

        // When
        val container = playerRule.onNode(hasClickAction()).getBoundsInRoot()

        // Then
        assertDpEqual("Container bottom", container.bottom, ScreenSize - SpecVerticalPadding)
    }

    @Test
    fun container_isButtonSizeTall() {
        // Given
        playerRule.setEdgeButtonContent()

        // When
        val container = playerRule.onNode(hasClickAction()).getBoundsInRoot()

        // Then
        assertDpEqual("Container height", container.height, buttonSize.specHeight)
    }

    @Test
    fun containerBounds_matchWearMaterial3EdgeButton() {
        // Given
        val showWearMaterial3 = playerRule.setEdgeButtonContent()
        showWearMaterial3.value = true
        playerRule.waitForIdle()
        val expected = playerRule.onNode(hasClickAction()).getBoundsInRoot()

        // When
        showWearMaterial3.value = false
        playerRule.waitForIdle()
        val actual = playerRule.onNode(hasClickAction()).getBoundsInRoot()

        // Then
        assertDpRectEqual("Container bounds", actual, expected)
    }

    @Test
    fun containerPixels_matchWearMaterial3EdgeButton() {
        // Given: a column left of the centre, the centre and a column right of the centre.
        val columnFractions = listOf(0.3f, 0.5f, 0.7f)
        val showWearMaterial3 = playerRule.setEdgeButtonContent()
        showWearMaterial3.value = true
        playerRule.waitForIdle()
        val expected = columnFractions.map { playerRule.containerRows(it) }

        // When
        showWearMaterial3.value = false
        playerRule.waitForIdle()
        val actual = columnFractions.map { playerRule.containerRows(it) }

        // Then
        for ((index, fraction) in columnFractions.withIndex()) {
            assertRowsMatch("Container rows at x = $fraction", actual[index], expected[index])
        }
    }

    @Test
    fun tapOnVerticalPadding_doesNotClick() {
        // Given
        val clicks = mutableListOf<String>()
        playerRule.setEdgeButtonContent(onNamedAction = { name, _, _ -> clicks += name })
        val bottomPaddingCenter = ScreenSize - SpecVerticalPadding / 2
        val topPaddingCenter =
            ScreenSize - SpecVerticalPadding - buttonSize.specHeight - SpecVerticalPadding / 2

        // When
        playerRule.tapAtCenterX(bottomPaddingCenter)
        playerRule.tapAtCenterX(topPaddingCenter)

        // Then
        assertThat(clicks).isEmpty()
    }

    @Test
    fun tapOnContainer_clicks() {
        // Given
        val clicks = mutableListOf<String>()
        playerRule.setEdgeButtonContent(onNamedAction = { name, _, _ -> clicks += name })

        // When
        playerRule.tapAtCenterX(ScreenSize - SpecVerticalPadding - buttonSize.specHeight / 2)

        // Then
        assertThat(clicks).containsExactly(ClickActionName)
    }

    @Test
    fun callerHeight_includesVerticalPaddingLikeWearMaterial3() {
        // Given: a caller height such as one used to animate the button in.
        val callerHeight = 40.dp
        val showWearMaterial3 =
            playerRule.setEdgeButtonContent(
                remoteModifier = RemoteModifier.height(callerHeight.value.rdp),
                wearMaterial3Modifier = Modifier.height(callerHeight),
            )
        showWearMaterial3.value = true
        playerRule.waitForIdle()
        val expected = playerRule.onNode(hasClickAction()).getBoundsInRoot()

        // When
        showWearMaterial3.value = false
        playerRule.waitForIdle()
        val actual = playerRule.onNode(hasClickAction()).getBoundsInRoot()

        // Then
        assertDpRectEqual("Container bounds", actual, expected)
    }

    @Test
    fun callerHeightBelowTwiceVerticalPadding_leavesNoContainerLikeWearMaterial3() {
        // Given
        val callerHeight = SpecVerticalPadding * 2 - 2.dp
        val showWearMaterial3 =
            playerRule.setEdgeButtonContent(
                remoteModifier = RemoteModifier.height(callerHeight.value.rdp),
                wearMaterial3Modifier = Modifier.height(callerHeight),
            )
        showWearMaterial3.value = true
        playerRule.waitForIdle()
        val expected = playerRule.onNode(hasClickAction()).getBoundsInRoot()

        // When
        showWearMaterial3.value = false
        playerRule.waitForIdle()
        val actual = playerRule.onNode(hasClickAction()).getBoundsInRoot()

        // Then
        assertDpEqual("Wear Material3 container height", expected.height, 0.dp)
        assertDpEqual("Container height", actual.height, expected.height)
        assertThat(playerRule.containerRows()).isEmpty()
    }

    /**
     * A Wear Material3 [EdgeButtonSize] and the [RemoteEdgeButtonSize] that mirrors it.
     *
     * @property specHeight The container height of [wearMaterial3] in Wear Material3, from
     *   [WearMaterial3EdgeButtonSpec].
     */
    class ButtonSize(
        private val name: String,
        val remote: RemoteEdgeButtonSize,
        val wearMaterial3: EdgeButtonSize,
        val specHeight: Dp,
    ) {
        override fun toString(): String = name
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun data(): List<ButtonSize> =
            listOf(
                ButtonSize(
                    "ExtraSmall",
                    RemoteEdgeButtonSize.ExtraSmall,
                    EdgeButtonSize.ExtraSmall,
                    WearMaterial3EdgeButtonSpec.ExtraSmallHeight,
                ),
                ButtonSize(
                    "Small",
                    RemoteEdgeButtonSize.Small,
                    EdgeButtonSize.Small,
                    WearMaterial3EdgeButtonSpec.SmallHeight,
                ),
                ButtonSize(
                    "Medium",
                    RemoteEdgeButtonSize.Medium,
                    EdgeButtonSize.Medium,
                    WearMaterial3EdgeButtonSpec.MediumHeight,
                ),
                ButtonSize(
                    "Large",
                    RemoteEdgeButtonSize.Large,
                    EdgeButtonSize.Large,
                    WearMaterial3EdgeButtonSpec.LargeHeight,
                ),
            )

        private val SpecVerticalPadding = WearMaterial3EdgeButtonSpec.VerticalPadding

        private val ScreenSize = ScreenSizeDp.dp

        /**
         * Half a pixel at this test's xhdpi density (2 px per dp). Compose lays out in whole pixels
         * and the bounds are read back in dp as floats, so an edge that is not on a whole pixel can
         * be off by up to half a pixel. With the whole-dp sizes used here the measured difference
         * is 0. A missing or doubled [SpecVerticalPadding] is 3 dp, 12 times this tolerance.
         */
        private val PixelRoundingTolerance = 0.25.dp

        /**
         * One pixel row. The curved corners are anti-aliased, and the two buttons are drawn by
         * different renderers (Compose for Wear Material3, the remote player for the remote
         * button), so the first or last row of a column can fall on either side of the colour
         * threshold. The measured difference here is 0. A missing [SpecVerticalPadding] is 6 rows.
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
    private fun RcPlayerTestRule.containerRows(xFraction: Float = 0.5f): List<Int> {
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
    ) {
        assertWithMessage("$what of the $buttonSize button, dp")
            .that(actual.value)
            .isWithin(tolerance.value)
            .of(expected.value)
    }

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
}
