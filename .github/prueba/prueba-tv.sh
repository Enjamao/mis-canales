#!/bin/bash
# Lo lanza prueba.yml dentro del emulador de Android TV: el buscador y los vídeos anteriores en la tele.
set -x
APK=app/build/outputs/apk/prueba/app-prueba.apk
PKG=es.miscanales

fallos() {
  echo "===== $1 ====="
  adb logcat -d -b crash | tee -a fallos.txt
  adb logcat -d | grep -E "AndroidRuntime|FATAL" | tail -30
  adb logcat -c
  adb logcat -c -b crash
}

textos() {
  echo "--- pantalla: $1"
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null; adb pull /sdcard/ui.xml ui.xml >/dev/null
  python3 -c "import re;print(re.findall(r' text=\"([^\"]+)\"', open('ui.xml',encoding='utf-8').read())[:45])"
}

pulsar() {
  # pulsar TEXTO [píxeles más abajo] [ultimo]: el primero con ese texto, o el último (el más bajo)
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null; adb pull /sdcard/ui.xml ui.xml >/dev/null
  python3 - "$1" "${2:-0}" "${3:-primero}" <<'PY'
import re, subprocess, sys
xml = open('ui.xml', encoding='utf-8').read()
found = []
for m in re.finditer(r'<node [^>]*>', xml):
    if f'text="{sys.argv[1]}"' in m.group(0):
        b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', m.group(0))
        found.append(((int(b.group(2)) + int(b.group(4))) // 2, (int(b.group(1)) + int(b.group(3))) // 2))
if found:
    y, x = sorted(found)[-1] if sys.argv[3] == 'ultimo' else found[0]
    y += int(sys.argv[2])
    subprocess.run(['adb', 'shell', 'input', 'tap', str(x), str(y)])
    print('pulsado', sys.argv[1], x, y)
else:
    print('no está en pantalla:', sys.argv[1])
PY
}

tecla() { adb shell input keyevent "$@"; sleep 1; }

adb install -r "$APK"
adb logcat -c; adb logcat -c -b crash
CHANNELS='[{&quot;id&quot;:&quot;UCBJycsmduvYEL83R_U4JriQ&quot;,&quot;name&quot;:&quot;Marques Brownlee&quot;,&quot;kids&quot;:true},{&quot;id&quot;:&quot;UCupvZG-5ko_eiXAupbDfxWw&quot;,&quot;name&quot;:&quot;CNN&quot;,&quot;category&quot;:&quot;Noticias&quot;}]'
printf '%s\n' "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>" "<map>" "    <string name=\"channels\">$CHANNELS</string>" \
  "    <boolean name=\"test_ignore_errors\" value=\"true\" />" "    <int name=\"test_end_after\" value=\"15\" />" "</map>" > store.xml
adb shell run-as $PKG mkdir -p shared_prefs
adb shell "run-as $PKG sh -c 'cat > shared_prefs/store.xml'" < store.xml

adb shell am start -W -n $PKG/.MainActivity
sleep 25
textos "inicio en la tele"
fallos "abrir en la tele"

# reproducción continua en la tele: el primer vídeo (tiene el foco), a los 15 s "acaba" y pasa al siguiente
adb logcat -c
tecla KEYCODE_DPAD_CENTER
sleep 32
echo "--- reproducción continua en la tele:"
adb logcat -d | grep "MisCanales" | tail -5
adb shell dumpsys activity activities | grep -E "topResumedActivity|mResumedActivity" | head -1
# Atrás en la tele: sale del vídeo (sin ventana flotante)
tecla KEYCODE_BACK
sleep 3
adb shell dumpsys activity activities | grep -E "topResumedActivity|mResumedActivity" | head -1
fallos "reproducción continua (tele)"

# al menú (izquierda) y a "Buscar", la segunda entrada
tecla KEYCODE_DPAD_LEFT
tecla KEYCODE_DPAD_DOWN
tecla KEYCODE_DPAD_CENTER
sleep 3
textos "buscador de la tele"
fallos "abrir el buscador"

# escribir en la caja (ya tiene el foco) y buscar con Intro, como con el teclado de la tele
adb shell input text "iphone"; sleep 1
tecla KEYCODE_ENTER
sleep 25
textos "«iphone» en mis canales (tele)"
fallos "buscar en mis canales (tele)"
# fuera de la caja (abajo, a los resultados) para que se vean los botones
tecla KEYCODE_DPAD_DOWN
textos "resultados con el foco abajo (tele)"

pulsar "Todo YouTube"
sleep 15
textos "«iphone» en todo YouTube (tele)"
fallos "buscar en todo YouTube (tele)"
pulsar "Canales" 60
sleep 15
textos "canal encontrado (tele)"
fallos "canal encontrado (tele)"
tecla KEYCODE_BACK
sleep 2

# Mis canales → Marques Brownlee → vídeos anteriores
tecla KEYCODE_DPAD_LEFT
tecla KEYCODE_DPAD_DOWN
tecla KEYCODE_DPAD_CENTER
sleep 3
pulsar "Marques Brownlee"
sleep 5
for i in $(seq 1 12); do tecla KEYCODE_DPAD_DOWN; done
textos "canal, abajo (tele)"
pulsar "Ver vídeos anteriores"
sleep 15
for i in $(seq 1 12); do tecla KEYCODE_DPAD_DOWN; done
textos "vídeos anteriores (tele)"
fallos "vídeos anteriores (tele)"

# Mis Canales Kids en la tele (Marques Brownlee va marcado «para niños»)
adb shell am start -W -n $PKG/.KidsActivity; sleep 15
textos "Kids en la tele"
fallos "abrir Kids (tele)"
# a "Mayores" (arriba a la derecha) y crear el PIN con el teclado de la pantalla (y con números)
tecla KEYCODE_DPAD_UP
for i in 1 2 3 4; do tecla KEYCODE_DPAD_RIGHT; done
tecla KEYCODE_DPAD_CENTER
sleep 2
textos "teclado del PIN (tele)"
adb shell input keyevent KEYCODE_1 KEYCODE_2 KEYCODE_3 KEYCODE_4; sleep 2
adb shell input keyevent KEYCODE_1 KEYCODE_2 KEYCODE_3 KEYCODE_4; sleep 3
textos "ajustes de Kids (tele)"
fallos "PIN y ajustes de Kids (tele)"

# Categorías en la tele. CNN va en «Noticias» desde el principio: Inicio va por filas («Noticias» y «Otros»).
foco() {
  echo "--- foco: $1"
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null; adb pull /sdcard/ui.xml ui.xml >/dev/null
  python3 /tmp/foco.py
}
cat > /tmp/foco.py <<'PY'
import re
xml = open('ui.xml', encoding='utf-8').read()
nodes = re.findall(r'<node [^>]*>', xml)
for i, n in enumerate(nodes):
    if 'focused="true"' in n:
        texts = [re.search(r' text="([^"]*)"', m).group(1) for m in nodes[i:i + 8] if re.search(r' text="([^"]+)"', m)]
        print(re.search(r'bounds="[^"]*"', n).group(0), texts[:3])
        open('foco.txt', 'w', encoding='utf-8').write(texts[0] if texts else '')
PY
# mover el foco con la tecla $2 hasta que el elemento enfocado empiece por $1
enfocar() {
  for i in $(seq 1 ${3:-10}); do
    rm -f foco.txt; foco "buscando $1"
    grep -q "^$1" foco.txt 2>/dev/null && return 0
    tecla $2
  done
  echo "no se ha llegado a: $1"
}
adb shell am force-stop $PKG; sleep 1
adb shell am start -W -n $PKG/.MainActivity; sleep 15
textos "Inicio sin los canales de niños (tele): sin Marques Brownlee ni «Otros»"
# con «Sus canales también en Inicio: sí», Marques Brownlee (de niños, sin categoría) sale en «Otros»
adb shell am force-stop $PKG; sleep 1
adb shell "run-as $PKG sed -i 's|</map>|<boolean name=\"kidsInAdult\" value=\"true\" /></map>|' shared_prefs/store.xml"
adb shell run-as $PKG grep -c kidsInAdult shared_prefs/store.xml
adb shell am start -W -n $PKG/.MainActivity; sleep 15
textos "Inicio por filas (tele)"
foco "Inicio por filas: el primer vídeo de la primera fila"
tecla KEYCODE_DPAD_DOWN
foco "una fila más abajo (Otros)"
for i in 1 2 3; do tecla KEYCODE_DPAD_RIGHT; done
foco "tres a la derecha"
tecla KEYCODE_DPAD_UP
foco "una fila más arriba (Noticias)"
fallos "Inicio por filas (tele)"

# a la fila de arriba y hasta la píldora de CNN; mantener OK abre su página
# arriba: la fila de los filtros y canales, y encima la de las categorías
tecla KEYCODE_DPAD_UP
foco "fila de filtros y canales"
tecla KEYCODE_DPAD_UP
foco "fila de categorías"
enfocar "Todas" KEYCODE_DPAD_LEFT 6
tecla KEYCODE_DPAD_DOWN
enfocar "Todos" KEYCODE_DPAD_LEFT 6
enfocar "CNN" KEYCODE_DPAD_RIGHT 12
adb shell input keyevent --longpress KEYCODE_DPAD_CENTER; sleep 4
textos "página de CNN (tele)"
foco "página de CNN, al abrirla"
# (si el foco se ha quedado en el menú, a la derecha sale de él)
grep -q "^Inicio" foco.txt 2>/dev/null && tecla KEYCODE_DPAD_RIGHT
enfocar "Categoría" KEYCODE_DPAD_UP 4
enfocar "Categoría" KEYCODE_DPAD_LEFT 3
tecla KEYCODE_DPAD_CENTER; sleep 3
textos "elegir categoría (tele)"
foco "elegir categoría: en la actual"
# abajo, a la caja de la categoría nueva
tecla KEYCODE_DPAD_DOWN
foco "en la caja"
adb shell input text "Musica"; sleep 1
tecla KEYCODE_ENTER
sleep 4
textos "CNN con su categoría nueva (tele)"
adb shell run-as $PKG cat shared_prefs/store.xml | grep -oE "category&quot;:&quot;[^&]*"
fallos "poner categoría (tele)"

# Inicio arranca arriba: el foco en «Todos», con el título a la vista
adb shell am force-stop $PKG; sleep 1
adb logcat -c
adb shell am start -W -n $PKG/.MainActivity; sleep 12
textos "Inicio al abrir (tele): con el título"
foco "Inicio al abrir: en Todos"

# Home Assistant en la tele: por el menú a Mis canales, y su pastilla
tecla KEYCODE_DPAD_LEFT
tecla KEYCODE_DPAD_DOWN
tecla KEYCODE_DPAD_DOWN
tecla KEYCODE_DPAD_CENTER
sleep 3
textos "Mis canales (tele), con Home Assistant"
# con el mando: arriba hasta la primera fila de pastillas, a «Añadir canal» y la de al lado
for i in 1 2 3 4 5 6; do tecla KEYCODE_DPAD_UP; done
enfocar "Añadir canal" KEYCODE_DPAD_LEFT 6
enfocar "Conectar con Home Assistant" KEYCODE_DPAD_RIGHT 3
tecla KEYCODE_DPAD_CENTER; sleep 4
textos "Home Assistant (tele): el código"
foco "Home Assistant: en Conectar"
CODE=$(adb logcat -d | grep -oE "Emparejar: código [0-9]{4}" | tail -1 | grep -oE "[0-9]{4}$")
PORT=$(adb logcat -d | grep -oE "anunciada como .*, puerto [0-9]+" | tail -1 | grep -oE "[0-9]+$")
adb logcat -d | grep "Emparejar" | tail -3
echo "código $CODE, puerto $PORT"
if [ -n "$PORT" ]; then
  adb forward tcp:9876 tcp:$PORT
  # como el móvil: una línea con el código y la conexión; contesta otra línea
  cat > /tmp/mandar.py <<'PY'
import json, socket, sys
msg = {"code": sys.argv[1], "config": {"url": "http://127.0.0.1:1", "token": "falso"}}
s = socket.create_connection(("127.0.0.1", 9876), timeout=60)
s.sendall((json.dumps(msg) + "\n").encode())
print("la tele contesta:", s.makefile(encoding="utf-8").readline().strip())
PY
  python3 /tmp/mandar.py 0000
  python3 /tmp/mandar.py "$CODE"
  sleep 2
  textos "Home Assistant (tele): tras recibir la conexión (falsa, no responde)"
fi
fallos "Home Assistant en la tele"

# Actualizaciones en la tele: como si esta fuera una versión antigua. En Inicio, arriba, el botón;
# baja la de GitHub y abre el instalador
adb shell am force-stop $PKG; sleep 1
adb shell "run-as $PKG sed -i 's|</map>|<int name=\"test_version_code\" value=\"1\" /></map>|' shared_prefs/store.xml"
adb shell appops set $PKG REQUEST_INSTALL_PACKAGES allow
adb logcat -c
adb shell am start -W -n $PKG/.MainActivity; sleep 14
textos "Inicio con el botón de actualizar (tele)"
adb logcat -d | grep "Actualizar:" | tail -3
# desde «Todos», arriba (a las categorías) y arriba otra vez
tecla KEYCODE_DPAD_UP
tecla KEYCODE_DPAD_UP
foco "en el botón de actualizar"
enfocar "Actualizar a la" KEYCODE_DPAD_RIGHT 4
tecla KEYCODE_DPAD_CENTER; sleep 25
adb logcat -d | grep "Actualizar:" | tail -3
adb shell dumpsys activity activities | grep -E "topResumedActivity" | head -1
textos "el instalador (tele)"
fallos "actualizaciones (tele)"
exit 0
