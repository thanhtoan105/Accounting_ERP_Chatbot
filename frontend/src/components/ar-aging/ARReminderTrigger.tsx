'use client'

import { useState } from 'react'
import { Mail, Send, Loader2, AlertCircle } from 'lucide-react'
import { toast } from 'sonner'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from '@/components/ui/alert-dialog'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { arAgingApi } from '@/features/accounting/services/arAgingApi'

interface ARReminderTriggerProps {
  selectedCustomerIds?: number[]
  selectedInvoiceIds?: string[]
  onSuccess?: () => void
}

export function ARReminderTrigger({
  selectedCustomerIds = [],
  selectedInvoiceIds = [],
  onSuccess,
}: ARReminderTriggerProps) {
  const [sending, setSending] = useState(false)
  const [showConfirmDialog, setShowConfirmDialog] = useState(false)

  const hasSelection = selectedCustomerIds.length > 0 || selectedInvoiceIds.length > 0

  const handleSendReminders = async () => {
    try {
      setSending(true)
      setShowConfirmDialog(false)

      await arAgingApi.triggerReminders({
        customerIds: selectedCustomerIds.length > 0 ? selectedCustomerIds : undefined,
        invoiceIds: selectedInvoiceIds.length > 0 ? selectedInvoiceIds : undefined,
      })

      toast.success('Reminders sent successfully', {
        description: `Sent reminders to ${selectedCustomerIds.length > 0 ? `${selectedCustomerIds.length} customer(s)` : 'all overdue customers'}`,
      })

      onSuccess?.()
    } catch (err: any) {
      const message = err?.response?.data?.message || 'Failed to send reminders'
      toast.error('Error sending reminders', { description: message })
    } finally {
      setSending(false)
    }
  }

  return (
    <>
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <Mail className="h-5 w-5" />
            Send AR Reminders
          </CardTitle>
          <CardDescription>Manually trigger reminder emails for overdue invoices</CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          {/* Selection Summary */}
          <div className="space-y-2">
            <h4 className="text-sm font-semibold">Current Selection</h4>
            <div className="flex flex-wrap gap-2">
              {selectedCustomerIds.length > 0 && (
                <Badge variant="secondary">
                  {selectedCustomerIds.length} Customer{selectedCustomerIds.length !== 1 ? 's' : ''}
                </Badge>
              )}
              {selectedInvoiceIds.length > 0 && (
                <Badge variant="secondary">
                  {selectedInvoiceIds.length} Invoice{selectedInvoiceIds.length !== 1 ? 's' : ''}
                </Badge>
              )}
              {!hasSelection && <Badge variant="outline">All Overdue Customers</Badge>}
            </div>
          </div>

          {/* Info Alert */}
          <Alert>
            <AlertCircle className="h-4 w-4" />
            <AlertDescription>
              {hasSelection ? (
                <>
                  Reminders will be sent to the selected{' '}
                  {selectedCustomerIds.length > 0 ? 'customers' : 'invoices'}. Only overdue invoices
                  will be included in the reminders.
                </>
              ) : (
                <>
                  Reminders will be sent to <strong>all customers</strong> with overdue invoices.
                  This may send multiple emails.
                </>
              )}
            </AlertDescription>
          </Alert>

          {/* Email Preview Info */}
          <div className="rounded-lg border p-4 space-y-2">
            <h4 className="text-sm font-semibold">Email Content Preview</h4>
            <div className="text-sm text-muted-foreground space-y-1">
              <p>
                • <strong>Subject:</strong> Payment Reminder - [Company Name]
              </p>
              <p>
                • <strong>Content:</strong> Professional reminder with invoice details
              </p>
              <p>
                • <strong>Includes:</strong> Invoice table, amounts, due dates, days overdue
              </p>
              <p>
                • <strong>Format:</strong> HTML email with company branding
              </p>
            </div>
          </div>

          {/* Send Button */}
          <div className="flex justify-end pt-2">
            <Button onClick={() => setShowConfirmDialog(true)} disabled={sending} size="lg">
              {sending ? (
                <>
                  <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                  Sending...
                </>
              ) : (
                <>
                  <Send className="mr-2 h-4 w-4" />
                  Send Reminders
                </>
              )}
            </Button>
          </div>
        </CardContent>
      </Card>

      {/* Confirmation Dialog */}
      <AlertDialog open={showConfirmDialog} onOpenChange={setShowConfirmDialog}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Confirm Send Reminders</AlertDialogTitle>
            <AlertDialogDescription className="space-y-2">
              <p>Are you sure you want to send reminder emails?</p>
              <p className="font-semibold">
                {hasSelection ? (
                  <>
                    This will send reminders to{' '}
                    {selectedCustomerIds.length > 0
                      ? `${selectedCustomerIds.length} selected customer(s)`
                      : `${selectedInvoiceIds.length} selected invoice(s)`}
                    .
                  </>
                ) : (
                  <>
                    This will send reminders to{' '}
                    <span className="text-orange-600">ALL customers</span> with overdue invoices.
                  </>
                )}
              </p>
              <p className="text-sm text-muted-foreground">
                Emails will be sent immediately and cannot be undone.
              </p>
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Cancel</AlertDialogCancel>
            <AlertDialogAction onClick={handleSendReminders}>Yes, Send Reminders</AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </>
  )
}
