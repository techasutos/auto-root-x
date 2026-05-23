# AutoRoot X Helm Deployment

This directory contains a Helm chart for deploying the backend and frontend as
separate workloads behind one URL.

## 1. Build and push images

Backend:

```powershell
cd d:\projects\auto-root-x\autoroot-x
docker build -t gcr.io/<PROJECT_ID>/backend:latest -f ..\Dockerfile\backend\dockerfile .
docker push gcr.io/<PROJECT_ID>/backend:latest
```

Frontend:

```powershell
cd d:\projects\auto-root-x\autorootx-ui
docker build -t gcr.io/<PROJECT_ID>/frontend:latest -f ..\Dockerfile\frontend\dockerfile .
docker push gcr.io/<PROJECT_ID>/frontend:latest
```

## 2. Install chart

```powershell
cd d:\projects\auto-root-x
helm upgrade --install autorootx .\helm\autorootx --set backend.image.repository=gcr.io/<PROJECT_ID>/backend --set frontend.image.repository=gcr.io/<PROJECT_ID>/frontend
```

## 3. One URL for UI and API

The chart exposes a single Ingress:

```text
/api -> backend service
/    -> frontend service
```

The frontend also proxies `/api` to the backend service through Nginx, so Cloud
Shell web preview works when you port-forward only the frontend service:

```bash
kubectl -n autorootx port-forward svc/autorootx-autorootx-frontend 8080:80
```

## 4. Static IP

Create or reuse a global static IP:

```bash
gcloud compute addresses create autorootx-ip --global
gcloud compute addresses describe autorootx-ip --global --format="value(address)"
```

Deploy with:

```bash
helm upgrade --install autorootx ./helm/autorootx \
  --namespace autorootx \
  --set-string 'ingress.annotations.kubernetes\.io/ingress\.global-static-ip-name=autorootx-ip' \
  --set ingress.host=""
```

Open:

```text
http://STATIC_IP/
http://STATIC_IP/api/health
```

## 5. Trivy sidecar image scanning

The backend pod can include a Trivy scanner sidecar. The sidecar scans an image
on a schedule and writes JSON to a shared `emptyDir` volume:

```text
/var/run/autorootx/trivy/report.json
```

The backend reads that file through `TRIVY_REPORT_PATH` and uses it in the
Image scanner and Agent Trivy evidence source.

By default, the sidecar scans the backend image configured in Helm:

```yaml
backend:
  trivy:
    enabled: true
    targetImage: ""
    reportPath: /var/run/autorootx/trivy/report.json
```

To scan a different image:

```yaml
backend:
  trivy:
    targetImage: us-central1-docker.pkg.dev/YOUR_PROJECT/autorootx/backend:latest
```

For private registries, mount a Docker config secret:

```yaml
backend:
  trivy:
    dockerConfig:
      enabled: true
      secretName: regcred
```
