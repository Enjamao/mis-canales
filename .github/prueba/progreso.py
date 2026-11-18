"""Pone un vídeo de los cargados como empezado y sin acabar (para la fila «Seguir viendo»).

Uso: python3 progreso.py store.xml videos.json [canal] > store-nuevo.xml
(con [canal], uno de ese canal: tiene que salir en Inicio)
"""
import html
import json
import re
import sys
import time

store = open(sys.argv[1], encoding='utf-8').read()
videos = json.load(open(sys.argv[2], encoding='utf-8'))
if len(sys.argv) > 3:
    videos = [v for v in videos if v.get('channelName') == sys.argv[3]]
if not videos:
    print(store, end='')
    sys.exit()
vid = videos[0]['vid']
found = re.search(r'<string name="progress">(.*?)</string>', store, re.S)
progress = json.loads(html.unescape(found.group(1))) if found else {}
progress[vid] = {'pos': 120, 'dur': 900, 'at': int(time.time() * 1000)}
entry = '<string name="progress">' + html.escape(json.dumps(progress), quote=True) + '</string>'
if found:
    store = store[:found.start()] + entry + store[found.end():]
else:
    store = store.replace('</map>', entry + '\n</map>')
print(store, end='')
print('empezado:', vid, videos[0].get('title', ''), file=sys.stderr)
