package app.tuxguitar.android.view.dialog.fragment

import android.os.Bundle
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.tuxguitar.android.R
import app.tuxguitar.android.view.compose.TGComposeTheme

/**
 * Base of the dialogs drawn with Compose: a full screen with its own top bar (back, title, OK) and the [Content].
 * The action bar is hidden while it is shown, like on the main screen.
 */
abstract class TGComposeModalFragment(private val titleId: Int) : TGModalFragment(R.layout.view_compose_modal) {

	/** The dialog body. */
	@Composable
	protected abstract fun Content()

	/** Applies the dialog when OK is tapped. */
	protected abstract fun onOk()

	/** False keeps OK disabled (e.g. invalid input); read from Compose state so the button follows it. */
	protected open fun isOkEnabled(): Boolean = true

	/** Dialogs without OK (they apply each change right away) only show back. */
	protected open fun hasOk(): Boolean = true

	/** Optional second action next to OK (e.g. "Clean"), as a string resource; null for none. */
	protected open fun secondaryActionLabel(): Int? = null

	/** Runs the second action; the dialog closes afterwards. */
	protected open fun onSecondaryAction() {}

	/** Called before the content is shown: read the dialog attributes into Compose state here. */
	protected open fun initState() {}

	override fun onPostCreate(savedInstanceState: Bundle?) {
		this.createActionBar(false, false, titleId)
	}

	override fun onPostInflateView() {
		initState()
		(this.view as ComposeView).setContent {
			TGComposeTheme {
				Surface(modifier = Modifier.fillMaxSize()) {
					Column {
						DialogTopBar(
							title = stringResource(titleId),
							okEnabled = isOkEnabled(),
							showOk = hasOk(),
							secondaryLabel = secondaryActionLabel()?.let { stringResource(it) },
							onSecondary = {
								onSecondaryAction()
								close()
							},
							onBack = { close() },
							onOk = {
								onOk()
								close()
							},
						)
						Box(modifier = Modifier.weight(1f)) {
							Content()
						}
					}
				}
			}
		}
	}

	// posted: when screens are swapped, this runs after the previous one restored the action bar
	override fun onShowView() {
		this.view?.post { this.findActivity()?.supportActionBar?.hide() }
	}

	override fun onHideView() {
		this.findActivity()?.supportActionBar?.show()
	}
}

@Composable
private fun DialogTopBar(
	title: String,
	okEnabled: Boolean,
	showOk: Boolean,
	secondaryLabel: String?,
	onSecondary: () -> Unit,
	onBack: () -> Unit,
	onOk: () -> Unit,
) {
	Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
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
			if (secondaryLabel != null) {
				TextButton(onClick = onSecondary) {
					Text(secondaryLabel)
				}
			}
			if (showOk) {
				TextButton(onClick = onOk, enabled = okEnabled) {
					Text(stringResource(R.string.global_button_ok), fontWeight = FontWeight.Bold)
				}
			}
		}
	}
}
