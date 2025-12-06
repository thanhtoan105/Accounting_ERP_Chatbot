'use client'

import { useState, useEffect, useCallback } from 'react'
import { X, ChevronRight, ExternalLink, Paperclip, Loader2 } from 'lucide-react'
import { useTranslation } from 'react-i18next'

import { Button } from '@/components/ui/button'
import { Sheet, SheetContent, SheetHeader, SheetTitle } from '@/components/ui/sheet'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { Skeleton } from '@/components/ui/skeleton'
import { Badge } from '@/components/ui/badge'
import { Separator } from '@/components/ui/separator'
import { getVoucherById, getVoucherAttachments } from '@/services/voucher'
import type { VoucherDTO, VoucherLineDTO } from '@/types/voucher'
import type { VoucherAttachmentDTO } from '@/types/attachment'

interface VoucherDetailModalProps {
  open: boolean
  onClose: () => void
  voucherId: string | null
  breadcrumb?: {
    accountCode: string
    accountName: string
    amountType?: string
  }
}

function formatCurrency(value: number | undefined | null): string {
  if (value === undefined || value === null || value === 0) return '-'
  return new Intl.NumberFormat('vi-VN', {
    style: 'currency',
    currency: 'VND',
    minimumFractionDigits: 0,
    maximumFractionDigits: 0,
  }).format(value)
}

function formatDate(dateString: string | undefined | null): string {
  if (!dateString) return '-'
  return new Date(dateString).toLocaleDateString('vi-VN')
}

function getStatusVariant(status: string): 'default' | 'secondary' | 'destructive' | 'outline' {
  switch (status?.toUpperCase()) {
    case 'POSTED':
      return 'default'
    case 'DRAFT':
      return 'secondary'
    case 'DELETED':
      return 'destructive'
    default:
      return 'outline'
  }
}

export function VoucherDetailModal({
  open,
  onClose,
  voucherId,
  breadcrumb,
}: VoucherDetailModalProps) {
  const { t } = useTranslation()
  const [loading, setLoading] = useState(false)
  const [voucher, setVoucher] = useState<VoucherDTO | null>(null)
  const [attachments, setAttachments] = useState<VoucherAttachmentDTO[]>([])
  const [attachmentsLoading, setAttachmentsLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const loadVoucher = useCallback(async () => {
    if (!open || !voucherId) return

    try {
      setLoading(true)
      setError(null)
      const result = await getVoucherById(voucherId)
      setVoucher(result)
    } catch (err) {
      console.error('Failed to load voucher:', err)
      setError(t('errors.loadFailed'))
    } finally {
      setLoading(false)
    }
  }, [open, voucherId, t])

  const loadAttachments = useCallback(async () => {
    if (!open || !voucherId) return

    try {
      setAttachmentsLoading(true)
      const result = await getVoucherAttachments(voucherId)
      setAttachments(result)
    } catch (err) {
      console.error('Failed to load attachments:', err)
      // Non-critical - don't show error for attachments
    } finally {
      setAttachmentsLoading(false)
    }
  }, [open, voucherId])

  useEffect(() => {
    if (open && voucherId) {
      void loadVoucher()
      void loadAttachments()
    }
  }, [open, voucherId, loadVoucher, loadAttachments])

  useEffect(() => {
    if (!open) {
      setVoucher(null)
      setAttachments([])
      setError(null)
    }
  }, [open])

  const handleOpenFullPage = () => {
    window.open(`/accounting/vouchers/${voucherId}`, '_blank')
  }

  const totalDebit = voucher?.lines?.reduce((sum, line) => sum + (line.debit || 0), 0) || 0
  const totalCredit = voucher?.lines?.reduce((sum, line) => sum + (line.credit || 0), 0) || 0

  return (
    <Sheet open={open} onOpenChange={(isOpen) => !isOpen && onClose()}>
      <SheetContent side="right" className="w-[800px] sm:w-[900px] overflow-y-auto">
        {/* Breadcrumb */}
        {breadcrumb && (
          <div className="flex items-center gap-1 text-sm text-muted-foreground mb-2">
            <span>{t('trialBalance.title')}</span>
            <ChevronRight className="h-3 w-3" />
            <span>{breadcrumb.accountCode}</span>
            <ChevronRight className="h-3 w-3" />
            <span className="text-foreground font-medium">
              {voucher?.voucherNumber || t('common.loading')}
            </span>
          </div>
        )}

        <SheetHeader className="pb-4 border-b">
          <div className="flex items-center justify-between">
            <SheetTitle className="text-lg">
              {t('vouchers.detail.title', { defaultValue: 'Chi tiết chứng từ' })}
            </SheetTitle>
            <div className="flex items-center gap-2">
              <Button variant="outline" size="sm" onClick={handleOpenFullPage}>
                <ExternalLink className="h-4 w-4 mr-1" />
                {t('common.openFullPage', { defaultValue: 'Mở trang đầy đủ' })}
              </Button>
              <Button variant="ghost" size="icon" onClick={onClose}>
                <X className="h-4 w-4" />
              </Button>
            </div>
          </div>
        </SheetHeader>

        {loading ? (
          <div className="mt-4 space-y-4">
            <div className="grid grid-cols-2 gap-4">
              {[...Array(6)].map((_, i) => (
                <Skeleton key={i} className="h-8 w-full" />
              ))}
            </div>
            <Skeleton className="h-48 w-full" />
          </div>
        ) : error ? (
          <div className="mt-4 text-center text-destructive py-8">
            <p>{error}</p>
            <Button variant="outline" className="mt-4" onClick={loadVoucher}>
              {t('common.retry')}
            </Button>
          </div>
        ) : voucher ? (
          <div className="mt-4 space-y-6">
            {/* Voucher Summary */}
            <div className="grid grid-cols-2 md:grid-cols-3 gap-4 text-sm">
              <div>
                <span className="text-muted-foreground">{t('vouchers.voucherNumber')}:</span>
                <p className="font-medium">{voucher.voucherNumber}</p>
              </div>
              <div>
                <span className="text-muted-foreground">{t('vouchers.voucherDate')}:</span>
                <p className="font-medium">{formatDate(voucher.voucherDate)}</p>
              </div>
              <div>
                <span className="text-muted-foreground">{t('vouchers.status')}:</span>
                <div className="mt-1">
                  <Badge variant={getStatusVariant(voucher.status)}>
                    {voucher.statusDisplay || voucher.status}
                  </Badge>
                </div>
              </div>
              <div>
                <span className="text-muted-foreground">{t('vouchers.voucherType')}:</span>
                <p className="font-medium">{voucher.voucherTypeName || voucher.voucherType}</p>
              </div>
              <div>
                <span className="text-muted-foreground">{t('vouchers.period')}:</span>
                <p className="font-medium">{voucher.periodName || '-'}</p>
              </div>
              <div>
                <span className="text-muted-foreground">{t('vouchers.reference')}:</span>
                <p className="font-medium">{voucher.referenceNumber || '-'}</p>
              </div>
            </div>

            {voucher.description && (
              <div className="text-sm">
                <span className="text-muted-foreground">{t('vouchers.description')}:</span>
                <p className="mt-1">{voucher.description}</p>
              </div>
            )}

            <Separator />

            {/* GL Lines Table */}
            <div>
              <h3 className="text-sm font-medium mb-3">
                {t('vouchers.lines', { defaultValue: 'Bút toán' })}
              </h3>
              <div className="rounded-md border">
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead className="w-[100px]">{t('vouchers.accountCode')}</TableHead>
                      <TableHead>{t('vouchers.accountName')}</TableHead>
                      <TableHead>
                        {t('vouchers.lineDescription', { defaultValue: 'Diễn giải' })}
                      </TableHead>
                      <TableHead className="text-right w-[120px]">{t('vouchers.debit')}</TableHead>
                      <TableHead className="text-right w-[120px]">{t('vouchers.credit')}</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {voucher.lines?.map((line: VoucherLineDTO, index: number) => (
                      <TableRow key={line.id || index}>
                        <TableCell className="font-mono text-sm">{line.accountCode}</TableCell>
                        <TableCell>{line.accountName}</TableCell>
                        <TableCell
                          className="max-w-[200px] truncate"
                          title={line.description || ''}
                        >
                          {line.description || '-'}
                        </TableCell>
                        <TableCell className="text-right font-mono">
                          {formatCurrency(line.debit)}
                        </TableCell>
                        <TableCell className="text-right font-mono">
                          {formatCurrency(line.credit)}
                        </TableCell>
                      </TableRow>
                    ))}
                    {/* Totals row */}
                    <TableRow className="bg-muted/50 font-medium">
                      <TableCell colSpan={3} className="text-right">
                        {t('common.total', { defaultValue: 'Tổng cộng' })}:
                      </TableCell>
                      <TableCell className="text-right font-mono">
                        {formatCurrency(totalDebit)}
                      </TableCell>
                      <TableCell className="text-right font-mono">
                        {formatCurrency(totalCredit)}
                      </TableCell>
                    </TableRow>
                  </TableBody>
                </Table>
              </div>
            </div>

            {/* Attachments */}
            {(attachments.length > 0 || attachmentsLoading) && (
              <>
                <Separator />
                <div>
                  <h3 className="text-sm font-medium mb-3 flex items-center gap-2">
                    <Paperclip className="h-4 w-4" />
                    {t('vouchers.attachments', { defaultValue: 'Tài liệu đính kèm' })}
                    {attachments.length > 0 && (
                      <Badge variant="secondary">{attachments.length}</Badge>
                    )}
                  </h3>
                  {attachmentsLoading ? (
                    <Skeleton className="h-12 w-full" />
                  ) : (
                    <div className="space-y-2">
                      {attachments.map((att) => (
                        <div
                          key={att.id}
                          className="flex items-center justify-between p-2 rounded-md border hover:bg-muted/50"
                        >
                          <div className="flex items-center gap-2">
                            <Paperclip className="h-4 w-4 text-muted-foreground" />
                            <span className="text-sm">{att.fileName}</span>
                            <span className="text-xs text-muted-foreground">
                              ({(att.fileSize / 1024).toFixed(1)} KB)
                            </span>
                          </div>
                          <Button variant="ghost" size="sm" asChild>
                            <a
                              href={att.downloadUrl || att.signedUrl}
                              target="_blank"
                              rel="noopener noreferrer"
                            >
                              {t('common.download', { defaultValue: 'Tải xuống' })}
                            </a>
                          </Button>
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              </>
            )}

            {/* Audit Info */}
            <Separator />
            <div className="text-xs text-muted-foreground space-y-1">
              {voucher.createdByName && (
                <p>
                  {t('common.createdBy', { defaultValue: 'Người tạo' })}: {voucher.createdByName}
                  {voucher.createdAt && ` - ${formatDate(voucher.createdAt)}`}
                </p>
              )}
              {voucher.postedByName && (
                <p>
                  {t('vouchers.postedBy', { defaultValue: 'Người hạch toán' })}:{' '}
                  {voucher.postedByName}
                  {voucher.postedAt && ` - ${formatDate(voucher.postedAt)}`}
                </p>
              )}
            </div>
          </div>
        ) : null}
      </SheetContent>
    </Sheet>
  )
}
