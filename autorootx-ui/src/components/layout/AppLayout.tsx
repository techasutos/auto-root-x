import { NavLink, Outlet } from 'react-router-dom'
import {
  LayoutDashboard,
  ScrollText,
  Shield,
  Package,
  Puzzle,
  Settings,
  Activity,
  ChevronRight,
} from 'lucide-react'
import { Ticket } from 'lucide-react'
import { cn } from '@/lib/utils'

const nav = [
  { label: 'Dashboard', to: '/', icon: LayoutDashboard, end: true },
  { label: 'separator', type: 'sep' as const },
  { label: 'Analyzers', type: 'heading' as const },
  { label: 'Logs Analyzer', to: '/logs', icon: ScrollText },
  { label: 'Image Scanner', to: '/image', icon: Shield },
  { label: 'OSS Scanner', to: '/oss', icon: Package },
  { label: 'separator', type: 'sep' as const },
  { label: 'Administration', type: 'heading' as const },
  { label: 'Plugins', to: '/admin/plugins', icon: Puzzle },
  { label: 'Settings', to: '/admin/settings', icon: Settings },
  { label: 'separator', type: 'sep' as const },
  { label: 'Integrations', type: 'heading' as const },
  { label: 'ServiceNow', to: '/servicenow', icon: Ticket },
]

export default function AppLayout() {
  return (
    <div className="flex h-screen overflow-hidden bg-background">
      {/* Sidebar */}
      <aside className="w-60 flex flex-col shrink-0 border-r border-border bg-card">
        {/* Logo */}
        <div className="flex items-center gap-2.5 px-4 py-5 border-b border-border">
          <Activity className="h-5 w-5 text-primary" />
          <span className="font-semibold text-sm tracking-wide text-foreground">AutoRoot-X</span>
        </div>

        {/* Nav */}
        <nav className="flex-1 overflow-y-auto px-2 py-3 space-y-0.5">
          {nav.map((item, i) => {
            if (item.type === 'sep') {
              return <div key={i} className="my-2 border-t border-border" />
            }
            if (item.type === 'heading') {
              return (
                <p key={i} className="px-3 pt-1 pb-1.5 text-xs font-semibold uppercase tracking-wider text-muted-foreground">
                  {item.label}
                </p>
              )
            }
            const Icon = item.icon!
            return (
              <NavLink
                key={item.to}
                to={item.to!}
                end={item.end}
                className={({ isActive }) =>
                  cn(
                    'flex items-center gap-2.5 rounded-lg px-3 py-2 text-sm font-medium transition-colors group',
                    isActive
                      ? 'bg-accent text-accent-foreground'
                      : 'text-muted-foreground hover:bg-accent/50 hover:text-foreground'
                  )
                }
              >
                <Icon className="h-4 w-4 shrink-0" />
                <span className="flex-1">{item.label}</span>
                <ChevronRight className="h-3 w-3 opacity-0 group-hover:opacity-40 transition-opacity" />
              </NavLink>
            )
          })}
        </nav>

        {/* Footer */}
        <div className="px-4 py-3 border-t border-border text-xs text-muted-foreground">
          v1.0 · GCP Powered
        </div>
      </aside>

      {/* Main content */}
      <main className="flex-1 overflow-y-auto">
        <Outlet />
      </main>
    </div>
  )
}
