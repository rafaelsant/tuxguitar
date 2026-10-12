package app.tuxguitar.android.view.dialog.info

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import app.tuxguitar.android.R
import app.tuxguitar.android.view.compose.TGCheckRow
import app.tuxguitar.android.view.compose.TGDropdownField
import app.tuxguitar.android.view.compose.TGForm
import app.tuxguitar.android.view.compose.TGFormSection
import app.tuxguitar.android.view.compose.TGOption
import app.tuxguitar.android.view.compose.TGRadioList
import app.tuxguitar.android.view.compose.TGTextField
import app.tuxguitar.android.view.dialog.fragment.TGComposeModalFragment
import app.tuxguitar.document.TGDocumentContextAttributes
import app.tuxguitar.editor.action.TGActionProcessor
import app.tuxguitar.editor.action.composition.TGChangeInfoAction
import app.tuxguitar.song.models.TGSong

class TGSongInfoDialog : TGComposeModalFragment(R.string.song_properties_dlg_title) {

	private var name by mutableStateOf("")
	private var artist by mutableStateOf("")
	private var album by mutableStateOf("")
	private var author by mutableStateOf("")
	private var date by mutableStateOf("")
	private var copyright by mutableStateOf("")
	private var writer by mutableStateOf("")
	private var transcriber by mutableStateOf("")
	private var comments by mutableStateOf("")

	private val song: TGSong
		get() = getAttribute(TGDocumentContextAttributes.ATTRIBUTE_SONG)

	override fun initState() {
		name = song.name ?: ""
		artist = song.artist ?: ""
		album = song.album ?: ""
		author = song.author ?: ""
		date = song.date ?: ""
		copyright = song.copyright ?: ""
		writer = song.writer ?: ""
		transcriber = song.transcriber ?: ""
		comments = song.comments ?: ""
	}

	@Composable
	override fun Content() {
		TGForm {
			TGTextField(stringResource(R.string.song_properties_dlg_name_label), name, { name = it })
			TGTextField(stringResource(R.string.song_properties_dlg_artist_label), artist, { artist = it })
			TGTextField(stringResource(R.string.song_properties_dlg_album_label), album, { album = it })
			TGTextField(stringResource(R.string.song_properties_dlg_author_label), author, { author = it })
			TGTextField(stringResource(R.string.song_properties_dlg_date_label), date, { date = it })
			TGTextField(stringResource(R.string.song_properties_dlg_copyright_label), copyright, { copyright = it })
			TGTextField(stringResource(R.string.song_properties_dlg_writer_label), writer, { writer = it })
			TGTextField(stringResource(R.string.song_properties_dlg_transcriber_label), transcriber, { transcriber = it })
			TGTextField(stringResource(R.string.song_properties_dlg_comments_label), comments, { comments = it }, singleLine = false)
		}
	}

	override fun onOk() {
		val processor = TGActionProcessor(findContext(), TGChangeInfoAction.NAME)
		processor.setAttribute(TGDocumentContextAttributes.ATTRIBUTE_SONG, song)
		processor.setAttribute(TGChangeInfoAction.ATTRIBUTE_NAME, name)
		processor.setAttribute(TGChangeInfoAction.ATTRIBUTE_ARTIST, artist)
		processor.setAttribute(TGChangeInfoAction.ATTRIBUTE_ALBUM, album)
		processor.setAttribute(TGChangeInfoAction.ATTRIBUTE_AUTHOR, author)
		processor.setAttribute(TGChangeInfoAction.ATTRIBUTE_DATE, date)
		processor.setAttribute(TGChangeInfoAction.ATTRIBUTE_COPYRIGHT, copyright)
		processor.setAttribute(TGChangeInfoAction.ATTRIBUTE_WRITER, writer)
		processor.setAttribute(TGChangeInfoAction.ATTRIBUTE_TRANSCRIBER, transcriber)
		processor.setAttribute(TGChangeInfoAction.ATTRIBUTE_COMMENTS, comments)
		processor.processOnNewThread()
	}
}
