import type { AddPluginRequest, AdminPluginStatus, AnalysisResult, ApiErrorResponse, PluginMeta, ServiceNowTicketRequest, ServiceNowTicketResponse } from './types'

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(path, {
    headers: {
      'Content-Type': 'application/json',
      ...(init?.headers ?? {}),
    },
    ...init,
  })

  if (!response.ok) {
    let payload: ApiErrorResponse | undefined
    try {
      payload = await response.json()
    } catch {
      payload = undefined
    }
    throw new Error(payload?.message ?? `Request failed: ${response.status}`)
  }

  if (response.status === 204) {
    return undefined as T
  }

  return response.json() as Promise<T>
}

export function getPlugins(): Promise<PluginMeta[]> {
  return request<PluginMeta[]>('/api/plugins')
}

export function analyze(analyzerId: string, payload: Record<string, string>): Promise<AnalysisResult> {
  return request<AnalysisResult>('/api/analyze', {
    method: 'POST',
    body: JSON.stringify({ analyzerId, payload }),
  })
}

export function getAdminPlugins(): Promise<AdminPluginStatus[]> {
  return request<AdminPluginStatus[]>('/api/admin/plugins')
}

export function setPluginEnabled(id: string, enabled: boolean): Promise<void> {
  return request<void>(`/api/admin/plugins/${id}/enabled?enabled=${enabled}`, {
    method: 'PUT',
  })
}

export function addPlugin(payload: AddPluginRequest): Promise<void> {
  return request<void>('/api/admin/plugins', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function createIncident(payload: ServiceNowTicketRequest): Promise<ServiceNowTicketResponse> {
  return request<ServiceNowTicketResponse>('/api/servicenow/incidents', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}
