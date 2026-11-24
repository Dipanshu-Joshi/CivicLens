import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { AppLayout } from '../components/AppLayout'
import { Badge, Button, Card, PageHeader, StatePanel } from '../components/ui'
import { errorMessage } from '../lib/api/errors'
import { listDepartments, verifyDepartment } from '../lib/api/client'
import { humanizeStatus } from '../lib/utils/format'

export default function AdminPage() {
  const queryClient = useQueryClient()
  const departments = useQuery({ queryKey: ['departments'], queryFn: listDepartments })

  const verify = useMutation({
    mutationFn: ({ id, decision }: { id: string; decision: 'VERIFIED' | 'REJECTED' }) =>
      verifyDepartment(id, decision),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['departments'] }),
        queryClient.invalidateQueries({ queryKey: ['me'] }),
      ])
    },
  })

  return (
    <AppLayout>
      <PageHeader
        eyebrow="Super admin"
        title="Department applications"
        description="Verification is enforced by Spring Boot role checks before department access is granted."
      />

      {verify.isError && (
        <div className="mb-5 rounded-xl border border-red-200 bg-red-50 px-3 py-2.5 text-sm text-red-700">
          {errorMessage(verify.error, 'Verification request failed')}
        </div>
      )}

      {departments.isPending && (
        <Card className="p-10 text-center text-sm text-stone-500">Loading applications…</Card>
      )}

      {departments.isError && (
        <StatePanel
          tone="error"
          title="Could not load department applications"
          description={errorMessage(departments.error, 'The service did not return departments.')}
          action={
            <Button variant="secondary" onClick={() => void departments.refetch()}>
              Try again
            </Button>
          }
        />
      )}

      {departments.isSuccess && departments.data.length === 0 && (
        <StatePanel icon="clipboard" title="No applications yet" description="New department applications will appear here." />
      )}

      <div className="grid gap-4">
        {departments.data?.map((department) => (
          <Card key={department.id} className="flex flex-col gap-4 p-5 sm:flex-row sm:items-center sm:justify-between">
            <div>
              <div className="flex flex-wrap items-center gap-2">
                <p className="font-semibold text-stone-900">{department.name}</p>
                <Badge tone={department.verificationStatus === 'VERIFIED' ? 'success' : 'warn'}>
                  {humanizeStatus(department.verificationStatus)}
                </Badge>
              </div>
              <p className="mt-1 font-mono text-xs text-stone-500">
                {department.departmentCode} · {department.officialEmail}
              </p>
              <p className="mt-1 text-xs text-stone-500">
                Type: {humanizeStatus(department.departmentType)}
                {department.city ? ` · ${department.city}` : ''}
              </p>
            </div>
            {department.verificationStatus === 'PENDING_VERIFICATION' && (
              <div className="flex gap-2">
                <Button
                  size="sm"
                  loading={verify.isPending}
                  onClick={() => verify.mutate({ id: department.id, decision: 'VERIFIED' })}
                >
                  Verify
                </Button>
                <Button
                  size="sm"
                  variant="danger"
                  loading={verify.isPending}
                  onClick={() => verify.mutate({ id: department.id, decision: 'REJECTED' })}
                >
                  Reject
                </Button>
              </div>
            )}
          </Card>
        ))}
      </div>
    </AppLayout>
  )
}
