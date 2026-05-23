# AutoRoot-X Architecture

AutoRoot-X is the control plane for cloud reliability and security analysis. It should not replace Google Cloud's native observability and security systems; it should route incidents to them, collect evidence, and use Vertex AI/Gemini to turn findings into remediation plans.

## Recommended Architecture

```text
                    AutoRoot-X
         +----------------------------+
         | React + TSX Control Plane  |
         +-------------+--------------+
                       |
               AI Orchestrator
                       |
      +---------------------------------+
      | Vertex AI Agent / Gemini Router |
      +---------------------------------+
           |        |         |
           v        v         v

   Cloud Logging   SCC    Artifact Analysis
   Trace           OSV    GKE Insights
   Monitoring      deps.dev
```

## Responsibility Split

AutoRoot-X owns:

- A React/TSX control plane for operators.
- A backend AI orchestrator that classifies a problem and chooses analyzers.
- Evidence aggregation across logs, security findings, image scans, dependency scans, and runtime signals.
- Vertex AI/Gemini prompting for root-cause summaries and remediation suggestions.
- Optional ticket creation in ServiceNow.

Google Cloud owns the source-of-truth signals:

- Cloud Logging, Trace, and Monitoring for reliability incidents.
- Security Command Center for centralized security findings and prioritization.
- Artifact Analysis for container image vulnerability metadata.
- GKE insights and cluster/workload health signals.
- Vertex AI Agent Builder or Agent Engine when the router evolves into a managed agent runtime.

External security intelligence can supplement GCP:

- OSV for open-source vulnerability lookup.
- deps.dev for dependency metadata, package versions, and dependency graph context.
- Trivy sidecar reports for pod-local image scan evidence.

## Backend Routing Model

The orchestrator should support two modes:

- Direct mode: run a requested analyzer such as `LOGS`, `IMAGE`, or `OSS`.
- Auto mode: accept a problem payload, infer the relevant analyzers, execute them, and merge their findings into one analysis result.

The recommended analyzer flow is:

```text
Incoming problem
      |
      v
Normalize payload
      |
      v
Classify issue type with deterministic hints first
      |
      v
Run relevant evidence collectors
      |
      v
Send compact evidence bundle to Vertex AI/Gemini
      |
      v
Return severity, confidence, root cause, impact, and fix
```

## Image Security Flow

For image analysis, deterministic scanners should produce the findings and Gemini should produce remediation guidance.

Preferred flow:

```text
Trivy sidecar / Artifact Analysis / SCC
      |
      v
Structured CVE findings
      |
      v
AutoRoot-X ImageAnalyzer
      |
      v
Vertex AI remediation prompt
      |
      v
Prioritized fix plan
```

This keeps vulnerability detection explainable and repeatable, while still using AI for prioritization and operator-friendly fixes.

## Evolution Path

1. Keep the current Spring Boot orchestrator as the first router.
2. Add real evidence collectors for Cloud Logging, Monitoring, SCC, Artifact Analysis, GKE, OSV, and deps.dev.
3. Standardize analyzer outputs into one evidence schema.
4. Move routing from heuristic rules to a Vertex AI Agent/Gemini router when tool coverage is stable.
5. Add traceability: every AI conclusion should link back to source evidence.
6. Add guardrails before any automated remediation action.
