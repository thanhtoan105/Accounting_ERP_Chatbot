import { useState, useEffect, useCallback, useMemo } from 'react'
import {
  Box,
  Paper,
  Typography,
  TextField,
  InputAdornment,
  MenuItem,
  FormControl,
  Select,
  FormControlLabel,
  Switch,
  Chip,
  CircularProgress,
  Alert,
  Card,
  CardContent,
} from '@mui/material'
import SearchIcon from '@mui/icons-material/Search'
import { getChartOfAccounts } from '../services/chartOfAccounts'
import type { ChartOfAccountHierarchy, ChartOfAccountFilters } from '../types/chartOfAccount'
import AccountTreeView from '../components/account/AccountTreeView'
import AccountDetailsModal from '../components/account/AccountDetailsModal'

const ACCOUNT_TYPES = [
  { value: '', label: 'All Types' },
  { value: 'Asset', label: 'Asset' },
  { value: 'Liability', label: 'Liability' },
  { value: 'Equity', label: 'Equity' },
  { value: 'Revenue', label: 'Revenue' },
  { value: 'Expense', label: 'Expense' },
]

/**
 * Chart of Accounts page - displays TT200 COA in hierarchical tree view.
 * Supports search, filtering, and account details viewing.
 */
export default function ChartOfAccounts() {
  const [accounts, setAccounts] = useState<ChartOfAccountHierarchy[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  // Filters
  const [searchTerm, setSearchTerm] = useState<string>('')
  const [typeFilter, setTypeFilter] = useState<string>('')
  const [codePrefixFilter, setCodePrefixFilter] = useState<string>('')
  const [postableFilter, setPostableFilter] = useState<boolean | null>(null)

  // Selected account for details modal
  const [selectedAccount, setSelectedAccount] = useState<ChartOfAccountHierarchy | null>(null)
  const [detailsModalOpen, setDetailsModalOpen] = useState(false)

  // Debounced search
  const [debouncedSearch, setDebouncedSearch] = useState<string>('')

  useEffect(() => {
    const timer = setTimeout(() => {
      setDebouncedSearch(searchTerm)
    }, 300)
    return () => clearTimeout(timer)
  }, [searchTerm])

  const loadAccounts = useCallback(async () => {
    try {
      setLoading(true)
      setError(null)

      const filters: ChartOfAccountFilters = {}
      if (debouncedSearch.trim()) filters.search = debouncedSearch.trim()
      if (typeFilter) filters.type = typeFilter
      if (codePrefixFilter.trim()) filters.codePrefix = codePrefixFilter.trim()
      if (postableFilter !== null) filters.postable = postableFilter

      const response = await getChartOfAccounts(filters)

      // Convert to hierarchy format if flat array
      if (Array.isArray(response.data) && response.data.length > 0) {
        if ('children' in response.data[0]) {
          // Already hierarchy
          setAccounts(response.data as ChartOfAccountHierarchy[])
        } else {
          // Convert flat to hierarchy (for filtered views)
          const flatAccounts = response.data as any[]
          const accountMap = new Map<number, ChartOfAccountHierarchy>()
          const rootAccounts: ChartOfAccountHierarchy[] = []

          // Create map
          flatAccounts.forEach((acc) => {
            accountMap.set(acc.id, { ...acc, children: [] })
          })

          // Build tree
          flatAccounts.forEach((acc) => {
            const account = accountMap.get(acc.id)!
            if (acc.parentId === null || acc.parentId === undefined) {
              rootAccounts.push(account)
            } else {
              const parent = accountMap.get(acc.parentId)
              if (parent) {
                parent.children = parent.children || []
                parent.children.push(account)
              }
            }
          })

          // Sort
          const sortAccounts = (accs: ChartOfAccountHierarchy[]) => {
            accs.sort((a, b) => a.orderingPosition - b.orderingPosition)
            accs.forEach((acc) => {
              if (acc.children) sortAccounts(acc.children)
            })
          }
          sortAccounts(rootAccounts)
          setAccounts(rootAccounts)
        }
      } else {
        setAccounts([])
      }
    } catch (err) {
      const errorMessage =
        err instanceof Error
          ? err.message
          : (err as { error?: { message?: string }; message?: string })?.error?.message ||
            (err as { message?: string })?.message ||
            'Failed to load chart of accounts'
      setError(errorMessage)
    } finally {
      setLoading(false)
    }
  }, [debouncedSearch, typeFilter, codePrefixFilter, postableFilter])

  useEffect(() => {
    loadAccounts()
  }, [loadAccounts])

  const handleAccountClick = (account: ChartOfAccountHierarchy) => {
    setSelectedAccount(account)
    setDetailsModalOpen(true)
  }

  const filteredAccounts = useMemo(() => {
    if (!searchTerm.trim() && !typeFilter && !codePrefixFilter.trim() && postableFilter === null) {
      return accounts
    }
    return accounts
  }, [accounts, searchTerm, typeFilter, codePrefixFilter, postableFilter])

  return (
    <Box sx={{ p: 3 }}>
      <Typography variant="h4" gutterBottom>
        Chart of Accounts
      </Typography>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
        TT200 Standard - Read-only view
      </Typography>

      {/* Filters */}
      <Paper sx={{ p: 2, mb: 3 }}>
        <Box sx={{ display: 'flex', gap: 2, flexWrap: 'wrap', alignItems: 'center' }}>
          <TextField
            placeholder="Search by code or name..."
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            size="small"
            InputProps={{
              startAdornment: (
                <InputAdornment position="start">
                  <SearchIcon />
                </InputAdornment>
              ),
            }}
            sx={{ minWidth: 250 }}
          />

          <FormControl size="small" sx={{ minWidth: 150 }}>
            <Select value={typeFilter} onChange={(e) => setTypeFilter(e.target.value)} displayEmpty>
              {ACCOUNT_TYPES.map((type) => (
                <MenuItem key={type.value} value={type.value}>
                  {type.label}
                </MenuItem>
              ))}
            </Select>
          </FormControl>

          <TextField
            placeholder="Code prefix (e.g., 131)"
            value={codePrefixFilter}
            onChange={(e) => setCodePrefixFilter(e.target.value)}
            size="small"
            sx={{ minWidth: 150 }}
          />

          <FormControlLabel
            control={
              <Switch
                checked={postableFilter === true}
                onChange={(e) => setPostableFilter(e.target.checked ? true : null)}
              />
            }
            label="Postable only"
          />

          {(typeFilter || codePrefixFilter || postableFilter !== null || searchTerm) && (
            <Chip
              label="Clear filters"
              onClick={() => {
                setSearchTerm('')
                setTypeFilter('')
                setCodePrefixFilter('')
                setPostableFilter(null)
              }}
              onDelete={() => {
                setSearchTerm('')
                setTypeFilter('')
                setCodePrefixFilter('')
                setPostableFilter(null)
              }}
              color="default"
              variant="outlined"
            />
          )}
        </Box>
      </Paper>

      {/* Error */}
      {error && (
        <Alert severity="error" sx={{ mb: 2 }} onClose={() => setError(null)}>
          {error}
        </Alert>
      )}

      {/* Loading */}
      {loading && (
        <Box sx={{ display: 'flex', justifyContent: 'center', p: 4 }}>
          <CircularProgress />
        </Box>
      )}

      {/* Tree View */}
      {!loading && !error && (
        <Paper sx={{ p: 2 }}>
          {filteredAccounts.length === 0 ? (
            <Typography variant="body2" color="text.secondary" align="center" sx={{ py: 4 }}>
              No accounts found. Try adjusting your filters.
            </Typography>
          ) : (
            <AccountTreeView
              accounts={filteredAccounts}
              onAccountClick={handleAccountClick}
              searchTerm={debouncedSearch}
            />
          )}
        </Paper>
      )}

      {/* Account Details Modal */}
      {selectedAccount && (
        <AccountDetailsModal
          open={detailsModalOpen}
          onClose={() => {
            setDetailsModalOpen(false)
            setSelectedAccount(null)
          }}
          account={selectedAccount}
        />
      )}
    </Box>
  )
}
