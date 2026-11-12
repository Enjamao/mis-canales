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
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null; adb pull /sdcard/ui.xml ui.xml >/dev/null
  python3 - "$1" "${2:-0}" <<'PY'
import re, subprocess, sys
xml = open('ui.xml', encoding='utf-8').read()
for m in re.finditer(r'<node [^>]*>', xml):
    node = m.group(0)
    if f'text="{sys.argv[1]}"' in node:
        b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', node)
        x, y = (int(b.group(1)) + int(b.group(3))) // 2, (int(b.group(2)) + int(b.group(4))) // 2 + int(sys.argv[2])
        subprocess.run(['adb', 'shell', 'input', 'tap', str(x), str(y)])
        print('pulsado', sys.argv[1], x, y)
        break
else:
    print('no está en pantalla:', sys.argv[1])
PY
}

tecla() { adb shell input keyevent "$@"; sleep 1; }

adb install -r "$APK"
adb logcat -c; adb logcat -c -b crash
CHANNELS='[{&quot;id&quot;:&quot;UCBJycsmduvYEL83R_U4JriQ&quot;,&quot;name&quot;:&quot;Marques Brownlee&quot;},{&quot;id&quot;:&quot;UCupvZG-5ko_eiXAupbDfxWw&quot;,&quot;name&quot;:&quot;CNN&quot;}]'
printf '%s\n' "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>" "<map>" "    <string name=\"channels\">$CHANNELS</string>" "</map>" > store.xml
adb shell run-as $PKG mkdir -p shared_prefs
adb shell "run-as $PKG sh -c 'cat > shared_prefs/store.xml'" < store.xml

adb shell am start -W -n $PKG/.MainActivity
sleep 25
textos "inicio en la tele"
fallos "abrir en la tele"

# al menú (izquierda) y a "Buscar", la segunda entrada
tecla KEYCODE_DPAD_LEFT
tecla KEYCODE_DPAD_DOWN
tecla KEYCODE_DPAD_CENTER
sleep 3
textos "buscador de la tele"
fallos "abrir el buscador"

# escribir en la caja (tiene el foco) y pulsar Buscar
tecla KEYCODE_DPAD_CENTER
adb shell input text "iphone"; sleep 1
tecla KEYCODE_BACK
pulsar "Buscar"
sleep 25
textos "«iphone» en mis canales (tele)"
fallos "buscar en mis canales (tele)"

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
exit 0
