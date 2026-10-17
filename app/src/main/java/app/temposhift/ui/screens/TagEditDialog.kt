package app.temposhift.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.temposhift.R
import app.temposhift.data.Song
import app.temposhift.ui.ACCENT
import app.temposhift.ui.BG
import app.temposhift.ui.PANEL
import app.temposhift.ui.TEXT

@Composable
fun TagEditDialog(song: Song, onDismiss: () -> Unit, onSave: (String, String, String) -> Unit) {
    val unknownTitle = stringResource(R.string.unknown_title)
    val unknownArtist = stringResource(R.string.unknown_artist)
    val unknownAlbum = stringResource(R.string.unknown_album)
    var title by remember { mutableStateOf(if (song.title == unknownTitle) "" else song.title) }
    var artist by remember { mutableStateOf(if (song.artist == unknownArtist) "" else song.artist) }
    var album by remember { mutableStateOf(if (song.album == unknownAlbum) "" else song.album) }
    val colors = TextFieldDefaults.colors(focusedContainerColor = BG, unfocusedContainerColor = BG)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PANEL,
        titleContentColor = TEXT,
        title = { Text(stringResource(R.string.menu_edit_tags)) },
        text = {
            Column {
                TextField(value = title, onValueChange = { title = it }, singleLine = true, label = { Text(stringResource(R.string.tag_title)) }, colors = colors)
                Spacer(Modifier.height(8.dp))
                TextField(value = artist, onValueChange = { artist = it }, singleLine = true, label = { Text(stringResource(R.string.tag_artist)) }, colors = colors)
                Spacer(Modifier.height(8.dp))
                TextField(value = album, onValueChange = { album = it }, singleLine = true, label = { Text(stringResource(R.string.tag_album)) }, colors = colors)
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(title.trim(), artist.trim(), album.trim()) }) { Text(stringResource(R.string.save), color = ACCENT) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}
