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
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.remote.core.CoreDocument
import androidx.compose.remote.creation.compose.action.RemoteAction
import androidx.compose.remote.creation.compose.action.hostAction
import androidx.compose.remote.creation.compose.capture.RemoteCreationDisplayInfo
import androidx.compose.remote.creation.compose.capture.heightDp
import androidx.compose.remote.creation.compose.capture.widthDp
import androidx.compose.remote.creation.compose.layout.RemoteAlignment
import androidx.compose.remote.creation.compose.layout.RemoteBox
import androidx.compose.remote.creation.compose.modifier.RemoteModifier
import androidx.compose.remote.creation.compose.modifier.fillMaxSize
import androidx.compose.remote.creation.compose.modifier.height
import androidx.compose.remote.creation.compose.state.rc
import androidx.compose.remote.creation.compose.state.rdp
import androidx.compose.remote.creation.compose.state.rs
import androidx.compose.remote.player.compose.RcPlayerTestRule
import androidx.compose.remote.player.compose.embedded.RcPlayer
import androidx.compose.remote.testing.RemoteCaptureTestRule
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Dp
import androidx.test.core.app.ApplicationProvider
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.EdgeButtonSize
import androidx.wear.compose.remote.material3.util.TestProfiles
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import kotlin.math.max
import kotlin.math.sqrt
import kotlin.test.assertNotNull
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w167dp-h167dp-xxhdpi")
class RemoteEdgeButtonUnitTest {
    @get:Rule val playerRule = RcPlayerTestRule()

    @get:Rule val captureRule = RemoteCaptureTestRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun calculateEdgeButtonContentWidth_matchesExpectedDpFormulaAcrossSizes() {
        val buttonWidthDp = 500f / 3f
        val sizes =
            listOf(
                RemoteEdgeButtonSize.ExtraSmall to 46f,
                RemoteEdgeButtonSize.Small to 56f,
                RemoteEdgeButtonSize.Medium to 70f,
                RemoteEdgeButtonSize.Large to 96f,
            )

        for ((size, heightDp) in sizes) {
            val actualDp =
                calculateEdgeButtonContentWidth(
                        actualButtonWidth = buttonWidthDp.rdp,
                        buttonHeight = size.maximumHeight,
                    )
                    .constantValue
            val expectedDp = expectedEdgeButtonContentWidthDp(buttonWidthDp, heightDp)
            assertWithMessage("Content width (dp) for height=$heightDp")
                .that(actualDp)
                .isWithin(0.01f)
                .of(expectedDp)
        }
    }

    @Test
    fun remoteEdgeButton_measuresNonZeroContentWidthAndTextAcrossSizes() = runTest {
        val density = 3f
        val widthPx = 500
        val heightPx = 260
        val displayInfo =
            RemoteCreationDisplayInfo(width = widthPx, height = heightPx, densityDpi = 480)

        val sizes =
            listOf(
                "ExtraSmall" to RemoteEdgeButtonSize.ExtraSmall,
                "Small" to RemoteEdgeButtonSize.Small,
                "Medium" to RemoteEdgeButtonSize.Medium,
                "Large" to RemoteEdgeButtonSize.Large,
            )

        val activeDocument = setPlayerContent(displayInfo)

        for ((name, buttonSize) in sizes) {
            val label = "Confirm $name action with long label"
            val document =
                captureRule.captureDocument(
                    context = context,
                    creationDisplayInfo = displayInfo,
                    profile = TestProfiles.wearWidgetsWithCoreText,
                ) {
                    RemoteMaterialTheme {
                        RemoteEdgeButton(
                            onClick = RemoteAction.None,
                            buttonSize = buttonSize,
                            colors = RemoteEdgeButtonDefaults.filledTonalButtonColors(),
                            border =
                                RemoteBorderStroke(
                                    width = 1.rdp,
                                    color = RemoteMaterialTheme.colorScheme.outline,
                                ),
                        ) {
                            RemoteText(label.rs)
                        }
                    }
                }

            activeDocument.value = document
            playerRule.waitForIdle()
            playerRule.onNodeWithText(label).assertExists()

            val hierarchy = document.displayHierarchy()
            val expectedMaxWidthPx =
                expectedEdgeButtonContentWidthDp(
                    widthPx / density,
                    buttonSize.maximumHeight.constantValue,
                ) * density

            val rowDims =
                Regex("""ROW\s+\[[^\]]+]\s*=\s*\[[^,]+,\s*[^,]+,\s*([\d.]+),\s*([\d.]+)]""")
                    .find(hierarchy)
            assertNotNull(rowDims, "Expected ROW bounds in hierarchy for $name:\n$hierarchy")
            val rowWidthPx = rowDims.groupValues[1].toFloat()
            assertWithMessage("ROW width px for $name")
                .that(rowWidthPx)
                .isWithin(1.5f)
                .of(expectedMaxWidthPx)

            val coreTextDims =
                Regex("""CORE_TEXT\s+\[[^\]]+]\s*=\s*\[[^,]+,\s*[^,]+,\s*([\d.]+),\s*([\d.]+)]""")
                    .find(hierarchy)
            assertNotNull(
                coreTextDims,
                "Expected CORE_TEXT bounds in hierarchy for $name:\n$hierarchy",
            )
            val textWidthPx = coreTextDims.groupValues[1].toFloat()
            val textHeightPx = coreTextDims.groupValues[2].toFloat()
            assertWithMessage("CORE_TEXT width px for $name")
                .that(textWidthPx)
                .isWithin(1.5f)
                .of(expectedMaxWidthPx)
            assertWithMessage("CORE_TEXT height px for $name").that(textHeightPx).isGreaterThan(10f)
            assertThat(textWidthPx).isAtMost(rowWidthPx + 0.5f)
        }
    }

    @Test
    fun remoteEdgeButton_leavesVerticalPaddingAroundContainerAcrossSizes() = runTest {
        val activeDocument = setPlayerContent(BottomAlignedDisplayInfo)

        for (buttonSize in AllSizes) {
            val heightDp = buttonSize.maximumHeight.constantValue
            val document = captureBottomAlignedButton(buttonSize)
            activeDocument.value = document
            playerRule.waitForIdle()

            // Like Wear Material3 EdgeButton, the button is VerticalPadding taller than its
            // container at the top and at the bottom, so the container doesn't touch the bottom
            // edge of the screen.
            assertButtonLayout(
                name = "$heightDp dp button",
                document = document,
                boundsHeightPx = (heightDp + 2 * VerticalPaddingDp) * BottomAlignedDensity,
            )
        }
    }

    @Test
    fun remoteEdgeButton_callerHeightIncludesVerticalPadding() = runTest {
        val activeDocument = setPlayerContent(BottomAlignedDisplayInfo)

        // As in Wear Material3, a height set by the caller (e.g. to animate the button in) is the
        // height of the whole button, so its container is 2 * VerticalPadding shorter.
        val callerHeightDp = 40f
        val document =
            captureBottomAlignedButton(
                RemoteEdgeButtonSize.Small,
                RemoteModifier.height(callerHeightDp.rdp),
            )
        activeDocument.value = document
        playerRule.waitForIdle()
        assertButtonLayout(
            name = "button with a $callerHeightDp dp caller height",
            document = document,
            boundsHeightPx = callerHeightDp * BottomAlignedDensity,
        )

        // A caller height below 2 * VerticalPadding leaves no room for the container.
        val tooShortHeightDp = 2 * VerticalPaddingDp - 2f
        val tooShortDocument =
            captureBottomAlignedButton(
                RemoteEdgeButtonSize.Small,
                RemoteModifier.height(tooShortHeightDp.rdp),
            )
        activeDocument.value = tooShortDocument
        playerRule.waitForIdle()
        assertButtonBounds(
            name = "button with a $tooShortHeightDp dp caller height",
            document = tooShortDocument,
            boundsHeightPx = tooShortHeightDp * BottomAlignedDensity,
        )
        assertThat(captureContainerRows()).isEmpty()
    }

    @Test
    fun remoteEdgeButton_clicksOnlyOnContainerNotOnVerticalPadding() = runTest {
        val clicks = mutableListOf<String>()
        val activeDocument =
            setPlayerContent(BottomAlignedDisplayInfo, onNamedAction = { clicks += it })
        activeDocument.value =
            captureBottomAlignedButton(
                RemoteEdgeButtonSize.Small,
                onClick = hostAction(ClickActionName.rs),
            )
        playerRule.waitForIdle()

        val heightPx = BottomAlignedDisplayInfo.height.toFloat()
        val paddingPx = VerticalPaddingDp * BottomAlignedDensity
        val containerHeightPx =
            RemoteEdgeButtonSize.Small.maximumHeight.constantValue * BottomAlignedDensity
        val centerX = BottomAlignedDisplayInfo.width / 2f
        fun clickAt(y: Float) {
            playerRule.onNodeWithTag(PlayerTag).performTouchInput { click(Offset(centerX, y)) }
            playerRule.waitForIdle()
        }

        // As in Wear Material3, taps on the padding above and below the container don't click.
        clickAt(heightPx - paddingPx / 2)
        clickAt(heightPx - containerHeightPx - paddingPx * 1.5f)
        assertThat(clicks).isEmpty()

        clickAt(heightPx - paddingPx - containerHeightPx / 2)
        assertThat(clicks).containsExactly(ClickActionName)
        playerRule
            .onNode(hasClickAction())
            .assertHeightIsEqualTo(Dp(RemoteEdgeButtonSize.Small.maximumHeight.constantValue))
    }

    @Test
    fun remoteEdgeButton_containerRowsMatchWearMaterial3EdgeButton() = runTest {
        val wearMaterial3Size = mutableStateOf<EdgeButtonSize?>(null)
        val activeDocument =
            setPlayerContent(BottomAlignedDisplayInfo) { content ->
                val size = wearMaterial3Size.value
                if (size != null) {
                    EdgeButton(
                        onClick = {},
                        modifier = Modifier.align(Alignment.BottomCenter),
                        buttonSize = size,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                    ) {}
                } else {
                    content()
                }
            }

        for ((remoteSize, wearSize) in
            AllSizes.zip(
                listOf(
                    EdgeButtonSize.ExtraSmall,
                    EdgeButtonSize.Small,
                    EdgeButtonSize.Medium,
                    EdgeButtonSize.Large,
                )
            )) {
            val name = "${remoteSize.maximumHeight.constantValue} dp button"
            wearMaterial3Size.value = wearSize
            playerRule.waitForIdle()
            val wearRows = ComparedColumns.map { captureContainerRows(it) }

            wearMaterial3Size.value = null
            activeDocument.value = captureBottomAlignedButton(remoteSize)
            playerRule.waitForIdle()
            val remoteRows = ComparedColumns.map { captureContainerRows(it) }

            for ((column, xFraction) in ComparedColumns.withIndex()) {
                val where = "$name at x = $xFraction"
                assertWithMessage("Wear Material3 container rows of a $where")
                    .that(wearRows[column])
                    .isNotEmpty()
                assertWithMessage("Container rows of a $where")
                    .that(remoteRows[column])
                    .isNotEmpty()
                assertWithMessage("Container top row of a $where")
                    .that(remoteRows[column].first())
                    .isIn(wearRows[column].first() - 1..wearRows[column].first() + 1)
                assertWithMessage("Container bottom row of a $where")
                    .that(remoteRows[column].last())
                    .isIn(wearRows[column].last() - 1..wearRows[column].last() + 1)
            }
        }
    }

    /**
     * Asserts that the button in [document] is [boundsHeightPx] tall at the bottom of the screen,
     * and that its container fills it except for VerticalPadding at the top and at the bottom.
     */
    private fun assertButtonLayout(name: String, document: CoreDocument, boundsHeightPx: Float) {
        assertButtonBounds(name, document, boundsHeightPx)

        val screenHeightPx = BottomAlignedDisplayInfo.height.toFloat()
        val paddingPx = VerticalPaddingDp * BottomAlignedDensity
        val containerRows = captureContainerRows()
        assertWithMessage("Container rows of a $name").that(containerRows).isNotEmpty()
        assertWithMessage("Container top row of a $name")
            .that(containerRows.first().toFloat())
            .isWithin(1f)
            .of(screenHeightPx - boundsHeightPx + paddingPx)
        assertWithMessage("Container bottom row of a $name")
            .that(containerRows.last().toFloat())
            .isWithin(1f)
            .of(screenHeightPx - paddingPx - 1)
    }

    /** Asserts that the button's layout bounds are [boundsHeightPx] tall at the screen bottom. */
    private fun assertButtonBounds(name: String, document: CoreDocument, boundsHeightPx: Float) {
        val (topPx, heightPx) = buttonBoundsPx(document)
        assertWithMessage("Height px of a $name").that(heightPx).isWithin(0.5f).of(boundsHeightPx)
        assertWithMessage("Bottom px of a $name")
            .that(topPx + heightPx)
            .isWithin(0.5f)
            .of(BottomAlignedDisplayInfo.height.toFloat())
    }

    /**
     * The top and height, in px, of the button's component in [document]'s layout: the closest BOX
     * above the button's merged semantics in [CoreDocument.displayHierarchy].
     */
    private fun buttonBoundsPx(document: CoreDocument): Pair<Float, Float> {
        val hierarchy = document.displayHierarchy().lines()
        val semanticsLine = hierarchy.indexOfFirst { it.contains(ButtonSemantics) }
        assertWithMessage("Button semantics in:\n${hierarchy.joinToString("\n")}")
            .that(semanticsLine)
            .isAtLeast(0)
        val bounds =
            hierarchy.subList(0, semanticsLine).asReversed().firstNotNullOf {
                BoxBoundsRegex.find(it)
            }
        val (top, height) = bounds.destructured
        return top.toFloat() to height.toFloat()
    }

    /**
     * Hosts the documents set on the returned state in an [RcPlayer] of [displayInfo]'s size. The
     * player is the content of [hostContent], which can show something else instead.
     */
    private fun setPlayerContent(
        displayInfo: RemoteCreationDisplayInfo,
        onNamedAction: (String) -> Unit = {},
        hostContent: @Composable BoxScope.(player: @Composable () -> Unit) -> Unit = { it() },
    ): MutableState<CoreDocument?> {
        val activeDocument = mutableStateOf<CoreDocument?>(null)
        playerRule.setContent {
            val currentDoc = activeDocument.value
            Box(
                modifier =
                    Modifier.size(displayInfo.widthDp, displayInfo.heightDp).testTag(PlayerTag)
            ) {
                hostContent {
                    if (currentDoc != null) {
                        key(currentDoc) {
                            RcPlayer(
                                document = currentDoc,
                                onNamedAction = { name, _, _ -> onNamedAction(name) },
                            )
                        }
                    }
                }
            }
        }
        return activeDocument
    }

    /** Captures a [RemoteEdgeButton] with a red container at the bottom of the screen. */
    private suspend fun captureBottomAlignedButton(
        buttonSize: RemoteEdgeButtonSize,
        modifier: RemoteModifier = RemoteModifier,
        onClick: RemoteAction = RemoteAction.None,
    ): CoreDocument =
        captureRule.captureDocument(
            context = context,
            creationDisplayInfo = BottomAlignedDisplayInfo,
            profile = TestProfiles.wearWidgetsWithCoreText,
        ) {
            RemoteMaterialTheme {
                RemoteBox(
                    modifier = RemoteModifier.fillMaxSize(),
                    contentAlignment = RemoteAlignment.BottomCenter,
                ) {
                    RemoteEdgeButton(
                        onClick = onClick,
                        modifier = modifier,
                        buttonSize = buttonSize,
                        colors =
                            RemoteEdgeButtonDefaults.buttonColors(containerColor = Color.Red.rc),
                    ) {}
                }
            }
        }

    /**
     * The rows of the player's column at [xFraction] of its width that show the red container, from
     * top to bottom.
     */
    private fun captureContainerRows(xFraction: Float = 0.5f): List<Int> {
        val pixels = playerRule.onNodeWithTag(PlayerTag).captureToImage().toPixelMap()
        val x = (pixels.width * xFraction).toInt()
        return (0 until pixels.height).filter { y ->
            pixels[x, y].let { it.red > 0.5f && it.blue < 0.5f }
        }
    }

    private fun expectedEdgeButtonContentWidthDp(widthDp: Float, heightDp: Float): Float {
        val extraSmallHeight = RemoteEdgeButtonSize.ExtraSmall.maximumHeight.constantValue
        val bottomPadding = VerticalPaddingDp
        val extraSmallEllipseHeight = 58f
        val buttonToEllipseRatio = 1.42f
        val targetSidePadding = 20f

        val finalFadeProgress = (1f - heightDp / extraSmallHeight).coerceIn(0f, 1f)
        val ellipseHeight =
            (extraSmallEllipseHeight + (heightDp - extraSmallHeight) * buttonToEllipseRatio) *
                (1f - finalFadeProgress) + heightDp * finalFadeProgress
        val screenRadius = widthDp / 2f
        val diff = max(screenRadius - bottomPadding - ellipseHeight / 2f, 0f)
        val localHalfWidth = sqrt(max(screenRadius * screenRadius - diff * diff, 0f))
        val r = heightDp - ellipseHeight / 2f
        return max(localHalfWidth - targetSidePadding - r, 0f) * 2f
    }

    private companion object {
        const val PlayerTag = "player"
        const val ClickActionName = "edgeButtonClick"
        const val ButtonSemantics = "SEMANTICS MERGE BUTTON"
        // Columns compared with Wear Material3: the left corner arc, the center and the right one.
        val ComparedColumns = listOf(0.3f, 0.5f, 0.7f)
        val AllSizes =
            listOf(
                RemoteEdgeButtonSize.ExtraSmall,
                RemoteEdgeButtonSize.Small,
                RemoteEdgeButtonSize.Medium,
                RemoteEdgeButtonSize.Large,
            )
        val BottomAlignedDisplayInfo =
            RemoteCreationDisplayInfo(width = 500, height = 360, densityDpi = 480)
        val BottomAlignedDensity = BottomAlignedDisplayInfo.density
        val VerticalPaddingDp = RemoteEdgeButtonDefaults.VerticalPadding.constantValue

        // The top and height of a BOX line in CoreDocument.displayHierarchy():
        // "BOX [id] = [x, y, width, height] VISIBLE".
        val BoxBoundsRegex =
            Regex("""BOX\s+\[[^\]]+]\s*=\s*\[[^,]+,\s*([\d.]+),\s*[^,]+,\s*([\d.]+)]""")
    }
}
