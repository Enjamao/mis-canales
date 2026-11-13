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
    print('textos:', [t for t in re.findall(r' text="([^"]+)"', xml)][:60])
PY
}

pulsar_abajo() {
  # el más bajo de la pantalla con ese texto (las pestañas de abajo: "Canales", "Inicio"…)
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null; adb pull /sdcard/ui.xml ui.xml >/dev/null
  python3 - "$1" <<'PY'
import re, subprocess, sys
xml = open('ui.xml', encoding='utf-8').read()
found = []
for m in re.finditer(r'<node [^>]*>', xml):
    if f'text="{sys.argv[1]}"' in m.group(0):
        b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', m.group(0))
        found.append(((int(b.group(2)) + int(b.group(4))) // 2, (int(b.group(1)) + int(b.group(3))) // 2))
if found:
    y, x = sorted(found)[-1]
    subprocess.run(['adb', 'shell', 'input', 'tap', str(x), str(y)])
    print('pulsado (abajo)', sys.argv[1], x, y)
else:
    print('no está en pantalla:', sys.argv[1])
PY
}

pulsar_arriba() {
  # como pulsar, pero el más alto de la pantalla (la barra de abajo repite "Añadir" y "Canales")
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null; adb pull /sdcard/ui.xml ui.xml >/dev/null
  python3 - "$1" "${2:-0}" <<'PY'
import re, subprocess, sys
xml = open('ui.xml', encoding='utf-8').read()
found = []
for m in re.finditer(r'<node [^>]*>', xml):
    node = m.group(0)
    if f'text="{sys.argv[1]}"' in node:
        b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', node)
        found.append(((int(b.group(2)) + int(b.group(4))) // 2, (int(b.group(1)) + int(b.group(3))) // 2))
if not found:
    print('no está en pantalla:', sys.argv[1])
else:
    y, x = sorted(found)[0]
    # con un segundo argumento, esos píxeles más abajo (p. ej. el primer canal, bajo el título "Canales")
    y += int(sys.argv[2])
    subprocess.run(['adb', 'shell', 'input', 'tap', str(x), str(y)])
    print('pulsado', sys.argv[1], '+', sys.argv[2], x, y)
PY
}

textos() {
  echo "--- pantalla: $1"
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null; adb pull /sdcard/ui.xml ui.xml >/dev/null
  python3 -c "import re;print(re.findall(r' text=\"([^\"]+)\"', open('ui.xml',encoding='utf-8').read())[:45])"
}

escribir() {
  # toca la primera caja de texto y escribe (Intro = buscar)
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null; adb pull /sdcard/ui.xml ui.xml >/dev/null
  python3 - <<'PY'
import re, subprocess
xml = open('ui.xml', encoding='utf-8').read()
node = re.search(r'<node [^>]*class="android.widget.EditText"[^>]*>', xml)
if node:
    b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', node.group(0))
    subprocess.run(['adb', 'shell', 'input', 'tap', str((int(b.group(1)) + int(b.group(3))) // 2), str((int(b.group(2)) + int(b.group(4))) // 2)])
else:
    print('no hay caja de texto')
PY
  sleep 1
  adb shell input text "$1"
  sleep 1
  adb shell input keyevent KEYCODE_ENTER
}

bajar_hasta() {
  SIZE=$(adb shell wm size | grep -oE '[0-9]+x[0-9]+' | tail -1); W=${SIZE%x*}; H=${SIZE#*x}
  for i in $(seq 1 30); do
    adb shell uiautomator dump /sdcard/ui.xml >/dev/null; adb pull /sdcard/ui.xml ui.xml >/dev/null
    grep -q "$1" ui.xml && return 0
    adb shell input swipe $((W/2)) $((H*2/3)) $((W/2)) $((H/3)) 300; sleep 1
  done
  echo "no aparece: $1"
}

adb install -r "$APK"
adb logcat -c; adb logcat -c -b crash

# Como el móvil de Eloy: con canales (y una versión anterior ya usada: sin avisos apuntados)
CHANNELS='[{&quot;id&quot;:&quot;UCBJycsmduvYEL83R_U4JriQ&quot;,&quot;name&quot;:&quot;Marques Brownlee&quot;},{&quot;id&quot;:&quot;UCupvZG-5ko_eiXAupbDfxWw&quot;,&quot;name&quot;:&quot;CNN&quot;}]'
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

# 1b. Con el móvil en reposo profundo (Doze) la alarma tiene que sonar igual (en la prueba, cada 3 min).
#     La app, como en el móvil de Eloy, con la batería "Sin restricciones".
adb shell dumpsys deviceidle whitelist +$PKG
adb shell input keyevent KEYCODE_HOME
adb shell dumpsys battery unplug
adb shell dumpsys deviceidle force-idle
adb shell dumpsys deviceidle get deep
# Android puede retrasarla un poco (hasta 3/4 del intervalo): se espera hasta 9 minutos
for i in $(seq 1 27); do
  adb logcat -d | grep -q "revisión (alarma)" && break
  sleep 20
done
echo "--- registro de la app en reposo (tras $((i * 20)) s):"
adb logcat -d | grep "MisCanales" | tail -10
adb shell dumpsys deviceidle get deep
adb shell dumpsys alarm | grep -A3 "es.miscanales" | head -12
adb shell dumpsys deviceidle unforce
adb shell dumpsys battery reset
fallos "reposo"

# 2. La revisión periódica, dos veces (la primera apunta, la segunda compara)
adb shell cmd jobscheduler run -f $PKG 4201
sleep 30
fallos "revisión 1"
adb shell run-as $PKG cat shared_prefs/nuevos.xml | head -c 600; echo
# se le hace olvidar los 3 vídeos más nuevos (CNN sube a diario) para que avise de ellos de verdad
adb shell input keyevent KEYCODE_HOME; sleep 2
adb shell am kill $PKG; sleep 2
adb shell run-as $PKG cat shared_prefs/nuevos.xml > nuevos.xml
python3 - <<'PY'
import re
s = open('nuevos.xml', encoding='utf-8').read()
m = re.search(r'<string name="videos">([^<]*)</string>', s)
ids = m.group(1).split(',')
print('olvidados:', ids[:3])
s = s.replace(m.group(0), '<string name="videos">' + ','.join(ids[3:]) + '</string>')
open('nuevos.xml', 'w', encoding='utf-8').write(s)
PY
adb shell "run-as $PKG sh -c 'cat > shared_prefs/nuevos.xml'" < nuevos.xml
adb shell cmd jobscheduler run -f $PKG 4201
sleep 40
fallos "revisión 2 (con avisos)"
echo "--- avisos de la app ---"
adb shell dumpsys notification --noredact | grep -A40 "pkg=$PKG" | grep -E "android.title=|android.text=|android.summaryText=|key=" | head -30

# 3. El botón de probar el aviso: Canales → bajar a Ajustes → Probar con el último vídeo
adb shell am start -W -n $PKG/.MainActivity
sleep 5
pulsar "Canales"
sleep 3
SIZE=$(adb shell wm size | grep -oE '[0-9]+x[0-9]+' | tail -1); W=${SIZE%x*}; H=${SIZE#*x}; echo "pantalla $W x $H"
# baja poco a poco hasta que aparezca el botón (los ajustes de HA van después)
for i in $(seq 1 20); do
  adb shell input swipe $((W/2)) $((H*2/3)) $((W/2)) $((H/3)) 400; sleep 1
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null; adb pull /sdcard/ui.xml ui.xml >/dev/null
  grep -q 'Probar con el último vídeo' ui.xml && break
  python3 -c "import re;print('textos:', re.findall(r' text=\"([^\"]+)\"', open('ui.xml',encoding='utf-8').read())[:12])"
done
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

# 5. Buscar en mis canales (lo cargado y lo que YouTube encuentra dentro de cada canal)
adb shell input keyevent KEYCODE_BACK; sleep 2
adb shell am start -W -n $PKG/.MainActivity; sleep 3
pulsar "Buscar"; sleep 2
escribir "iphone"; sleep 25
textos "buscar «iphone» en mis canales"
fallos "buscar en mis canales"

# 6. En todo YouTube, y más resultados
pulsar "Todo YouTube"; sleep 15
textos "«iphone» en todo YouTube"
fallos "buscar en todo YouTube"
bajar_hasta "Más resultados"; pulsar "Más resultados"; sleep 12
textos "más resultados"
fallos "más resultados"

# 7. Un canal de los resultados: sus vídeos y el botón de añadir
# arriba del todo (cambiando de pestaña) para tener a mano la caja y su botón de borrar
pulsar_abajo "Inicio"; sleep 2; pulsar_abajo "Buscar"; sleep 2
pulsar "Borrar"; sleep 1
escribir "realidad%svirtual"; sleep 15
textos "«realidad virtual» en todo YouTube"
# el primer canal: la fila de debajo del título "Canales" (el de arriba, no el de la barra)
pulsar_arriba "Canales" 50
sleep 15
textos "vídeos del canal encontrado"
fallos "canal encontrado"
pulsar_arriba "Añadir"; sleep 10
textos "tras añadir"
fallos "añadir canal"
bajar_hasta "Ver más"; pulsar "Ver más"; sleep 12
textos "más vídeos del canal encontrado"
fallos "más vídeos del canal encontrado"
adb shell input keyevent KEYCODE_BACK; sleep 2

# 8. Los vídeos anteriores de uno de tus canales
pulsar_abajo "Canales"; sleep 3
pulsar "Marques Brownlee"; sleep 5
textos "canal Marques Brownlee"
bajar_hasta "Ver vídeos anteriores"; pulsar "Ver vídeos anteriores"; sleep 15
textos "vídeos anteriores de Marques Brownlee"
bajar_hasta "Ver más"; pulsar "Ver más"; sleep 12
textos "más vídeos anteriores"
fallos "vídeos anteriores"

# 9. Kids: marcar un canal «Para niños» en la app de los mayores
adb shell input keyevent KEYCODE_BACK; sleep 1
adb shell am start -W -n $PKG/.MainActivity; sleep 3
pulsar_abajo "Canales"; sleep 3
pulsar "Para niños"; sleep 3
textos "canales, con uno «Para niños»"
fallos "marcar para niños"

# 10. Abrir Mis Canales Kids (su propio icono)
adb shell am start -W -n $PKG/.KidsActivity; sleep 15
textos "Kids"
fallos "abrir Kids"

# 11. Ajustes de Kids: crear el PIN 1234 (dos veces), 30 min al día y modo peque
pulsar "Ajustes de los mayores"; sleep 3
adb shell input text 1234; sleep 3
adb shell input text 1234; sleep 3
textos "ajustes de Kids"
pulsar "30 min"; sleep 1
pulsar "Miniaturas grandes y sin textos"; sleep 1
pulsar "Listo"; sleep 3
textos "Kids en modo peque, con 30 min"
fallos "ajustes de Kids"

# 12. Ver un vídeo en Kids (tocando la primera miniatura grande) y que cuente el tiempo
SIZE=$(adb shell wm size | grep -oE '[0-9]+x[0-9]+' | tail -1); W=${SIZE%x*}; H=${SIZE#*x}
adb shell input tap $((W/2)) $((H*2/5)); sleep 40
adb shell dumpsys activity activities | grep -E "topResumedActivity|ResumedActivity" | head -3
adb shell input keyevent KEYCODE_BACK; sleep 4
textos "Kids tras ver un vídeo"
adb shell run-as $PKG cat shared_prefs/store.xml | grep -E "kids|askPin|pin" | head
fallos "ver un vídeo en Kids"

# 13. «Pedir PIN al abrir» en la app de los mayores
adb shell am start -W -n $PKG/.MainActivity; sleep 3
pulsar_abajo "Canales"; sleep 2
bajar_hasta "Pedir PIN al abrir Mis Canales"
pulsar "Pedir PIN al abrir Mis Canales"; sleep 3
adb shell input text 1234; sleep 3
textos "con «Pedir PIN al abrir»"
adb shell am force-stop $PKG; sleep 1
adb shell am start -W -n $PKG/.MainActivity; sleep 6
textos "al abrir: tiene que pedir el PIN"
adb shell input text 1111; sleep 2
textos "PIN malo"
adb shell input text 1234; sleep 4
textos "tras el PIN bueno"
fallos "PIN al abrir"

adb shell dumpsys jobscheduler | grep -B2 -A8 "$PKG" | head -40
exit 0
