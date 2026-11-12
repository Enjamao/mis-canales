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
        if k in ("channelRenderer",) and k not in shown:
            shown.add(k)
            print(f"\n--- {k} (ruta {p[-160:]})\n{cut(v)}")
    tokens = [v["continuationEndpoint"]["continuationCommand"]["token"] for k, v, p in walk(data)
              if k == "continuationItemRenderer" and isinstance(v, dict) and "continuationEndpoint" in v
              and "continuationCommand" in v["continuationEndpoint"]]
    for k, v, p in walk(data):
        if k == "lockupMetadataViewModel":
            print("\n--- lockupMetadataViewModel\n" + cut(v, 3000))
            break
    vids = [v for k, v, p in walk(data) if k == "videoRenderer" and isinstance(v, dict)]
    for v in vids[:6]:
        short = "reelWatchEndpoint" in v.get("navigationEndpoint", {})
        print("vídeo:", v.get("videoId"), "| short" if short else "", "|", v.get("publishedTimeText"), "|", v.get("lengthText", {}).get("simpleText"),
              "|", v.get("viewCountText"), "|", [b.get("metadataBadgeRenderer", {}).get("label") for b in v.get("badges", [])],
              "| canal", v.get("ownerText", {}).get("runs", [{}])[0].get("navigationEndpoint", {}).get("browseEndpoint", {}).get("browseId"))
    print("\ncontinuaciones:", [t[:40] if t else t for t in tokens][:5])
    return tokens

q = sys.argv[1] if len(sys.argv) > 1 else "realidad virtual"
html = get("https://www.youtube.com/results?search_query=" + urllib.parse.quote(q) + "&hl=en&gl=ES")
cfg_version = re.search(r'"INNERTUBE_CLIENT_VERSION":"([^"]+)"', html)
cfg_key = re.search(r'"INNERTUBE_API_KEY":"([^"]+)"', html)
print("clientVersion:", cfg_version and cfg_version.group(1), "apiKey:", bool(cfg_key))
tokens = report(initial(html), f"BÚSQUEDA «{q}»")
context = {"client": {"clientName": "WEB", "clientVersion": cfg_version.group(1), "hl": "en", "gl": "ES"}}
if tokens:
    more = post("https://www.youtube.com/youtubei/v1/search?prettyPrint=false", {"context": context, "continuation": tokens[-1]})
    print("\nclaves arriba de la continuación:", list(more.keys()))
    report(more, "BÚSQUEDA, MÁS RESULTADOS")

# la pestaña Vídeos de un canal y su continuación (la de la cuadrícula), y en qué idioma vienen
for lang in ("es-ES,es;q=0.9", "en-US,en;q=0.9"):
    HEAD["Accept-Language"] = lang
    data = initial(get("https://www.youtube.com/channel/UCBJycsmduvYEL83R_U4JriQ/videos?hl=en&gl=ES"))
    grid = next(v for k, v, p in walk(data) if k == "richGridRenderer")
    items = grid["contents"]
    lockups = [v for k, v, p in walk(items) if k == "lockupViewModel"]
    meta = [[part["text"]["content"] for row in l["metadata"]["lockupMetadataViewModel"]["metadata"]["contentMetadataViewModel"]["metadataRows"] for part in row.get("metadataParts", [])] for l in lockups[:3]]
    print(f"\n######## CANAL con Accept-Language {lang}: {len(lockups)} vídeos; metadatos {meta}")
    print("contentType y ids:", [(l.get("contentType"), l.get("contentId")) for l in lockups[:3]])
    print("badge duración:", [b["thumbnailBadgeViewModel"]["text"] for l in lockups[:3] for o in l["contentImage"]["thumbnailViewModel"].get("overlays", []) for b in o.get("thumbnailBottomOverlayViewModel", {}).get("badges", [])])
    token = items[-1]["continuationItemRenderer"]["continuationEndpoint"]["continuationCommand"]["token"]
    ctx = {"client": {"clientName": "WEB", "clientVersion": cfg_version.group(1), "hl": "en", "gl": "ES"}}
    more = post("https://www.youtube.com/youtubei/v1/browse?prettyPrint=false", {"context": ctx, "continuation": token})
    lockups = [v for k, v, p in walk(more) if k == "lockupViewModel"]
    meta = [[part["text"]["content"] for row in l["metadata"]["lockupMetadataViewModel"]["metadata"]["contentMetadataViewModel"]["metadataRows"] for part in row.get("metadataParts", [])] for l in lockups[:3]]
    nxt = [v for k, v, p in walk(more) if k == "continuationItemRenderer"]
    print(f"continuación: {len(lockups)} vídeos más; metadatos {meta}; ¿sigue? {bool(nxt)}")
    sdata = initial(get("https://www.youtube.com/results?search_query=" + urllib.parse.quote(q) + "&hl=en&gl=ES"))
    vids = [v for k, v, p in walk(sdata) if k == "videoRenderer"]
    print("búsqueda:", [(v.get("publishedTimeText", {}).get("simpleText"), v.get("viewCountText", {}).get("simpleText")) for v in vids[:4]])
    ch = [v for k, v, p in walk(sdata) if k == "channelRenderer"]
    print("canales:", [(c["title"]["simpleText"], c.get("videoCountText", {}).get("simpleText"), c.get("subscriberCountText", {}).get("simpleText")) for c in ch])
