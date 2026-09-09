# Tests de API (Karate)

Pruebas de contrato y comportamiento de la API REST de AgriAudit Docs. Se
ejecutan por HTTP contra la pila ya desplegada (`docker compose`), sin arrancar
el backend desde aquí.

## Requisitos

- JDK 21 y Maven 3.9
- Backend accesible (por defecto `http://localhost:8081`) — `make up` en la raíz.

## Ejecución

```bash
cd api-tests
mvn test
```

Contra otro entorno:

```bash
mvn test -Dapi.baseUrl=https://mi-entorno.example -Dapi.adminEmail=admin@demo.test -Dapi.adminPassword=Admin123!
```

También se leen las variables de entorno `API_BASE_URL`, `API_ADMIN_EMAIL`,
`API_ADMIN_PASSWORD` (las usa el job de CI).

## Informe

Tras la ejecución: `target/karate-reports/karate-summary.html`.

## Qué cubre

| Feature | Área |
|---|---|
| `auth.feature` | Login, refresh de contrato del token, `/auth/me`, 401 sin token, token manipulado |
| `catalog.feature` | Estándares (GLOBALG.A.P. IFA v6, GRASP v2, SMETA 6.1), capítulos, puntos de control, normativa |
| `audits.feature` | Listado y detalle de auditorías, resultados de checklist, `/score`, validación al crear |
| `documents.feature` | Opciones de documento, generación (201), descarga de bytes, listado de la organización |
| `dashboard.feature` | Agregados del panel y exigencia de autenticación |

Las pruebas asumen el juego de datos de demostración (`AGRIAUDIT_DEMO_SEED=true`).

## CI

El job `api-tests` de `.github/workflows/ci.yml` levanta la pila con Docker
Compose, ejecuta `mvn test` aquí y publica `target/karate-reports` como artefacto.
