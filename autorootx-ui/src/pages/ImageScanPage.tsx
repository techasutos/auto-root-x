import { useState } from 'react'
import { Shield, Play, AlertCircle, Loader2, Wrench, ExternalLink } from 'lucide-react'
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { analyze } from '@/api'
import type { AnalysisResult, Vulnerability } from '@/types'

type SevKey = 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW'
const SEV_VARIANT: Record<SevKey, 'critical' | 'high' | 'medium' | 'low'> = {
  CRITICAL: 'critical',
  HIGH: 'high',
  MEDIUM: 'medium',
  LOW: 'low',
}

function VulnRow({ vuln, onFix }: { vuln: Vulnerability; onFix: (v: Vulnerability) => void }) {
  return (
    <tr className="border-b border-border/50 last:border-0 hover:bg-muted/20 transition-colors">
      <td className="px-4 py-3">
        <p className="font-medium text-sm">{vuln.title}</p>
        <p className="text-xs text-muted-foreground">{vuln.id}</p>
      </td>
      <td className="px-4 py-3">
        <Badge variant={SEV_VARIANT[vuln.severity as SevKey] ?? 'default'}>{vuln.severity}</Badge>
      </td>
      <td className="px-4 py-3 text-sm text-muted-foreground max-w-xs truncate">{vuln.affectedPackage ?? '—'}</td>
      <td className="px-4 py-3 text-xs text-muted-foreground">{vuln.currentVersion ?? '—'}</td>
      <td className="px-4 py-3 text-xs text-green-400">{vuln.fixedVersion ?? '—'}</td>
      <td className="px-4 py-3 text-xs">{vuln.cvss ?? '—'}</td>
      <td className="px-4 py-3">
        <Button size="sm" variant="outline" onClick={() => onFix(vuln)} className="gap-1.5 border-primary/40 text-primary hover:bg-primary/10">
          <Wrench className="h-3 w-3" />
          Fix
        </Button>
      </td>
    </tr>
  )
}

export default function ImageScanPage() {
  const [image, setImage] = useState('gcr.io/my-project/my-app:latest')
  const [loading, setLoading] = useState(false)
  const [result, setResult] = useState<AnalysisResult | null>(null)
  const [error, setError] = useState('')
  const [fixModal, setFixModal] = useState<Vulnerability | null>(null)

  async function run() {
    setLoading(true)
    setError('')
    setResult(null)
    try {
      const res = await analyze('IMAGE', { image })
      setResult(res)
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : 'Scan failed')
    } finally {
      setLoading(false)
    }
  }

  const vulns = result?.vulnerabilities ?? []
  const critical = vulns.filter((v) => v.severity === 'CRITICAL').length
  const high = vulns.filter((v) => v.severity === 'HIGH').length

  return (
    <div className="p-8 space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold flex items-center gap-2">
            <Shield className="h-6 w-6 text-primary" />
            Image Scanner
          </h1>
          <p className="text-sm text-muted-foreground mt-1">
            Scan container images for CVE vulnerabilities with AI-suggested fixes
          </p>
        </div>
      </div>

      {/* Input */}
      <Card>
        <CardContent className="pt-5 pb-5">
          <div className="flex gap-3">
            <input
              type="text"
              value={image}
              onChange={(e) => setImage(e.target.value)}
              placeholder="gcr.io/project/image:tag"
              className="flex-1 rounded-lg border border-input bg-muted/40 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-ring"
            />
            <Button onClick={run} disabled={loading || !image.trim()}>
              {loading ? <Loader2 className="h-4 w-4 animate-spin" /> : <Play className="h-4 w-4" />}
              {loading ? 'Scanning…' : 'Scan Image'}
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
        <div className="space-y-6">
          {/* Summary stats */}
          <div className="grid grid-cols-4 gap-4">
            <Card>
              <CardContent className="pt-5">
                <p className="text-xs text-muted-foreground">Total</p>
                <p className="text-3xl font-bold">{vulns.length}</p>
              </CardContent>
            </Card>
            <Card className="border-red-500/30">
              <CardContent className="pt-5">
                <p className="text-xs text-red-400">Critical</p>
                <p className="text-3xl font-bold text-red-400">{critical}</p>
              </CardContent>
            </Card>
            <Card className="border-orange-500/30">
              <CardContent className="pt-5">
                <p className="text-xs text-orange-400">High</p>
                <p className="text-3xl font-bold text-orange-400">{high}</p>
              </CardContent>
            </Card>
            <Card>
              <CardContent className="pt-5">
                <p className="text-xs text-muted-foreground">Medium / Low</p>
                <p className="text-3xl font-bold">{vulns.length - critical - high}</p>
              </CardContent>
            </Card>
          </div>

          {/* AI Summary */}
          {result.summary && (
            <Card>
              <CardHeader className="pb-3">
                <CardTitle className="text-base">AI Analysis</CardTitle>
              </CardHeader>
              <CardContent>
                <pre className="text-sm whitespace-pre-wrap font-mono bg-muted/40 rounded-lg p-4">
                  {result.summary}
                </pre>
              </CardContent>
            </Card>
          )}

          {/* Vulnerability table */}
          {vulns.length > 0 && (
            <Card>
              <CardHeader>
                <CardTitle className="text-base">Vulnerabilities</CardTitle>
                <CardDescription>Click Fix for AI-generated remediation steps</CardDescription>
              </CardHeader>
              <CardContent className="p-0">
                <div className="overflow-x-auto">
                  <table className="w-full text-sm">
                    <thead>
                      <tr className="border-b border-border bg-muted/20">
                        <th className="px-4 py-3 text-left text-xs text-muted-foreground font-medium uppercase">Vulnerability</th>
                        <th className="px-4 py-3 text-left text-xs text-muted-foreground font-medium uppercase">Severity</th>
                        <th className="px-4 py-3 text-left text-xs text-muted-foreground font-medium uppercase">Package</th>
                        <th className="px-4 py-3 text-left text-xs text-muted-foreground font-medium uppercase">Current</th>
                        <th className="px-4 py-3 text-left text-xs text-muted-foreground font-medium uppercase">Fixed in</th>
                        <th className="px-4 py-3 text-left text-xs text-muted-foreground font-medium uppercase">CVSS</th>
                        <th className="px-4 py-3 text-left text-xs text-muted-foreground font-medium uppercase">Action</th>
                      </tr>
                    </thead>
                    <tbody>
                      {vulns.map((v) => (
                        <VulnRow key={v.id} vuln={v} onFix={setFixModal} />
                      ))}
                    </tbody>
                  </table>
                </div>
              </CardContent>
            </Card>
          )}
        </div>
      )}

      {!loading && !result && !error && (
        <Card className="border-dashed">
          <CardContent className="py-12 flex flex-col items-center text-center gap-2">
            <Shield className="h-10 w-10 text-muted-foreground/40" />
            <CardDescription>Enter an image reference and click "Scan Image"</CardDescription>
          </CardContent>
        </Card>
      )}

      {/* Fix Modal */}
      {fixModal && (
        <div className="fixed inset-0 bg-black/60 flex items-center justify-center z-50 p-4" onClick={() => setFixModal(null)}>
          <Card className="w-full max-w-lg" onClick={(e) => e.stopPropagation()}>
            <CardHeader>
              <div className="flex items-center justify-between">
                <CardTitle className="text-base flex items-center gap-2">
                  <Wrench className="h-4 w-4 text-primary" />
                  Fix: {fixModal.title}
                </CardTitle>
                <Badge variant={SEV_VARIANT[fixModal.severity as SevKey] ?? 'default'}>{fixModal.severity}</Badge>
              </div>
              <CardDescription>{fixModal.id}</CardDescription>
            </CardHeader>
            <CardContent className="space-y-4">
              <div>
                <p className="text-xs text-muted-foreground mb-1">Description</p>
                <p className="text-sm">{fixModal.description}</p>
              </div>
              {fixModal.fixedVersion && (
                <div>
                  <p className="text-xs text-muted-foreground mb-1">Upgrade to</p>
                  <code className="text-sm text-green-400 bg-green-500/10 px-2 py-0.5 rounded">
                    {fixModal.affectedPackage}@{fixModal.fixedVersion}
                  </code>
                </div>
              )}
              <div>
                <p className="text-xs text-muted-foreground mb-1">Remediation Steps</p>
                <pre className="text-sm whitespace-pre-wrap bg-muted/40 rounded-lg p-3 font-mono">{fixModal.fix}</pre>
              </div>
              <Button className="w-full" onClick={() => setFixModal(null)}>Close</Button>
            </CardContent>
          </Card>
        </div>
      )}
    </div>
  )
}
