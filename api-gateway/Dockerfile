# ============================================================
# Dockerfile for Node.js API Gateway
# ============================================================
# This is a simple single-stage build because Node.js apps
# don't need a separate "compile" step like Java.
#
# The API Gateway receives all requests from the frontend
# and forwards them to the correct backend service:
#   /api/genai/* → GenAI Python service
#   /api/*       → Spring Boot backend
# ============================================================


# Use the official Node.js 20 image (Alpine = tiny Linux)
FROM node:20-alpine

# Set working directory
WORKDIR /app

# Copy package files first (Docker layer caching)
COPY package.json package-lock.json ./

# Install dependencies
# --omit=dev → skip devDependencies (we only need production packages)
RUN npm ci --omit=dev

# Copy the rest of the code (just server.js and .env basically)
COPY . .

# The gateway runs on port 8080
EXPOSE 8080

# Start the server
CMD ["node", "server.js"]
