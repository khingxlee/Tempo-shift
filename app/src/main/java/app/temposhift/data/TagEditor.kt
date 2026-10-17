package app.temposhift.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream

/**
 * Writes title / artist / album into the audio file itself (MP3 / ID3 tags) and into Android's media database.
 * Other formats are only updated in the media database; the app also keeps its own copy (see AppDb.tagOverrides).
 */
object TagEditor {

    /** Returns true when the audio file itself was rewritten with the new tags. */
    fun writeFile(ctx: Context, uri: Uri, title: String, artist: String, album: String): Boolean {
        val cr = ctx.contentResolver
        var fileChanged = false
        try {
            if ((cr.getType(uri) ?: "") == "audio/mpeg") fileChanged = writeId3(ctx, uri, title, artist, album)
        } catch (_: Exception) {
            fileChanged = false
        }
        try {
            val v = ContentValues()
            v.put(MediaStore.Audio.Media.TITLE, title)
            v.put(MediaStore.Audio.Media.ARTIST, artist)
            v.put(MediaStore.Audio.Media.ALBUM, album)
            cr.update(uri, v, null, null)
        } catch (_: Exception) { }
        return fileChanged
    }

    private fun writeId3(ctx: Context, uri: Uri, title: String, artist: String, album: String): Boolean {
        val cr = ctx.contentResolver
        var version = 4
        var oldTotal = 0
        var oldFrames = ByteArray(0)

        // 1) read the existing tag (if any)
        val readOk = cr.openInputStream(uri)?.use { ins ->
            val head = ByteArray(10)
            val got = readFully(ins, head)
            if (got == 10 && String(head, 0, 3, Charsets.ISO_8859_1) == "ID3") {
                val ver = head[3].toInt()
                val flags = head[5].toInt() and 0xFF
                val size = synchsafe(head, 6)
                // unsynchronisation / extended header / unknown version: do not touch this file
                if ((ver != 3 && ver != 4) || (flags and 0xC0) != 0 || size < 0 || size > 64 * 1024 * 1024) return false
                version = ver
                oldTotal = 10 + size + (if (ver == 4 && (flags and 0x10) != 0) 10 else 0)
                oldFrames = ByteArray(size)
                if (readFully(ins, oldFrames) != size) return false
            }
            true
        } ?: false
        if (!readOk) return false

        // 2) keep every frame except the three we replace
        val kept = ByteArrayOutputStream()
        var pos = 0
        while (pos + 10 <= oldFrames.size) {
            if (oldFrames[pos].toInt() == 0) break
            val id = String(oldFrames, pos, 4, Charsets.ISO_8859_1)
            val frameSize = if (version == 4) synchsafe(oldFrames, pos + 4) else bigEndian(oldFrames, pos + 4)
            if (frameSize < 0 || pos + 10 + frameSize > oldFrames.size) break
            if (id != "TIT2" && id != "TPE1" && id != "TALB") kept.write(oldFrames, pos, 10 + frameSize)
            pos += 10 + frameSize
        }
        // the tag was damaged or odd: leave the file alone rather than lose pictures or other frames
        if (pos < oldFrames.size && oldFrames[pos].toInt() != 0) return false

        // 3) new text frames
        val body = ByteArrayOutputStream()
        body.write(kept.toByteArray())
        if (title.isNotBlank()) body.write(textFrame(version, "TIT2", title))
        if (artist.isNotBlank()) body.write(textFrame(version, "TPE1", artist))
        if (album.isNotBlank()) body.write(textFrame(version, "TALB", album))
        val bodyBytes = body.toByteArray()

        val tmp = File.createTempFile("tag", ".mp3", ctx.cacheDir)
        try {
            FileOutputStream(tmp).use { out ->
                out.write("ID3".toByteArray(Charsets.ISO_8859_1))
                out.write(version)
                out.write(0)
                out.write(0)
                out.write(synchsafeBytes(bodyBytes.size))
                out.write(bodyBytes)
                val copied = cr.openInputStream(uri)?.use { ins ->
                    skipFully(ins, oldTotal.toLong())
                    ins.copyTo(out)
                    true
                } ?: false
                if (!copied) return false
            }
            if (tmp.length() < 1024L) return false
            // 4) only now overwrite the original
            val written = cr.openOutputStream(uri, "wt")?.use { o ->
                FileInputStream(tmp).use { it.copyTo(o) }
                true
            } ?: false
            return written
        } finally {
            tmp.delete()
        }
    }

    private fun textFrame(version: Int, id: String, text: String): ByteArray {
        val data: ByteArray = if (version == 4) {
            byteArrayOf(3) + text.toByteArray(Charsets.UTF_8)
        } else {
            byteArrayOf(1, 0xFF.toByte(), 0xFE.toByte()) + text.toByteArray(Charsets.UTF_16LE)
        }
        val out = ByteArrayOutputStream()
        out.write(id.toByteArray(Charsets.ISO_8859_1))
        out.write(if (version == 4) synchsafeBytes(data.size) else bigEndianBytes(data.size))
        out.write(0)
        out.write(0)
        out.write(data)
        return out.toByteArray()
    }

    private fun readFully(ins: InputStream, buf: ByteArray): Int {
        var total = 0
        while (total < buf.size) {
            val n = ins.read(buf, total, buf.size - total)
            if (n < 0) break
            total += n
        }
        return total
    }

    private fun skipFully(ins: InputStream, count: Long) {
        var left = count
        val scratch = ByteArray(8192)
        while (left > 0) {
            val s = ins.skip(left)
            if (s > 0) { left -= s; continue }
            val r = ins.read(scratch, 0, minOf(scratch.size.toLong(), left).toInt())
            if (r < 0) break
            left -= r
        }
    }

    private fun synchsafe(b: ByteArray, off: Int): Int =
        ((b[off].toInt() and 0x7F) shl 21) or ((b[off + 1].toInt() and 0x7F) shl 14) or
            ((b[off + 2].toInt() and 0x7F) shl 7) or (b[off + 3].toInt() and 0x7F)

    private fun bigEndian(b: ByteArray, off: Int): Int =
        ((b[off].toInt() and 0xFF) shl 24) or ((b[off + 1].toInt() and 0xFF) shl 16) or
            ((b[off + 2].toInt() and 0xFF) shl 8) or (b[off + 3].toInt() and 0xFF)

    private fun synchsafeBytes(n: Int): ByteArray = byteArrayOf(
        ((n shr 21) and 0x7F).toByte(), ((n shr 14) and 0x7F).toByte(), ((n shr 7) and 0x7F).toByte(), (n and 0x7F).toByte()
    )

    private fun bigEndianBytes(n: Int): ByteArray = byteArrayOf(
        ((n shr 24) and 0xFF).toByte(), ((n shr 16) and 0xFF).toByte(), ((n shr 8) and 0xFF).toByte(), (n and 0xFF).toByte()
    )
}
