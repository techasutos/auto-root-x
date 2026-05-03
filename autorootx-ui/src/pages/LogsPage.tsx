import { useState } from 'react'
import { ScrollText, Play, AlertCircle, Loader2 } from 'lucide-react'
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { analyze } from '@/api'
import type { AnalysisResult } from '@/types'

const SEV_VARIANT: Record<string, 'critical' | 'high' | 'medium' | 'low' | 'default'> = {
  CRITICAL: 'critical',
  HIGH: 'high',
  MEDIUM: 'medium',
  LOW: 'low',
}

export default function LogsPage() {
  const [loading, setLoading] = useState(false)
  const [result, setResult] = useState<AnalysisResult | null>(null)
  const [error, setError] = useState('')

  async function run() {
    setLoading(true)
    setError('')
    setResult(null)
    try {
      const res = await analyze('LOGS', {})
      setResult(res)
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : 'Analysis failed')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="p-8 space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold flex items-center gap-2">
            <ScrollText className="h-6 w-6 text-primary" />
            Logs Analyzer
          </h1>
          <p className="text-sm text-muted-foreground mt-1">
            Fetches GCP Cloud Logging errors and analyzes them with Vertex AI Gemini
          </p>
        </div>
        <Button onClick={run} disabled={loading} size="lg">
          {loading ? <Loader2 className="h-4 w-4 animate-spin" /> : <Play className="h-4 w-4" />}
          {loading ? 'Analyzing…' : 'Run Analysis'}
        </Button>
      </div>

      {error && (
        <div className="flex items-center gap-2 rounded-lg border border-destructive/50 bg-destructive/10 px-4 py-3 text-sm text-destructive">
          <AlertCircle className="h-4 w-4 shrink-0" />
          {error}
        </div>
      )}

      {result && (
        <div className="space-y-4">
          <div className="flex items-center gap-2">
            <Badge variant={SEV_VARIANT[result.severity ?? ''] ?? 'default'}>{result.severity ?? 'N/A'}</Badge>
            {result.confidence && <span className="text-xs text-muted-foreground">Confidence: {result.confidence}</span>}
          </div>

          <div className="grid gap-4">
            {result.summary && (
              <Card>
                <CardHeader className="pb-3">
                  <CardTitle className="text-base">AI Analysis Summary</CardTitle>
                </CardHeader>
                <CardContent>
                  <pre className="text-sm text-foreground whitespace-pre-wrap font-mono bg-muted/40 rounded-lg p-4">
                    {result.summary}
                  </pre>
                </CardContent>
              </Card>
            )}

            <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
              {result.rootCause && (
                <Card>
                  <CardHeader className="pb-2">
                    <CardTitle className="text-sm text-muted-foreground font-medium">Root Cause</CardTitle>
                  </CardHeader>
                  <CardContent>
                    <p className="text-sm">{result.rootCause}</p>
                  </CardContent>
                </Card>
              )}
              {result.impact && (
                <Card>
                  <CardHeader className="pb-2">
                    <CardTitle className="text-sm text-muted-foreground font-medium">Impact</CardTitle>
                  </CardHeader>
                  <CardContent>
                    <p className="text-sm">{result.impact}</p>
                  </CardContent>
                </Card>
              )}
              {result.fix && (
                <Card className="border-primary/30">
                  <CardHeader className="pb-2">
                    <CardTitle className="text-sm text-primary font-medium">Recommended Fix</CardTitle>
                  </CardHeader>
                  <CardContent>
                    <p className="text-sm">{result.fix}</p>
                  </CardContent>
                </Card>
              )}
            </div>
          </div>
        </div>
      )}

      {!loading && !result && !error && (
        <Card className="border-dashed">
          <CardContent className="py-12 flex flex-col items-center text-center gap-2">
            <ScrollText className="h-10 w-10 text-muted-foreground/40" />
            <CardDescription>Click "Run Analysis" to fetch and analyze GCP logs</CardDescription>
          </CardContent>
        </Card>
      )}
    </div>
  )
}
