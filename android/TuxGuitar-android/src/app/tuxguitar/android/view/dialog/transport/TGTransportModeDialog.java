package app.tuxguitar.android.view.dialog.transport;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.graphics.BlendMode;
import android.graphics.BlendModeColorFilter;
import android.graphics.Color;

import androidx.compose.ui.platform.ComposeView;

import app.tuxguitar.android.R;
import app.tuxguitar.android.action.impl.caret.TGMoveToAction;
import app.tuxguitar.android.view.dialog.fragment.TGModalFragment;
import app.tuxguitar.android.view.tablature.TGSongViewController;
import app.tuxguitar.document.TGDocumentContextAttributes;
import app.tuxguitar.editor.action.TGActionProcessor;
import app.tuxguitar.player.base.MidiPlayer;
import app.tuxguitar.player.base.MidiPlayerMode;
import app.tuxguitar.song.models.TGBeat;
import app.tuxguitar.song.models.TGMeasureHeader;
import app.tuxguitar.song.models.TGSong;
import app.tuxguitar.song.models.TGTrack;

/**
 * Play mode dialog: simple / training mode and loop range.
 * The content is a Compose screen (TransportModeScreen.kt) editing a TransportModeState.
 */
public class TGTransportModeDialog extends TGModalFragment {

	private final int colorErr = Color.GRAY;
	private final int colorOK = Color.TRANSPARENT;

	protected TransportModeState state;

	public TGTransportModeDialog() {
		super(R.layout.view_transport_mode);
	}

	@Override
	public void onPostCreate(Bundle savedInstanceState) {
		this.createActionBar(true, false, R.string.transport_mode_dlg_title);
	}

	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater menuInflater) {
		menuInflater.inflate(R.menu.menu_modal_fragment_ok, menu);
		menu.findItem(R.id.action_ok).setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
			public boolean onMenuItemClick(MenuItem item) {
				if (!state.isValid()) {
					return true;
				}

				//------ Set transport mode ------

				Integer type = (state.isTrainer() ? MidiPlayerMode.TYPE_CUSTOM : MidiPlayerMode.TYPE_SIMPLE );
				Boolean loop = state.isLoopEnabled();
				Integer loopSHeader = state.getLoopSHeader();
				Integer loopEHeader = state.getLoopEHeader();

				// move caret to loop start if loop defined
				if (loop) {
					TGTrack track = MidiPlayer.getInstance(findContext()).getSong().getTrack(0);
					TGBeat beat = track.getMeasure(loopSHeader > 0 ? loopSHeader-1 : 0).getBeat(0);
					TGActionProcessor tgActionProcessor = new TGActionProcessor(findContext(), TGMoveToAction.NAME);
					tgActionProcessor.setAttribute(TGDocumentContextAttributes.ATTRIBUTE_BEAT, beat);
					tgActionProcessor.process();
				}

				MidiPlayerMode mode = MidiPlayer.getInstance(findContext()).getMode();
				mode.setType(type);
				mode.setLoop(loop);
				mode.setSimplePercent(state.getSimplePercent());
				mode.setCustomPercentFrom(state.getTrainerFrom());
				mode.setCustomPercentTo(state.getTrainerTo());
				mode.setCustomPercentIncrement(state.getTrainerIncrement());
				mode.setLoopSHeader(loopSHeader);
				mode.setLoopEHeader(loopEHeader);

				TGTransportModeDialog.this.close();

				return true;
			}
		});
	}

	@Override
	public void onPrepareOptionsMenu(Menu menu) {
		boolean ok = (this.state != null && this.state.isValid());
		menu.findItem(R.id.action_ok).setEnabled(ok);
		menu.findItem(R.id.action_ok).getIcon().setColorFilter(new BlendModeColorFilter(ok ? colorOK : colorErr, BlendMode.SRC_ATOP));
	}

	@SuppressLint("InflateParams")
	public void onPostInflateView() {
		TGSong song = MidiPlayer.getInstance(findContext()).getSong();
		int measureCount = song.countMeasureHeaders();
		int[] tempos = new int[measureCount];
		String[] markers = new String[measureCount];
		for (int i = 0; i < measureCount; i++) {
			TGMeasureHeader header = song.getMeasureHeader(i);
			tempos[i] = header.getTempo().getQuarterValue();
			markers[i] = (header.hasMarker() ? header.getMarker().getTitle() : null);
		}
		int caretMeasure = TGSongViewController.getInstance(findContext()).getCaret().getMeasure().getNumber();

		this.state = new TransportModeState(measureCount, tempos, markers, caretMeasure, new Runnable() {
			public void run() {
				// update menu with onPrepareOptionsMenu
				if (getActivity() != null) {
					getActivity().invalidateOptionsMenu();
				}
			}
		});

		MidiPlayerMode mode = MidiPlayer.getInstance(findContext()).getMode();
		this.state.load(
				mode.getType() == MidiPlayerMode.TYPE_CUSTOM,
				mode.isLoop(),
				mode.getSimplePercent(),
				mode.getCustomPercentFrom(),
				mode.getCustomPercentTo(),
				mode.getCustomPercentIncrement(),
				mode.getLoopSHeader(),
				mode.getLoopEHeader());

		TransportModeCompose.bind((ComposeView) this.getView().findViewById(R.id.transport_mode_dlg_compose), this.state);
	}
}
