import { useEffect, useState } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { useTranslation } from 'react-i18next'
import { X, Plus, Loader2, HelpCircle } from 'lucide-react'
import { toast } from 'sonner'

import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Checkbox } from '@/components/ui/checkbox'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import {
  Form,
  FormControl,
  FormDescription,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from '@/components/ui/form'
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@/components/ui/tooltip'
import { Badge } from '@/components/ui/badge'

import { useCreateSchedule, useUpdateSchedule } from '../../services/reportSchedules'
import type { ReportScheduleDTO, CreateReportScheduleRequest } from '../../types/reportSchedule'
import { periodService } from '@/services/period'
import type { AccountingPeriod } from '@/types/accountingPeriod'

const CRON_PRESETS = [
  {
    labelKey: 'scheduleManagement.cronPresets.monthly',
    label: 'Monthly (5th, 8:00 AM)',
    value: '0 0 8 5 * ?',
    description: 'Monthly on 5th at 8 AM',
  },
  {
    labelKey: 'scheduleManagement.cronPresets.weekly',
    label: 'Weekly (Monday, 8:00 AM)',
    value: '0 0 8 ? * MON',
    description: 'Weekly on Monday at 8 AM',
  },
  {
    labelKey: 'scheduleManagement.cronPresets.quarterly',
    label: 'Quarterly (10th, 8:00 AM)',
    value: '0 0 8 10 1,4,7,10 ?',
    description: 'Quarterly on 10th',
  },
  {
    labelKey: 'scheduleManagement.cronPresets.custom',
    label: 'Custom',
    value: 'custom',
    description: 'Custom cron expression',
  },
]

const REPORT_TYPES = [
  { value: 'S06', labelKey: 'scheduleManagement.reportTypes.S06', label: 'S06 - General Ledger' },
  { value: 'B01', labelKey: 'scheduleManagement.reportTypes.B01', label: 'B01 - Balance Sheet' },
  { value: 'B02', labelKey: 'scheduleManagement.reportTypes.B02', label: 'B02 - Income Statement' },
  {
    value: 'B03',
    labelKey: 'scheduleManagement.reportTypes.B03',
    label: 'B03 - Cash Flow Statement',
  },
  {
    value: 'F01',
    labelKey: 'scheduleManagement.reportTypes.F01',
    label: 'F01 - Notes to Financial Statements',
  },
]

const PERIOD_RULES = [
  {
    value: 'LAST_CLOSED',
    labelKey: 'scheduleManagement.periodRules.lastClosed',
    label: 'Last Closed Period',
  },
  { value: 'CURRENT', labelKey: 'scheduleManagement.periodRules.current', label: 'Current Period' },
  {
    value: 'SPECIFIC',
    labelKey: 'scheduleManagement.periodRules.specific',
    label: 'Specific Period',
  },
]

const EXPORT_FORMATS = [
  { value: 'PDF', label: 'PDF' },
  { value: 'EXCEL', label: 'Excel' },
]

const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

// Simple cron validation - checks basic format
const isValidCronExpression = (cron: string): boolean => {
  if (!cron || cron === 'custom') return false
  const parts = cron.trim().split(/\s+/)
  // Spring cron uses 6 fields: second minute hour day month weekday
  return parts.length === 6
}

const scheduleSchema = z.object({
  name: z.string().min(1, 'Schedule name is required').max(200, 'Schedule name max 200 characters'),
  reportType: z.string().min(1, 'Report type is required'),
  cronPreset: z.string(),
  cronExpression: z
    .string()
    .min(1, 'Cron expression is required')
    .refine(
      (val) => isValidCronExpression(val),
      'Invalid cron expression format (must have 6 fields)',
    ),
  periodRule: z.string().min(1, 'Period rule is required'),
  periodId: z.string().optional(),
  exportFormats: z.array(z.string()).min(1, 'Select at least one export format'),
  recipients: z.array(z.string().email('Invalid email')).min(1, 'Add at least one recipient'),
})

type ScheduleFormValues = z.infer<typeof scheduleSchema>

interface ScheduleFormProps {
  schedule?: ReportScheduleDTO | null
  onSuccess: () => void
  onCancel: () => void
}

export function ScheduleForm({ schedule, onSuccess, onCancel }: ScheduleFormProps) {
  const { t } = useTranslation()
  const [periods, setPeriods] = useState<AccountingPeriod[]>([])
  const [emailInput, setEmailInput] = useState('')
  const [emailError, setEmailError] = useState<string | null>(null)

  const createMutation = useCreateSchedule()
  const updateMutation = useUpdateSchedule()

  const isEditing = !!schedule

  const getInitialCronPreset = (cronExpression: string): string => {
    const preset = CRON_PRESETS.find((p) => p.value === cronExpression)
    return preset ? preset.value : 'custom'
  }

  const form = useForm<ScheduleFormValues>({
    resolver: zodResolver(scheduleSchema),
    defaultValues: {
      name: schedule?.name || '',
      reportType: schedule?.reportType || '',
      cronPreset: schedule ? getInitialCronPreset(schedule.cronExpression) : CRON_PRESETS[0].value,
      cronExpression: schedule?.cronExpression || CRON_PRESETS[0].value,
      periodRule: schedule?.periodRule || 'LAST_CLOSED',
      periodId: (schedule?.parameters?.periodId as string) || '',
      exportFormats: schedule?.exportFormats || ['PDF'],
      recipients: schedule?.recipients || [],
    },
  })

  const watchPeriodRule = form.watch('periodRule')
  const watchCronPreset = form.watch('cronPreset')
  const watchRecipients = form.watch('recipients')

  useEffect(() => {
    const loadPeriods = async () => {
      const allPeriods = await periodService.getAllPeriods()
      setPeriods(allPeriods)
    }
    loadPeriods()
  }, [])

  useEffect(() => {
    if (watchCronPreset !== 'custom') {
      form.setValue('cronExpression', watchCronPreset)
    }
  }, [watchCronPreset, form])

  const handleAddEmail = () => {
    const email = emailInput.trim()
    if (!email) return

    if (!emailRegex.test(email)) {
      setEmailError(t('scheduleManagement.form.invalidEmail', 'Email không hợp lệ'))
      return
    }

    if (watchRecipients.includes(email)) {
      setEmailError(t('scheduleManagement.form.duplicateEmail', 'Email đã tồn tại'))
      return
    }

    form.setValue('recipients', [...watchRecipients, email])
    setEmailInput('')
    setEmailError(null)
  }

  const handleRemoveEmail = (emailToRemove: string) => {
    form.setValue(
      'recipients',
      watchRecipients.filter((e) => e !== emailToRemove),
    )
  }

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter') {
      e.preventDefault()
      handleAddEmail()
    }
  }

  const onSubmit = async (values: ScheduleFormValues) => {
    try {
      const payload: CreateReportScheduleRequest = {
        name: values.name,
        reportType: values.reportType,
        cronExpression: values.cronExpression,
        periodRule: values.periodRule,
        periodId: values.periodRule === 'SPECIFIC' ? values.periodId : undefined,
        exportFormats: values.exportFormats,
        recipients: values.recipients,
      }

      if (isEditing && schedule) {
        await updateMutation.mutateAsync({
          id: schedule.id,
          data: payload,
        })
        toast.success(t('scheduleManagement.messages.updated', 'Đã cập nhật lịch'))
      } else {
        await createMutation.mutateAsync(payload)
        toast.success(t('scheduleManagement.messages.created', 'Đã tạo lịch mới'))
      }
      onSuccess()
    } catch (error) {
      toast.error(
        error instanceof Error
          ? error.message
          : t('scheduleManagement.messages.saveFailed', 'Không thể lưu lịch'),
      )
    }
  }

  const isPending = createMutation.isPending || updateMutation.isPending

  return (
    <Form {...form}>
      <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-6">
        <FormField
          control={form.control}
          name="name"
          render={({ field }) => (
            <FormItem>
              <FormLabel>{t('scheduleManagement.form.name', 'Tên lịch')}</FormLabel>
              <FormControl>
                <Input
                  placeholder={t(
                    'scheduleManagement.form.namePlaceholder',
                    'VD: Báo cáo B01 hàng tháng',
                  )}
                  {...field}
                />
              </FormControl>
              <FormMessage />
            </FormItem>
          )}
        />

        <FormField
          control={form.control}
          name="reportType"
          render={({ field }) => (
            <FormItem>
              <FormLabel>{t('scheduleManagement.form.reportType', 'Loại báo cáo')}</FormLabel>
              <Select onValueChange={field.onChange} value={field.value}>
                <FormControl>
                  <SelectTrigger>
                    <SelectValue
                      placeholder={t(
                        'scheduleManagement.form.selectReportType',
                        'Chọn loại báo cáo',
                      )}
                    />
                  </SelectTrigger>
                </FormControl>
                <SelectContent>
                  {REPORT_TYPES.map((type) => (
                    <SelectItem key={type.value} value={type.value}>
                      {type.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              <FormMessage />
            </FormItem>
          )}
        />

        <div className="space-y-3">
          <FormField
            control={form.control}
            name="cronPreset"
            render={({ field }) => (
              <FormItem>
                <FormLabel className="flex items-center gap-2">
                  {t('scheduleManagement.form.schedule', 'Lịch chạy')}
                  <TooltipProvider>
                    <Tooltip>
                      <TooltipTrigger asChild>
                        <HelpCircle className="h-4 w-4 text-muted-foreground" />
                      </TooltipTrigger>
                      <TooltipContent className="max-w-xs">
                        <p>
                          {t(
                            'scheduleManagement.form.cronHelp',
                            'Chọn tần suất chạy báo cáo tự động',
                          )}
                        </p>
                      </TooltipContent>
                    </Tooltip>
                  </TooltipProvider>
                </FormLabel>
                <Select onValueChange={field.onChange} value={field.value}>
                  <FormControl>
                    <SelectTrigger>
                      <SelectValue />
                    </SelectTrigger>
                  </FormControl>
                  <SelectContent>
                    {CRON_PRESETS.map((preset) => (
                      <SelectItem key={preset.value} value={preset.value}>
                        {preset.label}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
                <FormMessage />
              </FormItem>
            )}
          />

          {watchCronPreset === 'custom' && (
            <FormField
              control={form.control}
              name="cronExpression"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>
                    {t('scheduleManagement.form.cronExpression', 'Biểu thức Cron')}
                  </FormLabel>
                  <FormControl>
                    <Input placeholder="0 0 8 5 * ?" {...field} />
                  </FormControl>
                  <FormDescription>
                    {t(
                      'scheduleManagement.form.cronFormat',
                      'Định dạng: giây phút giờ ngày tháng ngày-tuần',
                    )}
                  </FormDescription>
                  <FormMessage />
                </FormItem>
              )}
            />
          )}
        </div>

        <FormField
          control={form.control}
          name="periodRule"
          render={({ field }) => (
            <FormItem>
              <FormLabel>{t('scheduleManagement.form.periodRule', 'Quy tắc kỳ')}</FormLabel>
              <Select onValueChange={field.onChange} value={field.value}>
                <FormControl>
                  <SelectTrigger>
                    <SelectValue />
                  </SelectTrigger>
                </FormControl>
                <SelectContent>
                  {PERIOD_RULES.map((rule) => (
                    <SelectItem key={rule.value} value={rule.value}>
                      {rule.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              <FormMessage />
            </FormItem>
          )}
        />

        {watchPeriodRule === 'SPECIFIC' && (
          <FormField
            control={form.control}
            name="periodId"
            render={({ field }) => (
              <FormItem>
                <FormLabel>{t('scheduleManagement.form.period', 'Kỳ kế toán')}</FormLabel>
                <Select onValueChange={field.onChange} value={field.value}>
                  <FormControl>
                    <SelectTrigger>
                      <SelectValue
                        placeholder={t('scheduleManagement.form.selectPeriod', 'Chọn kỳ')}
                      />
                    </SelectTrigger>
                  </FormControl>
                  <SelectContent>
                    {periods.map((period) => (
                      <SelectItem key={period.id} value={period.id}>
                        {period.periodName} ({period.fiscalYear})
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
                <FormMessage />
              </FormItem>
            )}
          />
        )}

        <FormField
          control={form.control}
          name="exportFormats"
          render={() => (
            <FormItem>
              <FormLabel>{t('scheduleManagement.form.exportFormats', 'Định dạng xuất')}</FormLabel>
              <div className="flex gap-4">
                {EXPORT_FORMATS.map((format) => (
                  <FormField
                    key={format.value}
                    control={form.control}
                    name="exportFormats"
                    render={({ field }) => (
                      <FormItem className="flex items-center space-x-2 space-y-0">
                        <FormControl>
                          <Checkbox
                            checked={field.value?.includes(format.value)}
                            onCheckedChange={(checked) => {
                              const current = field.value || []
                              if (checked) {
                                field.onChange([...current, format.value])
                              } else {
                                field.onChange(current.filter((v) => v !== format.value))
                              }
                            }}
                          />
                        </FormControl>
                        <Label className="font-normal cursor-pointer">{format.label}</Label>
                      </FormItem>
                    )}
                  />
                ))}
              </div>
              <FormMessage />
            </FormItem>
          )}
        />

        <div className="space-y-3">
          <Label>{t('scheduleManagement.form.recipients', 'Người nhận')}</Label>
          <div className="flex gap-2">
            <Input
              type="email"
              placeholder={t(
                'scheduleManagement.form.emailPlaceholder',
                'Nhập email và nhấn Enter',
              )}
              value={emailInput}
              onChange={(e) => {
                setEmailInput(e.target.value)
                setEmailError(null)
              }}
              onKeyDown={handleKeyDown}
              className={emailError ? 'border-destructive' : ''}
            />
            <Button type="button" variant="outline" size="icon" onClick={handleAddEmail}>
              <Plus className="h-4 w-4" />
            </Button>
          </div>
          {emailError && <p className="text-sm text-destructive">{emailError}</p>}
          {form.formState.errors.recipients && (
            <p className="text-sm text-destructive">{form.formState.errors.recipients.message}</p>
          )}
          {watchRecipients.length > 0 && (
            <div className="flex flex-wrap gap-2 mt-2">
              {watchRecipients.map((email) => (
                <Badge key={email} variant="secondary" className="gap-1 pr-1">
                  {email}
                  <button
                    type="button"
                    onClick={() => handleRemoveEmail(email)}
                    className="ml-1 hover:bg-muted rounded p-0.5"
                  >
                    <X className="h-3 w-3" />
                  </button>
                </Badge>
              ))}
            </div>
          )}
        </div>

        <div className="flex justify-end gap-3 pt-4 border-t">
          <Button type="button" variant="outline" onClick={onCancel}>
            {t('common.cancel', 'Hủy')}
          </Button>
          <Button type="submit" disabled={isPending}>
            {isPending && <Loader2 className="h-4 w-4 animate-spin mr-2" />}
            {isEditing
              ? t('scheduleManagement.form.update', 'Cập nhật')
              : t('scheduleManagement.form.create', 'Tạo lịch')}
          </Button>
        </div>
      </form>
    </Form>
  )
}

export default ScheduleForm
