import { http, type ApiEnvelope } from "./http";
import type { Page } from "./playtests";
import type { IssueStatus } from "./issues";

export interface TestFeedback {
  id: number;
  projectId: number;
  sessionId?: number | null;
  stepId?: number | null;
  reporterId: number;
  title: string;
  description: string;
  status: IssueStatus;
  createdAt: string;
  updatedAt: string;
}
export interface FeedbackInput {
  sessionId?: number | null;
  stepId?: number | null;
  title: string;
  description: string;
}
const base = (projectId: number) => `/projects/${projectId}/feedback`;
export async function listFeedback(projectId: number, page = 1, size = 100) {
  return (
    await http.get<ApiEnvelope<Page<TestFeedback>>>(base(projectId), {
      params: { page, size },
    })
  ).data.data;
}
export async function createFeedback(projectId: number, input: FeedbackInput) {
  return (await http.post<ApiEnvelope<TestFeedback>>(base(projectId), input))
    .data.data;
}
export async function updateFeedbackStatus(
  projectId: number,
  id: number,
  status: IssueStatus,
) {
  return (
    await http.put<ApiEnvelope<TestFeedback>>(
      `${base(projectId)}/${id}/status`,
      { status },
    )
  ).data.data;
}
