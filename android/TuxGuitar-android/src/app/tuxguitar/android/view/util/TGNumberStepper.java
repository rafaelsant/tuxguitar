package app.tuxguitar.android.view.util;

import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import app.tuxguitar.android.R;

/**
 * Wraps a view_number_stepper layout: a "-" button, an editable number and a "+" button.
 * Holding a button repeats the step, so large ranges can be covered without scrolling a list.
 */
public class TGNumberStepper {

	public interface Listener {
		void onValueChanged(TGNumberStepper stepper);
	}

	private static final long REPEAT_DELAY = 60;

	private final EditText valueText;
	private final Button decreaseButton;
	private final Button increaseButton;
	private final Handler handler;

	private int minimum;
	private int maximum;
	private int value;
	private boolean valid;
	private boolean updating;
	private Listener listener;
	private Runnable repeater;

	public TGNumberStepper(View root, int minimum, int maximum) {
		this.valueText = (EditText) root.findViewById(R.id.number_stepper_value);
		this.decreaseButton = (Button) root.findViewById(R.id.number_stepper_decrease);
		this.increaseButton = (Button) root.findViewById(R.id.number_stepper_increase);
		this.handler = new Handler(Looper.getMainLooper());
		this.minimum = minimum;
		this.maximum = maximum;
		this.value = minimum;
		this.valid = true;

		this.installStepButton(this.decreaseButton, -1);
		this.installStepButton(this.increaseButton, 1);

		this.valueText.addTextChangedListener(new TextWatcher() {
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {
			}
			public void onTextChanged(CharSequence s, int start, int before, int count) {
			}
			public void afterTextChanged(Editable s) {
				if(!TGNumberStepper.this.updating) {
					onTextEdited(s.toString());
				}
			}
		});
		this.valueText.setOnFocusChangeListener(new View.OnFocusChangeListener() {
			public void onFocusChange(View v, boolean hasFocus) {
				if(!hasFocus) {
					commitText();
				}
			}
		});
		this.valueText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
			public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
				if( actionId == EditorInfo.IME_ACTION_DONE ) {
					commitText();
				}
				return false;
			}
		});
		this.updateText();
	}

	private void installStepButton(Button button, final int direction) {
		button.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				step(direction);
			}
		});
		button.setOnLongClickListener(new View.OnLongClickListener() {
			public boolean onLongClick(View v) {
				startRepeat(direction);
				return true;
			}
		});
		button.setOnTouchListener(new View.OnTouchListener() {
			public boolean onTouch(View v, MotionEvent event) {
				int action = event.getActionMasked();
				if( action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL ) {
					stopRepeat();
				}
				return false;
			}
		});
	}

	private void startRepeat(final int direction) {
		this.stopRepeat();
		this.repeater = new Runnable() {
			public void run() {
				if( step(direction) ) {
					handler.postDelayed(this, REPEAT_DELAY);
				}
			}
		};
		this.handler.post(this.repeater);
	}

	private void stopRepeat() {
		if( this.repeater != null ) {
			this.handler.removeCallbacks(this.repeater);
			this.repeater = null;
		}
	}

	private boolean step(int direction) {
		int next = this.clamp(this.value + direction);
		if( next == this.value && this.valid ) {
			return false;
		}
		this.setValueAndNotify(next);
		return true;
	}

	private void onTextEdited(String text) {
		Integer parsed = parse(text);
		this.valid = (parsed != null && parsed >= this.minimum && parsed <= this.maximum);
		if( this.valid ) {
			this.value = parsed;
			this.valueText.setError(null);
			this.updateButtons();
		} else {
			this.valueText.setError(this.valueText.getContext().getString(R.string.number_stepper_error_range, this.minimum, this.maximum));
		}
		this.notifyListener();
	}

	private void commitText() {
		Integer parsed = parse(this.valueText.getText().toString());
		this.setValueAndNotify(parsed != null ? this.clamp(parsed) : this.value);
	}

	private void setValueAndNotify(int value) {
		this.value = value;
		this.valid = true;
		this.updateText();
		this.notifyListener();
	}

	private void updateText() {
		this.updating = true;
		this.valueText.setText(Integer.toString(this.value));
		this.valueText.setError(null);
		this.updating = false;
		this.updateButtons();
	}

	private void updateButtons() {
		this.decreaseButton.setEnabled(this.isEnabled() && this.value > this.minimum);
		this.increaseButton.setEnabled(this.isEnabled() && this.value < this.maximum);
	}

	private void notifyListener() {
		if( this.listener != null ) {
			this.listener.onValueChanged(this);
		}
	}

	private int clamp(int value) {
		return Math.max(this.minimum, Math.min(this.maximum, value));
	}

	private static Integer parse(String text) {
		try {
			return Integer.valueOf(text.trim());
		} catch (NumberFormatException e) {
			return null;
		}
	}

	public int getValue() {
		return this.value;
	}

	/** Sets the value without notifying the listener. */
	public void setValue(int value) {
		this.value = this.clamp(value);
		this.valid = true;
		this.updateText();
	}

	/** Sets the value and notifies the listener, as if the user had changed it. */
	public void changeValue(int value) {
		this.setValueAndNotify(this.clamp(value));
	}

	public boolean isValid() {
		return this.valid;
	}

	public void setListener(Listener listener) {
		this.listener = listener;
	}

	public boolean isEnabled() {
		return this.valueText.isEnabled();
	}

	public void setEnabled(boolean enabled) {
		if(!enabled) {
			this.stopRepeat();
		}
		this.valueText.setEnabled(enabled);
		this.updateButtons();
	}
}
