package app.temposhift

import android.content.ComponentName
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

private fun Song.toItem(): MediaItem = MediaItem.Builder()
    .setMediaId(id.toString())
    .setUri(uri)
    .setMediaMetadata(MediaMetadata.Builder().setTitle(title).setArtist(artist).setAlbumTitle(album).build())
    .build()

/** Everything the screens show, plus the actions they trigger. Talks to PlayerService through a MediaController. */
class PlayerState(private val ctx: Context) {
    var songs by mutableStateOf<List<Song>>(emptyList())
    var current by mutableStateOf<Song?>(null)
    var currentId by mutableStateOf<Long?>(null)
    var isPlaying by mutableStateOf(false)
    var positionMs by mutableLongStateOf(0L)
    var durationMs by mutableLongStateOf(0L)
    var tempoPct by mutableIntStateOf(100)
    var pitchSt by mutableFloatStateOf(0f)
    var loopA by mutableLongStateOf(-1L)
    var loopB by mutableLongStateOf(-1L)
    var repeatMode by mutableIntStateOf(Player.REPEAT_MODE_OFF)
    var shuffle by mutableStateOf(false)
    var nextTitle by mutableStateOf("")

    private var controller: MediaController? = null
    private var future: ListenableFuture<MediaController>? = null

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) { sync() }
    }

    fun connect() {
        val token = SessionToken(ctx, ComponentName(ctx, PlayerService::class.java))
        val f = MediaController.Builder(ctx, token).buildAsync()
        future = f
        f.addListener({
            try {
                val c = f.get()
                controller = c
                c.addListener(listener)
                tempoPct = (c.playbackParameters.speed * 100f).roundToInt()
                pitchSt = (Math.round(12.0 * ln(c.playbackParameters.pitch.toDouble()) / ln(2.0) * 100.0) / 100.0).toFloat()
                sync()
            } catch (_: Exception) { }
        }, ContextCompat.getMainExecutor(ctx))
    }

    fun disconnect() {
        future?.let { MediaController.releaseFuture(it) }
        controller = null
        future = null
    }

    fun sync() {
        val c = controller ?: return
        isPlaying = c.isPlaying
        currentId = c.currentMediaItem?.mediaId?.toLongOrNull()
        current = currentId?.let { id -> songs.firstOrNull { it.id == id } }
        durationMs = if (c.duration == C.TIME_UNSET) 0L else c.duration
        positionMs = c.currentPosition
        repeatMode = c.repeatMode
        shuffle = c.shuffleModeEnabled
        nextTitle = if (c.hasNextMediaItem()) c.getMediaItemAt(c.nextMediaItemIndex).mediaMetadata.title?.toString().orEmpty() else ""
    }

    /** Called a few times a second: moves the seek bar and enforces the A-B loop. */
    fun tick() {
        val c = controller ?: return
        positionMs = c.currentPosition
        val d = c.duration
        if (d != C.TIME_UNSET) durationMs = d
        if (loopA >= 0 && loopB > loopA && positionMs >= loopB) c.seekTo(loopA)
    }

    fun playList(list: List<Song>, index: Int) {
        val c = controller ?: return
        loopA = -1L
        loopB = -1L
        c.setMediaItems(list.map { it.toItem() }, index, 0L)
        c.prepare()
        c.play()
        applyParams()
    }

    fun toggle() { controller?.let { if (it.isPlaying) it.pause() else it.play() } }
    fun next() { controller?.seekToNextMediaItem() }
    fun prev() { controller?.seekToPrevious() }
    fun seek(ms: Long) { controller?.seekTo(ms.coerceAtLeast(0L)) }
    fun skip(deltaMs: Long) {
        val c = controller ?: return
        val end = if (durationMs > 0) durationMs else Long.MAX_VALUE
        c.seekTo((c.currentPosition + deltaMs).coerceIn(0L, end))
    }

    fun setTempo(percent: Int) { tempoPct = percent.coerceIn(50, 200); applyParams() }
    fun setPitch(semitones: Float) { pitchSt = (Math.round(semitones * 100f) / 100f).coerceIn(-12f, 12f); applyParams() }

    private fun applyParams() {
        val ratio = 2.0.pow(pitchSt / 12.0).toFloat()
        controller?.setPlaybackParameters(PlaybackParameters(tempoPct / 100f, ratio))
    }

    fun markA() { loopA = positionMs; if (loopB <= loopA) loopB = -1L }
    fun markB() { if (loopA >= 0 && positionMs > loopA) loopB = positionMs }
    fun clearLoop() { loopA = -1L; loopB = -1L }

    fun cycleRepeat() {
        val c = controller ?: return
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }
    fun toggleShuffle() { controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled } }
}
