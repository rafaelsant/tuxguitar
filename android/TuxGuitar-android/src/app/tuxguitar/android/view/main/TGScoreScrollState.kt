package app.tuxguitar.android.view.main

import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** State the floating menu bar follows to step aside: score scrolling and the player sheet size. */
object TGScoreScrollState {

	var lastScrollMillis by mutableLongStateOf(0L)
		private set

	/** The player sheet is open at its full size (practice options): the menu bar steps aside to leave the score room. */
	var playerExpanded by mutableStateOf(false)

	@JvmStatic
	fun notifyScroll() {
		lastScrollMillis = SystemClock.uptimeMillis()
	}
}
