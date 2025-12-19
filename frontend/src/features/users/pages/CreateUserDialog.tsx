import { useEffect, useState, useMemo } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
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
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Alert, AlertDescription } from '@/components/ui/alert'
import {
  Field,
  FieldContent,
  FieldDescription,
  FieldError,
  FieldGroup,
  FieldLabel,
} from '@/components/ui/field'
import { Loader2 } from 'lucide-react'
import { createUser, type CreateUserRequest } from '@/services/user'
import { getRoleDisplayName, getAssignableRoles } from '@/utils/roles'
import { useAuth } from '@/hooks/useAuth'

interface CreateUserDialogProps {
  open: boolean
  onClose: () => void
  onSuccess: () => void
}

/**
 * Dialog component for creating a new user directly (with password).
 */
export default function CreateUserDialog({ open, onClose, onSuccess }: CreateUserDialogProps) {
  const { user: currentUser } = useAuth()
  const assignableRoles = useMemo(() => getAssignableRoles(currentUser?.role), [currentUser?.role])

  const createUserSchema = z.object({
    email: z.string().email('Please enter a valid email address').min(1, 'Email is required'),
    fullName: z
      .string()
      .min(1, 'Full name is required')
      .min(2, 'Full name must be at least 2 characters'),
    password: z.string().min(8, 'Password must be at least 8 characters'),
    role: z.enum(['admin', 'accountant', 'chief_accountant', 'cfo', 'super_admin'] as const),
  })

  type CreateUserFormValues = z.infer<typeof createUserSchema>
  type FormRole = CreateUserFormValues['role']

  const form = useForm<CreateUserFormValues>({
    resolver: zodResolver(createUserSchema),
    defaultValues: {
      email: '',
      fullName: '',
      password: '',
      role: (assignableRoles[0] || 'accountant') as FormRole,
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

  const selectedRole = watch('role')
  const [formError, setFormError] = useState<string | null>(null)

  // Reset form when dialog opens/closes
  useEffect(() => {
    if (open) {
      const defaultRole = (assignableRoles[0] || 'accountant') as FormRole
      reset({
        email: '',
        fullName: '',
        password: '',
        role: defaultRole,
      })
      setFormError(null)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open])

  const onSubmit = async (values: CreateUserFormValues) => {
    setFormError(null)
    try {
      const request: CreateUserRequest = {
        email: values.email.trim().toLowerCase(),
        fullName: values.fullName.trim(),
        password: values.password,
        role: values.role !== 'accountant' ? values.role : undefined, // Don't send if default
      }
      await createUser(request)
      handleClose()
      onSuccess()
    } catch (err) {
      const errorMessage =
        err instanceof Error
          ? err.message
          : (err as { error?: { message?: string }; message?: string })?.error?.message ||
            (err as { message?: string })?.message ||
            'Failed to create user'
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
      <DialogContent className="sm:max-w-[500px]">
        <form onSubmit={handleSubmit(onSubmit)} noValidate>
          <DialogHeader className="space-y-2">
            <DialogTitle>Create User</DialogTitle>
            <DialogDescription>
              Create a new user account directly. The user will be able to log in immediately with
              the provided credentials.
            </DialogDescription>
          </DialogHeader>
          <FieldGroup className="space-y-1 py-1">
            {formError && (
              <Alert variant="destructive">
                <AlertDescription>{formError}</AlertDescription>
              </Alert>
            )}
            <Field className="gap-2">
              <FieldLabel htmlFor="create-email">
                Email Address <span className="text-destructive">*</span>
              </FieldLabel>
              <FieldContent>
                <Input
                  id="create-email"
                  type="email"
                  placeholder="user@example.com"
                  {...register('email')}
                  aria-invalid={!!errors.email}
                  disabled={isSubmitting}
                  autoFocus
                />
                {errors.email?.message && <FieldError>{errors.email.message}</FieldError>}
              </FieldContent>
            </Field>
            <Field className="gap-2">
              <FieldLabel htmlFor="create-fullname">
                Full Name <span className="text-destructive">*</span>
              </FieldLabel>
              <FieldContent>
                <Input
                  id="create-fullname"
                  placeholder="John Doe"
                  {...register('fullName')}
                  aria-invalid={!!errors.fullName}
                  disabled={isSubmitting}
                />
                {errors.fullName?.message && <FieldError>{errors.fullName.message}</FieldError>}
              </FieldContent>
            </Field>
            <Field className="gap-2">
              <FieldLabel htmlFor="create-password">
                Password <span className="text-destructive">*</span>
              </FieldLabel>
              <FieldContent>
                <Input
                  id="create-password"
                  type="password"
                  {...register('password')}
                  aria-invalid={!!errors.password}
                  disabled={isSubmitting}
                />
                {errors.password?.message && <FieldError>{errors.password.message}</FieldError>}
                <FieldDescription>Must be at least 8 characters</FieldDescription>
              </FieldContent>
            </Field>
            <Field className="gap-2">
              <FieldLabel htmlFor="create-role">Role</FieldLabel>
              <FieldContent>
                <Select
                  value={selectedRole}
                  onValueChange={(value) =>
                    setValue('role', value as FormRole, { shouldValidate: true })
                  }
                  disabled={isSubmitting || assignableRoles.length === 0}
                >
                  <SelectTrigger id="create-role" aria-invalid={!!errors.role}>
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {assignableRoles.map((r) => (
                      <SelectItem key={r} value={r}>
                        {getRoleDisplayName(r)}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
                {errors.role?.message && <FieldError>{errors.role.message}</FieldError>}
                {assignableRoles.length === 0 && (
                  <FieldDescription>You do not have permission to assign roles</FieldDescription>
                )}
              </FieldContent>
            </Field>
          </FieldGroup>
          <DialogFooter className="gap-2">
            <Button type="button" variant="outline" onClick={handleClose} disabled={isSubmitting}>
              Cancel
            </Button>
            <Button type="submit" disabled={isSubmitting}>
              {isSubmitting ? (
                <>
                  <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                  Creating...
                </>
              ) : (
                'Create User'
              )}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
