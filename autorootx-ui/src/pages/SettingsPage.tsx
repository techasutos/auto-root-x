import { useState, useEffect } from 'react'
import { Settings, Save, Loader2, Puzzle, CheckCircle, XCircle } from 'lucide-react'
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { getAdminPlugins, setPluginEnabled } from '@/api'
import type { AdminPluginStatus } from '@/types'

interface Setting { key: string; label: string; description: string; value: string }

const SETTINGS_KEY = 'autorootx_settings'

const DEFAULTS: Setting[] = [
  { key: 'gcp.project', label: 'GCP Project ID', description: 'Your Google Cloud project ID for Vertex AI and Cloud Logging', value: '' },
  { key: 'gcp.region', label: 'GCP Region', description: 'Region for Vertex AI (e.g. us-central1)', value: 'us-central1' },
  { key: 'vertex.model', label: 'Vertex AI Model', description: 'Gemini model to use for analysis', value: 'gemini-1.5-pro' },
  { key: 'cors.origins', label: 'CORS Allowed Origins', description: 'Comma-separated list of allowed frontend origins', value: 'http://localhost:5173' },
]

export default function SettingsPage() {
  const [settings, setSettings] = useState<Setting[]>(DEFAULTS)
  const [saved, setSaved] = useState(false)
  const [plugins, setPlugins] = useState<AdminPluginStatus[]>([])
  const [toggling, setToggling] = useState<string | null>(null)

  useEffect(() => {
    const stored = localStorage.getItem(SETTINGS_KEY)
    if (stored) {
      try {
        const parsed = JSON.parse(stored) as Record<string, string>
        setSettings(DEFAULTS.map((s) => ({ ...s, value: parsed[s.key] ?? s.value })))
      } catch {}
    }
    getAdminPlugins().then(setPlugins).catch(() => {})
  }, [])

  function update(key: string, value: string) {
    setSettings((prev) => prev.map((s) => s.key === key ? { ...s, value } : s))
  }

  function save() {
    const map: Record<string, string> = {}
    settings.forEach((s) => { map[s.key] = s.value })
    localStorage.setItem(SETTINGS_KEY, JSON.stringify(map))
    setSaved(true)
    setTimeout(() => setSaved(false), 2000)
  }

  async function toggle(id: string, enabled: boolean) {
    setToggling(id)
    try {
      await setPluginEnabled(id, enabled)
      setPlugins((prev) => prev.map((p) => p.id === id ? { ...p, enabled } : p))
    } finally {
      setToggling(null)
    }
  }

  return (
    <div className="p-8 space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold flex items-center gap-2">
            <Settings className="h-6 w-6 text-primary" />
            Settings
          </h1>
          <p className="text-sm text-muted-foreground mt-1">
            Configure GCP project, Vertex AI model, and CORS settings
          </p>
        </div>
        <Button onClick={save} variant={saved ? 'outline' : 'default'}>
          {saved ? '✓ Saved' : <><Save className="h-4 w-4" /> Save Settings</>}
        </Button>
      </div>

      <Card>
        <CardHeader>
          <CardTitle className="text-base">Application Configuration</CardTitle>
          <CardDescription>Settings are stored locally. Apply them to your application.properties for production.</CardDescription>
        </CardHeader>
        <CardContent className="space-y-5">
          {settings.map((s) => (
            <div key={s.key} className="space-y-1.5">
              <label className="text-sm font-medium">{s.label}</label>
              <p className="text-xs text-muted-foreground">{s.description}</p>
              <input
                type="text"
                value={s.value}
                onChange={(e) => update(s.key, e.target.value)}
                className="w-full rounded-lg border border-input bg-muted/40 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-ring"
              />
            </div>
          ))}
        </CardContent>
      </Card>

      {/* Plugin Enable/Disable */}
      <Card>
        <CardHeader>
          <div className="flex items-center justify-between">
            <div>
              <CardTitle className="text-base flex items-center gap-2">
                <Puzzle className="h-4 w-4 text-primary" />
                Analyzer Plugins
              </CardTitle>
              <CardDescription className="mt-1">
                Quickly enable or disable analyzers. Full management is available on the{' '}
                <a href="/admin/plugins" className="text-primary hover:underline">Plugins page</a>.
              </CardDescription>
            </div>
          </div>
        </CardHeader>
        <CardContent>
          {plugins.length === 0 ? (
            <div className="flex items-center gap-2 text-sm text-muted-foreground">
              <Loader2 className="h-4 w-4 animate-spin" /> Loading plugins…
            </div>
          ) : (
            <div className="space-y-2">
              {plugins.map((p) => (
                <div
                  key={p.id}
                  className="flex items-center justify-between rounded-lg border border-border bg-muted/20 px-4 py-3"
                >
                  <div className="flex items-center gap-3">
                    <div className={`h-2 w-2 rounded-full ${p.enabled ? 'bg-green-400' : 'bg-muted-foreground/40'}`} />
                    <div>
                      <p className="text-sm font-medium">{p.name}</p>
                      <p className="text-xs text-muted-foreground">{p.category} · {p.dynamic ? 'Dynamic' : 'Built-in'}</p>
                    </div>
                  </div>
                  <div className="flex items-center gap-3">
                    <Badge variant={p.enabled ? 'success' : 'outline'}>{p.enabled ? 'Enabled' : 'Disabled'}</Badge>
                    <Button
                      size="sm"
                      variant={p.enabled ? 'ghost' : 'outline'}
                      disabled={toggling === p.id}
                      onClick={() => toggle(p.id, !p.enabled)}
                      className="gap-1.5 text-xs"
                    >
                      {toggling === p.id
                        ? <Loader2 className="h-3 w-3 animate-spin" />
                        : p.enabled
                          ? <XCircle className="h-3 w-3 text-destructive" />
                          : <CheckCircle className="h-3 w-3 text-green-400" />
                      }
                      {p.enabled ? 'Disable' : 'Enable'}
                    </Button>
                  </div>
                </div>
              ))}
            </div>
          )}
        </CardContent>
      </Card>

      <Card className="border-dashed border-muted-foreground/30">
        <CardContent className="pt-5">
          <p className="text-xs text-muted-foreground font-semibold mb-2 uppercase">application.properties snippet</p>
          <pre className="text-xs font-mono bg-muted/40 rounded-lg p-3 text-muted-foreground overflow-x-auto">
            {settings.map((s) => `${s.key}=${s.value}`).join('\n')}
          </pre>
        </CardContent>
      </Card>
    </div>
  )
}
