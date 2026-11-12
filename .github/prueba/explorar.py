"""Mira cómo vienen hoy los datos de la búsqueda de YouTube y de la lista de vídeos de un canal
(con las mismas cabeceras que la app), para escribir bien el código que los lee. Lo lanza explorar.yml."""
import json, re, sys, urllib.parse, urllib.request
from collections import Counter

UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
HEAD = {"User-Agent": UA, "Accept-Language": "es-ES,es;q=0.9", "Cookie": "SOCS=CAI; CONSENT=YES+1"}

def get(url):
    return urllib.request.urlopen(urllib.request.Request(url, headers=HEAD), timeout=30).read().decode("utf-8")

def post(url, body):
    req = urllib.request.Request(url, data=json.dumps(body).encode(), headers={**HEAD, "Content-Type": "application/json"})
    return json.loads(urllib.request.urlopen(req, timeout=30).read().decode("utf-8"))

def initial(html):
    m = re.search(r"var ytInitialData = (\{.*?\});</script>", html, re.S) or re.search(r"ytInitialData\"?\] ?= ?(\{.*?\});", html, re.S)
    return json.loads(m.group(1))

def walk(node, path=""):
    if isinstance(node, dict):
        for k, v in node.items():
            yield k, v, path
            yield from walk(v, path + "/" + k)
    elif isinstance(node, list):
        for v in node:
            yield from walk(v, path)

def cut(obj, n=2500):
    s = json.dumps(obj, ensure_ascii=False)
    return s[:n] + ("…" if len(s) > n else "")

def report(data, title):
    print(f"\n######## {title}")
    names = Counter(k for k, v, p in walk(data) if k.endswith(("Renderer", "ViewModel")) and isinstance(v, dict))
    print("renderers:", names.most_common(40))
    shown = set()
    for k, v, p in walk(data):
        if k in ("videoRenderer", "channelRenderer", "lockupViewModel", "shortsLockupViewModel", "reelItemRenderer") and k not in shown:
            shown.add(k)
            print(f"\n--- {k} (ruta {p[-160:]})\n{cut(v)}")
    tokens = [v.get("token") for k, v, p in walk(data) if k == "continuationCommand" and isinstance(v, dict)]
    print("\ncontinuaciones:", [t[:40] if t else t for t in tokens][:5])
    return tokens

q = sys.argv[1] if len(sys.argv) > 1 else "realidad virtual"
html = get("https://www.youtube.com/results?search_query=" + urllib.parse.quote(q) + "&hl=es&gl=ES")
cfg_version = re.search(r'"INNERTUBE_CLIENT_VERSION":"([^"]+)"', html)
cfg_key = re.search(r'"INNERTUBE_API_KEY":"([^"]+)"', html)
print("clientVersion:", cfg_version and cfg_version.group(1), "apiKey:", bool(cfg_key))
tokens = report(initial(html), f"BÚSQUEDA «{q}»")
context = {"client": {"clientName": "WEB", "clientVersion": cfg_version.group(1), "hl": "es", "gl": "ES"}}
if tokens:
    more = post("https://www.youtube.com/youtubei/v1/search?prettyPrint=false", {"context": context, "continuation": tokens[-1]})
    print("\nclaves arriba de la continuación:", list(more.keys()))
    report(more, "BÚSQUEDA, MÁS RESULTADOS")

# la pestaña Vídeos de un canal y su continuación
html = get("https://www.youtube.com/channel/UCBJycsmduvYEL83R_U4JriQ/videos?hl=es&gl=ES")
tokens = report(initial(html), "CANAL /videos (hl=es)")
if tokens:
    more = post("https://www.youtube.com/youtubei/v1/browse?prettyPrint=false", {"context": context, "continuation": tokens[-1]})
    print("\nclaves arriba de la continuación:", list(more.keys()))
    report(more, "CANAL, MÁS VÍDEOS")
