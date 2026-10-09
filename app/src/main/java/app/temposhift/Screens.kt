package app.temposhift

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

val BG = Color(0xFF0F1A15)
val PANEL = Color(0xFF16241D)
val ACCENT = Color(0xFF6EE0A0)
val DIM = Color(0xFF8FA89A)
val TEXT = Color(0xFFE6F0EA)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = ACCENT, onPrimary = Color(0xFF062014),
            background = BG, onBackground = TEXT,
            surface = BG, onSurface = TEXT,
            surfaceVariant = PANEL, secondaryContainer = PANEL
        ),
        content = content
    )
}

private fun fmt(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return (s / 60).toString() + ":" + (s % 60).toString().padStart(2, '0')
}

@Composable
fun TempoShiftApp(state: PlayerState) {
    val ctx = LocalContext.current
    val audioPerm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(ctx, audioPerm) == PackageManager.PERMISSION_GRANTED) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { _ ->
        granted = ContextCompat.checkSelfPermission(ctx, audioPerm) == PackageManager.PERMISSION_GRANTED
    }
    LaunchedEffect(Unit) {
        val wanted = if (Build.VERSION.SDK_INT >= 33) arrayOf(audioPerm, Manifest.permission.POST_NOTIFICATIONS) else arrayOf(audioPerm)
        launcher.launch(wanted)
    }
    LaunchedEffect(granted) {
        if (granted) {
            state.songs = withContext(Dispatchers.IO) { Library.load(ctx) }
            state.sync()
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            state.tick()
            delay(200)
        }
    }

    var showPlayer by remember { mutableStateOf(false) }
    BackHandler(enabled = showPlayer) { showPlayer = false }

    Box(Modifier.fillMaxSize().background(BG)) {
        Column(Modifier.fillMaxSize()) {
            LibraryScreen(state, Modifier.weight(1f))
            if (state.current != null) MiniPlayer(state) { showPlayer = true }
        }
        AnimatedVisibility(
            visible = showPlayer,
            enter = slideInVertically { it },
            exit = slideOutVertically { it }
        ) {
            NowPlaying(state) { showPlayer = false }
        }
    }
}

@Composable
fun Cover(song: Song?, size: Dp) {
    val ctx = LocalContext.current
    val bmp by produceState<Bitmap?>(null, song?.id) {
        value = if (song == null) null else Library.artwork(ctx, song)
    }
    Box(
        Modifier.size(size).clip(RoundedCornerShape(8.dp)).background(Color(0xFF24362D)),
        contentAlignment = Alignment.Center
    ) {
        val b = bmp
        if (b != null) {
            Image(bitmap = b.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Icon(Icons.Filled.MusicNote, contentDescription = null, tint = DIM)
        }
    }
}

@Composable
fun LibraryScreen(state: PlayerState, modifier: Modifier) {
    var tab by remember { mutableIntStateOf(0) }
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val list = remember(state.songs, query) {
        if (query.isBlank()) state.songs
        else state.songs.filter { it.title.contains(query, true) || it.artist.contains(query, true) || it.album.contains(query, true) }
    }
    Column(modifier) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp).height(56.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { }) { Icon(Icons.Filled.Menu, contentDescription = "Menu") }
            if (searching) {
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    placeholder = { Text("Search songs, albums, artists") },
                    modifier = Modifier.weight(1f),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent
                    )
                )
                IconButton(onClick = { searching = false; query = "" }) { Icon(Icons.Filled.Close, contentDescription = "Close search") }
            } else {
                Text("Library", fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f).padding(start = 8.dp))
                IconButton(onClick = { searching = true }) { Icon(Icons.Filled.Search, contentDescription = "Search") }
                IconButton(onClick = { }) { Icon(Icons.Filled.MoreVert, contentDescription = "More") }
            }
        }
        val tabs = listOf("Songs", "Albums", "Artists", "Playlists")
        ScrollableTabRow(selectedTabIndex = tab, containerColor = BG, contentColor = ACCENT, edgePadding = 8.dp) {
            tabs.forEachIndexed { i, t ->
                Tab(
                    selected = tab == i,
                    onClick = { tab = i },
                    text = { Text(t, fontWeight = FontWeight.SemiBold) },
                    selectedContentColor = ACCENT,
                    unselectedContentColor = DIM
                )
            }
        }
        when {
            tab == 3 -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Playlists arrive in the next stage", color = DIM)
            }
            list.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No music found. Allow access to audio files.", color = DIM)
            }
            tab == 0 -> LazyColumn(Modifier.fillMaxSize()) {
                itemsIndexed(list, key = { _, s -> s.id }) { i, s ->
                    SongRow(s, s.id == state.currentId) { state.playList(list, i) }
                }
            }
            tab == 1 -> {
                val groups = remember(list) { list.groupBy { it.album }.toList() }
                LazyColumn(Modifier.fillMaxSize()) {
                    items(groups, key = { it.first }) { g ->
                        GroupRow(g.first, g.second.first().artist + " \u00B7 " + g.second.size + " songs", g.second.first()) { state.playList(g.second, 0) }
                    }
                }
            }
            else -> {
                val groups = remember(list) { list.groupBy { it.artist }.toList() }
                LazyColumn(Modifier.fillMaxSize()) {
                    items(groups, key = { it.first }) { g ->
                        GroupRow(g.first, g.second.size.toString() + " songs", g.second.first()) { state.playList(g.second, 0) }
                    }
                }
            }
        }
    }
}

@Composable
fun SongRow(song: Song, active: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Cover(song, 48.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(song.title, color = if (active) ACCENT else TEXT, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.artist, color = DIM, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.Filled.MoreVert, contentDescription = null, tint = DIM)
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

@Composable
fun MiniPlayer(state: PlayerState, onOpen: () -> Unit) {
    val s = state.current ?: return
    Row(
        Modifier.fillMaxWidth().background(PANEL).clickable(onClick = onOpen).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Cover(s, 44.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(s.title, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(s.artist, color = DIM, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = { state.prev() }, modifier = Modifier.size(40.dp)) { Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous") }
        FilledIconButton(
            onClick = { state.toggle() },
            modifier = Modifier.size(44.dp),
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = TEXT, contentColor = BG)
        ) {
            Icon(if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = "Play or pause")
        }
        IconButton(onClick = { state.next() }, modifier = Modifier.size(40.dp)) { Icon(Icons.Filled.SkipNext, contentDescription = "Next") }
    }
}

@Composable
private fun Line() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(PANEL))
}

@Composable
fun ControlBlock(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    onReset: () -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontWeight = FontWeight.Bold, modifier = Modifier.width(72.dp))
            Text(valueText, fontSize = 22.sp, color = ACCENT, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            IconButton(onClick = onReset) { Icon(Icons.Filled.Refresh, contentDescription = "Reset", tint = DIM) }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onMinus) { Icon(Icons.Filled.Remove, contentDescription = "Decrease") }
            Slider(
                value = value.coerceIn(range.start, range.endInclusive),
                onValueChange = onChange,
                valueRange = range,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(thumbColor = ACCENT, activeTrackColor = ACCENT, inactiveTrackColor = PANEL)
            )
            IconButton(onClick = onPlus) { Icon(Icons.Filled.Add, contentDescription = "Increase") }
        }
    }
}

@Composable
private fun LoopButton(label: String, on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(38.dp).clip(RoundedCornerShape(8.dp)).background(if (on) ACCENT else DIM).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = BG, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun NowPlaying(state: PlayerState, onClose: () -> Unit) {
    val s = state.current
    Column(Modifier.fillMaxSize().background(BG)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) { Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Collapse") }
            Cover(s, 48.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(s?.title ?: "Nothing playing", fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(s?.artist ?: "", color = DIM, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = { }) { Icon(Icons.Filled.MoreVert, contentDescription = "More") }
        }
        Line()
        ControlBlock(
            label = "Pitch",
            valueText = String.format("%+.2f", state.pitchSt),
            value = state.pitchSt,
            range = -12f..12f,
            onChange = { state.setPitch(it) },
            onMinus = { state.setPitch(state.pitchSt - 0.1f) },
            onPlus = { state.setPitch(state.pitchSt + 0.1f) },
            onReset = { state.setPitch(0f) }
        )
        Line()
        ControlBlock(
            label = "Tempo",
            valueText = state.tempoPct.toString() + "%",
            value = state.tempoPct.toFloat(),
            range = 50f..200f,
            onChange = { state.setTempo(it.toInt()) },
            onMinus = { state.setTempo(state.tempoPct - 1) },
            onPlus = { state.setTempo(state.tempoPct + 1) },
            onReset = { state.setTempo(100) }
        )
        Line()
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Loop", fontWeight = FontWeight.Bold, modifier = Modifier.width(72.dp))
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center) {
                LoopButton("A", state.loopA >= 0) { state.markA() }
                Spacer(Modifier.width(24.dp))
                LoopButton("B", state.loopB >= 0) { state.markB() }
            }
            IconButton(onClick = { state.clearLoop() }) { Icon(Icons.Filled.Close, contentDescription = "Clear loop") }
        }
        Line()
        Spacer(Modifier.weight(1f))
        Line()

        var drag by remember { mutableStateOf<Float?>(null) }
        val dur = state.durationMs
        val frac = if (dur > 0) (state.positionMs.toFloat() / dur.toFloat()).coerceIn(0f, 1f) else 0f
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(fmt(state.positionMs), color = DIM, fontSize = 13.sp)
            Slider(
                value = drag ?: frac,
                onValueChange = { drag = it },
                onValueChangeFinished = {
                    drag?.let { state.seek((it * dur).toLong()) }
                    drag = null
                },
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                colors = SliderDefaults.colors(thumbColor = ACCENT, activeTrackColor = ACCENT, inactiveTrackColor = PANEL)
            )
            Text(fmt(dur), color = DIM, fontSize = 13.sp)
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = { state.cycleRepeat() }, modifier = Modifier.size(42.dp)) {
                Icon(
                    if (state.repeatMode == Player.REPEAT_MODE_ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                    contentDescription = "Repeat",
                    tint = if (state.repeatMode == Player.REPEAT_MODE_OFF) TEXT else ACCENT
                )
            }
            IconButton(onClick = { state.prev() }, modifier = Modifier.size(42.dp)) { Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous") }
            IconButton(onClick = { state.skip(-10000L) }, modifier = Modifier.size(42.dp)) { Icon(Icons.Filled.Replay10, contentDescription = "Back 10 seconds") }
            Box(
                Modifier.size(68.dp).clip(RoundedCornerShape(22.dp)).background(TEXT).clickable { state.toggle() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = "Play or pause",
                    tint = BG,
                    modifier = Modifier.size(36.dp)
                )
            }
            IconButton(onClick = { state.skip(10000L) }, modifier = Modifier.size(42.dp)) { Icon(Icons.Filled.Forward10, contentDescription = "Forward 10 seconds") }
            IconButton(onClick = { state.next() }, modifier = Modifier.size(42.dp)) { Icon(Icons.Filled.SkipNext, contentDescription = "Next") }
            IconButton(onClick = { state.toggleShuffle() }, modifier = Modifier.size(42.dp)) {
                Icon(Icons.Filled.Shuffle, contentDescription = "Shuffle", tint = if (state.shuffle) ACCENT else TEXT)
            }
        }
        if (state.nextTitle.isNotBlank()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.MusicNote, contentDescription = null, tint = ACCENT, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Playing next: " + state.nextTitle, color = ACCENT, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.height(10.dp))
    }
}
