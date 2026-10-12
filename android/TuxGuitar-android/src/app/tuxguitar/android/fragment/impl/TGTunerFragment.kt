package app.tuxguitar.android.fragment.impl

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import app.tuxguitar.android.R
import app.tuxguitar.android.action.impl.gui.TGOpenFragmentAction
import app.tuxguitar.android.action.impl.transport.TGTransportStopAction
import app.tuxguitar.android.activity.TGActivity
import app.tuxguitar.android.fragment.TGFragmentController
import app.tuxguitar.android.view.compose.TGComposeTheme
import app.tuxguitar.android.view.compose.TGScreenTopBar
import app.tuxguitar.android.view.tablature.TGSongViewController
import app.tuxguitar.android.view.tuner.TGPitchDetector
import app.tuxguitar.android.view.tuner.TGTunerAudio
import app.tuxguitar.android.view.tuner.TGTunerString
import app.tuxguitar.android.view.tuner.TGTuning
import app.tuxguitar.document.TGDocumentManager
import app.tuxguitar.editor.action.TGActionProcessor
import app.tuxguitar.player.base.MidiPlayer
import app.tuxguitar.song.models.TGTrack
import app.tuxguitar.util.TGContext
import kotlin.math.abs

class TGTunerFragmentController : TGFragmentController<TGTunerFragment> {
	override fun getFragment(): TGTunerFragment = TGTunerFragment()
}

/** Opens the tuner screen (side menu and ⋮ menu). */
fun openTuner(context: TGContext, activity: TGActivity) {
	val processor = TGActionProcessor(context, TGOpenFragmentAction.NAME)
	processor.setAttribute(TGOpenFragmentAction.ATTRIBUTE_CONTROLLER, TGTunerFragmentController())
	processor.setAttribute(TGOpenFragmentAction.ATTRIBUTE_ACTIVITY, activity)
	processor.process()
}

/**
 * Tuner: listens to the microphone and shows how far the played string is from the tuning of the current track
 * (standard tuning for percussion tracks). The string is recognized on its own, or can be locked by tapping it.
 */
class TGTunerFragment : Fragment() {

	private companion object {
		const val HISTORY = 5
		// frames are ~46 ms: the reading dims after ~0.5 s of silence (the peg is being turned) and clears after ~3 s
		const val STALE_FRAMES = 11
		const val CLEAR_FRAMES = 65
		// played notes further than this from every string are not taken for a string
		const val STRING_RANGE = 3.5
		// in tune: entered within 5 cents, left beyond 8, so the state does not flicker at the edge
		const val IN_TUNE_ENTER = 5
		const val IN_TUNE_LEAVE = 8
		const val CLOSE_CENTS = 15
		const val REFERENCE_MILLIS = 2000L
	}

	/**
	 * What the screen shows. [string] is the string being tuned (null when the note is far from all of them:
	 * then [target] is just the nearest semitone and [nearest] the closest string).
	 */
	private class Reading(
		val frequency: Float,
		val target: Int,
		val cents: Int,
		val string: TGTunerString?,
		val nearest: TGTunerString?,
		val inTune: Boolean,
	)

	private var permission by mutableStateOf(false)
	private var permissionDenied by mutableStateOf(false)
	private var microphoneError by mutableStateOf(false)
	private var strings by mutableStateOf(TGTuning.STANDARD)
	private var trackName by mutableStateOf<String?>(null)
	private var hasInstrument by mutableStateOf(false)
	private var locked by mutableStateOf<TGTunerString?>(null)
	private var reading by mutableStateOf<Reading?>(null)
	private var stale by mutableStateOf(false)
	private var lastString by mutableStateOf<TGTunerString?>(null)
	private var referencePlaying by mutableStateOf<TGTunerString?>(null)

	private var track: TGTrack? = null
	private var audio: TGTunerAudio? = null
	private var audioSession = 0
	private val history = ArrayDeque<Double>()
	private var silentFrames = 0
	private var ignoreUntil = 0L
	private val mainHandler = Handler(Looper.getMainLooper())

	private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
		permission = granted
		permissionDenied = !granted
		if (granted && isResumed) {
			startAudio()
		}
	}

	private val tgActivity: TGActivity
		get() = requireActivity() as TGActivity

	private val tgContext: TGContext
		get() = tgActivity.findContext()

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
		return ComposeView(requireContext()).apply {
			// hands are on the instrument: the screen must not go off while tuning
			keepScreenOn = true
			setContent {
				TGComposeTheme {
					Surface(modifier = Modifier.fillMaxSize()) {
						Column {
							TGScreenTopBar(stringResource(R.string.tuner_title), { tgActivity.callBackAction() })
							if (permission) TunerScreen() else PermissionScreen()
						}
					}
				}
			}
		}
	}

	override fun onResume() {
		super.onResume()
		tgActivity.setLegacyActionBarVisible(false)
		// the song playing would be heard by the microphone
		if (MidiPlayer.getInstance(tgContext).isRunning) {
			TGActionProcessor(tgContext, TGTransportStopAction.NAME).process()
		}
		loadTuning()
		permission = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
		if (permission) {
			startAudio()
		} else if (!permissionDenied) {
			// asked right away: opening the tuner already says what it is for
			permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
		}
	}

	override fun onPause() {
		stopAudio()
		super.onPause()
	}

	private fun loadTuning() {
		val track = runCatching { TGSongViewController.getInstance(tgContext).caret.track }.getOrNull()
		val trackStrings = TGTuning.stringsOf(track)
		this.track = if (trackStrings != null) track else null
		hasInstrument = trackStrings != null
		strings = trackStrings ?: TGTuning.STANDARD
		trackName = if (trackStrings != null) track?.name else null
		if (locked != null && locked !in strings) {
			locked = null
		}
		if (lastString != null && lastString !in strings) {
			lastString = null
		}
	}

	// ---- audio

	private fun startAudio() {
		if (audio?.isRunning == true) {
			return
		}
		// readings still arriving from a stopped microphone are dropped
		val session = ++audioSession
		val audio = TGTunerAudio(requireContext().applicationContext) { pitch ->
			mainHandler.post { if (session == audioSession) onPitch(pitch) }
		}
		microphoneError = !audio.start()
		this.audio = audio
	}

	private fun stopAudio() {
		audio?.stop()
		audio = null
		audioSession++
		mainHandler.removeCallbacksAndMessages(null)
		history.clear()
		reading = null
		referencePlaying = null
	}

	private fun onPitch(pitch: Float) {
		// skip the reference note we are playing
		if (SystemClock.uptimeMillis() < ignoreUntil) {
			return
		}
		if (pitch == TGPitchDetector.NO_PITCH) {
			silentFrames++
			if (silentFrames >= STALE_FRAMES) {
				stale = true
			}
			if (silentFrames >= CLEAR_FRAMES) {
				history.clear()
				reading = null
			}
			return
		}
		silentFrames = 0
		stale = false
		history.addLast(TGTuning.midi(pitch))
		while (history.size > HISTORY) {
			history.removeFirst()
		}
		// median: a single wrong reading (an octave jump at the attack) does not move the needle
		val midi = history.sorted()[history.size / 2]
		val previous = reading
		val nearest = TGTuning.nearest(strings, midi)
		val string = locked ?: autoString(midi, nearest, previous?.string)
		val target = string?.midi ?: Math.round(midi).toInt()
		val cents = TGTuning.cents(midi, target)
		val wasInTune = previous != null && previous.inTune && previous.string == string
		val inTune = string != null && abs(cents) <= (if (wasInTune) IN_TUNE_LEAVE else IN_TUNE_ENTER)
		reading = Reading(pitch, target, cents, string, nearest, inTune)
		if (string != null) {
			lastString = string
		}
	}

	/** Nearest string within range; the current one is kept unless another is clearly (1 semitone) closer. */
	private fun autoString(midi: Double, nearest: TGTunerString?, current: TGTunerString?): TGTunerString? {
		val candidate = nearest?.takeIf { abs(midi - it.midi) <= STRING_RANGE }
		if (current != null && candidate != current && abs(midi - current.midi) <= STRING_RANGE) {
			if (candidate == null || abs(midi - current.midi) - abs(midi - candidate.midi) < 1.0) {
				return current
			}
		}
		return candidate
	}

	private fun playReference(string: TGTunerString) {
		val context = tgContext
		val player = MidiPlayer.getInstance(context)
		val documentManager = TGDocumentManager.getInstance(context)
		val song = documentManager.song ?: return
		val channel = documentManager.songManager.getChannel(song, track?.channelId ?: return) ?: return
		if (player.isRunning || channel.isPercussionChannel || referencePlaying != null) {
			return
		}
		ignoreUntil = SystemClock.uptimeMillis() + REFERENCE_MILLIS + 300
		history.clear()
		reading = null
		referencePlaying = string
		mainHandler.postDelayed({ referencePlaying = null }, REFERENCE_MILLIS + 300)
		val volume = (player.volume / 10.0 * channel.volume).toInt()
		Thread {
			player.playBeat(
				channel.channelId, channel.bank.toInt(), channel.program.toInt(), volume, channel.balance.toInt(),
				channel.chorus.toInt(), channel.reverb.toInt(), channel.phaser.toInt(), channel.tremolo.toInt(),
				arrayOf(intArrayOf(string.midi, 100)), REFERENCE_MILLIS, 0,
			)
		}.start()
	}

	// ---- UI

	@Composable
	private fun PermissionScreen() {
		Column(
			horizontalAlignment = Alignment.CenterHorizontally,
			verticalArrangement = Arrangement.Center,
			modifier = Modifier.fillMaxSize().padding(32.dp),
		) {
			Icon(painterResource(R.drawable.ic_bar_tuner_24dp), contentDescription = null, modifier = Modifier.size(48.dp))
			Text(
				stringResource(R.string.tuner_permission_message),
				textAlign = TextAlign.Center,
				modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
			)
			// once refused with "don't ask again", only the app settings can grant it
			val blocked = permissionDenied && !shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)
			if (blocked) {
				Text(
					stringResource(R.string.tuner_permission_blocked),
					textAlign = TextAlign.Center,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
				)
			}
			FilledTonalButton(
				onClick = {
					if (blocked) {
						startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", requireContext().packageName, null)))
					} else {
						permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
					}
				},
				modifier = Modifier.padding(top = 16.dp),
			) {
				Text(stringResource(if (blocked) R.string.tuner_permission_settings else R.string.tuner_permission_button))
			}
		}
	}

	@Composable
	private fun TunerScreen() {
		BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(16.dp)) {
			// the note follows the space (not the font scale), so nothing is pushed off a short screen
			val noteSize = with(LocalDensity.current) { (maxHeight * 0.22f).coerceIn(56.dp, 112.dp).toSp() }
			if (maxHeight < 480.dp) {
				// landscape and small screens: note and meter on the left, strings on the right
				Column {
					TuningLabel()
					Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
						NoteDisplay(noteSize, Modifier.weight(1f))
						StringsPanel(Modifier.weight(1f).padding(start = 16.dp))
					}
				}
			} else {
				Column(horizontalAlignment = Alignment.CenterHorizontally) {
					TuningLabel()
					Spacer(Modifier.weight(1f))
					NoteDisplay(noteSize, Modifier.fillMaxWidth())
					Spacer(Modifier.weight(1f))
					StringsPanel(Modifier.fillMaxWidth())
				}
			}
		}
	}

	@Composable
	private fun TuningLabel() {
		Text(
			trackName?.let { stringResource(R.string.tuner_tuning_of, it) } ?: stringResource(R.string.tuner_standard_tuning),
			style = MaterialTheme.typography.bodyMedium,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
			textAlign = TextAlign.Center,
			modifier = Modifier.fillMaxWidth(),
		)
	}

	@Composable
	private fun NoteDisplay(noteSize: TextUnit, modifier: Modifier) {
		val reading = reading
		val playing = referencePlaying
		val dark = isSystemInDarkTheme()
		val inTuneColor = if (dark) Color(0xFF66BB6A) else Color(0xFF2E7D32)
		val closeColor = if (dark) Color(0xFFFFB74D) else Color(0xFFB26A00)
		val color = when {
			reading == null || reading.string == null -> MaterialTheme.colorScheme.onSurfaceVariant
			reading.inTune -> inTuneColor
			abs(reading.cents) <= CLOSE_CENTS -> closeColor
			else -> MaterialTheme.colorScheme.onSurface
		}
		// shown note: the reading, else the reference being played, else the locked string (dimmed)
		val shownNote = reading?.target ?: playing?.midi ?: locked?.midi
		val direction = if (reading != null && reading.cents < 0) stringResource(R.string.tuner_tune_up) else stringResource(R.string.tuner_tune_down)
		// the status carries the state in words and symbols, not only in color
		val status = when {
			microphoneError -> stringResource(R.string.tuner_microphone_error)
			playing != null -> stringResource(R.string.tuner_playing_reference, playing.label)
			reading == null -> stringResource(R.string.tuner_play_string)
			reading.string == null -> stringResource(R.string.tuner_nearest_string, reading.nearest?.label ?: "") + " · " + direction
			reading.inTune -> "✓ " + stringResource(R.string.tuner_in_tune)
			reading.cents < 0 -> "↑ $direction"
			else -> "↓ $direction"
		}

		Column(
			horizontalAlignment = Alignment.CenterHorizontally,
			modifier = modifier.alpha(if (stale && playing == null) 0.4f else 1f),
		) {
			Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.semantics(mergeDescendants = true) {}) {
				Text(
					shownNote?.let { TGTuning.noteName(it) } ?: "–",
					fontSize = noteSize,
					fontWeight = FontWeight.Bold,
					color = if (reading == null) MaterialTheme.colorScheme.onSurfaceVariant else color,
					maxLines = 1,
				)
				if (shownNote != null) {
					Text(
						(shownNote / 12 - 1).toString(),
						style = MaterialTheme.typography.headlineSmall,
						color = if (reading == null) MaterialTheme.colorScheme.onSurfaceVariant else color,
						modifier = Modifier.padding(bottom = 16.dp),
					)
				}
			}
			Text(
				reading?.let { "%.1f Hz".format(it.frequency) } ?: " ",
				style = MaterialTheme.typography.bodyMedium,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
			)
			Meter(reading?.cents ?: 0, reading != null, reading?.inTune == true, inTuneColor, color)
			// only the coarse state is announced, not every cents change
			Text(
				status,
				style = MaterialTheme.typography.headlineSmall,
				fontWeight = FontWeight.Bold,
				color = if (reading == null) MaterialTheme.colorScheme.onSurface else color,
				textAlign = TextAlign.Center,
				modifier = Modifier.padding(top = 4.dp).semantics { liveRegion = LiveRegionMode.Polite },
			)
			Text(
				if (reading != null && !reading.inTune) stringResource(R.string.tuner_cents, (if (reading.cents > 0) "+" else "") + reading.cents) else " ",
				style = MaterialTheme.typography.bodyLarge,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
			)
			if (microphoneError) {
				TextButton(onClick = { startAudio() }) {
					Text(stringResource(R.string.tuner_retry))
				}
			}
		}
	}

	/** Scale from -50 to +50 cents (flat to sharp) with the in-tune zone in the middle. */
	@Composable
	private fun Meter(cents: Int, active: Boolean, inTune: Boolean, inTuneColor: Color, needleColor: Color) {
		val needle by animateFloatAsState(cents.coerceIn(-50, 50).toFloat(), label = "needle")
		val tickColor = MaterialTheme.colorScheme.outline
		val meterLabel = stringResource(R.string.tuner_meter)
		Row(
			verticalAlignment = Alignment.CenterVertically,
			modifier = Modifier.fillMaxWidth().clearAndSetSemantics {
				contentDescription = meterLabel
				progressBarRangeInfo = ProgressBarRangeInfo(cents.coerceIn(-50, 50).toFloat(), -50f..50f)
			},
		) {
			Text("♭", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
			Canvas(modifier = Modifier.weight(1f).height(80.dp).padding(horizontal = 8.dp, vertical = 8.dp)) {
				val margin = 8.dp.toPx()
				val perCent = (size.width - 2 * margin) / 100f
				val center = size.width / 2
				val h = size.height
				// the zone fills in when in tune
				drawRect(
					inTuneColor.copy(alpha = if (active && inTune) 0.5f else 0.18f),
					topLeft = Offset(center - IN_TUNE_ENTER * perCent, 0f),
					size = Size(2 * IN_TUNE_ENTER * perCent, h),
				)
				for (c in -50..50 step 10) {
					val x = center + c * perCent
					val length = when {
						c == 0 -> h * 0.7f
						c % 50 == 0 -> h * 0.5f
						else -> h * 0.3f
					}
					drawLine(tickColor, Offset(x, h - length), Offset(x, h), strokeWidth = (if (c == 0) 3 else 2).dp.toPx())
				}
				if (active) {
					val x = center + needle * perCent
					drawLine(needleColor, Offset(x, 0f), Offset(x, h), strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
					drawCircle(needleColor, radius = 6.dp.toPx(), center = Offset(x, h))
				}
			}
			Text("♯", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
		}
	}

	@Composable
	private fun StringsPanel(modifier: Modifier) {
		Column(modifier = modifier) {
			// lowest string first: the detected one is outlined, a tap locks the target to it (tap again for auto)
			Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
				strings.forEach { string ->
					StringButton(
						string = string,
						locked = string == locked,
						detected = locked == null && string == reading?.string,
						compact = strings.size > 6,
						modifier = Modifier.weight(1f),
					)
				}
			}
			Row(
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.spacedBy(12.dp),
				modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
			) {
				FilterChip(
					selected = locked == null,
					onClick = { locked = null },
					label = { Text(stringResource(R.string.tuner_auto)) },
				)
				Spacer(Modifier.weight(1f))
				// the reference note needs the track instrument
				val referenceString = locked ?: reading?.string ?: lastString
				FilledTonalButton(
					onClick = { referenceString?.let { playReference(it) } },
					enabled = hasInstrument && referenceString != null && referencePlaying == null,
				) {
					Icon(painterResource(R.drawable.ic_bar_play_24dp), contentDescription = null, modifier = Modifier.size(18.dp))
					Text(stringResource(R.string.tuner_reference), modifier = Modifier.padding(start = 8.dp), maxLines = 1)
				}
			}
		}
	}

	@Composable
	private fun StringButton(string: TGTunerString, locked: Boolean, detected: Boolean, compact: Boolean, modifier: Modifier) {
		val colors = MaterialTheme.colorScheme
		val description = when {
			locked -> stringResource(R.string.tuner_string_locked, string.label)
			detected -> stringResource(R.string.tuner_string_detected, string.label)
			else -> stringResource(R.string.tuner_string_description, string.label)
		}
		Surface(
			selected = locked,
			onClick = { this.locked = if (locked) null else string },
			shape = RoundedCornerShape(12.dp),
			color = if (locked) colors.primaryContainer else colors.surfaceContainerHigh,
			contentColor = if (locked) colors.onPrimaryContainer else colors.onSurface,
			border = if (detected) BorderStroke(2.dp, colors.primary) else null,
			modifier = modifier.height(56.dp).clearAndSetSemantics { contentDescription = description },
		) {
			Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxHeight()) {
				Row(verticalAlignment = Alignment.Bottom) {
					Text(
						TGTuning.noteName(string.midi),
						fontSize = if (compact) 15.sp else 18.sp,
						fontWeight = FontWeight.Bold,
						maxLines = 1,
						softWrap = false,
					)
					Text(
						(string.midi / 12 - 1).toString(),
						fontSize = if (compact) 10.sp else 12.sp,
						maxLines = 1,
						softWrap = false,
						modifier = Modifier.padding(bottom = 2.dp),
					)
				}
			}
		}
	}
}
