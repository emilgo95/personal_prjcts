# Mapa de Valinor (Aman 3D)

Visor 3D interactivo de Aman, el continente de Valinor, hecho con [three.js](https://threejs.org/). Modelo creado en Blender y exportado a glTF.

## Ejecutar

Necesitas Python 3 y conexión a internet (three.js y las tipografías se cargan desde CDN).

```bash
cd valinor-map
python3 serve.py
```

Se abre solo `http://localhost:8000/aman_visor.html`. Si no, ábrela a mano. Alternativa sin script: `python3 -m http.server 8000` y entra en esa URL.

> No funciona abriendo el `.html` con doble clic (`file://`): el navegador bloquea los módulos ES y la carga del modelo.

## Archivos

| Archivo | Contenido |
|---|---|
| `aman_visor.html` | Visor completo (UI, escena, luces, vegetación, etiquetas) |
| `aman_web.glb` | Modelo 3D del terreno y los lugares |
| `aman_web.json` | Materiales, alturas del terreno y datos de lugares |
| `aman_inventario.json` | Inventario de objetos del modelo |
| `serve.py` | Servidor local mínimo |

three.js se fija a la versión 0.170.0 en el `importmap` de `aman_visor.html`.
