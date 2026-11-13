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
CHANNELS='[{&quot;id&quot;:&quot;UCBJycsmduvYEL83R_U4JriQ&quot;,&quot;name&quot;:&quot;Marques Brownlee&quot;,&quot;kids&quot;:true},{&quot;id&quot;:&quot;UCupvZG-5ko_eiXAupbDfxWw&quot;,&quot;name&quot;:&quot;CNN&quot;}]'
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
exit 0
