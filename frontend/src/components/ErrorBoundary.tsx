import React, { Component, type ReactNode } from 'react'
import i18n from '@/i18n'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { AlertCircle, RefreshCw } from 'lucide-react'

interface Props {
  children: ReactNode
  fallback?: ReactNode
}

interface State {
  hasError: boolean
  error: Error | null
}

export class ErrorBoundary extends Component<Props, State> {
  constructor(props: Props) {
    super(props)
    this.state = { hasError: false, error: null }
  }

  static getDerivedStateFromError(error: Error): State {
    return { hasError: true, error }
  }

  componentDidCatch(error: Error, errorInfo: React.ErrorInfo) {
    console.error('ErrorBoundary caught an error:', error, errorInfo)
  }

  handleReset = () => {
    this.setState({ hasError: false, error: null })
  }

  render() {
    if (this.state.hasError) {
      if (this.props.fallback) {
        return this.props.fallback
      }

      return (
        <div className="flex min-h-[400px] items-center justify-center p-6">
          <Alert variant="destructive" className="max-w-md">
            <AlertCircle className="size-4" />
            <AlertTitle>{i18n.t('errors.somethingWentWrong')}</AlertTitle>
            <AlertDescription className="mt-2">
              {this.state.error?.message || i18n.t('errors.unexpectedError')}
            </AlertDescription>
            <div className="mt-4 flex gap-2">
              <Button variant="outline" size="sm" onClick={() => window.location.reload()}>
                <RefreshCw className="mr-2 size-4" />
                {i18n.t('errors.reloadPage')}
              </Button>
              <Button variant="outline" size="sm" onClick={this.handleReset}>
                {i18n.t('errors.tryAgain')}
              </Button>
            </div>
          </Alert>
        </div>
      )
    }

    return this.props.children
  }
}
