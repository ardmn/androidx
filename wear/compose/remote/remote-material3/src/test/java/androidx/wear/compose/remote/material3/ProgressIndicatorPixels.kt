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
import androidx.compose.remote.creation.compose.state.rc
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/**
 * The farthest a point can be from a corner of the pixel that contains it is sqrt(2), about 1.41
 * px. So a point at least this far inside a shape lies in a pixel that the shape covers fully, and
 * a point this far outside lies in a pixel that the shape doesn't touch, whatever the anti-aliasing
 * of the shape's edge.
 */
internal const val AntiAliasMarginPx = 1.5f

/**
 * The spacing of the samples of [countRingSamplesNotIndicator]. Half a pixel, so that consecutive
 * samples are never a pixel apart and no pixel of the ring is skipped.
 */
internal const val RingSampleSpacingPx = 0.5f

/**
 * Progress indicator colors whose indicator and track are told apart by [isIndicatorColor] and
 * [isTrackColor]: a red indicator and a blue track.
 */
@Composable
@RemoteComposable
internal fun progressIndicatorTestColors(): RemoteProgressIndicatorColors =
    RemoteProgressIndicatorDefaults.colors(
        indicatorColor = Color.Red.rc,
        trackColor = Color.Blue.rc,
    )

/**
 * Whether [color] is mostly the indicator color of [progressIndicatorTestColors]. A pixel that the
 * indicator covers less than half, at an anti-aliased edge, is not counted.
 */
internal fun isIndicatorColor(color: Color): Boolean = color.red > 0.5f && color.blue < 0.5f

/**
 * Whether [color] is mostly the track color of [progressIndicatorTestColors]. A pixel that the
 * track covers less than half, at an anti-aliased edge, is not counted.
 */
internal fun isTrackColor(color: Color): Boolean = color.blue > 0.5f && color.red < 0.5f

/** Converts the length [px] along a circle of [radiusPx] into the angle it spans, in degrees. */
internal fun arcDegrees(px: Float, radiusPx: Float): Float =
    Math.toDegrees(px / radiusPx.toDouble()).toFloat()

/**
 * Returns the color of the pixel that contains the point at [degrees], clockwise from 3 o'clock, on
 * the circle of [radiusPx] around the center of the painted canvas.
 */
internal fun DocumentPainterRule.DocumentPainter.colorOnCircle(
    degrees: Float,
    radiusPx: Float,
): Color {
    val radians = Math.toRadians(degrees.toDouble())
    val x = floor(width / 2f + radiusPx * cos(radians)).toInt()
    val y = floor(height / 2f + radiusPx * sin(radians)).toInt()
    return colorAt(x, y)
}

/**
 * Returns how many points of a ring are not drawn in the indicator color. The ring is centered on
 * the canvas, with a stroke of [strokeWidthPx] centered on the circle of [radiusPx]. It is sampled
 * from [fromDegrees] to [toDegrees], and across the stroke within [antiAliasMarginPx] of its edges,
 * every [sampleSpacingPx] in both directions.
 */
internal fun DocumentPainterRule.DocumentPainter.countRingSamplesNotIndicator(
    fromDegrees: Float,
    toDegrees: Float,
    radiusPx: Float,
    strokeWidthPx: Float,
    antiAliasMarginPx: Float = AntiAliasMarginPx,
    sampleSpacingPx: Float = RingSampleSpacingPx,
): Int {
    require(toDegrees >= fromDegrees) { "No ring from $fromDegrees to $toDegrees degrees" }
    val radialSteps = ringRadialSteps(strokeWidthPx, antiAliasMarginPx, sampleSpacingPx)
    val angleStepDegrees = arcDegrees(sampleSpacingPx, radiusPx)
    // Rounded up, so that the last sample is at toDegrees.
    val angleSteps = ceil((toDegrees - fromDegrees) / angleStepDegrees).toInt()
    return (0..angleSteps).sumOf { angleStep ->
        val degrees = minOf(fromDegrees + angleStep * angleStepDegrees, toDegrees)
        (-radialSteps..radialSteps).count { radialStep ->
            val radius = radiusPx + radialStep * sampleSpacingPx
            !isIndicatorColor(colorOnCircle(degrees, radius))
        }
    }
}

/**
 * Returns how many samples [countRingSamplesNotIndicator] takes across a stroke of [strokeWidthPx],
 * at each angle.
 */
internal fun ringSamplesAcrossStroke(
    strokeWidthPx: Float,
    antiAliasMarginPx: Float = AntiAliasMarginPx,
    sampleSpacingPx: Float = RingSampleSpacingPx,
): Int = 2 * ringRadialSteps(strokeWidthPx, antiAliasMarginPx, sampleSpacingPx) + 1

/** The samples on each side of the middle of the stroke, clear of its anti-aliased edges. */
private fun ringRadialSteps(
    strokeWidthPx: Float,
    antiAliasMarginPx: Float,
    sampleSpacingPx: Float,
): Int {
    val halfDepthPx = strokeWidthPx / 2f - antiAliasMarginPx
    require(halfDepthPx >= 0f) { "A stroke of $strokeWidthPx px has no pixel clear of its edges" }
    return floor(halfDepthPx / sampleSpacingPx).toInt()
}
