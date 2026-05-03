{{- define "autorootx.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{- define "autorootx.fullname" -}}
{{- if .Values.fullnameOverride -}}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" -}}
{{- else -}}
{{- $name := default .Chart.Name .Values.nameOverride -}}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" -}}
{{- end -}}
{{- end -}}

{{- define "autorootx.labels" -}}
app.kubernetes.io/name: {{ include "autorootx.name" . }}
helm.sh/chart: {{ .Chart.Name }}-{{ .Chart.Version | replace "+" "_" }}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end -}}

{{- define "autorootx.backendServiceAccountName" -}}
{{- if .Values.backend.serviceAccount.create -}}
{{- default (printf "%s-backend" (include "autorootx.fullname" .)) .Values.backend.serviceAccount.name -}}
{{- else -}}
{{- default "default" .Values.backend.serviceAccount.name -}}
{{- end -}}
{{- end -}}
