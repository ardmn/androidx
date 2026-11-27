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

import androidx.compose.ui.unit.dp

/**
 * Spec values of the Wear Material3 `EdgeButton` (`androidx.wear.compose.material3`, EdgeButton.kt)
 * that are internal there, restated once for the tests that check [RemoteEdgeButton] against them.
 */
internal object WearMaterial3EdgeButtonSpec {
    /** `EdgeButtonVerticalPadding`: the padding above and below the container. */
    val VerticalPadding = 3.dp

    /** `EdgeButtonSize.ExtraSmall.maximumHeight`: the container height. */
    val ExtraSmallHeight = 46.dp

    /** `EdgeButtonSize.Small.maximumHeight`: the container height. */
    val SmallHeight = 56.dp

    /** `EdgeButtonSize.Medium.maximumHeight`: the container height. */
    val MediumHeight = 70.dp

    /** `EdgeButtonSize.Large.maximumHeight`: the container height. */
    val LargeHeight = 96.dp

    /** `EXTRA_SMALL_ELLIPSIS_HEIGHT`: the ellipse height of the shape at the ExtraSmall size. */
    val ExtraSmallEllipseHeight = 58.dp

    /** `BUTTON_TO_ELLIPSIS_RATIO`: how fast the ellipse grows with the button height. */
    const val ButtonToEllipseRatio = 1.42f

    /**
     * `TARGET_SIDE_PADDING`: the horizontal distance from the side of the button to the screen
     * edge.
     */
    val TargetSidePadding = 20.dp
}
