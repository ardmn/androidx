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
 * The spacing of the samples of [countRingSamplesNotIndicator] on the circle it samples. Half a
 * pixel, so that consecutive samples within the sampled depth of the stroke stay under a pixel
 * apart and no pixel there is skipped.
 */
internal const val RingSampleSpacingPx = 0.5f

/**
 * Progress indicator colors whose indicator and track are told apart by [isIndicator] and
 * [isTrack]: a red indicator and a blue track.
 */
@Composable
@RemoteComposable
internal fun testColors(): RemoteProgressIndicatorColors =
    RemoteProgressIndicatorDefaults.colors(
        indicatorColor = Color.Red.rc,
        trackColor = Color.Blue.rc,
    )

/**
 * Whether [color] reads as the red indicator color of [testColors]: red above half and blue below
 * half. Alpha is not checked.
 */
internal fun isIndicator(color: Color): Boolean = color.red > 0.5f && color.blue < 0.5f

/**
 * Whether [color] reads as the blue track color of [testColors]: blue above half and red below
 * half. Alpha is not checked.
 */
internal fun isTrack(color: Color): Boolean = color.blue > 0.5f && color.red < 0.5f

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
    require(x in 0 until width && y in 0 until height) {
        "The point at $degrees degrees on a circle of $radiusPx px is outside the canvas"
    }
    return colorAt(x, y)
}

/**
 * Returns how many points of a ring are not drawn in the indicator color. The ring is centered on
 * the canvas, with a stroke of [strokeWidthPx] centered on the circle of [radiusPx]. It is sampled
 * from [fromDegrees] to [toDegrees], and across the stroke at least [antiAliasMarginPx] inside its
 * edges, every [sampleSpacingPx] in both directions.
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
    val halfDepthPx = strokeWidthPx / 2f - antiAliasMarginPx
    require(halfDepthPx >= 0f) { "A stroke of $strokeWidthPx px has no pixel clear of its edges" }
    val radialSteps = floor(halfDepthPx / sampleSpacingPx).toInt()
    val angleStepDegrees = arcDegrees(sampleSpacingPx, radiusPx)
    // Rounded up, so that the last sample is at toDegrees.
    val angleSteps = ceil((toDegrees - fromDegrees) / angleStepDegrees).toInt()
    return (0..angleSteps).sumOf { angleStep ->
        val degrees = minOf(fromDegrees + angleStep * angleStepDegrees, toDegrees)
        (-radialSteps..radialSteps).count { radialStep ->
            val radius = radiusPx + radialStep * sampleSpacingPx
            !isIndicator(colorOnCircle(degrees, radius))
        }
    }
}
