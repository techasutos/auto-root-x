export interface PluginMeta {
  id: string
  name: string
  category: string
  inputs: string[]
}

export interface AdminPluginStatus extends PluginMeta {
  enabled: boolean
  dynamic: boolean
}

export interface Vulnerability {
  id: string
  title: string
  severity: 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW'
  description: string
  affectedPackage?: string
  currentVersion?: string
  fixedVersion?: string
  fix: string
  cvss?: string
}

export interface AnalysisResult {
  summary?: string
  rootCause?: string
  impact?: string
  fix?: string
  severity?: string
  confidence?: string
  aiUsage?: AiUsage
  vulnerabilities?: Vulnerability[]
}

export interface AiUsage {
  provider?: string
  model?: string
  callCount?: number
  latencyMs?: number
  retries?: number
  inputTokens?: number
  outputTokens?: number
  totalTokens?: number
  estimatedCostUsd?: number
  errorClass?: string
  rateLimited?: boolean
  circuitOpen?: boolean
}

export interface ApiErrorResponse {
  timestamp: string
  status: number
  error: string
  message: string
  path: string
}

export interface AddPluginRequest {
  id: string
  name: string
  category: string
  inputs: string[]
  summaryTemplate?: string
}

export interface ServiceNowTicketRequest {
  title: string
  description: string
  severity: string
  analyzerType?: string
  analysisSummary?: string
  affectedComponent?: string
}

export interface ServiceNowTicketResponse {
  ticketId: string
  status: string
  priority: string
  url: string
  createdAt: string
  message: string
}

export interface AgentRequest {
  problem: string
  context?: string
  hints?: Record<string, unknown>
}

export interface AgentResult {
  selectedAnalyzerId?: string
  selectedAnalyzerName?: string
  reason?: string
  mode?: string
  payload?: Record<string, unknown>
  analysis?: AnalysisResult
}
