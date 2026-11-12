#!/bin/bash
# Lo lanza prueba.yml dentro del emulador ya arrancado.
set -x
APK=app/build/outputs/apk/prueba/app-prueba.apk
PKG=es.miscanales

fallos() {
  echo "===== $1 ====="
  adb logcat -d -b crash | tee -a fallos.txt
  adb logcat -d | grep -E "AndroidRuntime|$PKG|NewVideos|JobScheduler.*miscanales" | tail -60
  adb logcat -c
  adb logcat -c -b crash
}

pulsar() {
  # toca el elemento de la pantalla que tiene ese texto
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null
  adb pull /sdcard/ui.xml ui.xml >/dev/null
  python3 - "$1" <<'PY'
import re, subprocess, sys
xml = open('ui.xml', encoding='utf-8').read()
for m in re.finditer(r'<node [^>]*>', xml):
    node = m.group(0)
    if f'text="{sys.argv[1]}"' in node or f'content-desc="{sys.argv[1]}"' in node:
        b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', node)
        x, y = (int(b.group(1)) + int(b.group(3))) // 2, (int(b.group(2)) + int(b.group(4))) // 2
        subprocess.run(['adb', 'shell', 'input', 'tap', str(x), str(y)])
        print('pulsado', sys.argv[1], x, y)
        break
else:
    print('no está en pantalla:', sys.argv[1])
PY
}

adb install -r "$APK"
adb logcat -c; adb logcat -c -b crash

# Como el móvil de Eloy: con canales (y una versión anterior ya usada: sin avisos apuntados)
CHANNELS='[{&quot;id&quot;:&quot;UCBJycsmduvYEL83R_U4JriQ&quot;,&quot;name&quot;:&quot;Marques Brownlee&quot;},{&quot;id&quot;:&quot;UC_x5XG1OV2P6uZZ5FSM9Ttw&quot;,&quot;name&quot;:&quot;Google for Developers&quot;}]'
cat > store.xml <<XML
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
    <string name="channels">$CHANNELS</string>
</map>
XML
adb shell run-as $PKG mkdir -p shared_prefs
adb shell "run-as $PKG sh -c 'cat > shared_prefs/store.xml'" < store.xml
adb shell run-as $PKG cat shared_prefs/store.xml

# 1. Abrir la app (sale el permiso de avisos)
adb shell am start -W -n $PKG/.MainActivity
sleep 25
adb exec-out screencap -p > 1-abrir.png
fallos "abrir"
pulsar "Permitir"
pulsar "Allow"
sleep 5
fallos "permiso"

# 2. La revisión periódica, dos veces (la primera apunta, la segunda compara)
adb shell cmd jobscheduler run -f $PKG 4201
sleep 30
fallos "revisión 1"
adb shell run-as $PKG cat shared_prefs/nuevos.xml | head -c 600; echo
adb shell cmd jobscheduler run -f $PKG 4201
sleep 30
fallos "revisión 2"

# 3. El botón de probar el aviso: Canales → bajar a Ajustes → Probar con el último vídeo
adb shell am start -W -n $PKG/.MainActivity
sleep 5
pulsar "Canales"
sleep 3
for i in 1 2 3 4 5 6; do adb shell input swipe 500 1800 500 500 300; sleep 1; done
adb exec-out screencap -p > 3-ajustes.png
pulsar "Probar con el último vídeo"
sleep 15
fallos "probar aviso"
adb shell dumpsys notification --noredact | grep -E "pkg=$PKG|android.title|android.text" | head -20

# 4. Tocar un aviso: abre el vídeo en la app
adb shell am start -W -n $PKG/.MainActivity --activity-clear-top --activity-single-top \
  --es play dQw4w9WgXcQ --es title "Vídeo de prueba" --es channelName "Canal" --el published 0
sleep 20
adb exec-out screencap -p > 4-aviso.png
fallos "tocar aviso"

adb shell dumpsys jobscheduler | grep -B2 -A8 "$PKG" | head -40
exit 0
