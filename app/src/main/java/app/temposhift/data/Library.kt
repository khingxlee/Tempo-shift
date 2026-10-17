package app.temposhift.data

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import app.temposhift.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val uri: Uri,
    val path: String = "",
    val sizeBytes: Long = 0L,
    val mime: String = "",
    val modified: Long = 0L,
    val year: Int = 0,
    val track: Int = 0
) {
    /** Folder the file lives in (empty when unknown). */
    val folder: String get() = path.substringBeforeLast('/', "")
}

/** Reads the phone's music from Android's media database. */
object Library {
    // pictures are cached by memory size, not by count, so scrolling a long list stays light
    private val cache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }
    // at most three covers are decoded at the same time
    private val gate = Semaphore(3)

    fun clearArtCache() { cache.evictAll() }

    @Suppress("DEPRECATION")
    fun load(ctx: Context, minSeconds: Int, overrides: Map<Long, TagOverride> = emptyMap()): List<Song> {
        val out = ArrayList<Song>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.DATE_MODIFIED,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.TRACK
        )
        val selection = MediaStore.Audio.Media.IS_MUSIC + " != 0 AND " + MediaStore.Audio.Media.DURATION + " >= " + (minSeconds * 1000L)
        val order = MediaStore.Audio.Media.TITLE + " COLLATE NOCASE ASC"
        val unknownTitle = ctx.getString(R.string.unknown_title)
        val unknownArtist = ctx.getString(R.string.unknown_artist)
        val unknownAlbum = ctx.getString(R.string.unknown_album)
        try {
            ctx.contentResolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection, selection, null, order)?.use { c ->
                val iId = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val iTitle = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val iArtist = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val iAlbum = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val iAlbumId = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val iDur = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val iData = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val iSize = c.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                val iMime = c.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
                val iMod = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
                val iYear = c.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
                val iTrack = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
                while (c.moveToNext()) {
                    val id = c.getLong(iId)
                    val artist = c.getString(iArtist)
                    val o = overrides[id]
                    out.add(
                        Song(
                            id = id,
                            title = o?.title?.takeIf { it.isNotBlank() } ?: c.getString(iTitle) ?: unknownTitle,
                            artist = o?.artist?.takeIf { it.isNotBlank() }
                                ?: (if (artist.isNullOrBlank() || artist == "<unknown>") unknownArtist else artist),
                            album = o?.album?.takeIf { it.isNotBlank() } ?: c.getString(iAlbum) ?: unknownAlbum,
                            albumId = c.getLong(iAlbumId),
                            durationMs = c.getLong(iDur),
                            uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id),
                            path = c.getString(iData) ?: "",
                            sizeBytes = c.getLong(iSize),
                            mime = c.getString(iMime) ?: "",
                            modified = c.getLong(iMod),
                            year = c.getInt(iYear),
                            track = c.getInt(iTrack) % 1000
                        )
                    )
                }
            }
        } catch (_: Exception) { }
        return out
    }

    /** Cover picture of a song at about [px] pixels. Small sizes are used in lists, big ones for album pages. */
    suspend fun artwork(ctx: Context, song: Song, px: Int = 256): Bitmap? {
        val key = song.id.toString() + "_" + px
        cache.get(key)?.let { return it }
        return withContext(Dispatchers.IO) {
            gate.withPermit {
                val again = cache.get(key)
                if (again != null) {
                    again
                } else {
                    try {
                        if (Build.VERSION.SDK_INT >= 29) {
                            val bmp = ctx.contentResolver.loadThumbnail(song.uri, Size(px, px), null)
                            cache.put(key, bmp)
                            bmp
                        } else null
                    } catch (_: Exception) { null }
                }
            }
        }
    }
}
