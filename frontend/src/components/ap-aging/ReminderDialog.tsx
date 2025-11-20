'use client'

import { useState } from 'react'
import { Mail, Send, X } from 'lucide-react'
import { toast } from 'sonner'

import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { Badge } from '@/components/ui/badge'
import { sendReminder, sendBatchReminders } from '@/services/apAging'
import type { ReminderRequestDTO, BatchReminderRequestDTO } from '@/types/apAging'

interface ReminderDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  supplierId?: number
  billIds?: string[]
  supplierIds?: number[] // For batch reminders
  onSuccess?: () => void
}

export function ReminderDialog({
  open,
  onOpenChange,
  supplierId,
  billIds,
  supplierIds,
  onSuccess,
}: ReminderDialogProps) {
  const [recipients, setRecipients] = useState<string[]>([''])
  const [message, setMessage] = useState('')
  const [sending, setSending] = useState(false)

  const isBatch = supplierIds !== undefined && supplierIds.length > 0

  const handleAddRecipient = () => {
    setRecipients([...recipients, ''])
  }

  const handleRemoveRecipient = (index: number) => {
    setRecipients(recipients.filter((_, i) => i !== index))
  }

  const handleRecipientChange = (index: number, value: string) => {
    const newRecipients = [...recipients]
    newRecipients[index] = value
    setRecipients(newRecipients)
  }

  const handleSend = async () => {
    const validRecipients = recipients.filter((r) => r.trim().length > 0)
    if (validRecipients.length === 0) {
      toast.error('Please add at least one recipient')
      return
    }

    try {
      setSending(true)

      if (isBatch) {
        const request: BatchReminderRequestDTO = {
          supplierIds: supplierIds!,
          recipients: validRecipients,
          message: message.trim() || undefined,
        }
        const result = await sendBatchReminders(request)
        if (result.failedCount === 0) {
          toast.success(`Reminders sent to ${result.successCount} suppliers`)
          onSuccess?.()
          onOpenChange(false)
        } else {
          toast.warning(
            `Sent to ${result.successCount} suppliers, failed for ${result.failedCount}`,
          )
        }
      } else {
        const request: ReminderRequestDTO = {
          supplierId,
          billIds,
          recipients: validRecipients,
          message: message.trim() || undefined,
        }
        const result = await sendReminder(request)
        if (result.success) {
          toast.success('Reminder sent successfully')
          onSuccess?.()
          onOpenChange(false)
        } else {
          toast.error('Failed to send reminder', {
            description: result.message,
          })
        }
      }
    } catch (err: any) {
      const errorMessage = err?.response?.data?.message || err?.message || 'Failed to send reminder'
      toast.error('Failed to send reminder', {
        description: errorMessage,
      })
    } finally {
      setSending(false)
    }
  }

  const handleClose = () => {
    if (!sending) {
      setRecipients([''])
      setMessage('')
      onOpenChange(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={handleClose}>
      <DialogContent className="max-w-2xl">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <Mail className="h-5 w-5" />
            {isBatch ? 'Send Batch Reminder' : 'Send Reminder'}
          </DialogTitle>
          <DialogDescription>
            {isBatch
              ? `Send reminder to ${supplierIds?.length || 0} suppliers about overdue payables`
              : 'Send reminder about overdue payables'}
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-4">
          {/* Recipients */}
          <div className="space-y-2">
            <Label>Recipients *</Label>
            {recipients.map((recipient, index) => (
              <div key={index} className="flex items-center gap-2">
                <Input
                  type="email"
                  placeholder="email@example.com"
                  value={recipient}
                  onChange={(e) => handleRecipientChange(index, e.target.value)}
                />
                {recipients.length > 1 && (
                  <Button
                    variant="ghost"
                    size="icon"
                    onClick={() => handleRemoveRecipient(index)}
                  >
                    <X className="h-4 w-4" />
                  </Button>
                )}
              </div>
            ))}
            <Button variant="outline" size="sm" onClick={handleAddRecipient}>
              Add Recipient
            </Button>
          </div>

          {/* Message */}
          <div className="space-y-2">
            <Label>Message (Optional)</Label>
            <Textarea
              placeholder="Enter custom reminder message..."
              value={message}
              onChange={(e) => setMessage(e.target.value)}
              rows={4}
            />
            <p className="text-xs text-muted-foreground">
              If left empty, a default message with bill details will be generated.
            </p>
          </div>

          {/* Preview */}
          {isBatch && supplierIds && (
            <div className="space-y-2">
              <Label>Suppliers</Label>
              <div className="flex flex-wrap gap-2">
                {supplierIds.map((id) => (
                  <Badge key={id} variant="secondary">
                    Supplier #{id}
                  </Badge>
                ))}
              </div>
            </div>
          )}

          {billIds && billIds.length > 0 && (
            <div className="space-y-2">
              <Label>Bills</Label>
              <div className="flex flex-wrap gap-2">
                {billIds.map((id) => (
                  <Badge key={id} variant="secondary">
                    {id}
                  </Badge>
                ))}
              </div>
            </div>
          )}
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={handleClose} disabled={sending}>
            Cancel
          </Button>
          <Button onClick={handleSend} disabled={sending}>
            {sending ? (
              <>
                <Send className="mr-2 h-4 w-4 animate-spin" />
                Sending...
              </>
            ) : (
              <>
                <Send className="mr-2 h-4 w-4" />
                Send Reminder
              </>
            )}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

