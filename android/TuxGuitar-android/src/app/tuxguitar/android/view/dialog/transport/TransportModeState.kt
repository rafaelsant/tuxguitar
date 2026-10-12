package app.tuxguitar.android.view.dialog.transport

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import app.tuxguitar.android.R
import kotlin.math.roundToInt

/**
 * Values edited by the play mode screen.
 *
 * The song data is passed in as plain arrays so the screen does not depend on the player:
 * [tempos] and [markers] are indexed by measure number - 1.
 */
class TransportModeState(
	val measureCount: Int,
	private val tempos: IntArray,
	private val markers: Array<String?>,
	val caretMeasure: Int,
	private val onChanged: Runnable,
) {
	companion object {
		const val MIN_PERCENT = 1
		const val MAX_PERCENT = 500
		val INCREMENT_PRESETS = intArrayOf(1, 2, 5, 10)
	}

	private val trainerState = mutableStateOf(false)
	private val simpleLoopState = mutableStateOf(false)
	private val simplePercentState = mutableIntStateOf(100)
	private val trainerFromState = mutableIntStateOf(80)
	private val trainerToState = mutableIntStateOf(100)
	private val trainerIncrementState = mutableIntStateOf(5)
	private val loopFromState = mutableIntStateOf(1)
	private val loopToState = mutableIntStateOf(measureCount)

	/** Fields whose text is currently not a number in range; they block saving. */
	private val invalidFields = mutableStateMapOf<String, Boolean>()

	var isTrainer: Boolean
		get() = trainerState.value
		set(value) { trainerState.value = value; onChanged.run() }

	var isSimpleLoop: Boolean
		get() = simpleLoopState.value
		set(value) { simpleLoopState.value = value; onChanged.run() }

	var simplePercent: Int
		get() = simplePercentState.intValue
		set(value) { simplePercentState.intValue = value; onChanged.run() }

	var trainerFrom: Int
		get() = trainerFromState.intValue
		set(value) { trainerFromState.intValue = value; onChanged.run() }

	var trainerTo: Int
		get() = trainerToState.intValue
		set(value) { trainerToState.intValue = value; onChanged.run() }

	var trainerIncrement: Int
		get() = trainerIncrementState.intValue
		set(value) { trainerIncrementState.intValue = value; onChanged.run() }

	var loopFrom: Int
		get() = loopFromState.intValue
		set(value) {
			loopFromState.intValue = value
			// keep the range valid when the start is moved past the end
			if (value > loopToState.intValue) {
				loopToState.intValue = value
			}
			onChanged.run()
		}

	var loopTo: Int
		get() = loopToState.intValue
		set(value) { loopToState.intValue = value; onChanged.run() }

	/** Loads the current player mode; loop headers <= 0 mean "from the beginning" / "to the end". */
	fun load(trainer: Boolean, loop: Boolean, simplePercent: Int, trainerFrom: Int, trainerTo: Int,
			trainerIncrement: Int, loopSHeader: Int, loopEHeader: Int) {
		trainerState.value = trainer
		simpleLoopState.value = loop
		simplePercentState.intValue = simplePercent.coerceIn(MIN_PERCENT, MAX_PERCENT)
		trainerFromState.intValue = trainerFrom.coerceIn(MIN_PERCENT, MAX_PERCENT)
		trainerToState.intValue = trainerTo.coerceIn(MIN_PERCENT, MAX_PERCENT)
		trainerIncrementState.intValue = trainerIncrement.coerceIn(MIN_PERCENT, MAX_PERCENT)
		loopFromState.intValue = (if (loopSHeader > 0) loopSHeader else 1).coerceIn(1, measureCount)
		loopToState.intValue = (if (loopEHeader > 0) loopEHeader else measureCount).coerceIn(loopFromState.intValue, measureCount)
	}

	fun setFieldValid(field: String, valid: Boolean) {
		if (valid) invalidFields.remove(field) else invalidFields[field] = true
		onChanged.run()
	}

	val isLoopEnabled: Boolean
		get() = isTrainer || isSimpleLoop

	/** Value to store as the loop start header: -1 means from the beginning. */
	val loopSHeader: Int
		get() = if (loopFrom <= 1) -1 else loopFrom

	/** Value to store as the loop end header: -1 means to the end. */
	val loopEHeader: Int
		get() = if (loopTo >= measureCount) -1 else loopTo

	/** String resource of the training mode error, or null. */
	val trainerError: Int?
		get() = when {
			!isTrainer -> null
			trainerFrom >= trainerTo -> R.string.transport_mode_dlg_trainer_error_range
			trainerIncrement > trainerTo - trainerFrom -> R.string.transport_mode_dlg_trainer_error_increment
			else -> null
		}

	/** String resource of the loop range error, or null. */
	val loopError: Int?
		get() = if (isLoopEnabled && loopTo < loopFrom) R.string.transport_mode_dlg_loop_range_error else null

	/** Only the sections in use are validated, so training values never block saving the simple mode. */
	val isValid: Boolean
		get() {
			val fieldsInUse = buildList {
				if (isTrainer) addAll(listOf("trainerFrom", "trainerTo", "trainerIncrement")) else add("simplePercent")
				if (isLoopEnabled) addAll(listOf("loopFrom", "loopTo"))
			}
			return fieldsInUse.none { invalidFields[it] == true } && trainerError == null && loopError == null
		}

	/** Quarter note tempo of the measure where playback starts, used to show percentages as BPM. */
	val baseTempo: Int
		get() = tempos[(if (isLoopEnabled) loopFrom else 1) - 1]

	fun percentToBpm(percent: Int): Int = (baseTempo * percent / 100f).roundToInt()

	fun markerAt(measure: Int): String? = markers.getOrNull(measure - 1)
}
