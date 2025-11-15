import { APIRequestContext } from '@playwright/test';

/**
 * API Request Helper
 * 
 * Pure function for making HTTP requests in tests.
 * Framework-agnostic - accepts all dependencies explicitly.
 * 
 * Pattern: Pure function → Fixture wrapper (see fixtures/index.ts)
 */
export type ApiRequestParams = {
  request: APIRequestContext;
  method: 'GET' | 'POST' | 'PUT' | 'DELETE' | 'PATCH';
  url: string;
  data?: unknown;
  headers?: Record<string, string>;
  token?: string;
};

export async function apiRequest({
  request,
  method,
  url,
  data,
  headers = {},
  token,
}: ApiRequestParams): Promise<unknown> {
  const baseURL = process.env.API_URL || 'http://localhost:8080';
  const fullUrl = url.startsWith('http') ? url : `${baseURL}${url}`;

  const requestHeaders = {
    'Content-Type': 'application/json',
    ...(token && { Authorization: `Bearer ${token}` }),
    ...headers,
  };

  const fetchOptions: {
    method: string;
    headers: Record<string, string>;
    data?: string;
  } = {
    method,
    headers: requestHeaders,
  };

  if (data) {
    fetchOptions.data = JSON.stringify(data);
  }

  const response = await request.fetch(fullUrl, fetchOptions);

  if (!response.ok()) {
    const errorText = await response.text();
    throw new Error(`API request failed: ${response.status()} ${errorText}`);
  }

  return response.json();
}

