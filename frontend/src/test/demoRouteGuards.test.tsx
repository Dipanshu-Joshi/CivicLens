import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { RequireAuth, RequireRole } from '../components/AuthGuards'
import type { Role } from '../lib/api/client'

const mocks = vi.hoisted(() => ({ useAuth: vi.fn() }))

vi.mock('../lib/auth/AuthContext', () => ({ useAuth: mocks.useAuth }))

function renderGuardedRoute(role: Role | null) {
  mocks.useAuth.mockReturnValue({
    user: role ? { id: `user-${role}`, email: `demo-${role}@example.com`, role } : null,
    isAuthenticated: role !== null,
    isLoading: false,
    login: vi.fn(),
    logout: vi.fn(),
    registerCitizen: vi.fn(),
    registerDepartment: vi.fn(),
  })

  return render(
    <MemoryRouter initialEntries={['/department']}>
      <Routes>
        <Route path="/login" element={<p>Login page</p>} />
        <Route path="/forbidden" element={<p>Forbidden page</p>} />
        <Route
          path="/department"
          element={
            <RequireRole roles={['DEPARTMENT_ADMIN']}>
              <RequireAuth>
                <p>Department workspace</p>
              </RequireAuth>
            </RequireRole>
          }
        />
      </Routes>
    </MemoryRouter>,
  )
}

beforeEach(() => {
  mocks.useAuth.mockReset()
})

describe('demo route authorization', () => {
  it('allows only the matching demo role into a protected workspace', () => {
    renderGuardedRoute('DEPARTMENT_ADMIN')
    expect(screen.getByText('Department workspace')).toBeInTheDocument()
  })

  it('redirects a citizen demo session away from the department workspace', () => {
    renderGuardedRoute('CITIZEN')
    expect(screen.getByText('Forbidden page')).toBeInTheDocument()
    expect(screen.queryByText('Department workspace')).not.toBeInTheDocument()
  })

  it('redirects a signed-out demo session to login', () => {
    renderGuardedRoute(null)
    expect(screen.getByText('Login page')).toBeInTheDocument()
  })
})
