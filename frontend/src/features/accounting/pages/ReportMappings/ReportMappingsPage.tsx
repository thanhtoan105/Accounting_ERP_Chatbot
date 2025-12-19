/**
 * ReportMappingsPage
 *
 * Admin page for managing TT200 account-to-line mappings.
 * Features:
 * - View current mappings by report type
 * - Edit account patterns with versioning
 * - View mapping history and audit trail
 * - Rollback to previous versions
 */

'use client'

import { useState, useCallback, useEffect } from 'react'
import { useTranslation } from 'react-i18next'
import { Settings2, RefreshCw, History, RotateCcw, Save, Edit2, AlertCircle } from 'lucide-react'
import { toast } from 'sonner'

import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { Button } from '@/components/ui/button'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { Card, CardContent, CardHeader } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogFooter,
} from '@/components/ui/dialog'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { ScrollArea } from '@/components/ui/scroll-area'
import { Skeleton } from '@/components/ui/skeleton'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { cn } from '@/lib/utils'

import { VoucherPageHeader } from '../../components/voucher-ui/VoucherPageHeader'
import {
  getMappings,
  updateMapping,
  getMappingHistory,
  rollbackMapping,
  type ReportType,
  type ReportMapping,
  type MappingVersion,
} from '../../services/reportMappings'

export function ReportMappingsPage() {
  const { t } = useTranslation()

  // State
  const [loading, setLoading] = useState(false)
  const [activeTab, setActiveTab] = useState<ReportType>('B01')
  const [mappings, setMappings] = useState<ReportMapping[]>([])

  // Edit dialog state
  const [editDialogOpen, setEditDialogOpen] = useState(false)
  const [editingMapping, setEditingMapping] = useState<ReportMapping | null>(null)
  const [editForm, setEditForm] = useState({
    accountPattern: '',
    operator: 'SUM' as 'SUM' | 'DIFF' | 'ABS',
    reason: '',
  })
  const [saving, setSaving] = useState(false)

  // History dialog state
  const [historyDialogOpen, setHistoryDialogOpen] = useState(false)
  const [historyLoading, setHistoryLoading] = useState(false)
  const [historyMapping, setHistoryMapping] = useState<ReportMapping | null>(null)
  const [historyVersions, setHistoryVersions] = useState<MappingVersion[]>([])

  // Rollback confirmation
  const [rollbackDialogOpen, setRollbackDialogOpen] = useState(false)
  const [rollbackVersion, setRollbackVersion] = useState<number | null>(null)
  const [rollbackLoading, setRollbackLoading] = useState(false)

  // Load mappings
  const loadMappings = useCallback(async () => {
    setLoading(true)
    try {
      const data = await getMappings(activeTab)
      setMappings(data)
    } catch (error) {
      toast.error(error instanceof Error ? error.message : t('reportMappings.errors.loadFailed'))
    } finally {
      setLoading(false)
    }
  }, [activeTab, t])

  useEffect(() => {
    void loadMappings()
  }, [loadMappings])

  // Open edit dialog
  const handleEdit = useCallback((mapping: ReportMapping) => {
    setEditingMapping(mapping)
    setEditForm({
      accountPattern: mapping.accountPattern,
      operator: mapping.operator,
      reason: '',
    })
    setEditDialogOpen(true)
  }, [])

  // Save mapping
  const handleSave = useCallback(async () => {
    if (!editingMapping) return

    if (!editForm.reason.trim()) {
      toast.error(t('reportMappings.validation.reasonRequired'))
      return
    }

    setSaving(true)
    try {
      await updateMapping(activeTab, editingMapping.lineCode, editForm)
      toast.success(t('reportMappings.success.updated'))
      setEditDialogOpen(false)
      void loadMappings()
    } catch (error) {
      toast.error(error instanceof Error ? error.message : t('reportMappings.errors.updateFailed'))
    } finally {
      setSaving(false)
    }
  }, [editingMapping, editForm, activeTab, t, loadMappings])

  // View history
  const handleViewHistory = useCallback(
    async (mapping: ReportMapping) => {
      setHistoryMapping(mapping)
      setHistoryDialogOpen(true)
      setHistoryLoading(true)

      try {
        const versions = await getMappingHistory(activeTab, mapping.lineCode)
        setHistoryVersions(versions)
      } catch (error) {
        toast.error(t('reportMappings.errors.historyFailed'))
      } finally {
        setHistoryLoading(false)
      }
    },
    [activeTab, t],
  )

  // Initiate rollback
  const handleInitiateRollback = useCallback((version: number) => {
    setRollbackVersion(version)
    setRollbackDialogOpen(true)
  }, [])

  // Confirm rollback
  const handleConfirmRollback = useCallback(async () => {
    if (!historyMapping || rollbackVersion === null) return

    setRollbackLoading(true)
    try {
      await rollbackMapping(activeTab, historyMapping.lineCode, rollbackVersion)
      toast.success(t('reportMappings.success.rolledBack'))
      setRollbackDialogOpen(false)
      setHistoryDialogOpen(false)
      void loadMappings()
    } catch (error) {
      toast.error(
        error instanceof Error ? error.message : t('reportMappings.errors.rollbackFailed'),
      )
    } finally {
      setRollbackLoading(false)
    }
  }, [historyMapping, rollbackVersion, activeTab, t, loadMappings])

  const reportNames: Record<ReportType, string> = {
    B01: t('reportMappings.reportTypes.balanceSheet'),
    B02: t('reportMappings.reportTypes.incomeStatement'),
    B03: t('reportMappings.reportTypes.cashFlow'),
    F01: t('reportMappings.reportTypes.detailedLedger'),
  }

  return (
    <div className="space-y-6 p-6">
      {/* Page Header */}
      <VoucherPageHeader
        icon={<Settings2 className="h-6 w-6" />}
        title={t('reportMappings.title')}
        subtitle={t('reportMappings.subtitle')}
        showRefresh
        onRefresh={loadMappings}
        refreshing={loading}
      />

      {/* Warning Alert */}
      <Alert>
        <AlertCircle className="h-4 w-4" />
        <AlertDescription>{t('reportMappings.warning')}</AlertDescription>
      </Alert>

      {/* Report Type Tabs */}
      <Card>
        <Tabs value={activeTab} onValueChange={(v) => setActiveTab(v as ReportType)}>
          <CardHeader className="pb-3">
            <TabsList className="grid w-full grid-cols-3 lg:w-auto lg:inline-grid">
              <TabsTrigger value="B01">B01 - {reportNames.B01}</TabsTrigger>
              <TabsTrigger value="B02">B02 - {reportNames.B02}</TabsTrigger>
              <TabsTrigger value="B03">B03 - {reportNames.B03}</TabsTrigger>
            </TabsList>
          </CardHeader>

          <CardContent>
            {loading ? (
              <div className="space-y-2">
                {Array.from({ length: 10 }).map((_, i) => (
                  <Skeleton key={i} className="h-12 w-full" />
                ))}
              </div>
            ) : (
              <div className="rounded-md border">
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead className="w-[80px]">{t('reportMappings.table.code')}</TableHead>
                      <TableHead>{t('reportMappings.table.lineName')}</TableHead>
                      <TableHead className="w-[200px]">
                        {t('reportMappings.table.accountPattern')}
                      </TableHead>
                      <TableHead className="w-[80px]">
                        {t('reportMappings.table.operator')}
                      </TableHead>
                      <TableHead className="w-[80px]">
                        {t('reportMappings.table.version')}
                      </TableHead>
                      <TableHead className="w-[120px]"></TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {mappings.map((mapping) => (
                      <TableRow
                        key={mapping.lineCode}
                        className={cn(
                          mapping.level === 1 && 'bg-muted/30 font-medium',
                          mapping.isCalculated && 'text-muted-foreground italic',
                        )}
                      >
                        <TableCell className="font-mono text-sm">{mapping.lineCode}</TableCell>
                        <TableCell style={{ paddingLeft: `${(mapping.level - 1) * 1.5 + 1}rem` }}>
                          {mapping.lineName}
                          {mapping.isCalculated && (
                            <Badge variant="outline" className="ml-2 text-xs">
                              {t('reportMappings.calculated')}
                            </Badge>
                          )}
                        </TableCell>
                        <TableCell className="font-mono text-sm text-muted-foreground">
                          {mapping.isCalculated ? mapping.formula : mapping.accountPattern}
                        </TableCell>
                        <TableCell>
                          <Badge variant="secondary">{mapping.operator}</Badge>
                        </TableCell>
                        <TableCell>
                          <Badge variant="outline">v{mapping.version}</Badge>
                        </TableCell>
                        <TableCell>
                          <div className="flex items-center gap-1">
                            {!mapping.isCalculated && (
                              <Button
                                variant="ghost"
                                size="icon"
                                onClick={() => handleEdit(mapping)}
                                title={t('common.edit')}
                              >
                                <Edit2 className="h-4 w-4" />
                              </Button>
                            )}
                            <Button
                              variant="ghost"
                              size="icon"
                              onClick={() => handleViewHistory(mapping)}
                              title={t('reportMappings.viewHistory')}
                            >
                              <History className="h-4 w-4" />
                            </Button>
                          </div>
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </div>
            )}
          </CardContent>
        </Tabs>
      </Card>

      {/* Edit Dialog */}
      <Dialog open={editDialogOpen} onOpenChange={setEditDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t('reportMappings.editDialog.title')}</DialogTitle>
            <DialogDescription>
              {t('reportMappings.editDialog.description', {
                lineCode: editingMapping?.lineCode,
                lineName: editingMapping?.lineName,
              })}
            </DialogDescription>
          </DialogHeader>

          <div className="space-y-4 py-4">
            <div className="space-y-2">
              <Label htmlFor="accountPattern">
                {t('reportMappings.editDialog.accountPattern')}
              </Label>
              <Textarea
                id="accountPattern"
                value={editForm.accountPattern}
                onChange={(e) =>
                  setEditForm((prev) => ({ ...prev, accountPattern: e.target.value }))
                }
                placeholder="111*,112*,113*"
                className="font-mono"
              />
              <p className="text-xs text-muted-foreground">
                {t('reportMappings.editDialog.accountPatternHelp')}
              </p>
            </div>

            <div className="space-y-2">
              <Label htmlFor="operator">{t('reportMappings.editDialog.operator')}</Label>
              <Select
                value={editForm.operator}
                onValueChange={(v) =>
                  setEditForm((prev) => ({ ...prev, operator: v as 'SUM' | 'DIFF' | 'ABS' }))
                }
              >
                <SelectTrigger id="operator">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="SUM">SUM - {t('reportMappings.operators.sum')}</SelectItem>
                  <SelectItem value="DIFF">DIFF - {t('reportMappings.operators.diff')}</SelectItem>
                  <SelectItem value="ABS">ABS - {t('reportMappings.operators.abs')}</SelectItem>
                </SelectContent>
              </Select>
            </div>

            <div className="space-y-2">
              <Label htmlFor="reason">{t('reportMappings.editDialog.reason')} *</Label>
              <Textarea
                id="reason"
                value={editForm.reason}
                onChange={(e) => setEditForm((prev) => ({ ...prev, reason: e.target.value }))}
                placeholder={t('reportMappings.editDialog.reasonPlaceholder')}
              />
              <p className="text-xs text-muted-foreground">
                {t('reportMappings.editDialog.reasonHelp')}
              </p>
            </div>
          </div>

          <DialogFooter>
            <Button variant="outline" onClick={() => setEditDialogOpen(false)}>
              {t('common.cancel')}
            </Button>
            <Button onClick={handleSave} disabled={saving}>
              {saving ? (
                <RefreshCw className="h-4 w-4 mr-2 animate-spin" />
              ) : (
                <Save className="h-4 w-4 mr-2" />
              )}
              {t('common.save')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* History Dialog */}
      <Dialog open={historyDialogOpen} onOpenChange={setHistoryDialogOpen}>
        <DialogContent className="max-w-2xl">
          <DialogHeader>
            <DialogTitle>{t('reportMappings.historyDialog.title')}</DialogTitle>
            <DialogDescription>
              {historyMapping?.lineCode} - {historyMapping?.lineName}
            </DialogDescription>
          </DialogHeader>

          <ScrollArea className="max-h-[400px]">
            {historyLoading ? (
              <div className="space-y-2">
                {Array.from({ length: 5 }).map((_, i) => (
                  <Skeleton key={i} className="h-16 w-full" />
                ))}
              </div>
            ) : historyVersions.length === 0 ? (
              <div className="text-center text-muted-foreground py-8">
                {t('reportMappings.historyDialog.noHistory')}
              </div>
            ) : (
              <div className="space-y-3">
                {historyVersions.map((version) => (
                  <div
                    key={version.version}
                    className={cn(
                      'p-4 rounded-lg border',
                      version.isCurrent && 'border-primary bg-primary/5',
                    )}
                  >
                    <div className="flex items-start justify-between">
                      <div className="space-y-1">
                        <div className="flex items-center gap-2">
                          <Badge variant={version.isCurrent ? 'default' : 'outline'}>
                            v{version.version}
                          </Badge>
                          {version.isCurrent && (
                            <Badge variant="secondary">
                              {t('reportMappings.historyDialog.current')}
                            </Badge>
                          )}
                        </div>
                        <div className="font-mono text-sm">{version.accountPattern}</div>
                        <div className="text-sm text-muted-foreground">{version.changeReason}</div>
                        <div className="text-xs text-muted-foreground">
                          {version.changedBy} -{' '}
                          {new Date(version.changedAt).toLocaleString('vi-VN')}
                        </div>
                      </div>
                      {!version.isCurrent && (
                        <Button
                          variant="outline"
                          size="sm"
                          onClick={() => handleInitiateRollback(version.version)}
                        >
                          <RotateCcw className="h-4 w-4 mr-1" />
                          {t('reportMappings.historyDialog.rollback')}
                        </Button>
                      )}
                    </div>
                  </div>
                ))}
              </div>
            )}
          </ScrollArea>
        </DialogContent>
      </Dialog>

      {/* Rollback Confirmation Dialog */}
      <Dialog open={rollbackDialogOpen} onOpenChange={setRollbackDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t('reportMappings.rollbackDialog.title')}</DialogTitle>
            <DialogDescription>
              {t('reportMappings.rollbackDialog.description', { version: rollbackVersion })}
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setRollbackDialogOpen(false)}>
              {t('common.cancel')}
            </Button>
            <Button onClick={handleConfirmRollback} disabled={rollbackLoading}>
              {rollbackLoading ? (
                <RefreshCw className="h-4 w-4 mr-2 animate-spin" />
              ) : (
                <RotateCcw className="h-4 w-4 mr-2" />
              )}
              {t('reportMappings.rollbackDialog.confirm')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}

export default ReportMappingsPage
