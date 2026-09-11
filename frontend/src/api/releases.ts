import { http, type ApiEnvelope } from "./http";
import type { Page } from "./playtests";

export interface ReleaseSummary {
  id: number;
  projectId: number;
  versionNo: number;
  publishedBy: number;
  publishedAt: string;
  schemaVersion: number;
}

const base = (projectId: number) => `/projects/${projectId}/releases`;

export async function listReleases(projectId: number, page = 1, size = 100) {
  return (
    await http.get<ApiEnvelope<Page<ReleaseSummary>>>(base(projectId), {
      params: { page, size },
    })
  ).data.data;
}

export async function publishRelease(projectId: number) {
  return (await http.post<ApiEnvelope<ReleaseSummary>>(base(projectId))).data
    .data;
}
