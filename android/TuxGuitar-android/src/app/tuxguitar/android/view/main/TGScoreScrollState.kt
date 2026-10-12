package app.tuxguitar.android.view.main

import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue

/** When the score was last scrolled by the user: the floating menu bar steps aside while the score moves. */
object TGScoreScrollState {

	var lastScrollMillis by mutableLongStateOf(0L)
		private set

	@JvmStatic
	fun notifyScroll() {
		lastScrollMillis = SystemClock.uptimeMillis()
	}
}
