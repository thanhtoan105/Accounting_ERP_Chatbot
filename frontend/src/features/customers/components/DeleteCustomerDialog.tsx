'use client'

import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from '@/components/ui/alert-dialog'
import { Alert, AlertDescription } from '@/components/ui/alert'
import type { Customer } from '@/types/customer'

interface DeleteCustomerDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  customer: Customer | null
  onConfirm: () => void
}

export default function DeleteCustomerDialog({
  open,
  onOpenChange,
  customer,
  onConfirm,
}: DeleteCustomerDialogProps) {
  const { t } = useTranslation()
  const [error, setError] = useState<string | null>(null)

  const handleConfirm = async () => {
    setError(null)
    try {
      await onConfirm()
      onOpenChange(false)
    } catch (err: any) {
      const errorMessage = err?.error?.message || err?.message || 'Failed to delete customer'
      setError(errorMessage)
    }
  }

  if (!customer) return null

  return (
    <AlertDialog open={open} onOpenChange={onOpenChange}>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>{t('customers.deleteConfirmTitle')}</AlertDialogTitle>
          <AlertDialogDescription>
            {t('customers.deleteConfirmMessage')} "{customer.code} - {customer.name}"?
            {t('customers.cannotBeUndone')}
          </AlertDialogDescription>
        </AlertDialogHeader>
        {error && (
          <Alert variant="destructive">
            <AlertDescription>{error}</AlertDescription>
          </Alert>
        )}
        <AlertDialogFooter>
          <AlertDialogCancel onClick={() => setError(null)}>{t('common.cancel')}</AlertDialogCancel>
          <AlertDialogAction
            onClick={handleConfirm}
            className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
          >
            {t('common.delete')}
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  )
}
