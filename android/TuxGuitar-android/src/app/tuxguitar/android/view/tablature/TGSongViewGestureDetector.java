package app.tuxguitar.android.view.tablature;

import app.tuxguitar.android.action.impl.caret.TGMoveToAxisPositionAction;
import app.tuxguitar.android.application.TGApplicationUtil;
import app.tuxguitar.android.view.practice.TGAppMode;
import app.tuxguitar.android.view.practice.TGAppModeController;
import app.tuxguitar.android.view.practice.TGPracticeSession;
import app.tuxguitar.util.TGContext;
import app.tuxguitar.editor.action.TGActionProcessor;

import android.content.Context;
import androidx.core.view.GestureDetectorCompat;
import android.view.GestureDetector;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;

public class TGSongViewGestureDetector extends GestureDetector.SimpleOnGestureListener {

	private GestureDetectorCompat gestureDetector;
	private TGSongViewScaleGestureDetector songViewScaleGestureDetector;
	private TGSongView songView;
	// measures of a loop being selected by long press and drag (practice mode), -1 when none
	private int loopSelectionStart = -1;
	private int loopSelectionEnd = -1;

	public TGSongViewGestureDetector(Context context, TGSongView songView) {
		this.gestureDetector = new GestureDetectorCompat(context, this);
		this.songViewScaleGestureDetector = new TGSongViewScaleGestureDetector(context, songView);
		this.songView = songView;
	}

	public boolean processTouchEvent(MotionEvent event) {
		if( this.loopSelectionStart > 0 ) {
			this.processLoopSelection(event);
			return true;
		}
		this.songViewScaleGestureDetector.processTouchEvent(event);
		if(!this.songViewScaleGestureDetector.isInProgress() ) {
			this.gestureDetector.onTouchEvent(event);
		}
		return true;
	}

	@Override
	public void onLongPress(MotionEvent e) {
		TGContext context = TGApplicationUtil.findContext(this.songView);
		TGAppMode mode = TGAppModeController.getInstance(context).getMode();
		if( mode == TGAppMode.PRACTICE ) {
			// practice: long press on a measure and drag over the following ones to loop them
			int measure = this.songView.getController().getAxisSelector().findMeasureNumberAt(e.getX(), e.getY());
			if( measure > 0 ) {
				this.loopSelectionStart = measure;
				this.loopSelectionEnd = measure;
				this.songView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
				TGPracticeSession.getInstance(context).setLoopRange(measure, measure);
			}
			return;
		}
		// the smart menu edits the song, so it only opens in edit mode
		this.moveToAxisPosition(e.getX(), e.getY(), mode == TGAppMode.EDIT);
	}

	private void processLoopSelection(MotionEvent event) {
		int action = event.getActionMasked();
		if( action == MotionEvent.ACTION_MOVE ) {
			int measure = this.songView.getController().getAxisSelector().findMeasureNumberAt(event.getX(), event.getY());
			if( measure > 0 && measure != this.loopSelectionEnd ) {
				this.loopSelectionEnd = measure;
				TGPracticeSession.getInstance(TGApplicationUtil.findContext(this.songView)).setLoopRange(
						Math.min(this.loopSelectionStart, measure), Math.max(this.loopSelectionStart, measure));
			}
		} else if( action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL ) {
			this.loopSelectionStart = -1;
			this.loopSelectionEnd = -1;
		}
	}

	@Override
	public boolean onSingleTapUp(MotionEvent e) {
		this.moveToAxisPosition(e.getX(), e.getY(), false);

		return true;
	}

	@Override
	public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
		if( this.songView.getController().isScrollActionAvailable() ) {
			this.updateAxis(this.songView.getController().getScroll().getX(), distanceX);
			this.updateAxis(this.songView.getController().getScroll().getY(), distanceY);
		}
		return true;
	}

	public void updateAxis(TGScrollAxis axis, float distance) {
		if( axis.isEnabled() ) {
			axis.setValue(Math.max(Math.min(axis.getValue() + distance, axis.getMaximum()), axis.getMinimum()));
		}
	}

	private void moveToAxisPosition(Float x, Float y, Boolean requestSmartMenu) {
		TGActionProcessor tgActionProcessor = new TGActionProcessor(TGApplicationUtil.findContext(this.songView), TGMoveToAxisPositionAction.NAME);
		tgActionProcessor.setAttribute(TGMoveToAxisPositionAction.ATTRIBUTE_X, x);
		tgActionProcessor.setAttribute(TGMoveToAxisPositionAction.ATTRIBUTE_Y, y);
		tgActionProcessor.setAttribute(TGMoveToAxisPositionAction.ATTRIBUTE_REQUEST_SMART_MENU, requestSmartMenu);
		tgActionProcessor.processOnNewThread();
	}
}
