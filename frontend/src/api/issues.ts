import { http, type ApiEnvelope } from "./http";
import type { Page } from "./playtests";

export type IssueStatus = "OPEN" | "RESOLVED" | "IGNORED";
export type IssueSeverity = "ERROR" | "WARNING";
export type IssueType =
  | "START_COUNT"
  | "ISOLATED"
  | "UNREACHABLE"
  | "DEAD_END"
  | "CYCLE"
  | "BROKEN_REFERENCE"
  | "ENDING_OUTGOING";

export interface DetectedIssue {
  id: number;
  projectId: number;
  issueType: IssueType;
  severity: IssueSeverity;
  targetType: "PROJECT" | "GRAPH" | "NODE" | "CHOICE";
  targetId?: number | null;
  message: string;
  status: IssueStatus;
  detectedAt: string;
  resolvedAt?: string | null;
}

const base = (projectId: number) => `/projects/${projectId}/issues`;

export async function listIssues(projectId: number, page = 1, size = 100) {
  return (
    await http.get<ApiEnvelope<Page<DetectedIssue>>>(base(projectId), {
      params: { page, size },
    })
  ).data.data;
}

export async function runAnalysis(projectId: number) {
  return (
    await http.post<ApiEnvelope<DetectedIssue[]>>(
      `/projects/${projectId}/analysis-runs`,
    )
  ).data.data;
}

export async function updateIssueStatus(
  projectId: number,
  issueId: number,
  status: IssueStatus,
) {
  return (
    await http.put<ApiEnvelope<DetectedIssue>>(
      `${base(projectId)}/${issueId}/status`,
      { status },
    )
  ).data.data;
}
