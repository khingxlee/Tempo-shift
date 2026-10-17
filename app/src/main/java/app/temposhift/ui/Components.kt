package app.temposhift.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.temposhift.R
import app.temposhift.data.Library
import app.temposhift.data.Song
import app.temposhift.player.PlayerState

fun fmt(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return (s / 60).toString() + ":" + (s % 60).toString().padStart(2, '0')
}

@Composable
fun Line() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(PANEL))
}

/** Loads a song's cover picture off the main thread. Returns null until it is ready. */
@Composable
fun rememberCover(song: Song?, px: Int): State<Bitmap?> {
    val ctx = LocalContext.current
    return produceState<Bitmap?>(null, song?.id, px) {
        value = if (song == null) null else Library.artwork(ctx, song, px)
    }
}

@Composable
fun Cover(song: Song?, modifier: Modifier, px: Int = 160, shape: Shape = RoundedCornerShape(8.dp)) {
    val bmp by rememberCover(song, px)
    Box(modifier.clip(shape).background(Color(0xFF24362D)), contentAlignment = Alignment.Center) {
        val b = bmp
        if (b != null) {
            Image(bitmap = b.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Icon(Icons.Filled.MusicNote, contentDescription = null, tint = DIM)
        }
    }
}

@Composable
fun Cover(song: Song?, size: Dp, px: Int = 160) {
    Cover(song, Modifier.size(size), px)
}

/** A soft, blurred version of the cover - used behind album pages, lyrics and the side menu. */
@Composable
fun BlurredBackdrop(bitmap: Bitmap?, modifier: Modifier, alpha: Float = 0.9f) {
    val soft = remember(bitmap) {
        if (bitmap == null) null else try {
            Bitmap.createScaledBitmap(bitmap, 32, 32, true).asImageBitmap()
        } catch (_: Exception) {
            null
        }
    }
    if (soft != null) {
        Image(
            bitmap = soft,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            filterQuality = FilterQuality.High,
            alpha = alpha,
            modifier = modifier.blur(24.dp)
        )
    }
}

@Composable
fun SongRow(song: Song, active: Boolean, host: SongMenuHost, onClick: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Cover(song, 48.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(song.title, color = if (active) ACCENT else TEXT, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.artist, color = DIM, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.cd_more), tint = DIM)
            }
            SongMenu(song, host, menu) { menu = false }
        }
    }
}

/** Big rounded album square with the name and song count underneath. */
@Composable
fun AlbumCard(title: String, subtitle: String, cover: Song, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(bottom = 6.dp)) {
        Cover(cover, Modifier.fillMaxWidth().aspectRatio(1f), px = 400, shape = RoundedCornerShape(22.dp))
        Spacer(Modifier.height(8.dp))
        Text(title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 4.dp))
        Text(subtitle, color = DIM, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 4.dp))
    }
}

@Composable
fun GroupRow(title: String, subtitle: String, cover: Song, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Cover(cover, 56.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = DIM, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** The bar at the bottom: 72 dp tall (20% more than before). */
@Composable
fun MiniPlayer(state: PlayerState, onOpen: () -> Unit) {
    val s = state.current ?: return
    val haptic = rememberHaptic()
    Row(
        Modifier.fillMaxWidth().height(72.dp).background(PANEL).clickable(onClick = onOpen).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Cover(s, 52.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(s.title, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(s.artist, color = DIM, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(Modifier.size(46.dp).bounceClick { state.prev() }, contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.SkipPrevious, contentDescription = stringResource(R.string.cd_previous), modifier = Modifier.size(28.dp))
        }
        Box(
            Modifier.size(52.dp).bounceClick { haptic(); state.toggle() }.clip(CircleShape).background(TEXT),
            contentAlignment = Alignment.Center
        ) {
            PlayPauseIcon(state.isPlaying, BG, 30.dp, stringResource(R.string.cd_play_pause))
        }
        Box(Modifier.size(46.dp).bounceClick { state.next() }, contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.SkipNext, contentDescription = stringResource(R.string.cd_next), modifier = Modifier.size(28.dp))
        }
    }
}
