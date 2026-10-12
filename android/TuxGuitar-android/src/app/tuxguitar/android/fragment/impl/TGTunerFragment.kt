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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
		// ~0.7 s of silence clears the reading
		const val SILENT_FRAMES = 15
		// played notes further than this from every string are shown against the nearest semitone
		const val STRING_RANGE = 3.5
		const val IN_TUNE_CENTS = 5
		const val CLOSE_CENTS = 15
		const val REFERENCE_MILLIS = 2000L
	}

	/** What the screen shows: the target note and the distance to it; target null means no reading. */
	private class Reading(val midi: Double, val frequency: Float, val target: Int, val string: TGTunerString?)

	private var permission by mutableStateOf(false)
	private var permissionDenied by mutableStateOf(false)
	private var microphoneError by mutableStateOf(false)
	private var strings by mutableStateOf(TGTuning.STANDARD)
	private var trackName by mutableStateOf<String?>(null)
	private var locked by mutableStateOf<TGTunerString?>(null)
	private var reading by mutableStateOf<Reading?>(null)

	private var track: TGTrack? = null
	private var audio: TGTunerAudio? = null
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
		view?.post { tgActivity.supportActionBar?.hide() }
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

	override fun onDestroyView() {
		(activity as? TGActivity)?.supportActionBar?.show()
		super.onDestroyView()
	}

	private fun loadTuning() {
		val track = runCatching { TGSongViewController.getInstance(tgContext).caret.track }.getOrNull()
		val trackStrings = TGTuning.stringsOf(track)
		this.track = if (trackStrings != null) track else null
		strings = trackStrings ?: TGTuning.STANDARD
		trackName = if (trackStrings != null) track?.name else null
		if (locked != null && locked !in strings) {
			locked = null
		}
	}

	// ---- audio

	private fun startAudio() {
		if (audio?.isRunning == true) {
			return
		}
		val audio = TGTunerAudio(requireContext().applicationContext) { pitch -> mainHandler.post { onPitch(pitch) } }
		microphoneError = !audio.start()
		this.audio = audio
	}

	private fun stopAudio() {
		audio?.stop()
		audio = null
		mainHandler.removeCallbacksAndMessages(null)
		history.clear()
		reading = null
	}

	private fun onPitch(pitch: Float) {
		// skip the reference note we are playing
		if (SystemClock.uptimeMillis() < ignoreUntil) {
			return
		}
		if (pitch == TGPitchDetector.NO_PITCH) {
			silentFrames++
			if (silentFrames >= SILENT_FRAMES) {
				history.clear()
				reading = null
			}
			return
		}
		silentFrames = 0
		history.addLast(TGTuning.midi(pitch))
		while (history.size > HISTORY) {
			history.removeFirst()
		}
		// median: a single wrong reading (an octave jump at the attack) does not move the needle
		val midi = history.sorted()[history.size / 2]
		val string = locked ?: TGTuning.nearest(strings, midi)?.takeIf { abs(midi - it.midi) <= STRING_RANGE }
		val target = string?.midi ?: Math.round(midi).toInt()
		reading = Reading(midi, pitch, target, string)
	}

	private fun playReference(string: TGTunerString) {
		val context = tgContext
		val player = MidiPlayer.getInstance(context)
		val documentManager = TGDocumentManager.getInstance(context)
		val song = documentManager.song ?: return
		val channel = documentManager.songManager.getChannel(song, track?.channelId ?: return) ?: return
		if (player.isRunning || channel.isPercussionChannel) {
			return
		}
		ignoreUntil = SystemClock.uptimeMillis() + REFERENCE_MILLIS + 300
		history.clear()
		reading = null
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
				modifier = Modifier.padding(vertical = 24.dp),
			)
			// once refused with "don't ask again", only the app settings can grant it
			val blocked = permissionDenied && !shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)
			FilledTonalButton(onClick = {
				if (blocked) {
					startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", requireContext().packageName, null)))
				} else {
					permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
				}
			}) {
				Text(stringResource(if (blocked) R.string.tuner_permission_settings else R.string.tuner_permission_button))
			}
		}
	}

	@Composable
	private fun TunerScreen() {
		val reading = reading
		val cents = reading?.let { TGTuning.cents(it.midi, it.target) } ?: 0
		val dark = isSystemInDarkTheme()
		val inTuneColor = if (dark) Color(0xFF66BB6A) else Color(0xFF2E7D32)
		val closeColor = if (dark) Color(0xFFFFB74D) else Color(0xFFB26A00)
		val color = when {
			reading == null -> MaterialTheme.colorScheme.onSurfaceVariant
			abs(cents) <= IN_TUNE_CENTS -> inTuneColor
			abs(cents) <= CLOSE_CENTS -> closeColor
			else -> MaterialTheme.colorScheme.onSurface
		}
		val status = when {
			microphoneError -> stringResource(R.string.tuner_microphone_error)
			reading == null -> stringResource(R.string.tuner_play_string)
			abs(cents) <= IN_TUNE_CENTS -> stringResource(R.string.tuner_in_tune)
			cents < 0 -> stringResource(R.string.tuner_tune_up)
			else -> stringResource(R.string.tuner_tune_down)
		}

		Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxSize().padding(16.dp)) {
			Text(
				trackName?.let { stringResource(R.string.tuner_tuning_of, it) } ?: stringResource(R.string.tuner_standard_tuning),
				style = MaterialTheme.typography.bodyMedium,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
			)
			Spacer(Modifier.weight(1f))

			// ---- note and needle
			Row(verticalAlignment = Alignment.Bottom) {
				Text(
					reading?.let { TGTuning.noteName(it.target) } ?: "–",
					fontSize = 112.sp,
					fontWeight = FontWeight.Bold,
					color = color,
				)
				if (reading != null) {
					Text(
						(reading.target / 12 - 1).toString(),
						style = MaterialTheme.typography.headlineSmall,
						color = color,
						modifier = Modifier.padding(bottom = 24.dp),
					)
				}
			}
			Text(
				reading?.let { "%.1f Hz".format(it.frequency) } ?: " ",
				style = MaterialTheme.typography.bodyMedium,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
			)
			Meter(
				cents = cents,
				active = reading != null,
				inTuneColor = inTuneColor,
				needleColor = color,
				description = status,
			)
			Text(
				if (reading != null && abs(cents) > IN_TUNE_CENTS) "$status · ${stringResource(R.string.tuner_cents, (if (cents > 0) "+" else "") + cents)}" else status,
				style = MaterialTheme.typography.titleMedium,
				color = color,
				modifier = Modifier.padding(top = 8.dp).semantics { liveRegion = LiveRegionMode.Polite },
			)
			Spacer(Modifier.weight(1f))

			// ---- strings, lowest first: the detected one is highlighted, a tap locks the target to it
			Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
				strings.forEach { string ->
					val description = stringResource(R.string.tuner_string_description, string.label)
					FilterChip(
						selected = string == locked || (locked == null && string == reading?.string),
						onClick = { locked = if (locked == string) null else string },
						label = {
							Text(
								TGTuning.noteName(string.midi),
								textAlign = TextAlign.Center,
								fontWeight = FontWeight.Bold,
								modifier = Modifier.fillMaxWidth(),
							)
						},
						shape = RoundedCornerShape(12.dp),
						modifier = Modifier.weight(1f).height(56.dp).semantics { contentDescription = description },
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
				val referenceString = locked ?: reading?.string
				FilledTonalButton(
					onClick = { referenceString?.let { playReference(it) } },
					enabled = track != null && referenceString != null,
				) {
					Icon(painterResource(R.drawable.ic_bar_play_24dp), contentDescription = null, modifier = Modifier.size(18.dp))
					Text(stringResource(R.string.tuner_reference), modifier = Modifier.padding(start = 8.dp))
				}
			}
		}
	}

	/** Scale from -50 to +50 cents with the in-tune zone in the middle. */
	@Composable
	private fun Meter(cents: Int, active: Boolean, inTuneColor: Color, needleColor: Color, description: String) {
		val needle by animateFloatAsState(cents.coerceIn(-50, 50).toFloat(), label = "needle")
		val tickColor = MaterialTheme.colorScheme.outline
		Canvas(
			modifier = Modifier
				.fillMaxWidth()
				.height(88.dp)
				.padding(vertical = 8.dp)
				.semantics { stateDescription = description },
		) {
			val margin = 12.dp.toPx()
			val perCent = (size.width - 2 * margin) / 100f
			val center = size.width / 2
			val h = size.height
			drawRect(
				inTuneColor.copy(alpha = 0.2f),
				topLeft = Offset(center - IN_TUNE_CENTS * perCent, 0f),
				size = Size(2 * IN_TUNE_CENTS * perCent, h),
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
	}
}
