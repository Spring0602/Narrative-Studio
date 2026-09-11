import { http, type ApiEnvelope } from "./http";

export type StoryNodeType = "NORMAL" | "ENDING";

export interface StoryNodeSummary {
  id: number;
  projectId: number;
  nodeKey: string;
  title: string;
  content?: string;
  nodeType: StoryNodeType;
  scene?: string;
  isStart: boolean;
  positionX: number;
  positionY: number;
  updatedAt: string;
}

export interface SaveStoryNodePayload {
  nodeKey: string;
  title: string;
  content?: string;
  nodeType: StoryNodeType;
  scene?: string;
  isStart: boolean;
  positionX: number;
  positionY: number;
}

export interface StoryChoiceSummary {
  id: number;
  projectId: number;
  sourceNodeId: number;
  targetNodeId: number;
  choiceText: string;
  sortOrder: number;
  enabled: boolean;
  updatedAt: string;
}

export interface SaveStoryChoicePayload {
  targetNodeId: number;
  choiceText: string;
  sortOrder: number;
  enabled: boolean;
}

export interface StoryGraph {
  nodes: StoryNodeSummary[];
  choices: StoryChoiceSummary[];
}

export interface StoryNodePosition {
  nodeId: number;
  positionX: number;
  positionY: number;
}

export async function getStoryGraph(projectId: number) {
  const { data } = await http.get<ApiEnvelope<StoryGraph>>(
    `/projects/${projectId}/story-nodes`,
  );
  return data.data;
}

export async function getStoryNode(projectId: number, nodeId: number) {
  const { data } = await http.get<ApiEnvelope<StoryNodeSummary>>(
    `/projects/${projectId}/story-nodes/${nodeId}`,
  );
  return data.data;
}

export async function createStoryNode(
  projectId: number,
  payload: SaveStoryNodePayload,
) {
  const { data } = await http.post<ApiEnvelope<StoryNodeSummary>>(
    `/projects/${projectId}/story-nodes`,
    payload,
  );
  return data.data;
}

export async function updateStoryNode(
  projectId: number,
  nodeId: number,
  payload: SaveStoryNodePayload,
) {
  const { data } = await http.put<ApiEnvelope<StoryNodeSummary>>(
    `/projects/${projectId}/story-nodes/${nodeId}`,
    payload,
  );
  return data.data;
}

export async function deleteStoryNode(projectId: number, nodeId: number) {
  await http.delete<ApiEnvelope<null>>(
    `/projects/${projectId}/story-nodes/${nodeId}`,
  );
}

export async function updateStoryNodePositions(
  projectId: number,
  positions: StoryNodePosition[],
) {
  await http.put<ApiEnvelope<null>>(
    `/projects/${projectId}/story-nodes/positions`,
    { positions },
  );
}

export async function listStoryChoices(projectId: number, nodeId: number) {
  const { data } = await http.get<ApiEnvelope<StoryChoiceSummary[]>>(
    `/projects/${projectId}/story-nodes/${nodeId}/choices`,
  );
  return data.data;
}

export async function createStoryChoice(
  projectId: number,
  nodeId: number,
  payload: SaveStoryChoicePayload,
) {
  const { data } = await http.post<ApiEnvelope<StoryChoiceSummary>>(
    `/projects/${projectId}/story-nodes/${nodeId}/choices`,
    payload,
  );
  return data.data;
}

export async function updateStoryChoice(
  projectId: number,
  nodeId: number,
  choiceId: number,
  payload: SaveStoryChoicePayload,
) {
  const { data } = await http.put<ApiEnvelope<StoryChoiceSummary>>(
    `/projects/${projectId}/story-nodes/${nodeId}/choices/${choiceId}`,
    payload,
  );
  return data.data;
}

export async function deleteStoryChoice(
  projectId: number,
  nodeId: number,
  choiceId: number,
) {
  await http.delete<ApiEnvelope<null>>(
    `/projects/${projectId}/story-nodes/${nodeId}/choices/${choiceId}`,
  );
}
