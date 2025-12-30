import axiosInstance from '@/utils/axios'

export interface EmbeddingStatusResponse {
  total: number
  embedded: number
  pending: number
  percentage: number
}

export async function getEmbeddingStatus(): Promise<EmbeddingStatusResponse> {
  const response = await axiosInstance.get<{ data: EmbeddingStatusResponse }>(
    '/admin/vouchers/embedding-status',
  )
  return response.data.data
}

export async function startBatchEmbedding(): Promise<void> {
  await axiosInstance.post('/admin/vouchers/embed/start')
}
