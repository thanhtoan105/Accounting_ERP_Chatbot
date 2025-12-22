import { useCallback, useEffect, useState } from 'react'
import {
  Activity,
  AlertTriangle,
  CheckCircle,
  Clock,
  FileText,
  PlayCircle,
  RefreshCw,
  Shield,
  XCircle,
} from 'lucide-react'
import { format, formatDistanceToNow } from 'date-fns'
import { toast } from 'sonner'
import { useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'

import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { Progress } from '@/components/ui/progress'
import { Skeleton } from '@/components/ui/skeleton'
import {
  listIntegrityChecks,
  getLastIntegrityCheck,
  runIntegrityCheck,
  type IntegrityCheckResultDTO,
  type IntegrityCheckStatus,
  type IssueSeverity,
} from '../../services/cashAudit'
import { useAuth } from '@/hooks/useAuth'
import { EmbeddingStatusCard } from '@/features/admin'

function StatusIcon({ status }: { status?: IntegrityCheckStatus }) {
  switch (status) {
    case 'PASSED':
      return <CheckCircle className="h-5 w-5 text-green-500" />
    case 'FAILED':
      return <XCircle className="h-5 w-5 text-red-500" />
    case 'ERROR':
      return <AlertTriangle className="h-5 w-5 text-orange-500" />
    default:
      return <Clock className="h-5 w-5 text-gray-400" />
  }
}

function StatusBadge({ status }: { status?: IntegrityCheckStatus }) {
  const variant =
    status === 'PASSED' ? 'secondary' : status === 'FAILED' ? 'destructive' : 'default'
  return <Badge variant={variant}>{status || 'UNKNOWN'}</Badge>
}

function SeverityBadge({ severity }: { severity: IssueSeverity }) {
  const variant =
    severity === 'HIGH' ? 'destructive' : severity === 'MEDIUM' ? 'default' : 'outline'
  return <Badge variant={variant}>{severity}</Badge>
}

function formatDuration(ms: number): string {
  if (ms < 1000) return `${ms}ms`
  if (ms < 60000) return `${(ms / 1000).toFixed(1)}s`
  return `${(ms / 60000).toFixed(1)}min`
}

export function IntegrityDashboardPage() {
  const navigate = useNavigate()
  const { t } = useTranslation()
  const { user } = useAuth()
  const isAdmin = user?.role === 'admin' || user?.role === 'ADMIN'

  const [loading, setLoading] = useState(true)
  const [running, setRunning] = useState(false)
  const [lastCheck, setLastCheck] = useState<IntegrityCheckResultDTO | null>(null)
  const [checkHistory, setCheckHistory] = useState<IntegrityCheckResultDTO[]>([])

  const loadData = useCallback(async () => {
    try {
      setLoading(true)
      const [lastCheckResult, historyResult] = await Promise.all([
        getLastIntegrityCheck(),
        listIntegrityChecks(10),
      ])
      setLastCheck(lastCheckResult)
      setCheckHistory(historyResult)
    } catch (error) {
      toast.error(t('cashAudit.integrity.loadFailed'))
      console.error(error)
    } finally {
      setLoading(false)
    }
  }, [t])

  useEffect(() => {
    loadData()
  }, [loadData])

  const handleRunCheck = async () => {
    try {
      setRunning(true)
      toast.info(t('cashAudit.integrity.running'))
      const result = await runIntegrityCheck()
      setLastCheck(result)
      setCheckHistory((prev) => [result, ...prev.slice(0, 9)])
      if (result.passed) {
        toast.success(t('cashAudit.integrity.checkPassed'))
      } else {
        toast.warning(t('cashAudit.integrity.checkFailed'), {
          description: `${result.issueCount} ${t('cashAudit.integrity.issuesFound').toLowerCase()}`,
        })
      }
    } catch (error) {
      toast.error(t('cashAudit.integrity.runFailed'))
      console.error(error)
    } finally {
      setRunning(false)
    }
  }

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold tracking-tight flex items-center gap-2">
            <Shield className="h-6 w-6 text-primary" />
            {t('cashAudit.integrity.title')}
          </h1>
          <p className="text-muted-foreground">{t('cashAudit.integrity.subtitle')}</p>
        </div>
        <div className="flex gap-2">
          <Button variant="outline" onClick={() => navigate('/accounting/audit')}>
            <FileText className="mr-2 h-4 w-4" />
            {t('cashAudit.explorer.title')}
          </Button>
          <Button variant="outline" onClick={loadData} disabled={loading}>
            <RefreshCw className={`mr-2 h-4 w-4 ${loading ? 'animate-spin' : ''}`} />
            {t('common.refresh')}
          </Button>
          {isAdmin && (
            <Button onClick={handleRunCheck} disabled={running}>
              <PlayCircle className={`mr-2 h-4 w-4 ${running ? 'animate-pulse' : ''}`} />
              {t('cashAudit.integrity.runCheck')}
            </Button>
          )}
        </div>
      </div>

      {/* Last Check Status Card */}
      <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-4">
        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">
              {t('cashAudit.integrity.lastCheck')}
            </CardTitle>
            {loading ? <Skeleton className="h-5 w-5" /> : <StatusIcon status={lastCheck?.status} />}
          </CardHeader>
          <CardContent>
            {loading ? (
              <Skeleton className="h-8 w-24" />
            ) : lastCheck ? (
              <>
                <div className="text-2xl font-bold">
                  <StatusBadge status={lastCheck.status} />
                </div>
                <p className="text-xs text-muted-foreground mt-1">
                  {formatDistanceToNow(new Date(lastCheck.executedAt), { addSuffix: true })}
                </p>
              </>
            ) : (
              <p className="text-sm text-muted-foreground">{t('cashAudit.integrity.noChecks')}</p>
            )}
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">
              {t('cashAudit.integrity.issuesFound')}
            </CardTitle>
            <AlertTriangle className="h-4 w-4 text-muted-foreground" />
          </CardHeader>
          <CardContent>
            {loading ? (
              <Skeleton className="h-8 w-16" />
            ) : (
              <>
                <div className="text-2xl font-bold">{lastCheck?.issueCount ?? 0}</div>
                <p className="text-xs text-muted-foreground">
                  {lastCheck?.issues?.filter((i) => i.severity === 'HIGH').length || 0} high
                  severity
                </p>
              </>
            )}
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">
              {t('cashAudit.integrity.recordsChecked')}
            </CardTitle>
            <Activity className="h-4 w-4 text-muted-foreground" />
          </CardHeader>
          <CardContent>
            {loading ? (
              <Skeleton className="h-8 w-20" />
            ) : (
              <>
                <div className="text-2xl font-bold">
                  {lastCheck?.recordsChecked?.toLocaleString() ?? 0}
                </div>
                <p className="text-xs text-muted-foreground">{t('cashAudit.integrity.records')}</p>
              </>
            )}
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">
              {t('cashAudit.integrity.duration')}
            </CardTitle>
            <Clock className="h-4 w-4 text-muted-foreground" />
          </CardHeader>
          <CardContent>
            {loading ? (
              <Skeleton className="h-8 w-16" />
            ) : (
              <>
                <div className="text-2xl font-bold">
                  {lastCheck ? formatDuration(lastCheck.duration) : '-'}
                </div>
                <p className="text-xs text-muted-foreground">
                  {t('cashAudit.integrity.executionTime')}
                </p>
              </>
            )}
          </CardContent>
        </Card>
      </div>

      {/* RAG Embedding Status - for AI chatbot feature */}
      {isAdmin && (
        <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
          <EmbeddingStatusCard />
        </div>
      )}

      {/* Running Check Progress */}
      {running && (
        <Card>
          <CardContent className="pt-6">
            <div className="space-y-2">
              <div className="flex items-center justify-between">
                <span className="text-sm font-medium">{t('cashAudit.integrity.running')}</span>
                <RefreshCw className="h-4 w-4 animate-spin" />
              </div>
              <Progress value={undefined} className="h-2" />
            </div>
          </CardContent>
        </Card>
      )}

      {/* Issues from Last Check */}
      {lastCheck?.issues && lastCheck.issues.length > 0 && (
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <AlertTriangle className="h-5 w-5 text-yellow-500" />
              {t('cashAudit.integrity.detectedIssues')}
            </CardTitle>
            <CardDescription>
              {lastCheck.issueCount} {t('cashAudit.integrity.issuesFromLastCheck')}
            </CardDescription>
          </CardHeader>
          <CardContent>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>{t('cashAudit.integrity.severity')}</TableHead>
                  <TableHead>{t('cashAudit.integrity.issueType')}</TableHead>
                  <TableHead>{t('cashAudit.integrity.description')}</TableHead>
                  <TableHead>{t('cashAudit.integrity.entity')}</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {lastCheck.issues.map((issue, index) => (
                  <TableRow
                    key={index}
                    className={
                      issue.severity === 'HIGH'
                        ? 'bg-red-50 dark:bg-red-950/20'
                        : issue.severity === 'MEDIUM'
                          ? 'bg-yellow-50 dark:bg-yellow-950/20'
                          : ''
                    }
                  >
                    <TableCell>
                      <SeverityBadge severity={issue.severity} />
                    </TableCell>
                    <TableCell className="font-mono text-sm">{issue.type}</TableCell>
                    <TableCell>
                      <div>
                        <p>{issue.description}</p>
                        {issue.details && (
                          <p className="text-xs text-muted-foreground mt-1">{issue.details}</p>
                        )}
                      </div>
                    </TableCell>
                    <TableCell>
                      {issue.entityType && (
                        <div className="text-sm">
                          <span>{issue.entityType}</span>
                          {issue.entityId && (
                            <span className="text-xs text-muted-foreground block font-mono">
                              {issue.entityId.substring(0, 8)}...
                            </span>
                          )}
                        </div>
                      )}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </CardContent>
        </Card>
      )}

      {/* Check History */}
      <Card>
        <CardHeader>
          <CardTitle>{t('cashAudit.integrity.history')}</CardTitle>
          <CardDescription>{t('cashAudit.integrity.historyDescription')}</CardDescription>
        </CardHeader>
        <CardContent>
          {loading ? (
            <div className="space-y-2">
              {[1, 2, 3].map((i) => (
                <Skeleton key={i} className="h-12 w-full" />
              ))}
            </div>
          ) : checkHistory.length > 0 ? (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>{t('cashAudit.integrity.status.title')}</TableHead>
                  <TableHead>{t('cashAudit.integrity.checkType')}</TableHead>
                  <TableHead>{t('cashAudit.integrity.executedAt')}</TableHead>
                  <TableHead>{t('cashAudit.integrity.duration')}</TableHead>
                  <TableHead>{t('cashAudit.integrity.issuesFound')}</TableHead>
                  <TableHead>{t('cashAudit.integrity.recordsChecked')}</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {checkHistory.map((check) => (
                  <TableRow key={check.checkId}>
                    <TableCell>
                      <div className="flex items-center gap-2">
                        <StatusIcon status={check.status} />
                        <StatusBadge status={check.status} />
                      </div>
                    </TableCell>
                    <TableCell>
                      <Badge variant="outline">{check.checkType}</Badge>
                    </TableCell>
                    <TableCell>
                      <div>
                        <p>{format(new Date(check.executedAt), 'dd/MM/yyyy HH:mm')}</p>
                        <p className="text-xs text-muted-foreground">
                          {formatDistanceToNow(new Date(check.executedAt), { addSuffix: true })}
                        </p>
                      </div>
                    </TableCell>
                    <TableCell>{formatDuration(check.duration)}</TableCell>
                    <TableCell>
                      <Badge variant={check.issueCount > 0 ? 'destructive' : 'secondary'}>
                        {check.issueCount}
                      </Badge>
                    </TableCell>
                    <TableCell>{check.recordsChecked.toLocaleString()}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          ) : (
            <p className="text-center text-muted-foreground py-8">
              {t('cashAudit.integrity.noChecks')}
            </p>
          )}
        </CardContent>
      </Card>
    </div>
  )
}
