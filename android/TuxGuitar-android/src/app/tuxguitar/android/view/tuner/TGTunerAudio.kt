package app.tuxguitar.android.view.tuner

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder

/**
 * Reads the microphone on its own thread and reports the detected pitch (Hz, or [TGPitchDetector.NO_PITCH])
 * about 20 times a second. The caller must hold the RECORD_AUDIO permission.
 */
class TGTunerAudio(private val context: Context, private val onPitch: (Float) -> Unit) {

	companion object {
		private const val SAMPLE_RATE = 44100
		private const val BUFFER = 4096
		private const val HOP = 2048
		// a bass low E is 41 Hz; the highest guitar frets are around 1.3 kHz
		private const val MIN_FREQUENCY = 38f
		private const val MAX_FREQUENCY = 1500f
		private const val MIN_RMS = 0.004f
	}

	@Volatile
	private var running = false
	private var thread: Thread? = null

	val isRunning: Boolean
		get() = running

	/** Starts listening; returns false if the microphone could not be opened. */
	@SuppressLint("MissingPermission")
	fun start(): Boolean {
		if (running) {
			return true
		}
		val minSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
		if (minSize <= 0) {
			return false
		}
		val record = try {
			AudioRecord(audioSource(), SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minSize, BUFFER * 2) * 2)
		} catch (e: Exception) {
			return false
		}
		if (record.state != AudioRecord.STATE_INITIALIZED) {
			record.release()
			return false
		}
		running = true
		thread = Thread({ loop(record) }, "tuner-audio").apply { start() }
		return true
	}

	/** Stops listening without waiting: the reading thread ends after its current read and releases the microphone. */
	fun stop() {
		running = false
		thread = null
	}

	/**
	 * Voice recognition: the usual source for tuners, without noise suppression and gain control (they distort the
	 * pitch) and without the special routing of the unprocessed source, which some devices handle badly.
	 */
	private fun audioSource(): Int = MediaRecorder.AudioSource.VOICE_RECOGNITION

	private fun loop(record: AudioRecord) {
		val detector = TGPitchDetector(SAMPLE_RATE, MIN_FREQUENCY, MAX_FREQUENCY, MIN_RMS)
		val window = FloatArray(BUFFER)
		val chunk = ShortArray(HOP)
		try {
			record.startRecording()
			while (running) {
				var read = 0
				while (running && read < HOP) {
					val count = record.read(chunk, read, HOP - read)
					if (count < 0) {
						running = false
						break
					}
					read += count
				}
				if (!running) {
					break
				}
				// slide the window by one hop
				System.arraycopy(window, HOP, window, 0, BUFFER - HOP)
				for (i in 0 until HOP) {
					window[BUFFER - HOP + i] = chunk[i] / 32768f
				}
				onPitch(detector.detect(window, BUFFER))
			}
		} catch (e: Exception) {
			running = false
		} finally {
			runCatching { record.stop() }
			record.release()
		}
	}
}
