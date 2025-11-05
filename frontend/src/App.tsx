import { BrowserRouter } from 'react-router-dom'
import { Toaster } from 'sonner'
import AppRoutes from '@/routes/AppRoutes'

function App() {
  return (
    <>
      <Toaster richColors />
      <BrowserRouter>
        <AppRoutes />
      </BrowserRouter>
    </>
  )
}

export default App
