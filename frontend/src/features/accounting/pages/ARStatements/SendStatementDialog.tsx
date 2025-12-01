'use client'

import { useState } from 'react'
import { Mail, Send } from 'lucide-react'
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
import { arStatementService } from '@/services/arStatement'
import { format } from 'date-fns'

interface SendStatementDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  customerId: number
  customerName: string
  format: 'SUMMARY' | 'DETAILED'
  asOfDate: Date
  onSuccess?: () => void
}

export function SendStatementDialog({
  open,
  onOpenChange,
  customerId,
  customerName,
  format,
  asOfDate,
  onSuccess,
}: SendStatementDialogProps) {
  const [email, setEmail] = useState('')
  const [sending, setSending] = useState(false)

  const handleSend = async () => {
    if (!email.trim()) {
      toast.error('Email address is required')
      return
    }

    // Basic email validation
    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
    if (!emailRegex.test(email)) {
      toast.error('Please enter a valid email address')
      return
    }

    try {
      setSending(true)
      await arStatementService.sendStatement({
        customerId,
        email: email.trim(),
        statementFormat: format,
        asOfDate: format(asOfDate, 'yyyy-MM-dd'),
      })
      toast.success('Statement sent successfully', {
        description: `Email sent to ${email}`,
      })
      setEmail('')
      onOpenChange(false)
      onSuccess?.()
    } catch (err: any) {
      toast.error('Failed to send statement', {
        description: err?.message || 'Unknown error',
      })
    } finally {
      setSending(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Send Statement to Customer</DialogTitle>
          <DialogDescription>Send the statement to {customerName} via email</DialogDescription>
        </DialogHeader>
        <div className="space-y-4 py-4">
          <div className="space-y-2">
            <Label htmlFor="email">Recipient Email</Label>
            <Input
              id="email"
              type="email"
              placeholder="customer@example.com"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              disabled={sending}
              data-testid="recipient-email-input"
            />
          </div>
          <div className="rounded-md bg-muted p-3 text-sm">
            <p className="font-medium mb-1">Statement Details:</p>
            <p className="text-muted-foreground">Format: {format}</p>
            <p className="text-muted-foreground">As of Date: {format(asOfDate, 'dd/MM/yyyy')}</p>
          </div>
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)} disabled={sending}>
            Cancel
          </Button>
          <Button
            onClick={handleSend}
            disabled={sending || !email.trim()}
            data-testid="confirm-send-button"
          >
            <Send className={`mr-2 h-4 w-4 ${sending ? 'animate-spin' : ''}`} />
            {sending ? 'Sending...' : 'Send'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
