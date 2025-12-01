'use client'

import { useMemo } from 'react'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'

/**
 * GL Account options for Cash and Bank accounts per Vietnamese Accounting Standard (Thông tư 200)
 */
const GL_ACCOUNT_OPTIONS = {
  CASH: [
    { code: '1111', name: 'Tiền mặt Việt Nam' },
    { code: '1112', name: 'Ngoại tệ' },
    { code: '1113', name: 'Vàng, bạc, kim khí quý, đá quý' },
  ],
  BANK: [
    { code: '1121', name: 'Tiền gửi Việt Nam' },
    { code: '1122', name: 'Ngoại tệ' },
    { code: '1123', name: 'Vàng, bạc, kim khí quý, đá quý' },
  ],
} as const

export type AccountType = 'CASH' | 'BANK'

interface GLAccountSelectProps {
  value?: string | null
  onValueChange: (value: string) => void
  accountType: AccountType
  disabled?: boolean
  placeholder?: string
}

/**
 * Select component for choosing GL Account Code based on account type.
 * Shows only relevant GL accounts per Vietnamese accounting standards (Thông tư 200).
 *
 * - CASH type: 1111 (VND), 1112 (Foreign), 1113 (Precious metals)
 * - BANK type: 1121 (VND), 1122 (Foreign), 1123 (Precious metals)
 */
export default function GLAccountSelect({
  value,
  onValueChange,
  accountType,
  disabled = false,
  placeholder = 'Chọn tài khoản...',
}: GLAccountSelectProps) {
  const options = useMemo(() => GL_ACCOUNT_OPTIONS[accountType] || [], [accountType])

  // Find the selected option for display
  const selectedOption = useMemo(() => options.find((opt) => opt.code === value), [options, value])

  return (
    <Select value={value || ''} onValueChange={onValueChange} disabled={disabled}>
      <SelectTrigger className="h-10">
        <SelectValue placeholder={placeholder}>
          {selectedOption ? `${selectedOption.code} - ${selectedOption.name}` : placeholder}
        </SelectValue>
      </SelectTrigger>
      <SelectContent>
        {options.map((option) => (
          <SelectItem key={option.code} value={option.code}>
            {option.code} - {option.name}
          </SelectItem>
        ))}
      </SelectContent>
    </Select>
  )
}

export { GL_ACCOUNT_OPTIONS }
