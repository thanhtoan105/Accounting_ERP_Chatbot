'use client'

import { MoreHorizontal, type LucideIcon } from 'lucide-react'
import { Link as RouterLink, useNavigate } from 'react-router-dom'

import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
  DropdownMenuSub,
  DropdownMenuSubTrigger,
  DropdownMenuSubContent,
} from '@/components/ui/dropdown-menu'
import {
  SidebarGroup,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  useSidebar,
} from '@/components/ui/sidebar'

export function NavMain({
  items,
}: {
  items: {
    title: string
    url: string
    icon?: LucideIcon
    isActive?: boolean
    items?: {
      title: string
      url: string
      items?: {
        title: string
        url: string
      }[]
    }[]
  }[]
}) {
  const { isMobile } = useSidebar()
  const navigate = useNavigate()

  return (
    <SidebarGroup>
      <SidebarMenu>
        {items.map((item) => (
          <SidebarMenuItem key={item.title}>
            {item.items?.length ? (
              <DropdownMenu>
                <DropdownMenuTrigger asChild>
                  <SidebarMenuButton className="data-[state=open]:bg-sidebar-accent data-[state=open]:text-sidebar-accent-foreground">
                    {item.title} <MoreHorizontal className="ml-auto" />
                  </SidebarMenuButton>
                </DropdownMenuTrigger>
                <DropdownMenuContent
                  side={isMobile ? 'bottom' : 'right'}
                  align={isMobile ? 'end' : 'start'}
                  className="min-w-56 rounded-lg"
                >
                  {item.items.map((sub) => {
                    // Check if sub-item has nested items (submenu)
                    if (sub.items && sub.items.length > 0) {
                      return (
                        <DropdownMenuSub key={sub.title}>
                          <DropdownMenuSubTrigger>{sub.title}</DropdownMenuSubTrigger>
                          <DropdownMenuSubContent>
                            {sub.items.map((nested) => (
                              <DropdownMenuItem asChild key={nested.title}>
                                <RouterLink to={nested.url}>{nested.title}</RouterLink>
                              </DropdownMenuItem>
                            ))}
                          </DropdownMenuSubContent>
                        </DropdownMenuSub>
                      )
                    }
                    // Regular menu item
                    return (
                      <DropdownMenuItem asChild key={sub.title}>
                        <RouterLink to={sub.url}>{sub.title}</RouterLink>
                      </DropdownMenuItem>
                    )
                  })}
                </DropdownMenuContent>
              </DropdownMenu>
            ) : (
              <SidebarMenuButton asChild onClick={() => navigate(item.url)}>
                <button type="button" className="w-full text-left">
                  {item.title}
                </button>
              </SidebarMenuButton>
            )}
          </SidebarMenuItem>
        ))}
      </SidebarMenu>
    </SidebarGroup>
  )
}
