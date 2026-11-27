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
package androidx.wear.compose.remote.material3

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.remote.creation.compose.action.hostAction
import androidx.compose.remote.creation.compose.capture.createRemoteCreationDisplayInfo
import androidx.compose.remote.creation.compose.layout.RemoteAlignment
import androidx.compose.remote.creation.compose.layout.RemoteBox
import androidx.compose.remote.creation.compose.modifier.RemoteModifier
import androidx.compose.remote.creation.compose.modifier.fillMaxSize
import androidx.compose.remote.creation.compose.modifier.height
import androidx.compose.remote.creation.compose.state.rc
import androidx.compose.remote.creation.compose.state.rdp
import androidx.compose.remote.creation.compose.state.rs
import androidx.compose.remote.player.compose.RcPlayerTestRule
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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

/**
 * The side of the square screen, the size of a large round watch (454 px at xhdpi). It is top level
 * so that the class's [Config] annotation can use it.
 */
private const val ScreenSizeDp = 227

/**
 * Checks that the container of [RemoteEdgeButton] (its clickable, drawn part) has the bounds,
 * pixels and bottom gap of the Wear M3 [EdgeButton] of the same size, the source of every expected
 * value, and that the gap takes no clicks.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w${ScreenSizeDp}dp-h${ScreenSizeDp}dp-xhdpi")
class RemoteEdgeButtonVerticalPaddingTest(private val buttonSize: ButtonSize) {
    @get:Rule val playerRule = RcPlayerTestRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun containerBounds_matchWearM3EdgeButton() {
        val showWearM3 = playerRule.setEdgeButtonContent()
        val expected = playerRule.wearM3ContainerBounds(showWearM3)
        playerRule.assertWearM3HasBottomGap(expected.bottom)

        val actual = playerRule.containerBounds()

        assertDpRectEqual("Container bounds", actual, expected)
    }

    @Test
    fun containerPixels_matchWearM3EdgeButton() {
        val columnFractions = listOf(0.3f, 0.5f, 0.7f)
        val showWearM3 = playerRule.setEdgeButtonContent()
        val expected =
            playerRule.measureWearM3(showWearM3) { playerRule.containerPixelRows(columnFractions) }

        val actual = playerRule.containerPixelRows(columnFractions)

        for ((index, fraction) in columnFractions.withIndex()) {
            assertRowsMatch("Container rows at x = $fraction", actual[index], expected[index])
        }
    }

    @Test
    fun tapOnBottomGap_doesNotClick() {
        val clicks = mutableListOf<String>()
        val showWearM3 =
            playerRule.setEdgeButtonContent(onHostActionName = { name -> clicks += name })
        val wearM3Bottom = playerRule.wearM3ContainerBounds(showWearM3).bottom
        playerRule.assertWearM3HasBottomGap(wearM3Bottom)
        val screenBottom = playerRule.screenBounds().bottom

        playerRule.tapAtCenterX((wearM3Bottom + screenBottom) / 2)

        assertThat(clicks).isEmpty()
    }

    @Test
    fun tapOnContainer_clicks() {
        val clicks = mutableListOf<String>()
        val showWearM3 =
            playerRule.setEdgeButtonContent(onHostActionName = { name -> clicks += name })
        val wearM3Container = playerRule.wearM3ContainerBounds(showWearM3)
        assertWithMessage("Wear M3 container height of the $buttonSize button")
            .that(wearM3Container.height)
            .isGreaterThan(0.dp)

        playerRule.tapAtCenterX((wearM3Container.top + wearM3Container.bottom) / 2)

        assertThat(clicks).containsExactly(ClickActionName)
    }

    @Test
    fun callerHeight_includesVerticalPaddingLikeWearM3() {
        // A height such as one used to animate the button in.
        val callerHeight = 40.dp
        val showWearM3 =
            playerRule.setEdgeButtonContent(
                remoteModifier = RemoteModifier.height(callerHeight.rdp),
                wearM3Modifier = Modifier.height(callerHeight),
            )
        val expected = playerRule.wearM3ContainerBounds(showWearM3)

        val actual = playerRule.containerBounds()

        assertDpRectEqual("Container bounds", actual, expected)
    }

    @Test
    fun callerHeightBelowTwiceVerticalPadding_leavesNoContainerLikeWearM3() {
        val callerHeight = 4.dp
        val showWearM3 =
            playerRule.setEdgeButtonContent(
                remoteModifier = RemoteModifier.height(callerHeight.rdp),
                wearM3Modifier = Modifier.height(callerHeight),
            )
        val expected = playerRule.wearM3ContainerBounds(showWearM3)
        assertDpEqual("Wear M3 container height", expected.height, 0.dp)

        val actual = playerRule.containerBounds()

        assertDpEqual("Container height", actual.height, expected.height)
        assertThat(playerRule.containerPixelRows(listOf(0.5f)).single()).isEmpty()
    }

    /** A Wear M3 [EdgeButtonSize] and the [RemoteEdgeButtonSize] that mirrors it. */
    class ButtonSize(
        private val name: String,
        val remote: RemoteEdgeButtonSize,
        val wearM3: EdgeButtonSize,
    ) {
        override fun toString(): String = name
    }

    /**
     * Shows a [RemoteEdgeButton] of [buttonSize] at the bottom of the screen and returns the state
     * that, set to `true`, shows the Wear M3 [EdgeButton] of the same size in its place, and set to
     * `false`, shows the remote button again.
     *
     * @param remoteModifier The modifier passed to [RemoteEdgeButton].
     * @param wearM3Modifier The modifier passed to [EdgeButton], on top of its alignment.
     * @param onHostActionName Receives the name of the host action that a click of the remote
     *   button runs, [ClickActionName].
     */
    private fun RcPlayerTestRule.setEdgeButtonContent(
        remoteModifier: RemoteModifier = RemoteModifier,
        wearM3Modifier: Modifier = Modifier,
        onHostActionName: (name: String) -> Unit = {},
    ): MutableState<Boolean> {
        val showWearM3 = mutableStateOf(false)
        val screenPx =
            (ScreenSizeDp * context.resources.displayMetrics.density).roundToInt().toFloat()
        setRemoteContent(
            profile = TestProfiles.wearWidgetsWithCoreText,
            onNamedAction = { name, _, _ -> onHostActionName(name) },
            remoteCreationDisplayInfo =
                createRemoteCreationDisplayInfo(context, Size(screenPx, screenPx)),
            playComposableWrapper = { remoteContent ->
                Box(modifier = Modifier.size(ScreenSizeDp.dp).testTag(ScreenTag)) {
                    if (showWearM3.value) {
                        EdgeButton(
                            onClick = {},
                            modifier = wearM3Modifier.align(Alignment.BottomCenter),
                            buttonSize = buttonSize.wearM3,
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
        return showWearM3
    }

    /** The bounds of the container of the button shown, the node with a click action. */
    private fun RcPlayerTestRule.containerBounds(): DpRect =
        onNode(hasClickAction()).getBoundsInRoot()

    /**
     * Shows the Wear M3 [EdgeButton] in place of the remote button with [showWearM3], returns what
     * [measure] returns, and shows the remote button again.
     */
    private fun <T> RcPlayerTestRule.measureWearM3(
        showWearM3: MutableState<Boolean>,
        measure: () -> T,
    ): T {
        showWearM3.value = true
        waitForIdle()
        return try {
            measure()
        } finally {
            showWearM3.value = false
            waitForIdle()
        }
    }

    /** The bounds of the container of the Wear M3 [EdgeButton], with [measureWearM3]. */
    private fun RcPlayerTestRule.wearM3ContainerBounds(showWearM3: MutableState<Boolean>): DpRect =
        measureWearM3(showWearM3) { containerBounds() }

    /** Asserts that the Wear M3 container ending at [wearM3Bottom] leaves a bottom gap. */
    private fun RcPlayerTestRule.assertWearM3HasBottomGap(wearM3Bottom: Dp) {
        assertWithMessage("Wear M3 bottom gap of the $buttonSize button")
            .that(screenBounds().bottom - wearM3Bottom)
            .isGreaterThan(0.dp)
    }

    /** The bounds of the [ScreenSizeDp] screen that shows both buttons. */
    private fun RcPlayerTestRule.screenBounds(): DpRect = onNodeWithTag(ScreenTag).getBoundsInRoot()

    /** Taps the screen at its horizontal centre, at [y] in root coordinates. */
    private fun RcPlayerTestRule.tapAtCenterX(y: Dp) {
        val screenTop = screenBounds().top
        onNodeWithTag(ScreenTag).performTouchInput {
            click(Offset(centerX, (y - screenTop).toPx()))
        }
        waitForIdle()
    }

    /**
     * For each of [xFractions], the rows, from top to bottom, of the screen's pixel column at that
     * fraction of its width that are [isContainerColor]. The screen is captured once for all
     * columns.
     */
    private fun RcPlayerTestRule.containerPixelRows(
        xFractions: List<Float>,
        tolerance: Float = ColorChannelTolerance,
    ): List<List<Int>> {
        val pixels = onNodeWithTag(ScreenTag).captureToImage().toPixelMap()
        return xFractions.map { xFraction ->
            val x = (pixels.width * xFraction).toInt()
            (0 until pixels.height).filter { y -> isContainerColor(pixels[x, y], tolerance) }
        }
    }

    /** Returns whether [color] is [ContainerColor], within [tolerance] on each RGB channel. */
    private fun isContainerColor(color: Color, tolerance: Float): Boolean =
        abs(color.red - ContainerColor.red) < tolerance &&
            abs(color.green - ContainerColor.green) < tolerance &&
            abs(color.blue - ContainerColor.blue) < tolerance

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
        assertWithMessage("$message (Wear M3)").that(expected).isNotEmpty()
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
                    wearM3 = EdgeButtonSize.ExtraSmall,
                ),
                ButtonSize(
                    name = "Small",
                    remote = RemoteEdgeButtonSize.Small,
                    wearM3 = EdgeButtonSize.Small,
                ),
                ButtonSize(
                    name = "Medium",
                    remote = RemoteEdgeButtonSize.Medium,
                    wearM3 = EdgeButtonSize.Medium,
                ),
                ButtonSize(
                    name = "Large",
                    remote = RemoteEdgeButtonSize.Large,
                    wearM3 = EdgeButtonSize.Large,
                ),
            )

        /**
         * Half a pixel at xhdpi: edges land on whole pixels, so this only absorbs the float
         * read-back in dp. A missing vertical padding moves an edge by 12 times this.
         */
        private val PixelRoundingTolerance = 0.25.dp

        /**
         * One row: two renderers anti-alias the corners, so an edge row can fall on either side of
         * the colour threshold. A missing vertical padding moves an edge by 6 rows.
         */
        private const val AntiAliasedEdgeRows = 1

        private const val ScreenTag = "screen"
        private const val ClickActionName = "edgeButtonClick"
        private val ContainerColor = Color.Red

        /** Half a colour channel: anti-aliased edge pixels are not exactly [ContainerColor]. */
        private const val ColorChannelTolerance = 0.5f
    }
}
