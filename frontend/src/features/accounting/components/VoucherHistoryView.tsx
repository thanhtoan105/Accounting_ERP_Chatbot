import { useState, useEffect } from 'react'
import { useTranslation } from 'react-i18next'
import { format, parseISO } from 'date-fns'
import {
  History,
  ChevronDown,
  ChevronRight,
  Download,
  Search,
  User,
  Calendar,
  AlertCircle,
  CheckCircle,
  XCircle,
} from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Badge } from '@/components/ui/badge'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Skeleton } from '@/components/ui/skeleton'
import { toast } from 'sonner'
import { getVoucherHistory, exportVoucherHistory } from '@/services/voucher'
import type { VoucherHistoryEntryDTO } from '@/types/voucher'

interface VoucherHistoryViewProps {
  voucherId: string
}

export function VoucherHistoryView({ voucherId }: VoucherHistoryViewProps) {
  const { t } = useTranslation()
  const [history, setHistory] = useState<VoucherHistoryEntryDTO[]>([])
  const [loading, setLoading] = useState(true)
  const [expandedEntries, setExpandedEntries] = useState<Set<number>>(new Set())
  const [searchTerm, setSearchTerm] = useState('')
  const [actionFilter, setActionFilter] = useState<string>('all')
  const [userFilter, setUserFilter] = useState<string>('all')

  useEffect(() => {
    loadHistory()
  }, [voucherId])

  const loadHistory = async () => {
    try {
      setLoading(true)
      const response = await getVoucherHistory(voucherId)
      setHistory(response.history)
    } catch (error) {
      console.error('Failed to load voucher history:', error)
      toast.error(t('vouchers.failedToLoadHistory'))
    } finally {
      setLoading(false)
    }
  }

  const handleExport = async (format: 'json' | 'pdf') => {
    try {
      const blob = await exportVoucherHistory(voucherId, format)
      const url = window.URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = `voucher-history-${voucherId}.${format}`
      document.body.appendChild(a)
      a.click()
      document.body.removeChild(a)
      window.URL.revokeObjectURL(url)
      toast.success(`Exported successfully (${format.toUpperCase()})`)
    } catch (error) {
      console.error('Export failed:', error)
      toast.error('Cannot export voucher history')
    }
  }

  const toggleExpand = (entryId: number) => {
    const newExpanded = new Set(expandedEntries)
    if (newExpanded.has(entryId)) {
      newExpanded.delete(entryId)
    } else {
      newExpanded.add(entryId)
    }
    setExpandedEntries(newExpanded)
  }

  // Filter history entries
  const filteredHistory = history.filter((entry) => {
    const matchesSearch =
      searchTerm === '' ||
      entry.summary.toLowerCase().includes(searchTerm.toLowerCase()) ||
      entry.userEmail?.toLowerCase().includes(searchTerm.toLowerCase()) ||
      entry.action.toLowerCase().includes(searchTerm.toLowerCase())

    const matchesAction = actionFilter === 'all' || entry.action === actionFilter
    const matchesUser = userFilter === 'all' || entry.userEmail === userFilter

    return matchesSearch && matchesAction && matchesUser
  })

  // Get unique actions and users for filters
  const uniqueActions = Array.from(new Set(history.map((e) => e.action))).sort()
  const uniqueUsers = Array.from(
    new Set(history.map((e) => e.userEmail).filter((email): email is string => Boolean(email))),
  ).sort()

  const getActionBadgeVariant = (action: string) => {
    if (action.includes('CREATED')) return 'default'
    if (action.includes('POSTED')) return 'default'
    if (action.includes('UPDATED')) return 'secondary'
    if (action.includes('UNPOSTED')) return 'destructive'
    if (action.includes('REVERSED')) return 'destructive'
    if (action.includes('DELETED')) return 'destructive'
    return 'outline'
  }

  const getChangeTypeColor = (changeType: string) => {
    switch (changeType) {
      case 'ADDED':
        return 'bg-green-100 text-green-800 dark:bg-green-900 dark:text-green-200'
      case 'REMOVED':
        return 'bg-red-100 text-red-800 dark:bg-red-900 dark:text-red-200'
      case 'CHANGED':
        return 'bg-yellow-100 text-yellow-800 dark:bg-yellow-900 dark:text-yellow-200'
      default:
        return 'bg-gray-100 text-gray-800 dark:bg-gray-900 dark:text-gray-200'
    }
  }

  if (loading) {
    return (
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <History className="h-5 w-5" />
            {t('vouchers.voucherHistory')}
          </CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          {[1, 2, 3].map((i) => (
            <Skeleton key={i} className="h-20 w-full" />
          ))}
        </CardContent>
      </Card>
    )
  }

  return (
    <Card>
      <CardHeader>
        <div className="flex items-center justify-between">
          <CardTitle className="flex items-center gap-2">
            <History className="h-5 w-5" />
            {t('vouchers.voucherHistory')} ({filteredHistory.length})
          </CardTitle>
          <div className="flex gap-2">
            <Button variant="outline" size="sm" onClick={() => handleExport('json')}>
              <Download className="h-4 w-4 mr-2" />
              JSON
            </Button>
            <Button variant="outline" size="sm" onClick={() => handleExport('pdf')}>
              <Download className="h-4 w-4 mr-2" />
              PDF
            </Button>
          </div>
        </div>
      </CardHeader>
      <CardContent className="space-y-4">
        {/* Filters */}
        <div className="flex flex-col sm:flex-row gap-2">
          <div className="relative flex-1">
            <Search className="absolute left-3 top-1/2 transform -translate-y-1/2 h-4 w-4 text-muted-foreground" />
            <Input
              placeholder="Search..."
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              className="pl-9"
            />
          </div>
          <Select value={actionFilter} onValueChange={setActionFilter}>
            <SelectTrigger className="w-full sm:w-[180px]">
              <SelectValue placeholder={t('vouchers.allActions')} />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="all">{t('vouchers.allActions')}</SelectItem>
              {uniqueActions.map((action) => (
                <SelectItem key={action} value={action}>
                  {action.replace('VOUCHER_', '').replace('_', ' ')}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
          <Select value={userFilter} onValueChange={setUserFilter}>
            <SelectTrigger className="w-full sm:w-[180px]">
              <SelectValue placeholder={t('vouchers.allUsers')} />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="all">{t('vouchers.allUsers')}</SelectItem>
              {uniqueUsers.map((user) => (
                <SelectItem key={user} value={user}>
                  {user}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>

        {/* History entries */}
        {filteredHistory.length === 0 ? (
          <div className="text-center py-8 text-muted-foreground">{t('vouchers.noHistoryFound')}</div>
        ) : (
          <div className="space-y-3">
            {filteredHistory.map((entry) => {
              const isExpanded = expandedEntries.has(entry.id)
              const hasDiff = entry.diff && Object.keys(entry.diff).length > 0

              return (
                <div key={entry.id} className="border rounded-lg p-4 space-y-3">
                  <div className="flex items-start justify-between">
                    <div className="flex-1 space-y-1">
                      <div className="flex items-center gap-2">
                        <Button
                          variant="ghost"
                          size="sm"
                          className="h-6 w-6 p-0"
                          onClick={() => toggleExpand(entry.id)}
                        >
                          {isExpanded ? (
                            <ChevronDown className="h-4 w-4" />
                          ) : (
                            <ChevronRight className="h-4 w-4" />
                          )}
                        </Button>
                        <Badge variant={getActionBadgeVariant(entry.action)}>
                          {entry.action.replace('VOUCHER_', '').replace('_', ' ')}
                        </Badge>
                        {entry.success === false && (
                          <Badge variant="destructive">
                            <XCircle className="h-3 w-3 mr-1" />
                            Failed
                          </Badge>
                        )}
                        {entry.success === true && (
                          <Badge variant="default" className="bg-green-600">
                            <CheckCircle className="h-3 w-3 mr-1" />
                            Success
                          </Badge>
                        )}
                      </div>
                      <p className="text-sm font-medium">{entry.summary}</p>
                      <div className="flex items-center gap-4 text-xs text-muted-foreground">
                        <span className="flex items-center gap-1">
                          <Calendar className="h-3 w-3" />
                          {format(parseISO(entry.timestamp), 'dd/MM/yyyy HH:mm:ss')}
                        </span>
                        {entry.userEmail && (
                          <span className="flex items-center gap-1">
                            <User className="h-3 w-3" />
                            {entry.userEmail}
                          </span>
                        )}
                      </div>
                    </div>
                  </div>

                  {isExpanded && (
                    <div className="space-y-3 pt-2 border-t">
                      {/* Field-level diff */}
                      {hasDiff && (
                        <div className="space-y-2">
                          <h4 className="text-sm font-semibold">{t('vouchers.changeDetails')}:</h4>
                          <div className="space-y-1">
                            {Object.entries(entry.diff!).map(([field, diff]) => (
                              <div
                                key={field}
                                className={`p-2 rounded text-sm ${getChangeTypeColor(diff.changeType)}`}
                              >
                                <div className="font-medium">{field}:</div>
                                {diff.changeType === 'ADDED' && (
                                  <div className="mt-1">
                                    <span className="font-semibold">Added:</span>{' '}
                                    {String(diff.afterValue)}
                                  </div>
                                )}
                                {diff.changeType === 'REMOVED' && (
                                  <div className="mt-1">
                                    <span className="font-semibold">Removed:</span>{' '}
                                    {String(diff.beforeValue)}
                                  </div>
                                )}
                                {diff.changeType === 'CHANGED' && (
                                  <div className="mt-1 space-y-1">
                                    <div>
                                      <span className="font-semibold">From:</span>{' '}
                                      {String(diff.beforeValue)}
                                    </div>
                                    <div>
                                      <span className="font-semibold">To:</span>{' '}
                                      {String(diff.afterValue)}
                                    </div>
                                  </div>
                                )}
                              </div>
                            ))}
                          </div>
                        </div>
                      )}

                      {/* Additional metadata */}
                      <div className="grid grid-cols-2 gap-2 text-xs">
                        {entry.ipAddress && (
                          <div>
                            <span className="font-medium">IP:</span> {entry.ipAddress}
                          </div>
                        )}
                        {entry.userRole && (
                          <div>
                            <span className="font-medium">Role:</span> {entry.userRole}
                          </div>
                        )}
                        {entry.diffHash && (
                          <div className="col-span-2">
                            <span className="font-medium">Hash:</span>{' '}
                            <code className="text-xs">{entry.diffHash.substring(0, 16)}...</code>
                          </div>
                        )}
                        {entry.failureReason && (
                          <div className="col-span-2 text-red-600">
                            <AlertCircle className="h-3 w-3 inline mr-1" />
                            <span className="font-medium">Failure reason:</span>{' '}
                            {entry.failureReason}
                          </div>
                        )}
                      </div>
                    </div>
                  )}
                </div>
              )
            })}
          </div>
        )}
      </CardContent>
    </Card>
  )
}
