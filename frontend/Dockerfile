# ============================================================
# Dockerfile for React Frontend
# ============================================================
# This also uses a two-stage build:
#   Stage 1 → install packages + build the React app (npm run build)
#   Stage 2 → serve the built HTML/CSS/JS files with Nginx
#
# Nginx is a lightweight web server — perfect for serving
# static files like a React production build.
# ============================================================


# ---- STAGE 1: BUILD ----
# Start with a Node.js image to build our React app
FROM node:20-alpine AS build

# Set working directory
WORKDIR /app

# Copy package files first (for Docker layer caching)
COPY package.json package-lock.json ./

# Install all npm dependencies
RUN npm ci

# Copy the rest of the frontend source code
COPY . .

# Build the React app for production
# Vite will create optimized files in the /app/dist folder
RUN npm run build


# ---- STAGE 2: SERVE WITH NGINX ----
# Start fresh with a tiny Nginx image
FROM nginx:alpine

# Copy our built React files into Nginx's default serving folder
COPY --from=build /app/dist /usr/share/nginx/html

# Copy our custom Nginx config (handles React Router properly)
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Nginx listens on port 80 by default
EXPOSE 80

# Start Nginx (daemon off = run in foreground so Docker can track it)
CMD ["nginx", "-g", "daemon off;"]
