package es.miscanales

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.DrawerValue
import androidx.tv.material3.Icon
import androidx.tv.material3.ModalNavigationDrawer
import androidx.tv.material3.Surface
import androidx.tv.material3.Text

val LocalNavigator = staticCompositionLocalOf<Navigator> { error("No Navigator") }
val LocalStore = staticCompositionLocalOf<Store> { error("No Store") }
val LocalFeed = staticCompositionLocalOf<Feed> { error("No Feed") }

/** El menú lateral. Como en YouTube, el icono va relleno en la sección en la que estás. */
enum class Section(val label: String, @DrawableRes val icon: Int, @DrawableRes val currentIcon: Int) {
    Home("Inicio", R.drawable.ic_home, R.drawable.ic_home_filled),
    Channels("Mis canales", R.drawable.ic_smart_display, R.drawable.ic_smart_display_filled),
    Add("Añadir canal", R.drawable.ic_add, R.drawable.ic_add),
}

@Composable
fun App(store: Store, feed: Feed) {
    val context = LocalContext.current
    val nav = remember { Navigator(context, store) }
    BackHandler(nav.stack.size > 1) { nav.back() }
    CompositionLocalProvider(LocalNavigator provides nav, LocalStore provides store, LocalFeed provides feed) {
        ModalNavigationDrawer(drawerContent = { value ->
            val open = value == DrawerValue.Open
            val current = remember { FocusRequester() }
            Column(
                Modifier.fillMaxHeight().background(Colors.bg).padding(12.dp).selectableGroup()
                    // al entrar en el menú, el foco va a la sección actual y no a la más cercana
                    .focusProperties { onEnter = { current.requestFocus() } }
                    .focusGroup(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Logo(open)
                Spacer(Modifier.height(12.dp))
                for (section in Section.entries) {
                    val isCurrent = section == nav.section
                    SidebarItem(
                        section,
                        isCurrent,
                        open,
                        onClick = { nav.go(section) },
                        modifier = if (isCurrent) Modifier.focusRequester(current) else Modifier,
                    )
                }
            }
        }) {
            // el menú cerrado queda encima del borde izquierdo del contenido
            Box(Modifier.fillMaxSize().background(Colors.bg).padding(start = 80.dp)) {
                key(nav.page) {
                    when (val page = nav.page) {
                        is HomePage -> HomeScreen(page)
                        is ChannelsPage -> ChannelsScreen(page)
                        is AddChannelPage -> AddChannelScreen(page)
                        is ChannelPage -> ChannelScreen(page)
                        is DetailsPage -> DetailsScreen(page)
                    }
                }
            }
        }
    }
}

class Navigator(private val context: Context, private val store: Store) {
    val home = HomePage()
    val stack = mutableStateListOf<Page>(home)
    val page get() = stack.last()

    /** La sección del menú a la que pertenece la página actual. */
    var section by mutableStateOf(Section.Home)
        private set

    fun open(page: Page) {
        stack += page
    }

    fun go(section: Section) {
        this.section = section
        stack.removeRange(1, stack.size)
        when (section) {
            Section.Home -> Unit
            Section.Channels -> stack += ChannelsPage()
            Section.Add -> stack += AddChannelPage()
        }
        page.needsFocus = true
    }

    /** Vuelve a Inicio enseñando solo los vídeos de [filter]. */
    fun goHome(filter: Filter) {
        go(Section.Home)
        home.show(filter)
        home.focus = null
    }

    fun back() {
        stack.removeAt(stack.lastIndex)
        if (stack.size == 1) section = Section.Home
        page.needsFocus = true
    }

    /** Lo pone en el reproductor de la app, que vuelve aquí con Atrás o al acabar (o en YouTube, si lo has elegido). */
    fun play(video: Video) {
        if (store.useYouTubeApp) return playOnYouTubeApp(video)
        store.setWatched(video.vid, true)
        PlayerActivity.start(context, video, store.subtitles)
    }

    /** Lo abre en la app de YouTube de la tele (con sus recomendaciones al volver). */
    fun playOnYouTubeApp(video: Video) {
        store.setWatched(video.vid, true)
        context.playOnYouTube(video.vid)
    }
}

/** Una entrada del menú. El icono no se mueve tanto si el menú está abierto como cerrado. */
@Composable
private fun SidebarItem(
    section: Section,
    current: Boolean,
    open: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val width by animateDpAsState(if (open) 220.dp else 56.dp, label = "sidebar item width")
    Surface(
        onClick = onClick,
        modifier = modifier.width(width).height(48.dp),
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (current) Colors.bg2 else Color.Transparent,
            contentColor = Colors.text,
            focusedContainerColor = Colors.text,
            focusedContentColor = Colors.bg,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Row(Modifier.fillMaxHeight().padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painterResource(if (current) section.currentIcon else section.icon),
                contentDescription = section.label,
                modifier = Modifier.size(24.dp),
            )
            if (open) {
                Text(
                    section.label,
                    fontSize = 15.sp,
                    fontWeight = if (current) FontWeight.Medium else FontWeight.Normal,
                    maxLines = 1,
                    modifier = Modifier.padding(start = 20.dp),
                )
            }
        }
    }
}

/** El botón rojo de "play", con el nombre al lado cuando el menú está abierto. */
@Composable
private fun Logo(open: Boolean) {
    Row(
        Modifier.padding(start = 10.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(36.dp).background(Colors.accent, RoundedCornerShape(9.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(R.drawable.ic_play_arrow_filled),
                contentDescription = null,
                modifier = Modifier.size(26.dp),
                tint = Color.White,
            )
        }
        if (open) {
            Spacer(Modifier.size(12.dp))
            Wordmark()
        }
    }
}
