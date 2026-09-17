import { http, type ApiEnvelope } from "./http";
import type { StoryDocument } from "./extensions";
import type { ProjectSummary } from "./projects";

export interface ExcelSheet { name:string; rowCount:number; sampleRows:string[][]; hidden:boolean }
export interface ExcelMapping {
  nodeSheet:string; headerRow:number; columns:Record<string,number>;
  choiceSheet?:string; choiceHeaderRow:number; choiceColumns:Record<string,number>;
  branches:{targetColumn:number;textColumn?:number}[];
  typeMappings:Record<string,"NORMAL"|"ENDING">; startNodeKey?:string; preserveExtra:boolean;
}
export interface ExcelOptions { name:string; description?:string; mapping:ExcelMapping; expectedDigest?:string; confirm?:boolean }
export interface ExcelPreview { document:StoryDocument; digest:string; warnings:string[] }
function form(file:File,options?:ExcelOptions) {
  const body=new FormData();body.append("file",file);
  if(options)body.append("options",new Blob([JSON.stringify(options)],{type:"application/json"}));
  return body;
}
export async function inspectExcel(file:File) {
  return (await http.post<ApiEnvelope<{sheets:ExcelSheet[]}>>("/story-excel/inspect",form(file),{timeout:60000})).data.data;
}
export async function previewExcel(file:File,options:ExcelOptions) {
  return (await http.post<ApiEnvelope<ExcelPreview>>("/story-excel/preview",form(file,options),{timeout:60000})).data.data;
}
export async function importExcel(file:File,options:ExcelOptions,projectId?:number) {
  const url=projectId===undefined?"/projects/import-excel":`/projects/${projectId}/import-excel`;
  return (await http.post<ApiEnvelope<ProjectSummary>>(url,form(file,options),{timeout:120000})).data.data;
}
