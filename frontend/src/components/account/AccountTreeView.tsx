import { useState, useEffect, useRef } from 'react'
import {
  Box,
  Typography,
  Collapse,
  IconButton,
  Chip,
  List,
  ListItem,
  ListItemButton,
  ListItemText,
} from '@mui/material'
import ExpandMoreIcon from '@mui/icons-material/ExpandMore'
import ChevronRightIcon from '@mui/icons-material/ChevronRight'
import CheckCircleIcon from '@mui/icons-material/CheckCircle'
import type { ChartOfAccountHierarchy } from '../../types/chartOfAccount'

interface AccountTreeViewProps {
  accounts: ChartOfAccountHierarchy[]
  onAccountClick: (account: ChartOfAccountHierarchy) => void
  searchTerm?: string
  level?: number
}

/**
 * Recursive tree view component for Chart of Accounts.
 * Supports expand/collapse, keyboard navigation, and visual hierarchy.
 */
export default function AccountTreeView({
  accounts,
  onAccountClick,
  searchTerm = '',
  level = 0,
}: AccountTreeViewProps) {
  const [expanded, setExpanded] = useState<Set<number>>(new Set())
  const itemRefs = useRef<Map<number, HTMLDivElement>>(new Map())

  // Auto-expand nodes if search term matches
  useEffect(() => {
    if (searchTerm.trim()) {
      const expandMatchingNodes = (accs: ChartOfAccountHierarchy[]): Set<number> => {
        const toExpand = new Set<number>()
        const term = searchTerm.toLowerCase()

        accs.forEach((acc) => {
          const matchesSearch =
            acc.code.toLowerCase().includes(term) || acc.name.toLowerCase().includes(term)

          if (matchesSearch) {
            // Expand all ancestors
            let current: ChartOfAccountHierarchy | null = acc
            while (current && current.parentId) {
              toExpand.add(current.parentId)
              // Find parent in tree
              const findParent = (
                nodes: ChartOfAccountHierarchy[],
              ): ChartOfAccountHierarchy | null => {
                for (const node of nodes) {
                  if (node.id === current!.parentId) return node
                  if (node.children) {
                    const found = findParent(node.children)
                    if (found) return found
                  }
                }
                return null
              }
              current = findParent(accounts)
            }
          }

          if (acc.children && acc.children.length > 0) {
            const childExpanded = expandMatchingNodes(acc.children)
            childExpanded.forEach((id) => toExpand.add(id))
            if (childExpanded.size > 0) {
              toExpand.add(acc.id)
            }
          }
        })

        return toExpand
      }

      const nodesToExpand = expandMatchingNodes(accounts)
      setExpanded(nodesToExpand)
    } else {
      // Expand first level by default when no search
      if (level === 0) {
        setExpanded(new Set(accounts.map((acc) => acc.id)))
      }
    }
  }, [searchTerm, accounts, level])

  const handleToggle = (accountId: number, event: React.MouseEvent) => {
    event.stopPropagation()
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

  const handleKeyDown = (
    event: React.KeyboardEvent,
    account: ChartOfAccountHierarchy,
    index: number,
  ) => {
    if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault()
      if (account.children && account.children.length > 0) {
        handleToggle(account.id, event as any)
      } else {
        onAccountClick(account)
      }
    } else if (event.key === 'ArrowDown') {
      event.preventDefault()
      const nextIndex = index + 1
      if (nextIndex < accounts.length) {
        const nextItem = accounts[nextIndex]
        itemRefs.current.get(nextItem.id)?.focus()
      }
    } else if (event.key === 'ArrowUp') {
      event.preventDefault()
      const prevIndex = index - 1
      if (prevIndex >= 0) {
        const prevItem = accounts[prevIndex]
        itemRefs.current.get(prevItem.id)?.focus()
      }
    } else if (event.key === 'ArrowRight' && account.children && account.children.length > 0) {
      event.preventDefault()
      if (!expanded.has(account.id)) {
        setExpanded((prev) => new Set([...prev, account.id]))
      }
    } else if (event.key === 'ArrowLeft' && expanded.has(account.id)) {
      event.preventDefault()
      setExpanded((prev) => {
        const newSet = new Set(prev)
        newSet.delete(account.id)
        return newSet
      })
    }
  }

  const highlightText = (text: string, search: string) => {
    if (!search.trim()) return text
    const parts = text.split(new RegExp(`(${search})`, 'gi'))
    return (
      <>
        {parts.map((part, i) =>
          part.toLowerCase() === search.toLowerCase() ? (
            <Typography
              component="span"
              key={i}
              sx={{ backgroundColor: 'yellow', fontWeight: 'bold' }}
            >
              {part}
            </Typography>
          ) : (
            part
          ),
        )}
      </>
    )
  }

  const hasChildren = (account: ChartOfAccountHierarchy) =>
    account.children && account.children.length > 0

  return (
    <List component="div" disablePadding>
      {accounts.map((account, index) => (
        <Box key={account.id}>
          <ListItem
            disablePadding
            sx={{
              pl: level * 2,
              '&:hover': { backgroundColor: 'action.hover' },
            }}
          >
            <ListItemButton
              ref={(el) => {
                if (el) itemRefs.current.set(account.id, el)
              }}
              onClick={() => onAccountClick(account)}
              onKeyDown={(e) => handleKeyDown(e, account, index)}
              tabIndex={0}
              sx={{ py: 0.5 }}
            >
              {hasChildren(account) && (
                <IconButton
                  size="small"
                  onClick={(e) => handleToggle(account.id, e)}
                  sx={{ mr: 1 }}
                  aria-label={expanded.has(account.id) ? 'Collapse' : 'Expand'}
                >
                  {expanded.has(account.id) ? <ExpandMoreIcon /> : <ChevronRightIcon />}
                </IconButton>
              )}
              {!hasChildren(account) && <Box sx={{ width: 32 }} />}

              <ListItemText
                primary={
                  <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                    <Typography variant="body2" sx={{ fontFamily: 'monospace', minWidth: 60 }}>
                      {highlightText(account.code, searchTerm)}
                    </Typography>
                    <Typography variant="body2" sx={{ flex: 1 }}>
                      {highlightText(account.name, searchTerm)}
                    </Typography>
                    <Chip
                      label={account.type}
                      size="small"
                      color={account.type === 'Asset' ? 'primary' : 'default'}
                      sx={{ height: 20, fontSize: '0.7rem' }}
                    />
                    {account.postable && (
                      <CheckCircleIcon
                        color="success"
                        sx={{ fontSize: 18 }}
                        titleAccess="Postable account"
                      />
                    )}
                  </Box>
                }
                secondary={
                  <Typography variant="caption" color="text.secondary">
                    {account.normalSide === 'Debit' ? 'Dư Nợ' : 'Dư Có'} •{' '}
                    {account.children?.length || 0} tài khoản con
                  </Typography>
                }
              />
            </ListItemButton>
          </ListItem>

          {hasChildren(account) && (
            <Collapse in={expanded.has(account.id)} timeout="auto" unmountOnExit>
              <Box sx={{ pl: 2 }}>
                <AccountTreeView
                  accounts={account.children!}
                  onAccountClick={onAccountClick}
                  searchTerm={searchTerm}
                  level={level + 1}
                />
              </Box>
            </Collapse>
          )}
        </Box>
      ))}
    </List>
  )
}
