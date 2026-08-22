export type AuthMode = 'apiKey' | 'jwt';

export interface AuthConfig {
  mode: AuthMode;
  apiKey?: string;
  token?: string;
  projectId?: string;
}

const AUTH_STORAGE_KEY = 'sluice.auth';
export const AUTH_CHANGED_EVENT = 'sluice-auth-changed';

export function getAuthConfig(): AuthConfig | null {
  if (typeof window === 'undefined') return null;

  const stored = window.sessionStorage.getItem(AUTH_STORAGE_KEY);
  if (!stored) return null;

  try {
    return JSON.parse(stored) as AuthConfig;
  } catch {
    window.sessionStorage.removeItem(AUTH_STORAGE_KEY);
    return null;
  }
}

export function saveAuthConfig(config: AuthConfig): void {
  window.sessionStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify(config));
  window.dispatchEvent(new Event(AUTH_CHANGED_EVENT));
}

export function clearAuthConfig(): void {
  window.sessionStorage.removeItem(AUTH_STORAGE_KEY);
  window.dispatchEvent(new Event(AUTH_CHANGED_EVENT));
}

export function getAuthHeaders(): Record<string, string> {
  const config = getAuthConfig();
  if (!config) return {};

  if (config.mode === 'apiKey' && config.apiKey?.trim()) {
    return { 'X-API-Key': config.apiKey.trim() };
  }

  if (config.mode === 'jwt' && config.token?.trim() && config.projectId?.trim()) {
    return {
      Authorization: `Bearer ${config.token.trim()}`,
      'X-Project-ID': config.projectId.trim(),
    };
  }

  return {};
}

export function hasAuthConfig(): boolean {
  return Object.keys(getAuthHeaders()).length > 0;
}
