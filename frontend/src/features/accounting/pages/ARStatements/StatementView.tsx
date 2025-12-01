'use client'

import { useCallback, useEffect, useState } from 'react'
import { FileText, RefreshCw, Download, Mail, Upload, Calendar } from 'lucide-react'
import { format } from 'date-fns'
import { toast } from 'sonner'

import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { Badge } from '@/components/ui/badge'
import { Skeleton } from '@/components/ui/skeleton'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { DatePicker } from '@/components/ui/date-picker'
import { arStatementService } from '@/services/arStatement'
import { getCustomers } from '@/features/customers/services/customer'
import { ExportStatementDialog } from './ExportStatementDialog'
import { SendStatementDialog } from './SendStatementDialog'
import { ReconciliationImportDialog } from './ReconciliationImportDialog'
import type {
  ARStatementSummary,
  ARStatementDetailed,
  StatementFormat,
  ExportFormat,
} from '@/types/arStatement'
import type { Customer } from '@/types/customer'

export function StatementView() {
  const [loading, setLoading] = useState(false)
  const [customers, setCustomers] = useState<Customer[]>([])
  const [selectedCustomerId, setSelectedCustomerId] = useState<number | null>(null)
  const [format, setFormat] = useState<StatementFormat>('SUMMARY')
  const [asOfDate, setAsOfDate] = useState<Date>(new Date())
  const [statement, setStatement] = useState<ARStatementSummary | ARStatementDetailed | null>(null)
  const [error, setError] = useState<string | null>(null)

  // Dialog states
  const [exportDialogOpen, setExportDialogOpen] = useState(false)
  const [sendDialogOpen, setSendDialogOpen] = useState(false)
  const [importDialogOpen, setImportDialogOpen] = useState(false)

  useEffect(() => {
    loadCustomers()
  }, [])

  const loadCustomers = async () => {
    try {
      const response = await getCustomers({ page: 1, size: 1000 })
      setCustomers(response.data || [])
    } catch (err: any) {
      toast.error('Failed to load customers', {
        description: err?.message || 'Unknown error',
      })
    }
  }

  const loadStatement = useCallback(async () => {
    if (!selectedCustomerId) {
      setStatement(null)
      return
    }

    try {
      setLoading(true)
      setError(null)
      const result = await arStatementService.getStatement({
        customerId: selectedCustomerId,
        format,
        asOfDate: format(asOfDate, 'yyyy-MM-dd'),
      })
      setStatement(result)
    } catch (err: any) {
      const message = err?.message || 'Failed to load statement'
      setError(message)
      toast.error('Failed to load statement', {
        description: message,
      })
    } finally {
      setLoading(false)
    }
  }, [selectedCustomerId, format, asOfDate])

  useEffect(() => {
    loadStatement()
  }, [loadStatement])

  const handleRefresh = () => {
    loadStatement()
    toast.success('Statement refreshed')
  }

  const handleExport = async (exportFormat: ExportFormat) => {
    if (!selectedCustomerId) {
      toast.error('Please select a customer')
      return
    }

    try {
      const blob = await arStatementService.exportStatement({
        customerId: selectedCustomerId,
        format: exportFormat,
        statementFormat: format,
        asOfDate: format(asOfDate, 'yyyy-MM-dd'),
      })

      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      const customer = customers.find((c) => c.id === selectedCustomerId)
      const customerCode = customer?.code || `CUST${selectedCustomerId}`
      const dateStr = format(asOfDate, 'yyyy-MM-dd')
      a.download = `Statement_${customerCode}_${dateStr}.${exportFormat === 'EXCEL' ? 'xlsx' : 'pdf'}`
      document.body.appendChild(a)
      a.click()
      document.body.removeChild(a)
      URL.revokeObjectURL(url)

      toast.success(`Statement exported as ${exportFormat}`)
    } catch (err: any) {
      toast.error('Failed to export statement', {
        description: err?.message || 'Unknown error',
      })
    }
  }

  const selectedCustomer = customers.find((c) => c.id === selectedCustomerId)

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold tracking-tight flex items-center gap-2">
            <FileText className="h-6 w-6 text-primary" />
            Customer Statements
          </h1>
          <p className="text-muted-foreground">
            Generate and view customer account statements and reconcile discrepancies
          </p>
        </div>
        <div className="flex items-center gap-2">
          <Button
            variant="outline"
            onClick={handleRefresh}
            disabled={loading || !selectedCustomerId}
            data-testid="refresh-button"
          >
            <RefreshCw className={`mr-2 h-4 w-4 ${loading ? 'animate-spin' : ''}`} />
            Refresh
          </Button>
          {selectedCustomerId && (
            <>
              <Button
                variant="outline"
                onClick={() => setImportDialogOpen(true)}
                disabled={loading}
                data-testid="import-reconciliation-button"
              >
                <Upload className="mr-2 h-4 w-4" />
                Import Reconciliation
              </Button>
              <Button
                variant="outline"
                onClick={() => setExportDialogOpen(true)}
                disabled={loading || !statement}
                data-testid="export-button"
              >
                <Download className="mr-2 h-4 w-4" />
                Export
              </Button>
              <Button
                onClick={() => setSendDialogOpen(true)}
                disabled={loading || !statement}
                data-testid="send-statement-button"
              >
                <Mail className="mr-2 h-4 w-4" />
                Send to Customer
              </Button>
            </>
          )}
        </div>
      </div>

      {/* Filters */}
      <div className="flex items-center gap-4">
        <div className="flex-1">
          <Select
            value={selectedCustomerId?.toString() || ''}
            onValueChange={(value) => setSelectedCustomerId(value ? Number(value) : null)}
          >
            <SelectTrigger className="w-full" data-testid="customer-picker">
              <SelectValue placeholder="Select customer..." />
            </SelectTrigger>
            <SelectContent>
              {customers.map((customer) => (
                <SelectItem
                  key={customer.id}
                  value={customer.id.toString()}
                  data-testid="customer-option"
                >
                  {customer.name} ({customer.code})
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
        <Select value={format} onValueChange={(value) => setFormat(value as StatementFormat)}>
          <SelectTrigger className="w-[180px]" data-testid="format-toggle">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="SUMMARY">Summary</SelectItem>
            <SelectItem value="DETAILED">Detailed</SelectItem>
          </SelectContent>
        </Select>
        <DatePicker
          value={format(asOfDate, 'yyyy-MM-dd')}
          onChange={(value) => value && setAsOfDate(new Date(value))}
          placeholder="As of Date"
        />
      </div>

      {/* Error Alert */}
      {error && (
        <Alert variant="destructive">
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      )}

      {/* Statement Display */}
      {loading ? (
        <div className="rounded-md border">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Invoice #</TableHead>
                <TableHead>Date</TableHead>
                <TableHead>Amount</TableHead>
                <TableHead>Paid</TableHead>
                <TableHead>Balance</TableHead>
                <TableHead>Running Balance</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {Array.from({ length: 5 }).map((_, i) => (
                <TableRow key={i}>
                  <TableCell>
                    <Skeleton className="h-4 w-[120px]" />
                  </TableCell>
                  <TableCell>
                    <Skeleton className="h-4 w-[100px]" />
                  </TableCell>
                  <TableCell>
                    <Skeleton className="h-4 w-[100px]" />
                  </TableCell>
                  <TableCell>
                    <Skeleton className="h-4 w-[100px]" />
                  </TableCell>
                  <TableCell>
                    <Skeleton className="h-4 w-[100px]" />
                  </TableCell>
                  <TableCell>
                    <Skeleton className="h-4 w-[100px]" />
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>
      ) : statement ? (
        <div className="space-y-4">
          {/* Customer Header */}
          <div className="rounded-md border p-4 bg-muted/50">
            <div className="grid grid-cols-2 gap-4">
              <div>
                <h3 className="font-semibold" data-testid="statement-customer-name">
                  {statement.customerName}
                </h3>
                <p className="text-sm text-muted-foreground">{statement.customerCode}</p>
                {statement.customerAddress && (
                  <p
                    className="text-sm text-muted-foreground mt-1"
                    data-testid="statement-customer-address"
                  >
                    {statement.customerAddress}
                  </p>
                )}
                {statement.customerTaxCode && (
                  <p
                    className="text-sm text-muted-foreground"
                    data-testid="statement-customer-tax-code"
                  >
                    Tax Code: {statement.customerTaxCode}
                  </p>
                )}
              </div>
              <div className="text-right">
                <p className="text-sm text-muted-foreground">As of Date</p>
                <p className="font-semibold">
                  {format(new Date(statement.asOfDate), 'dd/MM/yyyy')}
                </p>
                {statement.generatedAt && (
                  <>
                    <p className="text-sm text-muted-foreground mt-2">Generated</p>
                    <p className="text-sm">
                      {format(new Date(statement.generatedAt), 'dd/MM/yyyy HH:mm')}
                    </p>
                    {statement.generatedByName && (
                      <p className="text-xs text-muted-foreground">
                        by {statement.generatedByName}
                      </p>
                    )}
                  </>
                )}
              </div>
            </div>
          </div>

          {/* Summary View Table */}
          {format === 'SUMMARY' && 'invoices' in statement && (
            <div className="rounded-md border">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Invoice #</TableHead>
                    <TableHead>Date</TableHead>
                    <TableHead className="text-right">Invoice Amount</TableHead>
                    <TableHead className="text-right">Amount Paid</TableHead>
                    <TableHead className="text-right">Balance</TableHead>
                    <TableHead className="text-right">Running Balance</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {statement.invoices.length === 0 ? (
                    <TableRow>
                      <TableCell colSpan={6} className="text-center py-10 text-muted-foreground">
                        No invoices found for this customer.
                      </TableCell>
                    </TableRow>
                  ) : (
                    <>
                      {statement.invoices.map((invoice) => (
                        <TableRow key={invoice.invoiceId} data-testid="statement-row">
                          <TableCell className="font-medium" data-testid="statement-invoice-number">
                            {invoice.invoiceNumber}
                          </TableCell>
                          <TableCell>
                            {format(new Date(invoice.invoiceDate), 'dd/MM/yyyy')}
                          </TableCell>
                          <TableCell className="text-right" data-testid="statement-invoice-amount">
                            {invoice.invoiceAmount.toLocaleString('vi-VN')}₫
                          </TableCell>
                          <TableCell className="text-right">
                            {invoice.amountPaid.toLocaleString('vi-VN')}₫
                          </TableCell>
                          <TableCell className="text-right" data-testid="statement-balance">
                            {invoice.balance.toLocaleString('vi-VN')}₫
                          </TableCell>
                          <TableCell className="text-right font-semibold">
                            {invoice.runningBalance.toLocaleString('vi-VN')}₫
                          </TableCell>
                        </TableRow>
                      ))}
                      <TableRow
                        className="bg-muted/50 font-semibold"
                        data-testid="statement-totals"
                      >
                        <TableCell colSpan={2}>Totals</TableCell>
                        <TableCell className="text-right" data-testid="total-invoices">
                          {statement.totalInvoices.toLocaleString('vi-VN')}₫
                        </TableCell>
                        <TableCell className="text-right" data-testid="total-paid">
                          {statement.totalPaid.toLocaleString('vi-VN')}₫
                        </TableCell>
                        <TableCell
                          colSpan={2}
                          className="text-right"
                          data-testid="total-outstanding"
                        >
                          {statement.totalOutstanding.toLocaleString('vi-VN')}₫
                        </TableCell>
                      </TableRow>
                    </>
                  )}
                </TableBody>
              </Table>
            </div>
          )}

          {/* Detailed View Table */}
          {format === 'DETAILED' && 'transactions' in statement && (
            <div className="rounded-md border">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Type</TableHead>
                    <TableHead>Date</TableHead>
                    <TableHead>Reference</TableHead>
                    <TableHead>Description</TableHead>
                    <TableHead className="text-right">Debit</TableHead>
                    <TableHead className="text-right">Credit</TableHead>
                    <TableHead className="text-right">Running Balance</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {statement.transactions.length === 0 ? (
                    <TableRow>
                      <TableCell colSpan={7} className="text-center py-10 text-muted-foreground">
                        No transactions found for this customer.
                      </TableCell>
                    </TableRow>
                  ) : (
                    <>
                      {statement.transactions.map((tx, idx) => (
                        <TableRow key={idx}>
                          <TableCell>
                            <Badge variant={tx.type === 'INVOICE' ? 'default' : 'secondary'}>
                              {tx.type}
                            </Badge>
                          </TableCell>
                          <TableCell>
                            {format(new Date(tx.transactionDate), 'dd/MM/yyyy')}
                          </TableCell>
                          <TableCell>{tx.reference || '-'}</TableCell>
                          <TableCell>{tx.description || '-'}</TableCell>
                          <TableCell className="text-right">
                            {tx.debit > 0 ? tx.debit.toLocaleString('vi-VN') + '₫' : '-'}
                          </TableCell>
                          <TableCell className="text-right">
                            {tx.credit > 0 ? tx.credit.toLocaleString('vi-VN') + '₫' : '-'}
                          </TableCell>
                          <TableCell className="text-right font-semibold">
                            {tx.runningBalance.toLocaleString('vi-VN')}₫
                          </TableCell>
                        </TableRow>
                      ))}
                      <TableRow className="bg-muted/50 font-semibold">
                        <TableCell colSpan={4}>Totals</TableCell>
                        <TableCell className="text-right">
                          {statement.totalInvoices.toLocaleString('vi-VN')}₫
                        </TableCell>
                        <TableCell className="text-right">
                          {statement.totalPaid.toLocaleString('vi-VN')}₫
                        </TableCell>
                        <TableCell className="text-right">
                          {statement.totalOutstanding.toLocaleString('vi-VN')}₫
                        </TableCell>
                      </TableRow>
                    </>
                  )}
                </TableBody>
              </Table>
            </div>
          )}
        </div>
      ) : (
        <div className="rounded-md border p-10 text-center text-muted-foreground">
          <FileText className="h-12 w-12 mx-auto mb-4 opacity-50" />
          <p>Select a customer to view their statement</p>
        </div>
      )}

      {/* Dialogs */}
      {selectedCustomerId && (
        <>
          <ExportStatementDialog
            open={exportDialogOpen}
            onOpenChange={setExportDialogOpen}
            customerId={selectedCustomerId}
            format={format}
            asOfDate={asOfDate}
            onExport={handleExport}
          />
          <SendStatementDialog
            open={sendDialogOpen}
            onOpenChange={setSendDialogOpen}
            customerId={selectedCustomerId}
            customerName={selectedCustomer?.name || ''}
            format={format}
            asOfDate={asOfDate}
            onSuccess={() => {
              setSendDialogOpen(false)
              loadStatement()
            }}
          />
          <ReconciliationImportDialog
            open={importDialogOpen}
            onOpenChange={setImportDialogOpen}
            customerId={selectedCustomerId}
            customerName={selectedCustomer?.name || ''}
            onSuccess={() => {
              setImportDialogOpen(false)
              toast.success('Reconciliation imported successfully')
            }}
          />
        </>
      )}
    </div>
  )
}
