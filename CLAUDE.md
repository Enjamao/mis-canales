# Mis Canales

App Android (tele y móvil) que solo enseña los canales de YouTube que eliges. Enjamao la usa en un
móvil Android y en dos Fire TV Stick: uno 4K con el mando de la tele Samsung (por HDMI-CEC) y otro
con el mando del Fire.

Antes de cambiar nada, lee:

- [README.md](README.md): cómo se usa y se configura.
- [docs/desarrollo.md](docs/desarrollo.md): dónde está cada cosa, cómo se prueba y cómo se publica una versión.
- [docs/cambios.md](docs/cambios.md): qué trae cada versión.

Lo esencial:

- Todo en español: textos, comentarios, commits y documentación. Enjamao no es programador: explica los
  cambios con palabras sencillas.
- Cada versión: sube `versionCode` y `versionName` en `app/build.gradle.kts`, cambia la línea
  `pulsar "Actualizar a la …"` de `.github/prueba/prueba.sh`, y pon al día el README y `docs/cambios.md`.
- Push a `main` = versión publicada (`apk.yml`). Luego lanza `prueba.yml` y mira los registros y las
  capturas.
- Nunca cambies la clave de firma (`keystore/mis-canales.p12`, huella `d434e95e…`).
- Nunca subas tokens ni direcciones de Home Assistant: el repositorio es público.
- En la tele, todo tiene que ir con el mando, también con el de la Samsung (no sabe mantener OK:
  play/pausa hace lo mismo).
