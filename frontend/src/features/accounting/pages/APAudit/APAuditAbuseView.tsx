import { useCallback, useEffect, useState } from 'react'
import { ShieldAlert, RefreshCw, FileText } from 'lucide-react'
import { toast } from 'sonner'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { apAuditService } from '@/services/apAudit'
import type { AbuseDetectionResultDTO } from '@/types/apAudit'
import { format } from 'date-fns'

export function APAuditAbuseView() {
  const [loading, setLoading] = useState(false)
  const [detections, setDetections] = useState<AbuseDetectionResultDTO[]>([])

  const loadData = useCallback(async () => {
    try {
      setLoading(true)
      const data = await apAuditService.getAbuseDetections()
      setDetections(data)
    } catch (error) {
      toast.error('Failed to load abuse detections')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    loadData()
  }, [loadData])

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold tracking-tight flex items-center gap-2">
            <ShieldAlert className="h-6 w-6 text-orange-500" />
            Abuse Detection
          </h1>
          <p className="text-muted-foreground">
            Monitor suspicious activities and potential security threats.
          </p>
        </div>
        <Button variant="outline" onClick={loadData}>
          <RefreshCw className={`mr-2 h-4 w-4 ${loading ? 'animate-spin' : ''}`} />
          Refresh
        </Button>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Detected Patterns (Last 60 Minutes)</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="rounded-md border">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Severity</TableHead>
                  <TableHead>User</TableHead>
                  <TableHead>Pattern</TableHead>
                  <TableHead>Event Count</TableHead>
                  <TableHead>Time Range</TableHead>
                  <TableHead>Description</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {detections.length > 0 ? (
                  detections.map((item, index) => (
                    <TableRow key={index}>
                      <TableCell>
                        <Badge
                          variant={
                            item.severity === 'HIGH'
                              ? 'destructive'
                              : item.severity === 'MEDIUM'
                                ? 'default'
                                : 'secondary'
                          }
                        >
                          {item.severity}
                        </Badge>
                      </TableCell>
                      <TableCell>
                        <div className="flex flex-col">
                          <span className="font-medium">{item.userName}</span>
                          <span className="text-xs text-muted-foreground">{item.userEmail}</span>
                        </div>
                      </TableCell>
                      <TableCell>{item.patternType}</TableCell>
                      <TableCell>{item.eventCount}</TableCell>
                      <TableCell className="text-xs">
                        {format(new Date(item.firstEventTime), 'HH:mm:ss')} -{' '}
                        {format(new Date(item.lastEventTime), 'HH:mm:ss')}
                      </TableCell>
                      <TableCell className="max-w-md truncate" title={item.description}>
                        {item.description}
                      </TableCell>
                    </TableRow>
                  ))
                ) : (
                  <TableRow>
                    <TableCell colSpan={6} className="h-24 text-center text-muted-foreground">
                      No abuse patterns detected in the last hour.
                    </TableCell>
                  </TableRow>
                )}
              </TableBody>
            </Table>
          </div>
        </CardContent>
      </Card>
    </div>
  )
}
