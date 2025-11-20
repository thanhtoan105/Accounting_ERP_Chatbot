import { useEffect, useState } from 'react'
import { format } from 'date-fns'
import { Clock, CheckCircle, XCircle, Shield, AlertCircle } from 'lucide-react'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Skeleton } from '@/components/ui/skeleton'
import { getApprovalHistory, type ApprovalWorkflowDTO } from '@/services/purchaseBill'

interface ApprovalWorkflowHistoryProps {
  billId: string
}

function getStatusBadge(status: string) {
  switch (status) {
    case 'PENDING':
      return <Badge variant="outline" className="bg-yellow-50 text-yellow-700 border-yellow-200">
        <Clock className="mr-1 h-3 w-3" />
        Pending
      </Badge>
    case 'APPROVED':
      return <Badge variant="outline" className="bg-green-50 text-green-700 border-green-200">
        <CheckCircle className="mr-1 h-3 w-3" />
        Approved
      </Badge>
    case 'REJECTED':
      return <Badge variant="outline" className="bg-red-50 text-red-700 border-red-200">
        <XCircle className="mr-1 h-3 w-3" />
        Rejected
      </Badge>
    case 'AUTO_APPROVED':
      return <Badge variant="outline" className="bg-blue-50 text-blue-700 border-blue-200">
        <Shield className="mr-1 h-3 w-3" />
        Auto-Approved
      </Badge>
    default:
      return <Badge variant="outline">{status}</Badge>
  }
}

export function ApprovalWorkflowHistory({ billId }: ApprovalWorkflowHistoryProps) {
  const [workflows, setWorkflows] = useState<ApprovalWorkflowDTO[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    loadHistory()
  }, [billId])

  const loadHistory = async () => {
    setLoading(true)
    setError(null)
    try {
      const history = await getApprovalHistory(billId)
      setWorkflows(history)
    } catch (err: any) {
      setError(err?.message || 'Failed to load approval history')
    } finally {
      setLoading(false)
    }
  }

  if (loading) {
    return (
      <Card>
        <CardHeader>
          <CardTitle>Approval Workflow History</CardTitle>
        </CardHeader>
        <CardContent className="space-y-3">
          <Skeleton className="h-20 w-full" />
          <Skeleton className="h-20 w-full" />
        </CardContent>
      </Card>
    )
  }

  if (error) {
    return (
      <Card>
        <CardHeader>
          <CardTitle>Approval Workflow History</CardTitle>
        </CardHeader>
        <CardContent>
          <Alert variant="destructive">
            <AlertCircle className="h-4 w-4" />
            <AlertDescription>{error}</AlertDescription>
          </Alert>
        </CardContent>
      </Card>
    )
  }

  if (workflows.length === 0) {
    return (
      <Card>
        <CardHeader>
          <CardTitle>Approval Workflow History</CardTitle>
          <CardDescription>No approval workflow history for this bill</CardDescription>
        </CardHeader>
      </Card>
    )
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>Approval Workflow History</CardTitle>
        <CardDescription>
          Track all approval requests and decisions for this purchase bill
        </CardDescription>
      </CardHeader>
      <CardContent>
        <div className="space-y-4">
          {workflows.map((workflow) => (
            <div
              key={workflow.id}
              className="flex flex-col gap-3 p-4 border rounded-lg bg-muted/30"
            >
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2">
                  {getStatusBadge(workflow.status)}
                  {workflow.isSensitive && (
                    <Badge variant="outline" className="bg-orange-50 text-orange-700 border-orange-200">
                      <AlertCircle className="mr-1 h-3 w-3" />
                      Sensitive
                    </Badge>
                  )}
                </div>
                <div className="text-sm text-muted-foreground">
                  {format(new Date(workflow.createdAt), 'PPp')}
                </div>
              </div>

              <div className="grid grid-cols-2 gap-4 text-sm">
                <div>
                  <p className="font-medium text-muted-foreground">Bill Amount</p>
                  <p className="font-semibold">
                    {workflow.billAmount.toLocaleString('vi-VN', {
                      style: 'currency',
                      currency: 'VND',
                    })}
                  </p>
                </div>
                <div>
                  <p className="font-medium text-muted-foreground">Threshold</p>
                  <p className="font-semibold">
                    {workflow.thresholdAmount.toLocaleString('vi-VN', {
                      style: 'currency',
                      currency: 'VND',
                    })}
                  </p>
                </div>
              </div>

              <div className="grid grid-cols-2 gap-4 text-sm">
                <div>
                  <p className="font-medium text-muted-foreground">Submitted By</p>
                  <p>{workflow.createdByName || `User #${workflow.createdById}`}</p>
                </div>
                {workflow.approvedById && (
                  <div>
                    <p className="font-medium text-muted-foreground">
                      {workflow.status === 'APPROVED' || workflow.status === 'AUTO_APPROVED'
                        ? 'Approved By'
                        : 'Rejected By'}
                    </p>
                    <p>{workflow.approvedByName || `User #${workflow.approvedById}`}</p>
                  </div>
                )}
              </div>

              {workflow.approvalReason && (
                <div className="text-sm">
                  <p className="font-medium text-muted-foreground">Approval Reason</p>
                  <p className="mt-1 text-foreground">{workflow.approvalReason}</p>
                </div>
              )}

              {workflow.rejectionReason && (
                <div className="text-sm">
                  <p className="font-medium text-muted-foreground">Rejection Reason</p>
                  <p className="mt-1 text-destructive">{workflow.rejectionReason}</p>
                </div>
              )}

              {workflow.approvedAt && (
                <div className="text-xs text-muted-foreground">
                  {workflow.status === 'APPROVED' || workflow.status === 'AUTO_APPROVED'
                    ? 'Approved'
                    : 'Rejected'}{' '}
                  on {format(new Date(workflow.approvedAt), 'PPp')}
                </div>
              )}
            </div>
          ))}
        </div>
      </CardContent>
    </Card>
  )
}
