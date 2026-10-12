package app.tuxguitar.android.fragment.impl

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import app.tuxguitar.android.R
import app.tuxguitar.android.action.impl.storage.TGStorageLoadSettingsAction
import app.tuxguitar.android.action.impl.transport.TGTransportLoadSettingsAction
import app.tuxguitar.android.activity.TGActivity
import app.tuxguitar.android.properties.TGSharedPreferencesUtil
import app.tuxguitar.android.storage.TGStorageProperties
import app.tuxguitar.android.transport.TGTransportProperties
import app.tuxguitar.android.view.compose.TGComposeTheme
import app.tuxguitar.android.view.compose.TGScreenTopBar
import app.tuxguitar.editor.action.TGActionProcessor
import app.tuxguitar.player.base.MidiPlayer
import app.tuxguitar.util.TGContext

/** Settings screen, drawn with Compose. Values are stored in the same shared preferences the app reads its settings from. */
class TGPreferencesFragment : Fragment() {

	companion object {
		const val MODULE = "tuxguitar"
		const val RESOURCE = "settings"
	}

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
		val activity = requireActivity() as TGActivity
		return ComposeView(activity).apply {
			setContent {
				TGComposeTheme {
					Surface(modifier = Modifier.fillMaxSize()) {
						Column {
							TGScreenTopBar(stringResource(R.string.action_menu_settings), { activity.callBackAction() })
							SettingsScreen(activity, activity.findContext())
						}
					}
				}
			}
		}
	}

	override fun onResume() {
		super.onResume()
		(activity as? TGActivity)?.setLegacyActionBarVisible(false)
	}
}

private class PortOption(val key: String, val name: String)

@Composable
private fun SettingsScreen(activity: TGActivity, context: TGContext) {
	val preferences = remember {
		activity.getSharedPreferences(
			TGSharedPreferencesUtil.getSharedPreferencesName(activity, TGPreferencesFragment.MODULE, TGPreferencesFragment.RESOURCE), 0,
		)
	}
	val player = MidiPlayer.getInstance(context)
	val ports = remember { player.listOutputPorts().map { PortOption(it.key, it.name) } }
	var collectionBrowser by remember { mutableStateOf(TGStorageProperties(context).isUseCollectionBrowser) }
	var openPort by remember { mutableStateOf(ports.firstOrNull { player.isOutputPortOpen(it.key) }?.key) }

	fun reload(actionId: String) = TGActionProcessor(context, actionId).process()

	Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(vertical = 8.dp)) {
		SectionTitle(stringResource(R.string.preferences_general_title))
		// the app language is an Android setting (Android 13+): open it directly
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
			ListItem(
				headlineContent = { Text(stringResource(R.string.preferences_language_title)) },
				supportingContent = { Text(stringResource(R.string.preferences_language_summary)) },
				modifier = Modifier.clickable {
					activity.startActivity(Intent(Settings.ACTION_APP_LOCALE_SETTINGS, Uri.fromParts("package", activity.packageName, null)))
				},
			)
		}
		ListItem(
			headlineContent = { Text(stringResource(R.string.preferences_general_use_collection_browser_title)) },
			supportingContent = { Text(stringResource(R.string.preferences_general_use_collection_browser_summary)) },
			trailingContent = {
				Switch(checked = collectionBrowser, onCheckedChange = null)
			},
			modifier = Modifier.clickable {
				collectionBrowser = !collectionBrowser
				preferences.edit().putBoolean(TGStorageProperties.PROPERTY_COLLECTION_BROWSER, collectionBrowser).apply()
				reload(TGStorageLoadSettingsAction.NAME)
			},
		)

		HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
		SectionTitle(stringResource(R.string.preferences_sound_category_title))
		Text(
			stringResource(R.string.preferences_midi_output_port_title),
			style = MaterialTheme.typography.bodyMedium,
			modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
		)
		ports.forEach { port ->
			ListItem(
				headlineContent = { Text(port.name) },
				leadingContent = { RadioButton(selected = port.key == openPort, onClick = null) },
				modifier = Modifier.clickable {
					openPort = port.key
					preferences.edit().putString(TGTransportProperties.PROPERTY_MIDI_OUTPUT_PORT, port.key).apply()
					reload(TGTransportLoadSettingsAction.NAME)
				},
			)
		}
		if (ports.isEmpty()) {
			Text(
				stringResource(R.string.preferences_midi_output_port_summary_empty),
				modifier = Modifier.padding(horizontal = 16.dp),
			)
		}
	}
}

@Composable
private fun SectionTitle(text: String) {
	Text(
		text,
		style = MaterialTheme.typography.labelLarge,
		color = MaterialTheme.colorScheme.primary,
		modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
	)
}
