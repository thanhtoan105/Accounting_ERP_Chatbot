'use client'

import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'

interface PurchaseBillFormTotalsProps {
  totalAmount: number
  totalVAT: number
}

export function PurchaseBillFormTotals({ totalAmount, totalVAT }: PurchaseBillFormTotalsProps) {
  const formatCurrency = (value: number) => {
    return new Intl.NumberFormat('vi-VN', {
      style: 'currency',
      currency: 'VND',
      minimumFractionDigits: 0,
      maximumFractionDigits: 0,
    }).format(value)
  }

  return (
    <Card className="shadow-sm border-border/60">
      <CardHeader className="bg-muted/20 pb-4">
        <CardTitle className="text-lg font-medium">Summary</CardTitle>
      </CardHeader>
      <CardContent className="pt-6">
        <div className="flex flex-col gap-4 sm:flex-row sm:justify-end sm:gap-12">
          <div className="flex flex-col gap-1 text-right">
            <span className="text-sm text-muted-foreground">Total Amount</span>
            <span className="text-lg font-medium">{formatCurrency(totalAmount)}</span>
          </div>
          <div className="flex flex-col gap-1 text-right">
            <span className="text-sm text-muted-foreground">Total VAT</span>
            <span className="text-lg font-medium">{formatCurrency(totalVAT)}</span>
          </div>
          <div className="flex flex-col gap-1 text-right pt-4 sm:pt-0 sm:pl-8 sm:border-l">
            <span className="text-sm font-semibold text-primary">Grand Total</span>
            <span className="text-2xl font-bold tracking-tight">
              {formatCurrency(totalAmount + totalVAT)}
            </span>
          </div>
        </div>
      </CardContent>
    </Card>
  )
}
