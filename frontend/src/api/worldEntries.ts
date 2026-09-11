import { http, type ApiEnvelope } from './http'

export type WorldEntryType =
  | 'SETTING'
  | 'LOCATION'
  | 'FACTION'
  | 'HISTORY'
  | 'RULE'
  | 'OTHER'

export interface WorldEntrySummary {
  id: number
  projectId: number
  entryType: WorldEntryType
  title: string
  content: string
  sortOrder: number
  updatedAt: string
}

export interface SaveWorldEntryPayload {
  entryType: WorldEntryType
  title: string
  content: string
  sortOrder: number
}

export async function listWorldEntries(
  projectId: number,
  entryType?: WorldEntryType,
) {
  const { data } = await http.get<ApiEnvelope<WorldEntrySummary[]>>(
    `/projects/${projectId}/world-entries`,
    { params: entryType ? { entryType } : undefined },
  )
  return data.data
}

export async function getWorldEntry(projectId: number, entryId: number) {
  const { data } = await http.get<ApiEnvelope<WorldEntrySummary>>(
    `/projects/${projectId}/world-entries/${entryId}`,
  )
  return data.data
}

export async function createWorldEntry(
  projectId: number,
  payload: SaveWorldEntryPayload,
) {
  const { data } = await http.post<ApiEnvelope<WorldEntrySummary>>(
    `/projects/${projectId}/world-entries`,
    payload,
  )
  return data.data
}

export async function updateWorldEntry(
  projectId: number,
  entryId: number,
  payload: SaveWorldEntryPayload,
) {
  const { data } = await http.put<ApiEnvelope<WorldEntrySummary>>(
    `/projects/${projectId}/world-entries/${entryId}`,
    payload,
  )
  return data.data
}

export async function deleteWorldEntry(projectId: number, entryId: number) {
  await http.delete<ApiEnvelope<null>>(
    `/projects/${projectId}/world-entries/${entryId}`,
  )
}
