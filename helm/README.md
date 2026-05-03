# AutoRoot X Helm Deployment

This directory contains a Helm chart for deploying backend and frontend as separate workloads.

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

## 3. Optional values override

Create a values file for environment-specific tuning and install with:

```powershell
helm upgrade --install autorootx .\helm\autorootx -f values-prod.yaml
```
