package app.tuxguitar.android.application

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Freeze and crash reports for testing on the phone, where there is no logcat at hand. A watchdog thread checks that
 * the main thread keeps running; when it is stuck for a few seconds, the stack of every thread is written to a file
 * (what the main thread waits for, and who holds it). Uncaught exceptions are written the same way. The last report
 * can be shared from Settings.
 */
object TGDiagnostics {

	private const val FILE = "diagnostic-report.txt"
	private const val CHECK_MILLIS = 1000L
	private const val STALL_MILLIS = 5000L
	private const val MAX_FRAMES = 40

	@Volatile
	private var lastMainTick = 0L
	private var started = false

	@JvmStatic
	@Synchronized
	fun start(context: Context) {
		if (started) {
			return
		}
		started = true
		val appContext = context.applicationContext
		installCrashHandler(appContext)

		val main = Handler(Looper.getMainLooper())
		lastMainTick = SystemClock.uptimeMillis()
		val thread = Thread({
			var reported = false
			while (true) {
				main.post { lastMainTick = SystemClock.uptimeMillis() }
				try {
					Thread.sleep(CHECK_MILLIS)
				} catch (e: InterruptedException) {
					return@Thread
				}
				val stalled = SystemClock.uptimeMillis() - lastMainTick
				if (stalled >= STALL_MILLIS) {
					// one report per freeze
					if (!reported) {
						reported = true
						write(appContext, "Main thread stuck for ${stalled / 1000} s", null)
					}
				} else {
					reported = false
				}
			}
		}, "tg-freeze-watchdog")
		thread.isDaemon = true
		thread.start()
	}

	private fun installCrashHandler(context: Context) {
		val previous = Thread.getDefaultUncaughtExceptionHandler()
		Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
			runCatching { write(context, "Crash in thread '${thread.name}'", throwable) }
			previous?.uncaughtException(thread, throwable)
		}
	}

	@JvmStatic
	fun reportFile(context: Context): File = File(context.applicationContext.filesDir, FILE)

	@JvmStatic
	fun hasReport(context: Context): Boolean = reportFile(context).exists()

	@JvmStatic
	fun readReport(context: Context): String? = runCatching { reportFile(context).readText() }.getOrNull()

	@JvmStatic
	fun deleteReport(context: Context) {
		reportFile(context).delete()
	}

	private fun write(context: Context, title: String, throwable: Throwable?) {
		val text = StringBuilder()
		text.append("TuxGuitar diagnostic report\n")
		text.append(title).append('\n')
		text.append("Time: ").append(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())).append('\n')
		text.append("Device: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
			.append(", Android ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(")\n\n")
		if (throwable != null) {
			text.append(android.util.Log.getStackTraceString(throwable)).append('\n')
		}
		// main thread first, then the threads that wait or are blocked (lock holders show up among the others)
		val stacks = Thread.getAllStackTraces().entries.sortedWith(compareBy(
			{ if (it.key == Looper.getMainLooper().thread) 0 else 1 },
			{ if (it.key.state == Thread.State.BLOCKED || it.key.state == Thread.State.WAITING) 0 else 1 },
			{ it.key.name },
		))
		for ((thread, frames) in stacks) {
			if (frames.isEmpty()) {
				continue
			}
			text.append("\"").append(thread.name).append("\" ").append(thread.state).append('\n')
			frames.take(MAX_FRAMES).forEach { text.append("    at ").append(it).append('\n') }
			if (frames.size > MAX_FRAMES) {
				text.append("    ... ").append(frames.size - MAX_FRAMES).append(" more\n")
			}
			text.append('\n')
		}
		reportFile(context).writeText(text.toString())
	}
}
