import { useEffect, useState, useMemo } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import {
  Dialog,
  DialogContent,
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
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Field, FieldContent, FieldError, FieldGroup, FieldLabel } from '@/components/ui/field'
import { Skeleton } from '@/components/ui/skeleton'
import { Loader2 } from 'lucide-react'
import { updateUser, type UpdateUserRequest, getUserById } from '@/services/user'
import { getRoleDisplayName, getAssignableRoles, canManageRole } from '@/utils/roles'
import { useAuth } from '@/hooks/useAuth'
import { useRole } from '@/hooks/useRole'
import type { User } from '@/services/auth'

const STATUSES = ['ACTIVE', 'INACTIVE', 'LOCKED']

const FORM_ROLES = [
  'admin',
  'accountant',
  'chief_accountant',
  'cfo',
  'super_admin',
  'accountant_general',
  'accountant_ar',
  'accountant_ap',
  'cashier',
  'finance',
] as const

type FormRole = (typeof FORM_ROLES)[number]

const editUserSchema = z.object({
  fullName: z
    .string()
    .min(1, 'Full name is required')
    .min(2, 'Full name must be at least 2 characters'),
  role: z.enum(FORM_ROLES),
  status: z.enum(['ACTIVE', 'INACTIVE', 'LOCKED'] as const),
})

type EditUserFormValues = z.infer<typeof editUserSchema>

interface EditUserDialogProps {
  open: boolean
  user: User | null
  onClose: () => void
  onSuccess: () => void
}

/**
 * Dialog component for editing a user (fullName, role, status).
 */
export default function EditUserDialog({ open, onClose, onSuccess, user }: EditUserDialogProps) {
  const { user: currentUser } = useAuth()
  const { canChangeRoles } = useRole()
  const assignableRoles = useMemo(() => getAssignableRoles(currentUser?.role), [currentUser?.role])

  const [fetching, setFetching] = useState(false)
  const [formError, setFormError] = useState<string | null>(null)
  const [userData, setUserData] = useState<User | null>(user)

  const isCurrentUser = userData?.id === currentUser?.id
  const canEditRole = canChangeRoles() && !isCurrentUser
  const canEditStatus = canChangeRoles() && !isCurrentUser

  // Check if current user can manage the target user's role
  const canManageTargetUser =
    userData && currentUser?.role ? canManageRole(currentUser.role, userData.role) : false

  const form = useForm<EditUserFormValues>({
    resolver: zodResolver(editUserSchema),
    defaultValues: {
      fullName: '',
      role: 'accountant',
      status: 'ACTIVE',
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
  const selectedStatus = watch('status')

  // Fetch user data when dialog opens
  useEffect(() => {
    if (open && user) {
      setUserData(user)
      if (user.fullName) {
        // User data is complete, set form values
        reset({
          fullName: user.fullName || '',
          role: (user.role?.toLowerCase() || 'accountant') as FormRole,
          status: (user.status?.toUpperCase() || 'ACTIVE') as 'ACTIVE' | 'INACTIVE' | 'LOCKED',
        })
        setFormError(null)
      } else {
        // Need to fetch user data
        const fetchUser = async () => {
          try {
            setFetching(true)
            const freshUser = await getUserById(user.id)
            setUserData(freshUser)
            reset({
              fullName: freshUser.fullName || '',
              role: (freshUser.role?.toLowerCase() || 'accountant') as FormRole,
              status: (freshUser.status?.toUpperCase() || 'ACTIVE') as
                | 'ACTIVE'
                | 'INACTIVE'
                | 'LOCKED',
            })
            setFormError(null)
          } catch (err) {
            setFormError('Failed to load user data')
          } finally {
            setFetching(false)
          }
        }
        fetchUser()
      }
    }
  }, [open, user, reset])

  const onSubmit = async (values: EditUserFormValues) => {
    if (!userData) return

    setFormError(null)

    try {
      const request: UpdateUserRequest = {
        fullName: values.fullName.trim(),
      }

      // Only include role if it's different and user can edit it
      if (canEditRole && canManageTargetUser && values.role !== userData.role?.toLowerCase()) {
        request.role = values.role
      }

      // Only include status if it's different and user can edit it
      if (
        canEditStatus &&
        canManageTargetUser &&
        values.status !== userData.status?.toUpperCase()
      ) {
        request.status = values.status
      }

      await updateUser(userData.id, request)
      handleClose()
      onSuccess()
    } catch (err) {
      const errorMessage =
        err instanceof Error
          ? err.message
          : (err as { error?: { message?: string }; message?: string })?.error?.message ||
            (err as { message?: string })?.message ||
            'Failed to update user'
      setFormError(errorMessage)
    }
  }

  const handleClose = () => {
    reset()
    setFormError(null)
    setUserData(null)
    onClose()
  }

  return (
    <Dialog open={open} onOpenChange={(open) => !open && handleClose()}>
      <DialogContent className="sm:max-w-[500px]">
        <form onSubmit={handleSubmit(onSubmit)} noValidate>
          <DialogHeader className="space-y-2">
            <DialogTitle>Edit User</DialogTitle>
            {/* Removed verbose helper text to keep dialog concise */}
          </DialogHeader>
          <FieldGroup className="space-y-1.5 py-2">
            {formError && (
              <Alert variant="destructive">
                <AlertDescription>{formError}</AlertDescription>
              </Alert>
            )}

            {fetching ? (
              <div className="space-y-3">
                <Skeleton className="h-4 w-full" />
                <Skeleton className="h-10 w-full" />
                <Skeleton className="h-10 w-full" />
                <Skeleton className="h-10 w-full" />
              </div>
            ) : (
              <>
                {userData && (
                  <div className="text-sm text-muted-foreground pb-1">{userData.email}</div>
                )}

                {(isCurrentUser || (!isCurrentUser && !canManageTargetUser)) && (
                  <Alert variant="destructive">
                    <AlertTitle>Permission restricted</AlertTitle>
                    <AlertDescription>
                      {isCurrentUser
                        ? 'You cannot change your own role or status.'
                        : 'You do not have permission to manage this user.'}
                    </AlertDescription>
                  </Alert>
                )}

                <Field className="gap-2">
                  <FieldLabel htmlFor="edit-fullname">
                    Full Name <span className="text-destructive">*</span>
                  </FieldLabel>
                  <FieldContent>
                    <Input
                      id="edit-fullname"
                      placeholder="John Doe"
                      {...register('fullName')}
                      aria-invalid={!!errors.fullName}
                      disabled={isSubmitting || fetching}
                      autoFocus
                    />
                    {errors.fullName?.message && <FieldError>{errors.fullName.message}</FieldError>}
                  </FieldContent>
                </Field>

                <Field className="gap-2">
                  <FieldLabel htmlFor="edit-role">Role</FieldLabel>
                  <FieldContent>
                    <Select
                      value={selectedRole}
                      onValueChange={(value) =>
                        setValue('role', value as FormRole, { shouldValidate: true })
                      }
                      disabled={isSubmitting || fetching || !canEditRole || !canManageTargetUser}
                    >
                      <SelectTrigger id="edit-role" aria-invalid={!!errors.role}>
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
                    {/* Removed redundant field descriptions */}
                  </FieldContent>
                </Field>

                <Field className="gap-2">
                  <FieldLabel htmlFor="edit-status">Status</FieldLabel>
                  <FieldContent>
                    <Select
                      value={selectedStatus}
                      onValueChange={(value) =>
                        setValue('status', value as 'ACTIVE' | 'INACTIVE' | 'LOCKED', {
                          shouldValidate: true,
                        })
                      }
                      disabled={isSubmitting || fetching || !canEditStatus || !canManageTargetUser}
                    >
                      <SelectTrigger id="edit-status" aria-invalid={!!errors.status}>
                        <SelectValue />
                      </SelectTrigger>
                      <SelectContent>
                        {STATUSES.map((s) => (
                          <SelectItem key={s} value={s}>
                            {s}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                    {errors.status?.message && <FieldError>{errors.status.message}</FieldError>}
                    {/* Removed redundant field descriptions */}
                  </FieldContent>
                </Field>
              </>
            )}
          </FieldGroup>
          <DialogFooter className="gap-2">
            <Button
              type="button"
              variant="outline"
              onClick={handleClose}
              disabled={isSubmitting || fetching}
            >
              Cancel
            </Button>
            <Button type="submit" disabled={isSubmitting || fetching}>
              {isSubmitting || fetching ? (
                <>
                  <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                  Saving...
                </>
              ) : (
                'Save'
              )}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
