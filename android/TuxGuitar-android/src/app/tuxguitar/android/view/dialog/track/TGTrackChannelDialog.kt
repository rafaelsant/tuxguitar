package app.tuxguitar.android.view.dialog.track

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import app.tuxguitar.android.R
import app.tuxguitar.android.view.compose.TGForm
import app.tuxguitar.android.view.compose.TGNumberField
import app.tuxguitar.android.view.compose.TGTextField
import app.tuxguitar.android.view.dialog.fragment.TGComposeModalFragment
import app.tuxguitar.document.TGDocumentContextAttributes
import app.tuxguitar.editor.action.TGActionProcessor
import app.tuxguitar.song.models.TGSong
import app.tuxguitar.song.models.TGTrack
import app.tuxguitar.android.action.impl.gui.TGOpenFragmentAction
import app.tuxguitar.android.fragment.impl.TGChannelListFragmentController
import app.tuxguitar.android.view.compose.TGActionRow
import app.tuxguitar.android.view.compose.TGDropdownField
import app.tuxguitar.android.view.compose.TGOption
import app.tuxguitar.editor.action.track.TGSetTrackChannelAction
import app.tuxguitar.song.managers.TGSongManager
import app.tuxguitar.song.models.TGChannel

/** Instrument (channel) of a track; the channels themselves are edited in the instruments screen. */
class TGTrackChannelDialog : TGComposeModalFragment(R.string.channel_edit_dlg_title) {

	private var channels by mutableStateOf(listOf<TGChannel>())
	private var selected by mutableStateOf<TGChannel?>(null)

	private val songManager: TGSongManager
		get() = getAttribute(TGDocumentContextAttributes.ATTRIBUTE_SONG_MANAGER)

	private val song: TGSong
		get() = getAttribute(TGDocumentContextAttributes.ATTRIBUTE_SONG)

	private val track: TGTrack
		get() = getAttribute(TGDocumentContextAttributes.ATTRIBUTE_TRACK)

	// refreshed each time, as the channels may change in the instruments screen
	override fun onShowView() {
		super.onShowView()
		channels = songManager.getChannels(song).toList()
		if (selected == null || selected !in channels) {
			selected = songManager.getChannel(song, track.channelId)
		}
	}

	@Composable
	override fun Content() {
		TGForm {
			TGDropdownField(
				stringResource(R.string.track_channel_dlg_channel_label),
				listOf(TGOption<TGChannel?>(null, stringResource(R.string.global_spinner_select_option))) + channels.map { TGOption<TGChannel?>(it, it.name) },
				selected, { selected = it },
			)
			TGActionRow(stringResource(R.string.action_view_channel_list)) {
				val processor = TGActionProcessor(findContext(), TGOpenFragmentAction.NAME)
				processor.setAttribute(TGOpenFragmentAction.ATTRIBUTE_CONTROLLER, TGChannelListFragmentController.getInstance(findContext()))
				processor.setAttribute(TGOpenFragmentAction.ATTRIBUTE_ACTIVITY, findActivity())
				processor.process()
			}
		}
	}

	override fun onOk() {
		val processor = TGActionProcessor(findContext(), TGSetTrackChannelAction.NAME)
		processor.setAttribute(TGDocumentContextAttributes.ATTRIBUTE_TRACK, track)
		processor.setAttribute(TGDocumentContextAttributes.ATTRIBUTE_CHANNEL, selected)
		processor.processOnNewThread()
	}
}
