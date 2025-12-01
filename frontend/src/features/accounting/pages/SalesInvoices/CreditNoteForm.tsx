'use client'

import { useCallback, useEffect, useMemo, useState } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { format, addDays, parseISO } from 'date-fns'
import {
  AlertCircle,
  CalendarIcon,
  Loader2,
  Save,
  ArrowLeft,
  FileText,
  RotateCcw,
  Link as LinkIcon,
} from 'lucide-react'
import { useParams, useNavigate, useSearchParams } from 'react-router-dom'
import { z } from 'zod'
import { toast } from 'sonner'

import {
  SalesInvoiceLineGrid,
  type SalesInvoiceLine,
} from '@/components/sales/SalesInvoiceLineGrid'
import { CustomerPicker } from '@/components/sales/CustomerPicker'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import {
  Form,
  FormControl,
  FormDescription,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from '@/components/ui/form'
import { Input } from '@/components/ui/input'
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover'
import { Skeleton } from '@/components/ui/skeleton'
import { Textarea } from '@/components/ui/textarea'
import { Calendar } from '@/components/ui/calendar'
import { cn } from '@/lib/utils'
import { useAuth } from '@/hooks/useAuth'
import { getCompanyId } from '@/utils/axios'
import type { SalesInvoiceDTO, SalesInvoiceCreateRequest, VatRate } from '@/types/salesInvoice'
import {
  createCreditNote,
  getSalesInvoiceById,
} from '@/services/salesInvoice'
import { getPostableAccounts } from '@/services/chartOfAccounts'
import type { ChartOfAccount } from '@/types/chartOfAccount'
import type { Customer } from '@/types/customer'
import type { AccountSummary } from '@/components/account/AccountPicker'

const formSchema = z.object({
  customerId: z.number({ message: 'Customer is required' }),
  invoiceNumber: z.string().min(1, 'Invoice number is required'),
  invoiceDate: z.string({ message: 'Invoice date is required' }),
  dueDate: z.string({ message: 'Due date is required' }),
  reference: z
    .string()
    .min(1, 'Reference is required')
    .max(100, 'Reference must be 100 characters or less'),
  description: z
    .string()
    .max(500, 'Description must be 500 characters or less')
    .optional()
    .or(z.literal('')),
})

type CreditNoteFormValues = z.infer<typeof formSchema>

const DEFAULT_PAYMENT_TERMS_DAYS = 30
const COMPANY_DEFAULT_VAT_RATE: VatRate = 'TEN'
const VAT_RATE_MAP: Record<VatRate, number> = {
  ZERO: 0,
  FIVE: 0.05,
  TEN: 0.1,
  EXEMPT: 0,
}

function mapAccountsToSummaries(accounts: ChartOfAccount[]): AccountSummary[] {
  return accounts
    .filter((account) => account.postable)
    .map<AccountSummary>((account) => ({
      id: String(account.id),
      code: account.code,
      name: account.name,
      balanceSide: account.normalSide?.toLowerCase().includes('debit')
        ? 'debit'
        : account.normalSide?.toLowerCase().includes('credit')
          ? 'credit'
          : 'both',
      group: account.type,
      isLeaf: account.postable,
    }))
}

function createInitialLines(): SalesInvoiceLine[] {
  return [
    {
      id: `line-0-${Date.now()}`,
      lineNumber: 1,
      account: null,
      description: '',
      quantity: null,
      unitPrice: null,
      amount: null,
      vatRate: 'ZERO',
      vatAmount: null,
      costCenterId: null,
      itemId: null,
      costCenter: null,
      item: null,
      status: 'clean',
    },
  ]
}

function calculateVAT(amount: number, rate: VatRate): number {
  const rateValue = rate === 'FIVE' ? 0.05 : rate === 'TEN' ? 0.1 : rate === 'EXEMPT' ? 0 : 0
  return amount * rateValue
}

function calculateDueDate(
  invoiceDate: string,
  paymentTermsDays: number = DEFAULT_PAYMENT_TERMS_DAYS,
): string {
  const date = parseISO(invoiceDate)
  const dueDate = addDays(date, paymentTermsDays)
  return format(dueDate, 'yyyy-MM-dd')
}

/**
 * Credit Note Form Component
 * Creates a credit note (negative invoice) that references an original invoice.
 * Line items are auto-populated from the original invoice with inverted amounts.
 */
export default function CreditNoteForm() {
  const params = useParams<{ originalInvoiceId: string }>()
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const originalInvoiceId = params.originalInvoiceId
  const { user } = useAuth()
  const companyId = getCompanyId()
  const [accounts, setAccounts] = useState<AccountSummary[]>([])
  const [selectedCustomer, setSelectedCustomer] = useState<Customer | null>(null)
  const [originalInvoice, setOriginalInvoice] = useState<SalesInvoiceDTO | null>(null)
  const [loadingOriginal, setLoadingOriginal] = useState(true)
  const [loadingAccounts, setLoadingAccounts] = useState(true)
  const [saving, setSaving] = useState(false)
  const [lines, setLines] = useState<SalesInvoiceLine[]>(createInitialLines())

  const form = useForm<CreditNoteFormValues>({
    resolver: zodResolver(formSchema),
    defaultValues: {
      customerId: 0,
      invoiceNumber: '',
      invoiceDate: format(new Date(), 'yyyy-MM-dd'),
      dueDate: calculateDueDate(format(new Date(), 'yyyy-MM-dd')),
      reference: '',
      description: '',
    },
  })

  // Load original invoice and populate form
  useEffect(() => {
    if (!originalInvoiceId) {
      toast.error('Original invoice ID is required')
      navigate('/sales-invoices')
      return
    }

    let mounted = true
    async function loadOriginalInvoice() {
      try {
        setLoadingOriginal(true)
        const invoice = await getSalesInvoiceById(originalInvoiceId)
        if (!mounted) return

        // Only allow credit notes for POSTED invoices (AC-VAT-004)
        if (invoice.status !== 'POSTED') {
          toast.error('Credit notes can only be created for POSTED invoices')
          navigate('/sales-invoices')
          return
        }

        setOriginalInvoice(invoice)

        // Populate form with original invoice data
        form.setValue('customerId', invoice.customerId)
        form.setValue('invoiceDate', format(new Date(), 'yyyy-MM-dd'))
        form.setValue('dueDate', calculateDueDate(format(new Date(), 'yyyy-MM-dd')))
        form.setValue(
          'reference',
          `Credit Note for ${invoice.invoiceNumber}`,
        )
        form.setValue(
          'description',
          `Credit note for invoice ${invoice.invoiceNumber} dated ${format(new Date(invoice.invoiceDate), 'dd/MM/yyyy')}`,
        )
        form.setValue('invoiceNumber', `CN-${invoice.invoiceNumber}`)

        // Set customer
        if (invoice.customerName) {
          setSelectedCustomer({
            id: invoice.customerId,
            name: invoice.customerName,
            code: invoice.customerCode || null,
            companyId: invoice.companyId,
            email: null,
            phone: null,
            address: null,
            taxCode: null,
            isActive: true,
            createdAt: '',
            updatedAt: '',
          })
        }

        // Invert line items from original invoice (AC-VAT-004)
        const invertedLines: SalesInvoiceLine[] = invoice.lines.map((line, index) => ({
          id: `line-${index}-${Date.now()}`,
          lineNumber: index + 1,
          account: null, // Will be set when accounts load
          description: line.description,
          quantity: line.quantity ? -Math.abs(line.quantity) : null, // Invert quantity
          unitPrice: line.unitPrice ? -Math.abs(line.unitPrice) : null, // Invert unit price
          amount: line.amount ? -Math.abs(line.amount) : null, // Invert amount
          vatRate: line.vatRate || 'ZERO',
          vatAmount: line.vatAmount ? -Math.abs(line.vatAmount) : null, // Invert VAT amount
          costCenterId: line.costCenterId ? String(line.costCenterId) : null,
          itemId: line.itemId ? String(line.itemId) : null,
          costCenter: null,
          item: null,
          status: 'dirty',
        }))
        setLines(invertedLines)
      } catch (err: any) {
        if (!mounted) return
        toast.error('Failed to load original invoice', {
          description: err?.message,
        })
        navigate('/sales-invoices')
      } finally {
        if (mounted) setLoadingOriginal(false)
      }
    }
    loadOriginalInvoice()
    return () => {
      mounted = false
    }
  }, [originalInvoiceId, form, navigate])

  // Load accounts
  useEffect(() => {
    let mounted = true
    async function loadAccounts() {
      try {
        const response = await getPostableAccounts()
        if (!mounted) return
        setAccounts(mapAccountsToSummaries(response))
      } catch (err: any) {
        toast.error('Failed to load accounts', {
          description: err?.message,
        })
      } finally {
        if (mounted) setLoadingAccounts(false)
      }
    }
    loadAccounts()
    return () => {
      mounted = false
    }
  }, [])

  // Map account IDs to AccountSummary objects for line items
  useEffect(() => {
    if (originalInvoice && accounts.length > 0 && lines.length > 0) {
      const updatedLines = lines.map((line) => {
        if (line.account) return line // Already has account

        // Find matching account from original invoice line
        const originalLine = originalInvoice.lines.find(
          (ol, idx) => idx === (line.lineNumber ? line.lineNumber - 1 : 0),
        )
        if (originalLine) {
          const account = accounts.find((a) => a.id === String(originalLine.accountId))
          if (account) {
            return { ...line, account }
          }
        }
        return line
      })
      setLines(updatedLines)
    }
  }, [originalInvoice, accounts, lines.length])

  const totalAmount = useMemo(() => {
    return lines.reduce((sum, line) => sum + (line.amount ?? 0), 0)
  }, [lines])

  const totalVAT = useMemo(() => {
    return lines.reduce((sum, line) => sum + (line.vatAmount ?? 0), 0)
  }, [lines])

  const handleSubmit = useCallback(
    async (values: CreditNoteFormValues) => {
      if (!originalInvoiceId) {
        toast.error('Original invoice ID is required')
        return
      }

      try {
        setSaving(true)

        const linePayload = lines.map((line, index) => ({
          lineNumber: index + 1,
          accountId: line.account ? Number(line.account.id) : 0,
          description: line.description,
          quantity: line.quantity ?? undefined,
          unitPrice: line.unitPrice ?? undefined,
          amount: line.amount ?? 0,
          vatRate: line.vatRate,
          vatAmount: line.vatAmount ?? 0,
          costCenterId: line.costCenterId ? Number(line.costCenterId) : undefined,
          itemId: line.itemId ? Number(line.itemId) : undefined,
        }))

        const request: SalesInvoiceCreateRequest = {
          customerId: values.customerId,
          invoiceNumber: values.invoiceNumber,
          invoiceDate: values.invoiceDate,
          dueDate: values.dueDate,
          reference: values.reference,
          description: values.description || null,
          lines: linePayload,
          originalInvoiceId: originalInvoiceId, // Link to original invoice (AC-VAT-004)
        }

        const creditNote = await createCreditNote(originalInvoiceId, request)
        toast.success('Credit note created successfully')
        navigate(`/sales-invoices/${creditNote.id}`)
      } catch (err: any) {
        toast.error('Failed to create credit note', {
          description: err?.message || 'An error occurred',
        })
      } finally {
        setSaving(false)
      }
    },
    [lines, originalInvoiceId, navigate],
  )

  if (loadingOriginal || loadingAccounts) {
    return (
      <div className="container mx-auto py-6 space-y-6">
        <Skeleton className="h-12 w-64" />
        <Skeleton className="h-96 w-full" />
      </div>
    )
  }

  if (!originalInvoice) {
    return null
  }

  return (
    <div className="container mx-auto py-6 space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold">Create Credit Note</h1>
          <p className="text-muted-foreground mt-1">
            Create a credit note (negative invoice) for the original invoice
          </p>
        </div>
        <Button variant="outline" onClick={() => navigate('/sales-invoices')} disabled={saving}>
          <ArrowLeft className="mr-2 h-4 w-4" />
          Back to List
        </Button>
      </div>

      {/* Original Invoice Reference (AC-VAT-004) */}
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <LinkIcon className="h-5 w-5" />
            Original Invoice Reference
          </CardTitle>
        </CardHeader>
        <CardContent>
          <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
            <div>
              <div className="text-sm text-muted-foreground">Invoice Number</div>
              <div className="font-semibold">{originalInvoice.invoiceNumber}</div>
            </div>
            <div>
              <div className="text-sm text-muted-foreground">Invoice Date</div>
              <div className="font-semibold">
                {format(new Date(originalInvoice.invoiceDate), 'dd/MM/yyyy')}
              </div>
            </div>
            <div>
              <div className="text-sm text-muted-foreground">Customer</div>
              <div className="font-semibold">{originalInvoice.customerName || '-'}</div>
            </div>
            <div>
              <div className="text-sm text-muted-foreground">Total Amount</div>
              <div className="font-semibold">
                {new Intl.NumberFormat('vi-VN', {
                  style: 'currency',
                  currency: 'VND',
                  minimumFractionDigits: 0,
                  maximumFractionDigits: 0,
                }).format(originalInvoice.totalAmount)}
              </div>
            </div>
          </div>
          <div className="mt-4">
            <Button
              variant="outline"
              size="sm"
              onClick={() => navigate(`/sales-invoices/${originalInvoice.id}`)}
            >
              <FileText className="mr-2 h-4 w-4" />
              View Original Invoice
            </Button>
          </div>
        </CardContent>
      </Card>

      <Form {...form}>
        <form onSubmit={form.handleSubmit(handleSubmit)} className="space-y-6">
          <Card>
            <CardHeader>
              <CardTitle>Credit Note Details</CardTitle>
            </CardHeader>
            <CardContent className="space-y-4">
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                <FormField
                  control={form.control}
                  name="customerId"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>Customer *</FormLabel>
                      <FormControl>
                        <CustomerPicker
                          value={selectedCustomer}
                          onChange={(customer) => {
                            setSelectedCustomer(customer)
                            field.onChange(customer?.id || 0)
                          }}
                          disabled={true} // Customer is locked to original invoice
                        />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />

                <FormField
                  control={form.control}
                  name="invoiceNumber"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>Credit Note Number *</FormLabel>
                      <FormControl>
                        <Input {...field} placeholder="CN-..." />
                      </FormControl>
                      <FormDescription>
                        Credit note number (auto-generated from original invoice)
                      </FormDescription>
                      <FormMessage />
                    </FormItem>
                  )}
                />

                <FormField
                  control={form.control}
                  name="invoiceDate"
                  render={({ field }) => (
                    <FormItem className="flex flex-col">
                      <FormLabel>Credit Note Date *</FormLabel>
                      <Popover>
                        <PopoverTrigger asChild>
                          <FormControl>
                            <Button
                              variant="outline"
                              className={cn(
                                'w-full pl-3 text-left font-normal',
                                !field.value && 'text-muted-foreground',
                              )}
                            >
                              {field.value ? (
                                format(parseISO(field.value), 'dd/MM/yyyy')
                              ) : (
                                <span>Pick a date</span>
                              )}
                              <CalendarIcon className="ml-auto h-4 w-4 opacity-50" />
                            </Button>
                          </FormControl>
                        </PopoverTrigger>
                        <PopoverContent className="w-auto p-0" align="start">
                          <Calendar
                            mode="single"
                            selected={field.value ? parseISO(field.value) : undefined}
                            onSelect={(date) => {
                              if (date) {
                                field.onChange(format(date, 'yyyy-MM-dd'))
                                form.setValue('dueDate', calculateDueDate(format(date, 'yyyy-MM-dd')))
                              }
                            }}
                            disabled={(date) => date > new Date()}
                            initialFocus
                          />
                        </PopoverContent>
                      </Popover>
                      <FormMessage />
                    </FormItem>
                  )}
                />

                <FormField
                  control={form.control}
                  name="dueDate"
                  render={({ field }) => (
                    <FormItem className="flex flex-col">
                      <FormLabel>Due Date *</FormLabel>
                      <Popover>
                        <PopoverTrigger asChild>
                          <FormControl>
                            <Button
                              variant="outline"
                              className={cn(
                                'w-full pl-3 text-left font-normal',
                                !field.value && 'text-muted-foreground',
                              )}
                            >
                              {field.value ? (
                                format(parseISO(field.value), 'dd/MM/yyyy')
                              ) : (
                                <span>Pick a date</span>
                              )}
                              <CalendarIcon className="ml-auto h-4 w-4 opacity-50" />
                            </Button>
                          </FormControl>
                        </PopoverTrigger>
                        <PopoverContent className="w-auto p-0" align="start">
                          <Calendar
                            mode="single"
                            selected={field.value ? parseISO(field.value) : undefined}
                            onSelect={(date) => {
                              if (date) {
                                field.onChange(format(date, 'yyyy-MM-dd'))
                              }
                            }}
                            disabled={(date) => date < new Date()}
                            initialFocus
                          />
                        </PopoverContent>
                      </Popover>
                      <FormMessage />
                    </FormItem>
                  )}
                />

                <FormField
                  control={form.control}
                  name="reference"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>Reference *</FormLabel>
                      <FormControl>
                        <Input {...field} placeholder="Reference" />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />

                <FormField
                  control={form.control}
                  name="description"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>Description</FormLabel>
                      <FormControl>
                        <Textarea {...field} placeholder="Description" rows={3} />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
              </div>
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle>Line Items (Inverted from Original Invoice)</CardTitle>
              <p className="text-sm text-muted-foreground">
                Line items are pre-populated with inverted amounts from the original invoice. You
                can edit quantities, amounts, and VAT rates as needed.
              </p>
            </CardHeader>
            <CardContent>
              <SalesInvoiceLineGrid
                accounts={accounts}
                lines={lines}
                onLinesChange={setLines}
                readOnly={false}
                loading={loadingAccounts}
                onCalculateVAT={calculateVAT}
                defaultVatRate={COMPANY_DEFAULT_VAT_RATE}
              />
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle>Summary</CardTitle>
            </CardHeader>
            <CardContent>
              <Alert>
                <AlertCircle className="h-4 w-4" />
                <AlertTitle>Credit Note Amounts</AlertTitle>
                <AlertDescription>
                  Credit note amounts are negative (inverted) to reverse the original invoice. The
                  GL splits will be automatically inverted: Cr 131 (reverses AR), Dr 5xx (reverses
                  revenue), Dr 3331 (reverses VAT).
                </AlertDescription>
              </Alert>
              <div className="flex justify-end gap-8 mt-4">
                <div className="text-right">
                  <div className="text-sm text-muted-foreground">Total Amount</div>
                  <div className="text-lg font-semibold">
                    {new Intl.NumberFormat('vi-VN', {
                      style: 'currency',
                      currency: 'VND',
                      minimumFractionDigits: 0,
                      maximumFractionDigits: 0,
                    }).format(totalAmount)}
                  </div>
                </div>
                <div className="text-right">
                  <div className="text-sm text-muted-foreground">Total VAT</div>
                  <div className="text-lg font-semibold">
                    {new Intl.NumberFormat('vi-VN', {
                      style: 'currency',
                      currency: 'VND',
                      minimumFractionDigits: 0,
                      maximumFractionDigits: 0,
                    }).format(totalVAT)}
                  </div>
                </div>
                <div className="text-right">
                  <div className="text-sm text-muted-foreground">Grand Total</div>
                  <div className="text-xl font-bold">
                    {new Intl.NumberFormat('vi-VN', {
                      style: 'currency',
                      currency: 'VND',
                      minimumFractionDigits: 0,
                      maximumFractionDigits: 0,
                    }).format(totalAmount + totalVAT)}
                  </div>
                </div>
              </div>
            </CardContent>
          </Card>

          <div className="flex justify-end gap-2">
            <Button
              type="button"
              variant="outline"
              onClick={() => navigate('/sales-invoices')}
              disabled={saving}
            >
              Cancel
            </Button>
            <Button type="submit" disabled={saving}>
              {saving ? (
                <>
                  <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                  Creating...
                </>
              ) : (
                <>
                  <Save className="mr-2 h-4 w-4" />
                  Create Credit Note
                </>
              )}
            </Button>
          </div>
        </form>
      </Form>
    </div>
  )
}

