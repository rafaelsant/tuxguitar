package app.tuxguitar.android.view.compose

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import android.app.Activity
import android.view.View
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.tuxguitar.android.R

/** Top bar of the secondary screens and dialogs: back, title and [actions] on the right. */
@Composable
fun TGScreenTopBar(title: String, onBack: () -> Unit, actions: @Composable RowScope.() -> Unit = {}) {
	// the status bar takes the color of the bar, so no band of another color is left above it
	val barColor = MaterialTheme.colorScheme.surfaceContainer
	val activity = LocalContext.current as? Activity
	val dark = isSystemInDarkTheme()
	SideEffect {
		activity?.let {
			it.window.statusBarColor = barColor.toArgb()
			(it.findViewById<View>(R.id.root_layout) as? DrawerLayout)?.setStatusBarBackgroundColor(barColor.toArgb())
			WindowCompat.getInsetsController(it.window, it.window.decorView).isAppearanceLightStatusBars = !dark
		}
	}
	Surface(color = barColor, modifier = Modifier.fillMaxWidth()) {
		Row(modifier = Modifier.height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
			IconButton(onClick = onBack) {
				Icon(painterResource(R.drawable.ic_bar_back_24dp), stringResource(R.string.main_menu_back))
			}
			Text(
				title,
				style = MaterialTheme.typography.titleLarge,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
				modifier = Modifier.weight(1f).padding(start = 4.dp),
			)
			actions()
		}
	}
}
