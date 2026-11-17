# The frontend image of the local deployment: the built pages, served by nginx, which also passes /api on to
# the backend (see Deployment in docs/backend/backend-design.md). Built by deploy/compose.yaml.

FROM node:22-alpine AS build
WORKDIR /src
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /src/dist /usr/share/nginx/html
