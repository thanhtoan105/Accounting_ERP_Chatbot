'use client'

import * as React from 'react'
import { useTranslation } from 'react-i18next'
import { Label, Pie, PieChart, Sector } from 'recharts'
import type { PieSectorDataItem } from 'recharts/types/polar/Pie'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import {
  ChartContainer,
  ChartTooltip,
  ChartTooltipContent,
  ChartLegend,
  ChartLegendContent,
  type ChartConfig,
} from '@/components/ui/chart'
import { Skeleton } from '@/components/ui/skeleton'
import { useARAgingBuckets } from '../api/useDashboardCharts'

const BUCKET_LABELS: Record<string, string> = {
  CURRENT: 'Current',
  DAYS_1_30: '1-30 Days',
  DAYS_31_60: '31-60 Days',
  DAYS_61_90: '61-90 Days',
  DAYS_OVER_90: '90+ Days',
}

const chartConfig = {
  amount: { label: 'Amount' },
  CURRENT: { label: 'Current', color: 'hsl(142 76% 36%)' }, // green
  DAYS_1_30: { label: '1-30 Days', color: 'hsl(217 91% 60%)' }, // blue
  DAYS_31_60: { label: '31-60 Days', color: 'hsl(48 96% 53%)' }, // yellow
  DAYS_61_90: { label: '61-90 Days', color: 'hsl(25 95% 53%)' }, // orange
  DAYS_OVER_90: { label: '90+ Days', color: 'hsl(0 84% 60%)' }, // red
} satisfies ChartConfig

function formatCurrency(value: number): string {
  if (value >= 1_000_000_000) {
    return `${(value / 1_000_000_000).toFixed(1)}B`
  }
  if (value >= 1_000_000) {
    return `${(value / 1_000_000).toFixed(1)}M`
  }
  if (value >= 1_000) {
    return `${(value / 1_000).toFixed(0)}K`
  }
  return value.toLocaleString()
}

export function ARAgingChart() {
  const { t } = useTranslation()
  const { data, isLoading, error } = useARAgingBuckets()
  const [activeIndex, setActiveIndex] = React.useState<number | undefined>(undefined)

  if (isLoading) {
    return (
      <Card className="flex flex-col">
        <CardHeader className="items-center pb-0">
          <Skeleton className="h-5 w-24" />
          <Skeleton className="h-4 w-40 mt-1" />
        </CardHeader>
        <CardContent className="flex-1 pb-0">
          <Skeleton className="mx-auto aspect-square h-[250px] rounded-full" />
        </CardContent>
      </Card>
    )
  }

  if (error || !data) {
    return (
      <Card className="flex flex-col">
        <CardHeader className="items-center pb-0">
          <CardTitle>{t('dashboard.arAging.title', 'AR Aging')}</CardTitle>
        </CardHeader>
        <CardContent className="flex items-center justify-center h-[250px]">
          <p className="text-muted-foreground">Failed to load data</p>
        </CardContent>
      </Card>
    )
  }

  const chartData = data.buckets.map((bucket) => ({
    bucket: bucket.bucketKey,
    amount: bucket.amount,
    percent: bucket.percent,
    fill: `var(--color-${bucket.bucketKey})`,
  }))

  const totalOutstanding = data.totalOutstanding

  return (
    <Card className="flex flex-col">
      <CardHeader className="items-center pb-0">
        <CardTitle>{t('dashboard.arAging.title', 'AR Aging')}</CardTitle>
        <CardDescription>Receivables by aging bucket</CardDescription>
      </CardHeader>
      <CardContent className="flex-1 pb-0">
        <ChartContainer config={chartConfig} className="mx-auto aspect-square max-h-[300px]">
          <PieChart>
            <ChartTooltip
              cursor={false}
              content={
                <ChartTooltipContent
                  formatter={(value, name) => (
                    <div className="flex items-center gap-2">
                      <span>{BUCKET_LABELS[name as string] || name}</span>
                      <span className="font-mono font-medium">
                        {formatCurrency(value as number)}
                      </span>
                    </div>
                  )}
                />
              }
            />
            <Pie
              data={chartData}
              dataKey="amount"
              nameKey="bucket"
              innerRadius={60}
              outerRadius={100}
              strokeWidth={2}
              stroke="hsl(var(--background))"
              activeIndex={activeIndex}
              activeShape={({ outerRadius = 0, ...props }: PieSectorDataItem) => (
                <Sector {...props} outerRadius={outerRadius + 8} />
              )}
              onMouseEnter={(_, index) => setActiveIndex(index)}
              onMouseLeave={() => setActiveIndex(undefined)}
            >
              <Label
                content={({ viewBox }) => {
                  if (viewBox && 'cx' in viewBox && 'cy' in viewBox) {
                    return (
                      <text
                        x={viewBox.cx}
                        y={viewBox.cy}
                        textAnchor="middle"
                        dominantBaseline="middle"
                      >
                        <tspan
                          x={viewBox.cx}
                          y={viewBox.cy}
                          className="fill-foreground text-2xl font-bold"
                        >
                          {formatCurrency(totalOutstanding)}
                        </tspan>
                        <tspan
                          x={viewBox.cx}
                          y={(viewBox.cy || 0) + 20}
                          className="fill-muted-foreground text-xs"
                        >
                          Total
                        </tspan>
                      </text>
                    )
                  }
                }}
              />
            </Pie>
            <ChartLegend
              content={<ChartLegendContent nameKey="bucket" />}
              className="-translate-y-2 flex-wrap gap-2 [&>*]:basis-1/3 [&>*]:justify-center"
            />
          </PieChart>
        </ChartContainer>
      </CardContent>
    </Card>
  )
}
