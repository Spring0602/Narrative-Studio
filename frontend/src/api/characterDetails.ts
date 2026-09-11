import { http, type ApiEnvelope } from "./http";

export interface CharacterRelation {
  id: number;
  projectId: number;
  sourceCharacterId: number;
  targetCharacterId: number;
  relationType: string;
  description?: string | null;
}
export interface RelationInput {
  sourceCharacterId: number;
  targetCharacterId: number;
  relationType: string;
  description?: string | null;
}
export type KnowledgeLevel = "UNKNOWN" | "SUSPECTED" | "KNOWN";
export interface CharacterKnowledge {
  id: number;
  projectId: number;
  characterId: number;
  knowledgeKey: string;
  knowledgeLevel: KnowledgeLevel;
  description?: string | null;
  acquiredNodeId?: number | null;
}
export interface KnowledgeInput {
  knowledgeKey: string;
  knowledgeLevel: KnowledgeLevel;
  description?: string | null;
  acquiredNodeId?: number | null;
}

const projectBase = (projectId: number) => `/projects/${projectId}`;
export async function listRelations(projectId: number) {
  return (
    await http.get<ApiEnvelope<CharacterRelation[]>>(
      `${projectBase(projectId)}/character-relations`,
    )
  ).data.data;
}
export async function createRelation(projectId: number, input: RelationInput) {
  return (
    await http.post<ApiEnvelope<CharacterRelation>>(
      `${projectBase(projectId)}/character-relations`,
      input,
    )
  ).data.data;
}
export async function updateRelation(
  projectId: number,
  id: number,
  input: RelationInput,
) {
  return (
    await http.put<ApiEnvelope<CharacterRelation>>(
      `${projectBase(projectId)}/character-relations/${id}`,
      input,
    )
  ).data.data;
}
export async function deleteRelation(projectId: number, id: number) {
  await http.delete(`${projectBase(projectId)}/character-relations/${id}`);
}
export async function listKnowledge(projectId: number, characterId: number) {
  return (
    await http.get<ApiEnvelope<CharacterKnowledge[]>>(
      `${projectBase(projectId)}/characters/${characterId}/knowledge`,
    )
  ).data.data;
}
export async function createKnowledge(
  projectId: number,
  characterId: number,
  input: KnowledgeInput,
) {
  return (
    await http.post<ApiEnvelope<CharacterKnowledge>>(
      `${projectBase(projectId)}/characters/${characterId}/knowledge`,
      input,
    )
  ).data.data;
}
export async function updateKnowledge(
  projectId: number,
  characterId: number,
  id: number,
  input: KnowledgeInput,
) {
  return (
    await http.put<ApiEnvelope<CharacterKnowledge>>(
      `${projectBase(projectId)}/characters/${characterId}/knowledge/${id}`,
      input,
    )
  ).data.data;
}
export async function deleteKnowledge(
  projectId: number,
  characterId: number,
  id: number,
) {
  await http.delete(
    `${projectBase(projectId)}/characters/${characterId}/knowledge/${id}`,
  );
}
export async function getNodeCast(projectId: number, nodeId: number) {
  return (
    await http.get<ApiEnvelope<number[]>>(
      `${projectBase(projectId)}/story-nodes/${nodeId}/characters`,
    )
  ).data.data;
}
export async function replaceNodeCast(
  projectId: number,
  nodeId: number,
  characterIds: number[],
) {
  return (
    await http.put<ApiEnvelope<number[]>>(
      `${projectBase(projectId)}/story-nodes/${nodeId}/characters`,
      { characterIds },
    )
  ).data.data;
}
