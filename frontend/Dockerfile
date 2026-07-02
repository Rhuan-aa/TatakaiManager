# ---- build ----
FROM node:20-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

# ---- runtime (nginx) ----
FROM nginx:alpine AS runtime

# Config SPA (fallback de rotas para index.html)
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Bundle estático
COPY --from=build /app/dist /usr/share/nginx/html

# Gera env.js a partir das variáveis de ambiente na subida do container.
# A imagem nginx executa automaticamente os scripts em /docker-entrypoint.d/.
COPY docker-entrypoint.d/40-render-env.sh /docker-entrypoint.d/40-render-env.sh
RUN chmod +x /docker-entrypoint.d/40-render-env.sh

EXPOSE 80
