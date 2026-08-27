import { http, type ApiEnvelope } from './http'

export interface ProjectSummary {
  id: number
  name: string
  description?: string
  ownerId: number
  status: 'ACTIVE' | 'ARCHIVED'
  memberRole: 'OWNER' | 'EDITOR' | 'TESTER'
  updatedAt: string
}

export async function listProjects() {
  const { data } = await http.get<ApiEnvelope<ProjectSummary[]>>('/projects')
  return data.data
}

export async function createProject(payload: { name: string; description?: string }) {
  const { data } = await http.post<ApiEnvelope<ProjectSummary>>('/projects', payload)
  return data.data
}
