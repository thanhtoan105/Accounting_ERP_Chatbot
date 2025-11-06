import { useState, useMemo, useEffect } from 'react'
import {
  ChevronRight,
  ChevronDown,
  ArrowUpDown,
  ArrowUp,
  ArrowDown,
  ChevronLeft,
  ChevronsLeft,
  ChevronsRight,
} from 'lucide-react'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Label } from '@/components/ui/label'
import { cn } from '@/lib/utils'
import type { ChartOfAccountHierarchy } from '@/types/chartOfAccount'

interface AccountTableProps {
  accounts: ChartOfAccountHierarchy[]
  onAccountClick: (account: ChartOfAccountHierarchy) => void
  searchTerm?: string
  totalCount?: number
}

type SortField = 'code' | 'name' | 'type' | 'normalSide' | 'orderingPosition'
type SortDirection = 'asc' | 'desc' | null

/**
 * Determine account level based on code length (TT200 format)
 * Level 1: 1 digit (e.g., "1")
 * Level 2: 2 digits (e.g., "11")
 * Level 3: 3 digits (e.g., "111")
 * Level 4: 4 digits (e.g., "1111")
 */
function getAccountLevel(code: string): number {
  return code.length
}

/**
 * Flatten hierarchical accounts into a list with level information
 * Only shows level 3 and below (hides level 1 and 2)
 * Adjusts level to start from 0 for displayed accounts
 */
function flattenAccounts(
  accounts: ChartOfAccountHierarchy[],
  expanded: Set<number>,
  displayLevel: number = 0,
): Array<ChartOfAccountHierarchy & { level: number }> {
  const result: Array<ChartOfAccountHierarchy & { level: number }> = []

  accounts.forEach((account) => {
    const accountLevel = getAccountLevel(account.code)

    // Only show level 3 and below (skip level 1 and 2)
    if (accountLevel >= 3) {
      // Adjust level to start from 0 (level 3 becomes level 0 in display)
      const adjustedLevel = accountLevel - 3
      result.push({ ...account, level: adjustedLevel })

      // Process children if expanded
      if (account.children && account.children.length > 0 && expanded.has(account.id)) {
        result.push(...flattenAccounts(account.children, expanded, adjustedLevel + 1))
      }
    } else {
      // If account is level 1 or 2, skip it but process children
      // Always expand level 1 and 2 to show their children (level 3+)
      if (account.children && account.children.length > 0) {
        result.push(...flattenAccounts(account.children, expanded, displayLevel))
      }
    }
  })

  return result
}

/**
 * Sort accounts based on field and direction
 */
function sortAccounts(
  accounts: ChartOfAccountHierarchy[],
  sortField: SortField,
  sortDirection: 'asc' | 'desc',
): ChartOfAccountHierarchy[] {
  const sorted = [...accounts].sort((a, b) => {
    let aValue: any
    let bValue: any

    switch (sortField) {
      case 'code':
        aValue = a.code
        bValue = b.code
        break
      case 'name':
        aValue = a.name.toLowerCase()
        bValue = b.name.toLowerCase()
        break
      case 'type':
        aValue = a.type
        bValue = b.type
        break
      case 'normalSide':
        aValue = a.normalSide
        bValue = b.normalSide
        break
      case 'orderingPosition':
        aValue = a.orderingPosition
        bValue = b.orderingPosition
        break
      default:
        return 0
    }

    if (aValue < bValue) return sortDirection === 'asc' ? -1 : 1
    if (aValue > bValue) return sortDirection === 'asc' ? 1 : -1
    return 0
  })

  // Sort children recursively
  return sorted.map((account) => ({
    ...account,
    children: account.children
      ? sortAccounts(account.children, sortField, sortDirection)
      : undefined,
  }))
}

const getAccountTypeLabel = (type: string) => {
  switch (type) {
    case 'Asset':
      return 'Tài sản'
    case 'Liability':
      return 'Nợ phải trả'
    case 'Equity':
      return 'Vốn chủ sở hữu'
    case 'Revenue':
      return 'Doanh thu'
    case 'Expense':
      return 'Chi phí'
    default:
      return type
  }
}

const getAccountTypeBadgeVariant = (
  type: string,
): 'default' | 'secondary' | 'destructive' | 'outline' | 'success' | 'warning' => {
  switch (type) {
    case 'Asset':
      return 'default' // Blue/Primary
    case 'Liability':
      return 'destructive' // Red
    case 'Equity':
      return 'success' // Green
    case 'Revenue':
      return 'outline' // Gray/Outline
    case 'Expense':
      return 'warning' // Yellow
    default:
      return 'secondary'
  }
}

const highlightText = (text: string, search: string) => {
  if (!search.trim()) return text

  const parts = text.split(new RegExp(`(${search})`, 'gi'))
  return (
    <>
      {parts.map((part, i) =>
        part.toLowerCase() === search.toLowerCase() ? (
          <span key={i} className="bg-yellow-200 font-bold">
            {part}
          </span>
        ) : (
          part
        ),
      )}
    </>
  )
}

export default function AccountTable({
  accounts,
  onAccountClick,
  searchTerm = '',
  totalCount,
}: AccountTableProps) {
  const [expanded, setExpanded] = useState<Set<number>>(new Set())
  const [sortField, setSortField] = useState<SortField>('orderingPosition')
  const [sortDirection, setSortDirection] = useState<SortDirection>('asc')
  const [pageIndex, setPageIndex] = useState(0)
  const [pageSize, setPageSize] = useState(20)

  // Auto-expand level 3 accounts by default
  useEffect(() => {
    if (expanded.size === 0 && accounts.length > 0) {
      // Find all level 3 accounts and their ancestors
      const level3Accounts = new Set<number>()

      const findLevel3Accounts = (accs: ChartOfAccountHierarchy[]) => {
        accs.forEach((acc) => {
          const level = getAccountLevel(acc.code)
          if (level === 3) {
            level3Accounts.add(acc.id)
            // Also expand parent accounts to show level 3
            if (acc.parentId) {
              level3Accounts.add(acc.parentId)
            }
          }
          if (acc.children) {
            findLevel3Accounts(acc.children)
          }
        })
      }

      findLevel3Accounts(accounts)
      setExpanded(level3Accounts)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [accounts.length])

  const handleToggle = (accountId: number) => {
    setExpanded((prev) => {
      const newSet = new Set(prev)
      if (newSet.has(accountId)) {
        newSet.delete(accountId)
      } else {
        newSet.add(accountId)
      }
      return newSet
    })
  }

  const handleSort = (field: SortField) => {
    if (sortField === field) {
      // Cycle: asc -> desc -> null -> asc
      if (sortDirection === 'asc') {
        setSortDirection('desc')
      } else if (sortDirection === 'desc') {
        setSortDirection(null)
        setSortField('orderingPosition')
        setSortDirection('asc')
      } else {
        setSortDirection('asc')
      }
    } else {
      setSortField(field)
      setSortDirection('asc')
    }
  }

  const sortedAccounts = useMemo(() => {
    if (!sortDirection) return accounts
    return sortAccounts(accounts, sortField, sortDirection)
  }, [accounts, sortField, sortDirection])

  // Flatten accounts, showing only level 3 and below
  const allFlattenedAccounts = useMemo(() => {
    return flattenAccounts(sortedAccounts, expanded)
  }, [sortedAccounts, expanded])

  // Paginate accounts
  const flattenedAccounts = useMemo(() => {
    const startIndex = pageIndex * pageSize
    const endIndex = startIndex + pageSize
    return allFlattenedAccounts.slice(startIndex, endIndex)
  }, [allFlattenedAccounts, pageIndex, pageSize])

  const totalPages = Math.ceil(allFlattenedAccounts.length / pageSize)

  // Reset to first page when filters change
  useEffect(() => {
    setPageIndex(0)
  }, [sortedAccounts])

  const getSortIcon = (field: SortField) => {
    if (sortField !== field || !sortDirection) {
      return <ArrowUpDown className="size-4" />
    }
    return sortDirection === 'asc' ? (
      <ArrowUp className="size-4" />
    ) : (
      <ArrowDown className="size-4" />
    )
  }

  return (
    <div className="rounded-md border">
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead className="w-[50px]"></TableHead>
            <TableHead>
              <Button
                variant="ghost"
                size="sm"
                className="-ml-3 h-8 data-[state=open]:bg-accent"
                onClick={() => handleSort('code')}
              >
                Account number
                {getSortIcon('code')}
              </Button>
            </TableHead>
            <TableHead>
              <Button
                variant="ghost"
                size="sm"
                className="-ml-3 h-8 data-[state=open]:bg-accent"
                onClick={() => handleSort('name')}
              >
                Account name
                {getSortIcon('name')}
              </Button>
            </TableHead>
            <TableHead>
              <Button
                variant="ghost"
                size="sm"
                className="-ml-3 h-8 data-[state=open]:bg-accent"
                onClick={() => handleSort('type')}
              >
                Type
                {getSortIcon('type')}
              </Button>
            </TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {flattenedAccounts.length === 0 ? (
            <TableRow>
              <TableCell colSpan={4} className="text-center py-8 text-muted-foreground">
                No data
              </TableCell>
            </TableRow>
          ) : (
            flattenedAccounts.map((account) => {
              const hasChildren = account.children && account.children.length > 0
              const isExpanded = expanded.has(account.id)

              return (
                <TableRow
                  key={account.id}
                  className={cn(
                    'cursor-pointer hover:bg-muted/50',
                    account.level > 0 && 'bg-muted/20',
                  )}
                  onClick={() => onAccountClick(account)}
                >
                  <TableCell
                    className="w-[50px]"
                    onClick={(e) => {
                      e.stopPropagation()
                      if (hasChildren) {
                        handleToggle(account.id)
                      }
                    }}
                  >
                    {hasChildren ? (
                      <Button
                        variant="ghost"
                        size="icon"
                        className="size-6"
                        onClick={(e) => {
                          e.stopPropagation()
                          handleToggle(account.id)
                        }}
                      >
                        {isExpanded ? (
                          <ChevronDown className="size-4" />
                        ) : (
                          <ChevronRight className="size-4" />
                        )}
                      </Button>
                    ) : (
                      <div className="size-6" />
                    )}
                  </TableCell>
                  <TableCell
                    className="font-mono"
                    style={{ paddingLeft: `${account.level * 24 + 8}px` }}
                  >
                    {highlightText(account.code, searchTerm)}
                  </TableCell>
                  <TableCell>{highlightText(account.name, searchTerm)}</TableCell>
                  <TableCell>
                    <Badge variant={getAccountTypeBadgeVariant(account.type)} className="text-xs">
                      {getAccountTypeLabel(account.type)}
                    </Badge>
                  </TableCell>
                </TableRow>
              )
            })
          )}
        </TableBody>
      </Table>

      {/* Pagination */}
      <div className="flex items-center justify-between px-4 py-4 border-t">
        <div className="text-sm text-muted-foreground">
          Total:{' '}
          <strong className="text-foreground">{totalCount ?? allFlattenedAccounts.length}</strong>{' '}
          records
        </div>
        <div className="flex items-center gap-6">
          <div className="hidden items-center gap-2 lg:flex">
            <Label htmlFor="rows-per-page" className="text-sm font-medium">
              Number of records per page
            </Label>
            <Select
              value={`${pageSize}`}
              onValueChange={(value) => {
                setPageSize(Number(value))
                setPageIndex(0)
              }}
            >
              <SelectTrigger size="sm" className="w-20" id="rows-per-page">
                <SelectValue placeholder={pageSize} />
              </SelectTrigger>
              <SelectContent side="top">
                {[10, 20, 30, 50, 100].map((size) => (
                  <SelectItem key={size} value={`${size}`}>
                    {size}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
          <div className="flex items-center justify-center text-sm font-medium">
            Page {pageIndex + 1} / {totalPages || 1}
          </div>
          <div className="flex items-center gap-2">
            <Button
              variant="outline"
              className="hidden h-8 w-8 p-0 lg:flex"
              onClick={() => setPageIndex(0)}
              disabled={pageIndex === 0}
            >
              <span className="sr-only">First page</span>
              <ChevronsLeft className="size-4" />
            </Button>
            <Button
              variant="outline"
              className="h-8 w-8"
              size="icon"
              onClick={() => setPageIndex((prev) => Math.max(0, prev - 1))}
              disabled={pageIndex === 0}
            >
              <span className="sr-only">Previous page</span>
              <ChevronLeft className="size-4" />
            </Button>
            <Button
              variant="outline"
              className="h-8 w-8"
              size="icon"
              onClick={() => setPageIndex((prev) => Math.min(totalPages - 1, prev + 1))}
              disabled={pageIndex >= totalPages - 1}
            >
              <span className="sr-only">Next page</span>
              <ChevronRight className="size-4" />
            </Button>
            <Button
              variant="outline"
              className="hidden h-8 w-8 lg:flex"
              size="icon"
              onClick={() => setPageIndex(totalPages - 1)}
              disabled={pageIndex >= totalPages - 1}
            >
              <span className="sr-only">Last page</span>
              <ChevronsRight className="size-4" />
            </Button>
          </div>
        </div>
      </div>
    </div>
  )
}
