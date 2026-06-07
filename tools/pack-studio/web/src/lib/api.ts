import type { Project, ProjectDetail, ProjectMetadata, AudioMappingResult, ValidationResult, BuildResult } from "@/types";

const API_BASE = "/api";

async function request<T>(url: string, options: RequestInit = {}): Promise<T> {
  const response = await fetch(url, {
    headers: { "Content-Type": "application/json", ...(options.headers || {}) },
    ...options,
  });
  const data = await response.json().catch(() => ({}));
  if (!response.ok) {
    throw new Error(data.error || `Request failed: ${response.status}`);
  }
  return data as T;
}

export const api = {
  health: () => request<{ status: string }>(`${API_BASE}/health`),

  listProjects: () => request<Project[]>(`${API_BASE}/projects`),

  createProject: (name: string) =>
    request<Project>(`${API_BASE}/projects`, {
      method: "POST",
      body: JSON.stringify({ name }),
    }),

  getProject: (id: string) => request<ProjectDetail>(`${API_BASE}/projects/${id}`),

  importText: (id: string, metadata: ProjectMetadata, text: string) =>
    request<{ status: string }>(`${API_BASE}/projects/${id}/import-text`, {
      method: "POST",
      body: JSON.stringify({ metadata, text }),
    }),

  normalize: (id: string) =>
    request<{ chapters: number; paragraphs: number; sentences: number }>(
      `${API_BASE}/projects/${id}/normalize`,
      { method: "POST" }
    ),

  exportSentences: (id: string) =>
    request<{ fileName: string; relativePath: string; sentenceCount: number }>(
      `${API_BASE}/projects/${id}/export-sentences`,
      { method: "POST" }
    ),

  importNarration: (id: string, sourceDir: string) =>
    request<AudioMappingResult>(`${API_BASE}/projects/${id}/import-narration`, {
      method: "POST",
      body: JSON.stringify({ sourceDir }),
    }),

  validate: (id: string) =>
    request<ValidationResult>(`${API_BASE}/projects/${id}/validate`, { method: "POST" }),

  build: (id: string) =>
    request<BuildResult>(`${API_BASE}/projects/${id}/build`, { method: "POST" }),

  downloadArtifact: (id: string) => `${API_BASE}/projects/${id}/artifact`,
};
