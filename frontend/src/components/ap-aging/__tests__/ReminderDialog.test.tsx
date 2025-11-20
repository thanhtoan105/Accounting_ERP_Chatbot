import { describe, it, expect, beforeEach, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'

import { ReminderDialog } from '../ReminderDialog'
import * as apAgingService from '@/services/apAging'

const toast = vi.hoisted(() => ({
  success: vi.fn(),
  error: vi.fn(),
  warning: vi.fn(),
}))

vi.mock('sonner', () => ({
  toast,
}))

vi.mock('@/services/apAging')

describe('ReminderDialog', () => {
  const mockSendReminder = vi.mocked(apAgingService.sendReminder)
  const mockSendBatchReminders = vi.mocked(apAgingService.sendBatchReminders)

  beforeEach(() => {
    vi.clearAllMocks()
    toast.success.mockReset()
    toast.error.mockReset()
    toast.warning.mockReset()
  })

  it('validates at least one recipient is provided', async () => {
    const user = userEvent.setup()
    render(<ReminderDialog open onOpenChange={() => {}} />)

    await user.click(screen.getByRole('button', { name: /Send Reminder/i }))

    expect(toast.error).toHaveBeenCalledWith('Please add at least one recipient')
    expect(mockSendReminder).not.toHaveBeenCalled()
  })

  it('sends reminder for single supplier', async () => {
    mockSendReminder.mockResolvedValue({
      success: true,
      message: 'ok',
      sentTo: ['ap@example.com'],
      failedTo: [],
    })
    const user = userEvent.setup()
    const handleSuccess = vi.fn()
    const handleOpenChange = vi.fn()

    render(
      <ReminderDialog
        open
        onOpenChange={handleOpenChange}
        supplierId={42}
        onSuccess={handleSuccess}
      />,
    )

    await user.type(screen.getByPlaceholderText('email@example.com'), 'ap@example.com')
    await user.click(screen.getByRole('button', { name: /Send Reminder/i }))

    await waitFor(() =>
      expect(mockSendReminder).toHaveBeenCalledWith({
        supplierId: 42,
        billIds: undefined,
        recipients: ['ap@example.com'],
        message: undefined,
      }),
    )
    expect(handleSuccess).toHaveBeenCalled()
    expect(handleOpenChange).toHaveBeenCalledWith(false)
  })

  it('sends batch reminders when supplierIds provided', async () => {
    mockSendBatchReminders.mockResolvedValue({
      totalSuppliers: 2,
      successCount: 2,
      failedCount: 0,
      sentTo: ['a@example.com'],
      failedTo: [],
    })
    const user = userEvent.setup()

    render(
      <ReminderDialog open onOpenChange={() => {}} supplierIds={[10, 11]} onSuccess={() => {}} />,
    )

    await user.type(screen.getByPlaceholderText('email@example.com'), 'batch@example.com')
    await user.click(screen.getByRole('button', { name: /Send Reminder/i }))

    await waitFor(() =>
      expect(mockSendBatchReminders).toHaveBeenCalledWith({
        supplierIds: [10, 11],
        recipients: ['batch@example.com'],
        message: undefined,
      }),
    )
  })
})
