package app.tuxguitar.graphics.control;

import app.tuxguitar.song.models.TGNote;
import app.tuxguitar.ui.resource.UIColorModel;

/**
 * Colors single notes of the score and tablature, over the usual colors: for example to show which notes were played
 * right while practicing. Called while painting.
 */
public interface TGNoteHighlighter {

	/** The color of the note, or null to paint it as usual. */
	UIColorModel getHighlightColor(TGNote note);
}
