# Translately webapp — static nginx container image.
#
# Build from the repo root:
#   docker build -f infra/docker/webapp.Dockerfile -t translately-webapp:dev .
#
# nginx serves the built SPA and proxies /api/* to the backend.

# syntax=docker/dockerfile:1.7

# ---------- build stage ----------
FROM node:25-alpine AS build
WORKDIR /workspace

RUN corepack enable

# Copy only what pnpm needs to resolve first — maximizes cache reuse
COPY pnpm-workspace.yaml pnpm-lock.yaml package.json ./
COPY webapp/package.json ./webapp/package.json
RUN --mount=type=cache,target=/root/.local/share/pnpm/store \
    pnpm install --frozen-lockfile --filter @translately/webapp...

COPY webapp ./webapp
RUN pnpm --filter @translately/webapp build

# ---------- runtime stage ----------
FROM nginx:1.27-alpine AS runtime

COPY infra/docker/nginx.conf /etc/nginx/nginx.conf
COPY --from=build /workspace/webapp/dist /usr/share/nginx/html

RUN adduser -D -u 10001 -g 'translately' translately \
 && chown -R translately:translately /var/cache/nginx /var/log/nginx /etc/nginx /usr/share/nginx/html \
 && touch /run/nginx.pid \
 && chown translately:translately /run/nginx.pid

USER 10001:10001

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --retries=3 \
    CMD wget -qO- http://127.0.0.1:8080/healthz || exit 1

CMD ["nginx", "-g", "daemon off;"]
