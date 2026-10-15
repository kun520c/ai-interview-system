import type { AxiosError, AxiosRequestConfig, AxiosResponse, InternalAxiosRequestConfig } from 'axios'
import axios from 'axios'

import { ApiError, classifyHttpStatus } from '@/api/apiError'
import { isAuthPublicPath } from '@/api/endpoints'
import { isResultEnvelope, unwrapResultData } from '@/api/resultEnvelope'
import { notifyUnauthorized } from '@/api/unauthorized'
import { getSessionGeneration, isCurrentIdentityScope } from '@/app/session/sessionScope'
import { readAuthCredential } from '@/features/auth/storage/authStorage'
import type { Result } from '@/types/result'

type IdentityScopedConfig = InternalAxiosRequestConfig & {
  identityUserId?: number | null
  identityGeneration?: number
}

export function normalizeAxiosError(error: unknown): ApiError {
  if (error instanceof ApiError) {
    return error
  }

  if (axios.isAxiosError(error)) {
    const axiosError = error as AxiosError<unknown>
    if (axiosError.response) {
      const { status, data } = axiosError.response
      if (isResultEnvelope(data)) {
        return new ApiError({
          httpStatus: status,
          code: data.code,
          message: data.message,
          kind: classifyHttpStatus(status),
          cause: error,
        })
      }
      return new ApiError({
        httpStatus: status,
        code: status,
        message: axiosError.message || '请求失败',
        cause: error,
      })
    }

    return new ApiError({
      httpStatus: 0,
      code: 0,
      message: '网络异常，请稍后重试',
      kind: classifyHttpStatus(0),
      cause: error,
    })
  }

  return new ApiError({
    httpStatus: 0,
    code: 0,
    message: error instanceof Error ? error.message : '未知错误',
    kind: 'unknown',
    cause: error,
  })
}

export const httpClient = axios.create({
  baseURL: '/api',
  timeout: 120_000,
})

httpClient.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  if (isAuthPublicPath(config.url)) {
    return config
  }
  const credential = readAuthCredential()
  if (credential) {
    const scoped = config as IdentityScopedConfig
    scoped.headers.Authorization = `${credential.tokenType} ${credential.accessToken}`
    scoped.identityUserId = credential.userId
    scoped.identityGeneration = getSessionGeneration()
  }
  return config
})

httpClient.interceptors.response.use(
  (response: AxiosResponse<Result<unknown>>) => response,
  (error: unknown) => {
    const apiError = normalizeAxiosError(error)
    const config = (axios.isAxiosError(error) ? error.config : undefined) as IdentityScopedConfig | undefined
    const url = config?.url
    if (apiError.kind === 'unauthenticated' && !isAuthPublicPath(url)) {
      const currentUserId = readAuthCredential()?.userId ?? null
      if (
        config?.identityUserId === undefined ||
        config?.identityGeneration === undefined ||
        isCurrentIdentityScope(config.identityUserId, config.identityGeneration, currentUserId)
      ) {
        notifyUnauthorized()
      }
    }
    return Promise.reject(apiError)
  },
)

export async function apiGet<T>(url: string, config?: AxiosRequestConfig): Promise<T> {
  const response = await httpClient.get<Result<T>>(url, config)
  return unwrapResultData<T>(response.data, response.status)
}

export async function apiPost<T>(
  url: string,
  data?: unknown,
  config?: AxiosRequestConfig,
): Promise<T> {
  const response = await httpClient.post<Result<T>>(url, data, config)
  return unwrapResultData<T>(response.data, response.status)
}

export async function apiPut<T>(
  url: string,
  data?: unknown,
  config?: AxiosRequestConfig,
): Promise<T> {
  const response = await httpClient.put<Result<T>>(url, data, config)
  return unwrapResultData<T>(response.data, response.status)
}

export async function apiPostForm<T>(
  url: string,
  data: FormData,
  config?: AxiosRequestConfig,
): Promise<T> {
  const response = await httpClient.post<Result<T>>(url, data, config)
  return unwrapResultData<T>(response.data, response.status)
}
