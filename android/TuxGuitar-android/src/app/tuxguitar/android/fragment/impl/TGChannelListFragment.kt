package app.tuxguitar.android.fragment.impl

import android.os.Bundle
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.tuxguitar.android.R
import app.tuxguitar.android.action.impl.gui.TGOpenDialogAction
import app.tuxguitar.android.fragment.TGCachedFragment
import app.tuxguitar.android.view.compose.TGComposeTheme
import app.tuxguitar.android.view.compose.TGScreenTopBar
import app.tuxguitar.android.view.dialog.TGDialogController
import app.tuxguitar.android.view.dialog.channel.TGChannelEditDialogController
import app.tuxguitar.android.view.dialog.confirm.TGConfirmDialogController
import app.tuxguitar.document.TGDocumentContextAttributes
import app.tuxguitar.document.TGDocumentManager
import app.tuxguitar.editor.action.TGActionProcessor
import app.tuxguitar.editor.action.channel.TGAddNewChannelAction
import app.tuxguitar.editor.action.channel.TGRemoveChannelAction
import app.tuxguitar.editor.action.channel.TGUpdateChannelAction
import app.tuxguitar.editor.action.track.TGChangeTrackMuteAction
import app.tuxguitar.editor.action.track.TGChangeTrackSoloAction
import app.tuxguitar.player.base.MidiPlayer
import app.tuxguitar.song.models.TGChannel
import app.tuxguitar.song.models.TGTrack
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * Instruments screen (opened from the side menu): mute/solo per track, so a part can be heard alone or left out
 * to play along, and the song channels with their volume. Tapping a channel opens its editor.
 */
class TGChannelListFragment : TGCachedFragment(R.layout.view_compose_modal) {

	private data class TrackItem(val track: TGTrack, val name: String, val channel: String, val mute: Boolean, val solo: Boolean)

	private data class ChannelItem(val channel: TGChannel, val name: String, val instrument: String, val volume: Int, val removable: Boolean)

	private var tracks by mutableStateOf(listOf<TrackItem>())
	private var channels by mutableStateOf(listOf<ChannelItem>())

	override fun onPostCreate(savedInstanceState: Bundle?) {
		TGChannelListFragmentController.getInstance(findContext()).attachInstance(this)
		createActionBar(false, false, R.string.channel_list)
	}

	override fun onPostInflateView() {
		(view as ComposeView).setContent {
			TGComposeTheme {
				Surface(modifier = Modifier.fillMaxSize()) {
					Column {
						TGScreenTopBar(stringResource(R.string.channel_list), { findActivity().callBackAction() }) {
							IconButton(onClick = { run(TGAddNewChannelAction.NAME) }) {
								Icon(painterResource(R.drawable.ic_bar_add_24dp), stringResource(R.string.action_channel_list_add))
							}
						}
						Screen()
					}
				}
			}
		}
	}

	override fun onShowView() {
		findActivity()?.setLegacyActionBarVisible(false)
	}

	@Composable
	private fun Screen() {
		// follows edits made elsewhere (channel editor, undo); polling keeps this simple
		LaunchedEffect(Unit) {
			while (true) {
				refresh()
				delay(500)
			}
		}
		LazyColumn(
			contentPadding = PaddingValues(16.dp),
			verticalArrangement = Arrangement.spacedBy(8.dp),
			modifier = Modifier.fillMaxSize(),
		) {
			if (tracks.isNotEmpty()) {
				item { SectionLabel(stringResource(R.string.main_drawer_tracks)) }
				item { TrackGroup(tracks) }
			}
			item { SectionLabel(stringResource(R.string.channel_list_channels)) }
			if (channels.isEmpty()) {
				item {
					Text(
						stringResource(R.string.channel_list_empty),
						color = MaterialTheme.colorScheme.onSurfaceVariant,
						modifier = Modifier.padding(vertical = 16.dp),
					)
				}
			}
			items(channels, key = { "c" + it.channel.channelId }) { ChannelCard(it) }
		}
	}

	@Composable
	private fun SectionLabel(text: String) {
		Text(
			text,
			style = MaterialTheme.typography.labelLarge,
			color = MaterialTheme.colorScheme.primary,
			modifier = Modifier.padding(top = 8.dp, bottom = 4.dp).semantics { heading() },
		)
	}

	/** The tracks in one group, apart from the channel cards: these are switches, not things to open. */
	@Composable
	private fun TrackGroup(items: List<TrackItem>) {
		val anySolo = items.any { it.solo }
		Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
			Column {
				items.forEachIndexed { index, item ->
					if (index > 0) {
						HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(horizontal = 16.dp))
					}
					TrackRow(item, audible = !item.mute && (!anySolo || item.solo))
				}
			}
		}
	}

	@Composable
	private fun TrackRow(item: TrackItem, audible: Boolean) {
		val muteLabel = stringResource(R.string.channel_list_mute)
		val soloLabel = stringResource(R.string.channel_list_solo)
		Row(
			verticalAlignment = Alignment.CenterVertically,
			modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 4.dp),
		) {
			// tracks left silent by a mute or by another track's solo are faded
			Column(modifier = Modifier.weight(1f).alpha(if (audible) 1f else 0.5f)) {
				Text(item.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
				Text(item.channel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
			}
			FilterChip(
				selected = item.mute,
				onClick = { run(TGChangeTrackMuteAction.NAME, TGDocumentContextAttributes.ATTRIBUTE_TRACK to item.track) },
				label = { Text(muteLabel) },
				colors = FilterChipDefaults.filterChipColors(
					selectedContainerColor = MaterialTheme.colorScheme.errorContainer,
					selectedLabelColor = MaterialTheme.colorScheme.onErrorContainer,
				),
				modifier = Modifier.padding(start = 8.dp).semantics { contentDescription = "$muteLabel ${item.name}" },
			)
			FilterChip(
				selected = item.solo,
				onClick = { run(TGChangeTrackSoloAction.NAME, TGDocumentContextAttributes.ATTRIBUTE_TRACK to item.track) },
				label = { Text(soloLabel) },
				colors = FilterChipDefaults.filterChipColors(
					selectedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
					selectedLabelColor = MaterialTheme.colorScheme.onTertiaryContainer,
				),
				modifier = Modifier.padding(start = 8.dp).semantics { contentDescription = "$soloLabel ${item.name}" },
			)
		}
	}

	@Composable
	private fun ChannelCard(item: ChannelItem) {
		val removeQuestion = stringResource(R.string.action_channel_list_item_remove_confirm_question)
		val editLabel = stringResource(R.string.action_channel_list_item_edit)
		Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
			Column(modifier = Modifier.padding(bottom = 4.dp)) {
				// only the header opens the editor, so a tap next to the slider does not
				Row(
					verticalAlignment = Alignment.CenterVertically,
					modifier = Modifier
						.fillMaxWidth()
						.heightIn(min = 56.dp)
						.clickable(onClickLabel = editLabel) {
							openDialog(TGChannelEditDialogController(), TGDocumentContextAttributes.ATTRIBUTE_CHANNEL to item.channel)
						}
						.padding(start = 16.dp, end = 4.dp),
				) {
					Column(modifier = Modifier.weight(1f)) {
						Text(item.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
						Text(item.instrument, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
					}
					// a channel used by a track cannot be removed
					if (item.removable) {
						IconButton(onClick = {
							val remove = TGActionProcessor(findContext(), TGRemoveChannelAction.NAME)
							remove.setAttribute(TGDocumentContextAttributes.ATTRIBUTE_CHANNEL, item.channel)
							openDialog(
								TGConfirmDialogController(),
								TGConfirmDialogController.ATTRIBUTE_MESSAGE to removeQuestion,
								TGConfirmDialogController.ATTRIBUTE_RUNNABLE to Runnable { remove.process() },
							)
						}) {
							Icon(painterResource(R.drawable.ic_bar_delete_24dp), stringResource(R.string.action_channel_list_item_remove), tint = MaterialTheme.colorScheme.onSurfaceVariant)
						}
					}
					Icon(
						painterResource(R.drawable.ic_bar_chevron_right_24dp),
						contentDescription = null,
						tint = MaterialTheme.colorScheme.onSurfaceVariant,
						modifier = Modifier.padding(horizontal = 12.dp),
					)
				}
				VolumeSlider(item)
			}
		}
	}

	/** Applied when the finger is lifted, so a drag is a single undo step. */
	@Composable
	private fun VolumeSlider(item: ChannelItem) {
		var current by remember(item.volume) { mutableFloatStateOf(item.volume.toFloat()) }
		val volumeLabel = stringResource(R.string.channel_edit_dlg_volume_label).trimEnd(':', ' ')
		Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp)) {
			Text(
				volumeLabel,
				style = MaterialTheme.typography.labelMedium,
				modifier = Modifier.padding(end = 12.dp),
			)
			Slider(
				value = current,
				onValueChange = { current = it },
				onValueChangeFinished = {
					run(
						TGUpdateChannelAction.NAME,
						TGDocumentContextAttributes.ATTRIBUTE_CHANNEL to item.channel,
						TGUpdateChannelAction.ATTRIBUTE_VOLUME to current.roundToInt().coerceIn(0, 127).toShort(),
					)
				},
				valueRange = 0f..127f,
				modifier = Modifier.weight(1f).semantics {
					contentDescription = "$volumeLabel ${item.name}"
					stateDescription = current.roundToInt().toString()
				},
			)
			Text(
				current.roundToInt().toString(),
				style = MaterialTheme.typography.labelLarge,
				modifier = Modifier.padding(start = 12.dp),
			)
		}
	}

	private fun refresh() {
		runCatching {
			val documentManager = TGDocumentManager.getInstance(findContext())
			val song = documentManager.song ?: return
			val songManager = documentManager.songManager
			val instruments = MidiPlayer.getInstance(findContext()).instruments
			val percussion = getString(R.string.channel_edit_dlg_percussion_label)
			tracks = (0 until song.countTracks()).map { i ->
				val track = song.getTrack(i)
				TrackItem(track, track.name, songManager.getChannel(song, track.channelId)?.name ?: "", track.isMute, track.isSolo)
			}
			channels = songManager.getChannels(song).map { channel ->
				val program = channel.program.toInt()
				val instrument = when {
					channel.isPercussionChannel -> percussion
					instruments != null && program < instruments.size -> instruments[program].name
					else -> getString(R.string.channel_edit_dlg_program_value, program)
				}
				ChannelItem(channel, channel.name, instrument, channel.volume.toInt(), !songManager.isAnyTrackConnectedToChannel(song, channel.channelId))
			}
		}
	}

	private fun run(actionId: String, vararg attributes: Pair<String, Any>) {
		val processor = TGActionProcessor(findContext(), actionId)
		attributes.forEach { (key, value) -> processor.setAttribute(key, value) }
		processor.process()
		refresh()
	}

	private fun openDialog(controller: TGDialogController, vararg attributes: Pair<String, Any>) {
		run(
			TGOpenDialogAction.NAME,
			TGOpenDialogAction.ATTRIBUTE_DIALOG_CONTROLLER to controller,
			TGOpenDialogAction.ATTRIBUTE_DIALOG_ACTIVITY to findActivity(),
			*attributes,
		)
	}
}
