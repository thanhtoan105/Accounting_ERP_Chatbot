import type { ReactNode } from 'react'
import { useNavigate, useLocation } from 'react-router-dom'
import { useEffect } from 'react'
import {
  Avatar,
  Box,
  Button,
  Divider,
  Drawer,
  List,
  ListItem,
  ListItemButton,
  ListItemIcon,
  ListItemText,
  Toolbar,
  Typography,
} from '@mui/material'
import DashboardIcon from '@mui/icons-material/Dashboard'
import BusinessIcon from '@mui/icons-material/Business'
import PeopleIcon from '@mui/icons-material/People'
import AssessmentIcon from '@mui/icons-material/Assessment'
import DescriptionIcon from '@mui/icons-material/Description'
import AccountTreeIcon from '@mui/icons-material/AccountTree'
import LogoutIcon from '@mui/icons-material/Logout'
import { useAuth } from '../hooks/useAuth'
import { useRole } from '../hooks/useRole'
import type { Role } from '../utils/roles'
import CompanySwitcher from '../components/common/CompanySwitcher'
import { getAccessToken } from '../utils/axios'

const DRAWER_WIDTH = 240

interface NavItem {
  path: string
  label: string
  icon: React.ReactNode
  requiredRoles?: Role[]
}

/**
 * Generate a color from a string (used for avatar background)
 */
function stringToColor(string: string): string {
  let hash = 0
  let i

  for (i = 0; i < string.length; i += 1) {
    hash = string.charCodeAt(i) + ((hash << 5) - hash)
  }

  let color = '#'

  for (i = 0; i < 3; i += 1) {
    const value = (hash >> (i * 8)) & 0xff
    color += `00${value.toString(16)}`.slice(-2)
  }

  return color
}

/**
 * Generate avatar props from a name (color and initials)
 */
function stringAvatar(name: string) {
  const parts = name.trim().split(' ')
  let initials = ''

  if (parts.length >= 2) {
    // Use first letter of first name and first letter of last name
    initials = `${parts[0][0]}${parts[parts.length - 1][0]}`.toUpperCase()
  } else if (parts.length === 1 && parts[0].length > 0) {
    // Use first two letters of single name
    initials = parts[0].substring(0, 2).toUpperCase()
  } else {
    // Fallback: use first letter
    initials = name.length > 0 ? name[0].toUpperCase() : 'U'
  }

  return {
    sx: {
      bgcolor: stringToColor(name),
    },
    children: initials,
  }
}

const navItems: NavItem[] = [
  {
    path: '/',
    label: 'Dashboard',
    icon: <DashboardIcon />,
  },
  {
    path: '/company',
    label: 'Company Settings',
    icon: <BusinessIcon />,
    requiredRoles: ['admin', 'chief_accountant'],
  },
  {
    path: '/users',
    label: 'User Management',
    icon: <PeopleIcon />,
    requiredRoles: ['admin', 'chief_accountant'],
  },
  {
    path: '/chart-of-accounts',
    label: 'Chart of Accounts',
    icon: <AccountTreeIcon />,
    // All authenticated users can view COA
  },
  {
    path: '/reports',
    label: 'Reports',
    icon: <AssessmentIcon />,
    requiredRoles: ['admin', 'accountant', 'chief_accountant', 'cfo'],
  },
  {
    path: '/vouchers',
    label: 'Vouchers',
    icon: <DescriptionIcon />,
    requiredRoles: ['admin', 'accountant', 'chief_accountant'],
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
  const { hasAnyRole } = useRole()

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
    if (token) {
      // Token exists, allow rendering (auth state might just be updating)
      // This prevents redirect loop right after login
    } else {
      return (
        <Box
          sx={{
            display: 'flex',
            justifyContent: 'center',
            alignItems: 'center',
            minHeight: '100vh',
          }}
        >
          <Typography>Loading...</Typography>
        </Box>
      )
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

  // Get user display name for avatar
  const userDisplayName = user?.fullName || user?.email || 'User'

  return (
    <Box sx={{ display: 'flex' }}>
      {/* Sidebar Navigation */}
      <Drawer
        variant="permanent"
        sx={{
          width: DRAWER_WIDTH,
          flexShrink: 0,
          '& .MuiDrawer-paper': {
            width: DRAWER_WIDTH,
            boxSizing: 'border-box',
            display: 'flex',
            flexDirection: 'column',
          },
        }}
      >
        <Toolbar>
          <Typography variant="h6" noWrap component="div">
            Accounting
          </Typography>
        </Toolbar>
        <Divider />
        <List sx={{ flexGrow: 1, overflow: 'auto' }}>
          {visibleNavItems.map((item) => (
            <ListItem key={item.path} disablePadding>
              <ListItemButton
                selected={location.pathname === item.path}
                onClick={() => navigate(item.path)}
              >
                <ListItemIcon>{item.icon}</ListItemIcon>
                <ListItemText primary={item.label} />
              </ListItemButton>
            </ListItem>
          ))}
        </List>
        <Divider />
        {/* User info and logout at bottom of sidebar */}
        <Box
          sx={{
            p: 2,
            overflow: 'hidden',
            display: 'flex',
            flexDirection: 'column',
            flexShrink: 0,
          }}
        >
          <Box display="flex" alignItems="center" gap={1.5} mb={1.5}>
            <Avatar
              {...stringAvatar(userDisplayName)}
              sx={{ width: 32, height: 32, fontSize: '0.875rem' }}
            />
            <Box sx={{ flexGrow: 1, minWidth: 0, overflow: 'hidden' }}>
              <Typography variant="body2" noWrap sx={{ fontWeight: 500, fontSize: '0.8125rem' }}>
                {user?.fullName || 'User'}
              </Typography>
              <Typography
                variant="caption"
                color="text.secondary"
                noWrap
                sx={{ fontSize: '0.6875rem' }}
              >
                {user?.email || 'N/A'}
              </Typography>
            </Box>
          </Box>
          <CompanySwitcher />
          <Button
            fullWidth
            variant="outlined"
            color="error"
            startIcon={<LogoutIcon sx={{ fontSize: '1rem' }} />}
            onClick={handleLogout}
            size="small"
            sx={{ mt: 1.5, fontSize: '0.75rem' }}
          >
            Logout
          </Button>
        </Box>
      </Drawer>

      {/* Main Content */}
      <Box component="main" sx={{ flexGrow: 1, p: 3 }}>
        {children}
      </Box>
    </Box>
  )
}
