# Sluice dashboard

The dashboard is part of the Sluice monorepo. Use the repository-root [README](../README.md) for prerequisites, Docker startup, backend configuration, authentication, API usage, and verification commands.

From this directory:

```powershell
npm ci
npm run dev
```

Open [http://localhost:3000](http://localhost:3000). The Next.js backend-for-frontend proxies authenticated API requests to the Spring Boot backend using `API_BASE_URL`.
