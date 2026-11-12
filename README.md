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

## Sincronizar con Home Assistant

Los canales pueden vivir en una lista de tareas de Home Assistant, compartida entre la tele y el
móvil. Añadir o quitar un canal en uno lo hace en el otro, y también se pueden añadir desde la
propia lista de HA escribiendo el `@nombre` del canal (la app lo completa).

1. En HA: Ajustes → Dispositivos y servicios → Añadir integración → **Local To-do**, con el nombre
   **Mis Canales** (queda como `todo.mis_canales`).
2. En HA: tu perfil → Seguridad → **Tokens de acceso de larga duración** → Crear token.
3. En el móvil: Canales → Sincronizar con Home Assistant: la dirección de fuera (DuckDNS), la de
   casa si quieres (se prueba primero) y el token. Al conectar, sube los canales que ya tenías.
4. En la tele, por ADB (escribir el token con el mando no es práctico). El `-S` cierra la app antes,
   para que reciba los datos aunque estuviera abierta:

   ```sh
   adb shell am start -S -n es.miscanales/.MainActivity \
     --es ha_url "https://micasa.duckdns.org:8123" \
     --es ha_local_url "http://192.168.1.10:8123" \
     --es ha_token "EL_TOKEN"
   ```

En la lista, cada canal es un elemento con su nombre; en la descripción van su ID y su foto.

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
