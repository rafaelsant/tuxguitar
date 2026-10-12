package app.tuxguitar.android.view.dialog.transport;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.RadioButton;
import android.widget.TextView;
import android.graphics.BlendMode;
import android.graphics.BlendModeColorFilter;
import android.graphics.Color;

import app.tuxguitar.android.R;
import app.tuxguitar.android.action.impl.caret.TGMoveToAction;
import app.tuxguitar.android.view.dialog.fragment.TGModalFragment;
import app.tuxguitar.android.view.tablature.TGSongViewController;
import app.tuxguitar.android.view.util.TGNumberStepper;
import app.tuxguitar.document.TGDocumentContextAttributes;
import app.tuxguitar.editor.action.TGActionProcessor;
import app.tuxguitar.player.base.MidiPlayer;
import app.tuxguitar.player.base.MidiPlayerMode;
import app.tuxguitar.song.models.TGBeat;
import app.tuxguitar.song.models.TGMeasureHeader;
import app.tuxguitar.song.models.TGSong;
import app.tuxguitar.song.models.TGTrack;

public class TGTransportModeDialog extends TGModalFragment {

	protected static final int MIN_SELECTION = 1;
	protected static final int MAX_SELECTION = 500;

	protected RadioButton simple;
	protected RadioButton custom;

	protected TextView simpleLabel;
	protected TextView simplePercentLabel;
	protected TGNumberStepper simplePercent;
	protected TextView simplePercentHint;
	protected CheckBox simpleLoop;

	protected TextView customLabel;
	protected TextView customFromLabel;
	protected TGNumberStepper customFrom;
	protected TextView customFromHint;
	protected TextView customToLabel;
	protected TGNumberStepper customTo;
	protected TextView customToHint;
	protected TextView customIncrementLabel;
	protected TGNumberStepper customIncrement;
	protected TextView customIncrementHint;
	protected View[] customIncrementPresets;
	protected TextView customError;

	protected TextView loopLabel;
	protected TextView loopFromLabel;
	protected TGNumberStepper loopFrom;
	protected View loopFromCurrent;
	protected TextView loopFromHint;
	protected TextView loopToLabel;
	protected TGNumberStepper loopTo;
	protected View loopToCurrent;
	protected TextView loopToHint;
	protected TextView loopError;

	private final int colorErr = Color.GRAY;
	private final int colorOK = Color.TRANSPARENT;

	protected int measureCount;
	protected boolean ok;

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
				if(!updateState()) {
					return true;
				}

				//------ Set transport mode ------

				Integer type = (custom.isChecked() ? MidiPlayerMode.TYPE_CUSTOM : MidiPlayerMode.TYPE_SIMPLE );
				Boolean loop = (type == MidiPlayerMode.TYPE_CUSTOM || (type == MidiPlayerMode.TYPE_SIMPLE && simpleLoop.isChecked()));
				Integer loopSHeader = (loopFrom.getValue() <= 1 ? -1 : loopFrom.getValue());
				Integer loopEHeader = (loopTo.getValue() >= measureCount ? -1 : loopTo.getValue());

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
				mode.setSimplePercent(simplePercent.getValue());
				mode.setCustomPercentFrom(customFrom.getValue());
				mode.setCustomPercentTo(customTo.getValue());
				mode.setCustomPercentIncrement(customIncrement.getValue());
				mode.setLoopSHeader(loopSHeader);
				mode.setLoopEHeader(loopEHeader);

				TGTransportModeDialog.this.close();

				return true;
			}
		});
	}

	@Override
	public void onPrepareOptionsMenu(Menu menu) {
		menu.findItem(R.id.action_ok).setEnabled(this.ok);
		menu.findItem(R.id.action_ok).getIcon().setColorFilter(new BlendModeColorFilter(ok ? colorOK : colorErr, BlendMode.SRC_ATOP));
	}

	@SuppressLint("InflateParams")
	public void onPostInflateView() {
		View view = this.getView();
		this.measureCount = getSong().countMeasureHeaders();

		//---Simple/Trainer selector---
		this.simple = (RadioButton) view.findViewById(R.id.transport_mode_dlg_simple);
		this.custom = (RadioButton) view.findViewById(R.id.transport_mode_dlg_trainer);

		//---Simple---
		this.simpleLabel = (TextView) view.findViewById(R.id.transport_mode_dlg_simple_label);
		this.simplePercentLabel = (TextView) view.findViewById(R.id.transport_mode_dlg_simple_tempo_percent_label);
		this.simplePercent = new TGNumberStepper(view.findViewById(R.id.transport_mode_dlg_simple_tempo_percent_value), MIN_SELECTION, MAX_SELECTION);
		this.simplePercentHint = (TextView) view.findViewById(R.id.transport_mode_dlg_simple_tempo_percent_hint);
		this.simpleLoop = (CheckBox) view.findViewById(R.id.transport_mode_dlg_simple_loop);

		//---Trainer---
		this.customLabel = (TextView) view.findViewById(R.id.transport_mode_dlg_trainer_label);
		this.customFromLabel = (TextView) view.findViewById(R.id.transport_mode_dlg_trainer_tempo_percent_from_label);
		this.customFrom = new TGNumberStepper(view.findViewById(R.id.transport_mode_dlg_trainer_tempo_percent_from_value), MIN_SELECTION, MAX_SELECTION);
		this.customFromHint = (TextView) view.findViewById(R.id.transport_mode_dlg_trainer_tempo_percent_from_hint);
		this.customToLabel = (TextView) view.findViewById(R.id.transport_mode_dlg_trainer_tempo_percent_to_label);
		this.customTo = new TGNumberStepper(view.findViewById(R.id.transport_mode_dlg_trainer_tempo_percent_to_value), MIN_SELECTION, MAX_SELECTION);
		this.customToHint = (TextView) view.findViewById(R.id.transport_mode_dlg_trainer_tempo_percent_to_hint);
		this.customIncrementLabel = (TextView) view.findViewById(R.id.transport_mode_dlg_trainer_tempo_increment_label);
		this.customIncrement = new TGNumberStepper(view.findViewById(R.id.transport_mode_dlg_trainer_tempo_increment_value), MIN_SELECTION, MAX_SELECTION);
		this.customIncrementHint = (TextView) view.findViewById(R.id.transport_mode_dlg_trainer_tempo_increment_hint);
		this.customIncrementPresets = new View[] {
			this.createIncrementPreset(view, R.id.transport_mode_dlg_trainer_tempo_increment_preset_1, 1),
			this.createIncrementPreset(view, R.id.transport_mode_dlg_trainer_tempo_increment_preset_2, 2),
			this.createIncrementPreset(view, R.id.transport_mode_dlg_trainer_tempo_increment_preset_5, 5),
			this.createIncrementPreset(view, R.id.transport_mode_dlg_trainer_tempo_increment_preset_10, 10),
		};
		this.customError = (TextView) view.findViewById(R.id.transport_mode_dlg_trainer_error);

		//--- Loop Range ---
		this.loopLabel = (TextView) view.findViewById(R.id.transport_mode_dlg_loop_range_label);
		this.loopFromLabel = (TextView) view.findViewById(R.id.transport_mode_dlg_loop_range_from_label);
		this.loopFrom = new TGNumberStepper(view.findViewById(R.id.transport_mode_dlg_loop_range_from_value), 1, this.measureCount);
		this.loopFromCurrent = view.findViewById(R.id.transport_mode_dlg_loop_range_from_current);
		this.loopFromHint = (TextView) view.findViewById(R.id.transport_mode_dlg_loop_range_from_hint);
		this.loopToLabel = (TextView) view.findViewById(R.id.transport_mode_dlg_loop_range_to_label);
		this.loopTo = new TGNumberStepper(view.findViewById(R.id.transport_mode_dlg_loop_range_to_value), 1, this.measureCount);
		this.loopToCurrent = view.findViewById(R.id.transport_mode_dlg_loop_range_to_current);
		this.loopToHint = (TextView) view.findViewById(R.id.transport_mode_dlg_loop_range_to_hint);
		this.loopError = (TextView) view.findViewById(R.id.transport_mode_dlg_loop_range_error);

		//--- Initial values ---
		MidiPlayerMode mode = MidiPlayer.getInstance(findContext()).getMode();

		this.simple.setChecked(mode.getType() == MidiPlayerMode.TYPE_SIMPLE);
		this.custom.setChecked(mode.getType() == MidiPlayerMode.TYPE_CUSTOM);
		this.simpleLoop.setChecked(mode.isLoop());
		this.simplePercent.setValue(mode.getSimplePercent());
		this.customFrom.setValue(mode.getCustomPercentFrom());
		this.customTo.setValue(mode.getCustomPercentTo());
		this.customIncrement.setValue(mode.getCustomPercentIncrement());
		this.loopFrom.setValue(mode.getLoopSHeader() > 0 ? mode.getLoopSHeader() : 1);
		this.loopTo.setValue(mode.getLoopEHeader() > 0 ? mode.getLoopEHeader() : this.measureCount);

		//--- Listeners ---
		TGNumberStepper.Listener updateListener = new TGNumberStepper.Listener() {
			public void onValueChanged(TGNumberStepper stepper) {
				updateState();
			}
		};
		this.simplePercent.setListener(updateListener);
		this.customFrom.setListener(updateListener);
		this.customTo.setListener(updateListener);
		this.customIncrement.setListener(updateListener);
		this.loopTo.setListener(updateListener);
		this.loopFrom.setListener(new TGNumberStepper.Listener() {
			public void onValueChanged(TGNumberStepper stepper) {
				// keep the range valid when the start is moved past the end
				if (loopFrom.isValid() && loopTo.isValid() && loopFrom.getValue() > loopTo.getValue()) {
					loopTo.setValue(loopFrom.getValue());
				}
				updateState();
			}
		});

		this.loopFromCurrent.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				loopFrom.changeValue(getCaretMeasureNumber());
			}
		});
		this.loopToCurrent.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				loopTo.changeValue(getCaretMeasureNumber());
			}
		});

		OnCheckedChangeListener checkedListener = new OnCheckedChangeListener() {
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				updateState();
			}
		};
		this.custom.setOnCheckedChangeListener(checkedListener);
		this.simpleLoop.setOnCheckedChangeListener(checkedListener);

		this.updateState();
	}

	private View createIncrementPreset(View view, int id, final int value) {
		View button = view.findViewById(id);
		button.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				customIncrement.changeValue(value);
			}
		});
		return button;
	}

	/**
	 * Refreshes enabled states, hints and error messages.
	 * Only the sections in use are validated, so the training values never block saving the simple mode.
	 */
	protected boolean updateState() {
		boolean isTrainer = this.custom.isChecked();
		boolean isLoop = (isTrainer || this.simpleLoop.isChecked());

		this.enableTrainerMode(isTrainer);
		this.enableLoop(isLoop);

		boolean ok = true;
		if (isTrainer) {
			int errorId = 0;
			if (!this.customFrom.isValid() || !this.customTo.isValid() || !this.customIncrement.isValid()) {
				ok = false;
			} else if (this.customFrom.getValue() >= this.customTo.getValue()) {
				errorId = R.string.transport_mode_dlg_trainer_error_range;
			} else if (this.customIncrement.getValue() > (this.customTo.getValue() - this.customFrom.getValue())) {
				errorId = R.string.transport_mode_dlg_trainer_error_increment;
			}
			ok = (ok && errorId == 0);
			this.showError(this.customError, errorId);
		} else {
			ok = this.simplePercent.isValid();
			this.showError(this.customError, 0);
		}

		if (isLoop) {
			int errorId = 0;
			if (!this.loopFrom.isValid() || !this.loopTo.isValid()) {
				ok = false;
			} else if (this.loopTo.getValue() < this.loopFrom.getValue()) {
				errorId = R.string.transport_mode_dlg_loop_range_error;
			}
			ok = (ok && errorId == 0);
			this.showError(this.loopError, errorId);
		} else {
			this.showError(this.loopError, 0);
		}

		this.updateHints(isLoop);

		this.ok = ok;
		// update menu with onPrepareOptionsMenu
		getActivity().invalidateOptionsMenu();
		return ok;
	}

	private void updateHints(boolean isLoop) {
		// tempo of the measure where playback starts, used to show percentages as BPM
		int startMeasure = (isLoop && this.loopFrom.isValid() ? this.loopFrom.getValue() : 1);
		int baseTempo = getSong().getMeasureHeader(startMeasure - 1).getTempo().getQuarterValue();

		this.simplePercentHint.setText(this.getTempoHint(baseTempo, this.simplePercent.getValue()));
		this.customFromHint.setText(this.getTempoHint(baseTempo, this.customFrom.getValue()));
		this.customToHint.setText(this.getTempoHint(baseTempo, this.customTo.getValue()));
		this.customIncrementHint.setText(getActivity().getString(R.string.transport_mode_dlg_trainer_tempo_increment_hint,
				this.customIncrement.getValue(), Math.round(baseTempo * this.customIncrement.getValue() / 100f)));

		this.setHint(this.loopFromHint, this.getMarkerTitle(this.loopFrom.getValue()));
		String toHint = this.getMarkerTitle(this.loopTo.getValue());
		if (this.loopTo.getValue() == this.measureCount) {
			String endHint = getActivity().getString(R.string.transport_mode_dlg_loop_range_end_hint);
			toHint = (toHint != null ? (toHint + " · " + endHint) : endHint);
		}
		this.setHint(this.loopToHint, toHint);
	}

	private String getTempoHint(int baseTempo, int percent) {
		return getActivity().getString(R.string.transport_mode_dlg_tempo_bpm_hint, percent, Math.round(baseTempo * percent / 100f));
	}

	private String getMarkerTitle(int measure) {
		if (measure < 1 || measure > this.measureCount) {
			return null;
		}
		TGMeasureHeader header = getSong().getMeasureHeader(measure - 1);
		return (header.hasMarker() ? header.getMarker().getTitle() : null);
	}

	private void setHint(TextView hint, String text) {
		hint.setText(text);
		hint.setVisibility(text != null ? View.VISIBLE : View.GONE);
	}

	private void showError(TextView error, int errorId) {
		if (errorId != 0) {
			error.setText(errorId);
			error.setVisibility(View.VISIBLE);
		} else {
			error.setVisibility(View.GONE);
		}
	}

	private int getCaretMeasureNumber() {
		return TGSongViewController.getInstance(findContext()).getCaret().getMeasure().getNumber();
	}

	private TGSong getSong() {
		return MidiPlayer.getInstance(findContext()).getSong();
	}

	private void enableTrainerMode(boolean isTrainer) {
		this.simpleLabel.setEnabled(!isTrainer);
		this.simplePercentLabel.setEnabled(!isTrainer);
		this.simplePercent.setEnabled(!isTrainer);
		this.simplePercentHint.setEnabled(!isTrainer);
		this.simpleLoop.setEnabled(!isTrainer);

		this.customLabel.setEnabled(isTrainer);
		this.customFromLabel.setEnabled(isTrainer);
		this.customFrom.setEnabled(isTrainer);
		this.customFromHint.setEnabled(isTrainer);
		this.customToLabel.setEnabled(isTrainer);
		this.customTo.setEnabled(isTrainer);
		this.customToHint.setEnabled(isTrainer);
		this.customIncrementLabel.setEnabled(isTrainer);
		this.customIncrement.setEnabled(isTrainer);
		this.customIncrementHint.setEnabled(isTrainer);
		for (View preset : this.customIncrementPresets) {
			preset.setEnabled(isTrainer);
		}
	}

	private void enableLoop(boolean enable) {
		this.loopLabel.setEnabled(enable);
		this.loopFromLabel.setEnabled(enable);
		this.loopFrom.setEnabled(enable);
		this.loopFromCurrent.setEnabled(enable);
		this.loopFromHint.setEnabled(enable);
		this.loopToLabel.setEnabled(enable);
		this.loopTo.setEnabled(enable);
		this.loopToCurrent.setEnabled(enable);
		this.loopToHint.setEnabled(enable);
	}
}
