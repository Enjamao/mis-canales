package es.miscanales

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

class MainActivity : ComponentActivity() {
    private lateinit var feed: Feed

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = Store(this)
        feed = Feed(store)
        setContent {
            MisCanalesTheme { App(store, feed) }
        }
    }

    override fun onResume() {
        super.onResume()
        // al abrir la app, y al volver de YouTube o de otra app, si hace rato de la última carga
        feed.reloadIfStale()
    }
}
