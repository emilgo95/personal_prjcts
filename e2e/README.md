# Tests End-to-End (Playwright)

Pruebas de interfaz y accesibilidad de AgriAudit Docs. Se ejecutan contra la
aplicación **tal y como se despliega con `docker compose`** (frontend en
`:8080`, API en `:8081`), no contra el servidor de desarrollo de Vite.

## Requisitos

- Node 20+
- La pila levantada: desde la raíz del repo `make up` (o `docker compose up -d`).

## Puesta en marcha

```bash
cd e2e
npm install
npm run install:browsers   # descarga Chromium (una vez)
npm test
```

Informe HTML tras la ejecución:

```bash
npm run report
```

## Variables de entorno

| Variable | Por defecto | Uso |
|---|---|---|
| `E2E_BASE_URL` | `http://localhost:8080` | URL del frontend |
| `E2E_API_URL` | `http://localhost:8081` | URL de la API (login directo) |
| `E2E_ADMIN_EMAIL` | `admin@demo.test` | Usuario de las pruebas |
| `E2E_ADMIN_PASSWORD` | `Admin123!` | Contraseña |

## Qué cubre

| Fichero | Escenario |
|---|---|
| `tests/auth.spec.ts` | Guarda de rutas, login correcto/incorrecto, cierre de sesión |
| `tests/navigation.spec.ts` | Panel, listado de auditorías, catálogo de estándares, centro de documentos, normativa |
| `tests/documents.spec.ts` | Generar un documento desde una auditoría y descargarlo |
| `tests/a11y.spec.ts` | Accesibilidad (axe-core, WCAG 2.1 A/AA) en login y panel |

Las pruebas asumen el juego de datos de demostración (`AGRIAUDIT_DEMO_SEED=true`).

## CI

El job `e2e` de `.github/workflows/ci.yml` levanta la pila con Docker Compose,
ejecuta estas pruebas y publica el informe como artefacto.
