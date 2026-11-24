import {
  createContext,
  useContext,
  useMemo,
  useState,
  type ReactNode,
} from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  getMe,
  login as apiLogin,
  logout as apiLogout,
  registerCitizen as apiRegisterCitizen,
  registerDepartment as apiRegisterDepartment,
  setAccessToken,
  type LoginResponse,
  type MeResponse,
  type RegisterCitizenPayload,
  type RegisterDepartmentPayload,
  type VerificationStatus,
} from '../api/client'

interface AuthContextValue {
  user: MeResponse | null
  isLoading: boolean
  isAuthenticated: boolean
  login: (email: string, password: string) => Promise<LoginResponse>
  logout: () => Promise<void>
  registerCitizen: (payload: RegisterCitizenPayload) => Promise<void>
  registerDepartment: (payload: RegisterDepartmentPayload) => Promise<{
    departmentId: string
    verificationStatus: VerificationStatus
  }>
}

const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient()
  const [loggingOut, setLoggingOut] = useState(false)

  // On mount (and after reload) the 401 interceptor attempts a refresh using
  // the httpOnly cookie before giving up, so no token is persisted to storage.
  const meQuery = useQuery({
    queryKey: ['me'],
    queryFn: getMe,
    retry: false,
    staleTime: 60_000,
  })

  const loginMutation = useMutation({
    mutationFn: ({ email, password }: { email: string; password: string }) =>
      apiLogin(email, password),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['me'] })
    },
  })

  const logoutMutation = useMutation({
    mutationFn: apiLogout,
    onSuccess: async () => {
      setLoggingOut(true)
      setAccessToken(null)
      await queryClient.clear()
      setLoggingOut(false)
    },
  })

  const registerCitizenMutation = useMutation({
    mutationFn: apiRegisterCitizen,
  })

  const registerDepartmentMutation = useMutation({
    mutationFn: apiRegisterDepartment,
  })

  const loginAsync = loginMutation.mutateAsync
  const logoutAsync = logoutMutation.mutateAsync
  const registerCitizenAsync = registerCitizenMutation.mutateAsync
  const registerDepartmentAsync = registerDepartmentMutation.mutateAsync

  const value = useMemo<AuthContextValue>(
    () => ({
      user: meQuery.data ?? null,
      isLoading: meQuery.isPending || loggingOut,
      isAuthenticated: Boolean(meQuery.data),
      login: (email: string, password: string) => loginAsync({ email, password }),
      logout: logoutAsync,
      registerCitizen: registerCitizenAsync,
      registerDepartment: registerDepartmentAsync,
    }),
    [
      meQuery.data,
      meQuery.isPending,
      loggingOut,
      loginAsync,
      logoutAsync,
      registerCitizenAsync,
      registerDepartmentAsync,
    ],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

// eslint-disable-next-line react-refresh/only-export-components
export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext)
  if (!context) {
    throw new Error('useAuth must be used inside <AuthProvider>')
  }
  return context
}
