import { useEffect, useState } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { toast } from 'sonner'
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
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Field, FieldContent, FieldError, FieldGroup, FieldLabel } from '@/components/ui/field'
import { Loader2 } from 'lucide-react'
import { createTenant, type TenantProvisionRequest } from '../api/tenantApi'

const createTenantSchema = z.object({
  companyName: z.string().min(1, 'Tên công ty là bắt buộc'),
  taxCode: z
    .string()
    .min(1, 'Mã số thuế là bắt buộc')
    .regex(/^\d{10}(\d{4})?$/, 'Mã số thuế phải có 10 hoặc 14 chữ số'),
  address: z.string().optional(),
  legalRepresentative: z.string().optional(),
  adminEmail: z.string().email('Email không hợp lệ').min(1, 'Email là bắt buộc'),
  adminName: z.string().min(1, 'Tên người dùng là bắt buộc'),
})

type CreateTenantFormValues = z.infer<typeof createTenantSchema>

interface CreateTenantDialogProps {
  open: boolean
  onClose: () => void
  onSuccess: () => void
}

export default function CreateTenantDialog({ open, onClose, onSuccess }: CreateTenantDialogProps) {
  const form = useForm<CreateTenantFormValues>({
    resolver: zodResolver(createTenantSchema),
    defaultValues: {
      companyName: '',
      taxCode: '',
      address: '',
      legalRepresentative: '',
      adminEmail: '',
      adminName: '',
    },
    mode: 'onSubmit',
    reValidateMode: 'onBlur',
  })

  const {
    handleSubmit,
    register,
    reset,
    formState: { errors, isSubmitting },
  } = form

  const [formError, setFormError] = useState<string | null>(null)

  useEffect(() => {
    if (open) {
      reset({
        companyName: '',
        taxCode: '',
        address: '',
        legalRepresentative: '',
        adminEmail: '',
        adminName: '',
      })
      setFormError(null)
    }
  }, [open, reset])

  const onSubmit = async (values: CreateTenantFormValues) => {
    setFormError(null)
    try {
      const request: TenantProvisionRequest = {
        companyName: values.companyName.trim(),
        taxCode: values.taxCode.trim(),
        address: values.address?.trim() || undefined,
        legalRepresentative: values.legalRepresentative?.trim() || undefined,
        adminEmail: values.adminEmail.trim().toLowerCase(),
        adminName: values.adminName.trim(),
        fiscalYearStart: '01-01',
        currency: 'VND',
        coaPreset: 'TT200',
      }
      await createTenant(request)
      toast.success(`Đã gửi lời mời đến ${request.adminEmail}`)
      handleClose()
      onSuccess()
    } catch (err) {
      const errorMessage =
        err instanceof Error
          ? err.message
          : (err as { error?: { message?: string }; message?: string })?.error?.message ||
            (err as { message?: string })?.message ||
            'Không thể tạo tenant'
      setFormError(errorMessage)
    }
  }

  const handleClose = () => {
    reset()
    setFormError(null)
    onClose()
  }

  return (
    <Dialog open={open} onOpenChange={(open) => !open && handleClose()}>
      <DialogContent className="sm:max-w-[550px]">
        <form onSubmit={handleSubmit(onSubmit)} noValidate>
          <DialogHeader className="space-y-2">
            <DialogTitle>Tạo Tenant mới</DialogTitle>
            <DialogDescription>
              Tạo công ty mới và gửi lời mời đến quản trị viên. Họ sẽ nhận được email để thiết lập
              tài khoản.
            </DialogDescription>
          </DialogHeader>
          <FieldGroup className="space-y-2.5 py-4">
            {formError && (
              <Alert variant="destructive">
                <AlertDescription>{formError}</AlertDescription>
              </Alert>
            )}

            <Field className="gap-2">
              <FieldLabel htmlFor="companyName">
                Tên công ty <span className="text-destructive">*</span>
              </FieldLabel>
              <FieldContent>
                <Input
                  id="companyName"
                  placeholder="Công ty TNHH ABC"
                  {...register('companyName')}
                  aria-invalid={!!errors.companyName}
                  disabled={isSubmitting}
                  autoFocus
                />
                {errors.companyName?.message && (
                  <FieldError>{errors.companyName.message}</FieldError>
                )}
              </FieldContent>
            </Field>

            <Field className="gap-2">
              <FieldLabel htmlFor="taxCode">
                Mã số thuế <span className="text-destructive">*</span>
              </FieldLabel>
              <FieldContent>
                <Input
                  id="taxCode"
                  placeholder="0123456789"
                  {...register('taxCode')}
                  aria-invalid={!!errors.taxCode}
                  disabled={isSubmitting}
                />
                {errors.taxCode?.message && <FieldError>{errors.taxCode.message}</FieldError>}
              </FieldContent>
            </Field>

            <Field className="gap-2">
              <FieldLabel htmlFor="address">Địa chỉ</FieldLabel>
              <FieldContent>
                <Input
                  id="address"
                  placeholder="123 Đường ABC, Quận 1, TP.HCM"
                  {...register('address')}
                  disabled={isSubmitting}
                />
              </FieldContent>
            </Field>

            <Field className="gap-2">
              <FieldLabel htmlFor="legalRepresentative">Người đại diện pháp luật</FieldLabel>
              <FieldContent>
                <Input
                  id="legalRepresentative"
                  placeholder="Nguyễn Văn A"
                  {...register('legalRepresentative')}
                  disabled={isSubmitting}
                />
              </FieldContent>
            </Field>

            <div className="border-t pt-4 mt-4">
              <p className="text-sm font-medium mb-3">Thông tin quản trị viên</p>

              <Field className="gap-2">
                <FieldLabel htmlFor="adminEmail">
                  Email quản trị viên <span className="text-destructive">*</span>
                </FieldLabel>
                <FieldContent>
                  <Input
                    id="adminEmail"
                    type="email"
                    placeholder="admin@company.com"
                    {...register('adminEmail')}
                    aria-invalid={!!errors.adminEmail}
                    disabled={isSubmitting}
                  />
                  {errors.adminEmail?.message && (
                    <FieldError>{errors.adminEmail.message}</FieldError>
                  )}
                </FieldContent>
              </Field>

              <Field className="gap-2 mt-2.5">
                <FieldLabel htmlFor="adminName">
                  Tên quản trị viên <span className="text-destructive">*</span>
                </FieldLabel>
                <FieldContent>
                  <Input
                    id="adminName"
                    placeholder="Nguyễn Văn B"
                    {...register('adminName')}
                    aria-invalid={!!errors.adminName}
                    disabled={isSubmitting}
                  />
                  {errors.adminName?.message && <FieldError>{errors.adminName.message}</FieldError>}
                </FieldContent>
              </Field>
            </div>
          </FieldGroup>
          <DialogFooter className="gap-2">
            <Button type="button" variant="outline" onClick={handleClose} disabled={isSubmitting}>
              Hủy
            </Button>
            <Button type="submit" disabled={isSubmitting}>
              {isSubmitting ? (
                <>
                  <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                  Đang tạo...
                </>
              ) : (
                'Tạo Tenant'
              )}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
