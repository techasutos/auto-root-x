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

<<<<<<< Updated upstream
## 4. Trivy sidecar image scanning

The backend pod can include a Trivy scanner sidecar. The sidecar scans an image
on a schedule and writes JSON to a shared `emptyDir` volume:

```text
/var/run/autorootx/trivy/report.json
```

The backend reads that file through `TRIVY_REPORT_PATH` and uses it in the
Image scanner and Agent Trivy evidence source. This avoids assuming that plain
`trivy server` exposes a custom `/v1/scan` HTTP API.

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
=======
## 4. Optional Trivy sidecar report

If Trivy runs as a sidecar in the backend pod, write JSON output to a shared
volume and point the backend at that file:

```yaml
backend:
  config:
    trivyReportPath: /var/run/autorootx/trivy/report.json
  extraVolumes:
    - name: trivy-report
      emptyDir: {}
  extraVolumeMounts:
    - name: trivy-report
      mountPath: /var/run/autorootx/trivy
  extraContainers:
    - name: trivy
      image: aquasec/trivy:latest
      command: ["/bin/sh", "-c"]
      args:
        - >
          while true; do
            trivy image --format json --output /var/run/autorootx/trivy/report.json "$TARGET_IMAGE";
            sleep 3600;
          done
      env:
        - name: TARGET_IMAGE
          value: us-central1-docker.pkg.dev/YOUR_PROJECT/autorootx/backend:latest
      volumeMounts:
        - name: trivy-report
          mountPath: /var/run/autorootx/trivy
```

The backend can also accept a Trivy JSON payload directly with
`analyzerId=IMAGE` or `AUTO` using the `trivyReport` field.
>>>>>>> Stashed changes
