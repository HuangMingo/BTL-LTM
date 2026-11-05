# Stage 1: Build dependencies
FROM node:21-alpine AS deps
WORKDIR /app
COPY package.json ./
RUN npm install --production

# Stage 2: Runtime
FROM node:21-alpine
WORKDIR /app

# Copy dependencies từ stage 1
COPY --from=deps /app/node_modules ./node_modules
COPY package.json ./
COPY src/ ./src/

# Environment variables (sẽ được override bởi docker-compose)
ENV WEB_PORT=8080
ENV CLIENT_TARGET_HOST=server
ENV CLIENT_TARGET_PORT=5000

EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=3s \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/ || exit 1

CMD ["node", "src/server.js"]