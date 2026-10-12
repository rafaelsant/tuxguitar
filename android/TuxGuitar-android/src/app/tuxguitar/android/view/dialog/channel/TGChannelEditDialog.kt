package app.tuxguitar.android.view.dialog.channel

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.tuxguitar.android.R
import app.tuxguitar.android.view.compose.TGCheckRow
import app.tuxguitar.android.view.compose.TGDropdownField
import app.tuxguitar.android.view.compose.TGForm
import app.tuxguitar.android.view.compose.TGOption
import app.tuxguitar.android.view.compose.TGTextField
import app.tuxguitar.android.view.dialog.fragment.TGComposeModalFragment
import app.tuxguitar.document.TGDocumentContextAttributes
import app.tuxguitar.editor.TGEditorManager
import app.tuxguitar.editor.action.TGActionProcessor
import app.tuxguitar.editor.action.channel.TGUpdateChannelAction
import app.tuxguitar.event.TGEventListener
import app.tuxguitar.player.base.MidiPlayer
import app.tuxguitar.song.managers.TGSongManager
import app.tuxguitar.song.models.TGChannel
import app.tuxguitar.song.models.TGSong
import kotlin.math.roundToInt

/** Edits a channel of the instruments screen. Each change is applied (and heard) right away, so there is no OK. */
class TGChannelEditDialog : TGComposeModalFragment(R.string.channel_edit_dlg_title) {

	private class Levels(val volume: Int, val balance: Int, val reverb: Int, val chorus: Int, val phaser: Int, val tremolo: Int)

	private var name by mutableStateOf("")
	private var bank by mutableStateOf(0)
	private var program by mutableStateOf(0)
	private var percussion by mutableStateOf(false)
	private var percussionEnabled by mutableStateOf(true)
	private var levels by mutableStateOf(Levels(0, 0, 0, 0, 0, 0))
	private var instruments = listOf<String>()

	private var eventListener: TGEventListener? = null

	private val channel: TGChannel
		get() = getAttribute(TGDocumentContextAttributes.ATTRIBUTE_CHANNEL)

	override fun hasOk(): Boolean = false

	override fun onOk() {}

	override fun initState() {
		instruments = MidiPlayer.getInstance(findContext()).instruments?.take(128)?.map { it.name } ?: listOf()
		updateItems()
	}

	/** Reads the channel into the state; also called by [TGChannelEditEventListener] when the song changes. */
	fun updateItems() {
		val songManager: TGSongManager = getAttribute(TGDocumentContextAttributes.ATTRIBUTE_SONG_MANAGER)
		val song: TGSong = getAttribute(TGDocumentContextAttributes.ATTRIBUTE_SONG)
		val channel = channel
		name = channel.name
		bank = channel.bank.toInt()
		program = channel.program.toInt()
		percussion = channel.isPercussionChannel
		percussionEnabled = !songManager.isAnyTrackConnectedToChannel(song, channel.channelId) &&
			(!songManager.isAnyPercussionChannel(song) || percussion)
		levels = Levels(
			channel.volume.toInt(), channel.balance.toInt(), channel.reverb.toInt(),
			channel.chorus.toInt(), channel.phaser.toInt(), channel.tremolo.toInt(),
		)
	}

	override fun onShowView() {
		super.onShowView()
		val listener = TGChannelEditEventListener(this)
		eventListener = listener
		TGEditorManager.getInstance(findContext()).addUpdateListener(listener)
	}

	override fun onHideView() {
		eventListener?.let { TGEditorManager.getInstance(findContext()).removeUpdateListener(it) }
		eventListener = null
		if (name != channel.name) {
			update()
		}
		super.onHideView()
	}

	@Composable
	override fun Content() {
		TGForm {
			TGTextField(stringResource(R.string.channel_edit_dlg_name_label).trimEnd(':', ' '), name, { name = it })

			val resources = LocalContext.current.resources
			TGDropdownField(
				stringResource(R.string.channel_edit_dlg_program_label).trimEnd(':', ' '),
				(0 until 128).map { i ->
					TGOption(i, if (!percussion && i < instruments.size) instruments[i] else resources.getString(R.string.channel_edit_dlg_program_value, i))
				},
				program,
				{ value ->
					program = value
					update(TGUpdateChannelAction.ATTRIBUTE_PROGRAM to value.toShort())
				},
			)
			TGDropdownField(
				stringResource(R.string.channel_edit_dlg_bank_label).trimEnd(':', ' '),
				(0..maxOf(127, bank)).map { TGOption(it, resources.getString(R.string.channel_edit_dlg_bank_value, it)) },
				bank,
				{ value ->
					bank = value
					update(TGUpdateChannelAction.ATTRIBUTE_BANK to value.toShort())
				},
				enabled = !percussion,
			)
			TGCheckRow(stringResource(R.string.channel_edit_dlg_percussion_label), percussion, { checked ->
				percussion = checked
				update(
					TGUpdateChannelAction.ATTRIBUTE_BANK to (if (checked) TGChannel.DEFAULT_PERCUSSION_BANK else TGChannel.DEFAULT_BANK),
					TGUpdateChannelAction.ATTRIBUTE_PROGRAM to (if (checked) TGChannel.DEFAULT_PERCUSSION_PROGRAM else TGChannel.DEFAULT_PROGRAM),
				)
			}, enabled = percussionEnabled)

			val l = levels
			LevelSlider(R.string.channel_edit_dlg_volume_label, l.volume, TGUpdateChannelAction.ATTRIBUTE_VOLUME)
			LevelSlider(R.string.channel_edit_dlg_balance_label, l.balance, TGUpdateChannelAction.ATTRIBUTE_BALANCE)
			LevelSlider(R.string.channel_edit_dlg_reverb_label, l.reverb, TGUpdateChannelAction.ATTRIBUTE_REVERB)
			LevelSlider(R.string.channel_edit_dlg_chorus_label, l.chorus, TGUpdateChannelAction.ATTRIBUTE_CHORUS)
			LevelSlider(R.string.channel_edit_dlg_phaser_label, l.phaser, TGUpdateChannelAction.ATTRIBUTE_PHASER)
			LevelSlider(R.string.channel_edit_dlg_tremolo_label, l.tremolo, TGUpdateChannelAction.ATTRIBUTE_TREMOLO)
		}
	}

	/** 0..127 level; applied when the finger is lifted, so a drag is a single undo step. */
	@Composable
	private fun LevelSlider(labelId: Int, value: Int, attribute: String) {
		var current by remember(value) { mutableFloatStateOf(value.toFloat()) }
		Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
			Row {
				Text(stringResource(labelId).trimEnd(':', ' '), modifier = Modifier.weight(1f))
				Text(current.roundToInt().toString(), style = MaterialTheme.typography.labelLarge)
			}
			Slider(
				value = current,
				onValueChange = { current = it },
				onValueChangeFinished = { update(attribute to current.roundToInt().coerceIn(0, 127).toShort()) },
				valueRange = 0f..127f,
			)
		}
	}

	private fun update(vararg attributes: Pair<String, Any>) {
		val processor = TGActionProcessor(findContext(), TGUpdateChannelAction.NAME)
		processor.setAttribute(TGDocumentContextAttributes.ATTRIBUTE_CHANNEL, channel)
		processor.setAttribute(TGUpdateChannelAction.ATTRIBUTE_NAME, name)
		attributes.forEach { (key, value) -> processor.setAttribute(key, value) }
		processor.process()
	}
}
