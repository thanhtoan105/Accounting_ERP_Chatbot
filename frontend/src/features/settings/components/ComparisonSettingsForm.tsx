/**
 * Comparison Settings Form
 *
 * Admin-only form for configuring multi-period comparison settings:
 * - Variance threshold (percent and absolute)
 * - Default comparison mode
 * - Display preferences (sparklines, hide immaterial)
 */

import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Label } from '@/components/ui/label'
import { Input } from '@/components/ui/input'
import { Button } from '@/components/ui/button'
import { Switch } from '@/components/ui/switch'
import { Slider } from '@/components/ui/slider'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Skeleton } from '@/components/ui/skeleton'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { AlertCircle, Save, RotateCcw } from 'lucide-react'
import {
  useComparisonSettings,
  useUpdateComparisonSettings,
} from '@/features/accounting/services/comparisonSettings'
import type {
  ComparisonSettingsDTO,
  ComparisonMode,
} from '@/features/accounting/types/multiPeriodReport'

const COMPARISON_MODES: { value: ComparisonMode; labelKey: string }[] = [
  { value: 'YOY', labelKey: 'comparisonSettings.modes.yoy' },
  { value: 'MOM', labelKey: 'comparisonSettings.modes.mom' },
  { value: 'QUARTERLY', labelKey: 'comparisonSettings.modes.quarterly' },
  { value: 'CUSTOM', labelKey: 'comparisonSettings.modes.custom' },
]

export function ComparisonSettingsForm() {
  const { t } = useTranslation()
  const { data: settings, isLoading, error: loadError } = useComparisonSettings()
  const updateMutation = useUpdateComparisonSettings()

  const [formValues, setFormValues] = useState<ComparisonSettingsDTO>({
    varianceThresholdPercent: 10,
    varianceThresholdAbsolute: 1000000,
    defaultComparisonMode: 'YOY',
    showSparklines: true,
    hideImmaterialDefault: false,
  })
  const [isDirty, setIsDirty] = useState(false)

  useEffect(() => {
    if (settings) {
      setFormValues(settings)
      setIsDirty(false)
    }
  }, [settings])

  const handleChange = <K extends keyof ComparisonSettingsDTO>(
    key: K,
    value: ComparisonSettingsDTO[K],
  ) => {
    setFormValues((prev) => ({ ...prev, [key]: value }))
    setIsDirty(true)
  }

  const handleReset = () => {
    if (settings) {
      setFormValues(settings)
      setIsDirty(false)
    }
  }

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    try {
      await updateMutation.mutateAsync(formValues)
      toast.success(t('comparisonSettings.saveSuccess', 'Settings saved successfully'))
      setIsDirty(false)
    } catch (err) {
      toast.error(
        err instanceof Error
          ? err.message
          : t('comparisonSettings.saveFailed', 'Failed to save settings'),
      )
    }
  }

  const formatCurrency = (value: number) => {
    return new Intl.NumberFormat('vi-VN', {
      style: 'currency',
      currency: 'VND',
      maximumFractionDigits: 0,
    }).format(value)
  }

  if (isLoading) {
    return (
      <Card>
        <CardHeader>
          <Skeleton className="h-6 w-48" />
          <Skeleton className="h-4 w-64" />
        </CardHeader>
        <CardContent className="space-y-6">
          <Skeleton className="h-10 w-full" />
          <Skeleton className="h-10 w-full" />
          <Skeleton className="h-10 w-full" />
        </CardContent>
      </Card>
    )
  }

  if (loadError) {
    return (
      <Alert variant="destructive">
        <AlertCircle className="h-4 w-4" />
        <AlertDescription>
          {t('comparisonSettings.loadError', 'Failed to load comparison settings')}
        </AlertDescription>
      </Alert>
    )
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>{t('comparisonSettings.title', 'Comparison Settings')}</CardTitle>
        <CardDescription>
          {t(
            'comparisonSettings.description',
            'Configure thresholds and display preferences for multi-period comparison reports',
          )}
        </CardDescription>
      </CardHeader>
      <CardContent>
        <form onSubmit={handleSubmit} className="space-y-6">
          {/* Variance Thresholds Section */}
          <div className="space-y-4">
            <h3 className="text-sm font-semibold">
              {t('comparisonSettings.thresholds', 'Variance Thresholds')}
            </h3>

            {/* Percentage Threshold */}
            <div className="space-y-2">
              <div className="flex items-center justify-between">
                <Label htmlFor="varianceThresholdPercent">
                  {t('comparisonSettings.percentThreshold', 'Percentage Threshold')}
                </Label>
                <span className="text-sm font-medium tabular-nums">
                  {formValues.varianceThresholdPercent}%
                </span>
              </div>
              <Slider
                id="varianceThresholdPercent"
                min={1}
                max={50}
                step={1}
                value={[formValues.varianceThresholdPercent]}
                onValueChange={([value]) => handleChange('varianceThresholdPercent', value)}
              />
              <p className="text-xs text-muted-foreground">
                {t(
                  'comparisonSettings.percentThresholdHelp',
                  'Lines with variance exceeding this percentage will be highlighted',
                )}
              </p>
            </div>

            {/* Absolute Threshold */}
            <div className="space-y-2">
              <Label htmlFor="varianceThresholdAbsolute">
                {t('comparisonSettings.absoluteThreshold', 'Absolute Threshold (VND)')}
              </Label>
              <Input
                id="varianceThresholdAbsolute"
                type="number"
                min={0}
                step={100000}
                value={formValues.varianceThresholdAbsolute}
                onChange={(e) =>
                  handleChange('varianceThresholdAbsolute', parseInt(e.target.value) || 0)
                }
              />
              <p className="text-xs text-muted-foreground">
                {t('comparisonSettings.absoluteThresholdHelp', 'Current value: {{value}}', {
                  value: formatCurrency(formValues.varianceThresholdAbsolute),
                })}
              </p>
            </div>
          </div>

          {/* Default Mode Section */}
          <div className="space-y-2">
            <Label htmlFor="defaultComparisonMode">
              {t('comparisonSettings.defaultMode', 'Default Comparison Mode')}
            </Label>
            <Select
              value={formValues.defaultComparisonMode}
              onValueChange={(value) =>
                handleChange('defaultComparisonMode', value as ComparisonMode)
              }
            >
              <SelectTrigger id="defaultComparisonMode">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {COMPARISON_MODES.map((mode) => (
                  <SelectItem key={mode.value} value={mode.value}>
                    {t(mode.labelKey, mode.value)}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>

          {/* Display Preferences Section */}
          <div className="space-y-4">
            <h3 className="text-sm font-semibold">
              {t('comparisonSettings.displayPreferences', 'Display Preferences')}
            </h3>

            <div className="flex items-center justify-between">
              <div className="space-y-0.5">
                <Label htmlFor="showSparklines">
                  {t('comparisonSettings.showSparklines', 'Show Sparklines')}
                </Label>
                <p className="text-xs text-muted-foreground">
                  {t(
                    'comparisonSettings.sparklinesHelp',
                    'Display trend charts in comparison table',
                  )}
                </p>
              </div>
              <Switch
                id="showSparklines"
                checked={formValues.showSparklines}
                onCheckedChange={(checked) => handleChange('showSparklines', checked)}
              />
            </div>

            <div className="flex items-center justify-between">
              <div className="space-y-0.5">
                <Label htmlFor="hideImmaterialDefault">
                  {t('comparisonSettings.hideImmaterial', 'Hide Immaterial by Default')}
                </Label>
                <p className="text-xs text-muted-foreground">
                  {t(
                    'comparisonSettings.hideImmaterialHelp',
                    'Automatically hide lines below threshold when opening reports',
                  )}
                </p>
              </div>
              <Switch
                id="hideImmaterialDefault"
                checked={formValues.hideImmaterialDefault}
                onCheckedChange={(checked) => handleChange('hideImmaterialDefault', checked)}
              />
            </div>
          </div>

          {/* Actions */}
          <div className="flex gap-2 pt-4">
            <Button type="submit" disabled={!isDirty || updateMutation.isPending}>
              <Save className="mr-2 h-4 w-4" />
              {updateMutation.isPending
                ? t('common.saving', 'Saving...')
                : t('common.save', 'Save')}
            </Button>
            <Button
              type="button"
              variant="outline"
              onClick={handleReset}
              disabled={!isDirty || updateMutation.isPending}
            >
              <RotateCcw className="mr-2 h-4 w-4" />
              {t('common.reset', 'Reset')}
            </Button>
          </div>
        </form>
      </CardContent>
    </Card>
  )
}

export default ComparisonSettingsForm
