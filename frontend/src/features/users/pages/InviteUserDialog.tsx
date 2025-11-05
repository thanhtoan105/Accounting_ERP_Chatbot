import { useEffect, useState } from 'react'
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
import { Field, FieldContent, FieldError, FieldGroup, FieldLabel } from '@/components/ui/field'
import { Loader2 } from 'lucide-react'
import { createInvitation, type CreateInvitationRequest } from '@/services/invitation'
import { getRoleDisplayName, type Role } from '@/utils/roles'

const ROLES: Role[] = ['admin', 'accountant', 'chief_accountant', 'cfo']

const inviteUserSchema = z.object({
  email: z.string().email('Please enter a valid email address').min(1, 'Email is required'),
  role: z.enum(['admin', 'accountant', 'chief_accountant', 'cfo'] as const),
})

type InviteUserFormValues = z.infer<typeof inviteUserSchema>

interface InviteUserDialogProps {
  open: boolean
  onClose: () => void
  onSuccess: () => void
}

/**
 * Dialog component for inviting new users via email.
 */
export default function InviteUserDialog({ open, onClose, onSuccess }: InviteUserDialogProps) {
  const form = useForm<InviteUserFormValues>({
    resolver: zodResolver(inviteUserSchema),
    defaultValues: {
      email: '',
      role: 'accountant',
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
      reset({
        email: '',
        role: 'accountant',
      })
      setFormError(null)
    }
  }, [open, reset])

  const onSubmit = async (values: InviteUserFormValues) => {
    setFormError(null)
    try {
      const request: CreateInvitationRequest = {
        email: values.email.trim().toLowerCase(),
        role: values.role !== 'accountant' ? values.role : undefined, // Don't send if default
      }
      await createInvitation(request)
      handleClose()
      onSuccess()
    } catch (err) {
      const errorMessage =
        err instanceof Error
          ? err.message
          : (err as { error?: { message?: string }; message?: string })?.error?.message ||
          (err as { message?: string })?.message ||
          'Failed to send invitation'
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
            <DialogTitle>Invite User</DialogTitle>
            <DialogDescription>
              Send an invitation email to a new user. They will receive a link to set up their account.
            </DialogDescription>
          </DialogHeader>
          <FieldGroup className="space-y-2.5 py-2">
            {formError && (
              <Alert variant="destructive">
                <AlertDescription>{formError}</AlertDescription>
              </Alert>
            )}
            <Field className="gap-2">
              <FieldLabel htmlFor="invite-email">
                Email Address <span className="text-destructive">*</span>
              </FieldLabel>
              <FieldContent>
                <Input
                  id="invite-email"
                  type="email"
                  placeholder="user@example.com"
                  {...register('email')}
                  aria-invalid={!!errors.email}
                  disabled={isSubmitting}
                  autoFocus
                />
                {errors.email?.message && (
                  <FieldError>{errors.email.message}</FieldError>
                )}
              </FieldContent>
            </Field>
            <Field className="gap-2">
              <FieldLabel htmlFor="invite-role">Role</FieldLabel>
              <FieldContent>
                <Select
                  value={selectedRole}
                  onValueChange={(value) => setValue('role', value as Role, { shouldValidate: true })}
                  disabled={isSubmitting}
                >
                  <SelectTrigger id="invite-role" aria-invalid={!!errors.role}>
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {ROLES.map((r) => (
                      <SelectItem key={r} value={r}>
                        {getRoleDisplayName(r)}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
                {errors.role?.message && (
                  <FieldError>{errors.role.message}</FieldError>
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
                  Sending...
                </>
              ) : (
                'Send Invitation'
              )}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}

