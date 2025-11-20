'use client'

import { useCallback, useEffect, useMemo, useState } from 'react'
import { useForm, useWatch } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { format, parseISO } from 'date-fns'
import {
  AlertCircle,
  CalendarIcon,
  Loader2,
  Save,
  ArrowLeft,
  CheckCircle,
  XCircle,
  Send,
  Wallet,
  Building2,
} from 'lucide-react'
import { useParams, useNavigate } from 'react-router-dom'
import { z } from 'zod'
import { toast } from 'sonner'

import { PaymentAllocationGrid, type PaymentAllocation } from '@/components/payment/PaymentAllocationGrid'
import { AccountBalanceDisplay } from '@/components/payment/AccountBalanceDisplay'
import { SupplierPicker } from '@/components/purchase/SupplierPicker'
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
import type {
  APPaymentDTO,
  APPaymentCreateRequest,
  PaymentStatus,
  PaymentMethod,
  PaymentAllocationRequest,
} from '@/types/payment'
import {
  createPayment,
  updatePayment,
  getPaymentById,
  allocateFIFO,
  postPayment,
  cancelPayment,
  getOpenBillsForSupplier,
} from '@/services/payment'
import { getBankAccounts } from '@/features/bankaccounts/services/bankAccount'
import type { BankAccount, AccountType } from '@/types/bankAccount'
import type { Supplier } from '@/types/supplier'
import type { PurchaseBillDTO } from '@/types/purchaseBill'

const formSchema = z.object({
  supplierId: z.number({ required_error: 'Supplier is required' }),
  paymentDate: z.string({ required_error: 'Payment date is required' }),
  dueDate: z.string().optional().nullable(),
  cashAccountId: z.number().optional().nullable(),
  bankAccountId: z.number().optional().nullable(),
  amount: z.number({ required_error: 'Amount is required' }).positive('Amount must be positive'),
  reference: z.string().max(100, 'Reference must be 100 characters or less').optional().nullable(),
  paymentMethod: z.enum(['CASH', 'BANK_TRANSFER', 'CHECK', 'OTHER']).default('BANK_TRANSFER'),
  payee: z.string().max(200, 'Payee must be 200 characters or less').optional().nullable(),
  paymentProofUrl: z.string().max(500, 'Payment proof URL must be 500 characters or less').optional().nullable(),
  isStandalone: z.boolean().default(false),
})

type PaymentFormValues = z.infer<typeof formSchema>

const PAYMENT_METHOD_OPTIONS: { value: PaymentMethod; label: string }[] = [
  { value: 'CASH', label: 'Cash' },
  { value: 'BANK_TRANSFER', label: 'Bank Transfer' },
  { value: 'CHECK', label: 'Check' },
  { value: 'OTHER', label: 'Other' },
]

export default function PaymentForm() {
  const params = useParams<{ paymentId?: string }>()
  const navigate = useNavigate()
  const paymentId = params.paymentId && params.paymentId !== 'new' ? params.paymentId : undefined
  const isEditing = Boolean(paymentId)
  const { user } = useAuth()
  const [selectedSupplier, setSelectedSupplier] = useState<Supplier | null>(null)
  const [bankAccounts, setBankAccounts] = useState<BankAccount[]>([])
  const [loadingAccounts, setLoadingAccounts] = useState(true)
  const [loadingPayment, setLoadingPayment] = useState(false)
  const [saving, setSaving] = useState(false)
  const [posting, setPosting] = useState(false)
  const [cancelling, setCancelling] = useState(false)
  const [allocatingFIFO, setAllocatingFIFO] = useState(false)
  const [loadingOpenBills, setLoadingOpenBills] = useState(false)
  const [openBills, setOpenBills] = useState<PurchaseBillDTO[]>([])
  const [allocations, setAllocations] = useState<PaymentAllocation[]>([])
  const [editingPayment, setEditingPayment] = useState<APPaymentDTO | null>(null)
  const [formErrors, setFormErrors] = useState<Record<string, string>>({})
  const today = useMemo(() => new Date(), [])

  const form = useForm<PaymentFormValues>({
    resolver: zodResolver(formSchema),
    defaultValues: {
      supplierId: 0,
      paymentDate: format(new Date(), 'yyyy-MM-dd'),
      dueDate: null,
      cashAccountId: null,
      bankAccountId: null,
      amount: 0,
      reference: null,
      paymentMethod: 'BANK_TRANSFER',
      payee: null,
      paymentProofUrl: null,
      isStandalone: false,
    },
  })

  const watchedValues = useWatch({ control: form.control })
  const selectedDate = useMemo(() => {
    if (!watchedValues?.paymentDate) return null
    const parsed = new Date(watchedValues.paymentDate)
    return Number.isNaN(parsed.getTime()) ? null : parsed
  }, [watchedValues?.paymentDate])

  const selectedAccountId = watchedValues?.cashAccountId || watchedValues?.bankAccountId
  const paymentAmount = watchedValues?.amount || 0
  const isStandalone = watchedValues?.isStandalone || false
  const selectedAccountType: AccountType | undefined = watchedValues?.cashAccountId
    ? 'CASH'
    : watchedValues?.bankAccountId
      ? 'BANK'
      : undefined

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

  // Load payment if editing
  useEffect(() => {
    if (!isEditing || !paymentId || loadingAccounts) return
    let mounted = true
    setLoadingPayment(true)
    getPaymentById(paymentId)
      .then((payment) => {
        if (!mounted) return
        form.reset({
          supplierId: payment.supplierId,
          paymentDate: payment.paymentDate,
          dueDate: payment.dueDate || null,
          cashAccountId: payment.cashAccountId || null,
          bankAccountId: payment.bankAccountId || null,
          amount: payment.amount,
          reference: payment.reference || null,
          paymentMethod: payment.paymentMethod,
          payee: payment.payee || null,
          paymentProofUrl: payment.paymentProofUrl || null,
          isStandalone: payment.isStandalone,
        })
        setSelectedSupplier({
          id: payment.supplierId,
          code: payment.supplierCode || '',
          name: payment.supplierName || '',
          companyId: payment.companyId,
          active: true,
          createdAt: payment.createdAt,
          updatedAt: payment.updatedAt,
        })
        // Convert allocations
        const convertedAllocations: PaymentAllocation[] = payment.allocations.map((alloc) => ({
          id: alloc.id,
          purchaseBillId: alloc.purchaseBillId,
          purchaseBillNumber: alloc.purchaseBillNumber,
          purchaseBillDate: alloc.purchaseBillDate,
          purchaseBillDueDate: alloc.purchaseBillDueDate,
          purchaseBillTotalAmount: alloc.purchaseBillTotalAmount || 0,
          purchaseBillRemainingBalance: alloc.purchaseBillRemainingBalance || 0,
          allocatedAmount: alloc.allocatedAmount,
          allocationOrder: alloc.allocationOrder,
        }))
        setAllocations(convertedAllocations)
        setEditingPayment(payment)
      })
      .catch((error) => {
        console.error(error)
        toast.error('Failed to load payment')
        navigate('/payments')
      })
      .finally(() => {
        if (mounted) setLoadingPayment(false)
      })
    return () => {
      mounted = false
    }
  }, [paymentId, isEditing, loadingAccounts, form, navigate])

  // Load open bills when supplier changes
  useEffect(() => {
    const supplierId = watchedValues?.supplierId
    if (!supplierId || supplierId === 0 || isStandalone) {
      setOpenBills([])
      return
    }

    let mounted = true
    setLoadingOpenBills(true)
    getOpenBillsForSupplier(supplierId)
      .then((bills) => {
        if (!mounted) return
        setOpenBills(bills)
      })
      .catch((err) => {
        console.error('Failed to load open bills', err)
      })
      .finally(() => {
        if (mounted) setLoadingOpenBills(false)
      })

    return () => {
      mounted = false
    }
  }, [watchedValues?.supplierId, isStandalone])

  // Handle FIFO allocation
  const handleFIFOAllocation = useCallback(async () => {
    const supplierId = watchedValues?.supplierId
    const amount = watchedValues?.amount

    if (!supplierId || supplierId === 0 || !amount || amount <= 0) {
      toast.error('Please select a supplier and enter a payment amount')
      return
    }

    setAllocatingFIFO(true)
    try {
      const suggestedAllocations = await allocateFIFO(amount, supplierId)
      const converted: PaymentAllocation[] = suggestedAllocations.map((alloc) => ({
        purchaseBillId: alloc.purchaseBillId,
        purchaseBillNumber: alloc.purchaseBillNumber,
        purchaseBillDate: alloc.purchaseBillDate,
        purchaseBillDueDate: alloc.purchaseBillDueDate,
        purchaseBillTotalAmount: alloc.purchaseBillTotalAmount || 0,
        purchaseBillRemainingBalance: alloc.purchaseBillRemainingBalance || 0,
        allocatedAmount: alloc.allocatedAmount,
        allocationOrder: alloc.allocationOrder,
      }))
      setAllocations(converted)
      toast.success('FIFO allocation completed', {
        description: `${converted.length} bill(s) allocated`,
      })
    } catch (err: any) {
      toast.error('Failed to allocate payment', {
        description: err?.message || 'Please check supplier and amount',
      })
    } finally {
      setAllocatingFIFO(false)
    }
  }, [watchedValues?.supplierId, watchedValues?.amount])

  function buildRequest(values: PaymentFormValues): APPaymentCreateRequest {
    const allocationRequests: PaymentAllocationRequest[] = allocations.map((alloc) => ({
      purchaseBillId: alloc.purchaseBillId,
      allocatedAmount: alloc.allocatedAmount,
    }))

    return {
      id: paymentId,
      supplierId: values.supplierId,
      paymentDate: values.paymentDate,
      dueDate: values.dueDate || null,
      cashAccountId: values.cashAccountId || null,
      bankAccountId: values.bankAccountId || null,
      payee: values.payee || null,
      amount: values.amount,
      reference: values.reference || null,
      paymentMethod: values.paymentMethod,
      paymentProofUrl: values.paymentProofUrl || null,
      isStandalone: values.isStandalone,
      allocations: isStandalone ? [] : allocationRequests,
    }
  }

  async function handleSave(values: PaymentFormValues) {
    try {
      setSaving(true)
      setFormErrors({})
      const payload = buildRequest(values)

      // Validate allocations if not standalone
      if (!values.isStandalone && allocations.length === 0) {
        toast.error('Please allocate payment to bills or mark as standalone')
        return
      }

      const totalAllocated = allocations.reduce((sum, alloc) => sum + alloc.allocatedAmount, 0)
      if (!values.isStandalone && Math.abs(totalAllocated - values.amount) > 0.01) {
        toast.error('Total allocated amount must equal payment amount')
        return
      }

      const response = isEditing && paymentId
        ? await updatePayment(paymentId, payload)
        : await createPayment(payload)

      toast.success(isEditing ? 'Payment updated' : 'Payment created', {
        description: `Payment Number: ${response.paymentNumber}`,
      })

      setFormErrors({})
      if (isEditing && paymentId) {
        const updated = await getPaymentById(paymentId)
        setEditingPayment(updated)
      } else {
        navigate(`/payments/${response.id}`)
      }
    } catch (err: any) {
      const errorMessage = err?.error?.message || err?.message || 'Failed to save payment'
      toast.error('Failed to save payment', { description: errorMessage })
      
      // Parse field errors if available
      if (err?.error?.details) {
        setFormErrors(err.error.details)
      }
    } finally {
      setSaving(false)
    }
  }

  async function handlePost() {
    if (!paymentId) return

    setPosting(true)
    try {
      await postPayment(paymentId)
      toast.success('Payment posted successfully', {
        description: 'Voucher has been generated and bills updated',
      })
      const updated = await getPaymentById(paymentId)
      setEditingPayment(updated)
    } catch (err: any) {
      toast.error('Failed to post payment', { description: err?.message })
    } finally {
      setPosting(false)
    }
  }

  async function handleCancel() {
    if (!paymentId) return

    setCancelling(true)
    try {
      await cancelPayment(paymentId)
      toast.success('Payment cancelled')
      const updated = await getPaymentById(paymentId)
      setEditingPayment(updated)
    } catch (err: any) {
      toast.error('Failed to cancel payment', { description: err?.message })
    } finally {
      setCancelling(false)
    }
  }

  const isReadOnly = editingPayment?.status === 'POSTED' || editingPayment?.status === 'CANCELLED'
  const canPost = editingPayment?.status === 'DRAFT' || editingPayment?.status === 'PENDING_APPROVAL'
  const canCancel = editingPayment?.status === 'DRAFT'

  const cashAccounts = useMemo(
    () => bankAccounts.filter((acc) => acc.type === 'CASH'),
    [bankAccounts],
  )
  const bankAccountList = useMemo(
    () => bankAccounts.filter((acc) => acc.type === 'BANK'),
    [bankAccounts],
  )

  if (loadingPayment || loadingAccounts) {
    return (
      <div className="container mx-auto py-6 space-y-6">
        <Skeleton className="h-10 w-64" />
        <Skeleton className="h-96 w-full" />
      </div>
    )
  }

  return (
    <div className="container mx-auto py-6 space-y-6">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-4">
          <Button variant="ghost" size="icon" onClick={() => navigate('/payments')}>
            <ArrowLeft className="h-4 w-4" />
          </Button>
          <div>
            <h1 className="text-3xl font-bold tracking-tight">
              {isEditing ? 'Edit Payment' : 'Create Payment'}
            </h1>
            <p className="text-muted-foreground">
              {isEditing
                ? `Payment Number: ${editingPayment?.paymentNumber || ''}`
                : 'Enter payment details and allocate to bills'}
            </p>
          </div>
        </div>
        {editingPayment && (
          <Badge variant="outline" className="text-sm">
            {editingPayment.status}
          </Badge>
        )}
      </div>

      <Form {...form}>
        <form onSubmit={form.handleSubmit(handleSave)} className="space-y-6">
          <div className="grid gap-6 md:grid-cols-3">
            {/* Main Form */}
            <div className="md:col-span-2 space-y-6">
              <Card>
                <CardHeader>
                  <CardTitle>Payment Information</CardTitle>
                </CardHeader>
                <CardContent className="space-y-4">
                  <div className="grid gap-4 md:grid-cols-2">
                    <FormField
                      control={form.control}
                      name="supplierId"
                      render={({ field }) => (
                        <FormItem>
                          <FormLabel>
                            Supplier <span className="text-destructive">*</span>
                          </FormLabel>
                          <FormControl>
                            <SupplierPicker
                              value={selectedSupplier}
                              onChange={(supplier) => {
                                setSelectedSupplier(supplier)
                                field.onChange(supplier?.id || 0)
                                // Clear allocations when supplier changes
                                if (supplier?.id !== selectedSupplier?.id) {
                                  setAllocations([])
                                }
                              }}
                              disabled={isReadOnly}
                              error={formErrors.supplierId}
                            />
                          </FormControl>
                          <FormMessage />
                        </FormItem>
                      )}
                    />
                    <FormField
                      control={form.control}
                      name="paymentDate"
                      render={({ field }) => (
                        <FormItem>
                          <FormLabel>
                            Payment Date <span className="text-destructive">*</span>
                          </FormLabel>
                          <FormControl>
                            <Popover>
                              <PopoverTrigger asChild>
                                <Button
                                  variant="outline"
                                  className={cn(
                                    'w-full justify-start text-left font-normal',
                                    !field.value && 'text-muted-foreground',
                                  )}
                                  disabled={isReadOnly}
                                >
                                  <CalendarIcon className="mr-2 h-4 w-4" />
                                  {field.value ? format(parseISO(field.value), 'PPP') : 'Pick a date'}
                                </Button>
                              </PopoverTrigger>
                              <PopoverContent className="w-auto p-0" align="start">
                                <Calendar
                                  mode="single"
                                  selected={selectedDate || undefined}
                                  onSelect={(date) => {
                                    if (date) {
                                      field.onChange(format(date, 'yyyy-MM-dd'))
                                    }
                                  }}
                                  initialFocus
                                />
                              </PopoverContent>
                            </Popover>
                          </FormControl>
                          <FormMessage />
                        </FormItem>
                      )}
                    />
                    <FormField
                      control={form.control}
                      name="cashAccountId"
                      render={({ field }) => (
                        <FormItem>
                          <FormLabel>Cash Account</FormLabel>
                          <FormControl>
                            <Select
                              value={field.value?.toString() || ''}
                              onValueChange={(value) => {
                                field.onChange(value ? Number(value) : null)
                                // Clear bank account if cash account is selected
                                if (value) {
                                  form.setValue('bankAccountId', null)
                                }
                              }}
                              disabled={isReadOnly || Boolean(watchedValues?.bankAccountId)}
                            >
                              <SelectTrigger>
                                <SelectValue placeholder="Select cash account" />
                              </SelectTrigger>
                              <SelectContent>
                                {cashAccounts.map((acc) => (
                                  <SelectItem key={acc.id} value={acc.id.toString()}>
                                    {acc.accountNumber} • {acc.bankName}
                                  </SelectItem>
                                ))}
                              </SelectContent>
                            </Select>
                          </FormControl>
                          <FormMessage />
                        </FormItem>
                      )}
                    />
                    <FormField
                      control={form.control}
                      name="bankAccountId"
                      render={({ field }) => (
                        <FormItem>
                          <FormLabel>Bank Account</FormLabel>
                          <FormControl>
                            <Select
                              value={field.value?.toString() || ''}
                              onValueChange={(value) => {
                                field.onChange(value ? Number(value) : null)
                                // Clear cash account if bank account is selected
                                if (value) {
                                  form.setValue('cashAccountId', null)
                                }
                              }}
                              disabled={isReadOnly || Boolean(watchedValues?.cashAccountId)}
                            >
                              <SelectTrigger>
                                <SelectValue placeholder="Select bank account" />
                              </SelectTrigger>
                              <SelectContent>
                                {bankAccountList.map((acc) => (
                                  <SelectItem key={acc.id} value={acc.id.toString()}>
                                    {acc.accountNumber} • {acc.bankName}
                                  </SelectItem>
                                ))}
                              </SelectContent>
                            </Select>
                          </FormControl>
                          <FormMessage />
                        </FormItem>
                      )}
                    />
                    <FormField
                      control={form.control}
                      name="amount"
                      render={({ field }) => (
                        <FormItem>
                          <FormLabel>
                            Amount <span className="text-destructive">*</span>
                          </FormLabel>
                          <FormControl>
                            <Input
                              type="number"
                              step="0.01"
                              {...field}
                              value={field.value || ''}
                              onChange={(e) => field.onChange(Number(e.target.value) || 0)}
                              disabled={isReadOnly}
                              placeholder="0.00"
                            />
                          </FormControl>
                          <FormMessage />
                        </FormItem>
                      )}
                    />
                    <FormField
                      control={form.control}
                      name="paymentMethod"
                      render={({ field }) => (
                        <FormItem>
                          <FormLabel>Payment Method</FormLabel>
                          <Select
                            value={field.value}
                            onValueChange={field.onChange}
                            disabled={isReadOnly}
                          >
                            <FormControl>
                              <SelectTrigger>
                                <SelectValue />
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
                    <FormField
                      control={form.control}
                      name="reference"
                      render={({ field }) => (
                        <FormItem>
                          <FormLabel>Reference</FormLabel>
                          <FormControl>
                            <Input
                              {...field}
                              value={field.value || ''}
                              disabled={isReadOnly}
                              placeholder="Payment reference"
                              maxLength={100}
                            />
                          </FormControl>
                          <FormMessage />
                        </FormItem>
                      )}
                    />
                    <FormField
                      control={form.control}
                      name="payee"
                      render={({ field }) => (
                        <FormItem>
                          <FormLabel>Payee</FormLabel>
                          <FormControl>
                            <Input
                              {...field}
                              value={field.value || ''}
                              disabled={isReadOnly}
                              placeholder="Payee name"
                              maxLength={200}
                            />
                          </FormControl>
                          <FormMessage />
                        </FormItem>
                      )}
                    />
                  </div>
                  <FormField
                    control={form.control}
                    name="isStandalone"
                    render={({ field }) => (
                      <FormItem className="space-y-4">
                        <div className="flex flex-row items-center justify-between rounded-lg border p-4">
                          <div className="space-y-0.5">
                            <FormLabel className="text-base">Standalone Payment</FormLabel>
                            <FormDescription>
                              Mark as standalone if this is an advance payment or ad hoc transaction
                              not linked to specific bills
                            </FormDescription>
                          </div>
                          <FormControl>
                            <Switch
                              checked={field.value}
                              onCheckedChange={(checked) => {
                                field.onChange(checked)
                                if (checked) {
                                  setAllocations([])
                                }
                              }}
                              disabled={isReadOnly}
                            />
                          </FormControl>
                        </div>
                        {field.value && (
                          <Alert variant="destructive" className="border-orange-500 bg-orange-50 dark:bg-orange-950/20">
                            <AlertCircle className="h-4 w-4 text-orange-600 dark:text-orange-400" />
                            <AlertTitle className="text-orange-900 dark:text-orange-100">
                              Standalone Payment Warning
                            </AlertTitle>
                            <AlertDescription className="text-orange-800 dark:text-orange-200">
                              This is a standalone payment (advance/ad hoc transaction) not linked to specific bills.
                              Admin role required. This payment will still generate proper journal entries (Dr AP 331, Cr cash/bank 111/112).
                            </AlertDescription>
                          </Alert>
                        )}
                      </FormItem>
                    )}
                  />
                </CardContent>
              </Card>

              {/* Allocations */}
              {!isStandalone && (
                <Card>
                  <CardHeader>
                    <div className="flex items-center justify-between">
                      <CardTitle>Payment Allocations</CardTitle>
                      {!isReadOnly && (
                        <Button
                          type="button"
                          variant="outline"
                          size="sm"
                          onClick={handleFIFOAllocation}
                          disabled={allocatingFIFO || !watchedValues?.supplierId || !watchedValues?.amount}
                        >
                          {allocatingFIFO ? (
                            <>
                              <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                              Allocating...
                            </>
                          ) : (
                            <>
                              <Building2 className="mr-2 h-4 w-4" />
                              Allocate FIFO
                            </>
                          )}
                        </Button>
                      )}
                    </div>
                  </CardHeader>
                  <CardContent>
                    <PaymentAllocationGrid
                      allocations={allocations}
                      onAllocationsChange={setAllocations}
                      totalPaymentAmount={paymentAmount}
                      readOnly={isReadOnly}
                      loading={loadingOpenBills}
                      errors={formErrors}
                    />
                  </CardContent>
                </Card>
              )}

              {isStandalone && (
                <Alert>
                  <AlertCircle className="h-4 w-4" />
                  <AlertTitle>Standalone Payment</AlertTitle>
                  <AlertDescription>
                    This payment is not linked to specific bills. It will be recorded as an advance
                    payment or ad hoc transaction.
                  </AlertDescription>
                </Alert>
              )}
            </div>

            {/* Sidebar */}
            <div className="space-y-6">
              <AccountBalanceDisplay
                accountId={selectedAccountId}
                paymentAmount={paymentAmount}
                accountType={selectedAccountType}
              />

              {openBills.length > 0 && !isStandalone && (
                <Card>
                  <CardHeader>
                    <CardTitle className="text-sm">Open Bills</CardTitle>
                  </CardHeader>
                  <CardContent>
                    <div className="space-y-2 text-sm">
                      {openBills.slice(0, 5).map((bill) => (
                        <div key={bill.id} className="flex justify-between items-center">
                          <span className="font-medium">{bill.billNumber}</span>
                          <span className="text-muted-foreground">
                            {new Intl.NumberFormat('vi-VN', {
                              style: 'currency',
                              currency: 'VND',
                              minimumFractionDigits: 0,
                            }).format(bill.totalAmount)}
                          </span>
                        </div>
                      ))}
                      {openBills.length > 5 && (
                        <p className="text-xs text-muted-foreground">
                          +{openBills.length - 5} more bill(s)
                        </p>
                      )}
                    </div>
                  </CardContent>
                </Card>
              )}
            </div>
          </div>

          {Object.keys(formErrors).length > 0 && (
            <Alert variant="destructive">
              <AlertCircle className="h-4 w-4" />
              <AlertTitle>Validation Errors</AlertTitle>
              <AlertDescription>
                <ul className="list-disc list-inside">
                  {Object.entries(formErrors).map(([field, error]) => (
                    <li key={field}>
                      <strong>{field}:</strong> {error}
                    </li>
                  ))}
                </ul>
              </AlertDescription>
            </Alert>
          )}

          <div className="flex justify-end gap-2">
            <Button
              type="button"
              variant="outline"
              onClick={() => navigate('/payments')}
              disabled={saving || posting || cancelling}
            >
              Cancel
            </Button>
            {canCancel && (
              <Button
                type="button"
                variant="outline"
                onClick={handleCancel}
                disabled={cancelling}
                className="border-red-200 text-red-600 hover:bg-red-50"
              >
                {cancelling ? (
                  <>
                    <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                    Cancelling...
                  </>
                ) : (
                  <>
                    <XCircle className="mr-2 h-4 w-4" />
                    Cancel Payment
                  </>
                )}
              </Button>
            )}
            <Button type="submit" disabled={saving || isReadOnly}>
              {saving ? (
                <>
                  <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                  Saving...
                </>
              ) : (
                <>
                  <Save className="mr-2 h-4 w-4" />
                  {isEditing ? 'Update' : 'Save'}
                </>
              )}
            </Button>
            {canPost && (
              <Button type="button" variant="default" onClick={handlePost} disabled={posting}>
                {posting ? (
                  <>
                    <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                    Posting...
                  </>
                ) : (
                  <>
                    <Send className="mr-2 h-4 w-4" />
                    Post Payment
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

