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
import { Textarea } from '@/components/ui/textarea'
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover'
import { Command, CommandEmpty, CommandGroup, CommandInput, CommandItem, CommandList } from '@/components/ui/command'
import { X } from 'lucide-react'
import { Field, FieldContent, FieldError, FieldGroup, FieldLabel } from '@/components/ui/field'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { cn } from '@/lib/utils'
import {
  createVoucherType,
  updateVoucherType,
  getVoucherTypeById,
} from '@/services/voucherType'
import type { VoucherTypeCreateRequest, VoucherTypeUpdateRequest } from '@/types/voucherType'
import { getChartOfAccounts } from '@/services/chartOfAccounts'
import { filterLevel3Accounts } from '@/utils/accountUtils'
import type { ChartOfAccount } from '@/types/chartOfAccount'

interface VoucherTypeDialogProps {
  open: boolean
  onClose: () => void
  onSuccess: () => void
  voucherTypeId?: number
}

const voucherTypeSchema = z.object({
  typeCode: z.string().min(1, 'Type code is required'),
  typeName: z.string().min(1, 'Type name is required'),
  debitAccountId: z.number().optional().or(z.undefined()),
  creditAccountId: z.number().optional().or(z.undefined()),
  description: z.string().optional(),
})

type VoucherTypeFormValues = z.infer<typeof voucherTypeSchema>

export default function VoucherTypeDialog({
  open,
  onClose,
  onSuccess,
  voucherTypeId,
}: VoucherTypeDialogProps) {
  const isEditMode = !!voucherTypeId
  const [formError, setFormError] = useState<string | null>(null)
  const [accounts, setAccounts] = useState<ChartOfAccount[]>([])
  const [loadingAccounts, setLoadingAccounts] = useState(false)
  const [debitOpen, setDebitOpen] = useState(false)
  const [creditOpen, setCreditOpen] = useState(false)

  const form = useForm<VoucherTypeFormValues>({
    resolver: zodResolver(voucherTypeSchema),
    defaultValues: {
      typeCode: '',
      typeName: '',
      debitAccountId: undefined,
      creditAccountId: undefined,
      description: '',
    },
    mode: 'onSubmit',
    reValidateMode: 'onBlur',
  })

  const {
    handleSubmit,
    register,
    setValue,
    watch,
    reset,
    formState: { errors, isSubmitting },
  } = form

  const debitAccountId = watch('debitAccountId')
  const creditAccountId = watch('creditAccountId')

  const getAccountLabel = (id?: number) => {
    if (!id) return undefined
    const a = accounts.find((x) => x.id === id)
    return a ? `${a.code} - ${a.name}` : undefined
  }

  // Load accounts on mount
  useEffect(() => {
    if (open) {
      loadAccounts()
    }
  }, [open])

  // Load voucher type data for edit mode
  useEffect(() => {
    if (open && isEditMode && voucherTypeId) {
      loadVoucherType()
    } else if (open && !isEditMode) {
      reset({
        typeCode: '',
        typeName: '',
        debitAccountId: undefined,
        creditAccountId: undefined,
        description: '',
      })
      setFormError(null)
    }
  }, [open, isEditMode, voucherTypeId])

  const loadAccounts = async () => {
    try {
      setLoadingAccounts(true)
      const response = await getChartOfAccounts()
      const level3Accounts = filterLevel3Accounts(response.data)
      setAccounts(level3Accounts)
    } catch (err: any) {
      toast.error('Failed to load accounts', {
        description: err?.error?.message || err?.message || 'Unknown error',
      })
    } finally {
      setLoadingAccounts(false)
    }
  }

  const loadVoucherType = async () => {
    if (!voucherTypeId) return
    try {
      const voucherType = await getVoucherTypeById(voucherTypeId)
      reset({
        typeCode: voucherType.typeCode,
        typeName: voucherType.typeName,
        debitAccountId: voucherType.debitAccountId || undefined,
        creditAccountId: voucherType.creditAccountId || undefined,
        description: voucherType.description || '',
      })
      setFormError(null)
    } catch (err: any) {
      const errorMessage =
        err?.error?.message || err?.message || 'Failed to load voucher type'
      setFormError(errorMessage)
      toast.error('Failed to load voucher type', { description: errorMessage })
    }
  }

  const onSubmit = async (values: VoucherTypeFormValues) => {
    setFormError(null)
    try {
      if (isEditMode && voucherTypeId) {
        const request: VoucherTypeUpdateRequest = {
          typeCode: values.typeCode.trim(),
          typeName: values.typeName.trim(),
          debitAccountId: values.debitAccountId || undefined,
          creditAccountId: values.creditAccountId || undefined,
          description: values.description?.trim() || undefined,
        }
        await updateVoucherType(voucherTypeId, request)
        toast.success('Voucher type updated successfully')
      } else {
        const request: VoucherTypeCreateRequest = {
          typeCode: values.typeCode.trim(),
          typeName: values.typeName.trim(),
          debitAccountId: values.debitAccountId || undefined,
          creditAccountId: values.creditAccountId || undefined,
          description: values.description?.trim() || undefined,
        }
        await createVoucherType(request)
        toast.success('Voucher type created successfully')
      }
      onSuccess()
    } catch (err: any) {
      const errorMessage =
        err?.error?.message || err?.message || 'Failed to save voucher type'
      setFormError(errorMessage)
      toast.error('Failed to save voucher type', { description: errorMessage })
    }
  }

  const handleClose = () => {
    reset()
    setFormError(null)
    onClose()
  }

  return (
    <Dialog open={open} onOpenChange={(open) => !open && handleClose()}>
      <DialogContent className="sm:max-w-[700px]">
        <form onSubmit={handleSubmit(onSubmit)} noValidate>
          <DialogHeader>
            <DialogTitle>{isEditMode ? 'Edit Voucher Type' : 'Create Voucher Type'}</DialogTitle>
            <DialogDescription>
              {isEditMode
                ? 'Update the voucher type details below.'
                : 'Create a new voucher type with associated debit and credit accounts.'}
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
                <FieldLabel htmlFor="typeCode">
                  Type Code <span className="text-destructive">*</span>
                </FieldLabel>
                <FieldContent>
                  <Input
                    id="typeCode"
                    placeholder="e.g., CASH"
                    {...register('typeCode')}
                    aria-invalid={!!errors.typeCode}
                    disabled={isSubmitting}
                    autoFocus
                  />
                  {errors.typeCode?.message && (
                    <FieldError>{errors.typeCode.message}</FieldError>
                  )}
                </FieldContent>
              </Field>

              <Field className="gap-2">
                <FieldLabel htmlFor="typeName">
                  Type Name <span className="text-destructive">*</span>
                </FieldLabel>
                <FieldContent>
                  <Input
                    id="typeName"
                    placeholder="e.g., Cash Payment"
                    {...register('typeName')}
                    aria-invalid={!!errors.typeName}
                    disabled={isSubmitting}
                  />
                  {errors.typeName?.message && (
                    <FieldError>{errors.typeName.message}</FieldError>
                  )}
                </FieldContent>
              </Field>
            </div>

            <div className="grid grid-cols-2 gap-6">
              <Field className="gap-2 min-w-0">
                <FieldLabel htmlFor="debitAccountId">
                  Debit Account
                </FieldLabel>
                <FieldContent>
                  <Popover open={debitOpen} onOpenChange={setDebitOpen}>
                    <div className="relative w-full">
                      <PopoverTrigger asChild>
                        <Button
                          type="button"
                          variant="outline"
                          id="debitAccountId"
                          className={cn('w-full justify-between truncate pr-8')}
                          aria-invalid={!!errors.debitAccountId}
                          disabled={isSubmitting || loadingAccounts}
                        >
                          {getAccountLabel(debitAccountId) || 'Select debit account (optional)'}
                        </Button>
                      </PopoverTrigger>
                      {debitAccountId ? (
                        <button
                          type="button"
                          aria-label="Clear debit account"
                          className="absolute right-2 top-1/2 -translate-y-1/2 text-muted-foreground/70 hover:text-foreground transition h-5 w-5 rounded-md inline-flex items-center justify-center"
                          onClick={(e) => {
                            e.preventDefault()
                            e.stopPropagation()
                            setValue('debitAccountId', undefined, { shouldValidate: true })
                          }}
                        >
                          <X className="size-4" />
                        </button>
                      ) : null}
                    </div>
                    <PopoverContent
                      side="bottom"
                      align="start"
                      sideOffset={4}
                      avoidCollisions={false}
                      className="p-0 w-[--radix-popover-trigger-width]"
                    >
                      <Command>
                        <CommandInput placeholder="Search account..." className="h-9" />
                        <CommandList
                          className="max-h-80 overflow-auto"
                          onWheelCapture={(e) => e.stopPropagation()}
                        >
                          <CommandEmpty>No account found.</CommandEmpty>
                          <CommandGroup>
                            {accounts.map((a) => (
                              <CommandItem
                                key={a.id}
                                value={`${a.code} - ${a.name}`}
                                onSelect={() => {
                                  setValue('debitAccountId', a.id, { shouldValidate: true })
                                  setDebitOpen(false)
                                }}
                              >
                                {a.code} - {a.name}
                              </CommandItem>
                            ))}
                          </CommandGroup>
                        </CommandList>
                      </Command>
                    </PopoverContent>
                  </Popover>
                  {errors.debitAccountId?.message && (
                    <FieldError>{errors.debitAccountId.message}</FieldError>
                  )}
                </FieldContent>
              </Field>

              <Field className="gap-2 min-w-0">
                <FieldLabel htmlFor="creditAccountId">
                  Credit Account
                </FieldLabel>
                <FieldContent>
                  <Popover open={creditOpen} onOpenChange={setCreditOpen}>
                    <div className="relative w-full">
                      <PopoverTrigger asChild>
                        <Button
                          type="button"
                          variant="outline"
                          id="creditAccountId"
                          className={cn('w-full justify-between truncate pr-8')}
                          aria-invalid={!!errors.creditAccountId}
                          disabled={isSubmitting || loadingAccounts}
                        >
                          {getAccountLabel(creditAccountId) || 'Select credit account (optional)'}
                        </Button>
                      </PopoverTrigger>
                      {creditAccountId ? (
                        <button
                          type="button"
                          aria-label="Clear credit account"
                          className="absolute right-2 top-1/2 -translate-y-1/2 text-muted-foreground/70 hover:text-foreground transition h-5 w-5 rounded-md inline-flex items-center justify-center"
                          onClick={(e) => {
                            e.preventDefault()
                            e.stopPropagation()
                            setValue('creditAccountId', undefined, { shouldValidate: true })
                          }}
                        >
                          <X className="size-4" />
                        </button>
                      ) : null}
                    </div>
                    <PopoverContent
                      side="bottom"
                      align="start"
                      sideOffset={4}
                      avoidCollisions={false}
                      className="p-0 w-[--radix-popover-trigger-width]"
                    >
                      <Command>
                        <CommandInput placeholder="Search account..." className="h-9" />
                        <CommandList
                          className="max-h-80 overflow-auto"
                          onWheelCapture={(e) => e.stopPropagation()}
                        >
                          <CommandEmpty>No account found.</CommandEmpty>
                          <CommandGroup>
                            {accounts.map((a) => (
                              <CommandItem
                                key={a.id}
                                value={`${a.code} - ${a.name}`}
                                onSelect={() => {
                                  setValue('creditAccountId', a.id, { shouldValidate: true })
                                  setCreditOpen(false)
                                }}
                              >
                                {a.code} - {a.name}
                              </CommandItem>
                            ))}
                          </CommandGroup>
                        </CommandList>
                      </Command>
                    </PopoverContent>
                  </Popover>
                  {errors.creditAccountId?.message && (
                    <FieldError>{errors.creditAccountId.message}</FieldError>
                  )}
                </FieldContent>
              </Field>
            </div>

            <Field className="gap-2">
              <FieldLabel htmlFor="description">Description</FieldLabel>
              <FieldContent>
                <Textarea
                  id="description"
                  placeholder="Optional description..."
                  {...register('description')}
                  disabled={isSubmitting}
                  rows={3}
                />
              </FieldContent>
            </Field>
          </FieldGroup>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={handleClose} disabled={isSubmitting}>
              Cancel
            </Button>
            <Button type="submit" disabled={isSubmitting || loadingAccounts}>
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

