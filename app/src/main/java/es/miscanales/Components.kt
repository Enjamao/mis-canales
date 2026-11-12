package es.miscanales

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage

/** La tarjeta de un vídeo: OK lo pone en YouTube, mantener OK abre su ficha. */
@Composable
fun VideoCard(video: Video, modifier: Modifier = Modifier, showChannel: Boolean = true) {
    val nav = LocalNavigator.current
    Column {
        Surface(
            onClick = { nav.play(video) },
            onLongClick = { nav.open(DetailsPage(video)) },
            modifier = modifier,
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
            colors = ClickableSurfaceDefaults.colors(containerColor = Colors.bg2, focusedContainerColor = Colors.bg2),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
            border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(3.dp, Colors.text))),
        ) {
            Thumbnail(video)
        }
        Text(
            video.title,
            style = Type.title,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
        )
        if (showChannel) Text(video.channelName, style = Type.sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(video.stats(), style = Type.sub, maxLines = 1)
    }
}

/**
 * La miniatura, con "NUEVO" si no lo has visto, la barra roja de por dónde vas si lo has empezado,
 * y "VISTO" (y más apagada) si lo acabaste.
 */
@Composable
fun Thumbnail(video: Video, modifier: Modifier = Modifier) {
    val store = LocalStore.current
    val progress = store.progress[video.vid]
    val inProgress = progress != null && progress.started
    val watched = store.isWatched(video.vid) && !inProgress
    val new = store.isNew(video)
    Box(modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
        YouTubeImage(video.vid, Modifier.fillMaxSize().alpha(if (watched) .45f else 1f))
        if (progress != null && progress.fraction > 0f) {
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(4.dp).background(Color.White.copy(alpha = .35f))) {
                Box(Modifier.fillMaxWidth(progress.fraction).height(4.dp).background(Colors.accent))
            }
        }
        when {
            new -> Badge("NUEVO", Colors.accent, Modifier.align(Alignment.TopStart).padding(8.dp))
            watched -> Row(
                Modifier.align(Alignment.BottomEnd).padding(8.dp)
                    .background(Color.Black.copy(alpha = .8f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(painterResource(R.drawable.ic_check), contentDescription = null, Modifier.size(14.dp), tint = Color.White)
                Text("VISTO", style = Type.badge, modifier = Modifier.padding(start = 4.dp))
            }
            else -> {}
        }
        // la duración (en los resultados de búsqueda y en los vídeos anteriores de un canal)
        if (video.duration.isNotBlank() && !watched) {
            Text(
                video.duration,
                style = Type.badge,
                modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp)
                    .background(
                        if (video.duration == "EN DIRECTO") Colors.accent else Color.Black.copy(alpha = .8f),
                        RoundedCornerShape(4.dp),
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
fun Badge(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text,
        style = Type.badge,
        modifier = modifier.background(color, RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/** La miniatura de YouTube a buena calidad, o la pequeña si un vídeo no la tiene. */
@Composable
fun YouTubeImage(vid: String, modifier: Modifier = Modifier) {
    var big by remember(vid) { mutableStateOf(true) }
    AsyncImage(
        model = "https://i.ytimg.com/vi/$vid/${if (big) "hqdefault" else "mqdefault"}.jpg",
        contentDescription = null,
        // hqdefault es 4:3 con franjas negras; recortada a 16:9 quedan fuera
        contentScale = ContentScale.Crop,
        onError = { big = false },
        modifier = modifier,
    )
}

/** La foto redonda de un canal, o su inicial si no hay foto. */
@Composable
fun Avatar(channel: Channel?, size: Dp, modifier: Modifier = Modifier) {
    val name = channel?.name.orEmpty()
    Box(
        modifier.size(size).clip(CircleShape).background(Colors.bg3),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name.firstOrNull()?.uppercase() ?: "?",
            color = Colors.text,
            fontSize = (size.value * .42f).sp,
            fontWeight = FontWeight.Bold,
        )
        val avatar = channel?.avatar
        if (avatar != null) {
            AsyncImage(
                model = avatar,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** Las píldoras de YouTube: blancas si están elegidas, con borde si tienen el foco. */
@Composable
fun Chip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    @DrawableRes icon: Int? = null,
    shape: Shape = RoundedCornerShape(8.dp),
    trailing: String? = null,
    onLongClick: (() -> Unit)? = null,
) {
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) Colors.text else Colors.bg2,
            contentColor = if (selected) Colors.bg else Colors.text,
            focusedContainerColor = if (selected) Colors.text else Colors.bg3,
            focusedContentColor = if (selected) Colors.bg else Colors.text,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(2.dp, Colors.text), shape = shape)),
    ) {
        Row(
            Modifier.height(36.dp).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (icon != null) Icon(painterResource(icon), contentDescription = null, Modifier.size(20.dp))
            Text(text, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            if (trailing != null) Text(trailing, fontSize = 12.sp, color = LocalContentColor.current.copy(alpha = .6f))
        }
    }
}

/** Un botón redondo, como los de la ficha de un vídeo. */
@Composable
fun Pill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    selected: Boolean = false,
) = Chip(text, onClick, modifier, selected, icon, CircleShape)

@Composable
fun Wordmark(modifier: Modifier = Modifier) {
    Text(
        buildAnnotatedString {
            append("Mis ")
            withStyle(SpanStyle(color = Colors.accent)) { append("Canales") }
        },
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.6).sp,
        color = Colors.text,
        modifier = modifier,
    )
}

@Composable
fun PageHead(title: String, subtitle: String? = null) {
    Column(Modifier.padding(top = 8.dp, bottom = 12.dp)) {
        Text(title, style = Type.pageTitle)
        if (subtitle != null) Text(subtitle, style = Type.sub.copy(fontSize = 14.sp), modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
fun Status(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = Type.sub.copy(fontSize = 14.sp, lineHeight = 20.sp),
        modifier = modifier.fillMaxWidth().padding(vertical = 32.dp),
        textAlign = TextAlign.Center,
    )
}

/**
 * Recuerda que este elemento tenía el foco en [page], y se lo devuelve cuando vuelves a la página.
 * Si no hay ninguno recordado, el foco va al primero ([first]).
 */
@Composable
fun Modifier.restoreFocus(page: Page, key: String, first: Boolean = false): Modifier {
    val target = page.focus?.let { it == key } ?: first
    if (target) {
        LaunchedEffect(page) {
            if (!page.needsFocus) return@LaunchedEffect
            withFrameNanos { }
            // una lista perezosa puede componer un elemento antes de enseñarlo, y aún no puede coger el foco
            if (runCatching { page.focusRequester.requestFocus() }.getOrDefault(false)) page.needsFocus = false
        }
    }
    return onFocusChanged { if (it.isFocused) page.focus = key }
        .then(if (target) Modifier.focusRequester(page.focusRequester) else Modifier)
}
