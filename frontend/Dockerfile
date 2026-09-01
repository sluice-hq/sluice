# syntax=docker/dockerfile:1.7
FROM node:20-bookworm-slim AS dependencies
WORKDIR /workspace/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci

FROM node:20-bookworm-slim AS build
WORKDIR /workspace/frontend
COPY --from=dependencies /workspace/frontend/node_modules ./node_modules
COPY frontend ./
COPY LICENSE THIRD_PARTY_NOTICES.md /workspace/
RUN npm run build

FROM node:20-bookworm-slim AS runtime
WORKDIR /app
ENV NODE_ENV=production \
    HOSTNAME=0.0.0.0 \
    PORT=3000
COPY --from=build --chown=node:node /workspace/frontend/.next/standalone ./
COPY --from=build --chown=node:node /workspace/frontend/.next/static ./.next/static
COPY --from=build --chown=node:node /workspace/frontend/public ./public
COPY --from=build --chown=node:node /workspace/LICENSE /workspace/THIRD_PARTY_NOTICES.md /licenses/
COPY --from=build --chown=node:node /workspace/frontend/.next/THIRD_PARTY_LICENSES.txt /licenses/
USER node
EXPOSE 3000
HEALTHCHECK --interval=30s --timeout=5s --start-period=20s --retries=3 \
    CMD ["node", "-e", "fetch('http://127.0.0.1:3000/').then(response => { if (!response.ok) process.exit(1) }).catch(() => process.exit(1))"]
CMD ["node", "server.js"]
