import { useState } from 'react'
import { Trash2, AlertTriangle } from 'lucide-react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { apAuditService } from '@/services/apAudit'

export function APAuditPurgeDialog() {
  const [open, setOpen] = useState(false)
  const [userId, setUserId] = useState('')
  const [beforeDate, setBeforeDate] = useState('')
  const [confirmText, setConfirmText] = useState('')
  const [loading, setLoading] = useState(false)

  const handlePurge = async () => {
    if (confirmText !== 'DELETE') {
      toast.error('Please type DELETE to confirm')
      return
    }
    if (!beforeDate) {
      toast.error('Before Date is required')
      return
    }

    try {
      setLoading(true)
      const result = await apAuditService.purgeAuditLogs({
        userId: userId ? Number(userId) : undefined,
        beforeDate,
      })
      toast.success(`Purged ${result.purgedCount} records successfully`)
      setOpen(false)
    } catch (error) {
      toast.error('Failed to purge audit logs')
    } finally {
      setLoading(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button variant="destructive">
          <Trash2 className="mr-2 h-4 w-4" />
          GDPR Purge
        </Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle className="text-destructive flex items-center gap-2">
            <AlertTriangle className="h-5 w-5" />
            GDPR Data Purge
          </DialogTitle>
          <DialogDescription>
            Permanently delete audit logs for compliance. This action cannot be undone.
            Ensure you have a backup before proceeding.
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-4 py-4">
          <div className="space-y-2">
            <Label>Purge logs before date</Label>
            <Input
              type="date"
              value={beforeDate}
              onChange={(e) => setBeforeDate(e.target.value)}
            />
          </div>
          <div className="space-y-2">
            <Label>Specific User ID (Optional)</Label>
            <Input
              placeholder="User ID"
              value={userId}
              onChange={(e) => setUserId(e.target.value)}
            />
          </div>
          <div className="space-y-2">
            <Label className="text-destructive font-semibold">
              Type "DELETE" to confirm
            </Label>
            <Input
              value={confirmText}
              onChange={(e) => setConfirmText(e.target.value)}
              className="border-destructive/50 focus-visible:ring-destructive"
            />
          </div>
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={() => setOpen(false)}>
            Cancel
          </Button>
          <Button 
            variant="destructive" 
            onClick={handlePurge} 
            disabled={loading || confirmText !== 'DELETE' || !beforeDate}
          >
            {loading ? 'Purging...' : 'Purge Data'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
