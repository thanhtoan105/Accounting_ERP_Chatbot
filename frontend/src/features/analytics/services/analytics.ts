import axios from '@/utils/axios';

export interface MetabaseTokenResponse {
  token: string;
  dashboardId: string;
}

export interface MetabaseUrlResponse {
  url: string;
  dashboardId: string;
}

/**
 * Get a signed JWT token for embedding a Metabase dashboard.
 * The token includes the current company context for data filtering.
 */
export const getEmbeddingToken = async (dashboardId: number): Promise<MetabaseTokenResponse> => {
  const response = await axios.get<MetabaseTokenResponse>(
    `/api/v1/analytics/metabase/token/${dashboardId}`
  );
  return response.data;
};

/**
 * Get the complete iframe URL for an embedded Metabase dashboard.
 */
export const getEmbeddedDashboardUrl = async (dashboardId: number): Promise<MetabaseUrlResponse> => {
  const response = await axios.get<MetabaseUrlResponse>(
    `/api/v1/analytics/metabase/url/${dashboardId}`
  );
  return response.data;
};
