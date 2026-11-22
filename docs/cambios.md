# Cambios

Lo que trae cada versión, de la más nueva a la más vieja. Cada una se publica en
[Releases → latest](https://github.com/Enjamao/mis-canales/releases/tag/latest), y la app avisa sola.

## 0.26 — 22 nov 2026

- **0.26.0**: **El historial, en todos tus aparatos**. Con la lista nueva «Mis Canales historial» en Home
  Assistant (y «Mis Canales Montse historial» para Canales M), lo que abres en el móvil sale en el
  historial de la tele y al revés; borrarlo lo borra en todos. Sin la lista, sigue como antes.

## 0.25 — 21 nov 2026

- **0.25.3**: Ahora sí, las listas normales largas se leen hasta 200 vídeos: YouTube ha cambiado sus
  páginas de listas y se quedaban en 100.
- **0.25.2**: Registros para ver por qué las listas largas se quedaban en 100.
- **0.25.1**: Al compartir una lista, su ficha sale arriba del todo (en móviles pequeños el botón «Guardar
  en Listas» quedaba por debajo). Si una página de la lista falla, se guarda lo leído.
- **0.25.0**: **Guardar listas de YouTube**. Al compartir una lista desde YouTube (Compartir → Mis
  Canales) o pegar su enlace en «Añadir», se guarda una copia en Listas, con su nombre o el que le
  pongas, y sale también en la tele. Vale para las listas que hace alguien (hasta 200 vídeos) y para
  los mixes de YouTube (los vídeos que trae en ese momento).

## 0.24 — 21 nov 2026

- **0.24.1**: En el móvil, las **opciones de un vídeo** salen en un panel desde abajo, con una línea
  grande y un icono por opción (antes, una ventana con botones pequeños). Se abren manteniendo pulsado el
  vídeo o con el nuevo botón **⋮** que lleva al lado del título.
- **0.24.0**: **Idioma del audio**. Los vídeos con doblaje (MrBeast, por ejemplo) suenan en español si lo
  traen, y se puede cambiar al original: en el móvil, con el botón 🔊 de debajo del vídeo; en la tele, con
  ▲ dos veces. Se apaga en los ajustes («Audio en español si lo hay»; en la tele, la pastilla «Audio»). En
  Mis canales de la tele, los ajustes del reproductor van en su propia fila: antes «Subtítulos» y
  «Patrocinios» no cabían y no se veían.

## 0.23 — 20 nov 2026

- **0.23.2**: Al silenciar un canal desde su aviso con la app abierta, la campana de Canales sale tachada
  al momento.
- **0.23.1**: En Canales, la campana y la papelera, una encima de otra, para que «Para niños» quepa bien en
  móviles estrechos.
- **0.23.0**: **Silenciar un canal**: sus vídeos siguen en Inicio, pero no avisa de ellos. Se hace con el
  botón «Silenciar este canal» del propio aviso o con la campana de cada canal en la pestaña Canales. Se
  guarda en Home Assistant («sin avisos»), así que vale para todos los móviles y para Telegram.

## 0.22 — 18 nov 2026

- **0.22.1**: En la tele, la calidad llega hasta 1080p (antes, 1440p), para que no se corten los vídeos
  cuando el Fire TV Stick o la wifi no dan abasto.
- **0.22.0**: **Ver más tarde** e **Historial**, siempre arriba en Listas.
  - «Ver más tarde» se añade con un toque desde las opciones del vídeo. Dentro, «Quitar los vistos» y
    «Vaciar».
  - El Historial guarda lo que se abre en cada aparato, el último primero, con «Borrar el historial».

## 0.21 — 18 nov 2026

- **0.21.1**: En las tarjetas de la tele, «hace 2 horas» va antes que las visualizaciones, para que no se
  corte con la letra más grande.
- **0.21.0**:
  - **Lo que dura cada vídeo**, también en los recientes. Se saca de la página de vídeos de cada canal y
    se apunta para no volver a buscarlo.
  - **Fila «Seguir viendo»** arriba del todo en Inicio, con los empezados (en el móvil, de lado). Sustituye
    a la pastilla «Seguir viendo».
  - **Letra más grande en la tele**, también en el reproductor.
  - **Ajustes aparte en el móvil**, con la rueda ⚙ de Inicio o de Canales. La pestaña Canales queda solo
    con los canales.

## 0.20 — 17 nov 2026

- **0.20.0**: **Panel de canales**. En Inicio, en vez de una pastilla por canal, una sola, **Canales ▾**,
  que abre todos los canales en cuadrícula con su foto. Van por categorías, con los que tienen vídeos sin
  ver primero. Un toque elige uno y mantener pulsado elige varios. En el móvil y en la tele.

## 0.19 — 16 y 17 nov 2026

- **0.19.1**:
  - **Canales M**: el perfil morado se llama así (antes «Canales de Montse»). Sus listas de Home
    Assistant siguen siendo «Mis Canales Montse…».
  - La **dirección de casa** de Home Assistant (`http://192.168…`) ya funciona. Android no dejaba usar
    direcciones sin https, y la app siempre se iba por la de fuera.
- **0.19.0**: **Canales M** (entonces «Canales de Montse»), un segundo perfil de adulto con su icono
  morado.
  - Sus canales, vistos, progreso, listas y avisos van aparte, con sus listas en Home Assistant.
  - **Iconos en este móvil** (en la tele, fila «Iconos»): cada aparato elige qué iconos enseña.
  - **Recibir de otro móvil**: la conexión con Home Assistant pasa también de un móvil a otro.

## 0.18 — 15 nov 2026

- **0.18.0**: **Listas de reproducción**. Vídeos sueltos en listas con nombre, en el móvil y en la tele,
  sincronizadas por Home Assistant («Mis Canales listas»).
- **0.18.1**: Añadir a una lista funciona sin conexión con Home Assistant (se sube luego). En la tele, los
  botones de la ficha en varias filas.

## 0.17 — 14 y 15 nov 2026

- **0.17.0**: **Varios canales a la vez**, manteniendo pulsado, y Aleatorio entre ellos a partes iguales.
- **0.17.1**: Tras mantener pulsado un canal, el toque siguiente ya no se pierde.
- **0.17.2**: En la tele, play/pausa sobre un canal lo añade a los elegidos, para el mando de la Samsung.
- **0.17.3**: Buscar: el canal y las visualizaciones de los vídeos que YouTube manda en su formato nuevo
  (salían con «?»).

## 0.16 — 14 nov 2026

- **0.16.0**: **Siguiente y anterior**: botones de avance y retroceso del mando en la tele; en el móvil,
  al tocar la pantalla y en el aviso.

## 0.15 — 14 nov 2026

- **0.15.0**: **Aleatorio**. Mezcla lo que se ve y lo pone seguido; con un canal, entre unos cien
  vídeos suyos.

## 0.14 — 14 nov 2026

- **0.14.0**: **Actualizaciones desde GitHub**: la app avisa de la versión nueva, la baja y abre el
  instalador.
- **0.14.1**: En la tele, los vídeos anteriores de un canal también en Inicio (y en Kids).
- **0.14.2**: En la tele, al cargar vídeos anteriores el foco se queda en el botón.

## 0.13 — 14 nov 2026

- **0.13.0**: Home Assistant en la tele desde el móvil con **«Pasar a la tele»** y un código. En la tele,
  Inicio empieza arriba del todo.

## 0.12 — 13 y 14 nov 2026

- **0.12.0**: **Categorías de canales**: una fila de categorías en el móvil y una fila por categoría en
  la tele.
- **0.12.1**: Los canales de niños salen solo en Kids, con un ajuste para verlos también en la de los
  mayores. En la tele, las categorías arriba y los canales debajo.

## 0.11 — 13 nov 2026

- **0.11.0**: **Reproducción continua**, y ventana flotante al pulsar Atrás en el móvil.
- **0.11.1**: Kids se pone al día al volver a ella con los canales marcados «Para niños».

## 0.10 — 13 nov 2026

- **0.10.0**: **Mis Canales Kids**: icono propio, PIN, tiempo al día y modo peque.
- **0.10.1**: (deshecha) abrir Kids en el último modo usado.

## 0.9 — 12 y 13 nov 2026

- **0.9.0**: **Buscar** en tus canales y en todo YouTube, y **Ver vídeos anteriores** de un canal.
- **0.9.1**: En la tele, la caja de búsqueda más estrecha, para que quepan los botones.
- **0.9.2**: Los avisos llegan también con el móvil en reposo.

## 0.8 — 12 nov 2026

- **0.8.0**: **Avisos de vídeos nuevos en el móvil**, que abren el vídeo en Mis Canales. Prueba
  automática en un emulador.
- **0.8.1**: La app se cerraba al abrirla.

## 0.7 — 12 nov 2026

- **0.7.0**: **Ver en la tele** desde el móvil, por Home Assistant.

## 0.6 — 12 nov 2026

- **0.6.0**: **Seguir donde lo dejaste**, también entre aparatos, y **saltar patrocinios**
  (SponsorBlock).
- **0.6.1 a 0.6.8**: Calidad en la tele. El reproductor se porta como el Chrome de un PC, con el tamaño
  real de la pantalla y sin perder los formatos de 60 fps, con tope en 1440p.

## 0.5 — 12 nov 2026

- **0.5.0**: En el móvil, seguir sonando con la pantalla apagada y ventana flotante.
- **0.5.1**: El sonido sigue con la pantalla apagada, y el reproductor gira con el móvil.
- **0.5.2**: Arreglo del sonido al bloquear el móvil.

## 0.4 — 12 nov 2026

- **0.4.0**: **Versión para móvil**, y canales sincronizados con **Home Assistant**.

## 0.3 — 12 nov 2026

- **0.3.0**: Firma fija (cada versión se instala encima), subtítulos a elegir y más calidad de imagen.

## 0.2 — 11 y 12 nov 2026

- **0.2.0**: Reproductor propio: Atrás y el final del vídeo vuelven a Mis Canales.
- **0.2.1**: El vídeo se veía negro en el Fire TV.
- **0.2.2**: Añadir canales aunque el feed de YouTube no responda.

## 0.1 — 11 nov 2026

- **0.1.0**: Primera versión: app de tele solo con tus canales de YouTube.
