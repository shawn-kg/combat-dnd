/** An API error with the server's own message, ready to show to the user. */
export class ApiError extends Error {
  readonly status: number
  constructor(message: string, status: number) {
    super(message)
    this.status = status
  }
}

/** Uploads a file as multipart/form-data (field "file"). The browser sets the boundary itself. */
export async function upload<T = unknown>(path: string, file: File): Promise<T> {
  const form = new FormData()
  form.append('file', file)
  let res: Response
  try {
    res = await fetch(`/api${path}`, { method: 'POST', credentials: 'include', headers: { 'X-Requested-With': 'combat-dnd' }, body: form })
  } catch {
    throw new ApiError("Can't reach the server. Check your connection.", 0)
  }
  const data = await res.json().catch(() => null)
  if (!res.ok) throw new ApiError(data?.message ?? 'The upload failed. Try again.', res.status)
  return data as T
}

/** Calls the backend with cookies and the CSRF header. Returns the JSON body, or undefined for 204. */
export async function api<T = unknown>(method: string, path: string, body?: unknown): Promise<T> {
  let res: Response
  try {
    res = await fetch(`/api${path}`, {
      method,
      credentials: 'include',
      headers: { 'Content-Type': 'application/json', 'X-Requested-With': 'combat-dnd' },
      body: body === undefined ? undefined : JSON.stringify(body),
    })
  } catch {
    throw new ApiError("Can't reach the server. Check your connection.", 0)
  }
  if (res.status === 204) return undefined as T
  const data = await res.json().catch(() => null)
  if (!res.ok) throw new ApiError(data?.message ?? 'Something went wrong. Try again.', res.status)
  return data as T
}
