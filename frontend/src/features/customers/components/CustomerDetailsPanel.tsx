'use client'

import { useEffect, useState } from 'react'
import { Loader2, Building2, Mail, Phone, MapPin, ToggleLeft, Receipt, Calendar } from 'lucide-react'
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
import {
  Field,
  FieldContent,
  FieldLabel,
} from '@/components/ui/field'
import {
  getCustomerById,
  getCustomerARSummary,
} from '@/features/customers/services/customer'
import type { Customer, CustomerARSummary } from '@/types/customer'
import { getStatusLabel } from '@/types/customer'
import { toast } from 'sonner'

interface CustomerDetailsPanelProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  customer: Customer | null
}

export default function CustomerDetailsPanel({
  open,
  onOpenChange,
  customer,
}: CustomerDetailsPanelProps) {
  const [customerData, setCustomerData] = useState<Customer | null>(null)
  const [arSummary, setArSummary] = useState<CustomerARSummary | null>(null)
  const [loading, setLoading] = useState(false)
  const [arLoading, setArLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (open && customer) {
      loadCustomerDetails()
      loadARSummary()
    } else {
      setCustomerData(null)
      setArSummary(null)
      setError(null)
    }
  }, [open, customer])

  const loadCustomerDetails = async () => {
    if (!customer) return
    try {
      setLoading(true)
      setError(null)
      const data = await getCustomerById(customer.id)
      setCustomerData(data)
    } catch (err: any) {
      const errorMessage = err?.error?.message || err?.message || 'Failed to load customer details'
      setError(errorMessage)
      toast.error('Failed to load customer details', { description: errorMessage })
    } finally {
      setLoading(false)
    }
  }

  const loadARSummary = async () => {
    if (!customer) return
    try {
      setArLoading(true)
      const summary = await getCustomerARSummary(customer.id)
      setArSummary(summary)
    } catch (err: any) {
      // AR summary may not be available if Epic 5 is not implemented
      // Silently fail and show placeholder
      console.warn('AR summary not available:', err)
      setArSummary(null)
    } finally {
      setArLoading(false)
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

  if (!customer) return null

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-2xl max-h-[90vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>Customer Details</DialogTitle>
          <DialogDescription>View customer information and AR summary</DialogDescription>
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
          ) : customerData ? (
            <>
              {/* Primary Information */}
              <div className="space-y-4">
                <div className="flex items-center gap-2 pb-2 border-b">
                  <Building2 className="h-5 w-5 text-primary" />
                  <h3 className="text-base font-semibold">Primary Information</h3>
                </div>
                <div className="space-y-4">
                  <Field className="gap-2">
                    <FieldLabel className="text-sm font-medium">Customer Code</FieldLabel>
                    <FieldContent>
                      <div className="px-3 py-2 rounded-md border bg-muted/30">
                        <span className="text-sm font-medium">{customerData.code}</span>
                      </div>
                    </FieldContent>
                  </Field>

                  <Field className="gap-2">
                    <FieldLabel className="text-sm font-medium">Customer Name</FieldLabel>
                    <FieldContent>
                      <div className="px-3 py-2 rounded-md border bg-muted/30">
                        <span className="text-sm font-medium">{customerData.name}</span>
                      </div>
                    </FieldContent>
                  </Field>

                  {customerData.taxCode && (
                    <Field className="gap-2">
                      <FieldLabel className="text-sm font-medium">Tax Code</FieldLabel>
                      <FieldContent>
                        <div className="px-3 py-2 rounded-md border bg-muted/30">
                          <span className="text-sm font-medium">{customerData.taxCode}</span>
                        </div>
                      </FieldContent>
                    </Field>
                  )}

                  <Field className="gap-2">
                    <FieldLabel className="text-sm font-medium">Status</FieldLabel>
                    <FieldContent>
                      <Badge
                        className={
                          customerData.active
                            ? 'rounded-full border-none bg-green-600/10 text-green-600 focus-visible:ring-green-600/20 focus-visible:outline-none dark:bg-green-400/10 dark:text-green-400 dark:focus-visible:ring-green-400/40 [a&]:hover:bg-green-600/5 dark:[a&]:hover:bg-green-400/5'
                            : 'bg-destructive/10 [a&]:hover:bg-destructive/5 focus-visible:ring-destructive/20 dark:focus-visible:ring-destructive/40 text-destructive rounded-full border-none focus-visible:outline-none'
                        }
                      >
                        {getStatusLabel(customerData.active)}
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
                  {customerData.email && (
                    <Field className="gap-2">
                      <FieldLabel className="text-sm font-medium">
                        <Mail className="inline h-4 w-4 mr-1" />
                        Email Address
                      </FieldLabel>
                      <FieldContent>
                        <div className="px-3 py-2 rounded-md border bg-muted/30">
                          <span className="text-sm font-medium">{customerData.email}</span>
                        </div>
                      </FieldContent>
                    </Field>
                  )}

                  {customerData.phone && (
                    <Field className="gap-2">
                      <FieldLabel className="text-sm font-medium">
                        <Phone className="inline h-4 w-4 mr-1" />
                        Phone Number
                      </FieldLabel>
                      <FieldContent>
                        <div className="px-3 py-2 rounded-md border bg-muted/30">
                          <span className="text-sm font-medium">{customerData.phone}</span>
                        </div>
                      </FieldContent>
                    </Field>
                  )}

                  {customerData.address && (
                    <Field className="gap-2">
                      <FieldLabel className="text-sm font-medium">
                        <MapPin className="inline h-4 w-4 mr-1" />
                        Address
                      </FieldLabel>
                      <FieldContent>
                        <div className="px-3 py-2 rounded-md border bg-muted/30 min-h-[3rem]">
                          <span className="text-sm font-medium">{customerData.address}</span>
                        </div>
                      </FieldContent>
                    </Field>
                  )}

                  {!customerData.email && !customerData.phone && !customerData.address && (
                    <p className="text-sm text-muted-foreground italic">No contact information available</p>
                  )}
                </div>
              </div>

              <Separator className="my-6" />

              {/* AR Summary */}
              <div className="space-y-4">
                <div className="flex items-center gap-2 pb-2 border-b">
                  <Receipt className="h-5 w-5 text-primary" />
                  <h3 className="text-base font-semibold">AR Summary</h3>
                </div>
                {arLoading ? (
                  <div className="space-y-2">
                    <Skeleton className="h-10 w-full" />
                    <Skeleton className="h-10 w-full" />
                    <Skeleton className="h-10 w-full" />
                  </div>
                ) : arSummary ? (
                  <div className="space-y-4">
                    <Field className="gap-2">
                      <FieldLabel className="text-sm font-medium">Open Invoices</FieldLabel>
                      <FieldContent>
                        <div className="px-3 py-2 rounded-md border bg-muted/30">
                          <span className="text-sm font-medium">{arSummary.openInvoices}</span>
                        </div>
                      </FieldContent>
                    </Field>

                    <Field className="gap-2">
                      <FieldLabel className="text-sm font-medium">Total Owed</FieldLabel>
                      <FieldContent>
                        <div className="px-3 py-2 rounded-md border bg-muted/30">
                          <span className="text-sm font-medium">{formatCurrency(arSummary.totalOwed)}</span>
                        </div>
                      </FieldContent>
                    </Field>

                    <Field className="gap-2">
                      <FieldLabel className="text-sm font-medium">Average Payment Days</FieldLabel>
                      <FieldContent>
                        <div className="px-3 py-2 rounded-md border bg-muted/30">
                          <span className="text-sm font-medium">{arSummary.averagePaymentDays} days</span>
                        </div>
                      </FieldContent>
                    </Field>
                  </div>
                ) : (
                  <Alert>
                    <AlertDescription>
                      AR summary is not available. This feature requires Epic 5 (AR Module) to be
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
                        <span className="text-sm font-medium">{formatDate(customerData.createdAt)}</span>
                      </div>
                    </FieldContent>
                  </Field>

                  <Field className="gap-2">
                    <FieldLabel className="text-sm font-medium">Updated At</FieldLabel>
                    <FieldContent>
                      <div className="px-3 py-2 rounded-md border bg-muted/30">
                        <span className="text-sm font-medium">{formatDate(customerData.updatedAt)}</span>
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

