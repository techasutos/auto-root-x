import { useState, useEffect } from 'react'
import { Shield, ScrollText, Package, Puzzle, Activity, CheckCircle, AlertTriangle, Sparkles } from 'lucide-react'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { getPlugins, getAdminPlugins } from '@/api'
import type { PluginMeta, AdminPluginStatus } from '@/types'
import { Link } from 'react-router-dom'

const iconMap: Record<string, React.ElementType> = {
  IMAGE: Shield,
  LOGS: ScrollText,
  OSS: Package,
}

const quickLinks = [
  { label: 'Agent Triage', desc: 'Let Gemini route the issue', to: '/agent', icon: Sparkles, color: 'text-cyan-400', bg: 'bg-cyan-500/10' },
  { label: 'Logs Analyzer', desc: 'Analyze GCP logs with AI', to: '/logs', icon: ScrollText, color: 'text-blue-400', bg: 'bg-blue-500/10' },
  { label: 'Image Scanner', desc: 'Scan container vulnerabilities', to: '/image', icon: Shield, color: 'text-red-400', bg: 'bg-red-500/10' },
  { label: 'OSS Scanner', desc: 'Check open-source dependencies', to: '/oss', icon: Package, color: 'text-purple-400', bg: 'bg-purple-500/10' },
  { label: 'Plugins', desc: 'Manage analyzer plugins', to: '/admin/plugins', icon: Puzzle, color: 'text-orange-400', bg: 'bg-orange-500/10' },
]

export default function DashboardPage() {
  const [plugins, setPlugins] = useState<PluginMeta[]>([])
  const [adminPlugins, setAdminPlugins] = useState<AdminPluginStatus[]>([])

  useEffect(() => {
    getPlugins().then(setPlugins).catch(() => {})
    getAdminPlugins().then(setAdminPlugins).catch(() => {})
  }, [])

  const enabledCount = adminPlugins.filter((p) => p.enabled).length

  return (
    <div className="p-8 space-y-8">
      {/* Header */}
      <div>
        <h1 className="text-2xl font-bold text-foreground flex items-center gap-2">
          <Activity className="h-6 w-6 text-primary" />
          Dashboard
        </h1>
        <p className="text-sm text-muted-foreground mt-1">
          Automated root-cause analysis powered by Vertex AI
        </p>
      </div>

      {/* Stats */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <Card>
          <CardContent className="pt-6">
            <div className="flex items-center gap-3">
              <div className="p-2 rounded-lg bg-primary/10">
                <Puzzle className="h-5 w-5 text-primary" />
              </div>
              <div>
                <p className="text-sm text-muted-foreground">Total Plugins</p>
                <p className="text-2xl font-bold">{plugins.length || adminPlugins.length}</p>
              </div>
            </div>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="pt-6">
            <div className="flex items-center gap-3">
              <div className="p-2 rounded-lg bg-green-500/10">
                <CheckCircle className="h-5 w-5 text-green-400" />
              </div>
              <div>
                <p className="text-sm text-muted-foreground">Active Analyzers</p>
                <p className="text-2xl font-bold">{enabledCount}</p>
              </div>
            </div>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="pt-6">
            <div className="flex items-center gap-3">
              <div className="p-2 rounded-lg bg-yellow-500/10">
                <AlertTriangle className="h-5 w-5 text-yellow-400" />
              </div>
              <div>
                <p className="text-sm text-muted-foreground">Disabled Analyzers</p>
                <p className="text-2xl font-bold">{adminPlugins.length - enabledCount}</p>
              </div>
            </div>
          </CardContent>
        </Card>
      </div>

      {/* Quick Actions */}
      <div>
        <h2 className="text-base font-semibold mb-4">Quick Actions</h2>
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
          {quickLinks.map((q) => {
            const Icon = q.icon
            return (
              <Link key={q.to} to={q.to}>
                <Card className="hover:border-primary/50 transition-colors cursor-pointer">
                  <CardContent className="pt-5 pb-5">
                    <div className="flex items-center gap-4">
                      <div className={`p-2.5 rounded-lg ${q.bg}`}>
                        <Icon className={`h-5 w-5 ${q.color}`} />
                      </div>
                      <div>
                        <p className="font-medium text-sm">{q.label}</p>
                        <p className="text-xs text-muted-foreground">{q.desc}</p>
                      </div>
                    </div>
                  </CardContent>
                </Card>
              </Link>
            )
          })}
        </div>
      </div>

      {/* Plugin status table */}
      {adminPlugins.length > 0 && (
        <div>
          <h2 className="text-base font-semibold mb-4">Plugin Status</h2>
          <Card>
            <CardContent className="p-0">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b border-border">
                    <th className="px-4 py-3 text-left text-xs text-muted-foreground font-medium uppercase">Name</th>
                    <th className="px-4 py-3 text-left text-xs text-muted-foreground font-medium uppercase">Category</th>
                    <th className="px-4 py-3 text-left text-xs text-muted-foreground font-medium uppercase">Status</th>
                    <th className="px-4 py-3 text-left text-xs text-muted-foreground font-medium uppercase">Type</th>
                  </tr>
                </thead>
                <tbody>
                  {adminPlugins.map((p) => {
                    const Icon = iconMap[p.id] ?? Puzzle
                    return (
                      <tr key={p.id} className="border-b border-border/50 last:border-0">
                        <td className="px-4 py-3 flex items-center gap-2">
                          <Icon className="h-4 w-4 text-muted-foreground" />
                          {p.name}
                        </td>
                        <td className="px-4 py-3">
                          <Badge variant="secondary">{p.category}</Badge>
                        </td>
                        <td className="px-4 py-3">
                          <Badge variant={p.enabled ? 'success' : 'outline'}>
                            {p.enabled ? 'Enabled' : 'Disabled'}
                          </Badge>
                        </td>
                        <td className="px-4 py-3 text-muted-foreground text-xs">
                          {p.dynamic ? 'Dynamic' : 'Built-in'}
                        </td>
                      </tr>
                    )
                  })}
                </tbody>
              </table>
            </CardContent>
          </Card>
        </div>
      )}
    </div>
  )
}
