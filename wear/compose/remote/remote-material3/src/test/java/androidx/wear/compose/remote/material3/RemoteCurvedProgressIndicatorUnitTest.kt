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
 * Pixel tests of the `sweepAngle` KDoc: a sweep of 360 degrees or more is a full ring at progress
 * 1f, and a shorter sweep keeps the indicator and its round caps inside it.
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
            assertWithMessage("$case")
                .that(painter.countSamplesNotIndicatorOnRing(0f, 360f))
                .isEqualTo(0)
        }
    }

    @Test
    fun fullCircle_progressLeavingAndReturningToOne_opensAndClosesRingAtStartAngle() {
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
        val closedMissingSamples = painter.countPinchSamplesNotIndicator(aroundDegrees = startAngle)

        painter.setFloat(ProgressName, 0.99f)
        val reopenedMissingSamples =
            painter.countPinchSamplesNotIndicator(aroundDegrees = startAngle)
        painter.setFloat(ProgressName, 1f)
        val closedAgainMissingSamples =
            painter.countPinchSamplesNotIndicator(aroundDegrees = startAngle)

        assertThat(closedMissingSamples).isEqualTo(0)
        assertThat(reopenedMissingSamples).isGreaterThan(0)
        assertThat(closedAgainMissingSamples).isEqualTo(0)
    }

    @Test
    fun progressOne_sweepChangingAcross360_closesRingOnlyForFullCircle() {
        val startAngle = 270f
        val sweepName = "sweep"
        val painter = painterRule.paintDocument {
            RemoteCurvedProgressIndicator(
                progress = 1f.rf,
                modifier = RemoteModifier.fillMaxSize(),
                startAngle = startAngle.rf,
                sweepAngle = RemoteFloat.createNamedRemoteFloat(sweepName, 360f),
                colors = testColors(),
                strokeWidth = StrokeWidthDp.rdp,
                padding = 0.rdp,
            )
        }
        val fullCircleMissing = painter.countSamplesNotIndicatorOnRing(0f, 360f)

        painter.setFloat(sweepName, 300f)
        val openArcInsideStart = painter.isIndicatorOnRing(startAngle + EdgeMarginDegrees)
        val openArcBeforeStart = painter.isIndicatorOnRing(startAngle - EdgeMarginDegrees)
        painter.setFloat(sweepName, 360f)
        val fullCircleAgainMissing = painter.countSamplesNotIndicatorOnRing(0f, 360f)

        assertThat(fullCircleMissing).isEqualTo(0)
        assertThat(openArcInsideStart).isTrue()
        assertThat(openArcBeforeStart).isFalse()
        assertThat(fullCircleAgainMissing).isEqualTo(0)
    }

    @Test
    fun introOutroFullCircle_countingUpToOne_closesRingOnceSettled() {
        // Stroke widths and paddings set the length of the intro and the outro, and so the eased
        // progress, which must still reach 1 once its animations settle.
        data class IntroOutroCase(
            val strokeDp: Int,
            val paddingDp: Int,
            val gapAngleDegrees: Float = 0f,
        )
        for (case in
            listOf(
                IntroOutroCase(strokeDp = 8, paddingDp = 0, gapAngleDegrees = GapAngleDegrees),
                IntroOutroCase(strokeDp = 13, paddingDp = 3),
                IntroOutroCase(strokeDp = 19, paddingDp = 11),
                IntroOutroCase(strokeDp = 26, paddingDp = 7),
            )) {
            val painter = painterRule.paintDocument {
                RemoteIntroOutroCurvedProgressIndicator(
                    progress = RemoteFloat.createNamedRemoteFloat(ProgressName, 0.5f),
                    modifier = RemoteModifier.fillMaxSize(),
                    startAngle = 270f.rf,
                    sweepAngle = 360f.rf,
                    colors = testColors(),
                    strokeWidth = case.strokeDp.rdp,
                    padding = case.paddingDp.rdp,
                    gapAngleDegrees = case.gapAngleDegrees.rf,
                )
            }
            painter.advanceTimeBy(SettledMillis)

            painter.setFloat(ProgressName, 1f)
            painter.advanceTimeBy(SettledMillis)

            val strokePx = case.strokeDp * PxPerDp
            assertWithMessage("$case")
                .that(
                    painter.countRingSamplesNotIndicator(
                        fromDegrees = 0f,
                        toDegrees = 360f,
                        radiusPx = ringRadiusPx(strokePx, paddingPx = case.paddingDp * PxPerDp),
                        strokeWidthPx = strokePx,
                    )
                )
                .isEqualTo(0)
        }
    }

    @Test
    fun introOutroFullCircle_countingDownFromOne_isClosedRingFromFirstFrame() {
        val painter = painterRule.paintDocument {
            RemoteIntroOutroCurvedProgressIndicator(
                progress = 1f.rf,
                modifier = RemoteModifier.fillMaxSize(),
                startAngle = 270f.rf,
                sweepAngle = 360f.rf,
                colors = testColors(),
                strokeWidth = StrokeWidthDp.rdp,
                padding = 0.rdp,
                countDown = true.rb,
            )
        }
        val firstFrameMissing = painter.countSamplesNotIndicatorOnRing(0f, 360f)
        painter.advanceTimeBy(SettledMillis)
        val settledMissing = painter.countSamplesNotIndicatorOnRing(0f, 360f)

        assertThat(firstFrameMissing).isEqualTo(0)
        assertThat(settledMissing).isEqualTo(0)
        assertThat(painter.countPixels(::isTrack)).isEqualTo(0)
    }

    @Test
    fun introOutroFullCircle_countingDownBelowOne_opensRingAtStartAngle() {
        val startAngle = 270f
        val painter = painterRule.paintDocument {
            RemoteIntroOutroCurvedProgressIndicator(
                progress = RemoteFloat.createNamedRemoteFloat(ProgressName, 1f),
                modifier = RemoteModifier.fillMaxSize(),
                startAngle = startAngle.rf,
                sweepAngle = 360f.rf,
                colors = testColors(),
                strokeWidth = StrokeWidthDp.rdp,
                padding = 0.rdp,
                countDown = true.rb,
            )
        }
        painter.advanceTimeBy(SettledMillis)
        val closedMissingSamples = painter.countPinchSamplesNotIndicator(aroundDegrees = startAngle)

        painter.setFloat(ProgressName, 0.99f)
        painter.advanceTimeBy(SettledMillis)

        assertThat(closedMissingSamples).isEqualTo(0)
        assertThat(painter.isIndicatorOnRing(startAngle + EdgeMarginDegrees)).isTrue()
        assertThat(painter.isIndicatorOnRing(startAngle - EdgeMarginDegrees)).isFalse()
    }

    /**
     * With a gap and a collapsible dot, the two ends merge before progress 1f rather than snapping
     * closed at 1f, as [RemoteCircularProgressIndicator] closes its gap.
     */
    @Test
    fun fullCircle_progressApproachingOne_closesPinchContinuously() {
        val startAngle = 270f
        fun paintIndicator(progress: Float, sweepAngle: Float) = painterRule.paintDocument {
            RemoteCurvedProgressIndicator(
                progress = progress.rf,
                modifier = RemoteModifier.fillMaxSize(),
                startAngle = startAngle.rf,
                sweepAngle = sweepAngle.rf,
                colors = testColors(),
                strokeWidth = StrokeWidthDp.rdp,
                padding = 0.rdp,
                gapAngleDegrees = GapAngleDegrees.rf,
            )
        }
        // An open arc just under 360 degrees at progress 1 draws two round caps that just touch at
        // the start angle.
        val touchingCapsMissingSamples =
            paintIndicator(progress = 1f, sweepAngle = 359.9f)
                .countPinchSamplesNotIndicator(aroundDegrees = startAngle)
        // A step of 1/2000 is 0.18 degrees of the sweep, under a pixel along this ring, so that the
        // step before progress 1 is within the merge.
        val progressStep = 1f / 2000f
        val progresses = (20 downTo 0).map { 1f - it * progressStep }

        val missingSamples = progresses.map { progress ->
            paintIndicator(progress = progress, sweepAngle = 360f)
                .countPinchSamplesNotIndicator(aroundDegrees = startAngle)
        }

        assertThat(touchingCapsMissingSamples).isGreaterThan(0)
        assertThat(missingSamples).isInOrder(Comparator.reverseOrder<Int>())
        assertThat(missingSamples.first()).isGreaterThan(0)
        // A ring that snaps closed at progress 1 would still have touching caps here.
        assertThat(missingSamples[missingSamples.lastIndex - 1])
            .isLessThan(touchingCapsMissingSamples)
        assertThat(missingSamples.last()).isEqualTo(0)
    }

    @Test
    fun fullCircle_progressHalf_startsIndicatorAtStartAngle() {
        val startAngle = 270f
        val painter = painterRule.paintDocument {
            RemoteCurvedProgressIndicator(
                progress = 0.5f.rf,
                modifier = RemoteModifier.fillMaxSize(),
                startAngle = startAngle.rf,
                sweepAngle = 360f.rf,
                colors = testColors(),
                strokeWidth = StrokeWidthDp.rdp,
                padding = 0.rdp,
            )
        }

        assertThat(painter.isIndicatorOnRing(startAngle + EdgeMarginDegrees)).isTrue()
        assertThat(painter.isIndicatorOnRing(startAngle - EdgeMarginDegrees)).isFalse()
    }

    @Test
    fun openArc_progressOne_fillsSweepWithRoundCapsInsideIt() {
        val startAngle = 135f
        val capCenterInset = arcDegrees(StrokeWidthPx / 2f, RingRadiusPx)
        for (sweepAngle in listOf(90f, 300f, 355f)) {
            val endAngle = startAngle + sweepAngle
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
            assertWithMessage(message)
                .that(
                    painter.countSamplesNotIndicatorOnRing(
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

    /**
     * Counts the samples not in the indicator color from [fromDegrees] to [toDegrees] on the ring
     * of [StrokeWidthPx] and no padding.
     */
    private fun DocumentPainterRule.DocumentPainter.countSamplesNotIndicatorOnRing(
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
     * Counts the samples not in the indicator color on the ring of [StrokeWidthPx] and no padding,
     * within two stroke widths of [aroundDegrees], which covers both round caps meeting there.
     */
    private fun DocumentPainterRule.DocumentPainter.countPinchSamplesNotIndicator(
        aroundDegrees: Float
    ): Int {
        val twoStrokesDegrees = arcDegrees(2f * StrokeWidthPx, RingRadiusPx)
        return countSamplesNotIndicatorOnRing(
            fromDegrees = aroundDegrees - twoStrokesDegrees,
            toDegrees = aroundDegrees + twoStrokesDegrees,
        )
    }

    /** Whether the middle of the stroke of the default ring at [degrees] is the indicator. */
    private fun DocumentPainterRule.DocumentPainter.isIndicatorOnRing(degrees: Float): Boolean =
        isIndicator(colorOnCircle(degrees, RingRadiusPx))

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
