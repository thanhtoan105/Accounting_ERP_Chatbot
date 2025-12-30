'use client'

import { useTranslation } from 'react-i18next'
import { Area, AreaChart, CartesianGrid, XAxis, YAxis } from 'recharts'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import {
  ChartContainer,
  ChartTooltip,
  ChartTooltipContent,
  type ChartConfig,
} from '@/components/ui/chart'
import { Skeleton } from '@/components/ui/skeleton'
import { useCashFlow } from '../api/useDashboardCharts'

const chartConfig = {
  inflow: {
    label: 'Inflow',
    color: 'hsl(142 76% 36%)', // green
  },
  outflow: {
    label: 'Outflow',
    color: 'hsl(221 83% 53%)', // blue
  },
} satisfies ChartConfig

function formatCurrency(value: number): string {
  if (value >= 1_000_000_000) {
    return `${(value / 1_000_000_000).toFixed(1)}B`
  }
  if (value >= 1_000_000) {
    return `${(value / 1_000_000).toFixed(0)}M`
  }
  if (value >= 1_000) {
    return `${(value / 1_000).toFixed(0)}K`
  }
  return value.toLocaleString()
}

function formatMonth(monthLabel: string): string {
  const [year, month] = monthLabel.split('-')
  const date = new Date(parseInt(year), parseInt(month) - 1)
  return date.toLocaleDateString('en-US', { month: 'short' })
}

export function CashFlowChart() {
  const { t } = useTranslation()
  const { data, isLoading, error } = useCashFlow(12)

  if (isLoading) {
    return (
      <Card>
        <CardHeader>
          <Skeleton className="h-5 w-24" />
          <Skeleton className="h-4 w-40" />
        </CardHeader>
        <CardContent>
          <Skeleton className="h-[250px] w-full" />
        </CardContent>
      </Card>
    )
  }

  if (error || !data) {
    return (
      <Card>
        <CardHeader>
          <CardTitle>{t('dashboard.cashFlow.title', 'Cash Flow')}</CardTitle>
        </CardHeader>
        <CardContent className="flex items-center justify-center h-[250px]">
          <p className="text-muted-foreground">Failed to load data</p>
        </CardContent>
      </Card>
    )
  }

  const chartData = data.data.map((item) => ({
    month: formatMonth(item.monthLabel),
    fullMonth: item.monthLabel,
    inflow: item.inflow,
    outflow: item.outflow,
    net: item.net,
  }))

  const netPositive = data.netCashFlow >= 0

  return (
    <Card>
      <CardHeader>
        <CardTitle>{t('dashboard.cashFlow.title', 'Cash Flow')}</CardTitle>
        <CardDescription>
          Net:{' '}
          <span className={netPositive ? 'text-green-600' : 'text-red-600'}>
            {netPositive ? '+' : ''}
            {formatCurrency(data.netCashFlow)} đ
          </span>
        </CardDescription>
      </CardHeader>
      <CardContent className="pb-4">
        <ChartContainer config={chartConfig} className="h-[250px] w-full">
          <AreaChart data={chartData} margin={{ left: 0, right: 12, top: 12 }}>
            <defs>
              <linearGradient id="fillInflow" x1="0" y1="0" x2="0" y2="1">
                <stop offset="5%" stopColor="var(--color-inflow)" stopOpacity={0.8} />
                <stop offset="95%" stopColor="var(--color-inflow)" stopOpacity={0.1} />
              </linearGradient>
              <linearGradient id="fillOutflow" x1="0" y1="0" x2="0" y2="1">
                <stop offset="5%" stopColor="var(--color-outflow)" stopOpacity={0.8} />
                <stop offset="95%" stopColor="var(--color-outflow)" stopOpacity={0.1} />
              </linearGradient>
            </defs>
            <CartesianGrid vertical={false} strokeDasharray="3 3" className="stroke-muted" />
            <XAxis dataKey="month" tickLine={false} axisLine={false} tickMargin={8} fontSize={12} />
            <YAxis
              tickLine={false}
              axisLine={false}
              tickFormatter={formatCurrency}
              fontSize={12}
              width={50}
            />
            <ChartTooltip
              cursor={false}
              content={
                <ChartTooltipContent
                  formatter={(value) => (
                    <span className="font-mono font-medium">
                      {formatCurrency(value as number)} đ
                    </span>
                  )}
                  indicator="dot"
                />
              }
            />
            <Area
              type="natural"
              dataKey="outflow"
              stroke="var(--color-outflow)"
              strokeWidth={2}
              fill="url(#fillOutflow)"
              stackId="a"
            />
            <Area
              type="natural"
              dataKey="inflow"
              stroke="var(--color-inflow)"
              strokeWidth={2}
              fill="url(#fillInflow)"
              stackId="b"
            />
          </AreaChart>
        </ChartContainer>
      </CardContent>
    </Card>
  )
}
