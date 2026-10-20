/**
 * Client HTTP du backend. Tout passe par /api (relayé par Next.js) : les cookies d'authentification sont httpOnly,
 * le seul cookie lisible est XSRF-TOKEN, dont la valeur est renvoyée dans X-XSRF-TOKEN sur chaque écriture.
 * Une réponse 401 déclenche UN rafraîchissement de session puis rejoue la requête ; si le rafraîchissement échoue,
 * l'utilisateur est renvoyé à la connexion.
 */

export class ApiError extends Error {
  constructor(
    public status: number,
    public code: string | undefined,
    message: string,
    public errors?: Record<string, string>,
  ) {
    super(message);
  }
}

export type Query = Record<string, string | number | boolean | null | undefined>;

export interface RequestOptions {
  method?: "GET" | "POST" | "PUT" | "PATCH" | "DELETE";
  query?: Query;
  body?: unknown;
  form?: FormData;
  /** Renvoie le corps brut (téléchargements). */
  blob?: boolean;
  signal?: AbortSignal;
}

const MUTATING = new Set(["POST", "PUT", "PATCH", "DELETE"]);
let onUnauthorized: (() => void) | null = null;
let refreshing: Promise<boolean> | null = null;

/** Appelé quand la session ne peut plus être rétablie. */
export function setUnauthorizedHandler(fn: (() => void) | null) {
  onUnauthorized = fn;
}

export function buildQuery(query?: Query): string {
  if (!query) return "";
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(query)) {
    if (value === undefined || value === null || value === "") continue;
    params.append(key, String(value));
  }
  const s = params.toString();
  return s ? `?${s}` : "";
}

export function readCookie(name: string, source: string = typeof document === "undefined" ? "" : document.cookie): string | undefined {
  for (const part of source.split("; ")) {
    const i = part.indexOf("=");
    if (i > 0 && part.slice(0, i) === name) return decodeURIComponent(part.slice(i + 1));
  }
  return undefined;
}

async function ensureCsrf(): Promise<string | undefined> {
  let token = readCookie("XSRF-TOKEN");
  if (!token) {
    await fetch("/api/auth/csrf", { credentials: "same-origin" });
    token = readCookie("XSRF-TOKEN");
  }
  return token;
}

async function send(path: string, opts: RequestOptions): Promise<Response> {
  const method = opts.method ?? "GET";
  const headers: Record<string, string> = { Accept: "application/json" };
  let body: BodyInit | undefined;
  if (opts.form) {
    body = opts.form; // le navigateur fixe le Content-Type multipart avec sa frontière
  } else if (opts.body !== undefined) {
    headers["Content-Type"] = "application/json";
    body = JSON.stringify(opts.body);
  }
  if (MUTATING.has(method)) {
    const token = await ensureCsrf();
    if (token) headers["X-XSRF-TOKEN"] = token;
  }
  return fetch(`/api${path}${buildQuery(opts.query)}`, { method, headers, body, credentials: "same-origin", signal: opts.signal });
}

function refreshSession(): Promise<boolean> {
  refreshing ??= (async () => {
    try {
      const res = await send("/auth/refresh", { method: "POST" });
      return res.ok;
    } catch {
      return false;
    } finally {
      setTimeout(() => (refreshing = null), 0);
    }
  })();
  return refreshing;
}

async function toError(res: Response): Promise<ApiError> {
  let code: string | undefined;
  let detail = res.statusText;
  let errors: Record<string, string> | undefined;
  try {
    const data = await res.json();
    code = data.code;
    detail = data.detail ?? data.title ?? detail;
    errors = data.errors;
  } catch {
    /* corps vide ou non JSON */
  }
  return new ApiError(res.status, code, detail, errors);
}

export async function api<T = void>(path: string, opts: RequestOptions = {}): Promise<T> {
  let res = await send(path, opts);
  if (res.status === 401 && !path.startsWith("/auth/")) {
    if (await refreshSession()) {
      res = await send(path, opts);
    }
    if (res.status === 401) {
      onUnauthorized?.();
    }
  }
  if (!res.ok) throw await toError(res);
  if (opts.blob) return (await res.blob()) as T;
  if (res.status === 204) return undefined as T;
  const text = await res.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

/** Télécharge un fichier renvoyé par l'API et le propose à l'utilisateur. */
export async function download(path: string, query: Query | undefined, filename: string): Promise<void> {
  const blob = await api<Blob>(path, { query, blob: true });
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  a.remove();
  URL.revokeObjectURL(url);
}
