export const CSRF_COOKIE = 'sluice_csrf';
export const CSRF_HEADER = 'X-Sluice-CSRF';

function csrfToken(): string | undefined {
  if (typeof document === 'undefined') return undefined;
  const prefix = `${CSRF_COOKIE}=`;
  const value = document.cookie.split('; ').find((cookie) => cookie.startsWith(prefix));
  return value ? decodeURIComponent(value.slice(prefix.length)) : undefined;
}

export function csrfHeaders(method = 'GET'): Record<string, string> {
  if (['GET', 'HEAD', 'OPTIONS'].includes(method.toUpperCase())) return {};
  const token = csrfToken();
  return token ? { [CSRF_HEADER]: token } : {};
}

export function csrfFetch(input: RequestInfo | URL, init: RequestInit = {}): Promise<Response> {
  const headers = new Headers(init.headers);
  for (const [name, value] of Object.entries(csrfHeaders(init.method))) headers.set(name, value);
  return fetch(input, { ...init, headers });
}
