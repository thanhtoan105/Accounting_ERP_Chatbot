import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import {
  getEmbeddingStatus,
  startBatchEmbedding,
  type EmbeddingStatusResponse,
} from '../api/embeddingApi'

const EMBEDDING_QUERY_KEY = ['admin', 'vouchers', 'embedding-status'] as const

export function useEmbeddingStatus() {
  return useQuery<EmbeddingStatusResponse>({
    queryKey: EMBEDDING_QUERY_KEY,
    queryFn: getEmbeddingStatus,
    staleTime: 1000 * 60 * 5, // 5 minutes
    retry: 2,
    refetchInterval: (query) => (query.state.data?.pending ?? 0) > 0 ? 30000 : false,
  })
}

export function useStartBatchEmbedding() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: startBatchEmbedding,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: EMBEDDING_QUERY_KEY })
    },
  })
}
