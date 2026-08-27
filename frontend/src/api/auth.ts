import { http, type ApiEnvelope } from './http'

export interface AuthResult { token: string; userId: number; username: string; displayName: string }

export async function login(username: string, password: string) {
  const { data } = await http.post<ApiEnvelope<AuthResult>>('/auth/login', { username, password })
  return data.data
}

export async function register(username: string, password: string, displayName: string) {
  const { data } = await http.post<ApiEnvelope<AuthResult>>('/auth/register', { username, password, displayName })
  return data.data
}
