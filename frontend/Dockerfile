FROM node:24.15.0-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY angular.json tsconfig.json ./
COPY src src
COPY tests tests
RUN npm test && npm run build
FROM nginxinc/nginx-unprivileged:1.30.4-alpine
USER root
RUN apk upgrade --no-cache
COPY nginx.conf /etc/nginx/nginx.conf
COPY --from=build /app/dist/web/browser /usr/share/nginx/html
USER 101
EXPOSE 8080
