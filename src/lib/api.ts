// Ancien choix pendant la premiere integration : import axios from "axios";
// Le projet n'ayant pas Axios dans ses dependances, ce client utilise fetch,
// deja disponible dans le navigateur, sans ajouter de bibliotheque inutile.

const ACCESS_TOKEN_KEY = "kametud_access_token";
const REFRESH_TOKEN_KEY = "kametud_refresh_token";
const API_BASE_URL = import.meta.env.VITE_API_URL ?? "http://localhost:8080";
const AUTH_STORAGE_MODE = import.meta.env.DEV && import.meta.env.VITE_AUTH_STORAGE === "session"
  ? "session"
  : "local";

function getAuthStorage() {
  if (typeof window === "undefined") return null;
  return AUTH_STORAGE_MODE === "session" ? window.sessionStorage : window.localStorage;
}

function getStorageValue(key: string) {
  return getAuthStorage()?.getItem(key) ?? null;
}

export const authTokenStorage = {
  mode: AUTH_STORAGE_MODE,
  getAccessToken: () => getStorageValue(ACCESS_TOKEN_KEY),
  getRefreshToken: () => getStorageValue(REFRESH_TOKEN_KEY),
  setTokens: (accessToken: string, refreshToken?: string) => {
    const storage = getAuthStorage();
    if (!storage) return;
    storage.setItem(ACCESS_TOKEN_KEY, accessToken);
    if (refreshToken) storage.setItem(REFRESH_TOKEN_KEY, refreshToken);
  },
  clear: () => {
    const storage = getAuthStorage();
    if (!storage) return;
    storage.removeItem(ACCESS_TOKEN_KEY);
    storage.removeItem(REFRESH_TOKEN_KEY);
  },
};

interface ApiResponse<T> {
  data: T;
  status: number;
}

export class ApiError extends Error {
  constructor(public readonly status: number, message: string) {
    super(message);
    this.name = "ApiError";
  }
}

async function request<T>(path: string, method: string, body?: unknown): Promise<ApiResponse<T>> {
  const controller = new AbortController();
  const runtimeWindow = typeof window !== "undefined" ? window : undefined;
  const timeout = runtimeWindow ? runtimeWindow.setTimeout(() => controller.abort(), 30_000) : undefined;
  const headers = new Headers();
  const token = authTokenStorage.getAccessToken();
  const isPublicReferenceRequest = method === "GET"
    && (path === "/api/categories" || path === "/api/cities");
  // Login/register/refresh doivent rester appelables avec un ancien token expiré.
  // Categories et villes sont publiques : leur chargement ne doit pas echouer
  // pendant que AuthContext remplace un ancien JWT au demarrage.
  if (token && !path.startsWith("/api/auth/") && !isPublicReferenceRequest) {
    headers.set("Authorization", `Bearer ${token}`);
  }

  let requestBody: BodyInit | undefined;
  if (body instanceof FormData) {
    requestBody = body;
  } else if (body !== undefined) {
    headers.set("Content-Type", "application/json");
    requestBody = JSON.stringify(body);
  }

  try {
    const response = await fetch(`${API_BASE_URL.replace(/\/$/, "")}${path}`, {
      method,
      headers,
      body: requestBody,
      signal: controller.signal,
    });
    const contentType = response.headers.get("content-type") ?? "";
    const text = await response.text();
    const data = contentType.includes("application/json") && text
      ? JSON.parse(text)
      : text;

    if (!response.ok) {
      const message = typeof data === "object" && data && "message" in data
        ? String((data as { message: unknown }).message)
        : `Erreur API ${response.status}`;
      throw new ApiError(response.status, message);
    }
    return { data: data as T, status: response.status };
  } finally {
    if (timeout !== undefined) runtimeWindow?.clearTimeout(timeout);
  }
}

/** Client HTTP unique : tous les appels passent par la Gateway. */
export const api = {
  defaults: { baseURL: API_BASE_URL },
  get: <T>(path: string) => request<T>(path, "GET"),
  post: <T = unknown>(path: string, body?: unknown) => request<T>(path, "POST", body),
  put: <T = unknown>(path: string, body?: unknown) => request<T>(path, "PUT", body),
  patch: <T = unknown>(path: string, body?: unknown) => request<T>(path, "PATCH", body),
  delete: <T = unknown>(path: string) => request<T>(path, "DELETE"),
};

export async function uploadApiFile(
  file: File,
  visibility: "public" | "private" = "public",
  category = "general",
  resourceId?: string,
) {
  const formData = new FormData();
  formData.append("file", file);
  formData.append("visibility", visibility);
  formData.append("category", category);
  if (resourceId) formData.append("resourceId", resourceId);
  const { data } = await api.post<{ filename: string; downloadUrl: string }>(
    "/api/storage/upload",
    formData,
  );

  return new URL(data.downloadUrl, API_BASE_URL).toString();
}
