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
import type { Supplier } from '@/types/supplier'

interface DeleteSupplierDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  supplier: Supplier | null
  onConfirm: () => void
}

export default function DeleteSupplierDialog({
  open,
  onOpenChange,
  supplier,
  onConfirm,
}: DeleteSupplierDialogProps) {
  const { t } = useTranslation()
  const [error, setError] = useState<string | null>(null)

  const handleConfirm = async () => {
    setError(null)
    try {
      await onConfirm()
      onOpenChange(false)
    } catch (err: any) {
      const errorMessage = err?.error?.message || err?.message || 'Failed to delete supplier'
      setError(errorMessage)
    }
  }

  if (!supplier) return null

  return (
    <AlertDialog open={open} onOpenChange={onOpenChange}>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>{t('suppliers.deleteConfirmTitle')}</AlertDialogTitle>
          <AlertDialogDescription>
            {t('suppliers.deleteConfirmMessage')} "{supplier.code} - {supplier.name}"?
            {t('suppliers.cannotBeUndone')}
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
