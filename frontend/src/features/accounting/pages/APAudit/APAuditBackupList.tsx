import { useCallback, useEffect, useState } from 'react'
import { Archive, Download, Plus, RefreshCw } from 'lucide-react'
import { toast } from 'sonner'
import { format } from 'date-fns'

import { Button } from '@/components/ui/button'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { Badge } from '@/components/ui/badge'
import { apAuditService } from '@/services/apAudit'
import { APAuditBackup } from '@/types/apAudit'

export function APAuditBackupList() {
  const [loading, setLoading] = useState(false)
  const [backups, setBackups] = useState<APAuditBackup[]>([])
  const [creating, setCreating] = useState(false)

  const loadData = useCallback(async () => {
    try {
      setLoading(true)
      const response = await apAuditService.listBackups()
      setBackups(response.content)
    } catch (error) {
      toast.error('Failed to load backups')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    loadData()
  }, [loadData])

  const handleCreateBackup = async () => {
    try {
      setCreating(true)
      await apAuditService.createBackup()
      toast.success('Backup started successfully')
      loadData()
    } catch (error) {
      toast.error('Failed to create backup')
    } finally {
      setCreating(false)
    }
  }

  const handleDownload = async (backupId: number) => {
    try {
      const blob = await apAuditService.downloadBackup(backupId)
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = `backup-${backupId}.zip`
      document.body.appendChild(a)
      a.click()
      document.body.removeChild(a)
      URL.revokeObjectURL(url)
      toast.success('Backup downloaded')
    } catch (error) {
      toast.error('Failed to download backup')
    }
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold tracking-tight flex items-center gap-2">
            <Archive className="h-6 w-6 text-blue-500" />
            Disaster Recovery Backups
          </h1>
          <p className="text-muted-foreground">
            Manage automated and manual audit log archives.
          </p>
        </div>
        <div className="flex gap-2">
            <Button variant="outline" onClick={loadData}>
                <RefreshCw className={`mr-2 h-4 w-4 ${loading ? 'animate-spin' : ''}`} />
                Refresh
            </Button>
            <Button onClick={handleCreateBackup} disabled={creating}>
                <Plus className="mr-2 h-4 w-4" />
                Create Backup
            </Button>
        </div>
      </div>

      <div className="rounded-md border">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Backup Date</TableHead>
              <TableHead>Status</TableHead>
              <TableHead>Records</TableHead>
              <TableHead>Hash (SHA-256)</TableHead>
              <TableHead className="text-right">Actions</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {backups.length > 0 ? (
              backups.map((backup) => (
                <TableRow key={backup.id}>
                  <TableCell>
                    {format(new Date(backup.backupDate), 'dd/MM/yyyy HH:mm:ss')}
                  </TableCell>
                  <TableCell>
                    <Badge 
                        variant={backup.status === 'COMPLETED' ? 'default' : backup.status === 'FAILED' ? 'destructive' : 'outline'}
                    >
                        {backup.status}
                    </Badge>
                  </TableCell>
                  <TableCell>{backup.recordCount ?? '-'}</TableCell>
                  <TableCell className="font-mono text-xs text-muted-foreground max-w-[200px] truncate" title={backup.hash}>
                    {backup.hash}
                  </TableCell>
                  <TableCell className="text-right">
                    {backup.status === 'COMPLETED' && (
                        <Button variant="ghost" size="sm" onClick={() => handleDownload(backup.id)}>
                            <Download className="mr-2 h-4 w-4" />
                            Download
                        </Button>
                    )}
                  </TableCell>
                </TableRow>
              ))
            ) : (
              <TableRow>
                <TableCell colSpan={5} className="h-24 text-center text-muted-foreground">
                  No backups found.
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </div>
    </div>
  )
}
