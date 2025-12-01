'use client'

import { useCallback, useEffect, useMemo, useState } from 'react'
import { Download, RefreshCw, Search, Eye, ChevronLeft, ChevronRight } from 'lucide-react'
import { toast } from 'sonner'
import { format } from 'date-fns'

import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import {
    Table,
    TableBody,
    TableCell,
    TableHead,
    TableHeader,
    TableRow,
} from '@/components/ui/table'
import { Skeleton } from '@/components/ui/skeleton'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Label } from '@/components/ui/label'
import {
    Select,
    SelectContent,
    SelectItem,
    SelectTrigger,
    SelectValue,
} from '@/components/ui/select'
import { Badge } from '@/components/ui/badge'
import { DatePicker } from '@/components/ui/date-picker'
import {
    Dialog,
    DialogContent,
    DialogHeader,
    DialogTitle,
} from '@/components/ui/dialog'
import { getBankAccounts } from '@/features/bankaccounts/services/bankAccount'
import type { BankAccount } from '@/types/bankAccount'
import {
    getCashBook,
    getVoucherDetail,
    exportCashBook,
    downloadBlob,
    type CashBookResponse,
    type CashBookEntry,
    type VoucherDetail,
    type CashBookExportJob,
} from '../../services/cashBook'

const PAGE_SIZE_OPTIONS = [10, 20, 30, 50, 100]

function formatCurrency(value: number): string {
    return new Intl.NumberFormat('vi-VN', {
        style: 'currency',
        currency: 'VND',
        minimumFractionDigits: 0,
        maximumFractionDigits: 0,
    }).format(value)
}

function formatDate(dateString: string): string {
    try {
        return format(new Date(dateString), 'dd/MM/yyyy')
    } catch {
        return dateString
    }
}

export function CashBookPage() {
    // State
    const [loading, setLoading] = useState(false)
    const [bankAccounts, setBankAccounts] = useState<BankAccount[]>([])
    const [selectedAccountId, setSelectedAccountId] = useState<string>('')
    const [dateFrom, setDateFrom] = useState<Date | undefined>(undefined)
    const [dateTo, setDateTo] = useState<Date | undefined>(undefined)
    const [transactionType, setTransactionType] = useState<'all' | 'receipt' | 'payment'>('all')
    const [searchTerm, setSearchTerm] = useState('')
    const [page, setPage] = useState(0)
    const [pageSize, setPageSize] = useState(20)
    const [data, setData] = useState<CashBookResponse | null>(null)
    const [exporting, setExporting] = useState(false)

    // Voucher detail modal
    const [selectedVoucher, setSelectedVoucher] = useState<VoucherDetail | null>(null)
    const [voucherLoading, setVoucherLoading] = useState(false)
    const [voucherModalOpen, setVoucherModalOpen] = useState(false)

    // Load bank accounts
    useEffect(() => {
        const loadBankAccounts = async () => {
            try {
                const response = await getBankAccounts({ status: true, size: 100 })
                setBankAccounts(response.data || [])
                // Auto-select first account
                if (response.data?.length && !selectedAccountId) {
                    setSelectedAccountId(String(response.data[0].id))
                }
            } catch (error) {
                console.error('Failed to load bank accounts:', error)
                toast.error('Failed to load bank accounts')
            }
        }
        void loadBankAccounts()
    }, [])

    // Load cash book data
    const loadData = useCallback(async () => {
        if (!selectedAccountId) return

        try {
            setLoading(true)
            const result = await getCashBook(Number(selectedAccountId), {
                dateFrom: dateFrom ? format(dateFrom, 'yyyy-MM-dd') : undefined,
                dateTo: dateTo ? format(dateTo, 'yyyy-MM-dd') : undefined,
                type: transactionType,
                reference: searchTerm || undefined,
                page,
                size: pageSize,
            })
            setData(result)
        } catch (error) {
            console.error('Failed to load cash book:', error)
            toast.error('Failed to load cash book data')
        } finally {
            setLoading(false)
        }
    }, [selectedAccountId, dateFrom, dateTo, transactionType, searchTerm, page, pageSize])

    useEffect(() => {
        void loadData()
    }, [loadData])

    // Handle voucher click for drill-down
    const handleVoucherClick = useCallback(async (entry: CashBookEntry) => {
        if (!selectedAccountId) return

        try {
            setVoucherLoading(true)
            setVoucherModalOpen(true)
            const voucher = await getVoucherDetail(Number(selectedAccountId), entry.voucherId)
            setSelectedVoucher(voucher)
        } catch (error) {
            console.error('Failed to load voucher detail:', error)
            toast.error('Failed to load voucher detail')
            setVoucherModalOpen(false)
        } finally {
            setVoucherLoading(false)
        }
    }, [selectedAccountId])

    // Handle export
    const handleExport = useCallback(async (exportFormat: 'excel' | 'pdf') => {
        if (!selectedAccountId) {
            toast.error('Please select a bank account')
            return
        }

        try {
            setExporting(true)
            const result = await exportCashBook(Number(selectedAccountId), exportFormat, {
                dateFrom: dateFrom ? format(dateFrom, 'yyyy-MM-dd') : undefined,
                dateTo: dateTo ? format(dateTo, 'yyyy-MM-dd') : undefined,
                type: transactionType,
            })

            if (result instanceof Blob) {
                const extension = exportFormat === 'excel' ? 'xlsx' : 'pdf'
                const filename = `cash_book_${selectedAccountId}_${format(new Date(), 'yyyy-MM-dd')}.${extension}`
                downloadBlob(result, filename)
                toast.success('Export downloaded successfully')
            } else {
                // Async export
                const job = result as CashBookExportJob
                toast.info(`Export queued (${job.recordCount} records). Check back later for download.`)
            }
        } catch (error) {
            console.error('Failed to export cash book:', error)
            toast.error('Failed to export cash book')
        } finally {
            setExporting(false)
        }
    }, [selectedAccountId, dateFrom, dateTo, transactionType])

    const selectedAccount = bankAccounts.find((a) => String(a.id) === selectedAccountId)
    const totalPages = data ? Math.ceil(data.totalCount / pageSize) : 0

    return (
        <div className="space-y-6 p-6">
            {/* Header */}
            <div className="flex items-center justify-between">
                <div>
                    <h1 className="text-3xl font-bold">Cash Book / Bank Book</h1>
                    <p className="text-muted-foreground mt-1">
                        View transactions with running balance for cash and bank accounts
                    </p>
                </div>
            </div>

            {/* Filters */}
            <Card>
                <CardHeader>
                    <CardTitle>Filters</CardTitle>
                </CardHeader>
                <CardContent className="space-y-4">
                    <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
                        <div className="space-y-2">
                            <Label htmlFor="account">Bank/Cash Account</Label>
                            <Select
                                value={selectedAccountId}
                                onValueChange={(value) => {
                                    setSelectedAccountId(value)
                                    setPage(0)
                                }}
                            >
                                <SelectTrigger id="account">
                                    <SelectValue placeholder="Select account" />
                                </SelectTrigger>
                                <SelectContent>
                                    {bankAccounts.map((account) => (
                                        <SelectItem key={account.id} value={String(account.id)}>
                                            {account.bankName} - {account.accountNumber}
                                            <Badge variant="outline" className="ml-2">
                                                {account.type}
                                            </Badge>
                                        </SelectItem>
                                    ))}
                                </SelectContent>
                            </Select>
                        </div>

                        <div className="space-y-2">
                            <Label>Date From</Label>
                            <DatePicker
                                date={dateFrom}
                                onDateChange={(date) => {
                                    setDateFrom(date)
                                    setPage(0)
                                }}
                                placeholder="Start date"
                            />
                        </div>

                        <div className="space-y-2">
                            <Label>Date To</Label>
                            <DatePicker
                                date={dateTo}
                                onDateChange={(date) => {
                                    setDateTo(date)
                                    setPage(0)
                                }}
                                placeholder="End date"
                            />
                        </div>

                        <div className="space-y-2">
                            <Label htmlFor="type">Transaction Type</Label>
                            <Select
                                value={transactionType}
                                onValueChange={(value: 'all' | 'receipt' | 'payment') => {
                                    setTransactionType(value)
                                    setPage(0)
                                }}
                            >
                                <SelectTrigger id="type">
                                    <SelectValue />
                                </SelectTrigger>
                                <SelectContent>
                                    <SelectItem value="all">All Transactions</SelectItem>
                                    <SelectItem value="receipt">Receipts Only</SelectItem>
                                    <SelectItem value="payment">Payments Only</SelectItem>
                                </SelectContent>
                            </Select>
                        </div>
                    </div>

                    <div className="flex flex-wrap items-end gap-4">
                        <div className="flex-1 min-w-[200px] space-y-2">
                            <Label htmlFor="search">Search Reference/Description</Label>
                            <div className="relative">
                                <Search className="absolute left-2 top-2.5 h-4 w-4 text-muted-foreground" />
                                <Input
                                    id="search"
                                    placeholder="Search..."
                                    value={searchTerm}
                                    onChange={(e) => {
                                        setSearchTerm(e.target.value)
                                        setPage(0)
                                    }}
                                    className="pl-8"
                                />
                            </div>
                        </div>

                        <div className="flex gap-2">
                            <Button onClick={loadData} variant="outline" disabled={loading}>
                                <RefreshCw className={`h-4 w-4 mr-2 ${loading ? 'animate-spin' : ''}`} />
                                Refresh
                            </Button>
                            <Button
                                onClick={() => handleExport('excel')}
                                disabled={exporting || !selectedAccountId}
                            >
                                <Download className="h-4 w-4 mr-2" />
                                Export Excel
                            </Button>
                            <Button
                                onClick={() => handleExport('pdf')}
                                variant="outline"
                                disabled={exporting || !selectedAccountId}
                            >
                                <Download className="h-4 w-4 mr-2" />
                                Export PDF
                            </Button>
                        </div>
                    </div>
                </CardContent>
            </Card>

            {/* Summary Cards */}
            {data && (
                <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
                    <Card>
                        <CardContent className="pt-6">
                            <div className="text-sm text-muted-foreground">Opening Balance</div>
                            <div className="text-2xl font-bold">{formatCurrency(data.openingBalance)}</div>
                        </CardContent>
                    </Card>
                    <Card>
                        <CardContent className="pt-6">
                            <div className="text-sm text-muted-foreground">Total Inflow</div>
                            <div className="text-2xl font-bold text-green-600">
                                +{formatCurrency(data.totalInflow)}
                            </div>
                        </CardContent>
                    </Card>
                    <Card>
                        <CardContent className="pt-6">
                            <div className="text-sm text-muted-foreground">Total Outflow</div>
                            <div className="text-2xl font-bold text-red-600">
                                -{formatCurrency(data.totalOutflow)}
                            </div>
                        </CardContent>
                    </Card>
                    <Card>
                        <CardContent className="pt-6">
                            <div className="text-sm text-muted-foreground">Closing Balance</div>
                            <div className="text-2xl font-bold">{formatCurrency(data.closingBalance)}</div>
                        </CardContent>
                    </Card>
                </div>
            )}

            {/* Transactions Table */}
            <Card>
                <CardHeader>
                    <CardTitle>
                        Transactions
                        {selectedAccount && (
                            <span className="font-normal text-muted-foreground ml-2">
                                - {selectedAccount.bankName} ({selectedAccount.accountNumber})
                            </span>
                        )}
                    </CardTitle>
                </CardHeader>
                <CardContent>
                    {loading ? (
                        <div className="space-y-2">
                            {[...Array(5)].map((_, i) => (
                                <Skeleton key={i} className="h-12 w-full" />
                            ))}
                        </div>
                    ) : data ? (
                        <>
                            <div className="rounded-md border">
                                <Table>
                                    <TableHeader>
                                        <TableRow>
                                            <TableHead className="w-[100px]">Date</TableHead>
                                            <TableHead className="w-[120px]">Voucher #</TableHead>
                                            <TableHead>Description</TableHead>
                                            <TableHead className="text-right">Debit (Inflow)</TableHead>
                                            <TableHead className="text-right">Credit (Outflow)</TableHead>
                                            <TableHead className="text-right">Balance</TableHead>
                                            <TableHead className="w-[80px]">Action</TableHead>
                                        </TableRow>
                                    </TableHeader>
                                    <TableBody>
                                        {data.transactions.length === 0 ? (
                                            <TableRow>
                                                <TableCell colSpan={7} className="text-center text-muted-foreground">
                                                    No transactions found
                                                </TableCell>
                                            </TableRow>
                                        ) : (
                                            data.transactions.map((entry) => (
                                                <TableRow key={entry.voucherId}>
                                                    <TableCell>{formatDate(entry.transactionDate)}</TableCell>
                                                    <TableCell className="font-mono">{entry.voucherNumber}</TableCell>
                                                    <TableCell>
                                                        <div>{entry.description}</div>
                                                        {entry.counterpartyName && (
                                                            <div className="text-xs text-muted-foreground">
                                                                {entry.counterpartyType}: {entry.counterpartyName}
                                                            </div>
                                                        )}
                                                    </TableCell>
                                                    <TableCell className="text-right">
                                                        {entry.debit > 0 && (
                                                            <span className="text-green-600">
                                                                {formatCurrency(entry.debit)}
                                                            </span>
                                                        )}
                                                    </TableCell>
                                                    <TableCell className="text-right">
                                                        {entry.credit > 0 && (
                                                            <span className="text-red-600">
                                                                {formatCurrency(entry.credit)}
                                                            </span>
                                                        )}
                                                    </TableCell>
                                                    <TableCell className="text-right font-medium">
                                                        {formatCurrency(entry.runningBalance)}
                                                    </TableCell>
                                                    <TableCell>
                                                        <Button
                                                            variant="ghost"
                                                            size="sm"
                                                            onClick={() => handleVoucherClick(entry)}
                                                        >
                                                            <Eye className="h-4 w-4" />
                                                        </Button>
                                                    </TableCell>
                                                </TableRow>
                                            ))
                                        )}
                                    </TableBody>
                                </Table>
                            </div>

                            {/* Pagination */}
                            <div className="flex items-center justify-between mt-4">
                                <div className="text-sm text-muted-foreground">
                                    Showing {data.transactions.length > 0 ? page * pageSize + 1 : 0} to{' '}
                                    {Math.min((page + 1) * pageSize, data.totalCount)} of {data.totalCount} transactions
                                </div>
                                <div className="flex items-center gap-2">
                                    <Label htmlFor="pageSize" className="text-sm">
                                        Per page:
                                    </Label>
                                    <Select
                                        value={String(pageSize)}
                                        onValueChange={(value) => {
                                            setPageSize(Number(value))
                                            setPage(0)
                                        }}
                                    >
                                        <SelectTrigger id="pageSize" className="w-[80px]">
                                            <SelectValue />
                                        </SelectTrigger>
                                        <SelectContent>
                                            {PAGE_SIZE_OPTIONS.map((size) => (
                                                <SelectItem key={size} value={String(size)}>
                                                    {size}
                                                </SelectItem>
                                            ))}
                                        </SelectContent>
                                    </Select>
                                    <div className="flex gap-1">
                                        <Button
                                            variant="outline"
                                            size="sm"
                                            onClick={() => setPage((p) => Math.max(0, p - 1))}
                                            disabled={page === 0}
                                        >
                                            <ChevronLeft className="h-4 w-4" />
                                        </Button>
                                        <div className="flex items-center px-3 text-sm">
                                            Page {page + 1} of {totalPages || 1}
                                        </div>
                                        <Button
                                            variant="outline"
                                            size="sm"
                                            onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
                                            disabled={page >= totalPages - 1}
                                        >
                                            <ChevronRight className="h-4 w-4" />
                                        </Button>
                                    </div>
                                </div>
                            </div>
                        </>
                    ) : (
                        <div className="text-center text-muted-foreground py-8">
                            Select a bank/cash account to view transactions
                        </div>
                    )}
                </CardContent>
            </Card>

            {/* Voucher Detail Modal */}
            <Dialog open={voucherModalOpen} onOpenChange={setVoucherModalOpen}>
                <DialogContent className="max-w-3xl">
                    <DialogHeader>
                        <DialogTitle>
                            Voucher Details
                            {selectedVoucher && ` - ${selectedVoucher.voucherNumber}`}
                        </DialogTitle>
                    </DialogHeader>
                    {voucherLoading ? (
                        <div className="space-y-2">
                            {[...Array(3)].map((_, i) => (
                                <Skeleton key={i} className="h-8 w-full" />
                            ))}
                        </div>
                    ) : selectedVoucher ? (
                        <div className="space-y-4">
                            <div className="grid grid-cols-2 gap-4 text-sm">
                                <div>
                                    <span className="text-muted-foreground">Voucher Number:</span>{' '}
                                    <span className="font-medium">{selectedVoucher.voucherNumber}</span>
                                </div>
                                <div>
                                    <span className="text-muted-foreground">Date:</span>{' '}
                                    <span className="font-medium">{formatDate(selectedVoucher.voucherDate)}</span>
                                </div>
                                <div>
                                    <span className="text-muted-foreground">Status:</span>{' '}
                                    <Badge variant={selectedVoucher.status === 'posted' ? 'default' : 'secondary'}>
                                        {selectedVoucher.status}
                                    </Badge>
                                </div>
                                <div>
                                    <span className="text-muted-foreground">Currency:</span>{' '}
                                    <span className="font-medium">{selectedVoucher.currency}</span>
                                </div>
                            </div>
                            <div>
                                <span className="text-muted-foreground">Description:</span>{' '}
                                <span>{selectedVoucher.description}</span>
                            </div>

                            <div className="rounded-md border">
                                <Table>
                                    <TableHeader>
                                        <TableRow>
                                            <TableHead>#</TableHead>
                                            <TableHead>Account</TableHead>
                                            <TableHead>Description</TableHead>
                                            <TableHead className="text-right">Debit</TableHead>
                                            <TableHead className="text-right">Credit</TableHead>
                                        </TableRow>
                                    </TableHeader>
                                    <TableBody>
                                        {selectedVoucher.lines.map((line) => (
                                            <TableRow key={line.id}>
                                                <TableCell>{line.lineNumber}</TableCell>
                                                <TableCell>
                                                    <div className="font-mono">{line.accountCode}</div>
                                                    <div className="text-xs text-muted-foreground">{line.accountName}</div>
                                                </TableCell>
                                                <TableCell>{line.description || '-'}</TableCell>
                                                <TableCell className="text-right">
                                                    {line.debit > 0 ? formatCurrency(line.debit) : '-'}
                                                </TableCell>
                                                <TableCell className="text-right">
                                                    {line.credit > 0 ? formatCurrency(line.credit) : '-'}
                                                </TableCell>
                                            </TableRow>
                                        ))}
                                        <TableRow className="bg-muted/50 font-bold">
                                            <TableCell colSpan={3}>TOTAL</TableCell>
                                            <TableCell className="text-right">
                                                {formatCurrency(selectedVoucher.totalDebit)}
                                            </TableCell>
                                            <TableCell className="text-right">
                                                {formatCurrency(selectedVoucher.totalCredit)}
                                            </TableCell>
                                        </TableRow>
                                    </TableBody>
                                </Table>
                            </div>
                        </div>
                    ) : null}
                </DialogContent>
            </Dialog>
        </div>
    )
}
