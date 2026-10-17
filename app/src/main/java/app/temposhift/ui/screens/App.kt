package app.temposhift.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import app.temposhift.R
import app.temposhift.data.DeviceScanner
import app.temposhift.data.Library
import app.temposhift.data.Song
import app.temposhift.player.PlayerState
import app.temposhift.ui.ACCENT
import app.temposhift.ui.BG
import app.temposhift.ui.DIM
import app.temposhift.ui.Line
import app.temposhift.ui.MiniPlayer
import app.temposhift.ui.PANEL
import app.temposhift.ui.SongDialogs
import app.temposhift.ui.SongMenuHost
import app.temposhift.ui.TEXT
import app.temposhift.ui.blockTaps
import app.temposhift.ui.rememberCover
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Where the user is. The Library is always underneath; everything else slides in over it. */
sealed interface Route {
    object Library : Route
    object Settings : Route
    object Folders : Route
    data class Album(val name: String) : Route
    data class Artist(val name: String) : Route
    data class Folder(val path: String) : Route
}

@Composable
fun TempoShiftApp(state: PlayerState) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val audioPerm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(ctx, audioPerm) == PackageManager.PERMISSION_GRANTED) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { _ ->
        granted = ContextCompat.checkSelfPermission(ctx, audioPerm) == PackageManager.PERMISSION_GRANTED
    }

    suspend fun reloadSongs() {
        state.songs = withContext(Dispatchers.IO) { Library.load(ctx, state.minDurationSec, state.db.tagOverrides()) }
        state.sync()
    }

    LaunchedEffect(Unit) {
        val wanted = if (Build.VERSION.SDK_INT >= 33) arrayOf(audioPerm, Manifest.permission.POST_NOTIFICATIONS) else arrayOf(audioPerm)
        launcher.launch(wanted)
        state.refreshPlaylists()
    }
    LaunchedEffect(granted, state.minDurationSec) {
        if (granted) reloadSongs()
    }
    LaunchedEffect(Unit) {
        while (true) {
            state.tick()
            delay(200)
        }
    }

    // ---- navigation ----
    var stack by remember { mutableStateOf<List<Route>>(listOf(Route.Library)) }
    var showPlayer by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)

    fun push(route: Route) { showPlayer = false; stack = stack + route }
    fun pop() { if (stack.size > 1) stack = stack.dropLast(1) }

    val host = remember(state) {
        SongMenuHost(state, { name -> push(Route.Album(name)) }, { name -> push(Route.Artist(name)) })
    }

    val pickAudio = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            try { ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) { }
            state.openUri(uri)
        }
    }

    fun runScan() {
        scope.launch {
            Toast.makeText(ctx, R.string.scanning, Toast.LENGTH_SHORT).show()
            val n = DeviceScanner.scan(ctx)
            reloadSongs()
            Toast.makeText(ctx, ctx.getString(R.string.scan_done, n), Toast.LENGTH_LONG).show()
        }
    }
    fun refreshLibrary() {
        scope.launch {
            Library.clearArtCache()
            reloadSongs()
            Toast.makeText(ctx, R.string.refreshed_toast, Toast.LENGTH_SHORT).show()
        }
    }

    BackHandler(enabled = stack.size > 1) { pop() }
    BackHandler(enabled = drawerState.isOpen) { scope.launch { drawerState.close() } }
    BackHandler(enabled = showPlayer) { showPlayer = false }

    val itemColors = NavigationDrawerItemDefaults.colors(
        selectedContainerColor = BG,
        unselectedContainerColor = Color.Transparent,
        selectedIconColor = ACCENT,
        unselectedIconColor = DIM,
        selectedTextColor = ACCENT,
        unselectedTextColor = TEXT
    )
    val top = stack.last()

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = drawerState.isOpen || (!showPlayer && stack.size == 1),
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = PANEL, drawerContentColor = TEXT, windowInsets = WindowInsets(0, 0, 0, 0)) {
                DrawerHeader(state.current)
                Spacer(Modifier.height(10.dp))
                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.library)) },
                    selected = top == Route.Library,
                    onClick = { stack = listOf(Route.Library); scope.launch { drawerState.close() } },
                    icon = { Icon(Icons.Filled.LibraryMusic, contentDescription = null) },
                    colors = itemColors, modifier = Modifier.padding(horizontal = 12.dp)
                )
                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.drawer_folders)) },
                    selected = top == Route.Folders,
                    onClick = { stack = listOf(Route.Library, Route.Folders); scope.launch { drawerState.close() } },
                    icon = { Icon(Icons.Filled.Folder, contentDescription = null) },
                    colors = itemColors, modifier = Modifier.padding(horizontal = 12.dp)
                )
                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.drawer_scan)) },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        runScan()
                    },
                    icon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    colors = itemColors, modifier = Modifier.padding(horizontal = 12.dp)
                )
                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.drawer_picker)) },
                    selected = false,
                    onClick = { scope.launch { drawerState.close() }; pickAudio.launch(arrayOf("audio/*")) },
                    icon = { Icon(Icons.Filled.Description, contentDescription = null) },
                    colors = itemColors, modifier = Modifier.padding(horizontal = 12.dp)
                )
                Spacer(Modifier.height(6.dp))
                Box(Modifier.padding(horizontal = 28.dp)) { Line() }
                Spacer(Modifier.height(6.dp))
                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.drawer_about)) },
                    selected = false,
                    onClick = { scope.launch { drawerState.close() }; showAbout = true },
                    icon = { Icon(Icons.Filled.Info, contentDescription = null) },
                    colors = itemColors, modifier = Modifier.padding(horizontal = 12.dp)
                )
                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.settings)) },
                    selected = top == Route.Settings,
                    onClick = { stack = listOf(Route.Library, Route.Settings); scope.launch { drawerState.close() } },
                    icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                    colors = itemColors, modifier = Modifier.padding(horizontal = 12.dp)
                )
                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.drawer_help)) },
                    selected = false,
                    onClick = { scope.launch { drawerState.close() }; showHelp = true },
                    icon = { Icon(Icons.Filled.Help, contentDescription = null) },
                    colors = itemColors, modifier = Modifier.padding(horizontal = 12.dp)
                )
            }
        }
    ) {
        Box(Modifier.fillMaxSize().background(BG).windowInsetsPadding(WindowInsets.safeDrawing)) {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    LibraryScreen(
                        state = state,
                        host = host,
                        modifier = Modifier.fillMaxSize(),
                        onMenu = { scope.launch { drawerState.open() } },
                        onOpenAlbum = { name -> push(Route.Album(name)) },
                        onOpenArtist = { name -> push(Route.Artist(name)) }
                    )
                    AnimatedContent(
                        targetState = stack,
                        modifier = Modifier.fillMaxSize(),
                        transitionSpec = {
                            val forward = targetState.size >= initialState.size
                            if (forward) {
                                (slideInHorizontally(spring(dampingRatio = 0.9f, stiffness = 380f)) { it / 3 } + fadeIn(tween(220))) togetherWith
                                    fadeOut(tween(160))
                            } else {
                                (fadeIn(tween(160)) togetherWith (slideOutHorizontally(tween(260)) { it / 3 } + fadeOut(tween(220))))
                                    .apply { targetContentZIndex = -1f }
                            }
                        },
                        label = "routes"
                    ) { st ->
                        when (val r = st.last()) {
                            Route.Library -> Spacer(Modifier.fillMaxSize())
                            Route.Settings -> Box(Modifier.fillMaxSize().background(BG).blockTaps()) {
                                SettingsScreen(state, Modifier.fillMaxSize(), onScan = { runScan() }, onRefresh = { refreshLibrary() }) { pop() }
                            }
                            Route.Folders -> FoldersScreen(state, onBack = { pop() }, onOpenFolder = { path -> push(Route.Folder(path)) })
                            is Route.Album -> {
                                val songs = remember(state.songs, r.name) {
                                    state.songs.filter { it.album == r.name }.sortedWith(compareBy<Song>({ it.track }, { it.title.lowercase() }))
                                }
                                val artist = songs.groupBy { it.artist }.maxByOrNull { it.value.size }?.key ?: ""
                                val year = songs.firstOrNull { it.year > 0 }?.year
                                val meta = (if (year != null) year.toString() + " \u00B7 " else "") + stringResource(R.string.n_songs, songs.size)
                                DetailScreen(r.name, artist, meta, songs, state, host) { pop() }
                            }
                            is Route.Artist -> {
                                val songs = remember(state.songs, r.name) {
                                    state.songs.filter { it.artist == r.name }.sortedWith(compareBy<Song>({ it.album.lowercase() }, { it.track }, { it.title.lowercase() }))
                                }
                                val albums = songs.map { it.album }.distinct().size
                                DetailScreen(
                                    r.name,
                                    stringResource(R.string.n_songs, songs.size),
                                    stringResource(R.string.n_albums, albums),
                                    songs, state, host
                                ) { pop() }
                            }
                            is Route.Folder -> {
                                val songs = remember(state.songs, r.path) {
                                    state.songs.filter { it.folder == r.path }.sortedBy { it.title.lowercase() }
                                }
                                DetailScreen(r.path.substringAfterLast('/'), r.path, stringResource(R.string.n_songs, songs.size), songs, state, host) { pop() }
                            }
                        }
                    }
                }
                // the mini player rises from the bottom the first time a song plays
                AnimatedVisibility(
                    visible = state.current != null,
                    enter = slideInVertically(spring(dampingRatio = 0.8f, stiffness = 400f)) { it } + fadeIn(tween(200)),
                    exit = slideOutVertically(tween(200)) { it } + fadeOut(tween(150))
                ) {
                    MiniPlayer(state) { showPlayer = true }
                }
            }

            AnimatedVisibility(
                visible = showPlayer,
                enter = slideInVertically(spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)) { it } + fadeIn(tween(220)),
                exit = slideOutVertically(tween(260)) { it } + fadeOut(tween(200))
            ) {
                NowPlayingScreen(
                    state = state,
                    host = host,
                    onClose = { showPlayer = false },
                    onOpenAlbum = { name -> push(Route.Album(name)) }
                )
            }
        }
    }

    SongDialogs(host)

    if (showAbout) {
        val version = remember {
            try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "" } catch (_: Exception) { "" }
        }
        AlertDialog(
            onDismissRequest = { showAbout = false },
            containerColor = PANEL,
            title = { Text(stringResource(R.string.app_name)) },
            text = {
                Column {
                    Text(stringResource(R.string.tagline), color = ACCENT, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.st_version, version), color = DIM, fontSize = 13.sp)
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.about_text))
                }
            },
            confirmButton = { TextButton(onClick = { showAbout = false }) { Text(stringResource(R.string.close), color = ACCENT) } }
        )
    }
    if (showHelp) {
        AlertDialog(
            onDismissRequest = { showHelp = false },
            containerColor = PANEL,
            title = { Text(stringResource(R.string.drawer_help)) },
            text = { Text(stringResource(R.string.help_text)) },
            confirmButton = { TextButton(onClick = { showHelp = false }) { Text(stringResource(R.string.close), color = ACCENT) } }
        )
    }
}

/** Top of the side menu: the cover of the song that is playing, with its title and artist. */
@Composable
private fun DrawerHeader(song: Song?) {
    val bmp by rememberCover(song, 400)
    Box(
        Modifier.fillMaxWidth().height(200.dp)
            .background(Brush.verticalGradient(listOf(Color(0xFF3A4A42), Color(0xFF111A16))))
    ) {
        val b = bmp
        if (b != null) {
            Image(bitmap = b.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000)))))
        Column(Modifier.align(Alignment.BottomStart).padding(20.dp)) {
            Text(song?.title ?: stringResource(R.string.app_name), color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(song?.artist ?: stringResource(R.string.tagline), color = Color(0xCCFFFFFF), fontSize = 14.sp, maxLines = 1)
        }
    }
}
