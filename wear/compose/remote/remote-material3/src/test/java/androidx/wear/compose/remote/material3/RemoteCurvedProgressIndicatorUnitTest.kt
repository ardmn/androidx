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

import androidx.compose.remote.creation.compose.modifier.RemoteModifier
import androidx.compose.remote.creation.compose.modifier.fillMaxSize
import androidx.compose.remote.creation.compose.state.RemoteFloat
import androidx.compose.remote.creation.compose.state.rb
import androidx.compose.remote.creation.compose.state.rdp
import androidx.compose.remote.creation.compose.state.rf
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Pixel tests of the full ring rule of the `sweepAngle` KDoc: with 360 degrees or more, the
 * indicator is a full ring at progress 1f, as RemoteCircularProgressIndicator and Wear Material3
 * CircularProgressIndicator close a full circle at progress 1f. Below 360 degrees, the indicator
 * and its round caps stay within the sweep; on a full circle, they start at `startAngle` until the
 * end of the outro closes the ring.
 */
@Config(sdk = [35], qualifiers = "w1000dp-h500dp-mdpi")
@RunWith(RobolectricTestRunner::class)
class RemoteCurvedProgressIndicatorUnitTest {

    @get:Rule val painterRule = DocumentPainterRule(canvasSize = CanvasSizePx)

    @Test
    fun fullCircle_progressOne_drawsWholeRingInIndicatorColor() {
        data class FullCircleCase(
            val startAngle: Float,
            val sweepAngle: Float = 360f,
            val gapAngleDegrees: Float = GapAngleDegrees,
            val dotCollapsible: Boolean = true,
            val reverseDirection: Boolean = false,
        )
        for (case in
            listOf(
                FullCircleCase(startAngle = 270f),
                FullCircleCase(startAngle = 297f, gapAngleDegrees = 0f),
                FullCircleCase(startAngle = 0f, reverseDirection = true),
                FullCircleCase(startAngle = 135f, dotCollapsible = false),
                FullCircleCase(startAngle = 270f, sweepAngle = 362f),
            )) {
            // Given a full circle at progress 1
            // When it is painted
            val painter = painterRule.paintDocument {
                RemoteCurvedProgressIndicator(
                    progress = 1f.rf,
                    modifier = RemoteModifier.fillMaxSize(),
                    startAngle = case.startAngle.rf,
                    sweepAngle = case.sweepAngle.rf,
                    colors = progressIndicatorTestColors(),
                    strokeWidth = StrokeWidthDp.rdp,
                    padding = 0.rdp,
                    gapAngleDegrees = case.gapAngleDegrees.rf,
                    dotCollapsible = case.dotCollapsible.rb,
                    reverseDirection = case.reverseDirection.rb,
                )
            }

            // Then the whole ring is the indicator, with no pinch and no track
            assertWithMessage("$case").that(painter.countPixels(::isTrackColor)).isEqualTo(0)
            assertWithMessage("$case")
                .that(painter.countRingSamplesNotIndicator(0f, 360f))
                .isEqualTo(0)
        }
    }

    @Test
    fun fullCircle_progressLeavingAndReturningToOne_opensAndClosesRingAtStartAngle() {
        // Given a full circle closed at progress 1
        val startAngle = 270f
        val painter = painterRule.paintDocument {
            RemoteCurvedProgressIndicator(
                progress = RemoteFloat.createNamedRemoteFloat(ProgressName, 1f),
                modifier = RemoteModifier.fillMaxSize(),
                startAngle = startAngle.rf,
                sweepAngle = 360f.rf,
                colors = progressIndicatorTestColors(),
                strokeWidth = StrokeWidthDp.rdp,
                padding = 0.rdp,
            )
        }
        val closed = painter.countRingSamplesNotIndicator(around = startAngle)

        // When progress leaves 1, then returns to it
        painter.setFloat(ProgressName, 0.99f)
        val reopened = painter.countRingSamplesNotIndicator(around = startAngle)
        painter.setFloat(ProgressName, 1f)
        val closedAgain = painter.countRingSamplesNotIndicator(around = startAngle)

        // Then the ring opens at the start angle, and closes again
        assertThat(closed).isEqualTo(0)
        assertThat(reopened).isGreaterThan(0)
        assertThat(closedAgain).isEqualTo(0)
    }

    @Test
    fun progressOne_sweepChangingAcross360_closesRingOnlyForFullCircle() {
        // Given an indicator at progress 1 with a sweep of 360
        val startAngle = 270f
        val painter = painterRule.paintDocument {
            RemoteCurvedProgressIndicator(
                progress = 1f.rf,
                modifier = RemoteModifier.fillMaxSize(),
                startAngle = startAngle.rf,
                sweepAngle = RemoteFloat.createNamedRemoteFloat(SweepName, 360f),
                colors = progressIndicatorTestColors(),
                strokeWidth = StrokeWidthDp.rdp,
                padding = 0.rdp,
            )
        }
        val fullCircleMissing = painter.countRingSamplesNotIndicator(0f, 360f)

        // When the sweep drops under 360, then returns to 360
        painter.setFloat(SweepName, 300f)
        val openArcInsideStart = painter.isIndicatorOnRing(startAngle + EdgeMarginDegrees)
        val openArcBeforeStart = painter.isIndicatorOnRing(startAngle - EdgeMarginDegrees)
        painter.setFloat(SweepName, 360f)
        val fullCircleAgainMissing = painter.countRingSamplesNotIndicator(0f, 360f)

        // Then the ring is closed at 360, and the open arc starts at the start angle
        assertThat(fullCircleMissing).isEqualTo(0)
        assertThat(openArcInsideStart).isTrue()
        assertThat(openArcBeforeStart).isFalse()
        assertThat(fullCircleAgainMissing).isEqualTo(0)
    }

    @Test
    fun introOutroFullCircle_countingUpToOne_closesRingOnceSettled() {
        // Stroke widths and paddings set the length of the intro and the outro, and so the eased
        // progress, which must still reach 1 once its animations settle.
        for ((strokeDp, paddingDp, gapAngleDegrees) in
            listOf(
                Triple(8, 0, GapAngleDegrees),
                Triple(13, 3, 0f),
                Triple(19, 11, 0f),
                Triple(26, 7, 0f),
            )) {
            // Given a full circle counting up from progress 0.5
            val painter = painterRule.paintDocument {
                RemoteIntroOutroCurvedProgressIndicator(
                    progress = RemoteFloat.createNamedRemoteFloat(ProgressName, 0.5f),
                    modifier = RemoteModifier.fillMaxSize(),
                    startAngle = 270f.rf,
                    sweepAngle = 360f.rf,
                    colors = progressIndicatorTestColors(),
                    strokeWidth = strokeDp.rdp,
                    padding = paddingDp.rdp,
                    gapAngleDegrees = gapAngleDegrees.rf,
                )
            }
            painter.advanceTimeBy(SettledMillis)

            // When progress reaches 1 and the outro settles
            painter.setFloat(ProgressName, 1f)
            painter.advanceTimeBy(SettledMillis)

            // Then the whole ring is the indicator
            val strokePx = strokeDp * PxPerDp
            assertWithMessage("stroke $strokeDp dp, padding $paddingDp dp, gap $gapAngleDegrees")
                .that(
                    painter.countRingSamplesNotIndicator(
                        fromDegrees = 0f,
                        toDegrees = 360f,
                        radiusPx = ringRadiusPx(strokePx, paddingPx = paddingDp * PxPerDp),
                        strokeWidthPx = strokePx,
                    )
                )
                .isEqualTo(0)
        }
    }

    @Test
    fun introOutroFullCircle_countingDownFromOne_isClosedRingFromFirstFrame() {
        // Given a full circle counting down
        // When it is painted at progress 1
        val painter = painterRule.paintDocument {
            RemoteIntroOutroCurvedProgressIndicator(
                progress = 1f.rf,
                modifier = RemoteModifier.fillMaxSize(),
                startAngle = 270f.rf,
                sweepAngle = 360f.rf,
                colors = progressIndicatorTestColors(),
                strokeWidth = StrokeWidthDp.rdp,
                padding = 0.rdp,
                countDown = true.rb,
            )
        }
        val firstFrameMissing = painter.countRingSamplesNotIndicator(0f, 360f)
        painter.advanceTimeBy(SettledMillis)
        val settledMissing = painter.countRingSamplesNotIndicator(0f, 360f)

        // Then the whole ring is the indicator from the first frame, with no track
        assertThat(firstFrameMissing).isEqualTo(0)
        assertThat(settledMissing).isEqualTo(0)
        assertThat(painter.countPixels(::isTrackColor)).isEqualTo(0)
    }

    @Test
    fun fullCircle_progressApproachingOne_closesPinchContinuously() {
        // A progress step of 1/2000 is 0.18 degrees of a 360 degree sweep, and the outro moves the
        // ends a few times faster, about a pixel per step along this ring. A ring that closes
        // continuously therefore has at most one pixel of the ring missing a step before progress
        // 1, two angles of samples half a pixel apart, while a ring that snaps closed at progress
        // 1 still has its whole pinch there.
        val progressStep = 1f / 2000f
        val maxMissingSamplesForOneStep = ringSamplesAcrossStroke(StrokeWidthPx) * 2
        val progresses = (20 downTo 0).map { 1f - it * progressStep }

        // Given a full circle with a gap
        // When its progress approaches 1
        val missingSamples = progresses.map { progress ->
            painterRule
                .paintDocument {
                    RemoteCurvedProgressIndicator(
                        progress = progress.rf,
                        modifier = RemoteModifier.fillMaxSize(),
                        startAngle = 270f.rf,
                        sweepAngle = 360f.rf,
                        colors = progressIndicatorTestColors(),
                        strokeWidth = StrokeWidthDp.rdp,
                        padding = 0.rdp,
                        gapAngleDegrees = GapAngleDegrees.rf,
                    )
                }
                .countRingSamplesNotIndicator(around = 270f)
        }

        // Then the pinch at the start angle shrinks without a jump, to nothing at progress 1
        assertThat(missingSamples).isInOrder(Comparator.reverseOrder<Int>())
        assertThat(missingSamples.first()).isGreaterThan(0)
        assertThat(missingSamples[missingSamples.size - 2]).isAtMost(maxMissingSamplesForOneStep)
        assertThat(missingSamples.last()).isEqualTo(0)
    }

    @Test
    fun fullCircle_progressBelowOne_startsIndicatorAtStartAngle() {
        // Given a full circle at progress 0.5
        val startAngle = 270f
        // When it is painted
        val painter = painterRule.paintDocument {
            RemoteCurvedProgressIndicator(
                progress = 0.5f.rf,
                modifier = RemoteModifier.fillMaxSize(),
                startAngle = startAngle.rf,
                sweepAngle = 360f.rf,
                colors = progressIndicatorTestColors(),
                strokeWidth = StrokeWidthDp.rdp,
                padding = 0.rdp,
            )
        }

        // Then its round cap ends at the start angle, rather than closing a ring
        assertThat(painter.isIndicatorOnRing(startAngle + EdgeMarginDegrees)).isTrue()
        assertThat(painter.isIndicatorOnRing(startAngle - EdgeMarginDegrees)).isFalse()
    }

    @Test
    fun openArc_progressOne_keepsRoundCapsWithinSweep() {
        for (sweepAngle in listOf(90f, 300f, 355f)) {
            // Given an open arc at progress 1
            val startAngle = 135f
            val endAngle = startAngle + sweepAngle
            // When it is painted
            val painter = painterRule.paintDocument {
                RemoteCurvedProgressIndicator(
                    progress = 1f.rf,
                    modifier = RemoteModifier.fillMaxSize(),
                    startAngle = startAngle.rf,
                    sweepAngle = sweepAngle.rf,
                    colors = progressIndicatorTestColors(),
                    strokeWidth = StrokeWidthDp.rdp,
                    padding = 0.rdp,
                )
            }

            // Then the indicator fills the sweep, and its round caps end at its ends
            val message = "sweep $sweepAngle"
            assertWithMessage(message).that(painter.countPixels(::isTrackColor)).isEqualTo(0)
            val capCenterInset = arcDegrees(StrokeWidthPx / 2f, RingRadiusPx)
            assertWithMessage(message)
                .that(
                    painter.countRingSamplesNotIndicator(
                        fromDegrees = startAngle + capCenterInset,
                        toDegrees = endAngle - capCenterInset,
                    )
                )
                .isEqualTo(0)
            for (inside in listOf(startAngle + EdgeMarginDegrees, endAngle - EdgeMarginDegrees)) {
                assertWithMessage("$message, $inside")
                    .that(painter.isIndicatorOnRing(inside))
                    .isTrue()
            }
            for (outside in listOf(startAngle - EdgeMarginDegrees, endAngle + EdgeMarginDegrees)) {
                assertWithMessage("$message, $outside")
                    .that(painter.isIndicatorOnRing(outside))
                    .isFalse()
            }
        }
    }

    /** Counts the samples not in the indicator color on the ring of the default stroke. */
    private fun DocumentPainterRule.DocumentPainter.countRingSamplesNotIndicator(
        fromDegrees: Float,
        toDegrees: Float,
    ): Int =
        countRingSamplesNotIndicator(
            fromDegrees = fromDegrees,
            toDegrees = toDegrees,
            radiusPx = RingRadiusPx,
            strokeWidthPx = StrokeWidthPx,
        )

    /**
     * Counts the samples not in the indicator color on the ring of the default stroke, within two
     * stroke widths of [around], which covers both round caps meeting there.
     */
    private fun DocumentPainterRule.DocumentPainter.countRingSamplesNotIndicator(
        around: Float
    ): Int {
        val twoStrokesDegrees = arcDegrees(2f * StrokeWidthPx, RingRadiusPx)
        return countRingSamplesNotIndicator(around - twoStrokesDegrees, around + twoStrokesDegrees)
    }

    /** Whether the middle of the stroke of the default ring at [degrees] is the indicator. */
    private fun DocumentPainterRule.DocumentPainter.isIndicatorOnRing(degrees: Float): Boolean =
        isIndicatorColor(colorOnCircle(degrees, RingRadiusPx))

    private companion object {
        const val CanvasSizePx = 200f
        /** Pixels per dp at the mdpi density of the test [Config]. */
        const val PxPerDp = 1f
        const val StrokeWidthDp = 8f
        const val StrokeWidthPx = StrokeWidthDp * PxPerDp
        const val GapAngleDegrees = 2f
        /**
         * Several times the settling time of the progress animations, so that the tests don't
         * depend on their exact springs.
         */
        const val SettledMillis = 3000L
        const val ProgressName = "progress"
        const val SweepName = "sweep"

        /**
         * The radius of the middle of the stroke, inset by the [paddingPx] from the edge of the
         * canvas, as the `padding` KDoc states.
         */
        fun ringRadiusPx(strokeWidthPx: Float, paddingPx: Float = 0f): Float =
            (CanvasSizePx - strokeWidthPx) / 2f - paddingPx

        val RingRadiusPx = ringRadiusPx(StrokeWidthPx)

        /** An angle clear of the anti-aliased edge of a round cap ending on a ring sample. */
        val EdgeMarginDegrees = arcDegrees(AntiAliasMarginPx, RingRadiusPx)
    }
}
