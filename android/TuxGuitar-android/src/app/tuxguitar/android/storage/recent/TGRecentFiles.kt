package app.tuxguitar.android.storage.recent

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns

/** A song opened before; [uri] keeps a persisted read permission so it can be reopened later. */
class TGRecentFile(val uri: Uri, val name: String)

/** Songs recently opened with the Android file picker, most recent first. */
object TGRecentFiles {

	private const val PREFS = "recent-files"
	private const val KEY = "files"
	private const val MAX = 8
	private const val SEPARATOR = "\t"

	/** Records a document opened with ACTION_OPEN_DOCUMENT, keeping the permission to read it again. */
	@JvmStatic
	fun add(context: Context, uri: Uri) {
		runCatching {
			context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
		}.recoverCatching {
			// the provider may only grant reading
			context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
		}
		val name = displayName(context, uri) ?: uri.lastPathSegment ?: uri.toString()
		val files = listOf(TGRecentFile(uri, name)) + list(context).filter { it.uri != uri }
		save(context, files.take(MAX))
	}

	@JvmStatic
	fun list(context: Context): List<TGRecentFile> {
		val stored = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: return emptyList()
		return stored.lines().mapNotNull { line ->
			val parts = line.split(SEPARATOR, limit = 2)
			if (parts.size == 2) TGRecentFile(Uri.parse(parts[0]), parts[1]) else null
		}
	}

	@JvmStatic
	fun remove(context: Context, uri: Uri) {
		save(context, list(context).filter { it.uri != uri })
	}

	private fun save(context: Context, files: List<TGRecentFile>) {
		val value = files.joinToString("\n") { it.uri.toString() + SEPARATOR + it.name.replace("\n", " ").replace(SEPARATOR, " ") }
		context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, value).apply()
	}

	private fun displayName(context: Context, uri: Uri): String? = runCatching {
		context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
			if (cursor.moveToFirst()) cursor.getString(0) else null
		}
	}.getOrNull()
}
