import { http, type ApiEnvelope } from "./http";
import type { Page } from "./playtests";
import type { StoryChoiceSummary } from "./storyGraph";

export interface ChoiceDraft {
  id: number;
  projectId: number;
  sourceNodeId: number;
  choiceText?: string | null;
  sortOrder: number;
  createdBy: number;
  createdAt: string;
  updatedAt: string;
}

export interface ChoiceDraftInput {
  sourceNodeId: number;
  choiceText?: string | null;
  sortOrder: number;
}

const base = (projectId: number) => `/projects/${projectId}/choice-drafts`;

export async function listChoiceDrafts(
  projectId: number,
  page = 1,
  size = 100,
) {
  return (
    await http.get<ApiEnvelope<Page<ChoiceDraft>>>(base(projectId), {
      params: { page, size },
    })
  ).data.data;
}

export async function createChoiceDraft(
  projectId: number,
  input: ChoiceDraftInput,
) {
  return (await http.post<ApiEnvelope<ChoiceDraft>>(base(projectId), input))
    .data.data;
}

export async function updateChoiceDraft(
  projectId: number,
  draftId: number,
  input: ChoiceDraftInput,
) {
  return (
    await http.put<ApiEnvelope<ChoiceDraft>>(
      `${base(projectId)}/${draftId}`,
      input,
    )
  ).data.data;
}

export async function deleteChoiceDraft(projectId: number, draftId: number) {
  await http.delete(`${base(projectId)}/${draftId}`);
}

export async function publishChoiceDraft(
  projectId: number,
  draftId: number,
  targetNodeId: number,
) {
  return (
    await http.post<ApiEnvelope<StoryChoiceSummary>>(
      `${base(projectId)}/${draftId}/publish`,
      { targetNodeId },
    )
  ).data.data;
}
