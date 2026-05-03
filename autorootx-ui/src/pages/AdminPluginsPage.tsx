import { useState, useEffect } from 'react'
import { Puzzle, Plus, CheckCircle, XCircle, Loader2 } from 'lucide-react'
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { getAdminPlugins, setPluginEnabled, addPlugin } from '@/api'
import type { AdminPluginStatus, AddPluginRequest } from '@/types'

export default function AdminPluginsPage() {
  const [plugins, setPlugins] = useState<AdminPluginStatus[]>([])
  const [loading, setLoading] = useState(true)
  const [toggling, setToggling] = useState<string | null>(null)
  const [showAdd, setShowAdd] = useState(false)
  const [form, setForm] = useState<AddPluginRequest>({ id: '', name: '', category: '', inputs: [] })
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    getAdminPlugins().then(setPlugins).catch(() => {}).finally(() => setLoading(false))
  }, [])

  async function toggle(id: string, enabled: boolean) {
    setToggling(id)
    try {
      await setPluginEnabled(id, enabled)
      setPlugins((prev) => prev.map((p) => p.id === id ? { ...p, enabled } : p))
    } finally {
      setToggling(null)
    }
  }

  async function handleAdd(e: React.FormEvent) {
    e.preventDefault()
    setSaving(true)
    try {
      await addPlugin(form)
      const updated = await getAdminPlugins()
      setPlugins(updated)
      setShowAdd(false)
      setForm({ id: '', name: '', category: '', inputs: [] })
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="p-8 space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold flex items-center gap-2">
            <Puzzle className="h-6 w-6 text-primary" />
            Plugin Management
          </h1>
          <p className="text-sm text-muted-foreground mt-1">
            Enable or disable analyzers, and register dynamic plugins
          </p>
        </div>
        <Button onClick={() => setShowAdd(!showAdd)} variant="outline">
          <Plus className="h-4 w-4" />
          Add Plugin
        </Button>
      </div>

      {/* Add Plugin form */}
      {showAdd && (
        <Card className="border-primary/30">
          <CardHeader>
            <CardTitle className="text-base">Register New Plugin</CardTitle>
            <CardDescription>Dynamic plugins are forwarded to the AI engine for analysis</CardDescription>
          </CardHeader>
          <CardContent>
            <form onSubmit={handleAdd} className="grid grid-cols-2 gap-3">
              {(['id', 'name', 'category'] as const).map((field) => (
                <div key={field}>
                  <label className="text-xs text-muted-foreground mb-1 block capitalize">{field}</label>
                  <input
                    type="text"
                    required
                    value={form[field]}
                    onChange={(e) => setForm({ ...form, [field]: e.target.value })}
                    className="w-full rounded-lg border border-input bg-muted/40 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-ring"
                  />
                </div>
              ))}
              <div>
                <label className="text-xs text-muted-foreground mb-1 block">Inputs (comma-separated)</label>
                <input
                  type="text"
                  placeholder="e.g. image,tag"
                  onChange={(e) => setForm({ ...form, inputs: e.target.value.split(',').map((s) => s.trim()).filter(Boolean) })}
                  className="w-full rounded-lg border border-input bg-muted/40 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-ring"
                />
              </div>
              <div className="col-span-2 flex justify-end gap-2">
                <Button type="button" variant="ghost" onClick={() => setShowAdd(false)}>Cancel</Button>
                <Button type="submit" disabled={saving}>
                  {saving && <Loader2 className="h-4 w-4 animate-spin" />}
                  Register Plugin
                </Button>
              </div>
            </form>
          </CardContent>
        </Card>
      )}

      {/* Plugin list */}
      {loading ? (
        <div className="flex items-center gap-2 text-muted-foreground text-sm">
          <Loader2 className="h-4 w-4 animate-spin" /> Loading plugins…
        </div>
      ) : (
        <Card>
          <CardContent className="p-0">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-border bg-muted/20">
                  <th className="px-4 py-3 text-left text-xs text-muted-foreground font-medium uppercase">Plugin</th>
                  <th className="px-4 py-3 text-left text-xs text-muted-foreground font-medium uppercase">Category</th>
                  <th className="px-4 py-3 text-left text-xs text-muted-foreground font-medium uppercase">Inputs</th>
                  <th className="px-4 py-3 text-left text-xs text-muted-foreground font-medium uppercase">Type</th>
                  <th className="px-4 py-3 text-left text-xs text-muted-foreground font-medium uppercase">Status</th>
                  <th className="px-4 py-3 text-left text-xs text-muted-foreground font-medium uppercase">Toggle</th>
                </tr>
              </thead>
              <tbody>
                {plugins.map((p) => (
                  <tr key={p.id} className="border-b border-border/50 last:border-0">
                    <td className="px-4 py-3">
                      <p className="font-medium">{p.name}</p>
                      <p className="text-xs text-muted-foreground">{p.id}</p>
                    </td>
                    <td className="px-4 py-3"><Badge variant="secondary">{p.category}</Badge></td>
                    <td className="px-4 py-3 text-xs text-muted-foreground">{p.inputs.join(', ') || '—'}</td>
                    <td className="px-4 py-3 text-xs">{p.dynamic ? 'Dynamic' : 'Built-in'}</td>
                    <td className="px-4 py-3">
                      <Badge variant={p.enabled ? 'success' : 'outline'}>
                        {p.enabled ? 'Enabled' : 'Disabled'}
                      </Badge>
                    </td>
                    <td className="px-4 py-3">
                      <Button
                        size="sm"
                        variant={p.enabled ? 'ghost' : 'outline'}
                        disabled={toggling === p.id}
                        onClick={() => toggle(p.id, !p.enabled)}
                        className="gap-1.5"
                      >
                        {toggling === p.id
                          ? <Loader2 className="h-3 w-3 animate-spin" />
                          : p.enabled
                            ? <XCircle className="h-3 w-3 text-destructive" />
                            : <CheckCircle className="h-3 w-3 text-green-400" />
                        }
                        {p.enabled ? 'Disable' : 'Enable'}
                      </Button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </CardContent>
        </Card>
      )}
    </div>
  )
}
