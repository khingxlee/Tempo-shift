package app.temposhift.ui.screens

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.temposhift.R
import app.temposhift.data.Playlist
import app.temposhift.data.Song
import app.temposhift.player.PlayerState
import app.temposhift.ui.ACCENT
import app.temposhift.ui.AlbumCard
import app.temposhift.ui.BG
import app.temposhift.ui.DIM
import app.temposhift.ui.GroupRow
import app.temposhift.ui.SongMenuHost
import app.temposhift.ui.SongRow

private fun sorted(list: List<Song>, mode: Int): List<Song> = when (mode) {
    1 -> list.sortedByDescending { it.title.lowercase() }
    2 -> list.sortedBy { it.modified }
    3 -> list.sortedByDescending { it.modified }
    else -> list.sortedBy { it.title.lowercase() }
}

@Composable
fun LibraryScreen(
    state: PlayerState,
    host: SongMenuHost,
    modifier: Modifier,
    onMenu: () -> Unit,
    onOpenAlbum: (String) -> Unit,
    onOpenArtist: (String) -> Unit
) {
    var tab by remember { mutableIntStateOf(0) }
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var more by remember { mutableStateOf(false) }
    var sortPage by remember { mutableStateOf(false) }
    var showCreate by remember { mutableStateOf(false) }

    val filtered = remember(state.songs, query) {
        if (query.isBlank()) state.songs
        else state.songs.filter { it.title.contains(query, true) || it.artist.contains(query, true) || it.album.contains(query, true) }
    }
    val list = remember(filtered, state.sortMode) { sorted(filtered, state.sortMode) }

    Column(modifier) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp).height(56.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onMenu) { Icon(Icons.Filled.Menu, contentDescription = stringResource(R.string.cd_menu)) }
            if (searching) {
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.search_hint)) },
                    modifier = Modifier.weight(1f),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent
                    )
                )
                IconButton(onClick = { searching = false; query = "" }) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.cd_close_search))
                }
            } else {
                Text(
                    stringResource(R.string.library),
                    fontSize = 22.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f).padding(start = 8.dp)
                )
                IconButton(onClick = { searching = true }) {
                    Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.cd_search))
                }
                Box {
                    IconButton(onClick = { more = true; sortPage = false }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.cd_more))
                    }
                    DropdownMenu(expanded = more, onDismissRequest = { more = false; sortPage = false }) {
                        if (!sortPage) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.sort_order)) },
                                trailingIcon = { Icon(Icons.Filled.ChevronRight, contentDescription = null) },
                                onClick = { sortPage = true }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.shuffle_all)) },
                                onClick = { more = false; state.shuffleAll() }
                            )
                        } else {
                            Text(
                                stringResource(R.string.sort_order),
                                color = DIM, fontSize = 13.sp,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                            val options = listOf(
                                0 to R.string.sort_name_asc, 1 to R.string.sort_name_desc,
                                2 to R.string.sort_mod_asc, 3 to R.string.sort_mod_desc
                            )
                            options.forEach { opt ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(opt.second)) },
                                    trailingIcon = {
                                        RadioButton(
                                            selected = state.sortMode == opt.first,
                                            onClick = null,
                                            colors = RadioButtonDefaults.colors(selectedColor = ACCENT, unselectedColor = DIM)
                                        )
                                    },
                                    onClick = { state.setSort(opt.first); more = false; sortPage = false }
                                )
                            }
                        }
                    }
                }
            }
        }

        val tabs = listOf(R.string.tab_songs, R.string.tab_albums, R.string.tab_artists, R.string.tab_playlists)
        ScrollableTabRow(selectedTabIndex = tab, containerColor = BG, contentColor = ACCENT, edgePadding = 8.dp) {
            tabs.forEachIndexed { i, res ->
                Tab(
                    selected = tab == i,
                    onClick = { tab = i },
                    text = { Text(stringResource(res), fontWeight = FontWeight.SemiBold) },
                    selectedContentColor = ACCENT,
                    unselectedContentColor = DIM
                )
            }
        }

        Crossfade(targetState = tab, modifier = Modifier.fillMaxSize(), animationSpec = tween(220), label = "tabs") { t ->
            when {
                t == 3 -> PlaylistsTab(state) { showCreate = true }
                list.isEmpty() -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.no_music), color = DIM)
                }
                t == 0 -> LazyColumn(Modifier.fillMaxSize()) {
                    itemsIndexed(list, key = { _, s -> s.id }) { i, s ->
                        SongRow(song = s, active = s.id == state.currentId, host = host, onClick = { state.playList(list, i) })
                    }
                }
                t == 1 -> {
                    val groups = remember(list) { list.groupBy { it.album }.toList().sortedBy { it.first.lowercase() } }
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        gridItems(groups, key = { it.first }) { g ->
                            AlbumCard(g.first, stringResource(R.string.n_songs, g.second.size), g.second.first()) { onOpenAlbum(g.first) }
                        }
                    }
                }
                else -> {
                    val groups = remember(list) { list.groupBy { it.artist }.toList().sortedBy { it.first.lowercase() } }
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(groups, key = { it.first }) { g ->
                            GroupRow(g.first, stringResource(R.string.n_songs, g.second.size), g.second.first()) { onOpenArtist(g.first) }
                        }
                    }
                }
            }
        }
    }

    if (showCreate) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text(stringResource(R.string.new_playlist)) },
            text = {
                TextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.playlist_name)) }
                )
            },
            confirmButton = {
                TextButton(onClick = { state.createPlaylist(name); showCreate = false }) { Text(stringResource(R.string.create)) }
            },
            dismissButton = {
                TextButton(onClick = { showCreate = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

@Composable
private fun PlaylistsTab(state: PlayerState, onNew: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        TextButton(onClick = onNew, modifier = Modifier.padding(horizontal = 8.dp)) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.new_playlist))
        }
        if (state.playlists.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.no_playlists), color = DIM)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(state.playlists, key = { it.id }) { p ->
                    PlaylistRow(p, onClick = { state.playPlaylist(p) }, onDelete = { state.deletePlaylist(p) })
                }
            }
        }
    }
}

@Composable
private fun PlaylistRow(p: Playlist, onClick: () -> Unit, onDelete: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.MusicNote, contentDescription = null, tint = ACCENT)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(p.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(stringResource(R.string.n_songs, p.count), color = DIM, fontSize = 13.sp)
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.cd_delete_playlist), tint = DIM)
        }
    }
}
