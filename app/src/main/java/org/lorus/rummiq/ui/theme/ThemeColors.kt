package org.lorus.rummiq.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * Theme-aware "success"/winner accent green.
 *
 * Material's color scheme has no green role, and a single fixed green fails the contrast
 * requirement on one of the two backgrounds (a mid green like #4CAF50 is unreadable as text
 * on a light surface). So pick a shade per active theme, derived from the current surface
 * luminance (which reflects the app's theme override, unlike isSystemInDarkTheme()).
 */
private val SuccessLight = Color(0xFF2E7D32)
private val SuccessDark = Color(0xFF81C784)

/**
 * Gold/amber accent for TEXT. The scheme's tertiary (Amber40 #FFB300) is fine for icons,
 * borders and background tints, but as small text on a light surface it falls to ~1.4:1
 * contrast — so text uses a dark gold in the light theme and a bright gold in the dark one.
 */
private val GoldTextLight = Color(0xFF8D6E00)
private val GoldTextDark = Color(0xFFFFCA28)

val ColorScheme_isDark: Boolean
    @Composable @ReadOnlyComposable
    get() = MaterialTheme.colorScheme.surface.luminance() < 0.5f

@Composable
@ReadOnlyComposable
fun successColor(): Color = if (ColorScheme_isDark) SuccessDark else SuccessLight

@Composable
@ReadOnlyComposable
fun goldAccentColor(): Color = if (ColorScheme_isDark) GoldTextDark else GoldTextLight
