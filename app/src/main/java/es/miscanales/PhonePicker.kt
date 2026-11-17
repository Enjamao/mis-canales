@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package es.miscanales

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * El panel «Canales» de Inicio en el móvil, desde abajo: todos los canales (los de la categoría elegida)
 * en cuadrícula, con su foto. Un toque elige solo ese canal y cierra; mantenerlo pulsado lo añade a los
 * elegidos o lo quita, y el panel sigue abierto.
 */
@Composable
fun PhoneChannelsSheet(
    filter: Filter,
    category: String?,
    onFilter: (Filter) -> Unit,
    onDismiss: () -> Unit,
) {
    val store = LocalStore.current
    val feed = LocalFeed.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val ids = filter.channelIds()
    val unseen = unseenByChannel(store, feed.videos)
    val groups = channelGroups(store, category, unseen)
    // cerrar con la animación de bajar, y luego quitarlo
    fun close() {
        scope.launch { state.hide() }.invokeOnCompletion { onDismiss() }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(84.dp),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            item(key = "#head", span = { GridItemSpan(maxLineSpan) }) {
                Column(Modifier.padding(horizontal = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(pickerTitle(store, category), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                        if (ids.isNotEmpty()) {
                            TextButton(onClick = {
                                onFilter(Filter.All)
                                close()
                            }) { Text("Quitar") }
                        }
                    }
                    Hint("Toca un canal para ver sus vídeos. Mantenlo pulsado para elegir varios.")
                    if (ids.size > 1) {
                        Button(onClick = { close() }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                            Text("Ver los ${ids.size} elegidos")
                        }
                    }
                }
            }
            groups.forEach { group ->
                val title = group.title
                if (title != null) {
                    item(key = "#group:$title", span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            title,
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(start = 4.dp, top = 8.dp),
                        )
                    }
                }
                items(group.channels, key = { it.id }) { channel ->
                    val selected = channel.id in ids
                    Column(
                        Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .combinedClickable(
                                onClick = {
                                    onFilter(Filter.OneChannel(channel.id))
                                    close()
                                },
                                onLongClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onFilter(filter.toggled(channel.id))
                                },
                            )
                            .semantics { this.selected = selected }
                            .padding(vertical = 8.dp, horizontal = 2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        PickerAvatar(channel, unseen[channel.id] ?: 0, selected, 56.dp)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            channel.name,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}
