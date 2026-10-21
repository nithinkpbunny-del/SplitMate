import { api } from "./client"
import type { Activity, InvitePreview, Period, Split, SplitStatus, SplitSummary } from "./types"

export type CreateSplitBody = {
  name: string
  description?: string
  emoji?: string
  currency?: string
  period?: Period
}

export type UpdateSplitBody = Partial<CreateSplitBody> & { clearPeriod?: boolean; status?: SplitStatus }

export const splitsApi = {
  list: () => api<SplitSummary[]>("/splits"),
  get: (id: string) => api<Split>(`/splits/${id}`),
  create: (body: CreateSplitBody) => api<Split>("/splits", { method: "POST", body }),
  update: (id: string, body: UpdateSplitBody) => api<Split>(`/splits/${id}`, { method: "PATCH", body }),
  remove: (id: string) => api<{ deleted: boolean; archived: boolean; message: string }>(`/splits/${id}`, { method: "DELETE" }),
  removeMember: (id: string, userId: string) => api<void>(`/splits/${id}/members/${userId}`, { method: "DELETE" }),
  regenerateInvite: (id: string) => api<Split>(`/splits/${id}/invite/regenerate`, { method: "POST" }),
  setInviteEnabled: (id: string, enabled: boolean) => api<Split>(`/splits/${id}/invite`, { method: "PATCH", body: { enabled } }),
  activity: (id: string, limit = 50) => api<Activity[]>(`/splits/${id}/activity`, { query: { limit } }),
}

export const invitesApi = {
  preview: (code: string) => api<InvitePreview>(`/invites/${code}`),
  join: (code: string) => api<Split>(`/invites/${code}/join`, { method: "POST" }),
}

export const activityApi = {
  recent: (limit = 12) => api<Activity[]>("/activity", { query: { limit } }),
}

export const splitKeys = {
  all: ["splits"] as const,
  detail: (id: string) => ["splits", id] as const,
  activity: (id: string) => ["splits", id, "activity"] as const,
  recentActivity: ["activity", "recent"] as const,
}
