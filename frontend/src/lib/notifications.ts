import type { Role } from './api/client'

/**
 * Notification deep links must stay inside the recipient's role surface.
 * The shared /notifications page is available to every authenticated role,
 * but complaint detail routes are role-specific.
 */
export function complaintPathForRole(role: Role | undefined, complaintId: string): string {
  if (role === 'DEPARTMENT_ADMIN') {
    return `/department/complaints/${complaintId}`
  }
  if (role === 'SUPER_ADMIN') {
    return `/admin/complaints/${complaintId}`
  }
  return `/complaints/${complaintId}`
}
