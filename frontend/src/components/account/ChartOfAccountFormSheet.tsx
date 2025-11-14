'use client'

import { useEffect, useState } from 'react'
import { useForm, Controller } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { toast } from 'sonner'
import { Loader2 } from 'lucide-react'
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetFooter,
  SheetHeader,
  SheetTitle,
} from '@/components/ui/sheet'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Textarea } from '@/components/ui/textarea'
import { Field, FieldContent, FieldError, FieldGroup, FieldLabel } from '@/components/ui/field'
import { Alert, AlertDescription } from '@/components/ui/alert'
import {
  Command,
  CommandEmpty,
  CommandGroup,
  CommandInput,
  CommandItem,
  CommandList,
} from '@/components/ui/command'
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover'
import { Check, ChevronsUpDown } from 'lucide-react'
import { cn } from '@/lib/utils'
import {
  createChartOfAccount,
  updateChartOfAccount,
  getChartOfAccountById,
  getChartOfAccounts,
} from '@/services/chartOfAccounts'
import type {
  ChartOfAccount,
  ChartOfAccountCreateRequest,
  ChartOfAccountUpdateRequest,
  AccountTypeValue,
} from '@/types/chartOfAccount'
import {
  ACCOUNT_TYPE_OPTIONS,
  ACCOUNT_TYPE_MAP,
  NORMAL_SIDE_TO_ACCOUNT_TYPE,
} from '@/types/chartOfAccount'
import AccountCombobox from './AccountCombobox'

interface ChartOfAccountFormSheetProps {
  open: boolean
  onClose: () => void
  onSuccess: () => void
  account?: ChartOfAccount | null
}

const accountFormSchema = z.object({
  code: z
    .string()
    .min(1, 'Account code is required')
    .regex(/^\d{1,4}$/, 'Account code must be numeric (1-4 digits)'),
  name: z.string().min(1, 'Account name is required'),
  nameEnglish: z.string().optional().nullable(),
  description: z.string().optional().nullable(),
  accountType: z.enum(['Debit Balance', 'Credit Balance', 'Hermaphrodite', 'No Balance'], {
    required_error: 'Account type (Characteristic) is required',
  }),
  parentId: z.number().nullable().optional(),
  orderingPosition: z.number().int().min(0).default(0),
  type: z.string().optional(), // For backend compatibility, will be set to default 'Asset' in create
})

type AccountFormValues = z.infer<typeof accountFormSchema>

export default function ChartOfAccountFormSheet({
  open,
  onClose,
  onSuccess,
  account,
}: ChartOfAccountFormSheetProps) {
  const isEditMode = !!account
  const [formError, setFormError] = useState<string | null>(null)
  const [accountTypeOpen, setAccountTypeOpen] = useState(false)
  const [parentAccounts, setParentAccounts] = useState<ChartOfAccount[]>([])

  const form = useForm<AccountFormValues>({
    resolver: zodResolver(accountFormSchema),
    defaultValues: {
      code: '',
      name: '',
      nameEnglish: null,
      description: null,
      accountType: 'Debit Balance',
      parentId: null,
      orderingPosition: 0,
    },
    mode: 'onSubmit',
    reValidateMode: 'onBlur',
  })

  const {
    handleSubmit,
    register,
    control,
    reset,
    watch,
    formState: { errors, isSubmitting },
  } = form

  // Load account data for edit mode
  useEffect(() => {
    if (open && isEditMode && account) {
      loadAccount()
    } else if (open && !isEditMode) {
      reset({
        code: '',
        name: '',
        nameEnglish: null,
        description: null,
        accountType: 'Debit Balance',
        parentId: null,
        orderingPosition: 0,
      })
      setFormError(null)
    }
  }, [open, isEditMode, account])

  // Load parent accounts for combobox
  useEffect(() => {
    if (open) {
      loadParentAccounts()
    }
  }, [open])

  const loadAccount = async () => {
    if (!account) return
    try {
      const accountData = await getChartOfAccountById(account.id)
      // Map backend normalSide to form accountType
      const accountType = NORMAL_SIDE_TO_ACCOUNT_TYPE[accountData.normalSide] || 'Debit Balance'
      reset({
        code: accountData.code,
        name: accountData.name,
        nameEnglish: accountData.nameEnglish || null,
        description: accountData.description || null,
        accountType: accountType as
          | 'Debit Balance'
          | 'Credit Balance'
          | 'Hermaphrodite'
          | 'No Balance',
        parentId: accountData.parentId || null,
        orderingPosition: accountData.orderingPosition || 0,
      })
      setFormError(null)
    } catch (err: any) {
      const errorMessage = err?.error?.message || err?.message || 'Failed to load account'
      setFormError(errorMessage)
      toast.error('Failed to load account', { description: errorMessage })
    }
  }

  const loadParentAccounts = async () => {
    try {
      const response = await getChartOfAccounts({ active: true })
      // Filter out the account being edited to prevent circular references
      const filtered =
        isEditMode && account ? response.data.filter((acc) => acc.id !== account.id) : response.data
      setParentAccounts(filtered)
    } catch (err) {
      console.error('Failed to load parent accounts:', err)
      setParentAccounts([])
    }
  }

  const onSubmit = async (values: AccountFormValues) => {
    setFormError(null)
    try {
      // Map form accountType to backend normalSide
      const normalSide = ACCOUNT_TYPE_MAP[values.accountType]

      if (isEditMode && account) {
        const request: ChartOfAccountUpdateRequest = {
          code: values.code.trim(),
          name: values.name.trim(),
          nameEnglish: values.nameEnglish?.trim() || null,
          description: values.description?.trim() || null,
          normalSide: normalSide,
          parentId: values.parentId || null,
          orderingPosition: values.orderingPosition,
        }
        await updateChartOfAccount(account.id, request)
        toast.success('Account updated successfully')
      } else {
        const request: ChartOfAccountCreateRequest = {
          code: values.code.trim(),
          name: values.name.trim(),
          nameEnglish: values.nameEnglish?.trim() || null,
          description: values.description?.trim() || null,
          type: 'Asset', // Default type, can be enhanced later
          normalSide: normalSide,
          parentId: values.parentId || null,
          orderingPosition: values.orderingPosition,
        }
        await createChartOfAccount(request)
        toast.success('Account created successfully')
      }
      onSuccess()
    } catch (err: any) {
      const errorMessage = err?.error?.message || err?.message || 'Failed to save account'
      setFormError(errorMessage)
      toast.error('Failed to save account', { description: errorMessage })
    }
  }

  const handleClose = () => {
    reset()
    setFormError(null)
    onClose()
  }

  const selectedAccountType = watch('accountType')
  const selectedAccountTypeLabel = selectedAccountType || 'Debit Balance'

  return (
    <Sheet open={open} onOpenChange={(open) => !open && handleClose()}>
      <SheetContent side="right" className="w-full sm:max-w-lg overflow-y-auto">
        <form onSubmit={handleSubmit(onSubmit)} noValidate>
          <SheetHeader>
            <SheetTitle>{isEditMode ? 'Edit Account' : 'Add Account'}</SheetTitle>
            <SheetDescription>
              {isEditMode
                ? 'Update the account details below.'
                : 'Create a new account in the chart of accounts.'}
            </SheetDescription>
          </SheetHeader>
          <FieldGroup className="space-y-4 py-4">
            {formError && (
              <Alert variant="destructive">
                <AlertDescription>{formError}</AlertDescription>
              </Alert>
            )}

            {/* Account Number (Code) */}
            <Field className="gap-2">
              <FieldLabel htmlFor="code">
                Account Number <span className="text-destructive">*</span>
              </FieldLabel>
              <FieldContent>
                <Input
                  id="code"
                  type="number"
                  placeholder="e.g., 111"
                  {...register('code')}
                  aria-invalid={!!errors.code}
                  disabled={isSubmitting || isEditMode}
                  autoFocus
                />
                {errors.code?.message && <FieldError>{errors.code.message}</FieldError>}
              </FieldContent>
            </Field>

            {/* Account Name */}
            <Field className="gap-2">
              <FieldLabel htmlFor="name">
                Account Name <span className="text-destructive">*</span>
              </FieldLabel>
              <FieldContent>
                <Input
                  id="name"
                  placeholder="e.g., Tiền mặt"
                  {...register('name')}
                  aria-invalid={!!errors.name}
                  disabled={isSubmitting}
                />
                {errors.name?.message && <FieldError>{errors.name.message}</FieldError>}
              </FieldContent>
            </Field>

            {/* Primary Account (Parent) */}
            <Field className="gap-2">
              <FieldLabel htmlFor="parentId">Primary Account</FieldLabel>
              <FieldContent>
                <Controller
                  name="parentId"
                  control={control}
                  render={({ field }) => (
                    <AccountCombobox
                      value={field.value || null}
                      onValueChange={(value) => field.onChange(value)}
                      disabled={isSubmitting}
                      excludeAccountId={isEditMode ? account?.id : undefined}
                    />
                  )}
                />
                {errors.parentId?.message && <FieldError>{errors.parentId.message}</FieldError>}
              </FieldContent>
            </Field>

            {/* Account Type (Characteristic) */}
            <Field className="gap-2">
              <FieldLabel htmlFor="accountType">
                Account Type (Characteristic) <span className="text-destructive">*</span>
              </FieldLabel>
              <FieldContent>
                <Controller
                  name="accountType"
                  control={control}
                  render={({ field }) => (
                    <Popover open={accountTypeOpen} onOpenChange={setAccountTypeOpen}>
                      <PopoverTrigger asChild>
                        <Button
                          variant="outline"
                          role="combobox"
                          aria-expanded={accountTypeOpen}
                          className="w-full justify-between"
                          disabled={isSubmitting}
                        >
                          <span className="truncate">{selectedAccountTypeLabel}</span>
                          <ChevronsUpDown className="ml-2 h-4 w-4 shrink-0 opacity-50" />
                        </Button>
                      </PopoverTrigger>
                      <PopoverContent className="w-full p-0" align="start">
                        <Command>
                          <CommandInput placeholder="Search account type..." />
                          <CommandList>
                            <CommandEmpty>No account type found.</CommandEmpty>
                            <CommandGroup>
                              {ACCOUNT_TYPE_OPTIONS.map((option) => (
                                <CommandItem
                                  key={option.label}
                                  value={option.label}
                                  onSelect={() => {
                                    field.onChange(option.label)
                                    setAccountTypeOpen(false)
                                  }}
                                >
                                  <Check
                                    className={cn(
                                      'mr-2 h-4 w-4',
                                      field.value === option.label ? 'opacity-100' : 'opacity-0',
                                    )}
                                  />
                                  {option.label}
                                </CommandItem>
                              ))}
                            </CommandGroup>
                          </CommandList>
                        </Command>
                      </PopoverContent>
                    </Popover>
                  )}
                />
                {errors.accountType?.message && (
                  <FieldError>{errors.accountType.message}</FieldError>
                )}
              </FieldContent>
            </Field>

            {/* Description */}
            <Field className="gap-2">
              <FieldLabel htmlFor="description">Description</FieldLabel>
              <FieldContent>
                <Textarea
                  id="description"
                  placeholder="Optional description..."
                  {...register('description')}
                  disabled={isSubmitting}
                  rows={4}
                />
                {errors.description?.message && (
                  <FieldError>{errors.description.message}</FieldError>
                )}
              </FieldContent>
            </Field>
          </FieldGroup>
          <SheetFooter>
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
          </SheetFooter>
        </form>
      </SheetContent>
    </Sheet>
  )
}
