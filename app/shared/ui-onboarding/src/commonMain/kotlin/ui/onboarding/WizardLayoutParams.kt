/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass
import me.him188.ani.app.ui.foundation.layout.paneHorizontalPadding
import me.him188.ani.app.ui.foundation.layout.paneVerticalPadding

@Immutable
data class WizardLayoutParams(
    val horizontalPadding: Dp,
    val verticalPadding: Dp,
    val descHorizontalPadding: Dp = 4.dp,
) {
    companion object {
        @Composable
        fun calculate(windowSizeClass: WindowSizeClass): WizardLayoutParams {
            return remember(windowSizeClass) {
                WizardLayoutParams(
                    horizontalPadding = windowSizeClass.paneHorizontalPadding,
                    verticalPadding = windowSizeClass.paneVerticalPadding,
                )
            }
        }
    }
}