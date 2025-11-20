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
import { Label } from '@/components/ui/label'
import { Input } from '@/components/ui/input'
import { Textarea } from '@/components/ui/textarea'
import { supplierStatementService } from '@/services/supplierStatement'

interface SendStatementDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  statementId: string
  supplierName?: string
  onSuccess?: () => void
}

export function SendStatementDialog({
  open,
  onOpenChange,
  statementId,
  supplierName,
  onSuccess,
}: SendStatementDialogProps) {
  const [recipients, setRecipients] = useState<string[]>([''])
  const [message, setMessage] = useState('')
  const [sending, setSending] = useState(false)

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

    // Validate email format
    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
    const invalidEmails = validRecipients.filter((email) => !emailRegex.test(email))
    if (invalidEmails.length > 0) {
      toast.error('Please enter valid email addresses')
      return
    }

    try {
      setSending(true)
      await supplierStatementService.sendStatement(statementId, validRecipients)
      toast.success('Statement sent successfully')
      onSuccess?.()
      handleClose()
    } catch (error: any) {
      toast.error('Failed to send statement', {
        description: error?.message || 'Unknown error',
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
            Send Statement to Supplier
          </DialogTitle>
          <DialogDescription>
            {supplierName
              ? `Send statement to ${supplierName}`
              : 'Send statement to supplier via email'}
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
                  disabled={sending}
                />
                {recipients.length > 1 && (
                  <Button
                    variant="ghost"
                    size="icon"
                    onClick={() => handleRemoveRecipient(index)}
                    disabled={sending}
                  >
                    <X className="h-4 w-4" />
                  </Button>
                )}
              </div>
            ))}
            <Button variant="outline" size="sm" onClick={handleAddRecipient} disabled={sending}>
              Add Recipient
            </Button>
          </div>

          {/* Message */}
          <div className="space-y-2">
            <Label>Message (Optional)</Label>
            <Textarea
              placeholder="Enter custom message for the email..."
              value={message}
              onChange={(e) => setMessage(e.target.value)}
              rows={4}
              disabled={sending}
            />
            <p className="text-xs text-muted-foreground">
              If left empty, a default message will be generated.
            </p>
          </div>
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
                Send Statement
              </>
            )}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
