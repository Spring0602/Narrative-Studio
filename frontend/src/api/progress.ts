import { http, type ApiEnvelope } from "./http";
export interface ProgressSnapshot { values: Record<string,string>; completedEndings: string[]; visitedNodes: string[] }
export interface PlayerProgress { releaseId?: number; revision: number; progress: ProgressSnapshot }
const path=(projectId:number)=>`/projects/${projectId}/player-progress`;
export async function getPlayerProgress(projectId:number,releaseId?:number) {
  return (await http.get<ApiEnvelope<PlayerProgress>>(path(projectId),{params:{releaseId}})).data.data;
}
export async function resetPlayerProgress(projectId:number,releaseId:number|undefined,expectedRevision:number) {
  return (await http.post<ApiEnvelope<PlayerProgress>>(path(projectId)+"/reset",{expectedRevision,confirm:true},{params:{releaseId}})).data.data;
}
export async function overridePlayerProgress(projectId:number,releaseId:number|undefined,expectedRevision:number,
  values:Record<string,string>,completedEndings:string[]) {
  return (await http.put<ApiEnvelope<PlayerProgress>>(path(projectId),{expectedRevision,confirm:true,values,completedEndings},{params:{releaseId}})).data.data;
}
