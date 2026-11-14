# Mis Canales

App para Android TV, Fire TV y móviles Android que solo enseña los vídeos de los canales de YouTube que tú eliges.
No hay recomendaciones, ni portada, ni "siguiente vídeo". Los vídeos se ven dentro de la app, con el
reproductor oficial de YouTube para webs: Atrás te devuelve a tu lista, y al acabar el vídeo también.

## Cómo se usa

- **Inicio**: los últimos vídeos de todos tus canales, del más nuevo al más viejo. Arriba hay una píldora
  por canal para filtrar, **Sin ver** (con el número de vídeos nuevos) y **Actualizar**.
- **OK** sobre un vídeo: lo pone y lo marca como visto. En el reproductor: **OK** pausa,
  **◀ ▶** salta 10 segundos y **Atrás** vuelve a la lista.
- **Mantener OK** sobre un vídeo: abre su ficha, con la descripción, "Marcar como visto/no visto",
  "Ir al canal" y "Abrir en YouTube" (en la app de YouTube de la tele).
- Si un vídeo no deja verse fuera de YouTube, se abre solo en la app de YouTube.
- Calidad en la tele: hasta 1440p a 60 fps. El reproductor de YouTube dentro de una WebView del Fire TV se
  quedaba en 720p (descartaba todo lo de 60 fps); la app le pide ella la página del reproductor y le
  añade unos scripts para que se comporte como el Chrome de un PC. El tope está en `MAX_LINES`
  (`PlayerActivity.kt`): a 2160p60 el Fire TV Stick 4K va a saltos.
- **Mantener OK** sobre la píldora de un canal: abre la página del canal.
- **Mis canales**: la lista de canales, el botón para añadir, si se ven o no los Shorts y qué
  reproductor usar (el de Mis Canales o la app de YouTube).
  Al pulsar un canal se ven sus vídeos y el botón **Quitar canal**.
- **Añadir canal**: escribe el `@nombre` del canal tal como sale en YouTube, o pega su enlace.

"NUEVO" sale en los vídeos que no has visto y que se publicaron después de añadir el canal
(o en la semana de antes). La app vuelve a mirar los canales al abrirse si hace más de 15 minutos.

## En el móvil

El mismo APK, instalado en un móvil o una tableta, enseña una interfaz táctil: Inicio con los
vídeos, Canales (con los ajustes) y Añadir. En la app de YouTube, en cualquier vídeo,
**Compartir → Mis Canales** añade su canal.

En el móvil, el vídeo sigue sonando con la pantalla apagada (con play y pausa en el aviso y en la
pantalla de bloqueo), y al salir de la app queda en una ventana flotante. Se quita en Canales →
"Seguir sonando con la pantalla apagada". Ojo: las normas de YouTube para desarrolladores no
permiten la reproducción en segundo plano del reproductor insertado; es una app para uso propio,
y YouTube puede cambiar su reproductor y romperlo.

## Actualizaciones

Cada cambio publica la versión nueva en Releases de GitHub. La app (la de los mayores, no Kids) mira
al abrirse, cada unas horas, si hay una más nueva: en Inicio sale un botón «Actualizar a la …», que la
baja y abre el instalador de Android (hay que confirmar). También en Ajustes (móvil) y en Mis canales
(tele), con la versión que tienes y «Buscar actualizaciones».

La primera vez hay que darle permiso para instalar: en el móvil, la app abre ese ajuste; en el Fire,
Configuración → Mi Fire TV → Opciones para desarrolladores → Instalar aplicaciones desconocidas →
Mis Canales.

## Sincronizar con Home Assistant

Los canales pueden vivir en una lista de tareas de Home Assistant, compartida entre la tele y el
móvil. Añadir o quitar un canal en uno lo hace en el otro, y también se pueden añadir desde la
propia lista de HA escribiendo el `@nombre` del canal (la app lo completa).

1. En HA: Ajustes → Dispositivos y servicios → Añadir integración → **Local To-do**, con el nombre
   **Mis Canales** (queda como `todo.mis_canales`).
2. En HA: tu perfil → Seguridad → **Tokens de acceso de larga duración** → Crear token.
3. En el móvil: Canales → Sincronizar con Home Assistant: la dirección de fuera (DuckDNS), la de
   casa si quieres (se prueba primero) y el token. Al conectar, sube los canales que ya tenías.
4. En la tele, desde el móvil: en la tele, Mis canales → **Home Assistant** (o, sin canales aún,
   «Traer mis canales de Home Assistant»): sale un código. En el móvil, Canales → Sincronizar con
   Home Assistant → **Pasar a la tele**: elige la tele y escribe el código. Va por la wifi de casa
   (la tele se anuncia en la red solo mientras esa pantalla está abierta, y tras cinco códigos mal deja
   de escuchar). En la misma pantalla de la tele se puede poner también a mano (con el teclado de la
   app Amazon Fire TV del móvil se puede pegar el token).

   O por ADB. El `-S` cierra la app antes, para que reciba los datos aunque estuviera abierta:

   ```sh
   adb shell am start -S -n es.miscanales/.MainActivity \
     --es ha_url "https://micasa.duckdns.org:8123" \
     --es ha_local_url "http://192.168.1.10:8123" \
     --es ha_token "EL_TOKEN"
   ```

En la lista, cada canal es un elemento con su nombre; en la descripción van su ID y su foto (y,
si los tiene, «niños» y «categoría: Música»).

## Categorías

Cada canal puede tener una categoría (Música, Noticias…), que se guarda también en Home Assistant.

- **Ponerla**: en el móvil, en Canales, el botón «Categoría» de cada canal (una de las que ya hay,
  «Sin categoría» o una nueva); en la tele, dentro del canal, «Poner categoría».
- **Inicio, en el móvil y en la tele**: arriba, una fila con «Todas», las categorías y «Otros» (los
  canales sin categoría); debajo, la de siempre (Todos, Sin ver, Seguir viendo y los canales). Al
  elegir una categoría, solo salen los vídeos de sus canales, y la fila de debajo se queda con esos
  canales («Sin ver» y «Seguir viendo» también).
- **Tele**: con «Todas» y «Todos», Inicio va por filas, una por categoría, con «Ver todos» al final.
- La reproducción continua sigue la lista desde la que abriste el vídeo: una categoría entera, seguida.
- **Siguiente y anterior**: en la tele, los botones de avance y retroceso rápido del mando (◀ ▶ siguen
  saltando 10 s); en el móvil, al tocar la pantalla salen a los lados de la pausa, y están también en
  el aviso de reproducción (y en los auriculares o el coche por Bluetooth). «Anterior» vuelve al
  principio del vídeo si ya lleva un rato, y si no, al que sonó antes.
- **Aleatorio** (en la fila de los filtros): mezcla lo que estás viendo (una categoría, «Sin ver»…) y
  lo pone seguido. Con un canal elegido, mezcla entre unos cien vídeos suyos (o los que tenga).

## Reproducción continua y ventana flotante

- **Reproducción continua** (en Ajustes, encendida de entrada; en Kids, en sus ajustes y apagada): al
  acabar un vídeo sale «Siguiente: …» unos segundos y se pone el siguiente de la lista desde la que
  lo abriste (un canal, Inicio, una búsqueda…), sin salir del reproductor. En la tele, OK lo pone ya
  y Atrás sale; en el móvil, «Ver ya» y «Cancelar». Sigue también con la pantalla apagada y en la
  ventana flotante.
- **Ventana flotante** (móvil, no en Kids): en el reproductor, Atrás o «⌄ Minimizar» dejan el vídeo
  en una ventana pequeña y se sigue por la app. Tocándola vuelve a pantalla completa; arrastrándola
  fuera se cierra. En la tele, Atrás sale del vídeo como siempre.

## Mis Canales Kids

La app trae un segundo icono, **Mis Canales Kids** (ámbar, con una estrella), para los niños. Es el
mismo programa y la misma lista de canales, pero siempre en modo niños:

- Solo los canales marcados **Para niños**: en el móvil, en Canales, el botón de cada canal; en la
  tele, dentro de cada canal. Se guarda también en Home Assistant (una línea «niños» en la
  descripción del canal), así que se marca en un aparato y sale en todos. Kids lo ve al volver a ella
  (en el mismo aparato, al momento; con Home Assistant, desde otro aparato también).
- En la app de los mayores, los canales «Para niños» no salen en Inicio, el buscador ni los avisos
  (sí en Canales, para cambiarlos), salvo con «Ver aquí también los canales de niños» (en el móvil,
  en Ajustes; en la tele, en Mis canales).
- Sin buscar en YouTube, sin añadir canales, sin enlaces a YouTube desde el reproductor (y si un vídeo
  no se deja ver dentro de la app, no se abre en la app de YouTube) y sin sugerencias al acabar.
- Sus vídeos vistos y por dónde van, aparte de los de los mayores (y sin sincronizar).
- **Ajustes** (el candado; piden el PIN de los mayores, que se crea la primera vez): tiempo máximo al
  día (al acabarse, el vídeo se para; con el PIN, media hora más) y **modo peque** (miniaturas
  grandes y sin textos). En la tele, el PIN se escribe con el teclado de la pantalla.
- El PIN es de cada aparato. En la app de los mayores, **Pedir PIN al abrir** la cierra con el PIN
  (al abrirla y al volver a ella con Home), para los aparatos que usan también los niños.

## Buscar

En el móvil, la pestaña **Buscar**; en la tele, **Buscar** en el menú de la izquierda.

- **Mis canales**: busca entre los vídeos de tus canales, también los antiguos (lo ya cargado sale
  al momento, y además se busca dentro de cada canal en YouTube).
- **Todo YouTube**: vídeos y canales de todo YouTube, y nada más: ni recomendaciones ni vídeos
  relacionados. Un vídeo se puede ver sin añadir su canal. Al tocar un canal se ven sus vídeos
  (de 30 en 30) y el botón para añadirlo. Al final, **Más resultados**.
- En un canal tuyo, al final de sus vídeos, **Ver vídeos anteriores** carga los más antiguos que los
  15 últimos.

Todo sale de las páginas de YouTube, sin clave ni cuenta (`Search.kt`). Si YouTube cambia su web y
deja de funcionar, ahí es donde hay que tocar; `.github/workflows/explorar.yml` enseña cómo vienen
los datos ahora.

## Avisos de vídeos nuevos en el móvil

La app del móvil mira tus canales cada media hora, más o menos (Android junta estas tareas para
gastar menos batería), y avisa de cada vídeo nuevo con su miniatura. Al tocar el aviso, el vídeo
se abre en Mis Canales, no en la app de YouTube. Se apaga en Canales → Ajustes → Avisar de vídeos
nuevos, y ahí mismo hay un botón para probar cómo queda el aviso.

- La primera vez, y con cada canal recién añadido, solo se apunta lo que ya hay: no avisa de lo anterior.
- Solo se avisa de lo publicado en los dos últimos días y que no hayas visto ya.
- Si te llegan también los avisos de Telegram de Home Assistant, puedes apagar esa automatización.

## Ver en la tele desde el móvil

En el móvil, mantén pulsado un vídeo y toca **📺 Ver en la tele**, o tócalo debajo del vídeo
mientras lo ves: Home Assistant despierta el Fire TV y le abre Mis Canales con ese vídeo, desde
donde ibas (el móvil se pone en pausa). Funciona también fuera de casa.

Lo que hace falta en Home Assistant:

1. En el Fire TV: Ajustes → Mi Fire TV → Opciones para desarrolladores → **Depuración ADB** activada.
2. En HA: Ajustes → Dispositivos y servicios → Añadir integración → **Android Debug Bridge**,
   con la IP del Fire TV y el puerto 5555. La primera vez sale en la tele un aviso de depuración:
   marca "Permitir siempre desde este ordenador" y acepta.
3. En la app del móvil, en Canales → Ajustes → Ver en la tele, toca **Buscar** y luego **Guardar**
   (si HA solo tiene una tele, se encuentra sola la primera vez).

Por dentro, HA ejecuta en la tele (acción `androidtv.adb_command`):

```
input keyevent KEYCODE_WAKEUP; am start -n es.miscanales/.MainActivity --activity-clear-top --activity-single-top --es play ID_DEL_VIDEO --ei start SEGUNDO
```

## Aviso de vídeos nuevos por Telegram

`homeassistant/mis_canales.yaml` es un package de Home Assistant: cada 30 minutos lee
`todo.mis_canales`, mira el feed de cada canal (sin Shorts) y manda por Telegram los vídeos que
aún no ha avisado. La primera vez solo los apunta. Cómo se envía el aviso está en un solo sitio,
el script `mis_canales_enviar`. Para probarlo, lanza el evento `mis_canales_probar`.

## De dónde saca los vídeos

Del feed RSS público de cada canal (`youtube.com/feeds/videos.xml`), que da sus últimos 15 vídeos.
Sin cuenta de Google y sin clave de la API. Para quitar los Shorts usa la lista "UULF" del canal.
Los canales, los vídeos vistos y los ajustes se guardan en la tele.

## Compilar

Al subir el código a GitHub, el workflow `.github/workflows/apk.yml` compila el APK y lo deja en
**Releases → latest** como `mis-canales.apk`.

El APK se firma siempre con la misma clave (`keystore/mis-canales.p12`), para que cada versión
se pueda instalar encima de la anterior sin perder los canales. Su contraseña va en el secreto
`SIGNING_PASSWORD` del repositorio (Settings → Secrets and variables → Actions). Sin ese secreto
el workflow compila, pero no publica.

Para compilar en un PC hace falta JDK 21 y el Android SDK (plataforma 37):

```sh
./gradlew assembleRelease
```

## Instalar en la tele

1. En la tele, activa las opciones de desarrollador y la depuración ADB.
2. `adb connect <ip-de-la-tele>:5555` y acepta el aviso en la tele.
3. `adb install -r mis-canales.apk`

Hace falta Android 8 o posterior (en Fire TV, Fire OS 7 o posterior) y la app de YouTube.

## Créditos

La base de la interfaz para TV viene de [HackerTube for TV](https://github.com/ephraimduncan/hackertube.tv)
(MIT). Los iconos son [Material Symbols](https://github.com/google/material-design-icons) de Google (Apache 2.0).
