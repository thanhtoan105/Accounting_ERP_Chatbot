import axiosInstance from '@/utils/axios'

interface EmbedUrlResponse {
  embedUrl: string
}

export async function getMetabaseDashboardEmbedUrl(dashboardId: number): Promise<string> {
  const response = await axiosInstance.get<EmbedUrlResponse>(
    `/metabase/embed/dashboard/${dashboardId}`,
  )
  return response.data.embedUrl
}
