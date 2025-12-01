'use client'

import { useCallback, useEffect, useState } from 'react'
import { Download, RefreshCw, Eye } from 'lucide-react'
import { toast } from 'sonner'
import { format } from 'date-fns'
import { useNavigate } from 'react-router-dom'

import { Button } from '@/components/ui/button'
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
import { Badge } from '@/components/ui/badge'
import { DatePicker } from '@/components/ui/date-picker'
import {
  getCashBookSummary,
  exportCashBookSummary,
  downloadBlob,
  type CashBookSummary,
} from '../../services/cashBook'

function formatCurrency(value: number): string {
  return new Intl.NumberFormat('vi-VN', {
    style: 'currency',
    currency: 'VND',
    minimumFractionDigits: 0,
    maximumFractionDigits: 0,
  }).format(value)
}

export function CashBookSummaryPage() {
  const navigate = useNavigate()
  const [loading, setLoading] = useState(false)
  const [dateFrom, setDateFrom] = useState<Date | undefined>(undefined)
  const [dateTo, setDateTo] = useState<Date | undefined>(undefined)
  const [data, setData] = useState<CashBookSummary | null>(null)
  const [exporting, setExporting] = useState(false)

  const loadData = useCallback(async () => {
    try {
      setLoading(true)
      const result = await getCashBookSummary({
        dateFrom: dateFrom ? format(dateFrom, 'yyyy-MM-dd') : undefined,
        dateTo: dateTo ? format(dateTo, 'yyyy-MM-dd') : undefined,
      })
      setData(result)
    } catch (error) {
      console.error('Failed to load cash book summary:', error)
      toast.error('Failed to load cash book summary')
    } finally {
      setLoading(false)
    }
  }, [dateFrom, dateTo])

  useEffect(() => {
    void loadData()
  }, [loadData])

  const handleExport = useCallback(
    async (exportFormat: 'excel' | 'pdf') => {
      try {
        setExporting(true)
        const blob = await exportCashBookSummary(exportFormat, {
          dateFrom: dateFrom ? format(dateFrom, 'yyyy-MM-dd') : undefined,
          dateTo: dateTo ? format(dateTo, 'yyyy-MM-dd') : undefined,
        })
        const extension = exportFormat === 'excel' ? 'xlsx' : 'pdf'
        const filename = `cash_book_summary_${format(new Date(), 'yyyy-MM-dd')}.${extension}`
        downloadBlob(blob, filename)
        toast.success('Export downloaded successfully')
      } catch (error) {
        console.error('Failed to export summary:', error)
        toast.error('Failed to export summary')
      } finally {
        setExporting(false)
      }
    },
    [dateFrom, dateTo],
  )

  const handleAccountClick = (bankAccountId: number) => {
    navigate(`/accounting/cash-book?accountId=${bankAccountId}`)
  }

  return (
    <div className="space-y-6 p-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold">Cash Book Summary</h1>
          <p className="text-muted-foreground mt-1">
            Multi-account aggregated view with grand totals
          </p>
        </div>
      </div>

      {/* Filters */}
      <Card>
        <CardHeader>
          <CardTitle>Filters</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="flex flex-wrap items-end gap-4">
            <div className="space-y-2">
              <Label>Date From</Label>
              <DatePicker date={dateFrom} onDateChange={setDateFrom} placeholder="Start date" />
            </div>

            <div className="space-y-2">
              <Label>Date To</Label>
              <DatePicker date={dateTo} onDateChange={setDateTo} placeholder="End date" />
            </div>

            <div className="flex gap-2">
              <Button onClick={loadData} variant="outline" disabled={loading}>
                <RefreshCw className={`h-4 w-4 mr-2 ${loading ? 'animate-spin' : ''}`} />
                Refresh
              </Button>
              <Button onClick={() => handleExport('excel')} disabled={exporting}>
                <Download className="h-4 w-4 mr-2" />
                Export Excel
              </Button>
              <Button onClick={() => handleExport('pdf')} variant="outline" disabled={exporting}>
                <Download className="h-4 w-4 mr-2" />
                Export PDF
              </Button>
            </div>
          </div>
        </CardContent>
      </Card>

      {/* Grand Totals */}
      {data && (
        <div className="grid grid-cols-1 md:grid-cols-5 gap-4">
          <Card>
            <CardContent className="pt-6">
              <div className="text-sm text-muted-foreground">Total Opening</div>
              <div className="text-xl font-bold">
                {formatCurrency(data.grandTotals.totalOpeningBalance)}
              </div>
            </CardContent>
          </Card>
          <Card>
            <CardContent className="pt-6">
              <div className="text-sm text-muted-foreground">Total Inflow</div>
              <div className="text-xl font-bold text-green-600">
                +{formatCurrency(data.grandTotals.totalInflow)}
              </div>
            </CardContent>
          </Card>
          <Card>
            <CardContent className="pt-6">
              <div className="text-sm text-muted-foreground">Total Outflow</div>
              <div className="text-xl font-bold text-red-600">
                -{formatCurrency(data.grandTotals.totalOutflow)}
              </div>
            </CardContent>
          </Card>
          <Card>
            <CardContent className="pt-6">
              <div className="text-sm text-muted-foreground">Total Closing</div>
              <div className="text-xl font-bold">
                {formatCurrency(data.grandTotals.totalClosingBalance)}
              </div>
            </CardContent>
          </Card>
          <Card>
            <CardContent className="pt-6">
              <div className="text-sm text-muted-foreground">Transactions</div>
              <div className="text-xl font-bold">{data.grandTotals.totalTransactionCount}</div>
            </CardContent>
          </Card>
        </div>
      )}

      {/* Accounts Table */}
      <Card>
        <CardHeader>
          <CardTitle>Account Balances</CardTitle>
        </CardHeader>
        <CardContent>
          {loading ? (
            <div className="space-y-2">
              {[...Array(5)].map((_, i) => (
                <Skeleton key={i} className="h-12 w-full" />
              ))}
            </div>
          ) : data ? (
            <div className="rounded-md border">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Account</TableHead>
                    <TableHead>Type</TableHead>
                    <TableHead className="text-right">Opening</TableHead>
                    <TableHead className="text-right">Inflow</TableHead>
                    <TableHead className="text-right">Outflow</TableHead>
                    <TableHead className="text-right">Closing</TableHead>
                    <TableHead className="text-center">Transactions</TableHead>
                    <TableHead className="w-[80px]">Action</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {data.accounts.length === 0 ? (
                    <TableRow>
                      <TableCell colSpan={8} className="text-center text-muted-foreground">
                        No accounts found
                      </TableCell>
                    </TableRow>
                  ) : (
                    <>
                      {data.accounts.map((account) => (
                        <TableRow key={account.bankAccountId}>
                          <TableCell>
                            <div className="font-medium">{account.bankName}</div>
                            <div className="text-xs text-muted-foreground">
                              {account.accountNumber}
                            </div>
                          </TableCell>
                          <TableCell>
                            <Badge variant="outline">{account.accountType}</Badge>
                          </TableCell>
                          <TableCell className="text-right">
                            {formatCurrency(account.openingBalance)}
                          </TableCell>
                          <TableCell className="text-right text-green-600">
                            +{formatCurrency(account.totalInflow)}
                          </TableCell>
                          <TableCell className="text-right text-red-600">
                            -{formatCurrency(account.totalOutflow)}
                          </TableCell>
                          <TableCell className="text-right font-medium">
                            {formatCurrency(account.closingBalance)}
                          </TableCell>
                          <TableCell className="text-center">{account.transactionCount}</TableCell>
                          <TableCell>
                            <Button
                              variant="ghost"
                              size="sm"
                              onClick={() => handleAccountClick(account.bankAccountId)}
                            >
                              <Eye className="h-4 w-4" />
                            </Button>
                          </TableCell>
                        </TableRow>
                      ))}
                      {/* Totals row */}
                      <TableRow className="bg-muted/50 font-bold">
                        <TableCell colSpan={2}>GRAND TOTAL</TableCell>
                        <TableCell className="text-right">
                          {formatCurrency(data.grandTotals.totalOpeningBalance)}
                        </TableCell>
                        <TableCell className="text-right text-green-600">
                          +{formatCurrency(data.grandTotals.totalInflow)}
                        </TableCell>
                        <TableCell className="text-right text-red-600">
                          -{formatCurrency(data.grandTotals.totalOutflow)}
                        </TableCell>
                        <TableCell className="text-right">
                          {formatCurrency(data.grandTotals.totalClosingBalance)}
                        </TableCell>
                        <TableCell className="text-center">
                          {data.grandTotals.totalTransactionCount}
                        </TableCell>
                        <TableCell />
                      </TableRow>
                    </>
                  )}
                </TableBody>
              </Table>
            </div>
          ) : (
            <div className="text-center text-muted-foreground py-8">Loading summary data...</div>
          )}
        </CardContent>
      </Card>
    </div>
  )
}
