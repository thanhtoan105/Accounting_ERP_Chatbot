  import { useEffect, useMemo, useState } from 'react'
import { Loader2, RefreshCw, Search, ShieldCheck } from 'lucide-react'

import {
  getVoucherTemplateById,
  getVoucherTemplates,
} from '@/services/voucher'
import type {
  VoucherTemplateDTO,
  VoucherTemplateSummaryDTO,
} from '@/types/voucher'
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
        setError(err?.message || 'Cannot load voucher template list')
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
      setError(err?.message || 'Cannot view template')
    } finally {
      setPreviewLoading(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-4xl">
        <DialogHeader>
          <DialogTitle>Apply voucher template</DialogTitle>
          <DialogDescription>
            Select available template to automatically fill accounts and descriptions for accounting vouchers.
          </DialogDescription>
        </DialogHeader>

        <div className="flex flex-col gap-3 py-2">
          <div className="flex flex-wrap items-center gap-3">
            <div className="relative flex-1 min-w-[220px]">
              <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
              <Input
                placeholder="Search by name or template description..."
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
              Clear search
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
                  .catch((err) => setError(err?.message || 'Cannot load template list'))
                  .finally(() => setLoading(false))
              }}
            >
              <RefreshCw className="mr-2 h-4 w-4" />
                  Refresh
            </Button>
          </div>

          {error ? <p className="text-sm text-destructive">{error}</p> : null}

          <div className="grid gap-4 md:grid-cols-2">
            <div className="rounded-md border">
              <div className="flex items-center justify-between border-b px-4 py-2">
                <p className="text-sm font-semibold">Available templates ({filteredTemplates.length})</p>
                {loading ? <span className="text-xs text-muted-foreground">Loading…</span> : null}
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
                      No templates found.
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
                          {template.description || 'No description'}  
                        </p>
                        <div className="mt-2 flex flex-wrap items-center gap-2 text-xs">
                          {template.isActive ? (
                            <Badge variant="secondary">Active</Badge>
                          ) : (
                            <Badge variant="outline">Inactive</Badge>
                          )}
                          {template.firstLineDebitAccount ? (
                            <Badge variant="outline">
                              Debit {template.firstLineDebitAccount.code}
                            </Badge>
                          ) : null}
                          {template.firstLineCreditAccount ? (
                            <Badge variant="outline">
                              Credit {template.firstLineCreditAccount.code}
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
                <p className="text-sm font-semibold">View details</p>
                {previewLoading ? (
                  <span className="text-xs text-muted-foreground">Loading…</span>
                ) : null}
              </div>
              <div className="space-y-3 p-4 text-sm">
                {selectedTemplate ? (
                  <>
                    <div className="flex items-center justify-between gap-2">
                      <div>
                        <p className="text-base font-semibold">{selectedTemplate.name}</p>
                        <p className="text-xs text-muted-foreground">
                          {selectedTemplate.description || 'No description'}
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
                        Define line
                      </p>
                      <div className="space-y-3">
                        {selectedTemplate.lines.map((line) => (
                          <div
                            key={line.lineNumber}
                            className="rounded-md border p-3 text-xs leading-relaxed"
                          >
                            <p className="font-semibold">
                              Line {line.lineNumber}: Debit {line.debitAccountCode || '---'} / Credit{' '}
                              {line.creditAccountCode || '---'}
                            </p>
                            {line.defaultDescription ? (
                              <p className="text-muted-foreground">{line.defaultDescription}</p>
                            ) : null}
                            <div className="mt-1 flex flex-wrap gap-2">
                              {line.requiresCustomer ? (
                                <Badge variant="outline">Requires customer</Badge>
                              ) : null}
                              {line.requiresSupplier ? (
                                <Badge variant="outline">Requires supplier</Badge>
                              ) : null}
                              {line.requiresCostCenter ? (
                                  <Badge variant="outline">Requires cost center</Badge>
                              ) : null}
                              {line.lockAccounts ? (
                                <Badge variant="destructive">Lock accounts</Badge>
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
                          Applying...
                        </>
                      ) : (
                        'Apply this template'
                      )}
                    </Button>
                  </>
                ) : (
                  <div className="text-sm text-muted-foreground">
                      Select a template from the list on the left to view details and apply.
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
