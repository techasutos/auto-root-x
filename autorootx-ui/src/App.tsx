import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import AppLayout from './components/layout/AppLayout'
import DashboardPage from './pages/DashboardPage'
import LogsPage from './pages/LogsPage'
import ImageScanPage from './pages/ImageScanPage'
import OssPage from './pages/OssPage'
import AdminPluginsPage from './pages/AdminPluginsPage'
import SettingsPage from './pages/SettingsPage'
import ServiceNowPage from './pages/ServiceNowPage'
import AgentPage from './pages/AgentPage'

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route element={<AppLayout />}>
          <Route index element={<DashboardPage />} />
          <Route path="agent" element={<AgentPage />} />
          <Route path="logs" element={<LogsPage />} />
          <Route path="image" element={<ImageScanPage />} />
          <Route path="oss" element={<OssPage />} />
          <Route path="admin/plugins" element={<AdminPluginsPage />} />
          <Route path="admin/settings" element={<SettingsPage />} />
          <Route path="servicenow" element={<ServiceNowPage />} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Route>
      </Routes>
    </BrowserRouter>
  )
}
