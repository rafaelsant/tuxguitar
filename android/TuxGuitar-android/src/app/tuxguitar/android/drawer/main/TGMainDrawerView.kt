package app.tuxguitar.android.drawer.main

import android.content.Context
import android.util.AttributeSet
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.tuxguitar.android.R
import app.tuxguitar.android.action.impl.gui.TGOpenDialogAction
import app.tuxguitar.android.action.impl.gui.TGOpenFragmentAction
import app.tuxguitar.android.action.impl.storage.TGOpenDocumentAction
import app.tuxguitar.android.action.impl.storage.TGSaveDocumentAction
import app.tuxguitar.android.action.impl.storage.TGSaveDocumentAsAction
import app.tuxguitar.android.action.impl.track.TGGoToTrackAction
import app.tuxguitar.android.activity.TGActivity
import app.tuxguitar.android.application.TGApplicationUtil
import app.tuxguitar.android.fragment.impl.TGChannelListFragmentController
import app.tuxguitar.android.fragment.impl.openTuner
import app.tuxguitar.android.storage.recent.TGRecentFile
import app.tuxguitar.android.storage.recent.TGRecentFiles
import app.tuxguitar.android.storage.saf.TGSafActionHandler
import app.tuxguitar.android.view.compose.TGComposeTheme
import app.tuxguitar.android.view.dialog.info.TGSongInfoDialogController
import app.tuxguitar.android.view.practice.TGAppMode
import app.tuxguitar.android.view.practice.TGAppModeController
import app.tuxguitar.android.view.tablature.TGSongViewController
import app.tuxguitar.android.view.tablature.TGSongViewSmartMenu
import app.tuxguitar.document.TGDocumentContextAttributes
import app.tuxguitar.document.TGDocumentManager
import app.tuxguitar.editor.action.TGActionProcessor
import app.tuxguitar.editor.action.file.TGLoadTemplateAction
import app.tuxguitar.editor.action.track.TGAddNewTrackAction
import app.tuxguitar.song.models.TGTrack
import app.tuxguitar.util.TGContext
import kotlinx.coroutines.delay

/** Side menu of the main screen (inflated from view_main_drawer.xml): file actions, tracks, instruments and song info. */
class TGMainDrawerView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : AbstractComposeView(context, attrs) {

	@Composable
	override fun Content() {
		val tgContext = remember { TGApplicationUtil.findContext(this) }
		val activity = context as TGActivity
		TGComposeTheme {
			Surface(modifier = Modifier.fillMaxSize()) {
				MainDrawer(tgContext, activity)
			}
		}
	}
}

private class TrackItem(val track: TGTrack, val name: String, val tuning: String, val selected: Boolean)

@Composable
private fun MainDrawer(context: TGContext, activity: TGActivity) {
	var tracks by remember { mutableStateOf(listOf<TrackItem>()) }
	var songName by remember { mutableStateOf<String?>(null) }
	var recent by remember { mutableStateOf(listOf<TGRecentFile>()) }

	// the track list follows song edits and the caret; polling keeps this simple
	LaunchedEffect(Unit) {
		while (true) {
			recent = runCatching { TGRecentFiles.list(activity).take(5) }.getOrDefault(listOf())
			runCatching {
				val song = TGDocumentManager.getInstance(context).song
				val selected = TGSongViewController.getInstance(context).caret.track
				songName = song?.name?.takeIf { it.isNotBlank() }
				tracks = (0 until (song?.countTracks() ?: 0)).map { i ->
					val track = song.getTrack(i)
					TrackItem(track, track.name, tuningOf(track), track == selected)
				}
			}
			delay(500)
		}
	}

	fun run(actionId: String, vararg attributes: Pair<String, Any>) {
		val processor = TGActionProcessor(context, actionId)
		attributes.forEach { (key, value) -> processor.setAttribute(key, value) }
		processor.process()
	}

	Column(modifier = Modifier.fillMaxSize().padding(vertical = 12.dp)) {
		// ---- header
		Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
			Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
			songName?.let {
				Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
			}
		}

		// ---- recent songs: reopening yesterday's song is the most common action when practicing
		if (recent.isNotEmpty()) {
			SectionLabel(stringResource(R.string.main_drawer_recent))
			recent.forEach { file ->
				NavigationDrawerItem(
					label = { Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
					selected = false,
					onClick = { TGSafActionHandler(context).callReadUri(file.uri) },
					modifier = Modifier.padding(horizontal = 12.dp),
				)
			}
			HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
		}

		// ---- file: saving only in edit mode, so practicing never overwrites a song by accident
		val editing = TGAppModeController.getInstance(context).mode == TGAppMode.EDIT
		SectionLabel(stringResource(R.string.main_drawer_file))
		DrawerAction(stringResource(R.string.action_file_open)) { run(TGOpenDocumentAction.NAME) }
		DrawerAction(stringResource(R.string.action_file_new)) { run(TGLoadTemplateAction.NAME) }
		if (editing) {
			DrawerAction(stringResource(R.string.action_file_save)) { run(TGSaveDocumentAction.NAME) }
			DrawerAction(stringResource(R.string.action_file_save_as)) { run(TGSaveDocumentAsAction.NAME) }
		}

		HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

		// ---- tracks
		SectionLabel(stringResource(R.string.main_drawer_tracks))
		LazyColumn(modifier = Modifier.weight(1f)) {
			items(tracks, key = { it.track.number }) { item ->
				TrackRow(
					item = item,
					onClick = { run(TGGoToTrackAction.NAME, TGDocumentContextAttributes.ATTRIBUTE_TRACK to item.track) },
					// the long press menu edits the track, so only in edit mode
					onLongClick = if (!editing) null else {
						{
							run(
								TGGoToTrackAction.NAME,
								TGDocumentContextAttributes.ATTRIBUTE_TRACK to item.track,
								TGSongViewSmartMenu.REQUEST_SMART_MENU to true,
								TGSongViewSmartMenu.TRACK_AREA_SELECTED to true,
							)
						}
					},
				)
			}
		}
		if (editing) {
			TextButton(onClick = { run(TGAddNewTrackAction.NAME) }, modifier = Modifier.padding(horizontal = 12.dp)) {
				Icon(painterResource(R.drawable.ic_bar_add_24dp), contentDescription = null)
				Text(stringResource(R.string.action_track_add), modifier = Modifier.padding(start = 8.dp))
			}
		}

		HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

		// ---- footer
		// one per line: side by side, the longer translations wrapped
		Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
			TextButton(onClick = {
				run(
					TGOpenFragmentAction.NAME,
					TGOpenFragmentAction.ATTRIBUTE_CONTROLLER to TGChannelListFragmentController.getInstance(context),
					TGOpenFragmentAction.ATTRIBUTE_ACTIVITY to activity,
				)
			}) {
				Icon(painterResource(R.drawable.ic_bar_mixer_24dp), contentDescription = null)
				Text(stringResource(R.string.action_view_channel_list), modifier = Modifier.padding(start = 8.dp))
			}
			TextButton(onClick = {
				run(
					TGOpenDialogAction.NAME,
					TGOpenDialogAction.ATTRIBUTE_DIALOG_CONTROLLER to TGSongInfoDialogController(),
					TGOpenDialogAction.ATTRIBUTE_DIALOG_ACTIVITY to activity,
				)
			}) {
				Icon(painterResource(R.drawable.ic_bar_info_24dp), contentDescription = null)
				Text(stringResource(R.string.action_change_properties), modifier = Modifier.padding(start = 8.dp))
			}
		}
	}
}

private val NOTE_NAMES = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

/** Tuning from the lowest string to the highest, e.g. "C G C F A D"; empty for percussion. */
private fun tuningOf(track: TGTrack): String =
	if (track.isPercussion) "" else track.strings.sortedByDescending { it.number }.joinToString(" ") { NOTE_NAMES[it.value % 12] }

@Composable
private fun SectionLabel(text: String) {
	Text(
		text,
		style = MaterialTheme.typography.labelLarge,
		color = MaterialTheme.colorScheme.primary,
		modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
	)
}

@Composable
private fun DrawerAction(label: String, onClick: () -> Unit) {
	NavigationDrawerItem(
		label = { Text(label) },
		selected = false,
		onClick = onClick,
		modifier = Modifier.padding(horizontal = 12.dp),
	)
}

/** Track row: tap selects the track, long press also opens its smart menu. The selected track is highlighted. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TrackRow(item: TrackItem, onClick: () -> Unit, onLongClick: (() -> Unit)?) {
	val colors = MaterialTheme.colorScheme
	Row(
		verticalAlignment = Alignment.CenterVertically,
		modifier = Modifier
			.fillMaxWidth()
			.padding(horizontal = 12.dp)
			.heightIn(min = 48.dp)
			.clip(RoundedCornerShape(28.dp))
			.background(if (item.selected) colors.secondaryContainer else colors.surface)
			.combinedClickable(onClick = onClick, onLongClick = onLongClick)
			.padding(horizontal = 16.dp, vertical = 14.dp),
	) {
		Column {
			Text(
				item.name,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
				color = if (item.selected) colors.onSecondaryContainer else colors.onSurface,
				fontWeight = if (item.selected) FontWeight.Bold else FontWeight.Normal,
			)
			if (item.tuning.isNotEmpty()) {
				Text(item.tuning, fontSize = 12.sp, color = if (item.selected) colors.onSecondaryContainer else colors.onSurfaceVariant)
			}
		}
	}
}
