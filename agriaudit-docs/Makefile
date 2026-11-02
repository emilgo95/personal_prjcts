# AgriAudit Docs — atajos para desarrollo local.
# Requiere Docker + Docker Compose. En Windows, usar Git Bash o WSL.

.DEFAULT_GOAL := help
COMPOSE := docker compose

.PHONY: help
help: ## Muestra esta ayuda
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | \
	  awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-12s\033[0m %s\n", $$1, $$2}'

.PHONY: setup
setup: init ## Configuración inicial tras clonar (hooks de git + .env)
	@git config core.hooksPath .githooks
	@echo "Hooks activados (.githooks). Push directo a 'main' bloqueado: usa ramas + PR."

.PHONY: init
init: ## Crea el .env a partir de la plantilla (si no existe)
	@test -f .env || cp .env.example .env
	@echo "Listo. Revisa .env antes de 'make up'."

.PHONY: up
up: init ## Levanta toda la aplicación (build + arranque)
	$(COMPOSE) up --build -d
	@echo ""
	@echo "  Frontend : http://localhost:$${FRONTEND_PORT:-8080}"
	@echo "  API : http://localhost:$${BACKEND_PORT:-8081}/api (Swagger con AGRIAUDIT_API_DOCS=true)"
	@echo "  Login demo : admin@demo.test / Admin123!"

.PHONY: down
down: ## Para la aplicación (conserva los datos)
	$(COMPOSE) down

.PHONY: clean
clean: ## Para y BORRA los datos (volúmenes)
	$(COMPOSE) down -v

.PHONY: logs
logs: ## Sigue los logs de todos los servicios
	$(COMPOSE) logs -f

.PHONY: ps
ps: ## Estado de los contenedores
	$(COMPOSE) ps

.PHONY: rebuild
rebuild: ## Reconstruye las imágenes sin caché
	$(COMPOSE) build --no-cache

.PHONY: test
test: ## Ejecuta los tests del backend y del frontend
	cd backend && mvn -B -ntp verify
	cd frontend && npm ci && npm test -- --run

.PHONY: smoke
smoke: ## Comprueba que la app responde (tras 'make up')
	@curl -fsS http://localhost:$${BACKEND_PORT:-8081}/actuator/health | grep -q UP && echo "backend OK" || (echo "backend KO"; exit 1)
	@curl -fsS -o /dev/null -w "frontend HTTP %{http_code}\n" http://localhost:$${FRONTEND_PORT:-8080}

.PHONY: e2e
e2e: ## Tests End-to-End de UI y accesibilidad (Playwright) — requiere 'make up'
	cd e2e && npm install && npx playwright install --with-deps chromium && npm test

.PHONY: api-test
api-test: ## Tests de la API (Karate) — requiere 'make up'
	cd api-tests && mvn -B -ntp test

.PHONY: db
db: ## Abre una consola psql contra la base de datos
	$(COMPOSE) exec db psql -U $${POSTGRES_USER:-agriaudit} -d $${POSTGRES_DB:-agriaudit}

.PHONY: adminer
adminer: ## Levanta Adminer (UI de BD) en http://localhost:8090
	$(COMPOSE) --profile debug up -d adminer
	@echo "Adminer: http://localhost:8090  (Sistema: PostgreSQL, Servidor: db)"
