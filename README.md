# AgriAudit Docs

Aplicación para **gestionar la documentación de auditorías internacionales de empresas agrícolas**
y **generar los documentos en el formato esperado por cada estándar del sector**.

- **Backend:** Java 21 · Spring Boot 3.3 · Spring Security (JWT) · Flyway
- **Base de datos:** PostgreSQL 16
- **Frontend:** React 18 · TypeScript · Vite · MUI · TanStack Query
- **Generación de documentos:** PDF (Thymeleaf + openhtmltopdf), DOCX y XLSX (Apache POI)

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

> La contraseña de PostgreSQL solo se fija en la **primera** inicialización del volumen `pgdata`.
> Si cambias `POSTGRES_PASSWORD` después, ejecuta `docker compose down -v && docker compose up -d`
> para recrear la base de datos (se pierden los datos locales y se vuelven a aplicar las migraciones).

---

## Datos de demostración

Al arrancar con la base de datos vacía se carga automáticamente un juego de datos ficticio y coherente
(`AGRIAUDIT_DEMO_SEED=true`, desactívalo poniéndolo a `false`):

- **5 explotaciones** en España, Perú, Sudáfrica y Marruecos (frutos rojos, cítricos, uva de mesa, pomáceas, hortícolas).
- **2 usuarios adicionales**: `ana.auditora@demo.test` (AUDITOR) y `carlos.lector@demo.test` (VIEWER), contraseña `Demo1234!`.
- **8 auditorías** contra los 3 estándares, en todos los estados (borrador, en curso, cierre, enviada, cerrada) y tipos.
- Resultados de checklist, no conformidades (`NC-001`…) con clasificación y plazos, y acciones correctivas en distintos estados.
- **Documentos ya generados** (PDF/DOCX/XLSX) para las auditorías cerradas/enviadas/en cierre — el *Centro de documentos* no aparece vacío.

Es idempotente: si ya hay explotaciones, no vuelve a sembrar. Para regenerarlo: `docker compose down -v && docker compose up -d`.

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
> El interfaz `OcrClient` está aislado para poder sustituir el motor o añadir un LLM para una
> interpretación más fina en el futuro.

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

Requisitos: JDK 21, Maven 3.9, PostgreSQL 16 en `localhost:5432` con base `agriaudit`.

```bash
cd backend
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/agriaudit
export SPRING_DATASOURCE_USERNAME=agriaudit
export SPRING_DATASOURCE_PASSWORD=agriaudit
export JWT_SECRET=$(openssl rand -hex 32)
mvn spring-boot:run
```

### Frontend

Requisitos: Node 20+.

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

---

## Estructura

```
agriaudit-docs/
├── docker-compose.yml
├── Makefile
├── .env.example
├── .devcontainer/               # GitHub Codespaces
├── .github/workflows/           # CI (build + tests + smoke) y CD
├── deploy/k8s/                  # manifiestos Kubernetes (base + overlays)
├── docs/                        # documentación interna — SOLO LOCAL (en .gitignore)
├── backend/
│   └── src/main/
│       ├── java/com/agriaudit/   # domain · repo · security · service · web
│       └── resources/
│           ├── db/migration/     # Flyway: esquema + datos semilla
│           └── templates/docs/   # plantillas Thymeleaf de los PDF
└── frontend/
    └── src/                      # api · auth · components · audit · pages
```
