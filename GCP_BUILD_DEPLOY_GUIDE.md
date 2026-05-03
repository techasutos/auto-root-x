# AutoRoot-X GCP Build and Deploy Guide

This guide covers:
- Building backend and UI containers
- Pushing images to Google Artifact Registry
- Provisioning GKE with Terraform
- Deploying with Helm
- Setting backend/UI URLs and integration
- Accessing the app after deployment
- What is feasible on the $300 GCP free credit

## 1. Prerequisites

- GCP project with billing enabled
- `gcloud`, `kubectl`, `helm`, `terraform` installed
- Docker installed
- GitHub repo secrets configured (for CI):
  - `GCP_PROJECT_ID`
  - `GCP_SA_KEY` (JSON key for deployment SA)

Recommended APIs (Terraform enables these):
- `container.googleapis.com`
- `compute.googleapis.com`
- `logging.googleapis.com`
- `monitoring.googleapis.com`
- `aiplatform.googleapis.com`
- `artifactregistry.googleapis.com`
- `iam.googleapis.com`

## 2. Infrastructure Provisioning (Terraform)

From [infra/main.tf](infra/main.tf):

```bash
cd infra
terraform init
terraform plan -var="project_id=<YOUR_PROJECT_ID>"
terraform apply -var="project_id=<YOUR_PROJECT_ID>"
```

What Terraform now provisions:
- VPC + subnet
- GKE cluster with Workload Identity
- Node pool with:
  - secure boot + integrity monitoring
  - legacy metadata endpoint disabled
  - spot nodes (default true)
  - scoped permissions (no broad cloud-platform scope)
- dedicated node service account with minimum required roles

## 3. Artifact Registry Setup

The GitHub workflow can create repo automatically, but manual option:

```bash
gcloud artifacts repositories create autorootx \
  --repository-format=docker \
  --location=us-central1 \
  --description="AutoRootX images"

gcloud auth configure-docker us-central1-docker.pkg.dev
```

## 4. Build and Push Images (Manual)

### Backend (OpenJDK image)

Dockerfile used: [Dockerfile/backend/dockerfile](Dockerfile/backend/dockerfile)

```bash
docker build -f Dockerfile/backend/dockerfile \
  -t us-central1-docker.pkg.dev/<PROJECT_ID>/autorootx/backend:<TAG> \
  autoroot-x

docker push us-central1-docker.pkg.dev/<PROJECT_ID>/autorootx/backend:<TAG>
```

### Frontend

Dockerfile used: [Dockerfile/frontend/dockerfile](Dockerfile/frontend/dockerfile)

```bash
docker build -f Dockerfile/frontend/dockerfile \
  -t us-central1-docker.pkg.dev/<PROJECT_ID>/autorootx/frontend:<TAG> \
  autorootx-ui

docker push us-central1-docker.pkg.dev/<PROJECT_ID>/autorootx/frontend:<TAG>
```

## 5. Helm Configuration (All Values Driven)

Update [helm/autorootx/values.yaml](helm/autorootx/values.yaml):
- `backend.image.repository`
- `backend.image.tag`
- `frontend.image.repository`
- `frontend.image.tag`
- `ingress.host`
- backend runtime config block under `backend.config`

ConfigMap template source: [helm/autorootx/templates/backend-configmap.yaml](helm/autorootx/templates/backend-configmap.yaml)

### Security Hardening Included

- Pod security context + container security context
- Optional HPA: [helm/autorootx/templates/hpa.yaml](helm/autorootx/templates/hpa.yaml)
- Optional NetworkPolicy: [helm/autorootx/templates/networkpolicy.yaml](helm/autorootx/templates/networkpolicy.yaml)
- Optional TLS in ingress: [helm/autorootx/templates/ingress.yaml](helm/autorootx/templates/ingress.yaml)

## 6. Deploy to GKE with Helm

```bash
gcloud container clusters get-credentials autorootx-cluster --region us-central1 --project <PROJECT_ID>
kubectl create namespace autorootx --dry-run=client -o yaml | kubectl apply -f -

helm upgrade --install autorootx helm/autorootx \
  --namespace autorootx \
  --set backend.image.repository=us-central1-docker.pkg.dev/<PROJECT_ID>/autorootx/backend \
  --set backend.image.tag=<TAG> \
  --set frontend.image.repository=us-central1-docker.pkg.dev/<PROJECT_ID>/autorootx/frontend \
  --set frontend.image.tag=<TAG> \
  --set ingress.host=<YOUR_DOMAIN>
```

## 7. Backend/UI URL Integration

### Ingress-based integration (recommended)

Single domain:
- Frontend: `https://<YOUR_DOMAIN>/`
- Backend API: `https://<YOUR_DOMAIN>/api`

Ingress routes `/api` to backend service and `/` to frontend service:
- [helm/autorootx/templates/ingress.yaml](helm/autorootx/templates/ingress.yaml)

### CORS configuration

Set in values:

```yaml
backend:
  config:
    appCorsAllowedOrigins: "https://<YOUR_DOMAIN>"
```

This is mapped into env var `APP_CORS_ALLOWED_ORIGINS` via ConfigMap.

## 8. Accessing the Application After Deploy

### Check resources

```bash
kubectl get pods -n autorootx
kubectl get svc -n autorootx
kubectl get ingress -n autorootx
```

### If using external DNS

Point DNS A record to ingress external IP:

```bash
kubectl get ingress -n autorootx
```

Then open:
- `https://<YOUR_DOMAIN>/` (UI)
- `https://<YOUR_DOMAIN>/api/health` (backend health)

### Quick local fallback (without DNS)

```bash
kubectl port-forward -n autorootx svc/autorootx-autorootx-frontend 8081:80
```

Open `http://localhost:8081`.

## 9. GitHub Actions CI/CD

Workflow file: [.github/workflows/deploy.yml](.github/workflows/deploy.yml)

On push to `main`, it now:
1. Authenticates to GCP
2. Ensures Artifact Registry repo exists
3. Builds backend and frontend images
4. Pushes to Artifact Registry with `${GITHUB_SHA}` tag
5. Gets GKE credentials
6. Deploys Helm chart with new image tags

## 10. ServiceNow and Vertex AI Notes

- ServiceNow integration supports mock mode by default.
- For real ServiceNow, set:
  - `servicenow.mock-mode=false`
  - `servicenow.instance-url`
  - `servicenow.username`
  - `servicenow.password` (via Kubernetes secret)
- Vertex AI requires:
  - `GCP_PROJECT`
  - `GCP_REGION`
  - `VERTEX_MODEL`
  - Workload identity/permissions for runtime SA

## 11. What Is Possible on $300 Free Credit

## Possible

- 1 small non-production GKE environment (backend + frontend)
- Artifact Registry image storage and CI pushes
- Ingress and HTTPS (if configured with managed cert)
- Moderate development/testing usage
- Vertex AI testing with controlled request volume

## Risky / likely to consume credit quickly

- Continuous load testing on GKE
- Large node types (`e2-standard-4+`) or many replicas
- High-volume Vertex AI calls
- Long retention of large logs/metrics and many container images

## Not realistic on $300 credit for long duration

- Production-grade HA across multiple regions/zones with large autoscaling
- Sustained heavy AI inference usage
- Enterprise-scale observability retention at high throughput

## Cost Controls Recommended

- Keep node type at `e2-small` and replicas at 1 unless needed
- Keep `use_spot_nodes=true` (default in Terraform)
- Set image lifecycle policy on Artifact Registry
- Use budget alerts in GCP Billing
- Cap Vertex AI usage and add request throttling

## 12. Recommended Minimum Production Hardenings (Next Step)

- Move sensitive env values to Kubernetes Secret and External Secret Manager
- Enable TLS with managed certificate and strict ingress annotations
- Add PodDisruptionBudget and anti-affinity for backend
- Add structured backup/restore plan for stateful dependencies (if added later)
- Add policy checks (OPA/Gatekeeper) for image/tag and security context enforcement
