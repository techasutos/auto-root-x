# AutoRoot-X Workflow-Only GCP Runbook

This runbook is the end-to-end manual-triggered path for AutoRoot-X.

Everything in this repository is now designed to run from GitHub Actions with service-account-based authentication only. No workflow is triggered by push, commit, or merge.

## 1. Workflow Inventory

- [Provision GKE Infrastructure](d:/projects/auto-root-x/.github/workflows/infra.yml): creates and updates GCP infrastructure with Terraform.
- [Seed Secret Manager Secrets](d:/projects/auto-root-x/.github/workflows/seed-gsm-secrets.yml): creates runtime secrets in Google Secret Manager from a temporary GitHub secret.
- [Hydrate Runtime Secrets](d:/projects/auto-root-x/.github/workflows/hydrate-secrets.yml): copies Google Secret Manager values into Kubernetes secrets.
- [Deploy to GKE](d:/projects/auto-root-x/.github/workflows/deploy.yml): builds backend and frontend images, pushes them to Artifact Registry, and deploys Helm.
- [Cleanup GCP Environment](d:/projects/auto-root-x/.github/workflows/cleanup.yml): destroys the environment and optionally deletes runtime secrets and Terraform state.

## 2. One-Time Bootstrap Requirement

You still need one bootstrap Google identity with enough privilege to do the first setup for GitHub Actions. After that, the repo workflows handle the rest.

Recommended bootstrap model:

- Use GitHub Workload Identity Federation (WIF) exclusively.
- Do **not** use `GCP_SA_KEY` unless WIF cannot be used in your environment.

Required GitHub repository secrets:

- `GCP_PROJECT_ID`: `auto-root-x-495307`
- `GCP_WIF_PROVIDER`: full Workload Identity Provider resource name
- `GCP_WIF_SERVICE_ACCOUNT`: deployer Google service account email used by GitHub Actions

Temporary GitHub secret used only to seed Secret Manager:

- `AUTOROOTX_SERVICENOW_PASSWORD`: the raw ServiceNow password value

## 3. What Terraform Creates

Terraform sources:

- [infra/main.tf](d:/projects/auto-root-x/infra/main.tf)
- [infra/variables.tf](d:/projects/auto-root-x/infra/variables.tf)
- [infra/outputs.tf](d:/projects/auto-root-x/infra/outputs.tf)
- [infra/versions.tf](d:/projects/auto-root-x/infra/versions.tf)

Terraform provisions:

- Required APIs for GKE, Artifact Registry, Vertex AI, Secret Manager, IAM Credentials, and STS
- VPC and subnet
- GKE cluster with Workload Identity enabled
- GKE node pool and node service account
- Backend workload Google service account `autorootx-backend`
- IAM binding from Kubernetes service account `autorootx-backend` in namespace `autorootx` to the backend Google service account
- GCS Terraform state backend bucket created by the infra workflow before `terraform init`

## 4. Runtime Secret Flow

Runtime secret flow is:

1. Temporary GitHub secret
2. Google Secret Manager secret
3. Kubernetes secret `autorootx-secrets`
4. Backend pod environment variable `SERVICENOW_PASS`

Helm wiring for runtime secrets lives in:

- [helm/autorootx/values.yaml](d:/projects/auto-root-x/helm/autorootx/values.yaml)
- [helm/autorootx/templates/backend-deployment.yaml](d:/projects/auto-root-x/helm/autorootx/templates/backend-deployment.yaml)
- [helm/autorootx/templates/backend-serviceaccount.yaml](d:/projects/auto-root-x/helm/autorootx/templates/backend-serviceaccount.yaml)

## 5. Step-by-Step Deployment Procedure

### Step 0: Bootstrap the GitHub deployer identity and WIF provider

If you only have the project ID right now, then you are still missing the one-time bootstrap setup.

You need one Google identity with enough permission to create:

- a deployer service account for GitHub Actions
- a Workload Identity Pool
- a Workload Identity Provider for your GitHub repository
- IAM bindings that let the GitHub repository impersonate that deployer service account and mint access tokens for it

You cannot create these from the repo workflows until one privileged Google identity already exists, because the workflows need that identity to authenticate in the first place.

Use the following commands one time from any environment that already has privileged `gcloud` access.

Replace these placeholders before running:

- `<PROJECT_NUMBER>`: numeric project number for `auto-root-x-495307`
- `<GITHUB_ORG>`: your GitHub organization or username
- `<GITHUB_REPO>`: your repository name

```bash
PROJECT_ID="auto-root-x-495307"
PROJECT_NUMBER="<PROJECT_NUMBER>"
POOL_ID="github-pool"
PROVIDER_ID="github-provider"
DEPLOYER_SA_ID="github-deployer"
GITHUB_ORG="<GITHUB_ORG>"
GITHUB_REPO="<GITHUB_REPO>"

gcloud iam service-accounts create ${DEPLOYER_SA_ID} \
	--project=${PROJECT_ID} \
	--display-name="GitHub Actions Deployer"

gcloud iam workload-identity-pools create ${POOL_ID} \
	--project=${PROJECT_ID} \
	--location=global \
	--display-name="GitHub Actions Pool"

gcloud iam workload-identity-pools providers create-oidc ${PROVIDER_ID} \
	--project=${PROJECT_ID} \
	--location=global \
	--workload-identity-pool=${POOL_ID} \
	--display-name="GitHub Provider" \
	--issuer-uri="https://token.actions.githubusercontent.com" \
	--attribute-mapping="google.subject=assertion.sub,attribute.actor=assertion.actor,attribute.repository=assertion.repository,attribute.repository_owner=assertion.repository_owner" \
	--attribute-condition="assertion.repository=='${GITHUB_ORG}/${GITHUB_REPO}'"

PRINCIPAL_SET="principalSet://iam.googleapis.com/projects/${PROJECT_NUMBER}/locations/global/workloadIdentityPools/${POOL_ID}/attribute.repository/${GITHUB_ORG}/${GITHUB_REPO}"

gcloud iam service-accounts add-iam-policy-binding \
	${DEPLOYER_SA_ID}@${PROJECT_ID}.iam.gserviceaccount.com \
	--project=${PROJECT_ID} \
	--role="roles/iam.workloadIdentityUser" \
	--member="${PRINCIPAL_SET}"

gcloud iam service-accounts add-iam-policy-binding \
	${DEPLOYER_SA_ID}@${PROJECT_ID}.iam.gserviceaccount.com \
	--project=${PROJECT_ID} \
	--role="roles/iam.serviceAccountTokenCreator" \
	--member="${PRINCIPAL_SET}"
```

Those two `add-iam-policy-binding` commands are required service-account IAM bindings on `github-deployer`. Project roles alone are not enough for GitHub Actions WIF.

Grant the deployer service account the roles needed by the workflows:

```bash
for role in \
	roles/container.admin \
	roles/compute.networkAdmin \
	roles/iam.serviceAccountAdmin \
	roles/iam.serviceAccountUser \
	roles/iam.serviceAccountTokenCreator \
	roles/serviceusage.serviceUsageAdmin \
	roles/artifactregistry.admin \
	roles/storage.admin \
	roles/secretmanager.admin \
	roles/resourcemanager.projectIamAdmin; do
	gcloud projects add-iam-policy-binding ${PROJECT_ID} \
		--member="serviceAccount:${DEPLOYER_SA_ID}@${PROJECT_ID}.iam.gserviceaccount.com" \
		--role="${role}"
done
```

After the commands above, set these GitHub secrets:

- `GCP_PROJECT_ID`: `auto-root-x-495307`
- `GCP_WIF_PROVIDER`: `projects/<PROJECT_NUMBER>/locations/global/workloadIdentityPools/github-pool/providers/github-provider`
- `GCP_WIF_SERVICE_ACCOUNT`: `github-deployer@auto-root-x-495307.iam.gserviceaccount.com`

If you do not have access to `gcloud`, do the same bootstrap manually in the Google Cloud console:

1. Open Google Cloud console for project `auto-root-x-495307`.
2. Go to `IAM & Admin` -> `Service Accounts`.
3. Create a service account named `github-deployer`.
4. Grant it these project roles:
	- `Kubernetes Engine Admin`
	- `Compute Network Admin`
	- `Service Account Admin`
	- `Service Account User`
	- `Service Usage Admin`
	- `Artifact Registry Admin`
	- `Storage Admin`
	- `Secret Manager Admin`
	- `Project IAM Admin`

5. Go to `IAM & Admin` -> `Workload Identity Federation`.
6. Create a workload identity pool named `github-pool` in location `global`.
7. Inside that pool, create an OIDC provider named `github-provider`.
8. Use issuer URL `https://token.actions.githubusercontent.com`.
9. Configure attribute mapping with these values:
	- `google.subject` -> `assertion.sub`
	- `attribute.actor` -> `assertion.actor`
	- `attribute.repository` -> `assertion.repository`
	- `attribute.repository_owner` -> `assertion.repository_owner`
10. Add an attribute condition restricting access to your repo:
	 - `assertion.repository=='<GITHUB_ORG>/<GITHUB_REPO>'`
11. Build this principal string for your repository:
	- `principalSet://iam.googleapis.com/projects/<PROJECT_NUMBER>/locations/global/workloadIdentityPools/github-pool/attribute.repository/<GITHUB_ORG>/<GITHUB_REPO>`
12. Open the `github-deployer` service account.
13. In that service account's permissions or principal access section, add the principal string from step 11 with role `Workload Identity User`.
14. Add another entry for the same principal string with role `Service Account Token Creator`.
15. Copy the provider resource name from the provider details page.
16. Copy the deployer service account email from the service account details page.

After the manual console setup, set these GitHub secrets:

- `GCP_PROJECT_ID`: `auto-root-x-495307`
- `GCP_WIF_PROVIDER`: the copied provider resource name
- `GCP_WIF_SERVICE_ACCOUNT`: the copied service account email, usually `github-deployer@auto-root-x-495307.iam.gserviceaccount.com`

### Step 1: Set GitHub repository secrets

Add these secrets in GitHub Actions secrets before running any workflow:

- `GCP_PROJECT_ID`
- `GCP_WIF_PROVIDER`
- `GCP_WIF_SERVICE_ACCOUNT`

How to add secrets to your GitHub repository:

1. Go to your repository on GitHub: `https://github.com/<GITHUB_ORG>/<GITHUB_REPO>`
2. Click on `Settings` tab (top right).
3. In the left sidebar, click `Secrets and variables` -> `Actions`.
4. Click the green `New repository secret` button.
5. Add each secret one at a time:
   - **Name:** `GCP_PROJECT_ID`
		 **Value:** `auto-root-x-495307`
   - **Name:** `GCP_WIF_PROVIDER`
     **Value:** the full provider resource name you copied, e.g., `projects/123456789/locations/global/workloadIdentityPools/github-pool/providers/github-provider`
   - **Name:** `GCP_WIF_SERVICE_ACCOUNT`
		 **Value:** the deployer service account email, e.g., `github-deployer@auto-root-x-495307.iam.gserviceaccount.com`
6. After adding all three, you should see them listed in the `Secrets` section.

Where these values come from:

- `GCP_PROJECT_ID`: your Google Cloud project ID. In your case this is `auto-root-x-495307`.
- `GCP_WIF_PROVIDER`: the full resource name of the Google Workload Identity Federation provider that trusts your GitHub repository. Format example: `projects/123456789/locations/global/workloadIdentityPools/github-pool/providers/github-provider`.
Important distinction:

- The repo does have a workflow that creates workload service accounts through Terraform, including `autorootx-backend` and the GKE node service account.
- The repo does not create the initial GitHub deployer service account or the initial WIF provider before authentication, because one of those identities is required for GitHub Actions to authenticate to GCP in the first place.
- In other words, the first deployer identity is a bootstrap prerequisite. After that, the workflows can create and manage the application-side service accounts.

For initial Secret Manager seeding, add:

- `AUTOROOTX_SERVICENOW_PASSWORD`

### Step 2: Create the runtime secret in Google Secret Manager

Before running this step, add the ServiceNow password to GitHub secrets:

1. Go to your repository on GitHub → **Settings** → **Secrets and variables** → **Actions**
2. Click **New repository secret**
3. Add the secret:
   - **Name:** `AUTOROOTX_SERVICENOW_PASSWORD`
   - **Value:** your actual ServiceNow password

After adding the secret, run the manual workflow [Seed Secret Manager Secrets](d:/projects/auto-root-x/.github/workflows/seed-gsm-secrets.yml).

This workflow:

- Authenticates to GCP using Workload Identity Federation
- Creates `autorootx-servicenow-password` in Google Secret Manager if it does not exist
- Adds a new latest version of that secret using the value from `AUTOROOTX_SERVICENOW_PASSWORD`

After this workflow succeeds:
- The ServiceNow password is now safely stored in Google Secret Manager
- You can remove `AUTOROOTX_SERVICENOW_PASSWORD` from GitHub secrets if you do not want to keep it there

### Step 3: Create infrastructure and service accounts

Run the manual workflow [Provision GKE Infrastructure](d:/projects/auto-root-x/.github/workflows/infra.yml).

This workflow:

- Creates the Terraform state bucket `${GCP_PROJECT_ID}-tfstate` if it does not exist
- Runs `terraform init`, `plan`, and `apply`
- Creates GKE, network, node pool, backend workload Google service account, and Workload Identity binding

### Step 4: Hydrate Google Secret Manager values into Kubernetes

Run the manual workflow [Hydrate Runtime Secrets](d:/projects/auto-root-x/.github/workflows/hydrate-secrets.yml).

This workflow:

- Connects to the GKE cluster
- Ensures namespace `autorootx` exists
- Reads the latest version of `autorootx-servicenow-password` from Secret Manager
- Creates or updates Kubernetes secret `autorootx-secrets`

### Step 5: Deploy backend and frontend

Run the manual workflow [Deploy to GKE](d:/projects/auto-root-x/.github/workflows/deploy.yml).

This workflow:

- Builds backend image from [Dockerfile/backend/dockerfile](d:/projects/auto-root-x/Dockerfile/backend/dockerfile)
- Builds frontend image from [Dockerfile/frontend/dockerfile](d:/projects/auto-root-x/Dockerfile/frontend/dockerfile)
- Ensures Artifact Registry repository `autorootx` exists
- Pushes both images tagged with the workflow commit SHA
- Verifies Kubernetes secret `autorootx-secrets` exists before Helm deploy
- Deploys backend and frontend with Helm
- Sets the backend Kubernetes service account to `autorootx-backend`
- Annotates that Kubernetes service account for GKE Workload Identity

## 6. Accessing the UI

### Option A: Access UI via port-forward (fastest)

Use this when you want immediate access without waiting for DNS.

1. Ensure your `kubectl` context is connected to the cluster:

```bash
gcloud config set project auto-root-x-495307
gcloud container clusters get-credentials autorootx-cluster --region us-central1 --project auto-root-x-495307
kubectl get nodes
```

2. Find the frontend service name:

```bash
kubectl -n autorootx get svc -l app.kubernetes.io/component=frontend
```

3. Start port-forward to the frontend service (replace service name if different):

```bash
kubectl -n autorootx port-forward svc/autorootx-autorootx-frontend 8080:80
```

4. Open the UI:
	- If running from your own machine terminal: `http://localhost:8080`
	- If running from Cloud Shell: use **Web Preview** -> **Preview on port 8080** and open the generated `*.cloudshell.dev` URL

5. Keep the terminal window from step 3 running while you use the UI.

### Option B: Access UI via Ingress and DNS (recommended for persistent access)

Ingress template:

- [helm/autorootx/templates/ingress.yaml](d:/projects/auto-root-x/helm/autorootx/templates/ingress.yaml)

If ingress is enabled and a host is configured, the routes are:

- Frontend: `https://<host>/`
- Backend API: `https://<host>/api`

The workflow deploys both services into the same Helm release.

#### DNS Configuration Steps

1. Verify the ingress resource exists:

```bash
kubectl -n autorootx get ingress
```

2. Wait for the ingress external IP to appear in the `ADDRESS` column.

3. Choose your domain and host (for example, `app.yourdomain.com`).

4. Update Helm ingress host if needed (default is `autorootx.example.com`):
	- File: [helm/autorootx/values.yaml](d:/projects/auto-root-x/helm/autorootx/values.yaml)
	- Set `ingress.host` to your real host
	- Re-run deploy workflow

5. Create a DNS `A` record at your DNS provider:
	- Record type: `A`
	- Name/Host: your ingress host (for example, `app`)
	- Value/Target: ingress external IP from step 2
	- TTL: 300 seconds (or provider default)

6. Wait for DNS propagation and validate:

```bash
nslookup app.yourdomain.com
```

7. Open your UI URL:
	- `http://app.yourdomain.com/` (if HTTP is enabled)
	- `https://app.yourdomain.com/` (if TLS is configured)

### Verify the deployment

Check the following in the GitHub Actions logs or by running a follow-up operational workflow if you later add one:

- backend pods are healthy
- frontend pods are healthy
- ingress or frontend service has an external address

### Fallback path without DNS

If DNS is not ready yet, use the port-forward method in Option A.

## 7. Recommended Workflow Order

Run these workflows in this exact order:

1. [Seed Secret Manager Secrets](d:/projects/auto-root-x/.github/workflows/seed-gsm-secrets.yml)
2. [Provision GKE Infrastructure](d:/projects/auto-root-x/.github/workflows/infra.yml)
3. [Hydrate Runtime Secrets](d:/projects/auto-root-x/.github/workflows/hydrate-secrets.yml)
4. [Deploy to GKE](d:/projects/auto-root-x/.github/workflows/deploy.yml)

## 8. Cleanup Procedure

Run the manual workflow [Cleanup GCP Environment](d:/projects/auto-root-x/.github/workflows/cleanup.yml).

Inputs:

- `confirmation`: must be `DESTROY`
- `delete_secret_manager_secrets`: set `true` if you also want runtime GSM secrets removed
- `delete_tf_state_bucket`: set `true` if you also want the Terraform backend bucket removed after destroy

This workflow performs:

- best-effort Helm uninstall
- namespace and Kubernetes secret cleanup
- Terraform destroy using the persisted GCS backend state
- Artifact Registry repository deletion
- optional Secret Manager deletion
- optional Terraform state bucket deletion

### Resource Cleanup Check

After the cleanup workflow finishes, verify what is actually gone instead of assuming the destroy fully completed.

Set your project first:

```bash
gcloud config set project auto-root-x-495307
```

Check the GKE cluster is gone:

```bash
gcloud container clusters list --region us-central1
```

Expected result:

- `autorootx-cluster` should not be listed

Check for leftover Kubernetes namespace only if the cluster still exists or you still have valid cluster credentials:

```bash
kubectl get ns autorootx
```

Expected result:

- `autorootx` should be missing

Check Artifact Registry cleanup:

```bash
gcloud artifacts repositories list --location=us-central1
```

Expected result:

- repository `autorootx` should not be listed

Check Secret Manager cleanup if you ran the workflow with `delete_secret_manager_secrets=true`:

```bash
gcloud secrets list
```

Expected result:

- `autorootx-servicenow-password` should be missing if secret deletion was requested
- if you did not request secret deletion, this secret may still exist by design

Check Terraform state bucket cleanup if you ran the workflow with `delete_tf_state_bucket=true`:

```bash
gcloud storage buckets list
```

Expected result:

- bucket `gs://auto-root-x-495307-tfstate` should be missing if bucket deletion was requested
- if you did not request bucket deletion, this bucket may still exist by design

Check for leftover VPC networking resources:

```bash
gcloud compute networks list
gcloud compute addresses list --regions=us-central1
```

Expected result:

- the AutoRoot-X VPC and any reserved regional addresses created for this environment should be gone

Check service accounts that may remain:

```bash
gcloud iam service-accounts list --format="value(email)"
```

Expected result:

- `autorootx-backend@auto-root-x-495307.iam.gserviceaccount.com` should be gone if Terraform destroy completed
- `github-deployer@auto-root-x-495307.iam.gserviceaccount.com` will usually remain because it is part of the one-time bootstrap setup, not the application runtime stack

If any of the resources above still exist, review the [Cleanup GCP Environment](d:/projects/auto-root-x/.github/workflows/cleanup.yml) workflow logs to see which step failed or was skipped.

## 9. Troubleshooting

### Error: "The given credential is rejected by the attribute condition"

**Symptoms:**
```
Error: google-github-actions/auth failed with: failed to generate Google Cloud federated token...
{"error":"unauthorized_client","error_description":"The given credential is rejected by the attribute condition."}
```

**Root Cause:**

Your Workload Identity Federation provider's attribute condition does not match your actual GitHub repository path. The condition is case-sensitive and must match exactly.

**Step 1: Verify Your Actual GitHub Repository**

1. Go to your repository on GitHub (e.g., `https://github.com/MY_USERNAME/MY_REPO` for personal repos or `https://github.com/MY_ORG/MY_REPO` for org repos).
2. Extract the owner and repository name exactly as they appear in the URL:
   - For personal repos: owner is your GitHub username (e.g., `my-username`)
   - For org repos: owner is the organization name
   - Repository: `MY_REPO`
3. The full path GitHub will send is: `OWNER/REPOSITORY` (case-sensitive), e.g., `my-username/auto-root-x`.

**Step 2: Check Your Current WIF Provider Condition**

Run this command to see what condition is currently set:

```bash
PROJECT_ID="auto-root-x-495307"
gcloud iam workload-identity-pools providers describe github-provider \
  --project=${PROJECT_ID} \
  --location=global \
  --workload-identity-pool=github-pool \
  --format='value(attributeCondition)'
```

Compare the output with your actual GitHub org and repo. Look for:

```
assertion.repository=='YOUR_ORG/YOUR_REPO'
```

If the org or repo in the condition does not match **exactly** (including case), that's the problem.

**Step 3: Fix the Condition**

If the condition is wrong, update it with the correct owner and repo:

```bash
PROJECT_ID="auto-root-x-495307"
GITHUB_OWNER="YOUR_USERNAME_OR_ORG"  # For personal repos: use your GitHub username
GITHUB_REPO="auto-root-x"  # Replace if your repo has a different name

gcloud iam workload-identity-pools providers update github-provider \
  --project=${PROJECT_ID} \
  --location=global \
  --workload-identity-pool=github-pool \
  --attribute-condition="assertion.repository=='${GITHUB_OWNER}/${GITHUB_REPO}'"
```

Example for a personal repository:
```bash
PROJECT_ID="auto-root-x-495307"
GITHUB_OWNER="my-username"
GITHUB_REPO="auto-root-x"

gcloud iam workload-identity-pools providers update github-provider \
  --project=${PROJECT_ID} \
  --location=global \
  --workload-identity-pool=github-pool \
  --attribute-condition="assertion.repository=='${GITHUB_OWNER}/${GITHUB_REPO}'"
```

**Step 4: Retry Your Workflow**

After updating the condition, re-run your failing workflow. The attribute check should now pass.

---

### Error: "Permission 'iam.serviceAccounts.getAccessToken' denied" (even with roles)

**Symptoms:**
```
Successfully authenticated
Error: google-github-actions/setup-gcloud failed with: failed to execute command `gcloud --quiet config set project ***`: 
ERROR: (gcloud.config.set) There was a problem refreshing your current auth tokens: 
('Unable to acquire impersonated credentials', '... Permission 'iam.serviceAccounts.getAccessToken' denied ...')
```

**Root Cause:**

Authentication via WIF succeeded, but the `setup-gcloud` step is failing because it cannot impersonate the service account to get tokens for project configuration. This typically means:

- The GitHub repository principal is missing a service-account IAM binding on `github-deployer`
- The `roles/iam.serviceAccountTokenCreator` binding is missing on that service account
- Project roles were added, but the service-account policy bindings for the repository principalSet were not

**Step 1: Verify the Service Account IAM Policy on the Deployer**

Check the IAM policy on the deployer service account itself:

```bash
PROJECT_ID="auto-root-x-495307"
DEPLOYER_SA="github-deployer@${PROJECT_ID}.iam.gserviceaccount.com"

gcloud iam service-accounts get-iam-policy ${DEPLOYER_SA} \
  --project=${PROJECT_ID}
```

Look for two bindings where the member is your repository principalSet:

- `roles/iam.workloadIdentityUser`
- `roles/iam.serviceAccountTokenCreator`

The member should look like this:

```text
principalSet://iam.googleapis.com/projects/<PROJECT_NUMBER>/locations/global/workloadIdentityPools/github-pool/attribute.repository/<GITHUB_ORG>/<GITHUB_REPO>
```

**Step 2: Add the Missing Service Account IAM Bindings**

If either binding is missing, add both explicitly:

```bash
PROJECT_ID="auto-root-x-495307"
PROJECT_NUMBER="<PROJECT_NUMBER>"
DEPLOYER_SA="github-deployer@${PROJECT_ID}.iam.gserviceaccount.com"
GITHUB_OWNER="<GITHUB_ORG_OR_USERNAME>"
GITHUB_REPO="<GITHUB_REPO>"
PRINCIPAL_SET="principalSet://iam.googleapis.com/projects/${PROJECT_NUMBER}/locations/global/workloadIdentityPools/github-pool/attribute.repository/${GITHUB_OWNER}/${GITHUB_REPO}"

gcloud iam service-accounts add-iam-policy-binding ${DEPLOYER_SA} \
  --project=${PROJECT_ID} \
	--role="roles/iam.workloadIdentityUser" \
	--member="${PRINCIPAL_SET}"

gcloud iam service-accounts add-iam-policy-binding ${DEPLOYER_SA} \
	--project=${PROJECT_ID} \
	--role="roles/iam.serviceAccountTokenCreator" \
	--member="${PRINCIPAL_SET}"
```

**Step 3: Retry Your Workflow**

After fixing the service-account IAM bindings, re-run your failing workflow.

---

**Step 1: Verify the Deployer Project Roles**

Check the project-level roles granted to the deployer service account:

```bash
PROJECT_ID="auto-root-x-495307"
DEPLOYER_SA="github-deployer@${PROJECT_ID}.iam.gserviceaccount.com"

gcloud projects get-iam-policy ${PROJECT_ID} \
  --flatten="bindings[].members" \
  --filter="bindings.members:serviceAccount:${DEPLOYER_SA}" \
  --format='table(bindings.role)'
```

This output should include the project roles below. It will not show the repository principalSet bindings from the previous section because those live on the service account policy, not the project policy.

**Step 2: Verify All Required Roles**

The deployer service account needs all of these roles to run the workflows successfully. Check if you have all of them:

- `roles/container.admin` (Kubernetes Engine Admin)
- `roles/compute.networkAdmin` (Compute Network Admin)
- `roles/iam.serviceAccountAdmin` (Service Account Admin)
- `roles/iam.serviceAccountUser` (Service Account User)
- `roles/iam.serviceAccountTokenCreator` (Service Account Token Creator) ← **Required for getAccessToken**
- `roles/serviceusage.serviceUsageAdmin` (Service Usage Admin)
- `roles/artifactregistry.admin` (Artifact Registry Admin)
- `roles/storage.admin` (Storage Admin)
- `roles/secretmanager.admin` (Secret Manager Admin)
- `roles/resourcemanager.projectIamAdmin` (Project IAM Admin)

**Option A: Add roles using gcloud CLI**

If any roles are missing, add them with this script:

```bash
PROJECT_ID="auto-root-x-495307"
DEPLOYER_SA="github-deployer@${PROJECT_ID}.iam.gserviceaccount.com"

for role in \
	roles/container.admin \
	roles/compute.networkAdmin \
	roles/iam.serviceAccountAdmin \
	roles/iam.serviceAccountUser \
	roles/iam.serviceAccountTokenCreator \
	roles/serviceusage.serviceUsageAdmin \
	roles/artifactregistry.admin \
	roles/storage.admin \
	roles/secretmanager.admin \
	roles/resourcemanager.projectIamAdmin; do
	gcloud projects add-iam-policy-binding ${PROJECT_ID} \
		--member="serviceAccount:${DEPLOYER_SA}" \
		--role="${role}"
done
```

**Option B: Add roles manually in Google Cloud Console**

1. Open Google Cloud Console → `IAM & Admin` → `IAM`
2. Find the `github-deployer` service account
3. Click the edit pencil icon
4. Click `+ Add another role` for each missing role:
   - Kubernetes Engine Admin
   - Compute Network Admin
   - Service Account Admin
   - Service Account User
   - **Service Account Token Creator** ← Required for getAccessToken
   - Service Usage Admin
   - Artifact Registry Admin
   - Storage Admin
   - Secret Manager Admin
   - Project IAM Admin
5. Click `Save`

The two repository principalSet bindings still must be added on the `github-deployer` service account itself:

- `roles/iam.workloadIdentityUser`
- `roles/iam.serviceAccountTokenCreator`

---

### Error: "Invalid WIF provider resource name"

**Symptoms:**
```
Error: Failed to generate token using workload identity federation
...or authentication is rejected even after adding all roles
```

**Root Cause:**

The `GCP_WIF_PROVIDER` GitHub secret may contain an incorrect or non-existent provider resource name. Common issues:
- Using project ID instead of project number
- Pool or provider name doesn't match what was created
- Provider doesn't exist yet

**Step 1: Verify Your WIF Provider Exists**

Run this command to check if your provider exists and get the correct resource name:

```bash
PROJECT_ID="auto-root-x-495307"

gcloud iam workload-identity-pools providers describe github-provider \
  --project=${PROJECT_ID} \
  --location=global \
  --workload-identity-pool=github-pool \
  --format='value(name)'
```

This outputs the complete, correct resource name.

**Step 2: Compare With Your GitHub Secret**

Compare the output from Step 1 with your current `GCP_WIF_PROVIDER` GitHub secret. They must match exactly.

**Step 3: Update If Needed**

If the outputs differ:

1. Copy the correct resource name from Step 1
2. Go to your repository on GitHub → **Settings** → **Secrets and variables** → **Actions**
3. Edit the `GCP_WIF_PROVIDER` secret
4. Paste the correct resource name
5. Click **Update secret**

**Step 4: Retry Your Workflow**

After updating the secret, re-run your failing workflow.

---

## 10. Notes and Limits

- The first privileged GitHub-to-GCP trust setup still has to exist before the workflows can operate.
- The `Seed Secret Manager Secrets` workflow intentionally uses a temporary GitHub secret to get the first secret value into GSM. After seeding, GSM is the source of truth.
- The backend runtime identity is keyless after deployment because it uses GKE Workload Identity instead of mounted service account keys.
- All workflows are manual-only. Nothing runs on commit, branch push, or merge.
