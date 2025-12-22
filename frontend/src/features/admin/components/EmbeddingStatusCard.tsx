import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Progress } from '@/components/ui/progress'
import { Skeleton } from '@/components/ui/skeleton'
import { AlertCircle, Database, CheckCircle2, Play, Loader2 } from 'lucide-react'

import { useEmbeddingStatus, useStartBatchEmbedding } from '../hooks/useEmbeddingStatus'

export function EmbeddingStatusCard() {
  const { data, isLoading, isError, error } = useEmbeddingStatus()
  const startMutation = useStartBatchEmbedding()

  if (isLoading) {
    return (
      <Card>
        <CardHeader className="pb-2">
          <CardTitle className="flex items-center gap-2 text-base">
            <Database className="h-4 w-4" />
            RAG Embedding Status
          </CardTitle>
        </CardHeader>
        <CardContent className="space-y-3">
          <Skeleton className="h-2 w-full" />
          <Skeleton className="h-4 w-24" />
        </CardContent>
      </Card>
    )
  }

  if (isError) {
    return (
      <Card>
        <CardHeader className="pb-2">
          <CardTitle className="flex items-center gap-2 text-base">
            <Database className="h-4 w-4" />
            RAG Embedding Status
          </CardTitle>
        </CardHeader>
        <CardContent>
          <div className="text-destructive flex items-center gap-2 text-sm">
            <AlertCircle className="h-4 w-4" />
            {error instanceof Error ? error.message : 'Failed to load embedding status'}
          </div>
        </CardContent>
      </Card>
    )
  }

  if (!data) {
    return null
  }

  const isComplete = data.total > 0 && data.embedded === data.total
  const hasPending = data.pending > 0

  return (
    <Card>
      <CardHeader className="pb-2">
        <CardTitle className="flex items-center gap-2 text-base">
          <Database className="h-4 w-4" />
          RAG Embedding Status
        </CardTitle>
      </CardHeader>
      <CardContent className="space-y-3">
        <Progress value={data.percentage} />
        <div className="flex items-center justify-between text-sm">
          <span className="text-muted-foreground">
            Embedded: {data.embedded.toLocaleString()} / {data.total.toLocaleString()}
          </span>
          <span className={isComplete ? 'text-green-600' : 'text-muted-foreground'}>
            {isComplete ? (
              <span className="flex items-center gap-1">
                <CheckCircle2 className="h-4 w-4" />
                Complete
              </span>
            ) : (
              `${data.percentage.toFixed(1)}%`
            )}
          </span>
        </div>
        {hasPending && (
          <div className="flex items-center justify-between">
            <p className="text-muted-foreground text-xs">
              {data.pending.toLocaleString()} vouchers pending embedding
            </p>
            <Button
              size="sm"
              variant="outline"
              onClick={() => startMutation.mutate()}
              disabled={startMutation.isPending}
            >
              {startMutation.isPending ? (
                <>
                  <Loader2 className="mr-1 h-3 w-3 animate-spin" />
                  Starting...
                </>
              ) : (
                <>
                  <Play className="mr-1 h-3 w-3" />
                  Start Embedding
                </>
              )}
            </Button>
          </div>
        )}
        {startMutation.isSuccess && (
          <p className="text-xs text-green-600">Batch embedding started successfully</p>
        )}
        {startMutation.isError && (
          <p className="text-destructive text-xs">
            {startMutation.error instanceof Error
              ? startMutation.error.message
              : 'Failed to start embedding'}
          </p>
        )}
      </CardContent>
    </Card>
  )
}
