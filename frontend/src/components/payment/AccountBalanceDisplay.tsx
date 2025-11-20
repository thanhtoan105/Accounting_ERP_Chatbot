'use client'

import * as React from 'react'
import { useEffect, useState } from 'react'
import { AlertTriangle, Loader2, TrendingDown, TrendingUp } from 'lucide-react'

import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { cn } from '@/lib/utils'
import { getBalanceTooltip } from '@/features/bankaccounts/services/bankAccount'
import type { BalanceTooltip } from '@/types/bankAccount'

export interface AccountBalanceDisplayProps {
  accountId: number | null
  paymentAmount: number
  accountType?: 'CASH' | 'BANK'
  className?: string
}

export function AccountBalanceDisplay({
  accountId,
  paymentAmount,
  accountType,
  className,
}: AccountBalanceDisplayProps) {
  const [balance, setBalance] = useState<BalanceTooltip | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!accountId) {
      setBalance(null)
      setError(null)
      return
    }

    async function fetchBalance() {
      setLoading(true)
      setError(null)
      try {
        const data = await getBalanceTooltip(accountId)
        setBalance(data)
      } catch (err) {
        console.error('Failed to fetch account balance', err)
        setError('Failed to load account balance')
      } finally {
        setLoading(false)
      }
    }

    fetchBalance()
    // Note: We intentionally don't include paymentAmount in deps to avoid excessive API calls
    // The projected balance calculation happens in render based on current balance and paymentAmount
  }, [accountId])

  const formatCurrency = (value: number) => {
    return new Intl.NumberFormat('vi-VN', {
      style: 'currency',
      currency: 'VND',
      minimumFractionDigits: 0,
      maximumFractionDigits: 0,
    }).format(value)
  }

  const currentBalance = balance?.currentBalance ?? 0
  const projectedBalance = currentBalance - paymentAmount
  const isOverdraft = projectedBalance < 0
  const isLowBalance = projectedBalance >= 0 && projectedBalance < 1000000 // Less than 1M VND

  if (!accountId) {
    return (
      <Card className={cn(className)}>
        <CardHeader>
          <CardTitle className="text-sm">Account Balance</CardTitle>
        </CardHeader>
        <CardContent>
          <p className="text-sm text-muted-foreground">Select an account to view balance</p>
        </CardContent>
      </Card>
    )
  }

  if (loading) {
    return (
      <Card className={cn(className)}>
        <CardHeader>
          <CardTitle className="text-sm">Account Balance</CardTitle>
        </CardHeader>
        <CardContent className="space-y-2">
          <Skeleton className="h-8 w-full" />
          <Skeleton className="h-6 w-3/4" />
        </CardContent>
      </Card>
    )
  }

  if (error) {
    return (
      <Card className={cn(className)}>
        <CardHeader>
          <CardTitle className="text-sm">Account Balance</CardTitle>
        </CardHeader>
        <CardContent>
          <Alert variant="destructive">
            <AlertTriangle className="h-4 w-4" />
            <AlertTitle>Error</AlertTitle>
            <AlertDescription>{error}</AlertDescription>
          </Alert>
        </CardContent>
      </Card>
    )
  }

  return (
    <Card className={cn(className)}>
      <CardHeader>
        <CardTitle className="text-sm">Account Balance</CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        <div className="space-y-2">
          <div className="flex justify-between items-center">
            <span className="text-sm text-muted-foreground">Current Balance</span>
            <span className="text-lg font-semibold">{formatCurrency(currentBalance)}</span>
          </div>
          <div className="flex justify-between items-center">
            <span className="text-sm text-muted-foreground">Payment Amount</span>
            <span className="text-sm font-medium text-muted-foreground">
              -{formatCurrency(paymentAmount)}
            </span>
          </div>
          <div className="border-t pt-2">
            <div className="flex justify-between items-center">
              <span className="text-sm font-medium">Projected Balance</span>
              <div className="flex items-center gap-2">
                {isOverdraft ? (
                  <TrendingDown className="h-4 w-4 text-destructive" />
                ) : isLowBalance ? (
                  <AlertTriangle className="h-4 w-4 text-orange-600" />
                ) : (
                  <TrendingUp className="h-4 w-4 text-green-600" />
                )}
                <span
                  className={cn(
                    'text-xl font-bold',
                    isOverdraft && 'text-destructive',
                    isLowBalance && !isOverdraft && 'text-orange-600',
                  )}
                >
                  {formatCurrency(projectedBalance)}
                </span>
              </div>
            </div>
          </div>
        </div>

        {isOverdraft && (
          <Alert variant="destructive">
            <AlertTriangle className="h-4 w-4" />
            <AlertTitle>Overdraft Warning</AlertTitle>
            <AlertDescription>
              This payment will result in a negative balance. Please ensure sufficient funds are
              available.
            </AlertDescription>
          </Alert>
        )}

        {isLowBalance && !isOverdraft && (
          <Alert>
            <AlertTriangle className="h-4 w-4" />
            <AlertTitle>Low Balance Warning</AlertTitle>
            <AlertDescription>
              This payment will leave a low balance. Consider reviewing your cash flow.
            </AlertDescription>
          </Alert>
        )}

        {balance && (
          <div className="text-xs text-muted-foreground pt-2 border-t">
            <p>Period: {balance.currentPeriod}</p>
            <p>Prior Balance: {formatCurrency(balance.priorBalance)}</p>
          </div>
        )}
      </CardContent>
    </Card>
  )
}
