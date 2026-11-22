#!/usr/bin/env python3
"""Un Home Assistant de mentira para la prueba: solo las listas de tareas (todo.*) que se le dan, en memoria.

Uso: ha_falso.py PUERTO estado.json
  estado.json: {"todo.una_lista": [{"summary": "...", "description": "..."}], ...}
Las listas que no están en estado.json no existen (contesta 400, como Home Assistant).
GET /volcado devuelve las listas tal cual están, para mirarlas desde la prueba.
"""
import json
import sys
import uuid
from http.server import BaseHTTPRequestHandler, HTTPServer

port = int(sys.argv[1])
with open(sys.argv[2], encoding="utf-8") as f:
    lists = {
        name: [dict(uid=uuid.uuid4().hex, status="needs_action", **item) for item in items]
        for name, items in json.load(f).items()
    }


class Handler(BaseHTTPRequestHandler):
    def answer(self, code, obj):
        body = json.dumps(obj, ensure_ascii=False).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        if self.path.startswith("/volcado"):
            return self.answer(200, lists)
        if self.path.startswith("/api/states"):
            return self.answer(200, [])
        self.answer(404, {"message": "Not found"})

    def do_POST(self):
        length = int(self.headers.get("Content-Length", 0))
        data = json.loads(self.rfile.read(length) or b"{}")
        prefix = "/api/services/todo/"
        action = self.path.split(prefix)[-1].split("?")[0] if prefix in self.path else None
        entity = data.get("entity_id")
        if action is None or entity not in lists:
            return self.answer(400, {"message": "400: Bad Request"})
        items = lists[entity]

        def find(ref):
            return next((i for i in items if i["uid"] == ref or i["summary"] == ref), None)

        if action == "get_items":
            return self.answer(200, {"changed_states": [], "service_response": {entity: {"items": items}}})
        if action == "add_item":
            items.append({
                "uid": uuid.uuid4().hex,
                "summary": data["item"],
                "description": data.get("description", ""),
                "status": "needs_action",
            })
            return self.answer(200, [])
        if action == "update_item":
            item = find(data["item"])
            if item is None:
                return self.answer(400, {"message": "400: Bad Request"})
            if "rename" in data:
                item["summary"] = data["rename"]
            if "description" in data:
                item["description"] = data["description"]
            return self.answer(200, [])
        if action == "remove_item":
            item = find(data["item"])
            if item is not None:
                items.remove(item)
            return self.answer(200, [])
        self.answer(400, {"message": "400: Bad Request"})

    def log_message(self, *args):
        pass


HTTPServer(("0.0.0.0", port), Handler).serve_forever()
