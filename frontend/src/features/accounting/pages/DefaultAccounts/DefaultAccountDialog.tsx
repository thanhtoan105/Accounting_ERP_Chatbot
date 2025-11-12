'use client'

import { useEffect, useState } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { toast } from 'sonner'
import { Loader2 } from 'lucide-react'
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
import { Field, FieldContent, FieldError, FieldGroup, FieldLabel } from '@/components/ui/field'
import { Alert, AlertDescription } from '@/components/ui/alert'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import {
  createDefaultAccount,
  updateDefaultAccount,
  getDefaultAccountById,
} from '@/services/defaultAccount'
import type {
  DefaultAccountCreateRequest,
  DefaultAccountUpdateRequest,
  VoucherTypeOption,
  AccountDefault,
} from '@/types/defaultAccount'
import AccountDefaultsTable from './AccountDefaultsTable'
import { useRole } from '@/hooks/useRole'

interface DefaultAccountDialogProps {
  open: boolean
  onClose: () => void
  onSuccess: () => void
  defaultAccountId?: number
  initialData?: Partial<DefaultAccountFormValues> & { accountDefaults?: AccountDefault[] }
}

const VOUCHER_TYPE_OPTIONS: VoucherTypeOption[] = [
  'Cash Payment',
  'Bank Payment',
  'Cash Receipt',
  'Bank Receipt',
  'Other Business Voucher',
]

const defaultAccountSchema = z.object({
  voucherType: z.enum([
    'Cash Payment',
    'Bank Payment',
    'Cash Receipt',
    'Bank Receipt',
    'Other Business Voucher',
  ]),
  entryName: z.string().min(1, 'Entry name is required'),
  accountDefaults: z
    .array(
      z.object({
        columnName: z.string().min(1, 'Column name is required'),
        defaultAccountId: z.number().min(1, 'Account is required'),
      }),
    )
    .min(1, 'At least one account default is required'),
})

type DefaultAccountFormValues = z.infer<typeof defaultAccountSchema>

const createInitialAccountDefaults = (): AccountDefault[] => [
  {
    columnName: 'Debit Account (TK Nợ)',
    accountFilterIds: undefined,
    defaultAccountId: null,
    accountCode: null,
    accountName: null,
  },
  {
    columnName: 'Credit Account (TK Có)',
    accountFilterIds: undefined,
    defaultAccountId: null,
    accountCode: null,
    accountName: null,
  },
]

export default function DefaultAccountDialog({
  open,
  onClose,
  onSuccess,
  defaultAccountId,
  initialData,
}: DefaultAccountDialogProps) {
  const isEditMode = !!defaultAccountId
  const [formError, setFormError] = useState<string | null>(null)
  const { isAdmin } = useRole()
  const [accountDefaults, setAccountDefaults] = useState<AccountDefault[]>(
    createInitialAccountDefaults(),
  )

  const form = useForm<DefaultAccountFormValues>({
    resolver: zodResolver(defaultAccountSchema),
    defaultValues: {
      voucherType: 'Cash Payment',
      entryName: '',
      accountDefaults: [],
    },
    mode: 'onSubmit',
    reValidateMode: 'onBlur',
  })

  const {
    handleSubmit,
    register,
    reset,
    setValue,
    watch,
    formState: { errors, isSubmitting },
  } = form

  const voucherType = watch('voucherType')

  // Load default account data for edit mode or initialize with initialData
  useEffect(() => {
    if (open && isEditMode && defaultAccountId) {
      loadDefaultAccount()
    } else if (open && !isEditMode) {
      if (initialData) {
        // Pre-fill form with initial data (for duplication)
        reset({
          voucherType: initialData.voucherType || 'Cash Payment',
          entryName: initialData.entryName || '',
          accountDefaults: initialData.accountDefaults?.map((ad) => ({
            columnName: ad.columnName,
            defaultAccountId: ad.defaultAccountId ?? 0,
          })) || [],
        })
        setAccountDefaults(initialData.accountDefaults || createInitialAccountDefaults())
      } else {
        reset({
          voucherType: 'Cash Payment',
          entryName: '',
          accountDefaults: [],
        })
        setAccountDefaults(createInitialAccountDefaults())
      }
      setFormError(null)
    }
  }, [open, isEditMode, defaultAccountId, initialData])

  const loadDefaultAccount = async () => {
    if (!defaultAccountId) return
    try {
      const defaultAccount = await getDefaultAccountById(defaultAccountId)
      reset({
        voucherType: defaultAccount.voucherType,
        entryName: defaultAccount.entryName,
        accountDefaults: defaultAccount.accountDefaults.map((ad) => ({
          columnName: ad.columnName,
          defaultAccountId: ad.defaultAccountId ?? 0,
        })),
      })
      setAccountDefaults(defaultAccount.accountDefaults)
      setFormError(null)
    } catch (err: any) {
      const errorMessage =
        err?.error?.message || err?.message || 'Failed to load default account'
      setFormError(errorMessage)
      toast.error('Failed to load default account', { description: errorMessage })
    }
  }

  const handleRowChange = (index: number, row: AccountDefault) => {
    const updated = [...accountDefaults]
    updated[index] = row
    setAccountDefaults(updated)
    // Update form value for validation
    setValue(
      'accountDefaults',
      updated
        .filter((ad) => ad.defaultAccountId !== null && ad.defaultAccountId !== undefined)
        .map((ad) => ({
          columnName: ad.columnName,
          defaultAccountId: ad.defaultAccountId!,
        })),
      { shouldValidate: true },
    )
  }

  const handleAddRow = () => {
    const newRow: AccountDefault = {
      columnName: `Additional Account ${accountDefaults.length - 1}`,
      accountFilterIds: undefined,
      defaultAccountId: null,
      accountCode: null,
      accountName: null,
    }
    setAccountDefaults([...accountDefaults, newRow])
  }

  const handleRemoveRow = (index: number) => {
    const updated = accountDefaults.filter((_, i) => i !== index)
    setAccountDefaults(updated)
    // Update form value
    setValue(
      'accountDefaults',
      updated
        .filter((ad) => ad.defaultAccountId !== null && ad.defaultAccountId !== undefined)
        .map((ad) => ({
          columnName: ad.columnName,
          defaultAccountId: ad.defaultAccountId!,
        })),
      { shouldValidate: true },
    )
  }

  const onSubmit = async (values: DefaultAccountFormValues) => {
    setFormError(null)
    try {
      if (isEditMode && defaultAccountId) {
        const request: DefaultAccountUpdateRequest = {
          voucherType: values.voucherType,
          entryName: values.entryName.trim(),
          accountDefaults: values.accountDefaults,
        }
        await updateDefaultAccount(defaultAccountId, request)
        toast.success('Default account updated successfully')
      } else {
        const request: DefaultAccountCreateRequest = {
          voucherType: values.voucherType,
          entryName: values.entryName.trim(),
          accountDefaults: values.accountDefaults,
        }
        await createDefaultAccount(request)
        toast.success('Default account created successfully')
      }
      onSuccess()
    } catch (err: any) {
      const errorMessage =
        err?.error?.message || err?.message || 'Failed to save default account'
      setFormError(errorMessage)
      toast.error('Failed to save default account', { description: errorMessage })
    }
  }

  const handleClose = () => {
    reset()
    setAccountDefaults(createInitialAccountDefaults())
    setFormError(null)
    onClose()
  }

  return (
    <Dialog open={open} onOpenChange={(open) => !open && handleClose()}>
      <DialogContent className="sm:max-w-[900px] max-h-[90vh] overflow-y-auto">
        <form onSubmit={handleSubmit(onSubmit)} noValidate>
          <DialogHeader>
            <DialogTitle>
              {isEditMode ? 'Edit Default Account' : 'Create Default Account'}
            </DialogTitle>
            <DialogDescription>
              {isEditMode
                ? 'Update the default account details below.'
                : 'Create a new default account preset.'}
            </DialogDescription>
          </DialogHeader>
          <FieldGroup className="space-y-4 py-4">
            {formError && (
              <Alert variant="destructive">
                <AlertDescription>{formError}</AlertDescription>
              </Alert>
            )}

            <div className="grid grid-cols-[1fr_2fr] gap-4">
              <Field className="gap-2">
                <FieldLabel htmlFor="voucherType">
                  Voucher Type <span className="text-destructive">*</span>
                </FieldLabel>
                <FieldContent>
                  <Select
                    value={voucherType}
                    onValueChange={(value) => {
                      setValue('voucherType', value as VoucherTypeOption, { shouldValidate: true })
                    }}
                    disabled={isSubmitting}
                  >
                    <SelectTrigger id="voucherType" aria-invalid={!!errors.voucherType}>
                      <SelectValue placeholder="Select voucher type..." />
                    </SelectTrigger>
                    <SelectContent>
                      {VOUCHER_TYPE_OPTIONS.map((option) => (
                        <SelectItem key={option} value={option}>
                          {option}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                  {errors.voucherType?.message && (
                    <FieldError>{errors.voucherType.message}</FieldError>
                  )}
                </FieldContent>
              </Field>

              <Field className="gap-2">
                <FieldLabel htmlFor="entryName">
                  Entry Name <span className="text-destructive">*</span>
                </FieldLabel>
                <FieldContent>
                  <Input
                    id="entryName"
                    placeholder="e.g., Customer Cash Receipt (not per invoice)"
                    {...register('entryName')}
                    aria-invalid={!!errors.entryName}
                    disabled={isSubmitting}
                    autoFocus
                  />
                  {errors.entryName?.message && (
                    <FieldError>{errors.entryName.message}</FieldError>
                  )}
                </FieldContent>
              </Field>
            </div>

            <Field className="gap-2">
              <FieldLabel>Account Defaults</FieldLabel>
              <FieldContent>
                <AccountDefaultsTable
                  rows={accountDefaults}
                  voucherType={voucherType}
                  isAdmin={isAdmin()}
                  onRowChange={handleRowChange}
                  onAddRow={handleAddRow}
                  onRemoveRow={handleRemoveRow}
                  disabled={isSubmitting}
                />
                {errors.accountDefaults?.message && (
                  <FieldError>{errors.accountDefaults.message}</FieldError>
                )}
              </FieldContent>
            </Field>
          </FieldGroup>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={handleClose} disabled={isSubmitting}>
              Cancel
            </Button>
            <Button type="submit" disabled={isSubmitting}>
              {isSubmitting ? (
                <>
                  <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                  {isEditMode ? 'Updating...' : 'Creating...'}
                </>
              ) : (
                <>{isEditMode ? 'Update' : 'Create'}</>
              )}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}

