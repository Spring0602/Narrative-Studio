import { http, type ApiEnvelope } from "./http";

export interface ProjectSummary {
  id: number;
  name: string;
  description?: string;
  ownerId: number;
  status: "ACTIVE" | "ARCHIVED";
  memberRole: "OWNER" | "EDITOR" | "TESTER";
  updatedAt: string;
}

export async function listProjects() {
  const { data } = await http.get<ApiEnvelope<ProjectSummary[]>>("/projects");
  return data.data;
}

export async function createProject(payload: {
  name: string;
  description?: string;
}) {
  const { data } = await http.post<ApiEnvelope<ProjectSummary>>(
    "/projects",
    payload,
  );
  return data.data;
}

export async function getProject(projectId: number) {
  const { data } = await http.get<ApiEnvelope<ProjectSummary>>(
    `/projects/${projectId}`,
  );
  return data.data;
}

export async function updateProject(
  projectId: number,
  payload: { name: string; description?: string },
) {
  const { data } = await http.put<ApiEnvelope<ProjectSummary>>(
    `/projects/${projectId}`,
    payload,
  );
  return data.data;
}

export async function archiveProject(projectId: number) {
  await http.delete<ApiEnvelope<null>>(`/projects/${projectId}`);
}
