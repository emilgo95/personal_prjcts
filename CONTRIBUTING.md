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

> Nota: la protección de rama del lado de GitHub necesita el plan **Pro** para
> repos privados. Mientras el repo siga en plan Free, la disciplina de PR se
> apoya en este hook local y en la convención del equipo. Si se pasa a Pro,
> crear un *ruleset* sobre `main` con el check requerido `ci-ok`.

## CI

`.github/workflows/ci.yml` compila y testea backend y frontend, levanta la app
con Docker Compose y hace un *smoke test*, y valida los manifiestos de
Kubernetes. El job `ci-ok` resume el resultado: es el que hay que ver en verde
antes de fusionar.

## Antes de abrir el PR

```bash
make test     # tests de backend y frontend
make up       # la app arranca en local
make smoke    # responde
```
