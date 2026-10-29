# AgriAudit Docs

Aplicación para **gestionar la documentación de auditorías internacionales de empresas agrícolas**
y **generar los documentos en el formato esperado por cada estándar del sector**.

- **Backend:** Java 21 (LTS) · Spring Boot 4.1 (Spring Framework 7, Jackson 3) ·
  Spring Modulith 2.1 · Spring Security (JWT) · Flyway
- **Base de datos:** PostgreSQL 16
- **Frontend:** React 18 · TypeScript 7 · Vite 8 · MUI 9 · TanStack Query 5 · React Router 7 · Node 24 LTS
- **OCR:** Python 3.12 · FastAPI · RapidOCR (servicio aparte); el frontal se sirve con nginx 1.31
- **Generación de documentos:** PDF (Thymeleaf + openhtmltopdf), DOCX y XLSX (Apache POI)

**Trazabilidad de cambios:** registro inalterable de quién cambió qué y cuándo (con el valor anterior y el nuevo de
cada campo), encadenado con SHA-512 y firmado con HMAC-SHA-512, con verificación de integridad, sellos diarios
descargables y exportación a CSV. Visible para administración y calidad (toda la organización) y para cada auditora
(sus auditorías). Roles: administrador, auditor, **calidad** y lector.

**Privacidad (RGPD):** el historial guarda solo el código de usuario (el nombre se resuelve aparte y los datos
personales no se copian al historial). El administrador puede **bloquear los datos** de una persona (art. 32 LOPDGDD):
su nombre y correo dejan de verse en toda la aplicación, se reservan y se destruyen automáticamente al vencer el plazo
de conservación, configurable por organización (`AGRIAUDIT_BLOCKED_RETENTION_MONTHS`). También puede **exportar todos
los datos de la organización** en un ZIP (pantalla Privacidad).
Las evidencias adjuntas se pueden marcar como «contienen datos personales» (con aviso al subirlas); la marca solo la
ven administración, calidad y quien las subió.

**Listados propios:** cada organización importa los listados de su estándar desde Excel o CSV (plantilla
descargable, validación fila a fila y previsualización); son privados de la organización, las versiones nuevas no
alteran las auditorías ya hechas y los estándares de demostración se pueden ocultar.

Estándares implementados end-to-end: **GLOBALG.A.P. IFA v6**, **GRASP v2** y **SMETA 6.1 (4 pilares)**.
El esquema soporta añadir más estándares como datos semilla.

> La carpeta `docs/` (catálogo normativo, planes, requisitos, análisis de dominio) es
> **documentación interna y solo local**: está en `.gitignore` y no se versiona.

---

## Arranque rápido con Docker Compose

Requisitos: Docker Desktop (o Docker Engine + Compose v2).

```bash
git clone https://github.com/<tu-usuario>/agriaudit-docs.git
cd agriaudit-docs
cp .env.example .env
docker compose up --build
```

O con los atajos del `Makefile`: `make up` (arranca), `make smoke` (comprueba), `make down` (para).

Servicios:

| Servicio | URL | Notas |
|---|---|---|
| Frontend | http://localhost:8080 | SPA de React servida por nginx |
| Backend API | http://localhost:8081/api | REST + JWT |
| Swagger UI | http://localhost:8081/swagger-ui.html | Documentación de la API |
| Adminer (opcional) | http://localhost:8090 | `docker compose --profile debug up` |

**Usuario inicial** (creado/actualizado en cada arranque a partir de `.env`):

```
Correo:      admin@demo.test
Contraseña:  Admin123!
```

Cambia `ADMIN_PASSWORD` y `JWT_SECRET` en `.env` antes de cualquier uso real.

### Conexión directa a PostgreSQL (DBeaver, psql…)

| Campo | Valor |
|---|---|
| Host | `127.0.0.1` |
| Puerto | `5432` (configurable con `POSTGRES_PORT`; cámbialo si ya tienes otro PostgreSQL en el 5432) |
| Base de datos | `agriaudit` (`POSTGRES_DB`) |
| Usuario | `agriaudit` (`POSTGRES_USER`) |
| Contraseña | valor de `POSTGRES_PASSWORD` en tu `.env` (por defecto `agriaudit_pw_change_me`) |

`jdbc:postgresql://127.0.0.1:5432/agriaudit`

El contenedor solo publica el puerto en `127.0.0.1` (no en la red).

Las tablas están **agrupadas por módulo**, cada una en el esquema de PostgreSQL de su módulo
(`identity`, `catalog`, `fieldbook`, `storage`, `audit`, `findings`, `documents`, `ai`); en `public`
solo queda el historial de Flyway. En `psql`, cualifica el nombre (`SELECT * FROM audit.audits;`)
o ajusta la ruta de búsqueda (`SET search_path TO audit, findings, public;`).

> La contraseña de PostgreSQL solo se fija en la **primera** inicialización del volumen `pgdata`.
> Si cambias `POSTGRES_PASSWORD` después, ejecuta `docker compose down -v && docker compose up -d`
> para recrear la base de datos (se pierden los datos locales y se vuelven a aplicar las migraciones).

---

## Probar en un Codespace (sin instalar nada)

Para enseñar la app o revisar un PR con la aplicación viva, sin montar nada en local:

1. En GitHub: **`Code` → pestaña `Codespaces` → `Create codespace on main`**. El
   contenedor de desarrollo (`.devcontainer/`) trae Docker, Java 21 y Node 24, y
   crea el `.env` a partir de `.env.example`.
2. En la terminal del Codespace:

   ```bash
   make up
   ```

   La primera vez tarda unos minutos (construye las imágenes). Espera a que el
   backend esté sano:

   ```bash
   make smoke
   ```

3. Abre la aplicación por la pestaña **`PORTS`** de VS Code (abajo, junto a
   *Terminal*): en la fila **`8080`**, pulsa el icono del globo (*Open in
   Browser*). La URL es del tipo `https://<nombre>-8080.app.github.dev`.

   > **`http://localhost:8080` no funciona** desde tu navegador: apunta a tu
   > máquina, no al Codespace. Hay que usar siempre el enlace de la pestaña
   > `PORTS`. Para compartirlo con alguien externo: clic derecho en el puerto →
   > *Port Visibility* → *Public*.

4. Entra con `admin@demo.test` / `Admin123!` (juego de datos de demostración ya
   cargado).

El Codespace se **para solo** tras 30 min de inactividad y se **borra** tras
varias semanas sin uso; es un entorno de usar y tirar, no un `dev` permanente
con URL fija.

---

## Datos de demostración

Al arrancar con la base de datos vacía se carga automáticamente un juego de datos ficticio y coherente
(`AGRIAUDIT_DEMO_SEED=true`, desactívalo poniéndolo a `false`):

La historia de la demo: una **central hortofrutícola de la Región de Murcia** («Central Hortofrutícola del Segura»)
que gestiona la certificación de sus productores.

- **8 explotaciones** ficticias de Murcia (Molina de Segura, Fortuna, Lorca, Torre Pacheco, Cartagena, Cehegín, Ceuti,
  Fuente Álamo) con hortícolas, cítricos, fruta de hueso y uva de mesa.
- **2 usuarios adicionales**: `ana.auditora@demo.test` (AUDITOR, el que se usa en las demos) y
  `carlos.lector@demo.test` (VIEWER), contraseña `Demo1234!`.
- **12 auditorías** contra los 3 estándares, en todos los estados (borrador, en curso, cierre, enviada, cerrada) y tipos.
- Resultados de checklist con notas de evidencia, y **no conformidades** con textos realistas por capítulo
  (trazabilidad, fitosanitarios, agua de riego, formación…), clasificación y acciones correctivas en distintos estados.
- **Fechas relativas a hoy**: cada vez que se siembra, el panel muestra alguna no conformidad vencida, varias que vencen
  en los próximos 7 días y el resto con margen.
- Alguna no conformidad lleva una **foto de evidencia** de ejemplo (ficticia) para probar el análisis con OCR.
- **Documentos ya generados** (PDF/DOCX/XLSX) para las auditorías cerradas/enviadas/en cierre — el *Centro de documentos* no aparece vacío.

Es idempotente: si ya hay explotaciones, no vuelve a sembrar. Para regenerarlo con fechas nuevas:
`docker compose down -v && docker compose up -d`.

**Flujo de estados de una auditoría:** borrador → en curso → cierre → enviada → cerrada (con reapertura un paso atrás).
No se pasa a cierre con puntos de control sin evaluar ni se cierra con no conformidades sin cerrar o verificar.

---

## Reconocimiento de evidencias (OCR)

El servicio **`ocr`** del `docker compose` reconoce el texto de las evidencias adjuntas
(**PDF, PNG, JPG, escaneados y fotografías de notas manuscritas**) con **RapidOCR / PP-OCR**,
un motor **gratuito, de código abierto (Apache-2.0) y autoalojado**: no requiere clave de API,
no tiene coste por uso y **ningún dato sale del host** (relevante para las auditorías sociales
y el RGPD).

Flujo (siempre con confirmación del usuario, nada se guarda solo):

1. Adjunta una imagen o PDF a un punto de control o a una no conformidad.
2. Pulsa **Analizar** / **Analizar texto**.
3. El backend envía el fichero al servicio `ocr`, obtiene el texto y propone:
   - la **nota de evidencia** (texto reconocido, editable),
   - el **resultado de cumplimiento** sugerido (heurística por palabras clave, con su justificación),
   - borradores de **no conformidad** si el texto evidencia un incumplimiento.
4. El usuario revisa cada propuesta y pulsa *Aplicar* / *Crear* para cada una.

Configuración: `AGRIAUDIT_OCR_URL` (por defecto `http://ocr:8000`). Si lo dejas vacío, la función
queda desactivada y el botón *Analizar* no aparece.

> La heurística de cumplimiento/no conformidad es de palabras clave (gratuita y determinista).
> El OCR está detrás del puerto `DocExtractionPort` del módulo `ai`, de modo que el motor se puede
> sustituir (p. ej. por la visión de un LLM) sin tocar el resto.

---

## Flujo de uso

1. **Inicia sesión** con el usuario administrador.
2. **Explotaciones** → *Nueva explotación*: registra la empresa/finca agrícola (razón social, GGN, país, cultivos, hectáreas…).
3. **Auditorías** → *Nueva auditoría*: elige explotación, estándar y tipo (autoevaluación, interna, certificación…).
4. En la ficha de la auditoría:
   - **Checklist**: marca cada punto de control como *Conforme / No conforme / No aplica*, añade notas y **adjunta fotos o PDF como evidencia** en cada punto. Con el botón **Analizar** (véase *Reconocimiento de evidencias*), la IA de OCR lee el documento y **propone** la nota, el cumplimiento y posibles no conformidades para que los confirmes.
   - **Hallazgos y CAPA**: crea, **edita y elimina** no conformidades y sus acciones correctivas (causa raíz, corrección, acción preventiva, responsable, plazos, verificación); adjunta evidencia a cada no conformidad y analízala con OCR.
   - **Resumen**: consulta el resultado (% de *Major/Minor Musts*, semáforo) y el desglose por capítulo.
   - **Documentos**: genera y descarga el documento en el formato deseado (PDF / DOCX / XLSX). Las **imágenes adjuntas se embeben** en los informes y el resto de adjuntos se listan.
5. **Centro de documentos**: todos los documentos generados de la organización.
6. **Estándares** y **Normativa**: navegador del catálogo de puntos de control y reglamentos con enlace a la fuente oficial.

Roles: **ADMIN** (todo + usuarios), **AUDITOR** (explotaciones, auditorías, documentos), **VIEWER** (solo lectura).

---

## Documentos que genera la aplicación

| Documento | Formatos | Estilo de referencia |
|---|---|---|
| Plan de auditoría | PDF · DOCX | ISO 19011 |
| Checklist de evaluación | XLSX · PDF | Autoevaluación GLOBALG.A.P. |
| Informe de no conformidades | DOCX · PDF | GLOBALG.A.P. FA05 |
| Plan de acciones correctivas (CAPA / CAPR) | DOCX · XLSX | SMETA CAPR / SIZA |
| Informe de auditoría | PDF · DOCX | Informe completo con conclusión y firmas |
| Resumen de cumplimiento | PDF | % *Major/Minor Musts* + semáforo |
| Informe de evaluación GRASP | PDF · XLSX | Tabla "Sí/No/N.A." + resumen |
| Informe SMETA (4 pilares) | PDF · DOCX | Resumen por pilar + CAPR |

> **Aviso legal.** Los textos de los puntos de control son un resumen divulgativo elaborado a partir
> de fuentes públicas y **no reproducen literalmente** los documentos oficiales de los estándares
> (protegidos por derechos de autor de sus propietarios). El esquema permite importar los checklists
> oficiales que el cliente tenga licenciados. Verifica siempre los requisitos con la versión oficial vigente.

---

## Desarrollo local (sin Docker)

### Backend

Requisitos: JDK 21, Maven 3.9 y PostgreSQL 16 **con la extensión pgvector** en `localhost:5432` con base
`agriaudit` (la migración `V10` ejecuta `CREATE EXTENSION vector`). Lo más sencillo es levantar solo la
base de datos del Compose: `docker compose up -d db` (imagen `pgvector/pgvector:pg16`).

```bash
cd backend
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/agriaudit
export SPRING_DATASOURCE_USERNAME=agriaudit
export SPRING_DATASOURCE_PASSWORD=agriaudit
export JWT_SECRET=$(openssl rand -hex 32)
mvn spring-boot:run
```

### Frontend

Requisitos: Node 24 (LTS).

```bash
cd frontend
npm install
npm run dev        # http://localhost:5173, proxy /api -> http://localhost:8081
```

---

## Tests

### Backend

```bash
cd backend
mvn test
```

- `ScoringServiceTest` — reglas de puntuación (GLOBALG.A.P. 100 %/95 %, pilares SMETA).
- `DocumentRendererTest` — generación de DOCX y XLSX (cabeceras OOXML válidas).
- `PdfTemplateRenderTest` — procesado de todas las plantillas Thymeleaf y salida PDF válida (`%PDF-`).
- `AuditFlowIntegrationTest` — flujo completo login → explotación → auditoría → checklist → hallazgo → CAPA → generar y descargar documentos. **Requiere Docker** (Testcontainers PostgreSQL); se omite automáticamente si Docker no está disponible.

### Frontend

```bash
cd frontend
npm run test
```

### End-to-End de UI y accesibilidad (Playwright)

Con la app levantada (`make up`):

```bash
make e2e          # o: cd e2e && npm install && npm test
```

Prueba login, navegación, generación de documentos y accesibilidad (axe-core)
contra el frontend real. Ver [`e2e/README.md`](e2e/README.md).

### API (Karate)

Con la app levantada:

```bash
make api-test     # o: cd api-tests && mvn test
```

Pruebas de contrato y comportamiento de la API REST (auth, catálogo, auditorías,
documentos, panel). Ver [`api-tests/README.md`](api-tests/README.md).

---

## Estructura

```
agriaudit-docs/
├── docker-compose.yml
├── Makefile
├── .env.example
├── LICENSE                      # software propietario — todos los derechos reservados
├── .devcontainer/               # GitHub Codespaces
├── .github/                     # workflows de CI + CODEOWNERS
├── docs/                        # documentación interna — SOLO LOCAL (en .gitignore)
├── backend/
│   └── src/main/
│       ├── java/com/agriaudit/   # monolito modular (Spring Modulith, verificado en CI):
│       │                         #   identity · catalog · fieldbook · storage · audit · findings
│       │                         #   documents · dashboard · ai · platform · common
│       └── resources/
│           ├── db/migration/     # Flyway: un esquema de PostgreSQL por módulo + datos semilla
│           └── templates/docs/   # plantillas Thymeleaf de los PDF
├── frontend/
│   └── src/                      # api · auth · components · audit · pages
├── e2e/                          # tests E2E de UI + accesibilidad (Playwright)
└── api-tests/                    # tests de API (Karate)
```

---

## Licencia

Software **propietario**. Copyright © 2026 emilgo95. Todos los derechos
reservados. No se concede ninguna licencia de uso, copia, modificación o
distribución sin consentimiento previo por escrito. Ver [`LICENSE`](LICENSE).
