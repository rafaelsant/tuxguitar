package app.tuxguitar.android.view.compose

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
	primary = Color(0xFF4DB6AC),
	onPrimary = Color(0xFF00201D),
	primaryContainer = Color(0xFF00504A),
	onPrimaryContainer = Color(0xFFA6F2E8),
	secondaryContainer = Color(0xFF334B48),
	onSecondaryContainer = Color(0xFFCCE8E3),
	tertiaryContainer = Color(0xFF5C4300),
	onTertiaryContainer = Color(0xFFFFDF9E),
	background = Color(0xFF121212),
	surface = Color(0xFF121212),
	// neutral containers: the Material defaults are tinted purple, which clashed with the teal accent
	surfaceContainerLowest = Color(0xFF0D0F0F),
	surfaceContainerLow = Color(0xFF1C1F1F),
	surfaceContainer = Color(0xFF222626),
	surfaceContainerHigh = Color(0xFF2C3131),
	surfaceContainerHighest = Color(0xFF373C3C),
	outlineVariant = Color(0xFF3F4948),
)

private val LightColors = lightColorScheme(
	primary = Color(0xFF00796B),
	onPrimary = Color.White,
	primaryContainer = Color(0xFFA6F2E8),
	onPrimaryContainer = Color(0xFF00201D),
	secondaryContainer = Color(0xFFCCE8E3),
	onSecondaryContainer = Color(0xFF051F1C),
	tertiaryContainer = Color(0xFFFFDF9E),
	onTertiaryContainer = Color(0xFF261A00),
	surfaceContainerLowest = Color.White,
	surfaceContainerLow = Color(0xFFF2F4F3),
	surfaceContainer = Color(0xFFECEFEE),
	surfaceContainerHigh = Color(0xFFE6E9E8),
	surfaceContainerHighest = Color(0xFFE0E3E2),
)

/** Material 3 theme for the Compose screens; follows the system dark mode like the rest of the app. */
@Composable
fun TGComposeTheme(content: @Composable () -> Unit) {
	MaterialTheme(
		colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
		content = content,
	)
}
