import type { ReactNode } from 'react'
import { Link, Navigate, Route, Routes } from 'react-router-dom'
import { RequireAuth, RequireRole } from './components/AuthGuards'
import { PublicLayout } from './components/PublicLayout'
import { Icon } from './components/Icon'
import AdminAuditPage from './pages/AdminAuditPage'
import AdminComplaintDetailPage from './pages/AdminComplaintDetailPage'
import AdminComplaintsPage from './pages/AdminComplaintsPage'
import AdminDashboard from './pages/AdminDashboard'
import AdminDepartmentsPage from './pages/AdminDepartmentsPage'
import AdminPlatformPage from './pages/AdminPlatformPage'
import AdminUsersPage from './pages/AdminUsersPage'
import CitizenDashboard from './pages/CitizenDashboard'
import ComplaintDetailPage from './pages/ComplaintDetailPage'
import ComplaintSubmittedPage from './pages/ComplaintSubmittedPage'
import DepartmentComplaintDetailPage from './pages/DepartmentComplaintDetailPage'
import DepartmentComplaintsPage from './pages/DepartmentComplaintsPage'
import DepartmentDashboard from './pages/DepartmentDashboard'
import DepartmentProfilePage from './pages/DepartmentProfilePage'
import ForgotPasswordPage from './pages/ForgotPasswordPage'
import LandingPage from './pages/LandingPage'
import LoginPage from './pages/LoginPage'
import MyComplaintsPage from './pages/MyComplaintsPage'
import NotificationsPage from './pages/NotificationsPage'
import ProfilePage from './pages/ProfilePage'
import RegisterPage from './pages/RegisterPage'
import ReportIssuePage from './pages/ReportIssuePage'
import ResetPasswordPage from './pages/ResetPasswordPage'

function ForbiddenPage() {
  return (
    <PublicLayout>
      <main className="mx-auto flex max-w-lg flex-col items-center px-5 py-24 text-center">
        <div className="flex h-14 w-14 items-center justify-center rounded-2xl bg-red-50 text-red-600">
          <Icon name="warning" className="h-6 w-6" />
        </div>
        <h1 className="mt-5 text-2xl font-semibold text-stone-900">Access denied</h1>
        <p className="mt-2 text-sm text-stone-600">
          Your account does not have permission to view this page.
        </p>
        <Link
          to="/"
          className="mt-6 inline-flex items-center gap-2 rounded-lg bg-brand-700 px-4 py-2.5 text-sm font-semibold text-white hover:bg-brand-800"
        >
          Back to home
          <Icon name="arrow-right" className="h-4 w-4" />
        </Link>
      </main>
    </PublicLayout>
  )
}

function CitizenRoute({ children }: { children: ReactNode }) {
  return (
    <RequireRole roles={['CITIZEN']}>
      {children}
    </RequireRole>
  )
}

function AdminRoute({ children }: { children: ReactNode }) {
  return <RequireRole roles={['SUPER_ADMIN']}>{children}</RequireRole>
}

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<LandingPage />} />
      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<RegisterPage />} />
      <Route path="/forgot-password" element={<ForgotPasswordPage />} />
      <Route path="/reset-password" element={<ResetPasswordPage />} />
      <Route path="/forbidden" element={<ForbiddenPage />} />
      <Route path="/app" element={<Navigate to="/dashboard" replace />} />

      <Route
        path="/dashboard"
        element={
          <CitizenRoute>
            <CitizenDashboard />
          </CitizenRoute>
        }
      />
      <Route
        path="/complaints/new"
        element={
          <CitizenRoute>
            <ReportIssuePage />
          </CitizenRoute>
        }
      />
      <Route
        path="/complaints"
        element={
          <CitizenRoute>
            <MyComplaintsPage />
          </CitizenRoute>
        }
      />
      <Route
        path="/complaints/:id/submitted"
        element={
          <CitizenRoute>
            <ComplaintSubmittedPage />
          </CitizenRoute>
        }
      />
      <Route
        path="/complaints/:id"
        element={
          <CitizenRoute>
            <ComplaintDetailPage />
          </CitizenRoute>
        }
      />
      <Route
        path="/department"
        element={
          <RequireRole roles={['DEPARTMENT_ADMIN']}>
            <DepartmentDashboard />
          </RequireRole>
        }
      />
      <Route
        path="/department/complaints"
        element={
          <RequireRole roles={['DEPARTMENT_ADMIN']}>
            <DepartmentComplaintsPage />
          </RequireRole>
        }
      />
      <Route
        path="/department/complaints/:id"
        element={
          <RequireRole roles={['DEPARTMENT_ADMIN']}>
            <DepartmentComplaintDetailPage />
          </RequireRole>
        }
      />
      <Route
        path="/department/profile"
        element={
          <RequireRole roles={['DEPARTMENT_ADMIN']}>
            <DepartmentProfilePage />
          </RequireRole>
        }
      />
      <Route path="/department/*" element={<Navigate to="/department" replace />} />
      <Route
        path="/notifications"
        element={
          <RequireAuth>
            <NotificationsPage />
          </RequireAuth>
        }
      />
      <Route
        path="/profile"
        element={
          <RequireAuth>
            <ProfilePage />
          </RequireAuth>
        }
      />
      <Route
        path="/admin"
        element={
          <AdminRoute>
            <AdminDashboard />
          </AdminRoute>
        }
      />
      <Route
        path="/admin/departments"
        element={
          <AdminRoute>
            <AdminDepartmentsPage />
          </AdminRoute>
        }
      />
      <Route
        path="/admin/users"
        element={
          <AdminRoute>
            <AdminUsersPage />
          </AdminRoute>
        }
      />
      <Route
        path="/admin/complaints"
        element={
          <AdminRoute>
            <AdminComplaintsPage />
          </AdminRoute>
        }
      />
      <Route
        path="/admin/complaints/:id"
        element={
          <AdminRoute>
            <AdminComplaintDetailPage />
          </AdminRoute>
        }
      />
      <Route
        path="/admin/platform"
        element={
          <AdminRoute>
            <AdminPlatformPage />
          </AdminRoute>
        }
      />
      <Route
        path="/admin/audit"
        element={
          <AdminRoute>
            <AdminAuditPage />
          </AdminRoute>
        }
      />
      <Route
        path="/admin/*"
        element={
          <AdminRoute>
            <Navigate to="/admin" replace />
          </AdminRoute>
        }
      />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
