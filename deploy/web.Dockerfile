# syntax=docker/dockerfile:1
# Production web image: the frontend build served by Caddy, which also
# terminates HTTPS and proxies /api to the backend. Build from the repo root:
#   docker build -f deploy/web.Dockerfile -t watchmark-web:<tag> .

FROM node:24-slim AS build
WORKDIR /src/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci
COPY frontend/ ./
RUN npm run build

FROM caddy:2.10-alpine
COPY deploy/Caddyfile /etc/caddy/Caddyfile
# Fail the build, not the server start, on a Caddyfile mistake.
RUN SITE_ADDRESS=example.org caddy validate --config /etc/caddy/Caddyfile --adapter caddyfile
COPY --from=build /src/frontend/dist /srv
