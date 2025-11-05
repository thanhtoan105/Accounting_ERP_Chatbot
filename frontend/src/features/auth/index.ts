export { default as Login } from './pages/Login'
export { default as ForgotPassword } from './pages/ForgotPassword'
export { default as ResetPassword } from './pages/ResetPassword'
export * from './services/auth'
// <CHANGE> re-export auth components barrel for simpler imports
export * from './components'
