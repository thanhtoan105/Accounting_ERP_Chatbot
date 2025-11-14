import { useEffect, useMemo, useState } from 'react'
import { Loader2, RefreshCw, Search, ShieldCheck } from 'lucide-react'

import {
  getVoucherTemplateById,
  getVoucherTemplates,
  type VoucherTemplateDTO,
  type VoucherTemplateSummaryDTO,
} from '@/services/voucher'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { ScrollArea } from '@/components/ui/scroll-area'
import { Skeleton } from '@/components/ui/skeleton'
import { Separator } from '@/components/ui/separator'
import { cn } from '@/lib/utils'

interface VoucherTemplateSelectorProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  onTemplateApplied?: (template: VoucherTemplateDTO) => void
  isApplying?: boolean
}

export function VoucherTemplateSelector({
  open,
  onOpenChange,
  onTemplateApplied,
  isApplying = false,
}: VoucherTemplateSelectorProps) {
  const [templates, setTemplates] = useState<VoucherTemplateSummaryDTO[]>([])
  const [loading, setLoading] = useState(false)
  const [search, setSearch] = useState('')
  const [selectedTemplate, setSelectedTemplate] = useState<VoucherTemplateDTO | null>(null)
  const [previewLoading, setPreviewLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const filteredTemplates = useMemo(() => {
    if (!search.trim()) return templates
    const term = search.toLowerCase()
    return templates.filter(
      (template) =>
        template.name.toLowerCase().includes(term) ||
        template.description?.toLowerCase().includes(term),
    )
  }, [search, templates])

  useEffect(() => {
    if (!open) return
    let mounted = true
    async function loadTemplates() {
      try {
        setLoading(true)
        setError(null)
        const response = await getVoucherTemplates()
        if (mounted) {
          setTemplates(response)
        }
      } catch (err: any) {
        setError(err?.message || 'Không thể tải danh sách mẫu chứng từ')
      } finally {
        setLoading(false)
      }
    }
    loadTemplates()
    return () => {
      mounted = false
    }
  }, [open])

  async function handlePreview(template: VoucherTemplateSummaryDTO) {
    try {
      setPreviewLoading(true)
      const detailed = await getVoucherTemplateById(template.id)
      setSelectedTemplate(detailed)
    } catch (err: any) {
      setError(err?.message || 'Không thể xem mẫu')
    } finally {
      setPreviewLoading(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-4xl">
        <DialogHeader>
          <DialogTitle>Áp dụng mẫu chứng từ</DialogTitle>
          <DialogDescription>
            Chọn mẫu có sẵn để tự động điền tài khoản và diễn giải cho phiếu kế toán.
          </DialogDescription>
        </DialogHeader>

        <div className="flex flex-col gap-3 py-2">
          <div className="flex flex-wrap items-center gap-3">
            <div className="relative flex-1 min-w-[220px]">
              <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
              <Input
                placeholder="Tìm theo tên hoặc mô tả mẫu..."
                className="pl-9"
                value={search}
                onChange={(event) => setSearch(event.target.value)}
              />
            </div>
            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={() => {
                setSearch('')
                setSelectedTemplate(null)
              }}
            >
              Xóa tìm kiếm
            </Button>
            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={() => {
                setSelectedTemplate(null)
                setTemplates([])
                setSearch('')
                setError(null)
                setLoading(true)
                getVoucherTemplates()
                  .then(setTemplates)
                  .catch((err) => setError(err?.message || 'Không thể tải danh sách mẫu'))
                  .finally(() => setLoading(false))
              }}
            >
              <RefreshCw className="mr-2 h-4 w-4" />
              Làm mới
            </Button>
          </div>

          {error ? (
            <p className="text-sm text-destructive">{error}</p>
          ) : null}

          <div className="grid gap-4 md:grid-cols-2">
            <div className="rounded-md border">
              <div className="flex items-center justify-between border-b px-4 py-2">
                <p className="text-sm font-semibold">
                  Mẫu có sẵn ({filteredTemplates.length})
                </p>
                {loading ? <span className="text-xs text-muted-foreground">Đang tải…</span> : null}
              </div>
              <ScrollArea className="h-[360px]">
                <div className="divide-y">
                  {loading ? (
                    <div className="space-y-3 p-4">
                      {Array.from({ length: 5 }).map((_, idx) => (
                        <Skeleton key={idx} className="h-14 w-full" />
                      ))}
                    </div>
                  ) : filteredTemplates.length === 0 ? (
                    <div className="p-6 text-center text-sm text-muted-foreground">
                      Không tìm thấy mẫu nào phù hợp.
                    </div>
                  ) : (
                    filteredTemplates.map((template) => (
                      <button
                        key={template.id}
                        type="button"
                        className={cn(
                          'w-full px-4 py-3 text-left transition hover:bg-muted/60 focus-visible:outline-none',
                          selectedTemplate?.id === template.id && 'bg-primary/5',
                        )}
                        onClick={() => handlePreview(template)}
                      >
                        <p className="font-semibold text-sm">{template.name}</p>
                        <p className="text-xs text-muted-foreground line-clamp-2">
                          {template.description || 'Không có mô tả'}
                        </p>
                        <div className="mt-2 flex flex-wrap items-center gap-2 text-xs">
                          {template.isActive ? (
                            <Badge variant="secondary">Đang kích hoạt</Badge>
                          ) : (
                            <Badge variant="outline">Tạm ngưng</Badge>
                          )}
                          {template.firstLineDebitAccount ? (
                            <Badge variant="outline">
                              Nợ {template.firstLineDebitAccount.code}
                            </Badge>
                          ) : null}
                          {template.firstLineCreditAccount ? (
                            <Badge variant="outline">
                              Có {template.firstLineCreditAccount.code}
                            </Badge>
                          ) : null}
                        </div>
                      </button>
                    ))
                  )}
                </div>
              </ScrollArea>
            </div>

            <div className="rounded-md border">
              <div className="flex items-center justify-between border-b px-4 py-2">
                <p className="text-sm font-semibold">Xem chi tiết</p>
                {previewLoading ? (
                  <span className="text-xs text-muted-foreground">Đang tải…</span>
                ) : null}
              </div>
              <div className="space-y-3 p-4 text-sm">
                {selectedTemplate ? (
                  <>
                    <div className="flex items-center justify-between gap-2">
                      <div>
                        <p className="text-base font-semibold">{selectedTemplate.name}</p>
                        <p className="text-xs text-muted-foreground">
                          {selectedTemplate.description || 'Không có mô tả'}
                        </p>
                      </div>
                      {selectedTemplate.isActive ? (
                        <Badge>
                          <ShieldCheck className="mr-1 h-3 w-3" />
                          Active
                        </Badge>
                      ) : (
                        <Badge variant="outline">Inactive</Badge>
                      )}
                    </div>
                    <Separator />
                    <div className="space-y-2">
                      <p className="text-xs font-semibold uppercase text-muted-foreground">
                        Định nghĩa dòng
                      </p>
                      <div className="space-y-3">
                        {selectedTemplate.lines.map((line) => (
                          <div
                            key={line.lineNumber}
                            className="rounded-md border p-3 text-xs leading-relaxed"
                          >
                            <p className="font-semibold">
                              Dòng {line.lineNumber}: Nợ{' '}
                              {line.debitAccountCode || '---'} / Có{' '}
                              {line.creditAccountCode || '---'}
                            </p>
                            {line.defaultDescription ? (
                              <p className="text-muted-foreground">{line.defaultDescription}</p>
                            ) : null}
                            <div className="mt-1 flex flex-wrap gap-2">
                              {line.requiresCustomer ? <Badge variant="outline">Yêu cầu KH</Badge> : null}
                              {line.requiresSupplier ? <Badge variant="outline">Yêu cầu NCC</Badge> : null}
                              {line.requiresCostCenter ? (
                                <Badge variant="outline">Yêu cầu trung tâm CP</Badge>
                              ) : null}
                              {line.lockAccounts ? (
                                <Badge variant="destructive">Khóa tài khoản</Badge>
                              ) : null}
                            </div>
                          </div>
                        ))}
                      </div>
                    </div>
                    <Button
                      type="button"
                      className="w-full"
                      onClick={() => selectedTemplate && onTemplateApplied?.(selectedTemplate)}
                      disabled={!selectedTemplate || isApplying}
                    >
                      {isApplying ? (
                        <>
                          <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                          Đang áp dụng...
                        </>
                      ) : (
                        'Áp dụng mẫu này'
                      )}
                    </Button>
                  </>
                ) : (
                  <div className="text-sm text-muted-foreground">
                    Chọn một mẫu ở danh sách bên trái để xem chi tiết và áp dụng.
                  </div>
                )}
              </div>
            </div>
          </div>
        </div>
      </DialogContent>
    </Dialog>
  )
}

export default VoucherTemplateSelector

