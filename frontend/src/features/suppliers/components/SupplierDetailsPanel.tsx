'use client'

import { useEffect, useState } from 'react'
import {
  Loader2,
  Building2,
  Mail,
  Phone,
  MapPin,
  ToggleLeft,
  Receipt,
  Calendar,
} from 'lucide-react'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Badge } from '@/components/ui/badge'
import { Skeleton } from '@/components/ui/skeleton'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Separator } from '@/components/ui/separator'
import { Field, FieldContent, FieldLabel } from '@/components/ui/field'
import { getSupplierById, getSupplierAPSummary } from '@/features/suppliers/services/supplier'
import type { Supplier, SupplierAPSummary } from '@/types/supplier'
import { getStatusLabel } from '@/types/supplier'
import { toast } from 'sonner'

interface SupplierDetailsPanelProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  supplier: Supplier | null
}

export default function SupplierDetailsPanel({
  open,
  onOpenChange,
  supplier,
}: SupplierDetailsPanelProps) {
  const [supplierData, setSupplierData] = useState<Supplier | null>(null)
  const [apSummary, setApSummary] = useState<SupplierAPSummary | null>(null)
  const [loading, setLoading] = useState(false)
  const [apLoading, setApLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (open && supplier) {
      loadSupplierDetails()
      loadAPSummary()
    } else {
      setSupplierData(null)
      setApSummary(null)
      setError(null)
    }
  }, [open, supplier])

  const loadSupplierDetails = async () => {
    if (!supplier) return
    try {
      setLoading(true)
      setError(null)
      const data = await getSupplierById(supplier.id)
      setSupplierData(data)
    } catch (err: any) {
      const errorMessage = err?.error?.message || err?.message || 'Failed to load supplier details'
      setError(errorMessage)
      toast.error('Failed to load supplier details', { description: errorMessage })
    } finally {
      setLoading(false)
    }
  }

  const loadAPSummary = async () => {
    if (!supplier) return
    try {
      setApLoading(true)
      const summary = await getSupplierAPSummary(supplier.id)
      setApSummary(summary)
    } catch (err: any) {
      // AP summary may not be available if Epic 4 is not implemented
      // Silently fail and show placeholder
      console.warn('AP summary not available:', err)
      setApSummary(null)
    } finally {
      setApLoading(false)
    }
  }

  const formatCurrency = (amount: number) => {
    return new Intl.NumberFormat('vi-VN', {
      style: 'currency',
      currency: 'VND',
    }).format(amount)
  }

  const formatDate = (dateString: string) => {
    return new Date(dateString).toLocaleDateString('vi-VN', {
      year: 'numeric',
      month: 'long',
      day: 'numeric',
    })
  }

  if (!supplier) return null

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-2xl max-h-[90vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>Supplier Details</DialogTitle>
          <DialogDescription>View supplier information and AP summary</DialogDescription>
        </DialogHeader>

        <div className="space-y-6 py-4">
          {error && (
            <Alert variant="destructive">
              <AlertDescription>{error}</AlertDescription>
            </Alert>
          )}

          {loading ? (
            <div className="space-y-4">
              <Skeleton className="h-8 w-full" />
              <Skeleton className="h-8 w-full" />
              <Skeleton className="h-8 w-full" />
            </div>
          ) : supplierData ? (
            <>
              {/* Primary Information */}
              <div className="space-y-4">
                <div className="flex items-center gap-2 pb-2 border-b">
                  <Building2 className="h-5 w-5 text-primary" />
                  <h3 className="text-base font-semibold">Primary Information</h3>
                </div>
                <div className="space-y-4">
                  <Field className="gap-2">
                    <FieldLabel className="text-sm font-medium">Supplier Code</FieldLabel>
                    <FieldContent>
                      <div className="px-3 py-2 rounded-md border bg-muted/30">
                        <span className="text-sm font-medium">{supplierData.code}</span>
                      </div>
                    </FieldContent>
                  </Field>

                  <Field className="gap-2">
                    <FieldLabel className="text-sm font-medium">Supplier Name</FieldLabel>
                    <FieldContent>
                      <div className="px-3 py-2 rounded-md border bg-muted/30">
                        <span className="text-sm font-medium">{supplierData.name}</span>
                      </div>
                    </FieldContent>
                  </Field>

                  {supplierData.taxCode && (
                    <Field className="gap-2">
                      <FieldLabel className="text-sm font-medium">Tax Code</FieldLabel>
                      <FieldContent>
                        <div className="px-3 py-2 rounded-md border bg-muted/30">
                          <span className="text-sm font-medium">{supplierData.taxCode}</span>
                        </div>
                      </FieldContent>
                    </Field>
                  )}

                  <Field className="gap-2">
                    <FieldLabel className="text-sm font-medium">Status</FieldLabel>
                    <FieldContent>
                      <Badge
                        className={
                          supplierData.active
                            ? 'rounded-full border-none bg-green-600/10 text-green-600 focus-visible:ring-green-600/20 focus-visible:outline-none dark:bg-green-400/10 dark:text-green-400 dark:focus-visible:ring-green-400/40 [a&]:hover:bg-green-600/5 dark:[a&]:hover:bg-green-400/5'
                            : 'bg-destructive/10 [a&]:hover:bg-destructive/5 focus-visible:ring-destructive/20 dark:focus-visible:ring-destructive/40 text-destructive rounded-full border-none focus-visible:outline-none'
                        }
                      >
                        {getStatusLabel(supplierData.active)}
                      </Badge>
                    </FieldContent>
                  </Field>
                </div>
              </div>

              <Separator className="my-6" />

              {/* Contact Details */}
              <div className="space-y-4">
                <div className="flex items-center gap-2 pb-2 border-b">
                  <Mail className="h-5 w-5 text-primary" />
                  <h3 className="text-base font-semibold">Contact Details</h3>
                </div>
                <div className="space-y-4">
                  {supplierData.email && (
                    <Field className="gap-2">
                      <FieldLabel className="text-sm font-medium">
                        <Mail className="inline h-4 w-4 mr-1" />
                        Email Address
                      </FieldLabel>
                      <FieldContent>
                        <div className="px-3 py-2 rounded-md border bg-muted/30">
                          <span className="text-sm font-medium">{supplierData.email}</span>
                        </div>
                      </FieldContent>
                    </Field>
                  )}

                  {supplierData.phone && (
                    <Field className="gap-2">
                      <FieldLabel className="text-sm font-medium">
                        <Phone className="inline h-4 w-4 mr-1" />
                        Phone Number
                      </FieldLabel>
                      <FieldContent>
                        <div className="px-3 py-2 rounded-md border bg-muted/30">
                          <span className="text-sm font-medium">{supplierData.phone}</span>
                        </div>
                      </FieldContent>
                    </Field>
                  )}

                  {supplierData.address && (
                    <Field className="gap-2">
                      <FieldLabel className="text-sm font-medium">
                        <MapPin className="inline h-4 w-4 mr-1" />
                        Address
                      </FieldLabel>
                      <FieldContent>
                        <div className="px-3 py-2 rounded-md border bg-muted/30 min-h-[3rem]">
                          <span className="text-sm font-medium">{supplierData.address}</span>
                        </div>
                      </FieldContent>
                    </Field>
                  )}

                  {!supplierData.email && !supplierData.phone && !supplierData.address && (
                    <p className="text-sm text-muted-foreground italic">
                      No contact information available
                    </p>
                  )}
                </div>
              </div>

              <Separator className="my-6" />

              {/* AP Summary */}
              <div className="space-y-4">
                <div className="flex items-center gap-2 pb-2 border-b">
                  <Receipt className="h-5 w-5 text-primary" />
                  <h3 className="text-base font-semibold">AP Summary</h3>
                </div>
                {apLoading ? (
                  <div className="space-y-2">
                    <Skeleton className="h-10 w-full" />
                    <Skeleton className="h-10 w-full" />
                    <Skeleton className="h-10 w-full" />
                  </div>
                ) : apSummary ? (
                  <div className="space-y-4">
                    <Field className="gap-2">
                      <FieldLabel className="text-sm font-medium">Open Bills</FieldLabel>
                      <FieldContent>
                        <div className="px-3 py-2 rounded-md border bg-muted/30">
                          <span className="text-sm font-medium">{apSummary.openBills}</span>
                        </div>
                      </FieldContent>
                    </Field>

                    <Field className="gap-2">
                      <FieldLabel className="text-sm font-medium">Total Owed</FieldLabel>
                      <FieldContent>
                        <div className="px-3 py-2 rounded-md border bg-muted/30">
                          <span className="text-sm font-medium">
                            {formatCurrency(apSummary.totalOwed)}
                          </span>
                        </div>
                      </FieldContent>
                    </Field>

                    <Field className="gap-2">
                      <FieldLabel className="text-sm font-medium">Average Payment Days</FieldLabel>
                      <FieldContent>
                        <div className="px-3 py-2 rounded-md border bg-muted/30">
                          <span className="text-sm font-medium">
                            {apSummary.averagePaymentDays} days
                          </span>
                        </div>
                      </FieldContent>
                    </Field>
                  </div>
                ) : (
                  <Alert>
                    <AlertDescription>
                      AP summary is not available. This feature requires Epic 4 (AP Module) to be
                      implemented.
                    </AlertDescription>
                  </Alert>
                )}
              </div>

              <Separator className="my-6" />

              {/* Metadata */}
              <div className="space-y-4">
                <div className="flex items-center gap-2 pb-2 border-b">
                  <Calendar className="h-5 w-5 text-primary" />
                  <h3 className="text-base font-semibold">Metadata</h3>
                </div>
                <div className="space-y-4">
                  <Field className="gap-2">
                    <FieldLabel className="text-sm font-medium">Created At</FieldLabel>
                    <FieldContent>
                      <div className="px-3 py-2 rounded-md border bg-muted/30">
                        <span className="text-sm font-medium">
                          {formatDate(supplierData.createdAt)}
                        </span>
                      </div>
                    </FieldContent>
                  </Field>

                  <Field className="gap-2">
                    <FieldLabel className="text-sm font-medium">Updated At</FieldLabel>
                    <FieldContent>
                      <div className="px-3 py-2 rounded-md border bg-muted/30">
                        <span className="text-sm font-medium">
                          {formatDate(supplierData.updatedAt)}
                        </span>
                      </div>
                    </FieldContent>
                  </Field>
                </div>
              </div>
            </>
          ) : null}
        </div>
      </DialogContent>
    </Dialog>
  )
}
