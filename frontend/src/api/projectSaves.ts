import {http,type ApiEnvelope} from "./http";
import type {ProjectSummary} from "./projects";
export interface EditorDraft {module:string;label:string;entityTable:string;entityId:number|null;values:Record<string,any>}
export type Drafts=Record<string,EditorDraft>;
export interface SaveSummary {id:number;savedBy:number;kind:"MANUAL"|"AUTO";revision:number;label:string;hash:string;savedAt:string}
export interface SaveState {manual:SaveSummary[];automatic?:SaveSummary;currentHash:string}
export interface SaveDetail {summary:SaveSummary;snapshot:{schemaVersion:number;project:{name:string;description?:string};tables:Record<string,Record<string,unknown>[]>;drafts:Drafts}}
export async function getSaveState(id:number){return (await http.get<ApiEnvelope<SaveState>>(`/projects/${id}/saves`,{timeout:60000})).data.data;}
export async function getSaveDetail(id:number,save:number){return (await http.get<ApiEnvelope<SaveDetail>>(`/projects/${id}/saves/${save}`,{timeout:60000})).data.data;}
export async function saveVersion(id:number,drafts:Drafts,label:string){return (await http.post<ApiEnvelope<{changed:boolean;saved:SaveSummary}>>(`/projects/${id}/saves`,{label,drafts,expectedRevision:0},{timeout:60000})).data.data;}
export async function saveAutomatic(id:number,drafts:Drafts,expectedRevision:number,baselineHash:string){return (await http.put<ApiEnvelope<{changed:boolean;saved?:SaveSummary}>>(`/projects/${id}/saves/auto`,{drafts,expectedRevision,baselineHash},{timeout:60000})).data.data;}
export async function restoreCopy(id:number,save:SaveSummary,name:string){return (await http.post<ApiEnvelope<{project:ProjectSummary;drafts:Drafts}>>(`/projects/${id}/saves/${save.id}/restore-copy`,{name,confirm:true,expectedRevision:save.revision},{timeout:120000})).data.data;}
