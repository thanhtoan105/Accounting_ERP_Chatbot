import * as React from 'react'
import { useTranslation } from 'react-i18next'
import { GalleryVerticalEnd, MoreVertical, Building2, LogOut } from 'lucide-react'
import { Link, useNavigate } from 'react-router-dom'

import { NavMain } from '@/components/nav-main'
import { Button } from '@/components/ui/button'
import { Avatar, AvatarFallback } from '@/components/ui/avatar'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import {
  Sidebar,
  SidebarContent,
  SidebarFooter,
  SidebarHeader,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  SidebarRail,
} from '@/components/ui/sidebar'
import { useCompany } from '@/hooks/useCompany'

export type NavItem = {
  title: string
  url: string
  items?: { title: string; url: string }[]
}

export function AppSidebar({
  items,
  user,
  onLogout,
  ...props
}: React.ComponentProps<typeof Sidebar> & {
  items: NavItem[]
  user?: { name?: string | null; email?: string | null }
  onLogout?: () => void
}) {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { company } = useCompany()
  return (
    <Sidebar {...props}>
      <SidebarHeader>
        <SidebarMenu>
          <SidebarMenuItem>
            <SidebarMenuButton size="lg" asChild>
              <Link to="/">
                {company?.logoUrl ? (
                  <img
                    src={company.logoUrl}
                    alt={company.name || 'Company logo'}
                    className="aspect-square size-8 rounded-lg object-contain"
                  />
                ) : (
                  <div className="bg-sidebar-primary text-sidebar-primary-foreground flex aspect-square size-8 items-center justify-center rounded-lg">
                    <GalleryVerticalEnd className="size-4" />
                  </div>
                )}
                <div className="flex flex-col gap-0.5 leading-none">
                  <span className="font-medium">{company?.name || t('app.name')}</span>
                  <span className="">App</span>
                </div>
              </Link>
            </SidebarMenuButton>
          </SidebarMenuItem>
        </SidebarMenu>
      </SidebarHeader>
      <SidebarContent>
        <NavMain items={items} />
      </SidebarContent>
      <SidebarFooter>
        <div className="px-3 py-2 text-sm">
          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <Button variant="ghost" className="w-full justify-start p-2" size="sm" data-testid="user-menu-button">
                <div className="flex items-center gap-2 w-full justify-between">
                  <Avatar className="size-8">
                    <AvatarFallback>
                      {(user?.name || user?.email || 'U')
                        .split(' ')
                        .map((s) => s[0])
                        .slice(0, 2)
                        .join('')
                        .toUpperCase()}
                    </AvatarFallback>
                  </Avatar>
                  <div className="min-w-0 text-left flex-1">
                    <div className="font-medium truncate">{user?.name || 'User'}</div>
                    <div className="text-muted-foreground truncate">{user?.email || ''}</div>
                  </div>
                  <MoreVertical className="size-4 text-muted-foreground" />
                </div>
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent side="right" align="end" sideOffset={4} className="w-60">
              <DropdownMenuLabel>
                <div className="truncate">{user?.name || 'User'}</div>
                <div className="text-xs text-muted-foreground truncate">{user?.email || ''}</div>
              </DropdownMenuLabel>
              <DropdownMenuSeparator />
              <DropdownMenuItem
                onSelect={() => navigate('/company')}
                className="flex items-center gap-2"
              >
                <Building2 className="size-4" />
                <span>{t('nav.company')}</span>
              </DropdownMenuItem>
              <DropdownMenuSeparator />
              {onLogout && (
                <DropdownMenuItem onClick={onLogout} className="text-destructive" data-testid="logout-button">
                  <LogOut className="mr-2 size-4" />
                  <span>{t('auth.logout')}</span>
                </DropdownMenuItem>
              )}
            </DropdownMenuContent>
          </DropdownMenu>
        </div>
      </SidebarFooter>
      <SidebarRail />
    </Sidebar>
  )
}
