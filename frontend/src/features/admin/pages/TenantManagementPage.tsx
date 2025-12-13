import { useState } from 'react'
import { Plus, Building2 } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import CreateTenantDialog from '../components/CreateTenantDialog'

export default function TenantManagementPage() {
  const [createDialogOpen, setCreateDialogOpen] = useState(false)

  const handleCreateSuccess = () => {
    setCreateDialogOpen(false)
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold">Quản lý Tenant</h1>
          <p className="text-muted-foreground mt-1">
            Tạo và quản lý các công ty trong hệ thống kế toán đa người dùng
          </p>
        </div>
        <Button onClick={() => setCreateDialogOpen(true)}>
          <Plus className="mr-2 h-4 w-4" />
          Tạo Tenant mới
        </Button>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Danh sách Tenant</CardTitle>
          <CardDescription>Tất cả các công ty đã được tạo trong hệ thống</CardDescription>
        </CardHeader>
        <CardContent>
          <div className="flex flex-col items-center justify-center py-12 text-center">
            <Building2 className="h-12 w-12 text-muted-foreground/50 mb-4" />
            <h3 className="text-lg font-medium">Chưa có tenant nào</h3>
            <p className="text-muted-foreground mt-1 mb-4">
              Bắt đầu bằng cách tạo tenant đầu tiên cho hệ thống
            </p>
            <Button variant="outline" onClick={() => setCreateDialogOpen(true)}>
              <Plus className="mr-2 h-4 w-4" />
              Tạo Tenant mới
            </Button>
          </div>
        </CardContent>
      </Card>

      <CreateTenantDialog
        open={createDialogOpen}
        onClose={() => setCreateDialogOpen(false)}
        onSuccess={handleCreateSuccess}
      />
    </div>
  )
}
