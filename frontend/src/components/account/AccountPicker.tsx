import { useState, useEffect, useMemo } from 'react'
import { Autocomplete, TextField, Chip, Box, Typography } from '@mui/material'
import CheckCircleIcon from '@mui/icons-material/CheckCircle'
import { getPostableAccounts } from '../../services/chartOfAccounts'
import type { ChartOfAccount } from '../../types/chartOfAccount'

interface AccountPickerProps {
  value?: number | null
  onChange: (account: ChartOfAccount | null) => void
  label?: string
  error?: boolean
  helperText?: string
  disabled?: boolean
}

/**
 * Account picker component for voucher forms.
 * Only shows postable leaf accounts (accounts with postable=true and no children).
 * Supports search/typeahead with unaccented Vietnamese matching.
 */
export default function AccountPicker({
  value,
  onChange,
  label = 'Select Account',
  error = false,
  helperText,
  disabled = false,
}: AccountPickerProps) {
  const [accounts, setAccounts] = useState<ChartOfAccount[]>([])
  const [loading, setLoading] = useState(true)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)

  useEffect(() => {
    const loadAccounts = async () => {
      try {
        setLoading(true)
        setErrorMessage(null)
        const postableAccounts = await getPostableAccounts()
        setAccounts(postableAccounts)
      } catch (err) {
        const errorMsg = err instanceof Error ? err.message : 'Failed to load accounts'
        setErrorMessage(errorMsg)
      } finally {
        setLoading(false)
      }
    }

    loadAccounts()
  }, [])

  const selectedAccount = useMemo(() => {
    if (value === null || value === undefined) return null
    return accounts.find((acc) => acc.id === value) || null
  }, [value, accounts])

  const getOptionLabel = (option: ChartOfAccount) => {
    return `${option.code} - ${option.name}`
  }

  const isOptionEqualToValue = (option: ChartOfAccount, value: ChartOfAccount) => {
    return option.id === value.id
  }

  return (
    <Autocomplete
      value={selectedAccount}
      onChange={(_, newValue) => {
        onChange(newValue)
      }}
      options={accounts}
      getOptionLabel={getOptionLabel}
      isOptionEqualToValue={isOptionEqualToValue}
      loading={loading}
      disabled={disabled}
      renderInput={(params) => (
        <TextField
          {...params}
          label={label}
          error={error || errorMessage !== null}
          helperText={errorMessage || helperText}
          placeholder="Search by code or name..."
        />
      )}
      renderOption={(props, option) => (
        <Box component="li" {...props} key={option.id}>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, width: '100%' }}>
            <Typography variant="body2" sx={{ fontFamily: 'monospace', minWidth: 60 }}>
              {option.code}
            </Typography>
            <Typography variant="body2" sx={{ flex: 1 }}>
              {option.name}
            </Typography>
            {option.postable && <CheckCircleIcon color="success" sx={{ fontSize: 18 }} />}
          </Box>
        </Box>
      )}
      filterOptions={(options, { inputValue }) => {
        const search = inputValue.toLowerCase()
        return options.filter((option) => {
          return (
            option.code.toLowerCase().includes(search) || option.name.toLowerCase().includes(search)
          )
        })
      }}
      noOptionsText={loading ? 'Loading...' : 'No postable accounts found'}
    />
  )
}
