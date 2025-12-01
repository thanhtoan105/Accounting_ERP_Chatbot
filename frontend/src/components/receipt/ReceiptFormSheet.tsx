'use client'

import { useCallback, useEffect, useMemo, useState } from 'react'
import { useForm, useWatch } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { format } from 'date-fns'
import {
    AlertCircle,
    CalendarIcon,
    Loader2,
    Save,
    Send,
    Wallet,
    Building2,
    AlertTriangle,
    Zap,
} from 'lucide-react'
import { z } from 'zod'
import { toast } from 'sonner'

import {
    ReceiptAllocationGrid,
    type ReceiptAllocation,
} from '@/components/receipt/ReceiptAllocationGrid'
import { MoneyInput } from '@/components/inputs/MoneyInput'
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
import {
    Sheet,
    SheetContent,
    SheetDescription,
    SheetHeader,
    SheetTitle,
} from '@/components/ui/sheet'
import { ScrollArea } from '@/components/ui/scroll-area'
import { cn } from '@/lib/utils'

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
import type { BankAccount } from '@/types/bankAccount'
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

interface ReceiptFormSheetProps {
    open: boolean
    onOpenChange: (open: boolean) => void
    receiptId?: string
    onSuccess?: () => void
}

export function ReceiptFormSheet({
    open,
    onOpenChange,
    receiptId,
    onSuccess,
}: ReceiptFormSheetProps) {
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
    const [formErrors] = useState<Record<string, string>>({})

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

    // Reset form when sheet opens/closes
    useEffect(() => {
        if (open && !receiptId) {
            form.reset({
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
            })
            setSelectedCustomer(null)
            setAllocations([])
            setOpenInvoices([])
            setEditingReceipt(null)
        }
    }, [open, receiptId, form])

    // Load bank accounts
    useEffect(() => {
        if (!open) return
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
    }, [open])

    // Load receipt if editing
    useEffect(() => {
        if (!open || !isEditing || !receiptId || loadingAccounts) return
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
                    isStandalone: receipt.isStandalone || false,
                })
                setEditingReceipt(receipt)
                if (receipt.customerName) {
                    setSelectedCustomer({
                        id: receipt.customerId,
                        customerCode: '',
                        name: receipt.customerName,
                    } as unknown as Customer)
                }
                // Convert existing allocations
                if (receipt.allocations?.length) {
                    const existingAllocations: ReceiptAllocation[] = receipt.allocations.map((a, idx) => ({
                        salesInvoiceId: a.salesInvoiceId,
                        salesInvoiceNumber: a.salesInvoiceNumber || '',
                        salesInvoiceDate: a.salesInvoiceDate || null,
                        salesInvoiceTotalAmount: a.salesInvoiceTotalAmount || 0,
                        salesInvoiceRemainingBalance: a.salesInvoiceRemainingBalance || 0,
                        salesInvoiceDueDate: a.salesInvoiceDueDate || null,
                        allocatedAmount: a.allocatedAmount,
                        allocationOrder: a.allocationOrder || idx + 1,
                    }))
                    setAllocations(existingAllocations)
                }
            })
            .catch(() => {
                toast.error('Failed to load receipt')
            })
            .finally(() => {
                if (mounted) setLoadingReceipt(false)
            })
    }, [open, isEditing, receiptId, loadingAccounts, form])

    // Load open invoices when customer changes
    useEffect(() => {
        if (!selectedCustomer?.id || isStandalone) {
            setOpenInvoices([])
            return
        }
        let mounted = true
        setLoadingOpenInvoices(true)
        getOpenInvoicesForCustomer(selectedCustomer.id)
            .then((invoices) => {
                if (!mounted) return
                setOpenInvoices(invoices)
                // Initialize allocations
                if (!isEditing) {
                    const newAllocations: ReceiptAllocation[] = invoices.map((inv, idx) => ({
                        salesInvoiceId: inv.id,
                        salesInvoiceNumber: inv.invoiceNumber,
                        salesInvoiceDate: inv.invoiceDate || null,
                        salesInvoiceTotalAmount: inv.totalAmount,
                        salesInvoiceRemainingBalance: inv.remainingBalance,
                        salesInvoiceDueDate: inv.dueDate,
                        allocatedAmount: 0,
                        allocationOrder: idx + 1,
                    }))
                    setAllocations(newAllocations)
                }
            })
            .catch(() => {
                toast.error('Failed to load open invoices')
            })
            .finally(() => {
                if (mounted) setLoadingOpenInvoices(false)
            })
    }, [selectedCustomer?.id, isStandalone, isEditing])

    // Filter accounts
    const cashAccountsOnly = useMemo(
        () => bankAccounts.filter((acc) => acc.type === 'CASH'),
        [bankAccounts]
    )
    const bankAccountsOnly = useMemo(
        () => bankAccounts.filter((acc) => acc.type === 'BANK'),
        [bankAccounts]
    )

    // Auto-allocate FIFO
    const autoAllocate = useCallback(() => {
        if (isStandalone || allocations.length === 0 || receiptAmount <= 0) return
        let remaining = receiptAmount
        const newAllocations = allocations
            .slice()
            .sort((a, b) => {
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
            .sort((a, b) => (a.allocationOrder || 0) - (b.allocationOrder || 0))
        setAllocations(newAllocations)
    }, [isStandalone, allocations, receiptAmount])

    // Status checks
    const isDraft = !editingReceipt || editingReceipt.status === 'DRAFT'
    const canEdit = !editingReceipt || editingReceipt.status === 'DRAFT'

    // Format currency
    const formatCurrency = (value: number) => {
        return new Intl.NumberFormat('vi-VN', {
            style: 'currency',
            currency: 'VND',
            minimumFractionDigits: 0,
        }).format(value)
    }

    // Form submit
    const onSubmit = async (values: ReceiptFormValues) => {
        // Validate allocation total matches amount (unless standalone)
        if (!values.isStandalone) {
            const totalAllocated = allocations.reduce((sum, a) => sum + a.allocatedAmount, 0)
            if (Math.abs(totalAllocated - values.amount) > 0.01) {
                toast.error('Allocation mismatch', {
                    description: `Total allocated (${formatCurrency(totalAllocated)}) must equal receipt amount (${formatCurrency(values.amount)})`,
                })
                return
            }
        }

        // Validate account selection
        if (!values.cashAccountId && !values.bankAccountId) {
            toast.error('Please select either a cash or bank account')
            return
        }

        setSaving(true)
        try {
            const allocationRequests: ReceiptAllocationRequest[] = values.isStandalone
                ? []
                : allocations
                    .filter((a) => a.allocatedAmount > 0)
                    .map((a) => ({
                        salesInvoiceId: a.salesInvoiceId,
                        allocatedAmount: a.allocatedAmount,
                    }))

            const request: ARPaymentCreateRequest = {
                customerId: values.customerId,
                receiptDate: values.receiptDate,
                cashAccountId: values.cashAccountId || undefined,
                bankAccountId: values.bankAccountId || undefined,
                amount: values.amount,
                reference: values.reference || undefined,
                paymentMethod: values.paymentMethod,
                payee: values.payee || '',
                receiptProofUrl: values.receiptProofUrl || undefined,
                isStandalone: values.isStandalone,
                allocations: allocationRequests,
            }

            if (isEditing && receiptId) {
                await updateReceipt(receiptId, request)
                toast.success('Receipt updated successfully')
            } else {
                await createReceipt(request)
                toast.success('Receipt created successfully')
            }

            onOpenChange(false)
            onSuccess?.()
        } catch (err: any) {
            toast.error('Failed to save receipt', {
                description: err?.message || 'Unknown error',
            })
        } finally {
            setSaving(false)
        }
    }

    // Handle post
    const handlePost = async () => {
        if (!editingReceipt?.id) return
        setPosting(true)
        try {
            await postReceipt(editingReceipt.id)
            toast.success('Receipt posted successfully')
            onOpenChange(false)
            onSuccess?.()
        } catch (err: any) {
            toast.error('Failed to post receipt', {
                description: err?.message || 'Unknown error',
            })
        } finally {
            setPosting(false)
        }
    }

    return (
        <Sheet open={open} onOpenChange={onOpenChange}>
            <SheetContent className="w-full sm:max-w-4xl lg:max-w-5xl flex flex-col h-full p-6 overflow-hidden" side="right">
                <SheetHeader className="p-0 mb-4">
                    <SheetTitle className="flex items-center gap-2">
                        {isEditing ? 'Edit Receipt' : 'New Receipt'}
                        {editingReceipt?.receiptNumber && (
                            <Badge variant="outline">{editingReceipt.receiptNumber}</Badge>
                        )}
                        {editingReceipt?.status && (
                            <Badge
                                variant={
                                    editingReceipt.status === 'POSTED'
                                        ? 'default'
                                        : editingReceipt.status === 'REVERSED'
                                            ? 'destructive'
                                            : 'secondary'
                                }
                            >
                                {editingReceipt.status}
                            </Badge>
                        )}
                    </SheetTitle>
                    <SheetDescription>
                        {isEditing
                            ? 'Update receipt details and allocations'
                            : 'Record a customer payment receipt'}
                    </SheetDescription>
                </SheetHeader>

                <ScrollArea className="flex-1 min-h-0 overflow-auto">
                    {loadingReceipt || loadingAccounts ? (
                        <div className="space-y-4 py-4">
                            <Skeleton className="h-10 w-full" />
                            <Skeleton className="h-10 w-full" />
                            <Skeleton className="h-10 w-full" />
                            <Skeleton className="h-32 w-full" />
                        </div>
                    ) : (
                        <Form {...form}>
                            <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4 py-2">
                                {/* Customer */}
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
                                                        if (customer) {
                                                            form.setValue('payee', customer.name)
                                                        }
                                                    }}
                                                    disabled={!canEdit || isEditing}
                                                />
                                            </FormControl>
                                            <FormMessage />
                                        </FormItem>
                                    )}
                                />

                                {/* Date & Amount Row */}
                                <div className="grid grid-cols-2 gap-4">
                                    {/* Receipt Date */}
                                    <FormField
                                        control={form.control}
                                        name="receiptDate"
                                        render={({ field }) => (
                                            <FormItem>
                                                <FormLabel>Receipt Date *</FormLabel>
                                                <Popover>
                                                    <PopoverTrigger asChild>
                                                        <FormControl>
                                                            <Button
                                                                variant="outline"
                                                                className={cn(
                                                                    'w-full pl-3 text-left font-normal',
                                                                    !field.value && 'text-muted-foreground'
                                                                )}
                                                                disabled={!canEdit}
                                                            >
                                                                {selectedDate ? format(selectedDate, 'dd/MM/yyyy') : 'Pick a date'}
                                                                <CalendarIcon className="ml-auto h-4 w-4 opacity-50" />
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

                                    {/* Amount */}
                                    <FormField
                                        control={form.control}
                                        name="amount"
                                        render={({ field }) => (
                                            <FormItem>
                                                <FormLabel>Amount *</FormLabel>
                                                <FormControl>
                                                    <MoneyInput
                                                        value={field.value || 0}
                                                        onChange={(val: number | null) => field.onChange(val || 0)}
                                                        disabled={!canEdit}
                                                        placeholder="0"
                                                    />
                                                </FormControl>
                                                <FormMessage />
                                            </FormItem>
                                        )}
                                    />
                                </div>

                                {/* Payment Method & Account */}
                                <div className="grid grid-cols-2 gap-4">
                                    {/* Payment Method */}
                                    <FormField
                                        control={form.control}
                                        name="paymentMethod"
                                        render={({ field }) => (
                                            <FormItem>
                                                <FormLabel>Payment Method *</FormLabel>
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
                                                        {PAYMENT_METHOD_OPTIONS.map((opt) => (
                                                            <SelectItem key={opt.value} value={opt.value}>
                                                                {opt.label}
                                                            </SelectItem>
                                                        ))}
                                                    </SelectContent>
                                                </Select>
                                                <FormMessage />
                                            </FormItem>
                                        )}
                                    />

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
                                                        {cashAccountsOnly.map((account) => (
                                                            <SelectItem key={account.id} value={String(account.id)}>
                                                                {account.bankName}
                                                            </SelectItem>
                                                        ))}
                                                    </SelectContent>
                                                </Select>
                                                <FormMessage />
                                            </FormItem>
                                        )}
                                    />
                                </div>

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

                                {/* Reference & Payee */}
                                <div className="grid grid-cols-2 gap-4">
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

                                {/* Standalone Toggle (Admin Only) */}
                                {isAdmin() && (
                                    <FormField
                                        control={form.control}
                                        name="isStandalone"
                                        render={({ field }) => (
                                            <FormItem className="flex flex-row items-center justify-between rounded-lg border p-3">
                                                <div className="space-y-0.5">
                                                    <FormLabel className="text-sm flex items-center gap-2">
                                                        <AlertTriangle className="h-4 w-4 text-orange-600" />
                                                        Standalone Receipt
                                                    </FormLabel>
                                                    <FormDescription className="text-xs">
                                                        Advance payment without invoice allocation
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

                                <Separator />

                                {/* Invoice Allocations */}
                                {!isStandalone && (
                                    <div className="space-y-3">
                                        <div className="flex items-center justify-between">
                                            <div>
                                                <h4 className="text-sm font-medium">Invoice Allocations</h4>
                                                <p className="text-xs text-muted-foreground">
                                                    Allocated: {formatCurrency(allocations.reduce((sum, a) => sum + a.allocatedAmount, 0))} / {formatCurrency(receiptAmount)}
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
                                                    <Zap className="mr-1 h-3 w-3" />
                                                    Auto-Allocate
                                                </Button>
                                            )}
                                        </div>

                                        {loadingOpenInvoices ? (
                                            <Skeleton className="h-24 w-full" />
                                        ) : openInvoices.length === 0 && selectedCustomer ? (
                                            <Alert>
                                                <AlertCircle className="h-4 w-4" />
                                                <AlertTitle className="text-sm">No Open Invoices</AlertTitle>
                                                <AlertDescription className="text-xs">
                                                    This customer has no unpaid invoices.
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
                                    </div>
                                )}
                            </form>
                        </Form>
                    )}
                </ScrollArea>

                {/* Footer Actions */}
                <div className="flex justify-end gap-2 pt-4 border-t">
                    <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
                        Cancel
                    </Button>
                    {canEdit && (
                        <Button onClick={form.handleSubmit(onSubmit)} disabled={saving}>
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
                    {isEditing && isDraft && editingReceipt?.id && (
                        <AlertDialog>
                            <AlertDialogTrigger asChild>
                                <Button variant="default" disabled={posting}>
                                    <Send className="mr-2 h-4 w-4" />
                                    Post
                                </Button>
                            </AlertDialogTrigger>
                            <AlertDialogContent>
                                <AlertDialogHeader>
                                    <AlertDialogTitle>Post Receipt?</AlertDialogTitle>
                                    <AlertDialogDescription>
                                        This will generate accounting entries and update invoice balances. This action
                                        cannot be undone.
                                    </AlertDialogDescription>
                                </AlertDialogHeader>
                                <AlertDialogFooter>
                                    <AlertDialogCancel>Cancel</AlertDialogCancel>
                                    <AlertDialogAction onClick={handlePost}>
                                        {posting ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : null}
                                        Post Receipt
                                    </AlertDialogAction>
                                </AlertDialogFooter>
                            </AlertDialogContent>
                        </AlertDialog>
                    )}
                </div>
            </SheetContent>
        </Sheet>
    )
}
