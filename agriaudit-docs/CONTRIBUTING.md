# Cómo contribuir a AgriAudit Docs

## Flujo de trabajo: siempre con rama + Pull Request

**No se hace `git push` directo a `main`.** Todo cambio pasa por una rama y un PR.

```bash
# 1. Rama a partir de main actualizado
git switch main && git pull
git switch -c feat/descripcion-corta

# 2. Cambios + commits (conventional commits)
git add -A
git commit -m "feat: ..."

# 3. Subir y abrir el PR
git push -u origin feat/descripcion-corta
gh pr create --fill

# 4. Cuando el CI (ci-ok) esté en verde, fusionar
gh pr merge --squash    # o --rebase ; nunca merge commit
```

Prefijos de rama: `feat/`, `fix/`, `chore/`, `docs/`, `refactor/`, `test/`, `ci/`.

### Activar el guard local (una vez, tras clonar)

```bash
git config core.hooksPath .githooks
```

El hook `pre-push` rechaza el push a `main`. Excepción puntual justificada:
`AGRIAUDIT_ALLOW_MAIN=1 git push ...`.

> `main` está protegida además por un *ruleset* de GitHub (PR obligatorio,
> historial lineal, check requerido `ci-ok`, resolución de conversaciones, sin
> *force-push*). El hook local es una red de seguridad que falla antes de llegar
> a la red.

## CI

`.github/workflows/ci.yml` compila y testea backend y frontend, y en el job
`e2e` levanta la app con Docker Compose para pasar el *smoke test*, los tests
**End-to-End de UI y accesibilidad (Playwright)** y los tests de **API
(Karate)**; los informes se publican como artefactos. El job `codeql` analiza
Java y TypeScript con la CLI de **CodeQL**: como el repositorio es privado y no admite
*code scanning*, no sube resultados a GitHub; `.github/scripts/codeql_gate.py` anota los
hallazgos en el PR y **bloquea con severidad alta o crítica** (el SARIF queda como
artefacto). Un hallazgo que no aplica se registra en `.github/codeql-exceptions.json`
con su justificación, el test que protege esa premisa y una fecha de revisión. El job `secrets-scan` pasa **gitleaks** sobre los commits (configuración
y lista de permitidos en `.gitleaks.toml`). El job `ci-ok` resume el resultado de
todos: es el que hay que ver en verde antes de fusionar.

Dentro de `mvn verify` son bloqueantes los límites de módulo (Spring Modulith y
ArchUnit) y la higiene de datos entre módulos: cada entidad declara el esquema de
su módulo (`@Table(schema = "<módulo>")`) y entre módulos se guarda el identificador,
nunca una asociación JPA; los datos de otro módulo se leen por su API.

## Tests

| Suite | Dónde | Comando |
|---|---|---|
| Unitarios/integración backend | `backend/` | `cd backend && mvn verify` |
| Unitarios frontend | `frontend/` | `cd frontend && npm test -- --run` |
| E2E de UI + accesibilidad (Playwright) | `e2e/` | `make e2e` (con la app levantada) |
| API (Karate) | `api-tests/` | `make api-test` (con la app levantada) |

## Antes de abrir el PR

```bash
make test     # tests de backend y frontend
make up       # la app arranca en local
make smoke    # responde
make e2e      # tests E2E de UI (Playwright)
make api-test # tests de API (Karate)
```
