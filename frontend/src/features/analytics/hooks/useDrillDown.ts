import { useCallback, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import type { DrillDownAction, MetabaseClickEvent } from '../types'

const WIDGET_NAME_PATTERNS = {
  REVENUE_EXPENSE: /revenue.*expense|p&l|income.*expense/i,
  AR_BALANCES: /ar.*balance|receivable.*balance/i,
  AP_BALANCES: /ap.*balance|payable.*balance/i,
  TOP_DEBTORS: /top.*debtor|largest.*receivable/i,
  TOP_CREDITORS: /top.*creditor|largest.*payable/i,
  CASH_POSITION: /cash.*position|cash.*balance/i,
} as const

function parseClickEventToAction(event: MetabaseClickEvent): DrillDownAction | null {
  const { data } = event
  const dimensions = data.dimensions ?? {}
  const cardName = data.cardName ?? ''

  if (WIDGET_NAME_PATTERNS.REVENUE_EXPENSE.test(cardName)) {
    const date = dimensions['date'] ?? dimensions['period_date']
    const periodId = dimensions['period_id'] ?? dimensions['periodId']
    if (date) {
      return {
        type: 'VOUCHER_BY_DATE',
        date: String(date),
        periodId: periodId ? String(periodId) : undefined,
      }
    }
  }

  if (WIDGET_NAME_PATTERNS.AR_BALANCES.test(cardName)) {
    const customerId = dimensions['customer_id'] ?? dimensions['customerId']
    const bucket = dimensions['aging_bucket'] ?? dimensions['bucket']
    if (customerId) {
      return { type: 'CUSTOMER_DETAIL', customerId: String(customerId) }
    }
    if (bucket) {
      return { type: 'AR_AGING_BUCKET', bucket: String(bucket) }
    }
  }

  if (WIDGET_NAME_PATTERNS.AP_BALANCES.test(cardName)) {
    const supplierId = dimensions['supplier_id'] ?? dimensions['supplierId']
    const bucket = dimensions['aging_bucket'] ?? dimensions['bucket']
    if (supplierId) {
      return { type: 'SUPPLIER_DETAIL', supplierId: String(supplierId) }
    }
    if (bucket) {
      return { type: 'AP_AGING_BUCKET', bucket: String(bucket) }
    }
  }

  if (WIDGET_NAME_PATTERNS.TOP_DEBTORS.test(cardName)) {
    const customerId = dimensions['customer_id'] ?? dimensions['customerId']
    if (customerId) {
      return { type: 'CUSTOMER_TRANSACTIONS', customerId: String(customerId) }
    }
  }

  if (WIDGET_NAME_PATTERNS.TOP_CREDITORS.test(cardName)) {
    const supplierId = dimensions['supplier_id'] ?? dimensions['supplierId']
    if (supplierId) {
      return { type: 'SUPPLIER_TRANSACTIONS', supplierId: String(supplierId) }
    }
  }

  if (WIDGET_NAME_PATTERNS.CASH_POSITION.test(cardName)) {
    const accountCode = dimensions['account_code'] ?? dimensions['accountCode']
    const date = dimensions['date']
    if (accountCode) {
      return {
        type: 'VOUCHERS_BY_ACCOUNT',
        accountCode: String(accountCode),
        date: date ? String(date) : undefined,
      }
    }
  }

  return null
}

function isMetabaseClickEvent(data: unknown): data is MetabaseClickEvent {
  if (typeof data !== 'object' || data === null) return false
  const obj = data as Record<string, unknown>
  return (
    (obj.type === 'metabase:drill-through' || obj.type === 'metabase:click') &&
    typeof obj.data === 'object' &&
    obj.data !== null
  )
}

export function useDrillDown(metabaseOrigin?: string) {
  const navigate = useNavigate()

  const handleDrillDown = useCallback(
    (action: DrillDownAction) => {
      switch (action.type) {
        case 'VOUCHER_BY_DATE':
          navigate(
            `/vouchers?date=${encodeURIComponent(action.date)}${action.periodId ? `&periodId=${encodeURIComponent(action.periodId)}` : ''}`,
          )
          break
        case 'CUSTOMER_DETAIL':
          navigate(`/customers/${encodeURIComponent(action.customerId)}`)
          break
        case 'SUPPLIER_DETAIL':
          navigate(`/suppliers/${encodeURIComponent(action.supplierId)}`)
          break
        case 'CUSTOMER_TRANSACTIONS':
          navigate(`/customers/${encodeURIComponent(action.customerId)}/transactions`)
          break
        case 'SUPPLIER_TRANSACTIONS':
          navigate(`/suppliers/${encodeURIComponent(action.supplierId)}/transactions`)
          break
        case 'VOUCHERS_BY_ACCOUNT':
          navigate(
            `/vouchers?accountCode=${encodeURIComponent(action.accountCode)}${action.date ? `&date=${encodeURIComponent(action.date)}` : ''}`,
          )
          break
        case 'AR_AGING_BUCKET':
          navigate(
            `/customers${action.customerId ? `/${encodeURIComponent(action.customerId)}` : ''}?agingBucket=${encodeURIComponent(action.bucket)}`,
          )
          break
        case 'AP_AGING_BUCKET':
          navigate(
            `/suppliers${action.supplierId ? `/${encodeURIComponent(action.supplierId)}` : ''}?agingBucket=${encodeURIComponent(action.bucket)}`,
          )
          break
      }
    },
    [navigate],
  )

  useEffect(() => {
    if (!metabaseOrigin) return

    const handleMessage = (event: MessageEvent) => {
      if (event.origin !== metabaseOrigin) return

      if (isMetabaseClickEvent(event.data)) {
        const action = parseClickEventToAction(event.data)
        if (action) {
          handleDrillDown(action)
        }
      }
    }

    window.addEventListener('message', handleMessage)
    return () => window.removeEventListener('message', handleMessage)
  }, [metabaseOrigin, handleDrillDown])

  return { handleDrillDown, parseClickEventToAction }
}
