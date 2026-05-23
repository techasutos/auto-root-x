import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Sparkles, Play, AlertCircle, Loader2, BrainCircuit, Check, Wand2, Ticket, Wrench } from 'lucide-react'
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { runAgent } from '@/api'
import type { AgentResult, EvidenceFinding } from '@/types'

const SEV_VARIANT: Record<string, 'critical' | 'high' | 'medium' | 'low' | 'default'> = {
  CRITICAL: 'critical',
  HIGH: 'high',
  MEDIUM: 'medium',
  LOW: 'low',
}

const TOOL_OPTIONS = [
  { id: 'cloud-logging', label: 'Cloud Logging', category: 'Observability' },
  { id: 'monitoring', label: 'Monitoring', category: 'Observability' },
  { id: 'trace', label: 'Trace', category: 'Observability' },
  { id: 'gke-insights', label: 'GKE Insights', category: 'Platform' },
  { id: 'scc', label: 'SCC', category: 'Security' },
  { id: 'artifact-analysis', label: 'Artifact Analysis', category: 'Security' },
  { id: 'trivy', label: 'Trivy', category: 'Security' },
  { id: 'osv', label: 'OSV', category: 'Dependencies' },
  { id: 'deps-dev', label: 'deps.dev', category: 'Dependencies' },
]

function EvidenceRow({ finding }: { finding: EvidenceFinding }) {
  return (
    <div className="rounded-lg border border-border bg-muted/20 p-3 space-y-2">
      <div className="flex flex-wrap items-center gap-2">
        {finding.source && <Badge variant="outline">{finding.source}</Badge>}
        {finding.category && <Badge variant="secondary">{finding.category}</Badge>}
        {finding.severity && (
          <Badge variant={SEV_VARIANT[finding.severity] ?? 'default'}>{finding.severity}</Badge>
        )}
      </div>
      <div>
        <p className="text-sm font-medium">{finding.title ?? 'Evidence finding'}</p>
        {finding.resource && <p className="text-xs text-muted-foreground">{finding.resource}</p>}
      </div>
      {finding.summary && <p className="text-sm text-muted-foreground">{finding.summary}</p>}
      {finding.raw && (
        <pre className="max-h-28 overflow-auto rounded-md bg-background/70 p-2 text-xs whitespace-pre-wrap font-mono">
          {finding.raw}
        </pre>
      )}
    </div>
  )
}

function extractSection(summary: string, headings: string[]): string {
  for (const heading of headings) {
    const escaped = heading.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
    const pattern = new RegExp(`\\*\\*${escaped}\\*\\*:?\\s*([\\s\\S]*?)(?=\\n\\*\\*[A-Z ][A-Z ]+\\*\\*:?|$)`, 'i')
    const match = summary.match(pattern)
    if (match?.[1]?.trim()) {
      return match[1].trim()
    }
  }
  return ''
}

function extractSeverity(summary: string, fallback: string): string {
  const match = summary.match(/\*\*SEVERITY\*\*:?\s*(CRITICAL|HIGH|MEDIUM|LOW)/i)
  return match?.[1]?.toUpperCase() ?? fallback
}

export default function AgentPage() {
  const navigate = useNavigate()
  const [problem, setProblem] = useState('Kubernetes pod is crash looping after a new deployment and logs mention a null pointer exception')
  const [context, setContext] = useState('GKE on GCP, backend service deployed through Helm, want the fastest root-cause path.')
  const [toolMode, setToolMode] = useState<'auto' | 'manual'>('auto')
  const [selectedTools, setSelectedTools] = useState<string[]>(['cloud-logging', 'gke-insights'])
  const [loading, setLoading] = useState(false)
  const [result, setResult] = useState<AgentResult | null>(null)
  const [showFix, setShowFix] = useState(false)
  const [error, setError] = useState('')

  function toggleTool(id: string) {
    setSelectedTools((current) =>
      current.includes(id)
        ? current.filter((item) => item !== id)
        : [...current, id]
    )
  }

  async function run() {
    setLoading(true)
    setError('')
    setResult(null)
    setShowFix(false)
    try {
      const hints = toolMode === 'manual' ? { sources: selectedTools } : undefined
      const res = await runAgent({ problem, context, hints })
      setResult(res)
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : 'Agent analysis failed')
    } finally {
      setLoading(false)
    }
  }

  function openServiceNowDraft() {
    if (!result?.analysis) {
      return
    }

    const evidenceResources = result.evidenceBundle?.findings
      ?.map((finding) => finding.resource)
      .filter((resource): resource is string => Boolean(resource?.trim())) ?? []

    const selectedSources = result.selectedSources?.join(', ') || 'AutoRoot-X agent'
    const affectedComponent = evidenceResources[0] ?? ''
    const summary = result.analysis.summary ?? ''
    const severity = extractSeverity(summary, result.analysis.severity ?? 'HIGH')
    const rootCause = result.analysis.rootCause ?? extractSection(summary, ['ROOT CAUSE'])
    const impact = result.analysis.impact ?? extractSection(summary, ['IMPACT'])
    const fix = result.analysis.fix ?? extractSection(summary, ['RECOMMENDED FIX', 'FIX'])

    navigate('/servicenow', {
      state: {
        draftIncident: {
          title: `${severity}: ${problem.slice(0, 90)}`,
          severity,
          analyzerType: result.selectedAnalyzerId ?? 'AUTO',
          affectedComponent,
          description: [
            `Problem: ${problem}`,
            context ? `Context: ${context}` : '',
            `Evidence sources: ${selectedSources}`,
            rootCause ? `Root cause: ${rootCause}` : '',
            impact ? `Impact: ${impact}` : '',
            fix ? `Recommended fix: ${fix}` : '',
          ].filter(Boolean).join('\n\n'),
          analysisSummary: summary,
        },
      },
    })
  }

  const remediationText = result?.analysis
    ? result.analysis.fix
      ?? extractSection(result.analysis.summary ?? '', ['RECOMMENDED FIX', 'FIX'])
      ?? ''
    : ''

  return (
    <div className="p-8 space-y-6">
      <div className="flex items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold flex items-center gap-2">
            <Sparkles className="h-6 w-6 text-primary" />
            Agent Triage
          </h1>
          <p className="text-sm text-muted-foreground mt-1">
            Let Gemini collect evidence automatically or force specific tools when you know where to look.
          </p>
        </div>
        <Button onClick={run} disabled={loading || !problem.trim()} size="lg">
          {loading ? <Loader2 className="h-4 w-4 animate-spin" /> : <Play className="h-4 w-4" />}
          {loading ? 'Thinking...' : 'Run Agent'}
        </Button>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
        <Card>
          <CardHeader>
            <CardTitle className="text-base flex items-center gap-2">
              <BrainCircuit className="h-4 w-4" />
              Problem Statement
            </CardTitle>
            <CardDescription>Describe the issue in plain language</CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="space-y-1.5">
              <label className="text-sm font-medium">Problem</label>
              <textarea
                rows={6}
                value={problem}
                onChange={(e) => setProblem(e.target.value)}
                className="w-full rounded-lg border border-input bg-muted/40 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-ring resize-none"
              />
            </div>
            <div className="space-y-1.5">
              <label className="text-sm font-medium">Context</label>
              <textarea
                rows={4}
                value={context}
                onChange={(e) => setContext(e.target.value)}
                className="w-full rounded-lg border border-input bg-muted/40 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-ring resize-none"
              />
            </div>
            <div className="space-y-3 rounded-lg border border-border bg-muted/20 p-3">
              <div className="flex items-center justify-between gap-3">
                <div>
                  <p className="text-sm font-medium">Tool Selection</p>
                  <p className="text-xs text-muted-foreground">
                    Auto infers sources from the problem. Manual runs only selected tools.
                  </p>
                </div>
                <div className="inline-flex rounded-lg border border-border bg-background p-1">
                  <button
                    type="button"
                    onClick={() => setToolMode('auto')}
                    className={`inline-flex h-8 items-center gap-1.5 rounded-md px-3 text-xs font-medium transition-colors ${toolMode === 'auto' ? 'bg-primary text-primary-foreground' : 'text-muted-foreground hover:text-foreground'}`}
                  >
                    <Wand2 className="h-3.5 w-3.5" />
                    Auto
                  </button>
                  <button
                    type="button"
                    onClick={() => setToolMode('manual')}
                    className={`inline-flex h-8 items-center gap-1.5 rounded-md px-3 text-xs font-medium transition-colors ${toolMode === 'manual' ? 'bg-primary text-primary-foreground' : 'text-muted-foreground hover:text-foreground'}`}
                  >
                    <Check className="h-3.5 w-3.5" />
                    Manual
                  </button>
                </div>
              </div>

              {toolMode === 'manual' && (
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
                  {TOOL_OPTIONS.map((tool) => {
                    const active = selectedTools.includes(tool.id)
                    return (
                      <button
                        key={tool.id}
                        type="button"
                        onClick={() => toggleTool(tool.id)}
                        className={`flex min-h-12 items-center justify-between rounded-lg border px-3 py-2 text-left transition-colors ${active ? 'border-primary/60 bg-primary/10 text-foreground' : 'border-border bg-background/60 text-muted-foreground hover:text-foreground'}`}
                      >
                        <span>
                          <span className="block text-sm font-medium">{tool.label}</span>
                          <span className="block text-xs text-muted-foreground">{tool.category}</span>
                        </span>
                        <span className={`flex h-5 w-5 items-center justify-center rounded-full border ${active ? 'border-primary bg-primary text-primary-foreground' : 'border-border'}`}>
                          {active && <Check className="h-3 w-3" />}
                        </span>
                      </button>
                    )
                  })}
                </div>
              )}
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle className="text-base">Agent Output</CardTitle>
            <CardDescription>Routing decision plus the downstream analysis result</CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            {error && (
              <div className="flex items-center gap-2 rounded-lg border border-destructive/50 bg-destructive/10 px-4 py-3 text-sm text-destructive">
                <AlertCircle className="h-4 w-4 shrink-0" />
                {error}
              </div>
            )}

            {result ? (
              <div className="space-y-4">
                <div className="flex flex-wrap items-center gap-2">
                  <Badge variant="secondary">{result.mode ?? 'unknown'}</Badge>
                  {result.selectedAnalyzerId && <Badge variant="outline">{result.selectedAnalyzerId}</Badge>}
                  {result.analysis?.severity && (
                    <Badge variant={SEV_VARIANT[result.analysis.severity] ?? 'default'}>{result.analysis.severity}</Badge>
                  )}
                  {result.analysis?.confidence && (
                    <span className="text-xs text-muted-foreground">Confidence: {result.analysis.confidence}</span>
                  )}
                </div>

                {result.analysis && (
                  <div className="flex flex-wrap gap-2 rounded-lg border border-border bg-muted/20 p-3">
                    <Button type="button" onClick={() => setShowFix((value) => !value)} variant="outline">
                      <Wrench className="h-4 w-4" />
                      {showFix ? 'Hide Fix' : 'Review Fix'}
                    </Button>
                    <Button type="button" onClick={openServiceNowDraft}>
                      <Ticket className="h-4 w-4" />
                      Create Incident Draft
                    </Button>
                  </div>
                )}

                {showFix && (
                  <Card className="border-primary/30">
                    <CardHeader className="pb-2">
                      <CardTitle className="text-sm text-primary">Recommended Fix</CardTitle>
                      <CardDescription>Review before applying changes or raising an incident</CardDescription>
                    </CardHeader>
                    <CardContent>
                      <pre className="text-sm whitespace-pre-wrap font-mono bg-muted/40 rounded-lg p-4">
                        {remediationText || 'No dedicated remediation section was returned. Review the analysis summary below.'}
                      </pre>
                    </CardContent>
                  </Card>
                )}

                {result.analysis?.aiUsage && (
                  <Card>
                    <CardHeader className="pb-2">
                      <CardTitle className="text-sm">AI Usage</CardTitle>
                    </CardHeader>
                    <CardContent>
                      <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 text-sm">
                        <p>Latency: <span className="text-muted-foreground">{result.analysis.aiUsage.latencyMs ?? 0} ms</span></p>
                        <p>Tokens: <span className="text-muted-foreground">{result.analysis.aiUsage.totalTokens ?? 0}</span></p>
                        <p>Retries: <span className="text-muted-foreground">{result.analysis.aiUsage.retries ?? 0}</span></p>
                        <p>Cost: <span className="text-muted-foreground">${(result.analysis.aiUsage.estimatedCostUsd ?? 0).toFixed(6)}</span></p>
                        <p>Input: <span className="text-muted-foreground">{result.analysis.aiUsage.inputTokens ?? 0}</span></p>
                        <p>Output: <span className="text-muted-foreground">{result.analysis.aiUsage.outputTokens ?? 0}</span></p>
                        <p>Calls: <span className="text-muted-foreground">{result.analysis.aiUsage.callCount ?? 0}</span></p>
                        <p>Error Class: <span className="text-muted-foreground">{result.analysis.aiUsage.errorClass ?? 'none'}</span></p>
                      </div>
                    </CardContent>
                  </Card>
                )}

                <div className="space-y-2">
                  <p className="text-sm font-medium">Selected Analyzer</p>
                  <p className="text-sm text-muted-foreground">
                    {result.selectedAnalyzerName ?? 'General Gemini Triage'}
                  </p>
                  {result.reason && <p className="text-sm">{result.reason}</p>}
                </div>

                {result.selectedSources && result.selectedSources.length > 0 && (
                  <div className="space-y-2">
                    <p className="text-sm font-medium">Evidence Sources</p>
                    <div className="flex flex-wrap gap-2">
                      {result.selectedSources.map((source) => (
                        <Badge key={source} variant="outline">{source}</Badge>
                      ))}
                    </div>
                  </div>
                )}

                {result.evidenceBundle?.warnings && result.evidenceBundle.warnings.length > 0 && (
                  <div className="space-y-2">
                    <p className="text-sm font-medium">Evidence Gaps</p>
                    <div className="space-y-1">
                      {result.evidenceBundle.warnings.map((warning, i) => (
                        <p key={i} className="text-sm text-muted-foreground">{warning}</p>
                      ))}
                    </div>
                  </div>
                )}

                {result.payload && Object.keys(result.payload).length > 0 && (
                  <div className="space-y-2">
                    <p className="text-sm font-medium">Payload</p>
                    <pre className="text-xs whitespace-pre-wrap font-mono bg-muted/40 rounded-lg p-3">
                      {JSON.stringify(result.payload, null, 2)}
                    </pre>
                  </div>
                )}

                {result.analysis?.summary && (
                  <Card>
                    <CardHeader className="pb-3">
                      <CardTitle className="text-sm">Analysis Summary</CardTitle>
                    </CardHeader>
                    <CardContent>
                      <pre className="text-sm whitespace-pre-wrap font-mono bg-muted/40 rounded-lg p-4">
                        {result.analysis.summary}
                      </pre>
                    </CardContent>
                  </Card>
                )}

                {result.evidenceBundle?.findings && result.evidenceBundle.findings.length > 0 && (
                  <Card>
                    <CardHeader className="pb-3">
                      <CardTitle className="text-sm">Evidence Bundle</CardTitle>
                      <CardDescription>Source findings passed into the Gemini router</CardDescription>
                    </CardHeader>
                    <CardContent className="space-y-3">
                      {result.evidenceBundle.findings.slice(0, 8).map((finding, i) => (
                        <EvidenceRow key={i} finding={finding} />
                      ))}
                    </CardContent>
                  </Card>
                )}

                <div className="grid gap-4 sm:grid-cols-3">
                  {result.analysis?.rootCause && (
                    <Card>
                      <CardHeader className="pb-2">
                        <CardTitle className="text-xs text-muted-foreground font-medium">Root Cause</CardTitle>
                      </CardHeader>
                      <CardContent><p className="text-sm">{result.analysis.rootCause}</p></CardContent>
                    </Card>
                  )}
                  {result.analysis?.impact && (
                    <Card>
                      <CardHeader className="pb-2">
                        <CardTitle className="text-xs text-muted-foreground font-medium">Impact</CardTitle>
                      </CardHeader>
                      <CardContent><p className="text-sm">{result.analysis.impact}</p></CardContent>
                    </Card>
                  )}
                  {result.analysis?.fix && (
                    <Card className="border-primary/30">
                      <CardHeader className="pb-2">
                        <CardTitle className="text-xs text-primary font-medium">Recommended Fix</CardTitle>
                      </CardHeader>
                      <CardContent><p className="text-sm">{result.analysis.fix}</p></CardContent>
                    </Card>
                  )}
                </div>
              </div>
            ) : (
              !loading && (
                <Card className="border-dashed">
                  <CardContent className="py-12 flex flex-col items-center text-center gap-2">
                    <Sparkles className="h-10 w-10 text-muted-foreground/40" />
                    <CardDescription>Run the agent to see how Gemini routes and summarizes the issue</CardDescription>
                  </CardContent>
                </Card>
              )
            )}
          </CardContent>
        </Card>
      </div>
    </div>
  )
}
