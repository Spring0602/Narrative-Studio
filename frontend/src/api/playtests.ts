import { http, type ApiEnvelope } from './http'
import type { ProgressSnapshot } from './progress'

// Values are canonical strings, preserving signed 64-bit INTEGER precision in JS.
export type StateSnapshot = Record<string, string>
export interface PlaytestNode { id: number; nodeKey: string; title: string; content?: string; nodeType: 'NORMAL' | 'ENDING' }
export interface PlaytestChoice { id: number; targetNodeId: number; choiceText: string; sortOrder: number }
export interface PlaytestSession {
  id: number
  projectId: number
  testerId: number
  status: 'RUNNING' | 'COMPLETED' | 'ABORTED'
  currentNode: PlaytestNode
  stepNo: number
  state: StateSnapshot
  releaseId?: number
  knowledge: StateSnapshot
  availableChoices: PlaytestChoice[]
  progress?: ProgressSnapshot
  lockedChoices?: { id:number; choiceText:string; reason:string }[]
  deadEnd: boolean
  startedAt: string
  finishedAt?: string
}
export interface PlaytestStep {
  id: number
  stepNo: number
  nodeId: number
  choiceId?: number
  stateBefore: StateSnapshot
  stateAfter: StateSnapshot
  knowledgeBefore: StateSnapshot
  knowledgeAfter: StateSnapshot
  progressBefore?: ProgressSnapshot
  progressAfter?: ProgressSnapshot
  createdAt: string
}
export interface Page<T> { items: T[]; page: number; size: number; total: number; pages: number }
const base = (projectId: number) => `/projects/${projectId}/playtests`

export async function startPlaytest(projectId: number) {
  return (await http.post<ApiEnvelope<PlaytestSession>>(base(projectId))).data.data
}
export async function startReleasedPlaytest(projectId: number, releaseId: number) {
  return (await http.post<ApiEnvelope<PlaytestSession>>(
    `/projects/${projectId}/releases/${releaseId}/playtests`,
  )).data.data
}
export async function listPlaytests(projectId: number, page = 1, size = 20) {
  return (await http.get<ApiEnvelope<Page<PlaytestSession>>>(base(projectId), { params: { page, size } })).data.data
}
export async function getPlaytest(projectId: number, sessionId: number) {
  return (await http.get<ApiEnvelope<PlaytestSession>>(`${base(projectId)}/${sessionId}`)).data.data
}
export async function choosePlaytestOption(projectId: number, sessionId: number, choiceId: number, expectedStepNo: number) {
  return (await http.post<ApiEnvelope<PlaytestSession>>(
    `${base(projectId)}/${sessionId}/choices/${choiceId}`, { expectedStepNo },
  )).data.data
}
export async function restartPlaytest(projectId: number, sessionId: number) {
  return (await http.post<ApiEnvelope<PlaytestSession>>(`${base(projectId)}/${sessionId}/restart`)).data.data
}
export async function stopPlaytest(projectId: number, sessionId: number) {
  return (await http.post<ApiEnvelope<PlaytestSession>>(`${base(projectId)}/${sessionId}/stop`)).data.data
}
export async function getPlaytestSteps(projectId: number, sessionId: number, page = 1, size = 20) {
  return (await http.get<ApiEnvelope<Page<PlaytestStep>>>(`${base(projectId)}/${sessionId}/steps`, {
    params: { page, size },
  })).data.data
}
