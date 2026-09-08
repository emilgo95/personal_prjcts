# Despliegue en Kubernetes

Manifiestos para desplegar AgriAudit Docs (monolito: `backend` + `ocr` + `frontend`) en el
**clúster de Kubernetes ya existente**. Configuración con **Kustomize** (`base` + `overlays`),
config no secreta en **ConfigMap**, secretos fuera de Git.

> Esta fase **no** monta la nube gestionada (Postgres, objetos, broker). Ver
> `docs/PLAN-MIGRACION-ARQUITECTURA-B.md` §Fases.

## Estructura

```
deploy/k8s/
├── base/
│   ├── kustomization.yaml     # ensambla los 3 componentes + configMapGenerator + images
│   ├── backend.yaml           # Deployment, Service, ServiceAccount, HPA, PDB
│   ├── ocr.yaml               # Deployment, Service, NetworkPolicy (solo backend lo llama)
│   ├── frontend.yaml          # Deployment, Service, Ingress (/ y /api)
│   └── secret.example.yaml    # PLANTILLA — no se aplica; explica Sealed/External Secrets
└── overlays/
    ├── dev/      kustomization.yaml + config.env
    ├── staging/  kustomization.yaml + config.env
    └── prod/     kustomization.yaml + config.env   (más réplicas y recursos)
```

## Requisitos previos en el clúster

| Necesita | Para qué |
|---|---|
| Ingress controller (nginx/traefik) | Exponer `/` y `/api`. Ajustar `ingressClassName` y anotaciones en `base/frontend.yaml`. |
| cert-manager *(opcional)* | TLS automático. Si no está, crear el Secret `agriaudit-tls` a mano. |
| Sealed Secrets **o** External Secrets Operator | Gestionar `backend-secrets` sin ponerlo en claro en Git. |
| metrics-server | Que el `HorizontalPodAutoscaler` funcione. |
| Prometheus *(opcional)* | Scrapea `/actuator/prometheus` vía las anotaciones del pod. |

## Antes del primer despliegue: cambios en el código

1. **Ficheros a almacenamiento de objetos.** El backend hoy escribe en `/data/storage`
   (volumen local). Los manifiestos asumen `readOnlyRootFilesystem` + 2 réplicas, lo que
   **exige** un `FileStoragePort` con adaptador S3-compatible (MinIO en el clúster vale).
   Mientras no esté: `replicas: 1` y un PVC `ReadWriteMany` en `/data/storage`.
2. **PostgreSQL fuera del clúster.** Apuntar `SPRING_DATASOURCE_URL` (en el Secret) a una
   instancia gestionada o a una VM con backups. **No** correr Postgres como StatefulSet
   propio salvo que se use un operador (CloudNativePG) y alguien lo mantenga.
3. **Frontend con nginx *unprivileged*.** Cambiar `frontend/Dockerfile` a
   `nginxinc/nginx-unprivileged` y `frontend/nginx.conf` a `listen 8080;`.
4. **Perfil `k8s` en Spring.** Crear `application-k8s.yml` (o dejar que todo entre por
   variables de entorno del ConfigMap/Secret).
5. **Health probes.** Exponer `health.probes.enabled: true` en Actuator (grupos
   `readiness`/`liveness`).

## Secretos

`base/secret.example.yaml` **no se aplica**. Crear el Secret real así (ejemplo con Sealed Secrets):

```bash
kubectl create secret generic backend-secrets -n agriaudit-dev \
  --from-literal=SPRING_DATASOURCE_URL='jdbc:postgresql://<host>:5432/agriaudit' \
  --from-literal=SPRING_DATASOURCE_USERNAME='agriaudit' \
  --from-literal=SPRING_DATASOURCE_PASSWORD='<...>' \
  --from-literal=JWT_SECRET='<largo y aleatorio>' \
  --from-literal=AGRIAUDIT_STORAGE_S3_ENDPOINT='<...>' \
  --from-literal=AGRIAUDIT_STORAGE_S3_BUCKET='agriaudit-evidence' \
  --from-literal=AGRIAUDIT_STORAGE_S3_ACCESS_KEY='<...>' \
  --from-literal=AGRIAUDIT_STORAGE_S3_SECRET_KEY='<...>' \
  --dry-run=client -o yaml | kubeseal --format yaml > overlays/dev/backend-sealedsecret.yaml
```

Luego añadir `backend-sealedsecret.yaml` a `resources:` del overlay.

## Aplicar a mano (para probar)

```bash
kustomize build deploy/k8s/overlays/dev | kubectl apply -n agriaudit-dev -f -
kubectl -n agriaudit-dev rollout status deploy/backend
```

## Despliegue automático (CD)

`.github/workflows/cd.yml` + `.github/actions/kustomize-deploy` hacen **push-based deploy**:
CI construye y publica la imagen con tag `sha-<commit>`; CD hace
`kustomize edit set image ...:<sha>` y `kubectl apply` por entorno, con el entorno `prod`
protegido por aprobación manual (*required reviewers* en GitHub Environments).

Secretos que necesita el workflow (en *Settings → Secrets → Actions*, por entorno):
`KUBECONFIG_DEV`, `KUBECONFIG_STAGING`, `KUBECONFIG_PROD` — cada uno un kubeconfig con un
**ServiceAccount de despliegue con permisos mínimos** en su namespace (no cluster-admin).

Reemplazar `OWNER` por la organización/usuario de GitHub en `base/kustomization.yaml`.

### Alternativa GitOps (recomendada a medio plazo)

Con Argo CD o Flux, CD no toca el clúster: solo hace commit del nuevo tag a una rama de
entorno y el agente *pull-based* lo aplica. Ventaja: ninguna credencial del clúster vive en
GitHub, y el estado real del clúster queda reflejado en Git.
