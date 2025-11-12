'use client'

import { useEffect, useState, useMemo } from 'react'
import { useForm, Controller } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { toast } from 'sonner'
import { Loader2, Wallet, Building2 } from 'lucide-react'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import {
  Field,
  FieldContent,
  FieldError,
  FieldGroup,
  FieldLabel,
} from '@/components/ui/field'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Switch } from '@/components/ui/switch'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import {
  createBankAccount,
  updateBankAccount,
  getBankAccountById,
} from '@/features/bankaccounts/services/bankAccount'
import type { BankAccount, BankAccountCreateRequest, BankAccountUpdateRequest, AccountType } from '@/types/bankAccount'

interface BankAccountFormSheetProps {
  open: boolean
  onClose: () => void
  onSuccess: () => void
  bankAccount?: BankAccount | null
}

const bankAccountFormSchema = z.object({
  accountNumber: z.string().min(1, 'Account number is required').max(50, 'Account number must be at most 50 characters'),
  bankName: z.string().min(1, 'Bank name is required').max(255, 'Bank name must be at most 255 characters'),
  branch: z.string().max(255, 'Branch must be at most 255 characters').optional().or(z.literal('')),
  type: z.enum(['CASH', 'BANK'], { required_error: 'Account type is required' }),
  openingBalance: z.number().min(0, 'Opening balance must be non-negative'),
  active: z.boolean().default(true),
})

type BankAccountFormValues = z.infer<typeof bankAccountFormSchema>

export default function BankAccountFormSheet({
  open,
  onClose,
  onSuccess,
  bankAccount,
}: BankAccountFormSheetProps) {
  const isEditMode = !!bankAccount
  const [formError, setFormError] = useState<string | null>(null)
  const [duplicateError, setDuplicateError] = useState<string | null>(null)

  const form = useForm<BankAccountFormValues>({
    resolver: zodResolver(bankAccountFormSchema),
    defaultValues: {
      accountNumber: '',
      bankName: '',
      branch: '',
      type: 'BANK',
      openingBalance: 0,
      active: true,
    },
    mode: 'onBlur',
    reValidateMode: 'onBlur',
  })

  const {
    handleSubmit,
    register,
    control,
    reset,
    formState: { errors, isSubmitting },
  } = form

  // Load bank account data for edit mode
  useEffect(() => {
    if (open && isEditMode && bankAccount) {
      loadBankAccount()
    } else if (open && !isEditMode) {
      reset({
        accountNumber: '',
        bankName: '',
        branch: '',
        type: 'BANK',
        openingBalance: 0,
        active: true,
      })
      setFormError(null)
      setDuplicateError(null)
    }
  }, [open, isEditMode, bankAccount])

  const loadBankAccount = async () => {
    if (!bankAccount) return
    try {
      const bankAccountData = await getBankAccountById(bankAccount.id)
      reset({
        accountNumber: bankAccountData.accountNumber,
        bankName: bankAccountData.bankName,
        branch: bankAccountData.branch || '',
        type: bankAccountData.type,
        openingBalance: bankAccountData.openingBalance,
        active: bankAccountData.active,
      })
      setFormError(null)
      setDuplicateError(null)
    } catch (err: any) {
      const errorMessage = err?.error?.message || err?.message || 'Failed to load bank account'
      setFormError(errorMessage)
      toast.error('Failed to load bank account', { description: errorMessage })
    }
  }

  const onSubmit = async (values: BankAccountFormValues) => {
    setFormError(null)
    setDuplicateError(null)
    try {
      if (isEditMode && bankAccount) {
        const request: BankAccountUpdateRequest = {
          bankName: values.bankName.trim(),
          branch: values.branch?.trim() || undefined,
          type: values.type,
          openingBalance: values.openingBalance,
        }
        await updateBankAccount(bankAccount.id, request)
        toast.success('Bank account updated successfully')
      } else {
        const request: BankAccountCreateRequest = {
          accountNumber: values.accountNumber.trim(),
          bankName: values.bankName.trim(),
          branch: values.branch?.trim() || undefined,
          type: values.type,
          openingBalance: values.openingBalance,
          active: values.active,
        }
        await createBankAccount(request)
        toast.success('Bank account created successfully')
      }
      onSuccess()
    } catch (err: any) {
      const errorMessage = err?.error?.message || err?.message || 'Failed to save bank account'
      if (err?.status === 409) {
        // Duplicate error
        setDuplicateError(errorMessage)
        toast.error('Duplicate account number detected', { description: errorMessage })
      } else {
        setFormError(errorMessage)
        toast.error('Failed to save bank account', { description: errorMessage })
      }
    }
  }

  const handleClose = () => {
    reset()
    setFormError(null)
    setDuplicateError(null)
    onClose()
  }

  return (
    <Dialog open={open} onOpenChange={(open) => !open && handleClose()}>
      <DialogContent className="max-w-2xl max-h-[90vh] overflow-y-auto">
        <form onSubmit={handleSubmit(onSubmit)} noValidate>
          <DialogHeader>
            <DialogTitle>{isEditMode ? 'Edit Bank Account' : 'Add Bank Account'}</DialogTitle>
            <DialogDescription>
              {isEditMode
                ? 'Update the bank account details below.'
                : 'Create a new bank account. Account number must be unique per company.'}
            </DialogDescription>
          </DialogHeader>

          <div className="space-y-6 py-4">
            {/* Error Messages */}
            {(formError || duplicateError) && (
              <div className="space-y-2">
                {formError && (
                  <Alert variant="destructive">
                    <AlertDescription>{formError}</AlertDescription>
                  </Alert>
                )}
                {duplicateError && (
                  <Alert variant="destructive">
                    <AlertDescription>
                      <strong>Duplicate detected:</strong> {duplicateError}
                    </AlertDescription>
                  </Alert>
                )}
              </div>
            )}

            {/* Primary Information */}
            <div className="space-y-4">
              <div className="flex items-center gap-2 pb-2 border-b">
                <Wallet className="h-5 w-5 text-primary" />
                <h3 className="text-base font-semibold">Account Information</h3>
              </div>
              <FieldGroup className="space-y-4">
                {/* Account Number - Required, only in create mode */}
                {!isEditMode && (
                  <Field className="gap-2">
                    <FieldLabel htmlFor="accountNumber" className="text-sm font-medium">
                      Account Number <span className="text-destructive">*</span>
                    </FieldLabel>
                    <FieldContent>
                      <Input
                        id="accountNumber"
                        placeholder="e.g., 1234567890"
                        {...register('accountNumber')}
                        aria-invalid={!!errors.accountNumber}
                        disabled={isSubmitting}
                        autoFocus
                        className="h-10"
                      />
                      {errors.accountNumber?.message && (
                        <FieldError>{errors.accountNumber.message}</FieldError>
                      )}
                      <p className="text-xs text-muted-foreground mt-1.5">
                        Must be unique per company
                      </p>
                    </FieldContent>
                  </Field>
                )}

                {/* Bank Name */}
                <Field className="gap-2">
                  <FieldLabel htmlFor="bankName" className="text-sm font-medium">
                    Bank Name <span className="text-destructive">*</span>
                  </FieldLabel>
                  <FieldContent>
                    <Input
                      id="bankName"
                      placeholder="e.g., Vietcombank, Techcombank"
                      {...register('bankName')}
                      aria-invalid={!!errors.bankName}
                      disabled={isSubmitting}
                      className="h-10"
                    />
                    {errors.bankName?.message && (
                      <FieldError>{errors.bankName.message}</FieldError>
                    )}
                  </FieldContent>
                </Field>

                {/* Branch */}
                <Field className="gap-2">
                  <FieldLabel htmlFor="branch" className="text-sm font-medium">
                    Branch
                  </FieldLabel>
                  <FieldContent>
                    <Input
                      id="branch"
                      placeholder="e.g., Ho Chi Minh City Branch"
                      {...register('branch')}
                      aria-invalid={!!errors.branch}
                      disabled={isSubmitting}
                      className="h-10"
                    />
                    {errors.branch?.message && <FieldError>{errors.branch.message}</FieldError>}
                  </FieldContent>
                </Field>

                {/* Account Type */}
                <Field className="gap-2">
                  <FieldLabel id="type-label" htmlFor="type" className="text-sm font-medium">
                    Account Type <span className="text-destructive">*</span>
                  </FieldLabel>
                  <FieldContent>
                    <Controller
                      name="type"
                      control={control}
                      render={({ field }) => (
                        <Select
                          value={field.value}
                          onValueChange={(value: AccountType) => field.onChange(value)}
                          disabled={isSubmitting}
                        >
                          <SelectTrigger id="type" aria-labelledby="type-label" className="h-10">
                            <SelectValue placeholder="Select account type" />
                          </SelectTrigger>
                          <SelectContent>
                            <SelectItem value="CASH">Cash</SelectItem>
                            <SelectItem value="BANK">Bank</SelectItem>
                          </SelectContent>
                        </Select>
                      )}
                    />
                    {errors.type?.message && <FieldError>{errors.type.message}</FieldError>}
                  </FieldContent>
                </Field>

                {/* Opening Balance */}
                <Field className="gap-2">
                  <FieldLabel htmlFor="openingBalance" className="text-sm font-medium">
                    Opening Balance <span className="text-destructive">*</span>
                  </FieldLabel>
                  <FieldContent>
                    <Input
                      id="openingBalance"
                      type="number"
                      step="0.01"
                      min="0"
                      placeholder="0.00"
                      {...register('openingBalance', { valueAsNumber: true })}
                      aria-invalid={!!errors.openingBalance}
                      disabled={isSubmitting}
                      className="h-10"
                    />
                    {errors.openingBalance?.message && (
                      <FieldError>{errors.openingBalance.message}</FieldError>
                    )}
                  </FieldContent>
                </Field>

                {/* Active Status - Only in create mode */}
                {!isEditMode && (
                  <Field className="gap-2">
                    <FieldLabel htmlFor="active" className="text-sm font-medium">
                      Active Status
                    </FieldLabel>
                    <FieldContent>
                      <Controller
                        name="active"
                        control={control}
                        render={({ field }) => (
                          <div className="flex items-center gap-2">
                            <Switch
                              id="active"
                              checked={field.value}
                              onCheckedChange={field.onChange}
                              disabled={isSubmitting}
                            />
                            <label htmlFor="active" className="text-sm text-muted-foreground">
                              {field.value ? 'Active' : 'Inactive'}
                            </label>
                          </div>
                        )}
                      />
                    </FieldContent>
                  </Field>
                )}
              </FieldGroup>
            </div>
          </div>

          <DialogFooter>
            <Button type="button" variant="outline" onClick={handleClose} disabled={isSubmitting}>
              Cancel
            </Button>
            <Button type="submit" disabled={isSubmitting}>
              {isSubmitting && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
              {isEditMode ? 'Update' : 'Create'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}

