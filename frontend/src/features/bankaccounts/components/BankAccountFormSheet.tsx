'use client'

import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useForm, Controller } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { toast } from 'sonner'
import { Loader2, Banknote, Landmark, Check } from 'lucide-react'
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
import { Switch } from '@/components/ui/switch'
import { cn } from '@/lib/utils'
import {
  createBankAccount,
  updateBankAccount,
  getBankAccountById,
} from '@/features/bankaccounts/services/bankAccount'
import GLAccountSelect from '@/components/account/GLAccountSelect'
import type {
  BankAccount,
  BankAccountCreateRequest,
  BankAccountUpdateRequest,
} from '@/types/bankAccount'

interface BankAccountFormSheetProps {
  open: boolean
  onClose: () => void
  onSuccess: () => void
  bankAccount?: BankAccount | null
}

// Schema with conditional validation based on account type
const bankAccountFormSchema = z
  .object({
    type: z.enum(['CASH', 'BANK'], { message: 'Account type is required' }),
    // For CASH: optional (will auto-generate). For BANK: required
    accountNumber: z
      .string()
      .max(50, 'Account number must be at most 50 characters')
      .optional()
      .or(z.literal('')),
    // For CASH: Fund name. For BANK: Bank name
    bankName: z
      .string()
      .min(1, 'This field is required')
      .max(255, 'Must be at most 255 characters'),
    branch: z
      .string()
      .max(255, 'Branch must be at most 255 characters')
      .optional()
      .or(z.literal('')),
    openingBalance: z.number().min(0, 'Opening balance must be non-negative'),
    glAccountCode: z
      .string()
      .max(20, 'GL account code must be at most 20 characters')
      .optional()
      .or(z.literal('')),
    active: z.boolean(),
  })
  .refine(
    (data) => {
      // For BANK type, accountNumber is required
      if (data.type === 'BANK') {
        return data.accountNumber && data.accountNumber.length > 0
      }
      return true
    },
    {
      message: 'Account number is required for bank accounts',
      path: ['accountNumber'],
    },
  )

type BankAccountFormValues = z.infer<typeof bankAccountFormSchema>

export default function BankAccountFormSheet({
  open,
  onClose,
  onSuccess,
  bankAccount,
}: BankAccountFormSheetProps) {
  const { t: _t } = useTranslation()
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
      glAccountCode: '',
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
    watch,
    setValue,
    formState: { errors, isSubmitting },
  } = form

  // Watch account type to auto-set default GL code
  const accountType = watch('type')

  // Auto-set default GL code when type changes
  useEffect(() => {
    const currentGlCode = watch('glAccountCode')
    // Only auto-set if glAccountCode is empty or matches the other type's default
    const shouldAutoSet =
      !currentGlCode ||
      (accountType === 'BANK' && currentGlCode.startsWith('111')) ||
      (accountType === 'CASH' && currentGlCode.startsWith('112'))

    if (shouldAutoSet) {
      setValue('glAccountCode', accountType === 'BANK' ? '1121' : '1111')
    }
  }, [accountType, setValue, watch])

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
        glAccountCode: '',
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
        glAccountCode: bankAccountData.glAccountCode || '',
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
          glAccountCode: values.glAccountCode?.trim() || undefined,
        }
        await updateBankAccount(bankAccount.id, request)
        toast.success(
          values.type === 'CASH'
            ? 'Cập nhật quỹ thành công'
            : 'Cập nhật tài khoản ngân hàng thành công',
        )
      } else {
        // For CASH type, auto-generate account number if not provided
        let accountNumber = values.accountNumber?.trim() || ''
        if (values.type === 'CASH' && !accountNumber) {
          // Generate unique ID for cash fund: CASH-{timestamp}
          accountNumber = `CASH-${Date.now()}`
        }

        const request: BankAccountCreateRequest = {
          accountNumber: accountNumber,
          bankName: values.bankName.trim(),
          branch: values.branch?.trim() || undefined,
          type: values.type,
          openingBalance: values.openingBalance,
          glAccountCode: values.glAccountCode?.trim() || undefined,
          active: values.active,
        }
        await createBankAccount(request)
        toast.success(
          values.type === 'CASH'
            ? 'Tạo quỹ tiền mặt thành công'
            : 'Tạo tài khoản ngân hàng thành công',
        )
      }
      onSuccess()
    } catch (err: any) {
      const errorMessage = err?.error?.message || err?.message || 'Lỗi khi lưu'
      if (err?.status === 409) {
        // Duplicate error
        setDuplicateError(errorMessage)
        toast.error('Trùng lặp', { description: errorMessage })
      } else {
        setFormError(errorMessage)
        toast.error('Lỗi', { description: errorMessage })
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
            <DialogTitle>
              {isEditMode
                ? accountType === 'CASH'
                  ? 'Chỉnh sửa Quỹ tiền mặt'
                  : 'Chỉnh sửa Tài khoản ngân hàng'
                : 'Tạo Quỹ / Tài khoản ngân hàng'}
            </DialogTitle>
            <DialogDescription>
              {isEditMode
                ? 'Cập nhật thông tin chi tiết'
                : 'Chọn loại tài khoản và nhập thông tin cần thiết'}
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

            {/* Account Type Selection - Card Style */}
            {!isEditMode && (
              <div className="space-y-3">
                <FieldLabel className="text-sm font-medium">
                  Chọn loại tài khoản <span className="text-destructive">*</span>
                </FieldLabel>
                <Controller
                  name="type"
                  control={control}
                  render={({ field }) => (
                    <div className="grid grid-cols-2 gap-3">
                      {/* Cash Card */}
                      <button
                        type="button"
                        onClick={() => field.onChange('CASH')}
                        disabled={isSubmitting}
                        className={cn(
                          'relative flex flex-col items-center gap-2 rounded-lg border-2 p-4 text-center transition-all hover:border-primary/50 hover:bg-accent/50',
                          field.value === 'CASH'
                            ? 'border-primary bg-primary/5 ring-1 ring-primary/20'
                            : 'border-muted bg-background',
                          isSubmitting && 'cursor-not-allowed opacity-50',
                        )}
                      >
                        {field.value === 'CASH' && (
                          <div className="absolute right-2 top-2">
                            <Check className="h-4 w-4 text-primary" />
                          </div>
                        )}
                        <div
                          className={cn(
                            'flex h-12 w-12 items-center justify-center rounded-full',
                            field.value === 'CASH'
                              ? 'bg-primary/10 text-primary'
                              : 'bg-muted text-muted-foreground',
                          )}
                        >
                          <Banknote className="h-6 w-6" />
                        </div>
                        <div>
                          <p
                            className={cn(
                              'font-semibold',
                              field.value === 'CASH' ? 'text-primary' : 'text-foreground',
                            )}
                          >
                            Quỹ tiền mặt
                          </p>
                          <p className="text-xs text-muted-foreground">TK 111 - Tiền mặt</p>
                        </div>
                      </button>

                      {/* Bank Card */}
                      <button
                        type="button"
                        onClick={() => field.onChange('BANK')}
                        disabled={isSubmitting}
                        className={cn(
                          'relative flex flex-col items-center gap-2 rounded-lg border-2 p-4 text-center transition-all hover:border-primary/50 hover:bg-accent/50',
                          field.value === 'BANK'
                            ? 'border-primary bg-primary/5 ring-1 ring-primary/20'
                            : 'border-muted bg-background',
                          isSubmitting && 'cursor-not-allowed opacity-50',
                        )}
                      >
                        {field.value === 'BANK' && (
                          <div className="absolute right-2 top-2">
                            <Check className="h-4 w-4 text-primary" />
                          </div>
                        )}
                        <div
                          className={cn(
                            'flex h-12 w-12 items-center justify-center rounded-full',
                            field.value === 'BANK'
                              ? 'bg-primary/10 text-primary'
                              : 'bg-muted text-muted-foreground',
                          )}
                        >
                          <Landmark className="h-6 w-6" />
                        </div>
                        <div>
                          <p
                            className={cn(
                              'font-semibold',
                              field.value === 'BANK' ? 'text-primary' : 'text-foreground',
                            )}
                          >
                            Tài khoản ngân hàng
                          </p>
                          <p className="text-xs text-muted-foreground">TK 112 - Tiền gửi NH</p>
                        </div>
                      </button>
                    </div>
                  )}
                />
                {errors.type?.message && <FieldError>{errors.type.message}</FieldError>}
              </div>
            )}

            {/* Show current type in edit mode */}
            {isEditMode && (
              <div className="flex items-center gap-3 rounded-lg border bg-muted/30 p-3">
                <div
                  className={cn(
                    'flex h-10 w-10 items-center justify-center rounded-full',
                    'bg-primary/10 text-primary',
                  )}
                >
                  {accountType === 'CASH' ? (
                    <Banknote className="h-5 w-5" />
                  ) : (
                    <Landmark className="h-5 w-5" />
                  )}
                </div>
                <div>
                  <p className="font-medium">
                    {accountType === 'CASH' ? 'Quỹ tiền mặt' : 'Tài khoản ngân hàng'}
                  </p>
                  <p className="text-xs text-muted-foreground">
                    {accountType === 'CASH' ? 'TK 111 - Tiền mặt' : 'TK 112 - Tiền gửi NH'}
                  </p>
                </div>
              </div>
            )}

            {/* Divider */}
            <div className="border-t pt-4">
              <h3 className="text-sm font-medium text-muted-foreground mb-4">
                {accountType === 'CASH' ? 'Thông tin quỹ' : 'Thông tin tài khoản'}
              </h3>
            </div>

            {/* Detail Fields */}
            <div className="space-y-4">
              <FieldGroup className="space-y-4">
                {/* Account Number - Only for BANK type, only in create mode */}
                {accountType === 'BANK' && !isEditMode && (
                  <Field className="gap-2">
                    <FieldLabel htmlFor="accountNumber" className="text-sm font-medium">
                      Số tài khoản <span className="text-destructive">*</span>
                    </FieldLabel>
                    <FieldContent>
                      <Input
                        id="accountNumber"
                        placeholder="VD: 0001232225411"
                        {...register('accountNumber')}
                        aria-invalid={!!errors.accountNumber}
                        disabled={isSubmitting}
                        autoFocus
                        className="h-10"
                      />
                      {errors.accountNumber?.message && (
                        <FieldError>{errors.accountNumber.message}</FieldError>
                      )}
                    </FieldContent>
                  </Field>
                )}

                {/* Bank Name / Fund Name - Dynamic label based on type */}
                <Field className="gap-2">
                  <FieldLabel htmlFor="bankName" className="text-sm font-medium">
                    {accountType === 'CASH' ? 'Tên quỹ' : 'Tên ngân hàng'}{' '}
                    <span className="text-destructive">*</span>
                  </FieldLabel>
                  <FieldContent>
                    <Input
                      id="bankName"
                      placeholder={
                        accountType === 'CASH'
                          ? 'VD: Quỹ tiền mặt VND, Quỹ USD'
                          : 'VD: Vietcombank, Agribank, BIDV'
                      }
                      {...register('bankName')}
                      aria-invalid={!!errors.bankName}
                      disabled={isSubmitting}
                      className="h-10"
                    />
                    {errors.bankName?.message && <FieldError>{errors.bankName.message}</FieldError>}
                  </FieldContent>
                </Field>

                {/* Branch - Only for BANK type */}
                {accountType === 'BANK' && (
                  <Field className="gap-2">
                    <FieldLabel htmlFor="branch" className="text-sm font-medium">
                      Chi nhánh
                    </FieldLabel>
                    <FieldContent>
                      <Input
                        id="branch"
                        placeholder="VD: Chi nhánh TP.HCM"
                        {...register('branch')}
                        aria-invalid={!!errors.branch}
                        disabled={isSubmitting}
                        className="h-10"
                      />
                      {errors.branch?.message && <FieldError>{errors.branch.message}</FieldError>}
                    </FieldContent>
                  </Field>
                )}

                {/* Opening Balance */}
                <Field className="gap-2">
                  <FieldLabel htmlFor="openingBalance" className="text-sm font-medium">
                    Số dư đầu kỳ <span className="text-destructive">*</span>
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

                {/* GL Account Code */}
                <Field className="gap-2">
                  <FieldLabel htmlFor="glAccountCode" className="text-sm font-medium">
                    Số tài khoản kế toán <span className="text-destructive">*</span>
                  </FieldLabel>
                  <FieldContent>
                    <Controller
                      name="glAccountCode"
                      control={control}
                      render={({ field }) => (
                        <GLAccountSelect
                          value={field.value || null}
                          onValueChange={field.onChange}
                          accountType={accountType}
                          disabled={isSubmitting}
                          placeholder="Chọn tài khoản..."
                        />
                      )}
                    />
                    {errors.glAccountCode?.message && (
                      <FieldError>{errors.glAccountCode.message}</FieldError>
                    )}
                    <p className="text-xs text-muted-foreground mt-1.5">
                      Tài khoản kế toán theo Thông tư 200/2014/TT-BTC
                    </p>
                  </FieldContent>
                </Field>

                {/* Active Status - Only in create mode */}
                {!isEditMode && (
                  <Field className="gap-2">
                    <FieldLabel htmlFor="active" className="text-sm font-medium">
                      Trạng thái
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
                              {field.value ? 'Đang hoạt động' : 'Ngừng hoạt động'}
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
              Hủy
            </Button>
            <Button type="submit" disabled={isSubmitting}>
              {isSubmitting && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
              {isEditMode ? 'Cập nhật' : 'Tạo mới'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
