#!/bin/sh
# Gera /usr/share/nginx/html/env.js a partir das variáveis de ambiente do container.
# Permite trocar API_URL / WS_URL no deploy sem rebuildar a imagem.
set -eu

API_URL="${API_URL:-http://localhost:8080}"
WS_URL="${WS_URL:-http://localhost:8080/ws}"

cat > /usr/share/nginx/html/env.js <<EOF
window.__ENV__ = {
  API_URL: "${API_URL}",
  WS_URL: "${WS_URL}"
};
EOF

echo "[render-env] env.js gerado: API_URL=${API_URL} WS_URL=${WS_URL}"
