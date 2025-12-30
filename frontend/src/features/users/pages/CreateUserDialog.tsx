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
  Form,
  FormControl,
  FormDescription,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from '@/components/ui/form'
import { Loader2, User, Mail, Shield, Lock, AlertCircle, Sparkles } from 'lucide-react'
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
    reset,
    handleSubmit,
    formState: { isSubmitting },
  } = form
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
      <DialogContent className="sm:max-w-[550px] gap-0 p-0 overflow-hidden border-none shadow-xl">
        <div className="bg-gradient-to-b from-primary/10 to-transparent px-6 py-6 border-b">
          <DialogHeader className="space-y-2">
            <div className="flex items-center gap-2 text-primary mb-1">
              <div className="p-2 rounded-full bg-primary/10">
                <Sparkles className="w-5 h-5" />
              </div>
              <span className="text-xs font-semibold uppercase tracking-wider opacity-80">
                New Account
              </span>
            </div>
            <DialogTitle className="text-xl font-bold tracking-tight">Create User</DialogTitle>
            <DialogDescription className="text-muted-foreground/90">
              Manually create a user account. The user will be able to log in immediately with the
              provided credentials.
            </DialogDescription>
          </DialogHeader>
        </div>

        <Form {...form}>
          <form onSubmit={handleSubmit(onSubmit)} noValidate className="px-6 py-6 space-y-6">
            {formError && (
              <Alert
                variant="destructive"
                className="border-destructive/20 bg-destructive/5 text-destructive animate-in fade-in zoom-in-95 duration-200"
              >
                <AlertCircle className="h-4 w-4" />
                <AlertDescription>{formError}</AlertDescription>
              </Alert>
            )}

            <div className="space-y-4">
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <FormField
                  control={form.control}
                  name="fullName"
                  render={({ field }) => (
                    <FormItem className="space-y-1.5">
                      <FormLabel className="text-sm font-medium flex items-center gap-2">
                        <User className="w-4 h-4 text-muted-foreground" />
                        Full Name <span className="text-destructive">*</span>
                      </FormLabel>
                      <div className="relative group">
                        <FormControl>
                          <Input
                            placeholder="John Doe"
                            disabled={isSubmitting}
                            className="pl-9 transition-all border-[var(--border)] focus:ring-1 focus:ring-[var(--voucher-primary)] focus:border-[var(--voucher-primary)]"
                            {...field}
                          />
                        </FormControl>
                        <div className="absolute left-2.5 top-2.5 text-muted-foreground/50 group-focus-within:text-primary transition-colors">
                          <User className="w-4 h-4" />
                        </div>
                      </div>
                      <FormMessage className="ml-1" />
                    </FormItem>
                  )}
                />

                <FormField
                  control={form.control}
                  name="email"
                  render={({ field }) => (
                    <FormItem className="space-y-1.5">
                      <FormLabel className="text-sm font-medium flex items-center gap-2">
                        <Mail className="w-4 h-4 text-muted-foreground" />
                        Email Address <span className="text-destructive">*</span>
                      </FormLabel>
                      <div className="relative group">
                        <FormControl>
                          <Input
                            placeholder="user@example.com"
                            type="email"
                            disabled={isSubmitting}
                            autoFocus
                            className="pl-9 transition-all border-[var(--border)] focus:ring-1 focus:ring-[var(--voucher-primary)] focus:border-[var(--voucher-primary)]"
                            {...field}
                          />
                        </FormControl>
                        <div className="absolute left-2.5 top-2.5 text-muted-foreground/50 group-focus-within:text-primary transition-colors">
                          <Mail className="w-4 h-4" />
                        </div>
                      </div>
                      <FormMessage className="ml-1" />
                    </FormItem>
                  )}
                />
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <FormField
                  control={form.control}
                  name="role"
                  render={({ field }) => (
                    <FormItem className="space-y-1.5">
                      <FormLabel className="text-sm font-medium flex items-center gap-2">
                        <Shield className="w-4 h-4 text-muted-foreground" />
                        Role
                      </FormLabel>
                      <Select
                        onValueChange={field.onChange}
                        defaultValue={field.value}
                        disabled={isSubmitting || assignableRoles.length === 0}
                      >
                        <FormControl>
                          <SelectTrigger>
                            <SelectValue placeholder="Select a role" />
                          </SelectTrigger>
                        </FormControl>
                        <SelectContent>
                          {assignableRoles.map((r) => (
                            <SelectItem key={r} value={r}>
                              <span className="font-medium">{getRoleDisplayName(r)}</span>
                            </SelectItem>
                          ))}
                        </SelectContent>
                      </Select>
                      <FormMessage className="ml-1" />
                      {assignableRoles.length === 0 && (
                        <FormDescription className="text-yellow-600 dark:text-yellow-500">
                          You do not have permission to assign roles
                        </FormDescription>
                      )}
                    </FormItem>
                  )}
                />

                <FormField
                  control={form.control}
                  name="password"
                  render={({ field }) => (
                    <FormItem className="space-y-1.5">
                      <FormLabel className="text-sm font-medium flex items-center gap-2">
                        <Lock className="w-4 h-4 text-muted-foreground" />
                        Password <span className="text-destructive">*</span>
                      </FormLabel>
                      <div className="relative group">
                        <FormControl>
                          <Input
                            type="password"
                            placeholder="••••••••"
                            disabled={isSubmitting}
                            className="pl-9 transition-all border-[var(--border)] focus:ring-1 focus:ring-[var(--voucher-primary)] focus:border-[var(--voucher-primary)]"
                            {...field}
                          />
                        </FormControl>
                        <div className="absolute left-2.5 top-2.5 text-muted-foreground/50 group-focus-within:text-primary transition-colors">
                          <Lock className="w-4 h-4" />
                        </div>
                      </div>
                      <FormMessage className="ml-1" />
                      {!form.formState.errors.password && (
                        <FormDescription className="ml-1 text-xs">
                          At least 8 characters
                        </FormDescription>
                      )}
                    </FormItem>
                  )}
                />
              </div>

              <div className="bg-muted/30 p-3 rounded-lg border border-border/50 text-sm text-muted-foreground">
                <p className="flex items-center gap-2">
                  <AlertCircle className="w-4 h-4" />
                  Users created manually will not require email verification.
                </p>
              </div>
            </div>

            <DialogFooter className="gap-2 sm:justify-end">
              <Button type="button" variant="outline" onClick={handleClose} disabled={isSubmitting}>
                Cancel
              </Button>
              <Button type="submit" disabled={isSubmitting} className="min-w-[130px] shadow-sm">
                {isSubmitting ? (
                  <>
                    <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                    Creating...
                  </>
                ) : (
                  <>
                    <User className="mr-2 h-4 w-4" />
                    Create User
                  </>
                )}
              </Button>
            </DialogFooter>
          </form>
        </Form>
      </DialogContent>
    </Dialog>
  )
}
