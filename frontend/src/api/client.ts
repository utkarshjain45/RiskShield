import { ApiResponse } from '../types';

export class ApiError extends Error {
  status: number;
  data: any;

  constructor(message: string, status: number, data?: any) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.data = data;
  }
}

interface RequestOptions extends RequestInit {
  params?: Record<string, string | number | boolean | undefined | null>;
}

export async function apiClient<T>(endpoint: string, options: RequestOptions = {}): Promise<T> {
  const { params, headers, ...customConfig } = options;

  let url = endpoint.startsWith('http') ? endpoint : `/api/v1${endpoint.startsWith('/') ? '' : '/'}${endpoint}`;

  if (params) {
    const searchParams = new URLSearchParams();
    Object.entries(params).forEach(([key, val]) => {
      if (val !== undefined && val !== null && val !== '') {
        searchParams.append(key, String(val));
      }
    });
    const queryString = searchParams.toString();
    if (queryString) {
      url += (url.includes('?') ? '&' : '?') + queryString;
    }
  }

  const correlationId = `web-${Math.random().toString(36).substring(2, 10)}`;

  const config: RequestInit = {
    method: options.method || 'GET',
    headers: {
      'Content-Type': 'application/json',
      'X-Correlation-Id': correlationId,
      'X-API-Key': localStorage.getItem('riskshield_api_key') || 'adm_buildathon_riskshield_key',
      ...headers,
    },
    ...customConfig,
  };

  try {
    const response = await fetch(url, config);

    if (!response.ok) {
      let errData: any;
      try {
        errData = await response.json();
      } catch {
        errData = { message: response.statusText };
      }
      throw new ApiError(
        errData?.message || errData?.detail || `Request failed with status ${response.status}`,
        response.status,
        errData
      );
    }

    const payload: ApiResponse<T> | T = await response.json();
    // If wrapped in standard ApiResponse { success: true, data: ... }
    if (payload && typeof payload === 'object' && 'data' in payload && 'success' in payload) {
      return (payload as ApiResponse<T>).data;
    }
    return payload as T;
  } catch (error) {
    if (error instanceof ApiError) {
      throw error;
    }
    throw new ApiError(
      error instanceof Error ? error.message : 'Network error or backend unreachable',
      0
    );
  }
}
