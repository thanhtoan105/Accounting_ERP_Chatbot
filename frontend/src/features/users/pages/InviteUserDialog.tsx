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
import {
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from '@/components/ui/form'
import { Loader2, Mail, UserPlus, Shield, Info, AlertCircle } from 'lucide-react'
import { createInvitation, type CreateInvitationRequest } from '@/services/invitation'
import { getRoleDisplayName, type Role } from '@/utils/roles'
import { useAuth } from '@/hooks/useAuth'
import { setCompanyId } from '@/utils/axios'

const ROLES: Role[] = ['admin', 'accountant', 'chief_accountant', 'cfo']

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

const inviteUserSchema = z.object({
  email: z.string().email('Please enter a valid email address').min(1, 'Email is required'),
  role: z.enum(FORM_ROLES),
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
  const { user } = useAuth()
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
    reset,
    handleSubmit,
    formState: { isSubmitting },
  } = form
  const [formError, setFormError] = useState<string | null>(null)

  // Ensure company context is synced from user session when dialog opens
  useEffect(() => {
    if (open && user?.companyId) {
      setCompanyId(user.companyId)
    }
  }, [open, user?.companyId])

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

    if (!user?.companyId) {
      setFormError('Company context is required. Please refresh the page and try again.')
      return
    }

    setCompanyId(user.companyId)

    try {
      const request: CreateInvitationRequest = {
        email: values.email.trim().toLowerCase(),
        role: values.role !== 'accountant' ? values.role : undefined,
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
      <DialogContent className="sm:max-w-[500px] gap-0 p-0 overflow-hidden border-none shadow-xl">
        <div className="bg-gradient-to-b from-primary/10 to-transparent px-6 py-6 border-b">
          <DialogHeader className="space-y-2">
            <div className="flex items-center gap-2 text-primary mb-1">
              <div className="p-2 rounded-full bg-primary/10">
                <UserPlus className="w-5 h-5" />
              </div>
              <span className="text-xs font-semibold uppercase tracking-wider opacity-80">
                Invitation
              </span>
            </div>
            <DialogTitle className="text-xl font-bold tracking-tight">Invite User</DialogTitle>
            <DialogDescription className="text-muted-foreground/90">
              Send an invitation email to a new team member. They will receive a link to set up
              their account.
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
                          placeholder="colleague@company.com"
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

              <FormField
                control={form.control}
                name="role"
                render={({ field }) => (
                  <FormItem className="space-y-1.5">
                    <FormLabel className="text-sm font-medium flex items-center gap-2">
                      <Shield className="w-4 h-4 text-muted-foreground" />
                      Role Assignment
                    </FormLabel>
                    <Select
                      onValueChange={field.onChange}
                      defaultValue={field.value}
                      disabled={isSubmitting}
                    >
                      <FormControl>
                        <SelectTrigger>
                          <SelectValue placeholder="Select a role" />
                        </SelectTrigger>
                      </FormControl>
                      <SelectContent>
                        {ROLES.map((r) => (
                          <SelectItem key={r} value={r}>
                            <span className="font-medium">{getRoleDisplayName(r)}</span>
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                    <FormMessage className="ml-1" />

                    <div className="flex items-start gap-2 mt-2 p-2 rounded-md bg-muted/40 text-xs text-muted-foreground">
                      <Info className="w-3.5 h-3.5 shrink-0 mt-0.5 text-primary/60" />
                      <p>
                        The invited user will receive an email with a secure link valid for 48
                        hours.
                      </p>
                    </div>
                  </FormItem>
                )}
              />
            </div>

            <DialogFooter className="gap-2 sm:justify-end">
              <Button type="button" variant="outline" onClick={handleClose} disabled={isSubmitting}>
                Cancel
              </Button>
              <Button type="submit" disabled={isSubmitting} className="min-w-[130px] shadow-sm">
                {isSubmitting ? (
                  <>
                    <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                    Sending...
                  </>
                ) : (
                  <>
                    <Mail className="mr-2 h-4 w-4" />
                    Send Invitation
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
