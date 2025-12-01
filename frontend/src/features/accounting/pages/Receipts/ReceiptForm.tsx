'use client'

import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { useForm, useWatch } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { format } from 'date-fns'
import {
  AlertCircle,
  CalendarIcon,
  Loader2,
  Save,
  ArrowLeft,
  CheckCircle,
  Send,
  Wallet,
  Building2,
  AlertTriangle,
  Zap,
} from 'lucide-react'
import { useParams, useNavigate } from 'react-router-dom'
import { z } from 'zod'
import { toast } from 'sonner'

import {
  ReceiptAllocationGrid,
  type ReceiptAllocation,
} from '@/components/receipt/ReceiptAllocationGrid'
import { ReceiptAttachmentDropzone } from '@/components/receipt/ReceiptAttachmentDropzone'
import { CustomerPicker } from '@/components/sales/CustomerPicker'
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
  AlertDialogTrigger,
} from '@/components/ui/alert-dialog'
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
import { Calendar } from '@/components/ui/calendar'
import { Separator } from '@/components/ui/separator'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Switch } from '@/components/ui/switch'
import { cn } from '@/lib/utils'

import { useAuth } from '@/hooks/useAuth'
import { useRole } from '@/hooks/useRole'
import type {
  ARPaymentDTO,
  ARPaymentCreateRequest,
  PaymentMethod,
  ReceiptAllocationRequest,
  OpenInvoice,
} from '@/types/receipt'
import {
  createReceipt,
  updateReceipt,
  getReceiptById,
  postReceipt,
  getOpenInvoicesForCustomer,
} from '@/services/receipt'
import { getBankAccounts } from '@/features/bankaccounts/services/bankAccount'
import type { BankAccount, AccountType } from '@/types/bankAccount'
import type { Customer } from '@/types/customer'

const formSchema = z.object({
  customerId: z.number({ message: 'Customer is required' }),
  receiptDate: z.string({ message: 'Receipt date is required' }),
  cashAccountId: z.number().optional().nullable(),
  bankAccountId: z.number().optional().nullable(),
  amount: z.number({ message: 'Amount is required' }).positive('Amount must be positive'),
  reference: z.string().max(100, 'Reference must be 100 characters or less').optional().nullable(),
  paymentMethod: z.enum(['CASH', 'BANK_TRANSFER', 'CHECK', 'OTHER']),
  payee: z.string().max(200, 'Payee must be 200 characters or less').optional().nullable(),
  receiptProofUrl: z
    .string()
    .max(500, 'Receipt proof URL must be 500 characters or less')
    .optional()
    .nullable(),
  isStandalone: z.boolean(),
})

type ReceiptFormValues = z.infer<typeof formSchema>

const PAYMENT_METHOD_OPTIONS: { value: PaymentMethod; label: string }[] = [
  { value: 'CASH', label: 'Cash' },
  { value: 'BANK_TRANSFER', label: 'Bank Transfer' },
  { value: 'CHECK', label: 'Check' },
  { value: 'OTHER', label: 'Other' },
]

export default function ReceiptForm() {
  const params = useParams<{ receiptId?: string }>()
  const navigate = useNavigate()
  const receiptId = params.receiptId && params.receiptId !== 'new' ? params.receiptId : undefined
  const isEditing = Boolean(receiptId)
  const { isAdmin } = useRole()
  const [selectedCustomer, setSelectedCustomer] = useState<Customer | null>(null)
  const [bankAccounts, setBankAccounts] = useState<BankAccount[]>([])
  const [loadingAccounts, setLoadingAccounts] = useState(true)
  const [loadingReceipt, setLoadingReceipt] = useState(false)
  const [saving, setSaving] = useState(false)
  const [posting, setPosting] = useState(false)
  const [loadingOpenInvoices, setLoadingOpenInvoices] = useState(false)
  const [openInvoices, setOpenInvoices] = useState<OpenInvoice[]>([])
  const [allocations, setAllocations] = useState<ReceiptAllocation[]>([])
  const [editingReceipt, setEditingReceipt] = useState<ARPaymentDTO | null>(null)
  const [formErrors, setFormErrors] = useState<Record<string, string>>({})
  const [autosaving, setAutosaving] = useState(false)
  const [lastAutosave, setLastAutosave] = useState<Date | null>(null)
  const autosaveTimerRef = useRef<NodeJS.Timeout | null>(null)

  const form = useForm<ReceiptFormValues>({
    resolver: zodResolver(formSchema),
    defaultValues: {
      customerId: 0,
      receiptDate: format(new Date(), 'yyyy-MM-dd'),
      cashAccountId: null,
      bankAccountId: null,
      amount: 0,
      reference: null,
      paymentMethod: 'BANK_TRANSFER',
      payee: null,
      receiptProofUrl: null,
      isStandalone: false,
    },
  })

  const watchedValues = useWatch({ control: form.control })
  const selectedDate = useMemo(() => {
    if (!watchedValues?.receiptDate) return null
    const parsed = new Date(watchedValues.receiptDate)
    return Number.isNaN(parsed.getTime()) ? null : parsed
  }, [watchedValues?.receiptDate])

  const receiptAmount = watchedValues?.amount || 0
  const isStandalone = watchedValues?.isStandalone || false

  // Load bank accounts
  useEffect(() => {
    let mounted = true
    async function loadAccounts() {
      try {
        const response = await getBankAccounts({ status: true })
        if (!mounted) return
        setBankAccounts(response.data || [])
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

  // Load receipt if editing
  useEffect(() => {
    if (!isEditing || !receiptId || loadingAccounts) return
    let mounted = true
    setLoadingReceipt(true)
    getReceiptById(receiptId)
      .then((receipt) => {
        if (!mounted) return
        form.reset({
          customerId: receipt.customerId,
          receiptDate: receipt.receiptDate,
          cashAccountId: receipt.cashAccountId || null,
          bankAccountId: receipt.bankAccountId || null,
          amount: receipt.amount,
          reference: receipt.reference || null,
          paymentMethod: receipt.paymentMethod,
          payee: receipt.payee || null,
          receiptProofUrl: receipt.receiptProofUrl || null,
          isStandalone: receipt.isStandalone,
        })
        setSelectedCustomer({
          id: receipt.customerId,
          code: receipt.customerCode || '',
          name: receipt.customerName || '',
          companyId: receipt.companyId,
          active: true,
          createdAt: receipt.createdAt,
          updatedAt: receipt.updatedAt,
        })
        // Convert allocations
        const convertedAllocations: ReceiptAllocation[] = receipt.allocations.map((alloc) => ({
          id: alloc.id,
          salesInvoiceId: alloc.salesInvoiceId,
          salesInvoiceNumber: alloc.salesInvoiceNumber,
          salesInvoiceDate: alloc.salesInvoiceDate,
          salesInvoiceDueDate: alloc.salesInvoiceDueDate,
          salesInvoiceTotalAmount: alloc.salesInvoiceTotalAmount || 0,
          salesInvoiceRemainingBalance: alloc.salesInvoiceRemainingBalance || 0,
          allocatedAmount: alloc.allocatedAmount,
          allocationOrder: alloc.allocationOrder,
        }))
        setAllocations(convertedAllocations)
        setEditingReceipt(receipt)
      })
      .catch((error) => {
        console.error(error)
        toast.error('Failed to load receipt')
        navigate('/accounting/receipts')
      })
      .finally(() => {
        if (mounted) setLoadingReceipt(false)
      })
    return () => {
      mounted = false
    }
  }, [receiptId, isEditing, loadingAccounts, form, navigate])

  // Load open invoices when customer changes
  useEffect(() => {
    const customerId = watchedValues?.customerId
    if (!customerId || customerId === 0 || isStandalone) {
      setOpenInvoices([])
      return
    }

    let mounted = true
    setLoadingOpenInvoices(true)
    getOpenInvoicesForCustomer(customerId)
      .then((invoices) => {
        if (!mounted) return
        setOpenInvoices(invoices)
        // Auto-populate allocations from open invoices if new receipt
        if (!isEditing && invoices.length > 0) {
          const autoAllocations: ReceiptAllocation[] = invoices.map((inv, index) => ({
            salesInvoiceId: inv.id,
            salesInvoiceNumber: inv.invoiceNumber,
            salesInvoiceDate: inv.invoiceDate,
            salesInvoiceDueDate: inv.dueDate,
            salesInvoiceTotalAmount: inv.totalAmount,
            salesInvoiceRemainingBalance: inv.remainingBalance,
            allocatedAmount: 0, // User will fill in
            allocationOrder: index + 1,
          }))
          setAllocations(autoAllocations)
        }
      })
      .catch((err) => {
        console.error('Failed to load open invoices', err)
      })
      .finally(() => {
        if (mounted) setLoadingOpenInvoices(false)
      })

    return () => {
      mounted = false
    }
  }, [watchedValues?.customerId, isStandalone, isEditing])

  // Draft autosave (every 30 seconds)
  const performAutosave = useCallback(async () => {
    // Only autosave for existing DRAFT receipts (not new or posted)
    if (!isEditing || !receiptId || !editingReceipt || editingReceipt.status !== 'DRAFT') {
      return
    }

    const values = form.getValues()

    // Validation: either cash or bank account must be selected
    if (!values.cashAccountId && !values.bankAccountId) {
      return // Skip autosave if validation fails
    }

    // Validation: only one account type can be selected
    if (values.cashAccountId && values.bankAccountId) {
      return // Skip autosave if validation fails
    }

    const allocationRequests: ReceiptAllocationRequest[] = allocations
      .filter((alloc) => alloc.allocatedAmount > 0)
      .map((alloc) => ({
        salesInvoiceId: alloc.salesInvoiceId,
        allocatedAmount: alloc.allocatedAmount,
      }))

    const request: ARPaymentCreateRequest = {
      customerId: values.customerId,
      receiptDate: values.receiptDate,
      cashAccountId: values.cashAccountId || undefined,
      bankAccountId: values.bankAccountId || undefined,
      payee: values.payee || selectedCustomer?.name || '',
      amount: values.amount,
      reference: values.reference || undefined,
      paymentMethod: values.paymentMethod,
      receiptProofUrl: values.receiptProofUrl || undefined,
      isStandalone: values.isStandalone,
      allocations: allocationRequests.length > 0 ? allocationRequests : undefined,
    }

    setAutosaving(true)
    try {
      await updateReceipt(receiptId, request)
      setLastAutosave(new Date())
      console.log('Draft autosaved at', new Date().toLocaleTimeString())
    } catch (err: any) {
      console.error('Autosave failed:', err)
      // Silent failure - don't interrupt user
    } finally {
      setAutosaving(false)
    }
  }, [isEditing, receiptId, editingReceipt, form, allocations, selectedCustomer])

  // Autosave effect with 30-second debounce
  useEffect(() => {
    // Clear existing timer
    if (autosaveTimerRef.current) {
      clearTimeout(autosaveTimerRef.current)
    }

    // Only set timer for existing DRAFT receipts
    if (isEditing && receiptId && editingReceipt?.status === 'DRAFT') {
      autosaveTimerRef.current = setTimeout(() => {
        performAutosave()
      }, 30000) // 30 seconds
    }

    return () => {
      if (autosaveTimerRef.current) {
        clearTimeout(autosaveTimerRef.current)
      }
    }
  }, [watchedValues, isEditing, receiptId, editingReceipt, performAutosave])

  const formatCurrency = (value: number) => {
    return new Intl.NumberFormat('vi-VN', {
      style: 'currency',
      currency: 'VND',
      minimumFractionDigits: 0,
      maximumFractionDigits: 0,
    }).format(value)
  }

  // Auto-allocate receipt amount to invoices (FIFO - oldest first by due date)
  const autoAllocate = useCallback(() => {
    if (isStandalone || allocations.length === 0 || receiptAmount <= 0) return

    let remaining = receiptAmount
    const newAllocations = allocations
      .slice() // Clone array
      .sort((a, b) => {
        // Sort by due date (oldest first for FIFO)
        const dateA = a.salesInvoiceDueDate ? new Date(a.salesInvoiceDueDate).getTime() : 0
        const dateB = b.salesInvoiceDueDate ? new Date(b.salesInvoiceDueDate).getTime() : 0
        return dateA - dateB
      })
      .map((alloc) => {
        if (remaining <= 0) {
          return { ...alloc, allocatedAmount: 0 }
        }
        const balance = alloc.salesInvoiceRemainingBalance || 0
        const toAllocate = Math.min(remaining, balance)
        remaining -= toAllocate
        return { ...alloc, allocatedAmount: toAllocate }
      })
      // Restore original order by allocationOrder
      .sort((a, b) => (a.allocationOrder || 0) - (b.allocationOrder || 0))

    setAllocations(newAllocations)
  }, [isStandalone, allocations, receiptAmount])

  const cashAccounts = useMemo(
    () => bankAccounts.filter((acc) => acc.type === 'CASH'),
    [bankAccounts],
  )

  const bankAccountsOnly = useMemo(
    () => bankAccounts.filter((acc) => acc.type === 'BANK'),
    [bankAccounts],
  )

  const onSubmit = async (values: ReceiptFormValues) => {
    // Validation: either cash or bank account must be selected
    if (!values.cashAccountId && !values.bankAccountId) {
      toast.error('Validation Error', {
        description: 'Please select either a cash account or a bank account',
      })
      return
    }

    // Validation: only one account type can be selected
    if (values.cashAccountId && values.bankAccountId) {
      toast.error('Validation Error', {
        description: 'Please select only one account type (cash or bank)',
      })
      return
    }

    // Validation: allocations sum should not exceed receipt amount
    const totalAllocated = allocations.reduce((sum, alloc) => sum + alloc.allocatedAmount, 0)
    if (totalAllocated > values.amount) {
      toast.error('Validation Error', {
        description: `Total allocated amount (${formatCurrency(totalAllocated)}) exceeds receipt amount (${formatCurrency(values.amount)})`,
      })
      return
    }

    // Convert allocations
    const allocationRequests: ReceiptAllocationRequest[] = allocations
      .filter((alloc) => alloc.allocatedAmount > 0)
      .map((alloc) => ({
        salesInvoiceId: alloc.salesInvoiceId,
        allocatedAmount: alloc.allocatedAmount,
      }))

    const request: ARPaymentCreateRequest = {
      customerId: values.customerId,
      receiptDate: values.receiptDate,
      cashAccountId: values.cashAccountId || undefined,
      bankAccountId: values.bankAccountId || undefined,
      payee: values.payee || selectedCustomer?.name || '',
      amount: values.amount,
      reference: values.reference || undefined,
      paymentMethod: values.paymentMethod,
      receiptProofUrl: values.receiptProofUrl || undefined,
      isStandalone: values.isStandalone,
      allocations: allocationRequests.length > 0 ? allocationRequests : undefined,
    }

    setSaving(true)
    setFormErrors({})
    try {
      if (isEditing && receiptId) {
        await updateReceipt(receiptId, request)
        toast.success('Receipt updated successfully', {
          description: 'The receipt has been saved as draft.',
        })
      } else {
        const created = await createReceipt(request)
        toast.success('Receipt created successfully', {
          description: `Receipt ${created.receiptNumber} has been created as draft.`,
        })
        navigate(`/accounting/receipts/${created.id}/edit`)
      }
    } catch (err: any) {
      console.error('Failed to save receipt:', err)
      if (err?.errors) {
        setFormErrors(err.errors)
      }
      toast.error('Failed to save receipt', {
        description: err?.message || 'An unexpected error occurred. Please try again.',
      })
    } finally {
      setSaving(false)
    }
  }

  const handlePost = async () => {
    if (!receiptId) {
      toast.error('Cannot post unsaved receipt', {
        description: 'Please save the receipt first',
      })
      return
    }

    setPosting(true)
    try {
      await postReceipt(receiptId)
      toast.success('Receipt posted successfully', {
        description: 'A GL voucher has been created and invoice statuses have been updated.',
      })
      navigate('/accounting/receipts')
    } catch (err: any) {
      console.error('Failed to post receipt:', err)
      toast.error('Failed to post receipt', {
        description: err?.message || 'An unexpected error occurred.',
      })
    } finally {
      setPosting(false)
    }
  }

  const isDraft = editingReceipt?.status === 'DRAFT' || !editingReceipt
  const canEdit = isDraft
  const canPost = isDraft && isEditing

  if (loadingReceipt || loadingAccounts) {
    return (
      <div className="space-y-4">
        <Skeleton className="h-8 w-64" />
        <Card>
          <CardHeader>
            <Skeleton className="h-6 w-32" />
          </CardHeader>
          <CardContent className="space-y-4">
            <Skeleton className="h-10 w-full" />
            <Skeleton className="h-10 w-full" />
            <Skeleton className="h-10 w-full" />
          </CardContent>
        </Card>
      </div>
    )
  }

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-4">
          <Button variant="ghost" size="icon" onClick={() => navigate('/accounting/receipts')}>
            <ArrowLeft className="h-5 w-5" />
          </Button>
          <div>
            <h1 className="text-3xl font-bold tracking-tight">
              {isEditing ? 'Edit Receipt' : 'New Receipt'}
            </h1>
            {editingReceipt && (
              <div className="flex items-center gap-2 mt-1">
                <span className="text-muted-foreground">{editingReceipt.receiptNumber}</span>
                <Badge variant={editingReceipt.status === 'POSTED' ? 'default' : 'secondary'}>
                  {editingReceipt.status}
                </Badge>
              </div>
            )}
          </div>
        </div>
        <div className="flex gap-2">
          {canPost && (
            <Button onClick={handlePost} disabled={posting}>
              {posting ? (
                <>
                  <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                  Posting...
                </>
              ) : (
                <>
                  <Send className="mr-2 h-4 w-4" />
                  Post Receipt
                </>
              )}
            </Button>
          )}
        </div>
      </div>

      {/* Status Alerts */}
      {!isDraft && (
        <Alert>
          <CheckCircle className="h-4 w-4" />
          <AlertTitle>Receipt Posted</AlertTitle>
          <AlertDescription>
            This receipt has been posted and cannot be edited. A GL voucher has been created.
          </AlertDescription>
        </Alert>
      )}

      <Form {...form}>
        <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-6">
          {/* Receipt Details Card */}
          <Card>
            <CardHeader>
              <CardTitle>Receipt Details</CardTitle>
            </CardHeader>
            <CardContent className="space-y-4">
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                {/* Customer Picker */}
                <FormField
                  control={form.control}
                  name="customerId"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>
                        Customer <span className="text-destructive">*</span>
                      </FormLabel>
                      <FormControl>
                        <CustomerPicker
                          value={selectedCustomer}
                          onChange={(customer: Customer | null) => {
                            setSelectedCustomer(customer)
                            field.onChange(customer?.id || 0)
                          }}
                          disabled={!canEdit}
                        />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />

                {/* Receipt Date */}
                <FormField
                  control={form.control}
                  name="receiptDate"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>
                        Receipt Date <span className="text-destructive">*</span>
                      </FormLabel>
                      <Popover>
                        <PopoverTrigger asChild>
                          <FormControl>
                            <Button
                              variant="outline"
                              className={cn(
                                'w-full justify-start text-left font-normal',
                                !field.value && 'text-muted-foreground',
                              )}
                              disabled={!canEdit}
                            >
                              <CalendarIcon className="mr-2 h-4 w-4" />
                              {selectedDate ? format(selectedDate, 'PPP') : 'Pick a date'}
                            </Button>
                          </FormControl>
                        </PopoverTrigger>
                        <PopoverContent className="w-auto p-0" align="start">
                          <Calendar
                            mode="single"
                            selected={selectedDate || undefined}
                            onSelect={(date) => {
                              field.onChange(date ? format(date, 'yyyy-MM-dd') : '')
                            }}
                            initialFocus
                          />
                        </PopoverContent>
                      </Popover>
                      <FormMessage />
                    </FormItem>
                  )}
                />
              </div>

              <Separator />

              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                {/* Payment Method */}
                <FormField
                  control={form.control}
                  name="paymentMethod"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>
                        Payment Method <span className="text-destructive">*</span>
                      </FormLabel>
                      <Select
                        onValueChange={field.onChange}
                        value={field.value}
                        disabled={!canEdit}
                      >
                        <FormControl>
                          <SelectTrigger>
                            <SelectValue placeholder="Select method" />
                          </SelectTrigger>
                        </FormControl>
                        <SelectContent>
                          {PAYMENT_METHOD_OPTIONS.map((option) => (
                            <SelectItem key={option.value} value={option.value}>
                              {option.label}
                            </SelectItem>
                          ))}
                        </SelectContent>
                      </Select>
                      <FormMessage />
                    </FormItem>
                  )}
                />

                {/* Amount */}
                <FormField
                  control={form.control}
                  name="amount"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>
                        Amount (VND) <span className="text-destructive">*</span>
                      </FormLabel>
                      <FormControl>
                        <Input
                          type="number"
                          placeholder="0"
                          {...field}
                          onChange={(e) => field.onChange(parseFloat(e.target.value) || 0)}
                          disabled={!canEdit}
                        />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
              </div>

              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                {/* Cash Account */}
                <FormField
                  control={form.control}
                  name="cashAccountId"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel className="flex items-center gap-2">
                        <Wallet className="h-4 w-4" />
                        Cash Account
                      </FormLabel>
                      <Select
                        onValueChange={(value) => {
                          field.onChange(value === 'null' ? null : parseInt(value))
                          if (value !== 'null') {
                            form.setValue('bankAccountId', null)
                          }
                        }}
                        value={field.value ? String(field.value) : 'null'}
                        disabled={!canEdit || watchedValues?.bankAccountId !== null}
                      >
                        <FormControl>
                          <SelectTrigger>
                            <SelectValue placeholder="Select cash account" />
                          </SelectTrigger>
                        </FormControl>
                        <SelectContent>
                          <SelectItem value="null">None</SelectItem>
                          {cashAccounts.map((account) => (
                            <SelectItem key={account.id} value={String(account.id)}>
                              {account.bankName} ({account.accountNumber})
                            </SelectItem>
                          ))}
                        </SelectContent>
                      </Select>
                      <FormMessage />
                    </FormItem>
                  )}
                />

                {/* Bank Account */}
                <FormField
                  control={form.control}
                  name="bankAccountId"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel className="flex items-center gap-2">
                        <Building2 className="h-4 w-4" />
                        Bank Account
                      </FormLabel>
                      <Select
                        onValueChange={(value) => {
                          field.onChange(value === 'null' ? null : parseInt(value))
                          if (value !== 'null') {
                            form.setValue('cashAccountId', null)
                          }
                        }}
                        value={field.value ? String(field.value) : 'null'}
                        disabled={!canEdit || watchedValues?.cashAccountId !== null}
                      >
                        <FormControl>
                          <SelectTrigger>
                            <SelectValue placeholder="Select bank account" />
                          </SelectTrigger>
                        </FormControl>
                        <SelectContent>
                          <SelectItem value="null">None</SelectItem>
                          {bankAccountsOnly.map((account) => (
                            <SelectItem key={account.id} value={String(account.id)}>
                              {account.bankName} ({account.accountNumber})
                            </SelectItem>
                          ))}
                        </SelectContent>
                      </Select>
                      <FormMessage />
                    </FormItem>
                  )}
                />
              </div>

              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                {/* Payee */}
                <FormField
                  control={form.control}
                  name="payee"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>Payee</FormLabel>
                      <FormControl>
                        <Input
                          placeholder="Customer name"
                          {...field}
                          value={field.value || ''}
                          disabled={!canEdit}
                        />
                      </FormControl>
                      <FormDescription>Auto-filled from customer</FormDescription>
                      <FormMessage />
                    </FormItem>
                  )}
                />

                {/* Reference */}
                <FormField
                  control={form.control}
                  name="reference"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>Reference</FormLabel>
                      <FormControl>
                        <Input
                          placeholder="Reference number"
                          {...field}
                          value={field.value || ''}
                          disabled={!canEdit}
                        />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
              </div>

              {/* Receipt Proof URL */}
              <FormField
                control={form.control}
                name="receiptProofUrl"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>Receipt Proof URL</FormLabel>
                    <FormControl>
                      <Input
                        placeholder="https://..."
                        {...field}
                        value={field.value || ''}
                        disabled={!canEdit}
                      />
                    </FormControl>
                    <FormDescription>URL to receipt proof document or image</FormDescription>
                    <FormMessage />
                  </FormItem>
                )}
              />

              {/* Standalone Receipt Toggle (Admin Only) */}
              {isAdmin() && (
                <FormField
                  control={form.control}
                  name="isStandalone"
                  render={({ field }) => (
                    <FormItem className="flex flex-row items-center justify-between rounded-lg border p-4">
                      <div className="space-y-0.5">
                        <FormLabel className="text-base flex items-center gap-2">
                          <AlertTriangle className="h-4 w-4 text-orange-600" />
                          Standalone Receipt (Admin Only)
                        </FormLabel>
                        <FormDescription>
                          Create an advance/on-account receipt without immediate invoice allocation.
                          Can be matched to invoices later.
                        </FormDescription>
                      </div>
                      <FormControl>
                        <Switch
                          checked={field.value}
                          onCheckedChange={field.onChange}
                          disabled={!canEdit}
                        />
                      </FormControl>
                    </FormItem>
                  )}
                />
              )}
            </CardContent>
          </Card>

          {/* Allocations Card */}
          {!isStandalone && (
            <Card>
              <CardHeader className="flex flex-row items-center justify-between">
                <div>
                  <CardTitle>Invoice Allocations</CardTitle>
                  <p className="text-sm text-muted-foreground mt-1">
                    Allocated: {formatCurrency(allocations.reduce((sum, a) => sum + a.allocatedAmount, 0))} / {formatCurrency(receiptAmount)}
                    {allocations.reduce((sum, a) => sum + a.allocatedAmount, 0) < receiptAmount && (
                      <span className="text-amber-600 ml-2">
                        (Unallocated: {formatCurrency(receiptAmount - allocations.reduce((sum, a) => sum + a.allocatedAmount, 0))})
                      </span>
                    )}
                  </p>
                </div>
                {canEdit && allocations.length > 0 && (
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    onClick={autoAllocate}
                    disabled={receiptAmount <= 0}
                  >
                    <Zap className="mr-2 h-4 w-4" />
                    Auto-Allocate (FIFO)
                  </Button>
                )}
              </CardHeader>
              <CardContent>
                {loadingOpenInvoices ? (
                  <Skeleton className="h-32 w-full" />
                ) : openInvoices.length === 0 && selectedCustomer ? (
                  <Alert>
                    <AlertCircle className="h-4 w-4" />
                    <AlertTitle>No Open Invoices</AlertTitle>
                    <AlertDescription>
                      This customer has no open/unpaid invoices. Consider creating a standalone
                      receipt instead.
                    </AlertDescription>
                  </Alert>
                ) : (
                  <ReceiptAllocationGrid
                    allocations={allocations}
                    onAllocationsChange={setAllocations}
                    totalReceiptAmount={receiptAmount}
                    readOnly={!canEdit}
                    errors={formErrors}
                  />
                )}
              </CardContent>
            </Card>
          )}

          {/* Attachments Card - AC6.2-08 */}
          <Card>
            <CardHeader>
              <CardTitle>Attachments</CardTitle>
            </CardHeader>
            <CardContent>
              <ReceiptAttachmentDropzone
                receiptId={receiptId || null}
                disabled={!canEdit}
                maxFiles={10}
                maxTotalSize={20 * 1024 * 1024}
                maxFileSize={10 * 1024 * 1024}
              />
            </CardContent>
          </Card>

          {/* Autosave Indicator */}
          {isEditing && isDraft && (
            <div className="flex items-center justify-end text-sm text-muted-foreground">
              {autosaving ? (
                <div className="flex items-center gap-2">
                  <Loader2 className="h-3 w-3 animate-spin" />
                  <span>Autosaving...</span>
                </div>
              ) : lastAutosave ? (
                <span>Last saved: {lastAutosave.toLocaleTimeString()}</span>
              ) : null}
            </div>
          )}

          {/* Form Actions */}
          <div className="flex justify-end gap-4">
            <Button
              type="button"
              variant="outline"
              onClick={() => navigate('/accounting/receipts')}
            >
              Cancel
            </Button>
            {canEdit && (
              <Button type="submit" disabled={saving}>
                {saving ? (
                  <>
                    <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                    Saving...
                  </>
                ) : (
                  <>
                    <Save className="mr-2 h-4 w-4" />
                    Save Draft
                  </>
                )}
              </Button>
            )}
          </div>
        </form>
      </Form>
    </div>
  )
}
