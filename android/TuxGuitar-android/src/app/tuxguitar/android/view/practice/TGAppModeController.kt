package app.tuxguitar.android.view.practice

import androidx.compose.runtime.mutableStateOf
import app.tuxguitar.android.activity.TGActivityController
import app.tuxguitar.android.view.keyboard.TGTabKeyboardController
import app.tuxguitar.util.TGContext
import app.tuxguitar.util.singleton.TGSingletonFactory
import app.tuxguitar.util.singleton.TGSingletonUtil

/** Main screen modes, picked with the three icons in the top bar. */
enum class TGAppMode { PLAY, PRACTICE, EDIT }

/**
 * Holds the current main screen mode. The app starts in [TGAppMode.PLAY].
 * EDIT shows the tablature keyboard; PLAY and PRACTICE show their bar at the bottom (see TGBottomBars.kt).
 */
class TGAppModeController(private val context: TGContext) {

	private val modeState = mutableStateOf(TGAppMode.PLAY)

	val mode: TGAppMode
		get() = modeState.value

	fun setMode(mode: TGAppMode) {
		modeState.value = mode
		TGTabKeyboardController.getInstance(context).setVisible(mode == TGAppMode.EDIT)
		// refresh the highlighted mode icon in the top bar
		TGActivityController.getInstance(context).activity?.invalidateOptionsMenu()
	}

	companion object {
		@JvmStatic
		fun getInstance(context: TGContext): TGAppModeController =
			TGSingletonUtil.getInstance(context, TGAppModeController::class.java.name, object : TGSingletonFactory<TGAppModeController> {
				override fun createInstance(context: TGContext): TGAppModeController = TGAppModeController(context)
			})
	}
}
