import { http, type ApiEnvelope } from "./http";
import type { ProjectSummary } from "./projects";
import type { SaveStoryNodePayload } from "./storyGraph";
import type { VariableInput, ConditionInput, EffectInput, UnlockRule } from "./rules";

export interface StoryDocument {
  schemaVersion: 1 | 2;
  name: string;
  description?: string;
  nodes: SaveStoryNodePayload[];
  variables: VariableInput[];
  choices: {
    sourceNodeKey: string; targetNodeKey: string; choiceText: string; sortOrder: number; enabled: boolean;
    conditions: (Omit<ConditionInput, "variableId"> & { variableKey: string })[];
    effects: (Omit<EffectInput, "variableId"> & { variableKey: string })[];
    unlockRule?: UnlockRule | null;
  }[];
}
export interface EndingCoverage {
  releaseId?: number;
  totalSessions: number; completedSessions: number; runningSessions: number; abortedSessions: number;
  totalEndings: number; reachedEndings: number; coveragePercent: number;
  endings: { nodeId: number; nodeKey: string; title: string; completions: number }[];
}
export async function exportStory(projectId: number) {
  return (await http.get<ApiEnvelope<StoryDocument>>(`/projects/${projectId}/export`, { timeout: 60000 })).data.data;
}
export async function importStory(document: StoryDocument) {
  return (await http.post<ApiEnvelope<ProjectSummary>>("/projects/import", document, { timeout: 60000 })).data.data;
}
export async function getEndingCoverage(projectId: number, releaseId?: number) {
  return (await http.get<ApiEnvelope<EndingCoverage>>(`/projects/${projectId}/ending-coverage`, { params: { releaseId } })).data.data;
}
export async function generateDialogue(projectId: number,
  request: { nodeId: number; characterId: number; direction: string; consent: true }) {
  return (await http.post<ApiEnvelope<{ text: string; saved: false }>>(
    `/projects/${projectId}/ai/dialogue-candidates`, request, { timeout: 35000 },
  )).data.data;
}
