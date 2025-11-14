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
import { Field, FieldContent, FieldError, FieldGroup, FieldLabel } from '@/components/ui/field'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { cn } from '@/lib/utils'
import { createVoucherType, updateVoucherType, getVoucherTypeById } from '@/services/voucherType'
import type { VoucherTypeCreateRequest, VoucherTypeUpdateRequest } from '@/types/voucherType'
import AccountCombobox from '@/components/account/AccountCombobox'

interface VoucherTypeDialogProps {
  open: boolean
  onClose: () => void
  onSuccess: () => void
  voucherTypeId?: number
}

const voucherTypeSchema = z.object({
  typeCode: z.string().min(1, 'Type code is required'),
  typeName: z.string().min(1, 'Type name is required'),
  debitAccountId: z.number().nullable().optional(),
  creditAccountId: z.number().nullable().optional(),
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

  const form = useForm<VoucherTypeFormValues>({
    resolver: zodResolver(voucherTypeSchema),
    defaultValues: {
      typeCode: '',
      typeName: '',
      debitAccountId: null,
      creditAccountId: null,
      description: '',
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

  const debitAccountId = watch('debitAccountId')
  const creditAccountId = watch('creditAccountId')

  // Load voucher type data for edit mode
  useEffect(() => {
    if (open && isEditMode && voucherTypeId) {
      loadVoucherType()
    } else if (open && !isEditMode) {
      reset({
        typeCode: '',
        typeName: '',
        debitAccountId: null,
        creditAccountId: null,
        description: '',
      })
      setFormError(null)
    }
  }, [open, isEditMode, voucherTypeId])

  const loadVoucherType = async () => {
    if (!voucherTypeId) return
    try {
      const voucherType = await getVoucherTypeById(voucherTypeId)
      reset({
        typeCode: voucherType.typeCode,
        typeName: voucherType.typeName,
        debitAccountId: voucherType.debitAccountId ?? null,
        creditAccountId: voucherType.creditAccountId ?? null,
        description: voucherType.description || '',
      })
      setFormError(null)
    } catch (err: any) {
      const errorMessage = err?.error?.message || err?.message || 'Failed to load voucher type'
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
          debitAccountId: values.debitAccountId ?? null,
          creditAccountId: values.creditAccountId ?? null,
          description: values.description?.trim() || undefined,
        }
        await updateVoucherType(voucherTypeId, request)
        toast.success('Voucher type updated successfully')
      } else {
        const request: VoucherTypeCreateRequest = {
          typeCode: values.typeCode.trim(),
          typeName: values.typeName.trim(),
          debitAccountId: values.debitAccountId ?? null,
          creditAccountId: values.creditAccountId ?? null,
          description: values.description?.trim() || undefined,
        }
        await createVoucherType(request)
        toast.success('Voucher type created successfully')
      }
      onSuccess()
    } catch (err: any) {
      const errorMessage = err?.error?.message || err?.message || 'Failed to save voucher type'
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
              {isEditMode ? 'Update the voucher type details below.' : 'Create a new voucher type.'}
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
                  {errors.typeCode?.message && <FieldError>{errors.typeCode.message}</FieldError>}
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
                  {errors.typeName?.message && <FieldError>{errors.typeName.message}</FieldError>}
                </FieldContent>
              </Field>
            </div>

            <div className="grid grid-cols-2 gap-4">
              <Field className="gap-2">
                <FieldLabel htmlFor="debitAccountId">Debit Account</FieldLabel>
                <FieldContent>
                  <AccountCombobox
                    value={debitAccountId ?? null}
                    onValueChange={(value) => setValue('debitAccountId', value)}
                    disabled={isSubmitting}
                    placeholder="Select debit account..."
                  />
                </FieldContent>
              </Field>

              <Field className="gap-2">
                <FieldLabel htmlFor="creditAccountId">Credit Account</FieldLabel>
                <FieldContent>
                  <AccountCombobox
                    value={creditAccountId ?? null}
                    onValueChange={(value) => setValue('creditAccountId', value)}
                    disabled={isSubmitting}
                    placeholder="Select credit account..."
                  />
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
