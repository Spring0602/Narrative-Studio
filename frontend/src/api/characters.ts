import { http, type ApiEnvelope } from './http'

export type CharacterStatus = 'ACTIVE' | 'DELETED'

export interface CharacterSummary {
  id: number
  projectId: number
  name: string
  summary?: string
  personality?: string
  goal?: string
  valueOrder?: string
  status: CharacterStatus
  updatedAt: string
}

export interface SaveCharacterPayload {
  name: string
  summary?: string
  personality?: string
  goal?: string
  valueOrder?: string
}

export async function listCharacters(projectId: number) {
  const { data } = await http.get<ApiEnvelope<CharacterSummary[]>>(
    `/projects/${projectId}/characters`,
  )
  return data.data
}

export async function getCharacter(projectId: number, characterId: number) {
  const { data } = await http.get<ApiEnvelope<CharacterSummary>>(
    `/projects/${projectId}/characters/${characterId}`,
  )
  return data.data
}

export async function createCharacter(
  projectId: number,
  payload: SaveCharacterPayload,
) {
  const { data } = await http.post<ApiEnvelope<CharacterSummary>>(
    `/projects/${projectId}/characters`,
    payload,
  )
  return data.data
}

export async function updateCharacter(
  projectId: number,
  characterId: number,
  payload: SaveCharacterPayload,
) {
  const { data } = await http.put<ApiEnvelope<CharacterSummary>>(
    `/projects/${projectId}/characters/${characterId}`,
    payload,
  )
  return data.data
}

export async function deleteCharacter(projectId: number, characterId: number) {
  await http.delete<ApiEnvelope<null>>(
    `/projects/${projectId}/characters/${characterId}`,
  )
}
