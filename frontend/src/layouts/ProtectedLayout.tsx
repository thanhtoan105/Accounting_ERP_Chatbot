import type { ReactNode } from 'react'
import { useNavigate, useLocation } from 'react-router-dom'
import { useEffect, useState } from 'react'
import { useAuth } from '../hooks/useAuth'
import { useRole } from '../hooks/useRole'
import { useCompany } from '../hooks/useCompany'
import { useTheme } from '../hooks/useTheme'
import type { Role } from '../utils/roles'
import { getAccessToken } from '../utils/axios'
import { SidebarInset, SidebarProvider, SidebarTrigger } from '@/components/ui/sidebar'
import { AppSidebar } from '@/components/app'
import { Separator } from '@/components/ui/separator'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Moon, Sun } from 'lucide-react'
import {
  Breadcrumb,
  BreadcrumbItem,
  BreadcrumbLink,
  BreadcrumbList,
  BreadcrumbPage,
  BreadcrumbSeparator,
} from '@/components/ui/breadcrumb'
import { ChatbotWidget, ChatbotErrorBoundary } from '@/features/chatbot'
import { LanguageSwitcher } from '@/components/LanguageSwitcher'
import { useTranslation } from 'react-i18next'

interface NavItem {
  path: string
  label: string
  requiredRoles?: Role[]
}

/**
 * Generate a color from a string (used for avatar background)
 */
// Removed MUI avatar helpers; Shadcn version renders simple user info in sidebar footer

// Navigation items with translation keys (use t(item.labelKey) to get translated label)
const navItems: (NavItem & { labelKey: string })[] = [
  { path: '/', label: 'Dashboard', labelKey: 'nav.dashboard' },
  {
    path: '/company',
    label: 'Company Settings',
    labelKey: 'nav.company',
    requiredRoles: ['admin', 'chief_accountant'],
  },
  {
    path: '/users',
    label: 'User Management',
    labelKey: 'nav.users',
    requiredRoles: ['admin', 'chief_accountant'],
  },
  {
    path: '/analytics',
    label: 'Analytics',
    labelKey: 'nav.analytics',
    requiredRoles: ['admin', 'cfo', 'chief_accountant'],
  },
  {
    path: '/reports',
    label: 'Reports',
    labelKey: 'nav.reports',
    requiredRoles: ['admin', 'accountant', 'chief_accountant', 'cfo'],
  },
  {
    path: '/accounting/reports/comparison',
    label: 'Multi-Period Comparison',
    labelKey: 'nav.multiPeriodComparison',
    requiredRoles: ['admin', 'chief_accountant', 'cfo'],
  },
  {
    path: '/vouchers',
    label: 'Vouchers',
    labelKey: 'nav.vouchers',
    requiredRoles: ['admin', 'accountant', 'chief_accountant'],
  },
  {
    path: '/purchase-bills',
    label: 'Purchase Bills',
    labelKey: 'nav.purchaseBills',
    requiredRoles: ['admin', 'accountant', 'chief_accountant', 'cfo'],
  },
  {
    path: '/sales-invoices',
    label: 'Sales Invoices',
    labelKey: 'nav.salesInvoices',
    requiredRoles: ['admin', 'accountant', 'chief_accountant', 'cfo'],
  },
  {
    path: '/accounting/receipts',
    label: 'Receipts',
    labelKey: 'nav.receipts',
    requiredRoles: ['admin', 'accountant', 'chief_accountant', 'cfo'],
  },
  {
    path: '/payments',
    label: 'Payments',
    labelKey: 'nav.payments',
    requiredRoles: ['admin', 'accountant', 'chief_accountant', 'cfo'],
  },
  {
    path: '/customers',
    label: 'Customers',
    labelKey: 'nav.customers',
    requiredRoles: ['admin', 'accountant', 'chief_accountant'],
  },
  {
    path: '/suppliers',
    label: 'Suppliers',
    labelKey: 'nav.suppliers',
    requiredRoles: ['admin', 'accountant', 'chief_accountant'],
  },
  {
    path: '/bank-accounts',
    label: 'Cash/Bank Accounts',
    labelKey: 'nav.cashBankAccounts',
    requiredRoles: ['admin', 'accountant', 'chief_accountant'],
  },
  {
    path: '/admin/audit-logs',
    label: 'Audit Logs',
    labelKey: 'nav.auditLogs',
    requiredRoles: ['admin', 'chief_accountant'],
  },
  {
    path: '/admin/tenants',
    label: 'Tenant Management',
    labelKey: 'nav.tenantManagement',
    requiredRoles: ['super_admin'],
  },
]

interface ProtectedLayoutProps {
  children: ReactNode
}

/**
 * Protected layout with navigation sidebar.
 * Enforces authentication and hides menu items based on user role.
 */
export default function ProtectedLayout({ children }: ProtectedLayoutProps) {
  const { t, i18n } = useTranslation()
  const [, setLanguageKey] = useState(i18n.language)
  const navigate = useNavigate()
  const location = useLocation()
  const { isAuthenticated, loading, logout, user } = useAuth()
  const { hasAnyRole, getRoleDisplayName } = useRole()
  const { company, currentPeriod } = useCompany()
  const { resolvedTheme, toggleTheme } = useTheme()

  // Listen for language changes and trigger re-render
  useEffect(() => {
    const handleLanguageChange = (lng: string) => {
      setLanguageKey(lng)
    }

    i18n.on('languageChanged', handleLanguageChange)

    return () => {
      i18n.off('languageChanged', handleLanguageChange)
    }
  }, [i18n])

  const handleLogout = async () => {
    try {
      await logout()
      navigate('/login', { replace: true })
    } catch {
      // Even if logout fails, clear local state and redirect
      navigate('/login', { replace: true })
    }
  }

  // Redirect to login if not authenticated
  useEffect(() => {
    if (!loading) {
      const token = getAccessToken()
      // Check token directly - more reliable than useAuth state which might not be updated yet
      if (!token) {
        navigate('/login', { replace: true })
      } else if (!isAuthenticated) {
        // Token exists but useAuth state says not authenticated
        // This can happen right after login before state updates
        // Give it a moment, but if still not authenticated after brief delay, check token again
        const timeout = setTimeout(() => {
          const currentToken = getAccessToken()
          if (!currentToken) {
            navigate('/login', { replace: true })
          }
        }, 100)
        return () => clearTimeout(timeout)
      }
    }
  }, [isAuthenticated, loading, navigate])

  // Show loading state while checking authentication
  if (loading) {
    const token = getAccessToken()
    if (!token) {
      return <div className="min-h-[100dvh] grid place-items-center">{t('app.loading')}</div>
    }
  }

  // Check token directly for more reliable auth check
  const token = getAccessToken()
  if (!token) {
    // No token at all - definitely not authenticated
    return null
  }

  // Filter nav items based on role
  const visibleNavItems = navItems.filter((item) => {
    if (!item.requiredRoles || item.requiredRoles.length === 0) {
      return true // No role requirement, always visible
    }
    return hasAnyRole(item.requiredRoles)
  })

  // Move Company Settings from main nav to user submenu in the sidebar footer
  // Category items for sidebar menu (with translation keys)
  const categoryItems: Array<{
    titleKey: string
    url: string
    requiredRoles: Role[]
  }> = [
    {
      titleKey: 'nav.chartOfAccounts',
      url: '/chart-of-accounts',
      requiredRoles: ['admin', 'chief_accountant'],
    },
    {
      titleKey: 'nav.voucherTemplates',
      url: '/voucher-templates',
      requiredRoles: ['admin', 'chief_accountant', 'cfo'],
    },
    {
      titleKey: 'nav.voucherTypes',
      url: '/voucher-types',
      requiredRoles: ['admin', 'chief_accountant'],
    },
    {
      titleKey: 'nav.reportMappings',
      url: '/accounting/report-mappings',
      requiredRoles: ['admin'],
    },
  ]

  // Reports items for sidebar menu (with translation keys)
  const reportsItems: Array<{
    titleKey: string
    url: string
    requiredRoles: Role[]
  }> = [
    {
      titleKey: 'nav.apAging',
      url: '/ap-aging',
      requiredRoles: ['admin', 'accountant', 'chief_accountant', 'cfo'],
    },
    {
      titleKey: 'nav.arAging',
      url: '/accounting/ar-aging',
      requiredRoles: ['admin', 'accountant', 'chief_accountant', 'cfo'],
    },
    {
      titleKey: 'nav.inputVat',
      url: '/vat/reports/input',
      requiredRoles: ['admin', 'accountant', 'chief_accountant', 'cfo'],
    },
    {
      titleKey: 'nav.outputVat',
      url: '/vat/reports/output',
      requiredRoles: ['admin', 'accountant', 'chief_accountant', 'cfo'],
    },
    {
      titleKey: 'nav.trialBalance',
      url: '/accounting/trial-balance',
      requiredRoles: ['admin', 'chief_accountant', 'cfo'],
    },
    {
      titleKey: 'nav.statutoryReports',
      url: '/accounting/statutory-reports',
      requiredRoles: ['admin', 'chief_accountant', 'cfo'],
    },
    {
      titleKey: 'nav.cashBook',
      url: '/accounting/cash-book',
      requiredRoles: ['admin', 'accountant', 'chief_accountant', 'cfo'],
    },
    {
      titleKey: 'nav.cashBookSummary',
      url: '/accounting/cash-book/summary',
      requiredRoles: ['admin', 'accountant', 'chief_accountant', 'cfo'],
    },
    {
      titleKey: 'nav.bankReconciliation',
      url: '/accounting/bank-reconciliation',
      requiredRoles: ['admin', 'accountant', 'chief_accountant', 'cfo'],
    },
    {
      titleKey: 'nav.reportSchedules',
      url: '/reports/schedules',
      requiredRoles: ['admin', 'chief_accountant', 'cfo'],
    },
    {
      titleKey: 'nav.reportCenter',
      url: '/reports/center',
      requiredRoles: ['admin', 'accountant', 'chief_accountant', 'cfo'],
    },
    {
      titleKey: 'nav.multiPeriodComparison',
      url: '/accounting/reports/comparison',
      requiredRoles: ['admin', 'chief_accountant', 'cfo'],
    },
  ]

  // Filter category items based on role
  const visibleCategoryItems = categoryItems.filter((item) => {
    if (!item.requiredRoles || item.requiredRoles.length === 0) {
      return true
    }
    return hasAnyRole(item.requiredRoles)
  })

  // Filter reports items based on role
  const visibleReportsItems = reportsItems.filter((item) => {
    if (!item.requiredRoles || item.requiredRoles.length === 0) {
      return true
    }
    return hasAnyRole(item.requiredRoles)
  })

  // Build sidebar items with Purchase and Sales menu structure (using translations)
  const sidebarItems = visibleNavItems
    .filter(
      (i) =>
        i.path !== '/company' &&
        i.path !== '/reports' &&
        i.path !== '/accounting/receipts' &&
        i.path !== '/payments',
    )
    .map((i) => {
      // Check if this is Purchase Bills - create Purchase menu with Payments
      if (i.path === '/purchase-bills') {
        const purchaseMenuItems = [{ title: t('nav.purchaseBills'), url: '/purchase-bills' }]
        // Add Payments to Purchase menu
        const paymentsItem = visibleNavItems.find((item) => item.path === '/payments')
        if (paymentsItem) {
          purchaseMenuItems.push({ title: t('nav.payments'), url: '/payments' })
        }
        return {
          title: t('nav.purchase'),
          url: i.path,
          items: purchaseMenuItems,
        }
      }
      // Check if this is Sales Invoices - create Sales menu
      if (i.path === '/sales-invoices') {
        // Find receipts item to include in Sales menu
        const receiptsItem = visibleNavItems.find((item) => item.path === '/accounting/receipts')
        const salesMenuItems = [{ title: t('nav.salesInvoices'), url: '/sales-invoices' }]
        if (receiptsItem) {
          salesMenuItems.push({ title: t('nav.receipts'), url: '/accounting/receipts' })
        }
        return {
          title: t('nav.sales'),
          url: i.path,
          items: salesMenuItems,
        }
      }
      return { title: t(i.labelKey), url: i.path }
    })

  // Add Reports as a menu with sub-items if there are visible reports items
  if (visibleReportsItems.length > 0) {
    sidebarItems.push({
      title: t('nav.reports'),
      url: '#', // Not a clickable link, just a submenu trigger
      items: visibleReportsItems.map((report) => ({
        title: t(report.titleKey),
        url: report.url,
      })),
    })
  }

  // Add Category as a separate menu item if there are visible category items
  if (visibleCategoryItems.length > 0) {
    sidebarItems.push({
      title: t('nav.category'),
      url: '#', // Not a clickable link, just a submenu trigger
      items: visibleCategoryItems.map((cat) => ({
        title: t(cat.titleKey),
        url: cat.url,
      })),
    })
  }

  return (
    <SidebarProvider>
      <AppSidebar
        items={sidebarItems}
        user={{ name: user?.fullName || 'User', email: user?.email || '' }}
        onLogout={handleLogout}
      />
      <SidebarInset>
        <header className="flex h-16 shrink-0 items-center gap-2 border-b px-4">
          <SidebarTrigger className="-ml-1" />
          <Separator orientation="vertical" className="mr-2 data-[orientation=vertical]:h-4" />
          <Breadcrumb className="flex-1">
            <BreadcrumbList>
              <BreadcrumbItem className="hidden md:block">
                <BreadcrumbLink href="#">{t('app.name')}</BreadcrumbLink>
              </BreadcrumbItem>
              <BreadcrumbSeparator className="hidden md:block" />
              <BreadcrumbItem>
                <BreadcrumbPage>
                  {(() => {
                    const currentNav = visibleNavItems.find((i) => i.path === location.pathname)
                    return currentNav ? t(currentNav.labelKey) : t('nav.dashboard')
                  })()}
                </BreadcrumbPage>
              </BreadcrumbItem>
            </BreadcrumbList>
          </Breadcrumb>
          <div className="ml-auto flex items-center gap-3">
            {/* Company name and period */}
            {(company?.name || currentPeriod) && (
              <div className="hidden md:flex flex-col items-end text-sm">
                {company?.name && (
                  <span className="font-medium text-foreground">{company.name}</span>
                )}
                {currentPeriod && (
                  <span className="text-muted-foreground text-xs">
                    {t('common.period')}: {currentPeriod}
                  </span>
                )}
              </div>
            )}
            {/* Role badge */}
            {user?.role && (
              <Badge variant="secondary" className="hidden sm:inline-flex">
                {getRoleDisplayName()}
              </Badge>
            )}
            {/* Language switcher */}
            <LanguageSwitcher />
            {/* Theme switcher */}
            <Button
              variant="ghost"
              size="icon"
              onClick={toggleTheme}
              aria-label={resolvedTheme === 'dark' ? 'Switch to light mode' : 'Switch to dark mode'}
            >
              {resolvedTheme === 'dark' ? <Sun className="size-4" /> : <Moon className="size-4" />}
            </Button>
          </div>
        </header>
        <div className="p-4">{children}</div>
      </SidebarInset>

      {/* Chatbot Widget - Floating on all pages with error boundary */}
      {import.meta.env.VITE_CHATBOT_ENABLED !== 'false' && (
        <ChatbotErrorBoundary
          onError={(error, errorInfo) => {
            console.error('Chatbot crashed:', error, errorInfo)
            // TODO: Send to error tracking service (Sentry, etc.)
          }}
        >
          <ChatbotWidget />
        </ChatbotErrorBoundary>
      )}
    </SidebarProvider>
  )
}
