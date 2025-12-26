import { useCallback } from 'react'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Form } from '@/components/ui/form'
import { ScrollArea } from '@/components/ui/scroll-area'
import type { AccountingPeriod } from '@/types/accountingPeriod'
import { getVoucherById } from '@/services/voucher'

import { useVoucherFormState } from './useVoucherFormState'
import { VoucherFormHeader } from './VoucherFormHeader'
import { VoucherFormSummary } from './VoucherFormSummary'
import { VoucherFormGeneral } from './VoucherFormGeneral'
import { VoucherFormLines } from './VoucherFormLines'
import { VoucherFormAttachments } from './VoucherFormAttachments'
import { VoucherFormDialogs, VoucherFormHistory } from './VoucherFormDialogs'

export default function VoucherForm() {
  const state = useVoucherFormState()

  // Period change handler
  const handlePeriodChange = useCallback(
    (period: AccountingPeriod) => {
      state.setSelectedPeriod(period)
      if (period) {
        const periodStartDate = new Date(period.startDate)
        const periodEndDate = new Date(period.endDate)
        const currentDate = new Date(state.watchedValues.voucherDate || Date.now())

        if (currentDate < periodStartDate || currentDate > periodEndDate) {
          state.form.setValue('voucherDate', period.startDate)
        }
      }
    },
    [state.form, state.watchedValues.voucherDate, state.setSelectedPeriod],
  )

  // Revalidate handler
  const handleRevalidate = useCallback(() => {
    state.setValidationSummaryOpen(false)
    state.performRealTimeValidation(false)
  }, [state])

  // Attachment deleted handler
  const handleAttachmentDeleted = useCallback(() => {
    if (state.voucherId) {
      getVoucherById(state.voucherId)
        .then((voucher) => {
          state.setAttachmentCount(voucher.attachmentCount || 0)
        })
        .catch(() => {
          // Ignore errors
        })
    }
  }, [state.voucherId, state.setAttachmentCount])

  return (
    <div className="flex flex-col h-[calc(100vh-4rem)] bg-background">
      {/* Lock Alert */}
      {state.isLocked && state.draftLock ? (
        <Alert variant="destructive" className="animate-in slide-in-from-top duration-300 m-4 mb-0">
          <AlertTitle>Voucher is locked</AlertTitle>
          <AlertDescription className="flex flex-wrap items-center gap-2 text-sm">
            {state.draftLock.ownerName || 'Other user'} is editing this draft. Lock will expire in{' '}
            <span className="font-semibold font-mono">{state.lockCountdown ?? '—'}</span>.
            <Button
              size="sm"
              variant="outline"
              disabled={Boolean(state.lockCountdown) && state.lockCountdown !== '00:00'}
              onClick={state.claimLock}
            >
              Request edit permission
            </Button>
          </AlertDescription>
        </Alert>
      ) : null}

      <Form {...state.form}>
        <form
          onSubmit={state.form.handleSubmit(state.handleSave)}
          className="flex flex-col flex-1 min-h-0"
        >
          {/* Header Section - Fixed at top */}
          <div className="flex-none px-6 py-4 border-b bg-background z-10">
            <VoucherFormHeader
              isEditing={state.isEditing}
              editingVoucher={state.editingVoucher}
              voucherId={state.voucherId}
              attachmentCount={state.attachmentCount}
              onAttachmentClick={() => state.setAttachmentModalOpen(true)}
              onTemplateClick={() => state.setTemplateDialogOpen(true)}
              onValidateClick={() =>
                state.form.handleSubmit(() => state.performRealTimeValidation(false))()
              }
              onSaveClick={() => state.form.handleSubmit(state.handleSave)()}
              onPostClick={state.handlePost}
              onUnpostClick={() => state.setUnpostDialogOpen(true)}
              onReverseClick={() => state.setReverseDialogOpen(true)}
              formDisabled={state.formDisabled}
              loadingAccounts={state.loadingAccounts}
              saving={state.saving}
              posting={state.posting}
              unposting={state.unposting}
              reversing={state.reversing}
              canPost={state.canPost}
              canUnpost={state.canUnpost}
              canReverse={state.canReverse}
              autoSaveStatus={state.autoSaveStatus}
              lastSavedAt={state.lastSavedAt}
              autoSaveError={state.autoSaveError}
              validationSummary={state.validationSummary}
              onValidationSummaryClick={() => state.setValidationSummaryOpen(true)}
            />
          </div>

          {/* Main Content - Scrollable */}
          <ScrollArea className="flex-1">
            <div className="p-6 space-y-6 max-w-full">
              <VoucherFormGeneral
                form={state.form}
                formDisabled={state.formDisabled}
                selectedPeriod={state.selectedPeriod}
                onPeriodChange={handlePeriodChange}
                periodValidationError={state.periodValidationError}
                setPeriodValidationError={state.setPeriodValidationError}
                openPeriodRange={state.openPeriodRange}
                calendarMonth={state.calendarMonth}
                setCalendarMonth={state.setCalendarMonth}
                isDateDisabledSync={state.isDateDisabledSync}
                setDateValidationCache={state.setDateValidationCache}
              />

              <VoucherFormLines
                accounts={state.accounts}
                lines={state.lines}
                onLinesChange={state.setLines}
                validationMap={state.validationMap}
                lockedAccountIds={state.lockedAccountIds}
                readOnly={state.formDisabled}
                loading={state.validating}
                loadingAccounts={state.loadingAccounts}
                onUndo={state.undo}
                onRedo={state.redo}
                canUndo={state.canUndo}
                canRedo={state.canRedo}
              />

              <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
                <VoucherFormAttachments
                  voucherId={state.voucherId}
                  formDisabled={state.formDisabled}
                  setAttachmentCount={state.setAttachmentCount}
                />

                <VoucherFormHistory voucherId={state.voucherId} isEditing={state.isEditing} />
              </div>
            </div>
          </ScrollArea>

          {/* Footer Section - Fixed at bottom */}
          <div className="flex-none border-t bg-background z-10 shadow-[0_-4px_6px_-1px_rgba(0,0,0,0.05)]">
            <VoucherFormSummary
              totals={state.totals}
              attachmentCount={state.attachmentCount}
              validationSummary={state.validationSummary}
              editingVoucher={state.editingVoucher}
              isEditing={state.isEditing}
              onAttachmentClick={() => state.setAttachmentModalOpen(true)}
            />
          </div>
        </form>
      </Form>

      {/* Dialogs */}
      <VoucherFormDialogs
        validationSummaryOpen={state.validationSummaryOpen}
        setValidationSummaryOpen={state.setValidationSummaryOpen}
        validationMap={state.validationMap}
        validationSummary={state.validationSummary}
        onRevalidate={handleRevalidate}
        postingErrorModalOpen={state.postingErrorModalOpen}
        setPostingErrorModalOpen={state.setPostingErrorModalOpen}
        postingErrors={state.postingErrors}
        unpostDialogOpen={state.unpostDialogOpen}
        setUnpostDialogOpen={state.setUnpostDialogOpen}
        unpostReason={state.unpostReason}
        setUnpostReason={state.setUnpostReason}
        onUnpost={state.handleUnpost}
        unposting={state.unposting}
        reverseDialogOpen={state.reverseDialogOpen}
        setReverseDialogOpen={state.setReverseDialogOpen}
        reverseDescription={state.reverseDescription}
        setReverseDescription={state.setReverseDescription}
        reverseReason={state.reverseReason}
        setReverseReason={state.setReverseReason}
        onReverse={state.handleReverse}
        reversing={state.reversing}
        templateDialogOpen={state.templateDialogOpen}
        setTemplateDialogOpen={state.setTemplateDialogOpen}
        onTemplateApplied={state.handleTemplateApplied}
        applyingTemplate={state.applyingTemplate}
        voucherId={state.voucherId}
        attachmentModalOpen={state.attachmentModalOpen}
        setAttachmentModalOpen={state.setAttachmentModalOpen}
        editingVoucher={state.editingVoucher}
        formDisabled={state.formDisabled}
        onAttachmentDeleted={handleAttachmentDeleted}
      />
    </div>
  )
}
