import { useState } from 'react'
import { Package, Play, AlertCircle, Loader2, Plus, Trash2 } from 'lucide-react'
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { analyze } from '@/api'
import type { AnalysisResult } from '@/types'

interface Dep { name: string; version: string }

const DEFAULT_DEPS: Dep[] = [
  { name: 'lodash', version: '4.17.19' },
  { name: 'log4j-core', version: '2.14.1' },
]

export default function OssPage() {
  const [deps, setDeps] = useState<Dep[]>(DEFAULT_DEPS)
  const [newName, setNewName] = useState('')
  const [newVersion, setNewVersion] = useState('')
  const [loading, setLoading] = useState(false)
  const [result, setResult] = useState<AnalysisResult | null>(null)
  const [error, setError] = useState('')

  function addDep() {
    if (newName.trim() && newVersion.trim()) {
      setDeps([...deps, { name: newName.trim(), version: newVersion.trim() }])
      setNewName('')
      setNewVersion('')
    }
  }

  function removeDep(i: number) {
    setDeps(deps.filter((_, idx) => idx !== i))
  }

  async function run() {
    setLoading(true)
    setError('')
    setResult(null)
    try {
      const res = await analyze('OSS', { dependencies: deps })
      setResult(res)
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : 'Scan failed')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="p-8 space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold flex items-center gap-2">
            <Package className="h-6 w-6 text-primary" />
            OSS Scanner
          </h1>
          <p className="text-sm text-muted-foreground mt-1">
            Check open-source dependencies against OSV.dev vulnerability database
          </p>
        </div>
        <Button onClick={run} disabled={loading || deps.length === 0}>
          {loading ? <Loader2 className="h-4 w-4 animate-spin" /> : <Play className="h-4 w-4" />}
          {loading ? 'Scanning…' : 'Scan Dependencies'}
        </Button>
      </div>

      {/* Dependency list */}
      <Card>
        <CardHeader>
          <CardTitle className="text-base">Dependencies</CardTitle>
          <CardDescription>Add packages to check against known CVEs</CardDescription>
        </CardHeader>
        <CardContent className="space-y-3">
          {deps.map((d, i) => (
            <div key={i} className="flex items-center gap-3 rounded-lg border border-border bg-muted/20 px-3 py-2">
              <Package className="h-4 w-4 text-muted-foreground shrink-0" />
              <span className="flex-1 text-sm font-medium">{d.name}</span>
              <Badge variant="secondary">{d.version}</Badge>
              <button onClick={() => removeDep(i)} className="text-muted-foreground hover:text-destructive transition-colors">
                <Trash2 className="h-3.5 w-3.5" />
              </button>
            </div>
          ))}
          <div className="flex gap-2 pt-1">
            <input
              type="text"
              placeholder="package-name"
              value={newName}
              onChange={(e) => setNewName(e.target.value)}
              className="flex-1 rounded-lg border border-input bg-muted/40 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-ring"
            />
            <input
              type="text"
              placeholder="1.0.0"
              value={newVersion}
              onChange={(e) => setNewVersion(e.target.value)}
              onKeyDown={(e) => e.key === 'Enter' && addDep()}
              className="w-28 rounded-lg border border-input bg-muted/40 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-ring"
            />
            <Button variant="outline" size="sm" onClick={addDep} disabled={!newName || !newVersion}>
              <Plus className="h-4 w-4" />
            </Button>
          </div>
        </CardContent>
      </Card>

      {error && (
        <div className="flex items-center gap-2 rounded-lg border border-destructive/50 bg-destructive/10 px-4 py-3 text-sm text-destructive">
          <AlertCircle className="h-4 w-4 shrink-0" />
          {error}
        </div>
      )}

      {result && (
        <div className="space-y-4">
          <div className="flex items-center gap-2">
            {result.severity && <Badge variant={result.severity === 'CRITICAL' ? 'critical' : result.severity === 'HIGH' ? 'high' : 'medium'}>{result.severity}</Badge>}
          </div>
          {result.summary && (
            <Card>
              <CardHeader className="pb-3"><CardTitle className="text-base">Analysis Results</CardTitle></CardHeader>
              <CardContent>
                <pre className="text-sm whitespace-pre-wrap font-mono bg-muted/40 rounded-lg p-4">{result.summary}</pre>
              </CardContent>
            </Card>
          )}
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            {result.rootCause && (
              <Card>
                <CardHeader className="pb-2"><CardTitle className="text-sm text-muted-foreground font-medium">Root Cause</CardTitle></CardHeader>
                <CardContent><p className="text-sm">{result.rootCause}</p></CardContent>
              </Card>
            )}
            {result.fix && (
              <Card className="border-primary/30">
                <CardHeader className="pb-2"><CardTitle className="text-sm text-primary font-medium">Recommended Fix</CardTitle></CardHeader>
                <CardContent><p className="text-sm">{result.fix}</p></CardContent>
              </Card>
            )}
          </div>
        </div>
      )}
    </div>
  )
}
