"""Sirve el visor en http://localhost:8000/aman_visor.html (los módulos ES y fetch no funcionan con file://)."""
import http.server, socketserver, webbrowser

PUERTO = 8000

class Handler(http.server.SimpleHTTPRequestHandler):
    extensions_map = {**http.server.SimpleHTTPRequestHandler.extensions_map, ".glb": "model/gltf-binary", ".js": "text/javascript"}

if __name__ == "__main__":
    with socketserver.TCPServer(("", PUERTO), Handler) as srv:
        url = f"http://localhost:{PUERTO}/aman_visor.html"
        print("Visor en", url, "(Ctrl+C para parar)")
        webbrowser.open(url)
        srv.serve_forever()
