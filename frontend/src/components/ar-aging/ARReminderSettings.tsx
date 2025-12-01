'use client'

import { useEffect, useState } from 'react'
import { useForm } from 'react-hook-form'
import { Save, Loader2 } from 'lucide-react'
import { toast } from 'sonner'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Switch } from '@/components/ui/switch'
import { Separator } from '@/components/ui/separator'
import { arAgingApi } from '@/features/accounting/services/arAgingApi'
import type { ARReminderConfig } from '@/features/accounting/services/arAgingApi'

export function ARReminderSettings() {
  const [loading, setLoading] = useState(false)
  const [saving, setSaving] = useState(false)

  const {
    register,
    handleSubmit,
    setValue,
    watch,
    formState: { errors, isDirty },
  } = useForm<ARReminderConfig>({
    defaultValues: {
      preDueDays: 3,
      dueDateEnabled: true,
      postDueCadenceDays: 7,
    },
  })

  const dueDateEnabled = watch('dueDateEnabled')

  // Load current configuration
  useEffect(() => {
    const loadConfig = async () => {
      try {
        setLoading(true)
        const config = await arAgingApi.getReminderConfig()
        setValue('preDueDays', config.preDueDays)
        setValue('dueDateEnabled', config.dueDateEnabled)
        setValue('postDueCadenceDays', config.postDueCadenceDays)
      } catch (err: any) {
        const message = err?.response?.data?.message || 'Failed to load reminder configuration'
        toast.error('Error', { description: message })
      } finally {
        setLoading(false)
      }
    }
    loadConfig()
  }, [setValue])

  const onSubmit = async (data: ARReminderConfig) => {
    try {
      setSaving(true)
      await arAgingApi.updateReminderConfig(data)
      toast.success('Reminder settings saved successfully')
    } catch (err: any) {
      const message = err?.response?.data?.message || 'Failed to save reminder settings'
      toast.error('Error', { description: message })
    } finally {
      setSaving(false)
    }
  }

  if (loading) {
    return (
      <Card>
        <CardHeader>
          <CardTitle>AR Reminder Settings</CardTitle>
          <CardDescription>
            Configure automated reminder schedules for overdue invoices
          </CardDescription>
        </CardHeader>
        <CardContent>
          <div className="flex items-center justify-center py-8">
            <Loader2 className="h-8 w-8 animate-spin text-muted-foreground" />
          </div>
        </CardContent>
      </Card>
    )
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>AR Reminder Settings</CardTitle>
        <CardDescription>
          Configure automated reminder schedules for overdue invoices
        </CardDescription>
      </CardHeader>
      <CardContent>
        <form onSubmit={handleSubmit(onSubmit)} className="space-y-6">
          {/* Pre-Due Days */}
          <div className="space-y-2">
            <Label htmlFor="preDueDays">Pre-Due Reminder (Days Before Due Date)</Label>
            <Input
              id="preDueDays"
              type="number"
              min="0"
              max="30"
              {...register('preDueDays', {
                required: 'Pre-due days is required',
                min: { value: 0, message: 'Must be at least 0' },
                max: { value: 30, message: 'Must be at most 30' },
                valueAsNumber: true,
              })}
              className={errors.preDueDays ? 'border-red-500' : ''}
            />
            {errors.preDueDays && (
              <p className="text-sm text-red-500">{errors.preDueDays.message}</p>
            )}
            <p className="text-sm text-muted-foreground">
              Send reminder this many days before the invoice due date (0 to disable)
            </p>
          </div>

          <Separator />

          {/* Due Date Reminder */}
          <div className="flex items-center justify-between space-x-4">
            <div className="flex-1 space-y-1">
              <Label htmlFor="dueDateEnabled">Due Date Reminder</Label>
              <p className="text-sm text-muted-foreground">Send reminder on the invoice due date</p>
            </div>
            <Switch
              id="dueDateEnabled"
              checked={dueDateEnabled}
              onCheckedChange={(checked) =>
                setValue('dueDateEnabled', checked, { shouldDirty: true })
              }
            />
          </div>

          <Separator />

          {/* Post-Due Cadence */}
          <div className="space-y-2">
            <Label htmlFor="postDueCadenceDays">Post-Due Reminder Cadence (Days)</Label>
            <Input
              id="postDueCadenceDays"
              type="number"
              min="1"
              max="30"
              {...register('postDueCadenceDays', {
                required: 'Post-due cadence is required',
                min: { value: 1, message: 'Must be at least 1' },
                max: { value: 30, message: 'Must be at most 30' },
                valueAsNumber: true,
              })}
              className={errors.postDueCadenceDays ? 'border-red-500' : ''}
            />
            {errors.postDueCadenceDays && (
              <p className="text-sm text-red-500">{errors.postDueCadenceDays.message}</p>
            )}
            <p className="text-sm text-muted-foreground">
              Send reminders every N days after the due date for overdue invoices
            </p>
          </div>

          <Separator />

          {/* Example Schedule */}
          <div className="rounded-lg bg-muted p-4">
            <h4 className="text-sm font-semibold mb-2">Example Schedule</h4>
            <ul className="text-sm text-muted-foreground space-y-1">
              <li>
                • <strong>Day -3:</strong> Pre-due reminder (if configured)
              </li>
              <li>
                • <strong>Day 0:</strong> Due date reminder{' '}
                {dueDateEnabled ? '(enabled)' : '(disabled)'}
              </li>
              <li>
                • <strong>Day +7, +14, +21...:</strong> Post-due reminders (every{' '}
                {watch('postDueCadenceDays') || 7} days)
              </li>
            </ul>
          </div>

          {/* Save Button */}
          <div className="flex justify-end">
            <Button type="submit" disabled={saving || !isDirty}>
              {saving ? (
                <>
                  <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                  Saving...
                </>
              ) : (
                <>
                  <Save className="mr-2 h-4 w-4" />
                  Save Settings
                </>
              )}
            </Button>
          </div>
        </form>
      </CardContent>
    </Card>
  )
}
