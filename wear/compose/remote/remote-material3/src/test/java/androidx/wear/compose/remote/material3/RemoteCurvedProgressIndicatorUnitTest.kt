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

import androidx.compose.remote.creation.compose.layout.RemoteComposable
import androidx.compose.remote.creation.compose.modifier.RemoteModifier
import androidx.compose.remote.creation.compose.modifier.fillMaxSize
import androidx.compose.remote.creation.compose.state.RemoteFloat
import androidx.compose.remote.creation.compose.state.rb
import androidx.compose.remote.creation.compose.state.rc
import androidx.compose.remote.creation.compose.state.rdp
import androidx.compose.remote.creation.compose.state.rf
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// mdpi, so that dp sizes are px sizes.
@Config(sdk = [35], qualifiers = "w1000dp-h500dp-mdpi")
@RunWith(RobolectricTestRunner::class)
class RemoteCurvedProgressIndicatorUnitTest {

    @get:Rule val painterRule = DocumentPainterRule(canvasSize = CanvasSizePx)

    @Test
    fun fullCircle_progressOne_closesRingWithoutPinchAndDrawsNoTrack() {
        for (case in
            listOf(
                FullCircleCase(startAngle = 270f),
                FullCircleCase(startAngle = 297f, gapAngleDegrees = 0f),
                FullCircleCase(startAngle = 0f, reverseDirection = true),
                FullCircleCase(startAngle = 135f, dotCollapsible = false),
                FullCircleCase(startAngle = 270f, sweepAngle = 362f),
            )) {
            val painter = painterRule.paintDocument {
                RemoteCurvedProgressIndicator(
                    progress = 1f.rf,
                    modifier = RemoteModifier.fillMaxSize(),
                    startAngle = case.startAngle.rf,
                    sweepAngle = case.sweepAngle.rf,
                    colors = testColors(),
                    strokeWidth = StrokeWidthDp.rdp,
                    padding = 0.rdp,
                    gapAngleDegrees = case.gapAngleDegrees.rf,
                    dotCollapsible = case.dotCollapsible.rb,
                    reverseDirection = case.reverseDirection.rb,
                )
            }

            assertWithMessage("$case").that(painter.countPixels(::isTrack)).isEqualTo(0)
            assertWithMessage("$case").that(painter.missingRingPixels(0f, 360f)).isEqualTo(0)
        }
    }

    @Test
    fun fullCircle_progressLeavingOne_reopensAndClosesRing() {
        val startAngle = 270f
        val painter = painterRule.paintDocument {
            RemoteCurvedProgressIndicator(
                progress = RemoteFloat.createNamedRemoteFloat(ProgressName, 1f),
                modifier = RemoteModifier.fillMaxSize(),
                startAngle = startAngle.rf,
                sweepAngle = 360f.rf,
                colors = testColors(),
                strokeWidth = StrokeWidthDp.rdp,
                padding = 0.rdp,
            )
        }
        val missingAroundStart = {
            painter.missingRingPixels(
                fromAngle = startAngle - 2f * ThicknessDegrees,
                toAngle = startAngle + 2f * ThicknessDegrees,
            )
        }

        val closed = missingAroundStart()
        painter.setFloat(ProgressName, 0.99f)
        val reopened = missingAroundStart()
        painter.setFloat(ProgressName, 1f)
        val closedAgain = missingAroundStart()

        assertThat(closed).isEqualTo(0)
        assertThat(reopened).isGreaterThan(0)
        assertThat(closedAgain).isEqualTo(0)
    }

    @Test
    fun progressOne_sweepChangingAcross360_closesAndOpensRing() {
        val startAngle = 270f
        val painter = painterRule.paintDocument {
            RemoteCurvedProgressIndicator(
                progress = 1f.rf,
                modifier = RemoteModifier.fillMaxSize(),
                startAngle = startAngle.rf,
                sweepAngle = RemoteFloat.createNamedRemoteFloat(SweepName, 360f),
                colors = testColors(),
                strokeWidth = StrokeWidthDp.rdp,
                padding = 0.rdp,
            )
        }

        val fullCircleMissing = painter.missingRingPixels(0f, 360f)
        painter.setFloat(SweepName, 300f)
        val openArcStartCap = isIndicator(painter.ringColorAt(startAngle + 1f))
        val openArcBeforeStart = isIndicator(painter.ringColorAt(startAngle - 2f))
        painter.setFloat(SweepName, 360f)
        val fullCircleAgainMissing = painter.missingRingPixels(0f, 360f)

        assertThat(fullCircleMissing).isEqualTo(0)
        assertThat(openArcStartCap).isTrue()
        assertThat(openArcBeforeStart).isFalse()
        assertThat(fullCircleAgainMissing).isEqualTo(0)
    }

    @Test
    fun introOutroFullCircle_fullProgress_closesRingWithoutPinch() {
        // Full progress is the end of a count up, and the start of a count down.
        for (countDown in listOf(false, true)) {
            val painter = painterRule.paintDocument {
                RemoteIntroOutroCurvedProgressIndicator(
                    progress = 1f.rf,
                    modifier = RemoteModifier.fillMaxSize(),
                    startAngle = 270f.rf,
                    sweepAngle = 360f.rf,
                    colors = testColors(),
                    strokeWidth = StrokeWidthDp.rdp,
                    padding = 0.rdp,
                    countDown = countDown.rb,
                )
            }
            painter.advanceTimeBy(SettledMillis)

            assertWithMessage("countDown $countDown")
                .that(painter.countPixels(::isTrack))
                .isEqualTo(0)
            assertWithMessage("countDown $countDown")
                .that(painter.missingRingPixels(0f, 360f))
                .isEqualTo(0)
        }
    }

    @Test
    fun introOutroFullCircle_countUpToOne_closesRingOnceSettled() {
        // With a gap of 0 there is no slide, so the ring closes only at progress 1 exactly. With a
        // gap of 0, these strokes and paddings used to make the eased progress settle just below
        // 1 in float arithmetic. The default gap case closes during the slide.
        for ((strokeDp, paddingDp, gapAngleDegrees) in
            listOf(Triple(13, 3, 0f), Triple(19, 11, 0f), Triple(26, 7, 0f), Triple(8, 0, 2f))) {
            val painter = painterRule.paintDocument {
                RemoteIntroOutroCurvedProgressIndicator(
                    progress = RemoteFloat.createNamedRemoteFloat(ProgressName, 0.5f),
                    modifier = RemoteModifier.fillMaxSize(),
                    startAngle = 270f.rf,
                    sweepAngle = 360f.rf,
                    colors = testColors(),
                    strokeWidth = strokeDp.rdp,
                    padding = paddingDp.rdp,
                    gapAngleDegrees = gapAngleDegrees.rf,
                )
            }
            painter.advanceTimeBy(SettledMillis)
            painter.setFloat(ProgressName, 1f)
            painter.advanceTimeBy(SettledMillis)

            val arcRadius = (CanvasSizePx - strokeDp) / 2f - paddingDp
            assertWithMessage("stroke $strokeDp dp, padding $paddingDp dp, gap $gapAngleDegrees")
                .that(painter.missingRingPixels(0f, 360f, arcRadius))
                .isEqualTo(0)
        }
    }

    @Test
    fun fullCircle_progressSlidingIntoItsEnd_closesPinchSmoothly() {
        // The indicator slides over the gap once the track has collapsed, at the end of the outro.
        val totalTravel = GapAngleDegrees + ThicknessDegrees
        val maxProgress = 1f - totalTravel / 360f
        val slideStartProgress = maxProgress + (1f - maxProgress) * (ThicknessDegrees / totalTravel)
        val startAngle = 270f

        val missingPixels =
            listOf(0f, 0.15f, 0.3f, 1f).map { slideFraction ->
                val progress = slideStartProgress + (1f - slideStartProgress) * slideFraction
                val painter = painterRule.paintDocument {
                    RemoteCurvedProgressIndicator(
                        progress = progress.rf,
                        modifier = RemoteModifier.fillMaxSize(),
                        startAngle = startAngle.rf,
                        sweepAngle = 360f.rf,
                        colors = testColors(),
                        strokeWidth = StrokeWidthDp.rdp,
                        padding = 0.rdp,
                        gapAngleDegrees = GapAngleDegrees.rf,
                    )
                }
                painter.missingRingPixels(
                    fromAngle = startAngle - 2f * ThicknessDegrees,
                    toAngle = startAngle + 2f * ThicknessDegrees,
                )
            }

        assertThat(missingPixels).isInOrder(Comparator.reverseOrder<Int>())
        assertThat(missingPixels[0]).isGreaterThan(missingPixels[2])
        assertThat(missingPixels[2]).isGreaterThan(0)
        assertThat(missingPixels.last()).isEqualTo(0)
    }

    @Test
    fun fullCircle_progressBeforeItsEnd_keepsStartCapAtStartAngle() {
        val painter = painterRule.paintDocument {
            RemoteCurvedProgressIndicator(
                progress = 0.5f.rf,
                modifier = RemoteModifier.fillMaxSize(),
                startAngle = 270f.rf,
                sweepAngle = 360f.rf,
                colors = testColors(),
                strokeWidth = StrokeWidthDp.rdp,
                padding = 0.rdp,
            )
        }

        // The round cap ends at the start angle, so the ring just before it is the track.
        assertThat(isIndicator(painter.ringColorAt(271f))).isTrue()
        assertThat(isIndicator(painter.ringColorAt(268f))).isFalse()
        assertThat(isTrack(painter.ringColorAt(268f - ThicknessDegrees))).isTrue()
    }

    @Test
    fun openArc_progressOne_keepsRoundCapsWithinSweep() {
        for (sweepAngle in listOf(90f, 300f)) {
            val startAngle = 135f
            val painter = painterRule.paintDocument {
                RemoteCurvedProgressIndicator(
                    progress = 1f.rf,
                    modifier = RemoteModifier.fillMaxSize(),
                    startAngle = startAngle.rf,
                    sweepAngle = sweepAngle.rf,
                    colors = testColors(),
                    strokeWidth = StrokeWidthDp.rdp,
                    padding = 0.rdp,
                )
            }

            val message = "sweep $sweepAngle"
            assertWithMessage(message).that(painter.countPixels(::isTrack)).isEqualTo(0)
            val capsCenterInset = ThicknessDegrees / 2f
            assertWithMessage(message)
                .that(
                    painter.missingRingPixels(
                        fromAngle = startAngle + capsCenterInset,
                        toAngle = startAngle + sweepAngle - capsCenterInset,
                    )
                )
                .isEqualTo(0)
            // The round caps end at the ends of the sweep.
            for (inside in listOf(startAngle + 1f, startAngle + sweepAngle - 1f)) {
                assertWithMessage("$message, $inside")
                    .that(isIndicator(painter.ringColorAt(inside)))
                    .isTrue()
            }
            for (outside in listOf(startAngle - 2f, startAngle + sweepAngle + 2f)) {
                assertWithMessage("$message, $outside")
                    .that(isIndicator(painter.ringColorAt(outside)))
                    .isFalse()
            }
        }
    }

    /**
     * Returns how many points of the ring, sampled every [AngleStepDegrees] from [fromAngle] to
     * [toAngle] and every 0.5 px across the stroke of an arc of [arcRadius], are not drawn in the
     * indicator color.
     */
    private fun DocumentPainterRule.DocumentPainter.missingRingPixels(
        fromAngle: Float,
        toAngle: Float,
        arcRadius: Float = ArcRadiusPx,
    ): Int {
        val sampleCount = ((toAngle - fromAngle) / AngleStepDegrees).toInt()
        return (0..sampleCount).sumOf { sample ->
            val degrees = minOf(fromAngle + sample * AngleStepDegrees, toAngle)
            RadialOffsets.count { !isIndicator(ringColorAt(degrees, it, arcRadius)) }
        }
    }

    private fun DocumentPainterRule.DocumentPainter.ringColorAt(
        degrees: Float,
        radialOffset: Float = 0f,
        arcRadius: Float = ArcRadiusPx,
    ): Color {
        val radius = arcRadius + radialOffset
        val center = CanvasSizePx / 2f
        val rad = Math.toRadians(degrees.toDouble())
        // The pixel that contains the point.
        val x = floor(center + radius * cos(rad)).toInt()
        val y = floor(center + radius * sin(rad)).toInt()
        return colorAt(x, y)
    }

    @Composable
    @RemoteComposable
    private fun testColors(): RemoteProgressIndicatorColors =
        RemoteProgressIndicatorDefaults.colors(
            indicatorColor = Color.Red.rc,
            trackColor = Color.Blue.rc,
        )

    private fun isIndicator(color: Color): Boolean = color.red > 0.5f && color.blue < 0.5f

    private fun isTrack(color: Color): Boolean = color.blue > 0.5f && color.red < 0.5f

    private data class FullCircleCase(
        val startAngle: Float,
        val sweepAngle: Float = 360f,
        val gapAngleDegrees: Float = GapAngleDegrees,
        val dotCollapsible: Boolean = true,
        val reverseDirection: Boolean = false,
    )

    private companion object {
        const val CanvasSizePx = 200f
        const val StrokeWidthDp = 8
        const val StrokeWidthPx = 8f
        const val GapAngleDegrees = 2f
        const val ArcRadiusPx = (CanvasSizePx - StrokeWidthPx) / 2f
        const val ThicknessDegrees = StrokeWidthPx / ArcRadiusPx * (180f / PI.toFloat())
        const val SettledMillis = 3000L
        const val ProgressName = "progress"
        const val SweepName = "sweep"
        const val AngleStepDegrees = 0.1f

        /** Offsets from the middle of the stroke, inside its anti-aliased edges. */
        val RadialOffsets = (-5..5).map { it * 0.5f }
    }
}
