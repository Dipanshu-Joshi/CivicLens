import axios from 'axios'

export interface ProblemFieldError {
  field: string
  message: string
}

/** RFC 7807-shaped payload returned by GlobalExceptionHandler. */
export interface ProblemDetail {
  type?: string
  title?: string
  status?: number
  detail?: string
  instance?: string
  correlationId?: string
  errors?: ProblemFieldError[]
}

export function problemFrom(error: unknown): ProblemDetail {
  if (!axios.isAxiosError(error)) {
    return { title: 'Unexpected error', detail: 'Something went wrong. Please try again.' }
  }

  const data = error.response?.data as Partial<ProblemDetail> | undefined
  if (data && typeof data === 'object') {
    return {
      type: data.type,
      title: data.title,
      status: data.status ?? error.response?.status,
      detail: data.detail,
      instance: data.instance,
      correlationId: data.correlationId,
      errors: Array.isArray(data.errors) ? data.errors : undefined,
    }
  }

  return {
    title: error.response ? 'Request failed' : 'Network error',
    status: error.response?.status,
    detail: error.response
      ? 'The service returned an unreadable response.'
      : 'Could not reach CivicSense. Check your connection and try again.',
  }
}

export function errorMessage(error: unknown, fallback = 'Something went wrong'): string {
  const problem = problemFrom(error)
  if (problem.errors?.length) {
    return problem.errors.map((item) => `${item.field}: ${item.message}`).join('; ')
  }
  return problem.detail ?? problem.title ?? fallback
}

export function isNotFound(error: unknown): boolean {
  return problemFrom(error).status === 404
}
