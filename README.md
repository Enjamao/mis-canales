# Mis Canales

App para Android TV, Fire TV y móviles Android que solo enseña los vídeos de los canales de YouTube que tú
eliges. No tiene recomendaciones, ni portada, ni «siguiente vídeo» de YouTube. Los vídeos se ven dentro de
la app con el reproductor oficial de YouTube para webs. Atrás te devuelve a tu lista, igual que cuando
acaba el vídeo.

El mismo APK sirve para el móvil y para la tele: en un móvil enseña una interfaz táctil, y en una tele una
para el mando. Trae tres iconos, cada uno con lo suyo:

| Icono | Color | Para quién | Sus datos |
|---|---|---|---|
| **Mis Canales** | rojo | un adulto (el perfil principal) | sus canales, vistos, listas y avisos |
| **Canales M** | morado | otro adulto de casa | los suyos, aparte |
| **Mis Canales Kids** | ámbar, con estrella | los niños | los canales de Mis Canales marcados «Para niños» |

Esta guía explica cómo se usa y cómo se configura. Lo técnico (cómo está hecha, cómo se compila, las
pruebas y cómo publicar una versión) está en [docs/desarrollo.md](docs/desarrollo.md), y lo que ha ido
cambiando en cada versión, en [docs/cambios.md](docs/cambios.md).

## Índice

1. [Instalar](#instalar)
2. [Primeros pasos](#primeros-pasos)
3. [En el móvil](#en-el-móvil)
4. [En la tele](#en-la-tele)
5. [Canales](#canales)
6. [Inicio: filtros, aleatorio, siguiente y anterior](#inicio-filtros-aleatorio-siguiente-y-anterior)
7. [Vistos y «Seguir viendo»](#vistos-y-seguir-viendo)
8. [Listas de reproducción](#listas-de-reproducción)
9. [Buscar](#buscar)
10. [Perfiles: Mis Canales y Canales M](#perfiles-mis-canales-y-canales-m)
11. [Mis Canales Kids](#mis-canales-kids)
12. [Home Assistant](#home-assistant)
13. [Ver en la tele desde el móvil](#ver-en-la-tele-desde-el-móvil)
14. [Avisos de vídeos nuevos](#avisos-de-vídeos-nuevos)
15. [Todos los ajustes](#todos-los-ajustes)
16. [Actualizaciones](#actualizaciones)
17. [Problemas frecuentes](#problemas-frecuentes)
18. [Créditos](#créditos)

## Instalar

Hace falta Android 8 o posterior. En Fire TV, Fire OS 7 o posterior.

La última versión está siempre en
[Releases → latest](https://github.com/Enjamao/mis-canales/releases/tag/latest) como `mis-canales.apk`.
Enlace directo: <https://github.com/Enjamao/mis-canales/releases/download/latest/mis-canales.apk>

**En el móvil**: abre el enlace, baja el APK y ábrelo. Android pide permiso para instalar apps del
navegador la primera vez.

**En la tele (Fire TV)**, por ADB desde un PC:

1. En la tele: Configuración → Mi Fire TV → Opciones para desarrolladores → **Depuración ADB** activada.
2. `adb connect <ip-de-la-tele>:5555`, y en la tele acepta el aviso.
3. `adb install -r mis-canales.apk`

A partir de ahí, las versiones nuevas se instalan desde la propia app (ver [Actualizaciones](#actualizaciones)).
Para eso, la primera vez hay que dar permiso para instalar:

- En el móvil, la app abre ese ajuste ella sola.
- En el Fire: Configuración → Mi Fire TV → Opciones para desarrolladores → Instalar aplicaciones
  desconocidas → Mis Canales.

Cada versión se instala encima de la anterior sin perder nada: el APK se firma siempre con la misma clave.

## Primeros pasos

1. **Añadir canales**: en la pestaña **Añadir** (móvil) o **Añadir canal** (tele), escribe el `@nombre`
   del canal tal como sale en YouTube, o pega su enlace. En el móvil también se puede, desde la app de
   YouTube, en cualquier vídeo: **Compartir → Añadir a Mis Canales** (o «Añadir a Canales M»).
2. **Conectar con Home Assistant** (opcional, pero recomendable): así los canales, lo visto y las listas
   son los mismos en el móvil y en las teles. Se configura una vez en el móvil y se pasa a los demás
   aparatos con un código. Está explicado en [Home Assistant](#home-assistant).
3. **Elegir los iconos de cada aparato**: en el móvil de cada uno, solo el suyo. En las teles, todos.
   Ver [Perfiles](#perfiles-mis-canales-y-canales-m).

## En el móvil

Abajo hay cinco pestañas:

- **Inicio**: los últimos vídeos de tus canales, del más nuevo al más viejo. Arriba van los filtros
  (ver [Inicio](#inicio-filtros-aleatorio-siguiente-y-anterior)).
- **Buscar**: en tus canales o en todo YouTube.
- **Listas**: tus listas de reproducción.
- **Canales**: tus canales (con «Categoría», «Para niños» y la ✕ para quitarlo) y, debajo, todos los
  **Ajustes**.
- **Añadir**: añadir un canal.

**Con un vídeo**:

- Un toque lo pone.
- Mantenerlo pulsado abre sus opciones: **Ver**, **📺 Ver en la tele**, **Añadir a lista**,
  **Visto / No visto**, **Canal** (ir a él) y **YouTube** (abrirlo en la app de YouTube).

**En el reproductor**:

- Al tocar la pantalla salen la pausa, **anterior** y **siguiente** a los lados, y debajo
  **📺 Ver en la tele** y **⌄ Minimizar**.
- Gira con el móvil: en horizontal se ve a pantalla completa.
- **Ventana flotante**: Atrás o «⌄ Minimizar» dejan el vídeo en una ventana pequeña y se sigue usando la
  app. Tocándola vuelve a pantalla completa; arrastrándola fuera de la pantalla se cierra.
- **Con la pantalla apagada** el vídeo sigue sonando, con play, pausa, anterior y siguiente en el aviso,
  en la pantalla de bloqueo, en los auriculares y en el coche por Bluetooth. Se quita en Ajustes →
  «Seguir sonando con la pantalla apagada».

> Las normas de YouTube para desarrolladores no permiten la reproducción en segundo plano del reproductor
> insertado. Es una app para uso propio, y YouTube puede cambiar su reproductor y romperlo.

## En la tele

A la izquierda está el menú: **Inicio**, **Buscar**, **Listas**, **Mis canales** (los canales y todos los
ajustes de la tele) y **Añadir canal**.

**Inicio** va por filas. Arriba, el título y la fila de categorías; debajo, la de los filtros y los
canales. Con «Todas» y «Todos» elegidos, cada categoría tiene su fila de vídeos, con «Ver todos» al final.

**Sobre un vídeo**:

- **OK**: lo pone y lo marca como visto.
- **Mantener OK** o **play/pausa**: abre su ficha, con la descripción y los botones **Ver**,
  **Añadir a lista**, **Marcar como visto / no visto**, **Ir al canal** y **Abrir en YouTube** (en la
  app de YouTube de la tele).

**Sobre la pastilla de un canal** (en Inicio): OK lo elige solo; **mantener OK** o **play/pausa** lo
añade a los elegidos o lo quita.

**En el reproductor**:

| Tecla | Qué hace |
|---|---|
| OK, play/pausa | pausa / sigue |
| ◀ ▶ | 10 segundos atrás / adelante |
| ⏪ ⏩ (retroceso y avance rápido) | vídeo anterior / siguiente de la lista |
| ▼ (o el botón de subtítulos) | subtítulos sí / no |
| ▲ | enseña el panel con el título |
| Atrás | sale a la lista |

Al acabar un vídeo sale «Siguiente: …» unos segundos: OK lo pone ya y Atrás sale.

**Calidad**: hasta 1440p a 60 fps. Dentro de una WebView del Fire TV, el reproductor de YouTube se
quedaba en 720p. La app le pide ella misma la página del reproductor y le añade unos scripts para que se
porte como el Chrome de un PC. No pasa de 1440p porque a 2160p60 el Fire TV Stick 4K va a saltos.

### Los mandos

- **Mando del Fire TV**: todo funciona como en la tabla de arriba, incluido mantener OK.
- **Mando de la tele Samsung** (por HDMI-CEC): no sabe mantener OK, así que para elegir canales o abrir
  la ficha de un vídeo se usa **play/pausa**. A veces el primer play/pausa no llega: pulsa antes otro
  botón del mando y ya va.

### Los iconos en el Fire TV

Cada icono de la app (Mis Canales, Canales M, Kids) sale como una entrada aparte en el launcher.
Si en una tele sobra alguno, se quita en Mis canales → fila **Iconos**.

## Canales

- **Añadir**: el `@nombre`, el enlace del canal, el enlace de cualquier vídeo suyo o, en el móvil,
  Compartir desde la app de YouTube. También desde la propia lista de Home Assistant: escribe el
  `@nombre` como un elemento nuevo y la app lo completa.
- **Quitar**: en el móvil, la ✕ del canal en Canales. En la tele, dentro del canal, «Quitar canal». Con
  Home Assistant se quita en todos los aparatos.
- **Ver sus vídeos**: tocándolo en Canales. Al final de sus vídeos, **Ver vídeos anteriores** carga los
  más antiguos que los 15 últimos (de 30 en 30).
- **Categoría** (Música, Noticias…): en el móvil, el botón «Categoría» del canal (una de las que ya hay,
  «Sin categoría» o una nueva); en la tele, dentro del canal, «Poner categoría». Una por canal.
- **Para niños** (solo en Mis Canales): en el móvil, el botón del canal; en la tele, dentro del canal.
  Ver [Mis Canales Kids](#mis-canales-kids).

Las categorías y «Para niños» se guardan también en Home Assistant, así que se ponen en un aparato y
salen en todos.

## Inicio: filtros, aleatorio, siguiente y anterior

Los vídeos vienen del feed público de cada canal: sus 15 últimos, sin cuenta de Google ni clave. La app
vuelve a mirar los canales al abrirse si hace más de 15 minutos; **Actualizar** lo hace al momento.
**Shorts**: se ocultan o se ven según el ajuste.

**Filtros** (arriba de Inicio):

- Primera fila: **Todas**, una pastilla por categoría y **Otros** (los canales sin categoría).
- Segunda fila: **Todos**, **Sin ver** (con el número de vídeos nuevos), **Seguir viendo**,
  **Aleatorio**, **Actualizar** y una pastilla por canal.
- Al elegir una categoría, solo salen los vídeos de sus canales, y la segunda fila se queda con esos
  canales.

**Varios canales a la vez**: un toque en un canal lo elige solo. Mantenerlo pulsado (en la tele,
mantener OK o play/pausa) lo añade a los elegidos, o lo quita.

**Aleatorio**: mezcla lo que estás viendo (una categoría, «Sin ver», todo…) y lo pone seguido. Con un
canal elegido, mezcla unos cien vídeos suyos (o los que tenga). Con varios, unos cien entre todos, a
partes iguales. Va bien para música: elige la categoría o los canales y dale a Aleatorio.

**Reproducción continua**: al acabar un vídeo se pone el siguiente de la lista desde la que lo abriste
(un canal, una categoría, una búsqueda, una lista…), sin salir del reproductor. En el móvil salen
«Ver ya» y «Cancelar». Sigue también con la pantalla apagada y en la ventana flotante.

**Siguiente y anterior**: en la tele, los botones de avance y retroceso rápido del mando. En el móvil, al
tocar la pantalla, a los lados de la pausa, y también en el aviso de reproducción. «Anterior» vuelve al
principio del vídeo si ya lleva un rato, y si no, al que sonó antes.

## Vistos y «Seguir viendo»

- **NUEVO** sale en los vídeos que no has visto, publicados después de añadir el canal (o en la semana
  de antes).
- Al abrir un vídeo se marca como visto. Se puede cambiar a mano: en el móvil, manteniéndolo pulsado; en
  la tele, en su ficha.
- La app apunta **por dónde vas** en cada vídeo. La miniatura lleva una barra roja, y al volver a abrirlo
  sigue desde ahí. **Seguir viendo** reúne los empezados y sin acabar.
- **Entre aparatos**: con la lista de progreso en Home Assistant (ver [Home Assistant](#home-assistant)),
  lo que empiezas en el móvil sigue en la tele en el mismo punto, y al revés. Se guardan los 400 vídeos
  más recientes.
- **Saltar patrocinios**: con el ajuste encendido, la app se salta las partes de patrocinio,
  autopromoción y «suscríbete» que marca la comunidad de [SponsorBlock](https://sponsor.ajay.app).

## Listas de reproducción

Vídeos sueltos de cualquier canal (también de la búsqueda), guardados en listas con nombre. Están en la
pestaña **Listas** del móvil y en la entrada **Listas** del menú de la tele.

- **Añadir un vídeo**: en el móvil, mantenerlo pulsado → «Añadir a lista». En la tele, mantener OK (o
  play/pausa) → «Añadir a lista». Marca una lista de las que hay o escribe una nueva. Para quitarlo,
  igual, desmarcándola.
- Una lista existe mientras tiene vídeos.
- **Dentro de una lista**: los vídeos en el orden en que se añadieron, seguidos, con «Aleatorio».
  También se le cambia el nombre o se borra (si el nombre nuevo ya existe, se juntan).
- **En todos los aparatos**: con la lista «Mis Canales listas» en Home Assistant. Sin ella, cada aparato
  tiene las suyas. Si no hay conexión, lo añadido se queda en el aparato y se sube después.

## Buscar

En el móvil, la pestaña **Buscar**; en la tele, **Buscar** en el menú.

- **Mis canales**: busca entre los vídeos de tus canales, también los antiguos. Lo ya cargado sale al
  momento, y además se busca dentro de cada canal en YouTube.
- **Todo YouTube**: vídeos y canales de todo YouTube, sin recomendaciones ni vídeos relacionados. Un
  vídeo se puede ver sin añadir su canal. Al tocar un canal se ven sus vídeos y el botón para añadirlo.
  Al final, **Más resultados**.
- En la tele se escribe con el teclado de la tele y se busca con Intro.

## Perfiles: Mis Canales y Canales M

**Canales M** es la misma app con todo aparte, para el otro adulto de casa: canales, categorías,
vistos, por dónde va cada vídeo, listas, avisos y ajustes de reproducción. Cada uno añade lo suyo. Se
nota porque el título sale como «Canales **M**», con la M en morado.

**Qué es de cada perfil y qué es del aparato**:

| De cada perfil | Del aparato (igual para todos los iconos) |
|---|---|
| Canales, categorías, vistos, progreso, listas | La conexión con Home Assistant (dirección y token) |
| Shorts, reproductor, subtítulos, patrocinios | Qué iconos salen |
| Reproducción continua, segundo plano, avisos | El PIN y «Pedir PIN al abrir» |
| Sus listas de Home Assistant | Los ajustes de Kids |

**Iconos en este aparato**: en el móvil, Canales → Ajustes → **Iconos en este móvil**. En la tele,
Mis canales → fila **Iconos**. Quitar un icono no borra nada. El que estás usando no se puede quitar
desde sí mismo: se quita desde otro.

**Canales M en el otro móvil**:

1. Antes, en Home Assistant, crea sus tres listas (ver [Las listas](#las-listas)). Al recibir la
   conexión se prueba con su lista de canales, y si no existe no se guarda nada.
2. Instala el APK y abre **Canales M**.
3. En ese móvil: Canales → **Recibir de otro móvil**. Sale un código.
4. En el móvil que ya está conectado (el de Mis Canales): Canales → **Pasar a la tele o a otro móvil** → elige el otro móvil y escribe el
   código.
   Los dos móviles tienen que estar en la wifi de casa.
5. En el otro móvil, en **Iconos en este móvil**, quita «Mis Canales» y «Mis Canales Kids». En el otro,
   quita «Canales M».

Si sus listas no existen, al añadir un canal sale un error que dice qué lista falta.

## Mis Canales Kids

Es el icono de los niños: el mismo programa, pero siempre en modo niños.

- Solo salen los canales marcados **Para niños** en Mis Canales. Kids se pone al día al volver a
  ella: al momento en el mismo aparato, y con Home Assistant también desde otro.
- No hay búsqueda en YouTube, ni añadir canales, ni enlaces a YouTube desde el reproductor, ni
  sugerencias al acabar. Si un vídeo no se deja ver dentro de la app, no se abre en la de YouTube.
- Sus vistos y por dónde van, aparte de los de los mayores (y sin sincronizar).
- No mira si hay versiones nuevas: eso se hace desde Mis Canales.

**Ajustes de Kids** (el candado en el móvil, «Mayores» en la tele). Piden el PIN de los mayores, de 4
cifras, que se crea la primera vez. En la tele se escribe con el teclado de la pantalla.

- **Tiempo al día**: sin límite, 30 min, 1 h, 1 h 30 o 2 h. Al acabarse, el vídeo se para; con el PIN se
  da media hora más.
- **Modo peque**: miniaturas grandes y sin textos, para quien aún no lee.
- **Reproducción continua**: apagada de entrada; cuenta igual para el tiempo del día.
- **Cambiar el PIN**.

**En la app de los mayores** (solo Mis Canales):

- Los canales «Para niños» no salen en Inicio, en el buscador ni en los avisos (sí en Canales, para
  cambiarlos). Se pueden ver también con **Ver aquí también los canales de niños** (móvil: Ajustes; tele:
  Mis canales → «Sus canales también en Inicio»).
- **Pedir PIN al abrir**: tapa la app de los mayores con el PIN al abrirla y al volver a ella con Home.
  Es para los aparatos que usan también los niños.

## Home Assistant

Con Home Assistant, los canales, lo visto, por dónde vas y las listas viven en listas de tareas de tu
Home Assistant, y todos los aparatos ven lo mismo. Sin él, cada aparato guarda lo suyo.

### Las listas

Se crean en Home Assistant: Ajustes → Dispositivos y servicios → Añadir integración → **Local To-do**, una
vez por lista, con estos nombres exactos:

| Lista (nombre) | Entidad | Para qué | ¿Hace falta? |
|---|---|---|---|
| Mis Canales | `todo.mis_canales` | los canales de Mis Canales (y los de Kids) | sí, para sincronizar |
| Mis Canales progreso | `todo.mis_canales_progreso` | vistos y por dónde va cada vídeo | para compartirlos |
| Mis Canales listas | `todo.mis_canales_listas` | listas de reproducción | para compartirlas |
| Mis Canales Montse | `todo.mis_canales_montse` | los canales de Canales M | para Canales M |
| Mis Canales Montse progreso | `todo.mis_canales_montse_progreso` | sus vistos y progreso | para compartirlos |
| Mis Canales Montse listas | `todo.mis_canales_montse_listas` | sus listas | para compartirlas |

Si falta la de progreso o la de listas, eso se queda en cada aparato y lo demás sincroniza igual.

**Qué hay dentro** (no hace falta tocarlo, pero se puede):

- **Canales**: un elemento por canal, con su nombre. En la descripción, una línea por dato: el ID del
  canal (`UC…`), la dirección de su foto y, si los tiene, `niños` y `categoría: Música` (vale también
  sin tilde). Escribir un elemento nuevo con el `@nombre` de un canal lo añade: la app lo completa.
- **Progreso**: un elemento por vídeo, `ID · título`, y en la descripción el segundo por el que vas,
  lo que dura y cuándo se apuntó.
- **Listas**: un elemento por vídeo, `lista · título`, con los datos del vídeo en la descripción.

### Conectar el móvil

1. En Home Assistant: tu perfil → Seguridad → **Tokens de acceso de larga duración** → Crear token.
2. En el móvil: Canales → **Sincronizar con Home Assistant**. Rellena:
   - **Dirección de fuera**: la de DuckDNS, por ejemplo `https://micasa.duckdns.org:8123`.
   - **Dirección de casa** (opcional): la de dentro de casa, por ejemplo `http://192.168.1.10:8123`.
     Se prueba primero (es más rápida), y si no contesta, por ejemplo fuera de casa, la de fuera.
   - **Token**.
   - **Lista de tareas**: deja `todo.mis_canales`.
3. **Conectar**. Sube los canales que ya tenías en el móvil.

Debajo pone si está conectado y cuándo se sincronizó por última vez. **Desconectar** deja los canales en
el aparato.

La app trae la lista al abrirse y al volver a ella (si hace más de 20 segundos). Añadir, quitar, marcar
o cambiar algo se guarda en Home Assistant al momento.

### Pasar la conexión a la tele o a otro móvil

Va por la wifi de casa, con un código de 4 cifras:

- **A una tele**: en la tele, Mis canales → **Conectar con Home Assistant** (o, sin canales aún, en
  Inicio, **Traer mis canales de Home Assistant**). Sale un código. En el móvil: Canales → Sincronizar con
  Home Assistant → **Pasar a la tele o a otro móvil**, elige la tele y escribe el código.
- **A otro móvil**: en el otro, Canales → **Recibir de otro móvil**, y lo mismo.

El aparato que recibe se anuncia en la red solo mientras esa pantalla está abierta. Tras cinco códigos mal
deja de escuchar. Se pasa la conexión (direcciones y token), y cada icono usa sus listas: si se recibe
desde Canales M, vale también para Mis Canales, y al revés. Antes de guardarla, se prueba
leyendo la lista de canales del icono que tienes abierto: si esa lista no existe en Home Assistant, no
se guarda.

**A mano en la tele**: en la misma pantalla de la tele se pueden escribir los datos. Con el teclado de la
app Amazon Fire TV del móvil se puede pegar el token.

**Por ADB** (el `-S` cierra la app antes, para que reciba los datos aunque estuviera abierta):

```sh
adb shell am start -S -n es.miscanales/.MainActivity \
  --es ha_url "https://micasa.duckdns.org:8123" \
  --es ha_local_url "http://192.168.1.10:8123" \
  --es ha_token "EL_TOKEN"
```

## Ver en la tele desde el móvil

En el móvil, mantén pulsado un vídeo y toca **📺 Ver en la tele**, o tócalo debajo del vídeo mientras lo
ves. Home Assistant despierta el Fire TV y le abre la app con ese vídeo, desde donde ibas. El móvil se
pone en pausa. Funciona también fuera de casa, y abre el mismo icono (si estás en Canales M, se abre
Canales M en la tele).

Lo que hace falta:

1. En el Fire TV: Opciones para desarrolladores → **Depuración ADB** activada.
2. En Home Assistant: Añadir integración → **Android Debug Bridge**, con la IP del Fire TV y el puerto
   5555. La primera vez sale en la tele un aviso de depuración: marca «Permitir siempre desde este
   ordenador» y acepta.
3. En el móvil: Canales → Sincronizar con Home Assistant → **Ver en la tele**: **Buscar** y luego
   **Guardar**. Si Home Assistant solo tiene una tele, se encuentra sola la primera vez.

Por dentro, Home Assistant ejecuta en la tele (acción `androidtv.adb_command`):

```
input keyevent KEYCODE_WAKEUP; am start -n es.miscanales/.MainActivity --activity-clear-top --activity-single-top --es play ID_DEL_VIDEO --ei start SEGUNDO
```

## Avisos de vídeos nuevos

### En el móvil

El móvil mira tus canales cada media hora, más o menos (Android junta estas tareas para gastar menos
batería), y avisa de cada vídeo nuevo con su miniatura. Al tocar el aviso, el vídeo se abre en la app, no
en la de YouTube.

- Se enciende o se apaga en Ajustes → **Avisar de vídeos nuevos**. Ahí mismo, **Probar con el último
  vídeo** enseña cómo queda.
- Para que avise también con el móvil en reposo, la batería de la app tiene que estar «Sin
  restricciones». El botón **Quitar la restricción de batería** abre ese ajuste.
- La primera vez, y con cada canal recién añadido, solo apunta lo que ya hay: no avisa de lo anterior.
- Solo avisa de lo publicado en los dos últimos días y que no hayas visto.
- Si hay muchos a la vez, se juntan en un solo aviso.
- Cada perfil avisa de lo suyo, y solo si su icono sale en ese móvil. El aviso dice de qué perfil es.
- No avisa de los canales «Para niños» (salvo con «Ver aquí también los canales de niños»).

### Por Telegram (Home Assistant)

`homeassistant/mis_canales.yaml` es un package de Home Assistant. Cada 30 minutos lee `todo.mis_canales`,
mira el feed de cada canal (sin Shorts) y manda por Telegram los vídeos que aún no ha avisado. La primera
vez solo los apunta.

- Va en la carpeta de packages, por ejemplo `/config/packages/mis_canales.yaml`. Después: Herramientas
  para desarrolladores → YAML → Comprobar la configuración, y reiniciar.
- Cómo se envía el aviso está en un solo sitio, el script `mis_canales_enviar`. Antes de usarlo, cambia
  `notify.telegram_bot_XXXX` por la entidad `notify` de tu chat de Telegram.
- Para probarlo: Herramientas para desarrolladores → Eventos → `mis_canales_probar` → Lanzar evento.
- Si ya te llegan los avisos del móvil, puedes apagar la automatización «Mis Canales: aviso de vídeos
  nuevos».

## Todos los ajustes

En el móvil, en la pestaña **Canales**, debajo de los canales. En la tele, en **Mis canales**, las
pastillas de arriba.

| Ajuste | Móvil | Tele | Qué hace | Es de |
|---|---|---|---|---|
| Ocultar Shorts | sí | «Shorts» | quita los vídeos cortos | perfil |
| Ver en la app de YouTube | sí | «Reproductor» | abre los vídeos en la app de YouTube en vez de aquí | perfil |
| Subtítulos | sí | sí | empiezan con subtítulos (en la tele, ▼ los cambia) | perfil |
| Seguir sonando con la pantalla apagada | sí | — | segundo plano y ventana flotante al salir | perfil |
| Saltar patrocinios (SponsorBlock) | sí | «Patrocinios» | se salta patrocinios y autopromoción | perfil |
| Reproducción continua | sí | sí | al acabar, el siguiente | perfil |
| Avisar de vídeos nuevos | sí | — | avisos del móvil | perfil |
| Ver aquí también los canales de niños | sí | «Sus canales también en Inicio» | los de Kids también en Mis Canales | aparato |
| Pedir PIN al abrir Mis Canales | sí | sí | tapa la app de los mayores con el PIN | aparato |
| Cambiar el PIN | sí | sí | el PIN de los mayores | aparato |
| Iconos en este móvil | sí | «Iconos» | qué iconos salen | aparato |
| Versión / Actualizar | sí | sí | la versión que tienes y buscar otra | — |
| Sincronizar con Home Assistant | sí | «Home Assistant» | conexión y listas | aparato |
| Ver en la tele | sí | — | qué tele abre Home Assistant | aparato |

Los ajustes de Kids (tiempo al día, modo peque, reproducción continua de Kids) están en la propia Kids.

## Actualizaciones

Cada cambio en el código publica la versión nueva en
[Releases → latest](https://github.com/Enjamao/mis-canales/releases/tag/latest).

- La app (Mis Canales o Canales M; Kids no) mira al abrirse, como mucho cada 6 horas, si hay una
  más nueva. Si la hay, en Inicio sale un botón **Actualizar a la …**: la baja y abre el instalador de
  Android, donde hay que confirmar.
- También en Ajustes (móvil) o Mis canales (tele): la versión que tienes y **Buscar actualizaciones**.
- La primera vez hay que dar permiso para instalar (ver [Instalar](#instalar)).

## Problemas frecuentes

**«No se ha podido guardar en Home Assistant… ¿Está creada la lista…?»**
Falta esa lista en Home Assistant. Créala como Local To-do con el nombre que dice el mensaje (ver
[Las listas](#las-listas)).

**«No se ha podido guardar en Home Assistant: no acepta el token (401)»**
El token está mal o se ha borrado. Crea otro y vuelve a conectar.

**Un vídeo se abre en la app de YouTube**
Hay vídeos que su dueño no deja ver fuera de YouTube. En Mis Canales se abren en la app de YouTube; en
Kids no se abren.

**El botón de actualizar no instala**
Falta el permiso para instalar apps de Mis Canales (ver [Instalar](#instalar)).

**Los avisos del móvil no llegan con el móvil en reposo**
Pon la batería de la app «Sin restricciones» (Ajustes → Avisar de vídeos nuevos → Quitar la restricción
de batería).

**El mando de la Samsung no hace caso a play/pausa**
Pulsa antes otro botón del mando, y luego play/pausa.

**Ha desaparecido un icono**
Se ha quitado en «Iconos». Vuelve a ponerlo desde otro icono de la app: Ajustes → Iconos en este móvil
(en la tele, Mis canales → Iconos).

**Kids no enseña un canal nuevo**
Tiene que estar marcado «Para niños» en Mis Canales. Kids se pone al día al volver a abrirla.

**La búsqueda o «Ver vídeos anteriores» dejan de funcionar**
Salen de las páginas de YouTube, sin clave ni cuenta. Si YouTube cambia su web, hay que adaptar
`Search.kt`. El workflow «Explorar» enseña cómo vienen los datos ahora (ver
[docs/desarrollo.md](docs/desarrollo.md)).

**El sonido con la pantalla apagada o la calidad en la tele dejan de ir**
Dependen de cómo es el reproductor de YouTube por dentro. Si YouTube lo cambia, hay que adaptar
`PlayerActivity.kt` y `EmbedPatch.kt`.

## Créditos

La base de la interfaz para TV viene de [HackerTube for TV](https://github.com/ephraimduncan/hackertube.tv)
(MIT). Los iconos son [Material Symbols](https://github.com/google/material-design-icons) de Google
(Apache 2.0). Los segmentos de patrocinio, de [SponsorBlock](https://sponsor.ajay.app) (CC BY-NC-SA 4.0).
