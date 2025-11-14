'use client'

import * as React from 'react'
import { forwardRef, useCallback, useEffect, useMemo, useState } from 'react'

import { Input, type InputProps } from '@/components/ui/input'
import { cn } from '@/lib/utils'

export type MoneyInputValue = number | null

export interface MoneyInputProps
  extends Omit<InputProps, 'value' | 'defaultValue' | 'onChange' | 'type'> {
  value?: MoneyInputValue
  onChange?: (value: MoneyInputValue) => void
  allowNegative?: boolean
  decimals?: 0 | 2
  locale?: string
  /**
   * Optional validation message. When provided the input will be marked as invalid.
   */
  errorMessage?: string
}

const DEFAULT_LOCALE = 'vi-VN'

function formatNumber(value: number, decimals: 0 | 2, locale: string) {
  const formatter = new Intl.NumberFormat(locale, {
    minimumFractionDigits: decimals,
    maximumFractionDigits: decimals,
  })
  return formatter.format(value)
}

function parseInput(
  input: string,
  { allowNegative, decimals }: { allowNegative: boolean; decimals: 0 | 2 },
): MoneyInputValue {
  if (!input) return null

  const sanitized = input
    .replace(/[^\d,.-]/g, '')
    .replace(/\.(?=.*\.)/g, '') // remove extra dots
    .replace(/\.(?=.*[,])/g, '') // drop thousands separator when comma used as decimal

  const normalized = sanitized.replace(',', '.')
  if (!normalized) return null

  const value = Number(normalized)
  if (Number.isNaN(value)) return null
  if (!allowNegative && value < 0) return null

  if (decimals === 0) {
    return Math.round(value)
  }

  return Math.round(value * 100) / 100
}

export const MoneyInput = forwardRef<HTMLInputElement, MoneyInputProps>(
  (
    {
      value = null,
      onChange,
      allowNegative = false,
      decimals = 0,
      locale = DEFAULT_LOCALE,
      className,
      errorMessage,
      onBlur,
      onFocus,
      ...props
    },
    ref,
  ) => {
    const [display, setDisplay] = useState<string>('')
    const [isFocused, setIsFocused] = useState(false)

    const formattedValue = useMemo(() => {
      if (value === null || Number.isNaN(value)) return ''
      return formatNumber(value, decimals, locale)
    }, [value, decimals, locale])

    useEffect(() => {
      if (!isFocused) {
        setDisplay(formattedValue)
      }
    }, [formattedValue, isFocused])

    const handleInternalChange = useCallback(
      (nextDisplay: string) => {
        setDisplay(nextDisplay)
        const parsed = parseInput(nextDisplay, { allowNegative, decimals })
        onChange?.(parsed)
      },
      [allowNegative, decimals, onChange],
    )

    return (
      <div className="space-y-1">
        <Input
          {...props}
          ref={ref}
          inputMode="decimal"
          className={cn(errorMessage ? 'ring-1 ring-destructive/80' : undefined, className)}
          value={display}
          onFocus={(e) => {
            setIsFocused(true)
            setDisplay(value === null ? '' : String(value))
            onFocus?.(e)
          }}
          onBlur={(e) => {
            setIsFocused(false)
            setDisplay(formattedValue)
            onBlur?.(e)
          }}
          onChange={(event) => {
            const next = event.target.value
            if (next === '') {
              setDisplay('')
              onChange?.(null)
              return
            }
            handleInternalChange(next)
          }}
        />
        {errorMessage ? (
          <p className="text-xs text-destructive" role="alert">
            {errorMessage}
          </p>
        ) : null}
      </div>
    )
  },
)

MoneyInput.displayName = 'MoneyInput'

export default MoneyInput
