import { api } from "./client"
import type { User } from "./types"

export const authApi = {
  me: () => api<User>("/auth/me"),
  login: (body: { email: string; password: string }) => api<User>("/auth/login", { method: "POST", body }),
  register: (body: { name: string; email: string; password: string }) =>
    api<User>("/auth/register", { method: "POST", body }),
  logout: () => api<void>("/auth/logout", { method: "POST" }),
}

export const usersApi = {
  updateProfile: (body: { name?: string; avatarColor?: string }) => api<User>("/users/me", { method: "PATCH", body }),
  changePassword: (body: { currentPassword: string; newPassword: string }) =>
    api<void>("/users/me/password", { method: "PUT", body }),
  avatarColors: () => api<string[]>("/users/avatar-colors"),
}
