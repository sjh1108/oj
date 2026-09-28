# syntax=docker/dockerfile:1
FROM node:22-slim
WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci --omit=dev

COPY src ./src

# Register (idempotent) then start the gateway connection.
CMD ["sh", "-c", "node src/register-commands.js && node src/index.js"]
