'use client'

import * as React from 'react'
import { forwardRef, useCallback, useEffect, useRef, useState } from 'react'

import { Input } from '@/components/ui/input'
import { cn } from '@/lib/utils'

type InputProps = React.ComponentProps<'input'>

export type MoneyInputValue = number | null

export interface MoneyInputProps
  extends Omit<InputProps, 'value' | 'defaultValue' | 'onChange' | 'type'> {
  value?: MoneyInputValue
  onChange?: (value: MoneyInputValue) => void
  allowNegative?: boolean
  decimals?: 0 | 2
  locale?: string
  /**
   * Format while typing (default: true)
   */
  liveFormat?: boolean
  /**
   * Optional validation message. When provided the input will be marked as invalid.
   */
  errorMessage?: string
}

const DEFAULT_LOCALE = 'vi-VN'

function formatNumber(value: number, decimals: 0 | 2, locale: string): string {
  const formatter = new Intl.NumberFormat(locale, {
    minimumFractionDigits: decimals,
    maximumFractionDigits: decimals,
  })
  return formatter.format(value)
}

function extractDigits(input: string): string {
  return input.replace(/\D/g, '')
}

export const MoneyInput = forwardRef<HTMLInputElement, MoneyInputProps>(
  (
    {
      value = null,
      onChange,
      allowNegative = false,
      decimals = 0,
      locale = DEFAULT_LOCALE,
      liveFormat = true,
      className,
      errorMessage,
      onBlur,
      onFocus,
      ...props
    },
    ref,
  ) => {
    const [display, setDisplay] = useState<string>('')
    const inputRef = useRef<HTMLInputElement>(null)
    const cursorRef = useRef<number>(0)

    // Combine refs
    const combinedRef = useCallback(
      (node: HTMLInputElement | null) => {
        inputRef.current = node
        if (typeof ref === 'function') {
          ref(node)
        } else if (ref) {
          ref.current = node
        }
      },
      [ref],
    )

    // Format display value when external value changes
    useEffect(() => {
      if (value === null || Number.isNaN(value)) {
        setDisplay('')
      } else {
        setDisplay(formatNumber(value, decimals, locale))
      }
    }, [value, decimals, locale])

    // Restore cursor position after format
    useEffect(() => {
      if (inputRef.current && document.activeElement === inputRef.current) {
        const pos = Math.min(cursorRef.current, display.length)
        inputRef.current.setSelectionRange(pos, pos)
      }
    }, [display])

    const handleChange = useCallback(
      (event: React.ChangeEvent<HTMLInputElement>) => {
        const inputValue = event.target.value
        const cursorPosition = event.target.selectionStart || 0

        if (inputValue === '') {
          setDisplay('')
          onChange?.(null)
          return
        }

        // Extract only digits
        const digits = extractDigits(inputValue)
        if (!digits) {
          setDisplay('')
          onChange?.(null)
          return
        }

        const numericValue = parseInt(digits, 10)
        if (Number.isNaN(numericValue)) return
        if (!allowNegative && numericValue < 0) return

        // Calculate new cursor position
        // Count how many non-digit chars were before cursor in old value
        const charsBeforeCursor = inputValue.slice(0, cursorPosition)
        const digitsBeforeCursor = extractDigits(charsBeforeCursor).length

        // Format the new value
        const formatted = formatNumber(numericValue, decimals, locale)

        // Find new cursor position by counting digits in formatted string
        let newCursor = 0
        let digitCount = 0
        for (let i = 0; i < formatted.length && digitCount < digitsBeforeCursor; i++) {
          newCursor = i + 1
          if (/\d/.test(formatted[i])) {
            digitCount++
          }
        }

        cursorRef.current = newCursor
        setDisplay(formatted)
        onChange?.(numericValue)
      },
      [allowNegative, decimals, locale, onChange],
    )

    return (
      <div className="space-y-1">
        <Input
          {...props}
          ref={combinedRef}
          inputMode="numeric"
          className={cn(errorMessage ? 'ring-1 ring-destructive/80' : undefined, className)}
          value={display}
          onChange={handleChange}
          onFocus={onFocus}
          onBlur={onBlur}
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
