# ========================================
# Frontend Dockerfile — Multi-stage build
# ========================================
# Stage 1: Build with Node
# Stage 2: Serve with Nginx
# ========================================

# --- Build Stage ---
FROM node:20-alpine AS builder
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

# --- Serve Stage ---
FROM nginx:alpine
# Copy built files to nginx
COPY --from=builder /app/dist /usr/share/nginx/html
# Custom nginx config for SPA routing
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
