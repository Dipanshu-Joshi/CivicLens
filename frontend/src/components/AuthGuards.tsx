import type { ReactNode } from 'react'
import { Navigate } from 'react-router-dom'
import { useAuth } from '../lib/auth/AuthContext'
import type { Role } from '../lib/api/client'

function Loading() {
  return (
    <main className="flex min-h-screen items-center justify-center bg-[#f6f8f7] text-stone-500">
      <p className="text-sm">Checking your session…</p>
    </main>
  )
}

export function RequireAuth({ children }: { children: ReactNode }) {
  const { isAuthenticated, isLoading } = useAuth()
  if (isLoading) return <Loading />
  if (!isAuthenticated) return <Navigate to="/login" replace />
  return <>{children}</>
}

export function RequireRole({
  roles,
  children,
}: {
  roles: Role[]
  children: ReactNode
}) {
  const { user, isLoading } = useAuth()
  if (isLoading) return <Loading />
  if (!user) return <Navigate to="/login" replace />
  if (!roles.includes(user.role)) return <Navigate to="/forbidden" replace />
  return <>{children}</>
}
