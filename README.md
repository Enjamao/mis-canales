# Mis Canales

App para Android TV y Fire TV que solo enseña los vídeos de los canales de YouTube que tú eliges.
No hay recomendaciones, ni portada, ni "siguiente vídeo". Al pulsar un vídeo se abre en la app de
YouTube de la tele, y al acabar vuelves aquí.

## Cómo se usa

- **Inicio**: los últimos vídeos de todos tus canales, del más nuevo al más viejo. Arriba hay una píldora
  por canal para filtrar, **Sin ver** (con el número de vídeos nuevos) y **Actualizar**.
- **OK** sobre un vídeo: lo pone en YouTube y lo marca como visto.
- **Mantener OK** sobre un vídeo: abre su ficha, con la descripción, "Marcar como visto/no visto" e "Ir al canal".
- **Mantener OK** sobre la píldora de un canal: abre la página del canal.
- **Mis canales**: la lista de canales, el botón para añadir y si se ven o no los Shorts.
  Al pulsar un canal se ven sus vídeos y el botón **Quitar canal**.
- **Añadir canal**: escribe el `@nombre` del canal tal como sale en YouTube, o pega su enlace.

"NUEVO" sale en los vídeos que no has visto y que se publicaron después de añadir el canal
(o en la semana de antes). La app vuelve a mirar los canales al abrirse si hace más de 15 minutos.

## De dónde saca los vídeos

Del feed RSS público de cada canal (`youtube.com/feeds/videos.xml`), que da sus últimos 15 vídeos.
Sin cuenta de Google y sin clave de la API. Para quitar los Shorts usa la lista "UULF" del canal.
Los canales, los vídeos vistos y los ajustes se guardan en la tele.

## Compilar

Al subir el código a GitHub, el workflow `.github/workflows/apk.yml` compila el APK y lo deja en
**Releases → latest** como `mis-canales.apk`.

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
