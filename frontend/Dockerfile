# ---------- Build stage ----------
FROM node:24-alpine AS build
WORKDIR /app
COPY package.json package-lock.json* ./
RUN npm install
COPY . .
ARG VITE_API_BASE_URL=/api
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL
ARG VITE_DEMO_PREFILL=false
ENV VITE_DEMO_PREFILL=$VITE_DEMO_PREFILL
RUN npm run build

# ---------- Runtime stage ----------
FROM nginx:1.31-alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
