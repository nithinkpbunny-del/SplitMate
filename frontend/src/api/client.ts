/**
 * Typed fetch wrapper for the SplitMate API.
 *  - same-origin cookies (the JWT lives in an httpOnly cookie)
 *  - CSRF: echoes the XSRF-TOKEN cookie in the X-XSRF-TOKEN header on writes
 *  - errors become ApiError with the backend's { status, message, fieldErrors } shape
 *  - a 401 on a protected call notifies the app so it can send the user to /login
 */

export type FieldError = { field: string; message: string }

export type ErrorBody = {
  timestamp?: string
  status: number
  error?: string
  message: string
  path?: string
  fieldErrors?: FieldError[]
}

export class ApiError extends Error {
  readonly status: number
  readonly fieldErrors: FieldError[]

  constructor(body: ErrorBody) {
    super(body.message)
    this.name = "ApiError"
    this.status = body.status
    this.fieldErrors = body.fieldErrors ?? []
  }
}

type Method = "GET" | "POST" | "PUT" | "PATCH" | "DELETE"

type RequestOptions = {
  method?: Method
  body?: unknown
  signal?: AbortSignal
  query?: Record<string, string | number | boolean | undefined | null | string[]>
}

let unauthorizedHandler: (() => void) | null = null

export function onUnauthorized(handler: () => void) {
  unauthorizedHandler = handler
}

function readCookie(name: string): string | null {
  const match = document.cookie.split("; ").find((c) => c.startsWith(name + "="))
  return match ? decodeURIComponent(match.slice(name.length + 1)) : null
}

let csrfPromise: Promise<void> | null = null

async function ensureCsrfCookie(force = false): Promise<void> {
  if (!force && readCookie("XSRF-TOKEN")) return
  csrfPromise ??= fetch("/api/auth/csrf", { credentials: "same-origin" })
    .then(() => undefined)
    .finally(() => {
      csrfPromise = null
    })
  await csrfPromise
}

export function buildUrl(path: string, query?: RequestOptions["query"]): string {
  const url = new URL("/api" + path, window.location.origin)
  if (query) {
    for (const [key, value] of Object.entries(query)) {
      if (value === undefined || value === null || value === "") continue
      if (Array.isArray(value)) value.forEach((v) => url.searchParams.append(key, v))
      else url.searchParams.set(key, String(value))
    }
  }
  return url.pathname + url.search
}

async function send(path: string, options: RequestOptions, retried = false): Promise<Response> {
  const method = options.method ?? "GET"
  const headers: Record<string, string> = { Accept: "application/json" }
  if (options.body !== undefined) headers["Content-Type"] = "application/json"
  if (method !== "GET") {
    await ensureCsrfCookie()
    const token = readCookie("XSRF-TOKEN")
    if (token) headers["X-XSRF-TOKEN"] = token
  }

  let response: Response
  try {
    response = await fetch(buildUrl(path, options.query), {
      method,
      headers,
      body: options.body === undefined ? undefined : JSON.stringify(options.body),
      credentials: "same-origin",
      signal: options.signal,
    })
  } catch (err) {
    if (err instanceof DOMException && err.name === "AbortError") throw err
    throw new ApiError({ status: 0, message: "Can't reach SplitMate. Check your connection and try again." })
  }

  // A missing or rotated CSRF cookie: fetch a fresh one and retry once.
  if (response.status === 403 && method !== "GET" && !retried) {
    const body = await response.clone().json().catch(() => null)
    if (body?.message?.startsWith("Security token")) {
      await ensureCsrfCookie(true)
      return send(path, options, true)
    }
  }
  return response
}

async function toError(response: Response): Promise<ApiError> {
  const body = (await response.json().catch(() => null)) as ErrorBody | null
  return new ApiError(
    body && typeof body.message === "string"
      ? body
      : { status: response.status, message: response.statusText || "Request failed" },
  )
}

export async function api<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const response = await send(path, options)
  if (!response.ok) {
    const error = await toError(response)
    if (response.status === 401 && !path.startsWith("/auth/")) unauthorizedHandler?.()
    throw error
  }
  if (response.status === 204) return undefined as T
  const type = response.headers.get("Content-Type") ?? ""
  return (type.includes("json") ? response.json() : response.text()) as Promise<T>
}

/** Downloads a file response (PDF, CSV) through the browser. */
export async function download(path: string, fallbackName: string): Promise<void> {
  const response = await send(path, {})
  if (!response.ok) throw await toError(response)
  const blob = await response.blob()
  const disposition = response.headers.get("Content-Disposition") ?? ""
  const match = /filename="?([^";]+)"?/.exec(disposition)
  const name = match?.[1] ?? fallbackName
  const url = URL.createObjectURL(blob)
  const a = document.createElement("a")
  a.href = url
  a.download = name
  document.body.appendChild(a)
  a.click()
  a.remove()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}

export function primeCsrf(): void {
  void ensureCsrfCookie()
}

export function errorMessage(error: unknown): string {
  if (error instanceof ApiError) return error.message
  if (error instanceof Error) return error.message
  return "Something went wrong"
}
