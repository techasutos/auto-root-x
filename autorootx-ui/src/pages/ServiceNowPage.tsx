import { useState } from 'react'
import { Ticket, Send, CheckCircle, AlertCircle, Loader2, ExternalLink, Plus, Trash2 } from 'lucide-react'
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { createIncident } from '@/api'
import type { ServiceNowTicketRequest, ServiceNowTicketResponse } from '@/types'

const SEV_OPTIONS = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW']
const ANALYZER_OPTIONS = ['LOGS', 'IMAGE', 'OSS', 'GENERIC']

const SEV_VARIANT: Record<string, 'critical' | 'high' | 'medium' | 'low' | 'default'> = {
  CRITICAL: 'critical',
  HIGH: 'high',
  MEDIUM: 'medium',
  LOW: 'low',
}

interface TicketRecord extends ServiceNowTicketResponse {
  title: string
  severity: string
}

export default function ServiceNowPage() {
  const [form, setForm] = useState<ServiceNowTicketRequest>({
    title: '',
    description: '',
    severity: 'HIGH',
    analyzerType: 'GENERIC',
    analysisSummary: '',
    affectedComponent: '',
  })
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [tickets, setTickets] = useState<TicketRecord[]>([])

  function set(key: keyof ServiceNowTicketRequest, value: string) {
    setForm((prev) => ({ ...prev, [key]: value }))
  }

  async function submit(e: React.FormEvent) {
    e.preventDefault()
    setLoading(true)
    setError('')
    try {
      const resp = await createIncident(form)
      setTickets((prev) => [{ ...resp, title: form.title, severity: form.severity }, ...prev])
      // reset form keeping severity/type
      setForm((prev) => ({ ...prev, title: '', description: '', analysisSummary: '', affectedComponent: '' }))
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to create ticket')
    } finally {
      setLoading(false)
    }
  }

  function removeTicket(ticketId: string) {
    setTickets((prev) => prev.filter((t) => t.ticketId !== ticketId))
  }

  return (
    <div className="p-8 space-y-8">
      {/* Header */}
      <div>
        <h1 className="text-2xl font-bold flex items-center gap-2">
          <Ticket className="h-6 w-6 text-primary" />
          ServiceNow Incidents
        </h1>
        <p className="text-sm text-muted-foreground mt-1">
          Create incident tickets in ServiceNow from analysis findings. Configure
          <code className="mx-1 px-1.5 py-0.5 rounded bg-muted text-xs font-mono">servicenow.*</code>
          properties for production.
        </p>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-8">
        {/* Create form */}
        <div>
          <Card>
            <CardHeader>
              <CardTitle className="text-base flex items-center gap-2">
                <Plus className="h-4 w-4" />
                New Incident
              </CardTitle>
              <CardDescription>Fill in the details to raise a ServiceNow incident</CardDescription>
            </CardHeader>
            <CardContent>
              <form onSubmit={submit} className="space-y-4">
                {/* Title */}
                <div className="space-y-1.5">
                  <label className="text-sm font-medium">Title <span className="text-destructive">*</span></label>
                  <input
                    type="text"
                    required
                    value={form.title}
                    onChange={(e) => set('title', e.target.value)}
                    placeholder="Brief incident title"
                    className="w-full rounded-lg border border-input bg-muted/40 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-ring"
                  />
                </div>

                {/* Severity + Analyzer type */}
                <div className="grid grid-cols-2 gap-3">
                  <div className="space-y-1.5">
                    <label className="text-sm font-medium">Severity <span className="text-destructive">*</span></label>
                    <select
                      value={form.severity}
                      onChange={(e) => set('severity', e.target.value)}
                      className="w-full rounded-lg border border-input bg-muted/40 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-ring"
                    >
                      {SEV_OPTIONS.map((s) => <option key={s}>{s}</option>)}
                    </select>
                  </div>
                  <div className="space-y-1.5">
                    <label className="text-sm font-medium">Analyzer</label>
                    <select
                      value={form.analyzerType}
                      onChange={(e) => set('analyzerType', e.target.value)}
                      className="w-full rounded-lg border border-input bg-muted/40 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-ring"
                    >
                      {ANALYZER_OPTIONS.map((a) => <option key={a}>{a}</option>)}
                    </select>
                  </div>
                </div>

                {/* Affected Component */}
                <div className="space-y-1.5">
                  <label className="text-sm font-medium">Affected Component</label>
                  <input
                    type="text"
                    value={form.affectedComponent}
                    onChange={(e) => set('affectedComponent', e.target.value)}
                    placeholder="e.g. payment-service, auth-service"
                    className="w-full rounded-lg border border-input bg-muted/40 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-ring"
                  />
                </div>

                {/* Description */}
                <div className="space-y-1.5">
                  <label className="text-sm font-medium">Description <span className="text-destructive">*</span></label>
                  <textarea
                    required
                    rows={4}
                    value={form.description}
                    onChange={(e) => set('description', e.target.value)}
                    placeholder="Describe the issue, steps to reproduce, and business impact"
                    className="w-full rounded-lg border border-input bg-muted/40 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-ring resize-none"
                  />
                </div>

                {/* AI Analysis Summary */}
                <div className="space-y-1.5">
                  <label className="text-sm font-medium">AI Analysis Summary <span className="text-xs text-muted-foreground">(optional)</span></label>
                  <textarea
                    rows={3}
                    value={form.analysisSummary}
                    onChange={(e) => set('analysisSummary', e.target.value)}
                    placeholder="Paste the AI analysis output here to include in the ticket"
                    className="w-full rounded-lg border border-input bg-muted/40 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-ring resize-none"
                  />
                </div>

                {error && (
                  <div className="flex items-center gap-2 rounded-lg border border-destructive/50 bg-destructive/10 px-3 py-2 text-sm text-destructive">
                    <AlertCircle className="h-4 w-4 shrink-0" />
                    {error}
                  </div>
                )}

                <Button type="submit" className="w-full" disabled={loading}>
                  {loading
                    ? <><Loader2 className="h-4 w-4 animate-spin" /> Creating ticket…</>
                    : <><Send className="h-4 w-4" /> Create Incident</>
                  }
                </Button>
              </form>
            </CardContent>
          </Card>
        </div>

        {/* Ticket history */}
        <div className="space-y-4">
          <h2 className="text-base font-semibold text-foreground">Created Tickets (this session)</h2>

          {tickets.length === 0 ? (
            <Card className="border-dashed">
              <CardContent className="py-12 flex flex-col items-center text-center gap-2">
                <Ticket className="h-10 w-10 text-muted-foreground/40" />
                <CardDescription>No tickets created yet. Fill the form to raise an incident.</CardDescription>
              </CardContent>
            </Card>
          ) : (
            tickets.map((t) => (
              <Card key={t.ticketId} className="relative">
                <CardContent className="pt-5 pb-4">
                  <div className="flex items-start justify-between gap-3">
                    <div className="space-y-1 flex-1 min-w-0">
                      <div className="flex items-center gap-2 flex-wrap">
                        <CheckCircle className="h-4 w-4 text-green-400 shrink-0" />
                        <span className="font-semibold text-sm">{t.ticketId}</span>
                        <Badge variant={SEV_VARIANT[t.severity] ?? 'default'}>{t.severity}</Badge>
                        <Badge variant="secondary">{t.status}</Badge>
                      </div>
                      <p className="text-sm text-foreground truncate">{t.title}</p>
                      <p className="text-xs text-muted-foreground">{t.priority}</p>
                      {t.message && (
                        <p className="text-xs text-muted-foreground italic">{t.message}</p>
                      )}
                      <p className="text-xs text-muted-foreground">
                        Created: {new Date(t.createdAt).toLocaleString()}
                      </p>
                    </div>
                    <div className="flex flex-col gap-2 shrink-0">
                      <a
                        href={t.url}
                        target="_blank"
                        rel="noopener noreferrer"
                        className="inline-flex items-center gap-1 text-xs text-primary hover:underline"
                      >
                        <ExternalLink className="h-3 w-3" />
                        View
                      </a>
                      <button
                        onClick={() => removeTicket(t.ticketId)}
                        className="text-muted-foreground hover:text-destructive transition-colors"
                        title="Remove from list"
                      >
                        <Trash2 className="h-3.5 w-3.5" />
                      </button>
                    </div>
                  </div>
                </CardContent>
              </Card>
            ))
          )}
        </div>
      </div>
    </div>
  )
}
