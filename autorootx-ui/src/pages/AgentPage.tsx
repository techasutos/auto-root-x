import { useState } from 'react'
import { Sparkles, Play, AlertCircle, Loader2, BrainCircuit } from 'lucide-react'
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { runAgent } from '@/api'
import type { AgentResult } from '@/types'

const SEV_VARIANT: Record<string, 'critical' | 'high' | 'medium' | 'low' | 'default'> = {
  CRITICAL: 'critical',
  HIGH: 'high',
  MEDIUM: 'medium',
  LOW: 'low',
}

export default function AgentPage() {
  const [problem, setProblem] = useState('Kubernetes pod is crash looping after a new deployment and logs mention a null pointer exception')
  const [context, setContext] = useState('GKE on GCP, backend service deployed through Helm, want the fastest root-cause path.')
  const [loading, setLoading] = useState(false)
  const [result, setResult] = useState<AgentResult | null>(null)
  const [error, setError] = useState('')

  async function run() {
    setLoading(true)
    setError('')
    setResult(null)
    try {
      const res = await runAgent({ problem, context })
      setResult(res)
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : 'Agent analysis failed')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="p-8 space-y-6">
      <div className="flex items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold flex items-center gap-2">
            <Sparkles className="h-6 w-6 text-primary" />
            Agent Triage
          </h1>
          <p className="text-sm text-muted-foreground mt-1">
            Let Gemini route the issue to the right analyzer or handle it directly when no tool fits.
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