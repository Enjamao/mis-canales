# Mis Canales por dentro

Cómo está hecha la app, dónde está cada cosa, cómo se compila, cómo se prueba y cómo se publica una
versión. Para usarla y configurarla, mira el [README](../README.md).

## Resumen

- **Kotlin + Jetpack Compose**. En la tele, Compose para TV (`androidx.tv.material3`); en el móvil,
  Material 3. Un solo APK: al arrancar mira si el aparato es una tele (`isTv()` en `App.kt`: modo
  televisión, `leanback` o `amazon.hardware.fire_tv`) y enseña una interfaz u otra.
- Android 8 o posterior (`minSdk 26`), `targetSdk 36`. R8 activado en release.
- Paquete `es.miscanales`. Todo el código está en `app/src/main/java/es/miscanales/`.
- Sin servidor propio, sin cuenta de Google y sin clave de la API de YouTube. Los datos salen de:
  - el feed RSS público de cada canal,
  - las páginas de YouTube (búsqueda y vídeos anteriores),
  - el reproductor de YouTube para webs,
  - opcionalmente, Home Assistant (para compartir entre aparatos),
  - SponsorBlock (patrocinios),
  - GitHub Releases (actualizaciones).

## Dónde está cada cosa

### Arranque, perfiles y datos

| Archivo | Qué hay |
|---|---|
| `MainActivity.kt` | La actividad de la app. `KidsActivity` y `MontseActivity` son subclases con su icono y su `taskAffinity`. Recoge los intents: compartir un enlace, `play`/`start` (Ver en la tele y avisos) y `ha_url`/`ha_token`/`ha_local_url`/`ha_entity` (configurar Home Assistant por ADB). |
| `Profiles.kt` | `Profile` (Main y Montse, que es Canales M): su archivo de preferencias, su caché, su color y su sufijo para las listas de Home Assistant. `Launcher`: enseñar u ocultar cada icono en este aparato. |
| `Store.kt` | Lo guardado: canales, vistos, ajustes. Un `Store` por perfil (o Kids); lo del aparato va aparte. |
| `Library.kt` | Añadir, quitar y sincronizar canales, categorías, «Para niños» y vistos, con Home Assistant si lo hay. |
| `Progress.kt` | Por dónde vas en cada vídeo (`Progresses`) y su sincronización con Home Assistant (`ProgressSync`). |
| `Playlists.kt` | Listas de reproducción, una instancia por perfil, con su sincronización. «Ver más tarde» es una lista más (`WATCH_LATER`), que sale siempre la primera. |
| `History.kt` | El historial: los vídeos abiertos en este aparato, por perfil (no se sincroniza). Lo apunta el reproductor (`PlayerActivity`) al poner cada vídeo. |
| `Kids.kt` | El PIN (solo se guarda su huella), el tiempo de Kids por día y «Pedir PIN al abrir». |

### Pantallas

| Archivo | Qué hay |
|---|---|
| `App.kt` | La app de la tele: el menú lateral, las páginas y `playVideo`, que abre un vídeo desde cualquier sitio. |
| `Pages.kt` | La pila de páginas de la tele (cada una guarda su scroll y su foco) y `Feed`, que carga los vídeos de tus canales. |
| `HomeScreen.kt` | Inicio en la tele: filas por categoría, filtros, Aleatorio. |
| `ChannelPicker.kt` | El panel «Canales» de Inicio: qué canales salen y en qué orden (para el móvil y la tele) y la pantalla de la tele. |
| `ChannelScreens.kt` | Mis canales en la tele: la lista, la página de un canal, añadir canal, categorías y todos los ajustes. |
| `DetailsScreen.kt` | La ficha de un vídeo en la tele. |
| `TvSearch.kt`, `TvLists.kt`, `TvHa.kt` | Buscar, Listas y Home Assistant en la tele. |
| `PhoneApp.kt` | Toda la app del móvil: pestañas, Inicio (con «Seguir viendo»), Canales, los Ajustes (con la rueda ⚙), Añadir, Home Assistant, pasar y recibir la conexión. |
| `PhoneLists.kt` | Listas en el móvil. |
| `PhonePicker.kt` | El panel «Canales» en el móvil, desde abajo. |
| `KidsTv.kt`, `KidsPhone.kt` | Mis Canales Kids en la tele y en el móvil, con sus ajustes. |
| `Components.kt`, `Theme.kt`, `Format.kt` | Piezas comunes: pastillas, miniaturas («NUEVO» y la barra de progreso), el título con el color del perfil, colores y textos («hace 3 días»). |

### Vídeos y reproductor

| Archivo | Qué hay |
|---|---|
| `YouTube.kt` | El feed RSS de cada canal y cómo encontrar un canal a partir de un `@nombre`, un enlace o un vídeo. |
| `Durations.kt` | Lo que dura cada vídeo (el feed no lo trae): lo apuntado, para todo el aparato. `Feed` lo busca en la pestaña Vídeos de cada canal. |
| `Search.kt`, `SearchState.kt` | Buscar en YouTube y los vídeos anteriores de un canal, leyendo los datos de sus páginas. |
| `PlayerActivity.kt` | El reproductor: el de YouTube para webs dentro de una WebView, con los mandos, siguiente/anterior, reproducción continua, ventana flotante, «Ver en la tele», progreso, SponsorBlock y el tiempo de Kids. |
| `EmbedPatch.kt` | En la tele, la app pide la página del reproductor y le mete unos scripts antes que los de YouTube, para que se porte como el Chrome de un PC. Así se consigue más calidad. |
| `PlaybackService.kt` | En el móvil: sigue sonando con la pantalla apagada, con el aviso y los botones multimedia. |
| `SponsorBlock.kt` | Los tramos de patrocinio. Se pregunta solo por los 4 primeros caracteres de la huella del vídeo, para no decir cuál es. |

### Avisos, Home Assistant y actualizaciones

| Archivo | Qué hay |
|---|---|
| `NewVideos.kt` | Avisos de vídeos nuevos en el móvil: tarea periódica (JobScheduler) y alarma para el reposo, por perfil. El botón «Silenciar este canal» de cada aviso lo recoge `MuteReceiver`. |
| `HomeAssistant.kt` | La API REST de Home Assistant: listas de tareas (`todo.get_items`, `add_item`, `update_item`, `remove_item`), teles con ADB y `androidtv.adb_command`. Prueba primero la dirección de casa y luego la de fuera. |
| `Pairing.kt` | Pasar la conexión de un aparato a otro por la wifi (NSD), con código. |
| `Tele.kt` | «Ver en la tele»: el comando ADB que manda Home Assistant. |
| `Updater.kt` | Mirar si hay versión nueva en GitHub, bajarla e instalarla. |

### Otros archivos del repositorio

| Ruta | Qué hay |
|---|---|
| `app/src/main/AndroidManifest.xml` | Las tres actividades de los iconos, el reproductor, el servicio y los avisos. |
| `app/src/main/res/` | Iconos (`mipmap-*`), banners de la tele (`drawable-xhdpi`) y los iconos de Material Symbols (`drawable`). |
| `app/build.gradle.kts` | `versionCode` y `versionName`, la firma y la variante `prueba`. |
| `homeassistant/mis_canales.yaml` | Package de Home Assistant: avisos de vídeos nuevos por Telegram. |
| `keystore/mis-canales.p12` | La clave de firma. La contraseña no está en el repositorio. |
| `.github/workflows/` | `apk.yml`, `prueba.yml` y `explorar.yml` (ver abajo). |
| `.github/prueba/` | Los guiones de las pruebas en el emulador y `explorar.py`. |

## Lo que se guarda en cada aparato

En `SharedPreferences`:

| Archivo | Qué guarda |
|---|---|
| `store` | Mis Canales (canales, vistos, progreso, listas, historial, ajustes) y, además, lo del aparato: `ha` (conexión con Home Assistant, en JSON), `pin`, `askPin`, `kidsLimit`, `kidsSmall`, `kidsInAdult`, y lo de Kids (`kids_progress`…) |
| `store_montse` | Lo de Canales M |
| `nuevos` | Los vídeos y canales ya vistos por los avisos, por perfil (`videos`, `videos_montse`…) |

Y en archivos: `videos.json` y `videos_montse.json`, la última carga de vídeos de cada perfil, para que
Inicio salga al momento, y `duraciones.json`, lo que dura cada vídeo (hasta 4000, para todo el aparato).

La conexión con Home Assistant se guarda con la lista base (`todo.mis_canales`). Cada perfil le añade su
sufijo al usarla (`Profile.forProfile`: `todo.mis_canales_montse`). Las de progreso y listas son la de
canales más `_progreso` y `_listas`.

## Cómo funciona

### Vídeos de los canales

- `https://www.youtube.com/feeds/videos.xml?channel_id=UC…` da los 15 últimos vídeos de un canal.
- Sin Shorts: se pide la lista `UULF…` del canal (sus vídeos normales) en lugar del canal.
- `Feed` (en `Pages.kt`) carga todos los canales a la vez. Guarda la carga en `videos*.json` y vuelve a
  cargar al abrir la app si hace más de 15 minutos.
- «NUEVO»: no visto, y publicado después de añadir el canal o en la semana de antes.
- Lo que dura: el feed no lo trae. Después de cada carga, `Feed.fillDurations` pide la primera página de la
  pestaña Vídeos de los canales con vídeos sin duración (de 4 en 4) y la apunta en `Durations`. Los que no
  salen ahí (Shorts, directos) se apuntan sin duración para no volver a buscarlos, salvo los de las
  últimas 6 horas.

### Buscar y vídeos anteriores

`Search.kt` lee la página de resultados de YouTube (el `ytInitialData` que lleva dentro) y sigue las
continuaciones para «Más resultados». Lo mismo para los vídeos de un canal más allá de los 15 del feed,
de 30 en 30, en `SearchState.kt`.

YouTube cambia ese formato de vez en cuando. En 0.17.3, por ejemplo, empezó a mandar los vídeos como
`lockupViewModel` y el canal salía con «?». Para ver cómo vienen ahora: Actions → **Explorar** → Run
workflow, con lo que quieras buscar. Enseña la estructura de la respuesta.

### El reproductor

- Es el reproductor de YouTube para webs (`youtube.com/embed`) dentro de una WebView, manejado con su
  API de JavaScript. La app pone sus propios controles y teclas encima.
- En la tele, `EmbedPatch.kt` pide la página del reproductor con OkHttp y le mete scripts al principio,
  para que diga que es Chrome de PC con pantalla grande y no quite los formatos de 60 fps. El tope está
  en `MAX_LINES`, en `PlayerActivity.kt` (1080). A 2160p60 el Fire TV Stick 4K va a saltos, y a 1440p
  alguna vez se cortaba.
- Si un vídeo no deja verse insertado, se abre en la app de YouTube (salvo en Kids).
- Siguiente/anterior y la reproducción continua usan la cola que se pasa al abrir el vídeo (lo que había
  en pantalla a partir de él).
- En el móvil, `PlaybackService` mantiene la app viva con la pantalla apagada y pone el aviso multimedia.
- El progreso se apunta mientras suena y se sube a Home Assistant (`ProgressSync.push`).

### Home Assistant

Todo va por la API REST, con el token de larga duración:

- Leer una lista: `POST /api/services/todo/get_items?return_response`.
- Añadir: `todo.add_item`. Cambiar: `todo.update_item`. Quitar: `todo.remove_item`.

Cada petición prueba primero la dirección de casa (con un tiempo de espera corto) y después la de fuera.
Un 401 es «no acepta el token». La dirección de casa suele ser `http://` sin cifrar: por eso el manifiesto
lleva `android:usesCleartextTraffic="true"`, que Android pide para eso desde la versión 9.

**Canales**: un elemento por canal; el nombre es el del canal. La descripción tiene una línea por dato:
el ID `UC…`, la URL de la foto, `niños`, `categoría: X` y `sin avisos` (silenciado). Si un elemento no tiene ID (alguien escribió
`@nombre` a mano en Home Assistant), la app busca el canal y lo completa.

**Progreso**: un elemento por vídeo; el nombre es `ID · título`, y la descripción el JSON de `Progress`
(`pos`, `dur`, `at`). De cada vídeo vale el más reciente. Se quedan los 400 últimos.

**Listas**: un elemento por vídeo; el nombre es `lista · título`, y la descripción el JSON de
`PlaylistEntry` (el vídeo sin su descripción). Lo añadido sin conexión queda marcado `pending` y se sube
después.

### Pasar la conexión (Pairing)

1. El que recibe (una tele, o un móvil con «Recibir de otro móvil») abre un puerto TCP.
2. Lo anuncia por NSD como `_miscanales._tcp.` con el nombre del aparato.
3. Enseña un código de 4 cifras al azar.
4. El que manda busca ese servicio y envía una línea de JSON: `{"code": "1234", "config": {…}}`.
5. El que recibe prueba Home Assistant leyendo la lista de canales del icono abierto
   (`Library.connect`). Si contesta, guarda la conexión y sube los canales que tenía; si no, no guarda
   nada. Contesta con otra línea, con el resultado.

Tras 5 códigos mal deja de escuchar. El código solo sale en el registro en la variante de prueba.

### Ver en la tele

`Tele.kt` pide a Home Assistant `androidtv.adb_command` sobre la entidad `media_player` de la tele. El
comando es `input keyevent KEYCODE_WAKEUP; am start -n es.miscanales/<actividad del perfil> … --es play
ID --ei start SEGUNDO`.

### Avisos de vídeos nuevos

`NewVideos.kt` programa una tarea periódica de 30 minutos (JobScheduler) y una alarma para que siga
mirando con el móvil en reposo. Lo hace solo si algún perfil con su icono en este móvil tiene los avisos
encendidos.

Cada perfil apunta los vídeos que ya conoce. La primera vez, y con cada canal nuevo, solo los apunta. Avisa
de lo de los dos últimos días que no esté visto: hasta 6 avisos sueltos, y si hay más, solo el resumen.

### Actualizaciones

`apk.yml` publica en la release `latest` el APK y un `version.json` (`{"versionCode":42,
"versionName":"0.19.0"}`). `Updater.kt`:

1. Lee `version.json` al abrir la app, como mucho cada 6 horas (o a mano).
2. Si `versionCode` es mayor, baja `mis-canales.apk` a la caché.
3. Lo abre con el instalador de Android, con un `FileProvider` (`res/xml/update_paths.xml`).

## Compilar

Con el código subido a GitHub no hace falta compilar en ningún sitio: cada push a `main` lo hace
`apk.yml`.

Para compilar en un PC hace falta JDK 21 y el Android SDK (plataforma 37):

```sh
./gradlew assembleRelease
```

Sin la contraseña de la clave, en local se firma con la clave de depuración, y ese APK no se puede
instalar encima del publicado.

### La firma

El APK se firma siempre con `keystore/mis-canales.p12`, para que cada versión se instale encima de la
anterior sin perder nada. Su contraseña va en el secreto `SIGNING_PASSWORD` del repositorio (Settings →
Secrets and variables → Actions). Sin ese secreto el workflow compila, pero no publica. La huella
SHA-256 del certificado empieza por `d434e95e`; el workflow la escribe en el registro de cada
compilación.

**No cambies ni pierdas la clave.** Con otra firma, Android no deja instalar encima y habría que
desinstalar, perdiendo lo guardado en cada aparato.

## Publicar una versión nueva

1. Cambia el código.
2. En `app/build.gradle.kts`, sube `versionCode` (de uno en uno) y `versionName` (`0.19.0` → `0.19.1`
   para arreglos, `0.20.0` para cosas nuevas).
3. Pon al día el [README](../README.md) y [cambios.md](cambios.md).
4. Commit y push a `main`. `apk.yml` compila, firma y publica en Releases → latest.
5. Lanza la prueba en el emulador (abajo) y mira el resultado.
6. En los aparatos, la app avisa sola de la versión nueva en Inicio.

Un cambio solo en Home Assistant o en la documentación puede llevar `[skip ci]` en el mensaje del commit,
para no publicar otra vez el mismo APK.

## Pruebas en el emulador

`prueba.yml` se lanza a mano: Actions → **Prueba en emulador** → Run workflow. Desde aquí, con:

```sh
gh workflow run prueba.yml --ref main
```

Compila la variante **prueba**: la de release, pero depurable y firmada con la clave de depuración, con
avisos cada 3 minutos en vez de 30. Luego la prueba en dos emuladores a la vez:

- **prueba** (móvil, Android 15, `.github/prueba/prueba.sh`): Inicio, canales, categorías, Kids, PIN,
  avisos, búsqueda, Home Assistant y pasar a la tele, actualizaciones, aleatorio, varios canales,
  siguiente/anterior, listas y Canales M.
- **tele** (Android TV 11 a 720p, `.github/prueba/prueba-tv.sh`): lo mismo con el mando (D-pad, OK,
  play/pausa), Kids en la tele, Home Assistant por código, listas, actualizaciones y Canales M.

Los guiones van tocando la pantalla (`pulsar "texto"`) o moviendo el foco con las teclas
(`enfocar "texto" KEYCODE_DPAD_DOWN`). Escriben en el registro lo que hay en pantalla en cada paso
(`textos`) y los fallos de la app (`fallos`).

Para preparar casos, escriben en las preferencias de la app con `run-as`. Algunas claves solo se usan en
las pruebas:

- `test_version_code`: hacer como si la app fuera más antigua, para que salga «Actualizar a la …».
- `test_end_after`: hacer como si el vídeo acabara a los N segundos.
- `test_ignore_errors`: no hacer caso de los errores del reproductor del emulador.

Resultados:

- El registro de cada trabajo, en Actions. Si la app se cierra, el fallo sale traducido con el
  `mapping.txt` de R8, en el paso «Fallos traducidos».
- Las capturas: en los artefactos `capturas-movil` y `capturas-tele`, y además en las ramas
  `capturas-movil` y `capturas` del repositorio (`git fetch origin capturas-movil`).

## Explorar YouTube

`explorar.yml` (Actions → **Explorar** → Run workflow, con un texto a buscar) ejecuta
`.github/prueba/explorar.py`. Este pide a YouTube una búsqueda, la cuadrícula de vídeos de un canal y su
continuación, y enseña cómo vienen los datos. Sirve para adaptar `Search.kt` cuando YouTube cambia su
web.

## Costumbres del proyecto

- Todo en español: textos de la app, comentarios, mensajes de commit y documentación.
- Textos de la app cortos y claros, para leerlos en la tele a distancia.
- En la tele, todo tiene que poder hacerse con el mando, también con el de la tele Samsung (que no sabe
  mantener OK: por eso play/pausa hace lo mismo).
- Cada cambio visible va con su prueba en `prueba.sh` o `prueba-tv.sh`.
- Lo que se configura con Home Assistant tiene que seguir funcionando sin él (cada aparato con lo suyo).
