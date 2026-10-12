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
	background = Color(0xFF121212),
	surface = Color(0xFF121212),
)

private val LightColors = lightColorScheme(
	primary = Color(0xFF00796B),
	onPrimary = Color.White,
	primaryContainer = Color(0xFFA6F2E8),
	onPrimaryContainer = Color(0xFF00201D),
)

/** Material 3 theme for the Compose screens; follows the system dark mode like the rest of the app. */
@Composable
fun TGComposeTheme(content: @Composable () -> Unit) {
	MaterialTheme(
		colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
		content = content,
	)
}
