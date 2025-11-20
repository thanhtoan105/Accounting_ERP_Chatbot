import type { ReactNode } from 'react'
import { useNavigate, useLocation } from 'react-router-dom'
import { useEffect } from 'react'
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

interface NavItem {
  path: string
  label: string
  requiredRoles?: Role[]
}

/**
 * Generate a color from a string (used for avatar background)
 */
// Removed MUI avatar helpers; Shadcn version renders simple user info in sidebar footer

const navItems: NavItem[] = [
  { path: '/', label: 'Dashboard' },
  {
    path: '/company',
    label: 'Company Settings',
    requiredRoles: ['admin', 'chief_accountant'],
  },
  {
    path: '/users',
    label: 'User Management',
    requiredRoles: ['admin', 'chief_accountant'],
  },
  {
    path: '/reports',
    label: 'Reports',
    requiredRoles: ['admin', 'accountant', 'chief_accountant', 'cfo'],
  },
  {
    path: '/vouchers',
    label: 'Vouchers',
    requiredRoles: ['admin', 'accountant', 'chief_accountant'],
  },
  {
    path: '/purchase-bills',
    label: 'Purchase Bills',
    requiredRoles: ['admin', 'accountant', 'chief_accountant', 'cfo'],
  },
  {
    path: '/customers',
    label: 'Customers',
    requiredRoles: ['admin', 'accountant', 'chief_accountant'],
  },
  {
    path: '/suppliers',
    label: 'Suppliers',
    requiredRoles: ['admin', 'accountant', 'chief_accountant'],
  },
  {
    path: '/bank-accounts',
    label: 'Bank Accounts',
    requiredRoles: ['admin', 'accountant', 'chief_accountant'],
  },
  {
    path: '/admin/audit-logs',
    label: 'Audit Logs',
    requiredRoles: ['admin', 'chief_accountant'],
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
  const navigate = useNavigate()
  const location = useLocation()
  const { isAuthenticated, loading, logout, user } = useAuth()
  const { hasAnyRole, getRoleDisplayName } = useRole()
  const { company, currentPeriod } = useCompany()
  const { resolvedTheme, toggleTheme } = useTheme()

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
      return <div className="min-h-[100dvh] grid place-items-center">Loading...</div>
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
  // Category items for sidebar menu
  const categoryItems: Array<{
    title: string
    url: string
    requiredRoles: Role[]
  }> = [
    {
      title: 'Chart of Accounts',
      url: '/chart-of-accounts',
      requiredRoles: ['admin', 'chief_accountant'],
    },
    {
      title: 'Voucher Templates',
      url: '/voucher-templates',
      requiredRoles: ['admin', 'chief_accountant', 'cfo'],
    },
    {
      title: 'Voucher Types',
      url: '/voucher-types',
      requiredRoles: ['admin', 'chief_accountant'],
    },
  ]

  // Filter category items based on role
  const visibleCategoryItems = categoryItems.filter((item) => {
    if (!item.requiredRoles || item.requiredRoles.length === 0) {
      return true
    }
    return hasAnyRole(item.requiredRoles)
  })

  // Build sidebar items with Purchase menu structure
  const sidebarItems = visibleNavItems
    .filter((i) => i.path !== '/company')
    .map((i) => {
      // Check if this is Purchase Bills - create Purchase menu (without Category)
      if (i.path === '/purchase-bills') {
        return {
          title: 'Purchase',
          url: i.path,
          items: [{ title: 'Purchase Bills', url: '/purchase-bills' }],
        }
      }
      return { title: i.label, url: i.path }
    })

  // Add Category as a separate menu item if there are visible category items
  if (visibleCategoryItems.length > 0) {
    sidebarItems.push({
      title: 'Category',
      url: '#', // Not a clickable link, just a submenu trigger
      items: visibleCategoryItems.map((cat) => ({
        title: cat.title,
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
                <BreadcrumbLink href="#">Accounting</BreadcrumbLink>
              </BreadcrumbItem>
              <BreadcrumbSeparator className="hidden md:block" />
              <BreadcrumbItem>
                <BreadcrumbPage>
                  {visibleNavItems.find((i) => i.path === location.pathname)?.label || 'Dashboard'}
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
                  <span className="text-muted-foreground text-xs">Period: {currentPeriod}</span>
                )}
              </div>
            )}
            {/* Role badge */}
            {user?.role && (
              <Badge variant="secondary" className="hidden sm:inline-flex">
                {getRoleDisplayName()}
              </Badge>
            )}
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
    </SidebarProvider>
  )
}
