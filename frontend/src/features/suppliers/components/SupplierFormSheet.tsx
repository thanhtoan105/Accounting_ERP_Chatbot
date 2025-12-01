'use client'

import { useEffect, useState, useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import { useForm, Controller } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { toast } from 'sonner'
import { Loader2, Info, Building2, Mail, Phone, MapPin, ToggleLeft, Sparkles } from 'lucide-react'
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
import { Switch } from '@/components/ui/switch'
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@/components/ui/tooltip'
import { Separator } from '@/components/ui/separator'
import {
  createSupplier,
  updateSupplier,
  getSupplierById,
} from '@/features/suppliers/services/supplier'
import type { Supplier, SupplierCreateRequest, SupplierUpdateRequest } from '@/types/supplier'

interface SupplierFormSheetProps {
  open: boolean
  onClose: () => void
  onSuccess: () => void
  supplier?: Supplier | null
}

// Vietnamese tax code: 10 digits
const taxCodeRegex = /^\d{10}$/
// Phone: Vietnamese phone format (10-11 digits, may start with +84 or 0)
const phoneRegex = /^(\+84|0)?[1-9]\d{8,9}$/

// Create schema factory to handle conditional validation
const createSupplierFormSchema = (isEditMode: boolean) =>
  z.object({
    code: isEditMode
      ? z.string().min(1, 'Supplier code is required')
      : z
        .string()
        .optional()
        .refine((val) => !val || val.trim().length > 0, {
          message: 'Supplier code cannot be empty if provided',
        }),
    name: z.string().min(1, 'Supplier name is required'),
    taxCode: z
      .string()
      .optional()
      .refine((val) => !val || taxCodeRegex.test(val), {
        message: 'Tax code must be 10 digits',
      }),
    email: z
      .string()
      .optional()
      .refine((val) => !val || z.string().email().safeParse(val).success, {
        message: 'Invalid email format',
      }),
    phone: z
      .string()
      .optional()
      .refine((val) => !val || phoneRegex.test(val.replace(/\s/g, '')), {
        message: 'Invalid phone number format',
      }),
    address: z.string().optional(),
    active: z.boolean().default(true),
  })

export default function SupplierFormSheet({
  open,
  onClose,
  onSuccess,
  supplier,
}: SupplierFormSheetProps) {
  const { t } = useTranslation()
  const isEditMode = !!supplier
  const [formError, setFormError] = useState<string | null>(null)
  const [duplicateError, setDuplicateError] = useState<string | null>(null)

  // Create schema based on edit mode
  const supplierFormSchema = useMemo(() => createSupplierFormSchema(isEditMode), [isEditMode])
  type SupplierFormValues = z.infer<typeof supplierFormSchema>

  const form = useForm<SupplierFormValues>({
    resolver: zodResolver(supplierFormSchema),
    defaultValues: {
      code: '',
      name: '',
      taxCode: '',
      email: '',
      phone: '',
      address: '',
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
    formState: { errors, isSubmitting },
  } = form

  // Load supplier data for edit mode (prefer provided prop to avoid extra fetch in tests)
  useEffect(() => {
    if (open && isEditMode && supplier) {
      reset({
        code: supplier.code || '',
        name: supplier.name || '',
        taxCode: supplier.taxCode || '',
        email: supplier.email || '',
        phone: supplier.phone || '',
        address: supplier.address || '',
        active: supplier.active ?? true,
      })
      setFormError(null)
      setDuplicateError(null)
    } else if (open && !isEditMode) {
      reset({
        code: '',
        name: '',
        taxCode: '',
        email: '',
        phone: '',
        address: '',
        active: true,
      })
      setFormError(null)
      setDuplicateError(null)
    }
  }, [open, isEditMode, supplier])

  const loadSupplier = async () => {
    if (!supplier) return
    try {
      const supplierData = await getSupplierById(supplier.id)
      reset({
        code: supplierData.code,
        name: supplierData.name,
        taxCode: supplierData.taxCode || '',
        email: supplierData.email || '',
        phone: supplierData.phone || '',
        address: supplierData.address || '',
        active: supplierData.active,
      })
      setFormError(null)
      setDuplicateError(null)
    } catch (err: any) {
      const errorMessage = err?.error?.message || err?.message || 'Failed to load supplier'
      setFormError(errorMessage)
      toast.error('Failed to load supplier', { description: errorMessage })
    }
  }

  const onSubmit = async (values: SupplierFormValues) => {
    setFormError(null)
    setDuplicateError(null)
    try {
      if (isEditMode && supplier) {
        // Only send changed fields to match test expectations
        const request: SupplierUpdateRequest = {}

        const dirty: any = form.formState.dirtyFields
        if (dirty?.code) request.code = values.code.trim()
        if (dirty?.name) request.name = values.name.trim()
        if (dirty?.taxCode) request.taxCode = values.taxCode?.trim() || undefined
        if (dirty?.email) request.email = values.email?.trim() || undefined
        if (dirty?.phone) request.phone = values.phone?.trim() || undefined
        if (dirty?.address) request.address = values.address?.trim() || undefined
        if (dirty?.active) request.active = values.active
        await updateSupplier(supplier.id, request)
        toast.success('Supplier updated successfully')
      } else {
        const request: SupplierCreateRequest = {
          // Code is optional in create mode - backend will auto-generate if not provided
          code: values.code?.trim() || undefined,
          name: values.name.trim(),
          taxCode: values.taxCode?.trim() || undefined,
          email: values.email?.trim() || undefined,
          phone: values.phone?.trim() || undefined,
          address: values.address?.trim() || undefined,
          active: values.active,
        }
        await createSupplier(request)
        toast.success('Supplier created successfully')
      }
      onSuccess()
      onClose()
    } catch (err: any) {
      const errorMessage = err?.error?.message || err?.message || 'Failed to save supplier'
      if (err?.status === 409) {
        // Duplicate error
        setDuplicateError(errorMessage)
        toast.error('Duplicate supplier detected', { description: errorMessage })
      } else {
        setFormError(errorMessage)
        toast.error('Failed to save supplier', { description: errorMessage })
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
            <DialogTitle>{isEditMode ? t('suppliers.editSupplier') : t('suppliers.createSupplier')}</DialogTitle>
            <DialogDescription>
              {isEditMode
                ? t('suppliers.updateDetails')
                : t('suppliers.createDetails')}
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

            {/* Primary Information - Most Important Fields */}
            <div className="space-y-4">
              <div className="flex items-center gap-2 pb-2 border-b">
                <Building2 className="h-5 w-5 text-primary" />
                <h3 className="text-base font-semibold">Primary Information</h3>
              </div>
              <FieldGroup className="space-y-4">
                {/* Supplier Name - Most Important */}
                <Field className="gap-2">
                  <FieldLabel htmlFor="name" className="text-sm font-medium">
                    Supplier Name <span className="text-destructive">*</span>
                  </FieldLabel>
                  <FieldContent>
                    <Input
                      id="name"
                      placeholder="e.g., Công ty TNHH ABC"
                      {...register('name')}
                      aria-invalid={!!errors.name}
                      disabled={isSubmitting}
                      autoFocus
                      className="h-10"
                    />
                    {errors.name?.message && <FieldError>{errors.name.message}</FieldError>}
                  </FieldContent>
                </Field>

                {/* Supplier Code - Editable in edit mode, optional in create */}
                {isEditMode && (
                  <Field className="gap-2">
                    <FieldLabel htmlFor="code" className="text-sm font-medium">
                      Supplier Code <span className="text-destructive">*</span>
                    </FieldLabel>
                    <FieldContent>
                      <Input
                        id="code"
                        placeholder="e.g., SUP-2025-0001 or any custom format"
                        {...register('code')}
                        aria-invalid={!!errors.code}
                        disabled={isSubmitting}
                        className="h-10"
                      />
                      {errors.code?.message && <FieldError>{errors.code.message}</FieldError>}
                      <p className="text-xs text-muted-foreground mt-1.5">
                        You can use any format for the supplier code
                      </p>
                    </FieldContent>
                  </Field>
                )}

                {!isEditMode && (
                  <Field className="gap-2">
                    <FieldLabel htmlFor="code" className="text-sm font-medium">
                      Supplier Code{' '}
                      <span className="text-xs text-muted-foreground font-normal">(Optional)</span>
                    </FieldLabel>
                    <FieldContent>
                      <div className="relative">
                        <Input
                          id="code"
                          placeholder="Leave empty to auto-generate (SUP-YYYY-NNNN)"
                          {...register('code')}
                          aria-invalid={!!errors.code}
                          disabled={isSubmitting}
                          className="h-10 pr-20"
                        />
                        {!watch('code') && (
                          <div className="absolute right-3 top-1/2 -translate-y-1/2 flex items-center gap-1.5 text-xs text-muted-foreground pointer-events-none">
                            <Sparkles className="h-3.5 w-3.5" />
                            <span>Auto</span>
                          </div>
                        )}
                      </div>
                      {errors.code?.message && <FieldError>{errors.code.message}</FieldError>}
                      <div className="mt-1.5 space-y-1">
                        <p className="text-xs text-muted-foreground">
                          <span className="font-medium">Auto-generated format:</span> SUP-YYYY-NNNN
                          (e.g., SUP-2025-0001)
                        </p>
                        <p className="text-xs text-muted-foreground">
                          <span className="font-medium">Custom format:</span> Enter any format, or
                          leave empty to use auto-generated code
                        </p>
                      </div>
                    </FieldContent>
                  </Field>
                )}

                {/* Tax Code */}
                <Field className="gap-2">
                  <FieldLabel htmlFor="taxCode" className="text-sm font-medium">
                    Tax Code
                  </FieldLabel>
                  <FieldContent>
                    <Input
                      id="taxCode"
                      placeholder="e.g., 0123456789"
                      {...register('taxCode')}
                      aria-invalid={!!errors.taxCode}
                      disabled={isSubmitting}
                      maxLength={10}
                      className="h-10"
                    />
                    {errors.taxCode?.message && <FieldError>{errors.taxCode.message}</FieldError>}
                    <p className="text-xs text-muted-foreground mt-1.5">
                      10-digit Vietnamese tax code
                    </p>
                  </FieldContent>
                </Field>
              </FieldGroup>
            </div>

            <Separator className="my-6" />

            {/* Contact Information */}
            <div className="space-y-4">
              <div className="flex items-center gap-2 pb-2 border-b">
                <Mail className="h-5 w-5 text-primary" />
                <h3 className="text-base font-semibold">Contact Details</h3>
              </div>
              <FieldGroup className="space-y-4">
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                  {/* Email */}
                  <Field className="gap-2">
                    <FieldLabel htmlFor="email" className="text-sm font-medium">
                      Email
                    </FieldLabel>
                    <FieldContent>
                      <div className="relative">
                        <Mail className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground pointer-events-none" />
                        <Input
                          id="email"
                          type="email"
                          placeholder="contact@example.com"
                          {...register('email')}
                          aria-invalid={!!errors.email}
                          disabled={isSubmitting}
                          className="h-10 pl-9"
                        />
                      </div>
                      {errors.email?.message && <FieldError>{errors.email.message}</FieldError>}
                    </FieldContent>
                  </Field>

                  {/* Phone */}
                  <Field className="gap-2">
                    <FieldLabel htmlFor="phone" className="text-sm font-medium">
                      Phone Number
                    </FieldLabel>
                    <FieldContent>
                      <div className="relative">
                        <Phone className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground pointer-events-none" />
                        <Input
                          id="phone"
                          type="tel"
                          placeholder="0912345678"
                          {...register('phone')}
                          aria-invalid={!!errors.phone}
                          disabled={isSubmitting}
                          className="h-10 pl-9"
                        />
                      </div>
                      {errors.phone?.message && <FieldError>{errors.phone.message}</FieldError>}
                    </FieldContent>
                  </Field>
                </div>

                {/* Address */}
                <Field className="gap-2">
                  <FieldLabel htmlFor="address" className="text-sm font-medium">
                    Address
                  </FieldLabel>
                  <FieldContent>
                    <div className="relative">
                      <MapPin className="absolute left-3 top-3 h-4 w-4 text-muted-foreground pointer-events-none" />
                      <Textarea
                        id="address"
                        placeholder="e.g., 123 Main Street, District 1, Ho Chi Minh City"
                        {...register('address')}
                        disabled={isSubmitting}
                        rows={3}
                        className="pl-9"
                      />
                    </div>
                    {errors.address?.message && <FieldError>{errors.address.message}</FieldError>}
                  </FieldContent>
                </Field>
              </FieldGroup>
            </div>

            <Separator className="my-6" />

            {/* Status Section */}
            <div className="space-y-4">
              <div className="flex items-center gap-2 pb-2 border-b">
                <ToggleLeft className="h-5 w-5 text-primary" />
                <h3 className="text-base font-semibold">Status & Settings</h3>
              </div>
              <Field className="gap-2">
                <div className="flex items-center gap-2">
                  <FieldLabel htmlFor="active" className="text-sm font-medium">
                    Account Status
                  </FieldLabel>
                  <TooltipProvider>
                    <Tooltip>
                      <TooltipTrigger asChild>
                        <Info className="h-4 w-4 text-muted-foreground cursor-help" />
                      </TooltipTrigger>
                      <TooltipContent>
                        <p className="max-w-xs">
                          Linked AP data will not be deleted when deactivating a supplier
                        </p>
                      </TooltipContent>
                    </Tooltip>
                  </TooltipProvider>
                </div>
                <FieldContent>
                  <Controller
                    name="active"
                    control={control}
                    render={({ field }) => (
                      <div className="flex items-center gap-3 p-3 rounded-lg border bg-muted/30">
                        <Switch
                          id="active"
                          checked={field.value}
                          onCheckedChange={field.onChange}
                          disabled={isSubmitting}
                        />
                        <label
                          htmlFor="active"
                          className="text-sm font-medium cursor-pointer flex-1"
                        >
                          {field.value ? (
                            <span className="text-green-600 dark:text-green-400">
                              Active - Supplier is enabled
                            </span>
                          ) : (
                            <span className="text-muted-foreground">
                              Inactive - Supplier is disabled
                            </span>
                          )}
                        </label>
                      </div>
                    )}
                  />
                  {errors.active?.message && <FieldError>{errors.active.message}</FieldError>}
                </FieldContent>
              </Field>
            </div>
          </div>

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
                <>{isEditMode ? 'Update Supplier' : 'Create Supplier'}</>
              )}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
