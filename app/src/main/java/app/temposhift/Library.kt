package app.temposhift

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val uri: Uri
)

/** Reads the phone's music from Android's media database. */
object Library {
    private val cache = LruCache<Long, Bitmap>(120)

    fun load(ctx: Context): List<Song> {
        val out = ArrayList<Song>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION
        )
        val selection = MediaStore.Audio.Media.IS_MUSIC + " != 0 AND " + MediaStore.Audio.Media.DURATION + " > 15000"
        val order = MediaStore.Audio.Media.TITLE + " COLLATE NOCASE ASC"
        try {
            ctx.contentResolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection, selection, null, order)?.use { c ->
                val iId = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val iTitle = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val iArtist = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val iAlbum = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val iAlbumId = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val iDur = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                while (c.moveToNext()) {
                    val id = c.getLong(iId)
                    val artist = c.getString(iArtist)
                    out.add(
                        Song(
                            id = id,
                            title = c.getString(iTitle) ?: "Unknown title",
                            artist = if (artist.isNullOrBlank() || artist == "<unknown>") "Unknown artist" else artist,
                            album = c.getString(iAlbum) ?: "Unknown album",
                            albumId = c.getLong(iAlbumId),
                            durationMs = c.getLong(iDur),
                            uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                        )
                    )
                }
            }
        } catch (_: Exception) { }
        return out
    }

    suspend fun artwork(ctx: Context, song: Song): Bitmap? {
        cache.get(song.id)?.let { return it }
        return withContext(Dispatchers.IO) {
            try {
                if (Build.VERSION.SDK_INT >= 29) {
                    val bmp = ctx.contentResolver.loadThumbnail(song.uri, Size(256, 256), null)
                    cache.put(song.id, bmp)
                    bmp
                } else null
            } catch (_: Exception) { null }
        }
    }
}
