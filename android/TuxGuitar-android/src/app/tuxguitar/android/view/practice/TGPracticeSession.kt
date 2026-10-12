package app.tuxguitar.android.view.practice

import androidx.compose.runtime.mutableStateOf
import app.tuxguitar.android.view.tablature.TGSongViewController
import app.tuxguitar.util.TGContext
import app.tuxguitar.util.singleton.TGSingletonFactory
import app.tuxguitar.util.singleton.TGSingletonUtil

/**
 * Practice settings shared by the practice bar and the tablature (long press and drag over measures sets the loop).
 * Changes are saved and, in practice mode, applied to the player right away.
 */
class TGPracticeSession(private val context: TGContext) {

	val player = TGPracticePlayer(context)

	private val settingsState = mutableStateOf(player.loadSettings())

	var settings: TGPracticeSettings
		get() = settingsState.value
		set(value) {
			settingsState.value = value
			player.saveSettings(value)
			if (TGAppModeController.getInstance(context).mode == TGAppMode.PRACTICE) {
				apply()
			}
		}

	/** Applies the practice settings to the player and redraws the loop markers. */
	fun apply() {
		player.applyPractice(settings)
		TGSongViewController.getInstance(context).redraw()
	}

	/** Loops the given measures (1-based, inclusive), turning the loop on. */
	fun setLoopRange(first: Int, last: Int) {
		val count = runCatching { player.snapshot(null).measureCount }.getOrDefault(last)
		settings = settings.copy(
			loop = true,
			loopStart = if (first <= 1) -1 else first,
			loopEnd = if (last >= count) -1 else last,
		)
	}

	companion object {
		@JvmStatic
		fun getInstance(context: TGContext): TGPracticeSession =
			TGSingletonUtil.getInstance(context, TGPracticeSession::class.java.name, object : TGSingletonFactory<TGPracticeSession> {
				override fun createInstance(context: TGContext): TGPracticeSession = TGPracticeSession(context)
			})
	}
}
