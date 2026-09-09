import { http, type ApiEnvelope } from './http'

export type MemberRole = 'OWNER' | 'EDITOR' | 'TESTER'
export type EditableMemberRole = 'EDITOR' | 'TESTER'

export interface MemberSummary {
  id: number
  userId: number
  username: string
  displayName?: string
  memberRole: MemberRole
  joinedAt: string
}

export async function listMembers(projectId: number) {
  const { data } = await http.get<ApiEnvelope<MemberSummary[]>>(
    `/projects/${projectId}/members`,
  )
  return data.data
}

export async function addMember(
  projectId: number,
  payload: { username: string; memberRole: EditableMemberRole },
) {
  const { data } = await http.post<ApiEnvelope<MemberSummary>>(
    `/projects/${projectId}/members`,
    payload,
  )
  return data.data
}

export async function updateMemberRole(
  projectId: number,
  memberId: number,
  payload: { memberRole: EditableMemberRole },
) {
  const { data } = await http.put<ApiEnvelope<MemberSummary>>(
    `/projects/${projectId}/members/${memberId}/role`,
    payload,
  )
  return data.data
}

export async function removeMember(projectId: number, memberId: number) {
  await http.delete<ApiEnvelope<null>>(
    `/projects/${projectId}/members/${memberId}`,
  )
}