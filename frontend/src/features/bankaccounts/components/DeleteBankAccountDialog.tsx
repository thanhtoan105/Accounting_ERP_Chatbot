'use client'

import { useState } from 'react'
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
import { Button } from '@/components/ui/button'
import { deactivateBankAccount } from '@/features/bankaccounts/services/bankAccount'
import type { BankAccount } from '@/types/bankAccount'

interface DeleteBankAccountDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  bankAccount: BankAccount | null
  onConfirm: () => void
}

export default function DeleteBankAccountDialog({
  open,
  onOpenChange,
  bankAccount,
  onConfirm,
}: DeleteBankAccountDialogProps) {
  const [error, setError] = useState<string | null>(null)
  const [isDeactivating, setIsDeactivating] = useState(false)

  const handleConfirm = async () => {
    setError(null)
    try {
      await onConfirm()
      onOpenChange(false)
    } catch (err: any) {
      const errorMessage = err?.error?.message || err?.message || 'Failed to delete bank account'
      setError(errorMessage)
      // Don't close dialog if there's a 409 error - show deactivation option
    }
  }

  const handleDeactivate = async () => {
    if (!bankAccount) return
    setIsDeactivating(true)
    setError(null)
    try {
      await deactivateBankAccount(bankAccount.id)
      onOpenChange(false)
      // Refresh will be handled by parent component
    } catch (err: any) {
      const errorMessage =
        err?.error?.message || err?.message || 'Failed to deactivate bank account'
      setError(errorMessage)
    } finally {
      setIsDeactivating(false)
    }
  }

  if (!bankAccount) return null

  const isConflictError = error?.includes('referenced') || error?.includes('Cannot delete')

  return (
    <AlertDialog open={open} onOpenChange={onOpenChange}>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>Delete Bank Account</AlertDialogTitle>
          <AlertDialogDescription>
            Are you sure you want to delete bank account &quot;{bankAccount.accountNumber} - {bankAccount.bankName}
            &quot;? This action cannot be undone.
          </AlertDialogDescription>
        </AlertDialogHeader>
        {error && (
          <Alert variant="destructive">
            <AlertDescription>
              {error}
              {isConflictError && (
                <div className="mt-2">
                  <p className="text-sm font-medium mb-1">Suggestion:</p>
                  <p className="text-sm">
                    This account has linked transactions. Consider deactivating it instead to preserve historical data.
                  </p>
                </div>
              )}
            </AlertDescription>
          </Alert>
        )}
        <AlertDialogFooter>
          <AlertDialogCancel onClick={() => setError(null)}>Cancel</AlertDialogCancel>
          {isConflictError && (
            <Button
              variant="outline"
              onClick={handleDeactivate}
              disabled={isDeactivating}
            >
              {isDeactivating ? 'Deactivating...' : 'Deactivate Instead'}
            </Button>
          )}
          <AlertDialogAction
            onClick={handleConfirm}
            className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
            disabled={isConflictError}
          >
            Delete
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  )
}

