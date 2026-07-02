# Tatakai Manager

Sistema web para **gerenciar NPCs de RPG de mesa**: fichas de NPC, agendamento de
interações em slots de *TimeSkip*, controle de tempo de jogo e logs narrativos — com
sincronização **em tempo real** entre Mestre e jogadores.

Monorepo com backend (Spring Boot), frontend (React) e a orquestração de deploy num
repositório só.

---

## Funcionalidades

- **Autenticação** por JWT (cadastro/login).
- **Campanhas** com papéis por membro (Mestre / jogador) e convite de membros.
- **NPCs**: acervo reutilizável, associação por campanha, ficha completa (atributos,
  traços, specs, conhecimentos, tipos de interação), imagem (retrato) e visibilidade
  controlada pelo Mestre.
- **TimeSkip**: 4 slots de interação por dia por NPC (sem conflito), avanço de dia e
  encerramento, com histórico.
- **Agendamento em tempo real**: grade de NPCs × slots sincronizada via **WebSocket**
  (STOMP) — o que um cliente agenda aparece na hora para os outros.
- **Logs narrativos**: feed da campanha; jogadores vinculam o log a um agendamento,
  Mestre narra livremente. Sanitização de XSS no backend.
- **UI**: tema claro/escuro, layout responsivo, toasts e estados de carregamento.

---

## Stack

| Camada    | Tecnologias |
|-----------|-------------|
| Backend   | Java 21, Spring Boot 3.3 (Web, Data JPA, Security, WebSocket), PostgreSQL |
| Frontend  | React + Vite, React Router, Tailwind CSS v4, STOMP/SockJS |
| Infra     | Docker + Docker Compose; Caddy (TLS/WSS) para produção |

---

## Estrutura do monorepo

```
TatakaiManager/
├── backend/                 # API Spring Boot (repo original preservado via git subtree)
├── frontend/                # SPA React + Vite
├── docker-compose.yml       # ambiente de desenvolvimento
├── docker-compose.prod.yml  # produção (Caddy à frente; back/DB só na rede interna)
├── Caddyfile                # reverse proxy com TLS/WSS automáticos
├── DEPLOY.md                # runbook de deploy (Oracle Cloud Always Free — custo zero)
└── HANDOFF.md               # estado do projeto / notas de continuidade
```

---

## Como rodar

### Tudo via Docker (recomendado)

```bash
cp .env.example .env      # ajuste se quiser
docker compose up --build
```

| Serviço  | URL                    |
|----------|------------------------|
| Frontend | http://localhost:3001  |
| Backend  | http://localhost:8080  |
| Postgres | localhost:5433         |

As portas de host são configuráveis por env (evitam conflito com serviços locais).

### Backend isolado (IntelliJ / Maven)

O backend exige **JDK 21**:

```bash
cd backend
./mvnw test                 # suíte de testes
./mvnw spring-boot:run       # requer Postgres + envs (DB_URL, DB_USER, ...)
```

> A config fica em `backend/src/main/resources/application.properties`, que **não é
> versionado**. Use `application.properties.example` como base
> (`cp application.properties.example application.properties`).

### Frontend isolado

```bash
cd frontend
npm install
npm run dev                  # servidor de desenvolvimento Vite
```

---

## Configuração (variáveis de ambiente)

Tudo é injetado por env — nada de endpoint hard-coded.

| Variável | Onde | Descrição |
|----------|------|-----------|
| `DB_URL` / `DB_USER` / `DB_PASS` | backend | conexão com o Postgres |
| `JWT_SECRET` | backend | segredo do JWT (≥ 32 caracteres em produção) |
| `FRONTEND_URL` | backend | origem liberada para CORS e handshake WebSocket |
| `DDL_AUTO` | backend | `update` no 1º boot (cria schema), depois `validate` |
| `API_URL` / `WS_URL` | frontend | endpoints que o navegador usa (injetados em runtime) |

O frontend gera `/env.js` na subida do container a partir de `API_URL`/`WS_URL`, então
dá para trocar o endpoint **sem rebuildar a imagem**.

---

## Deploy

Runbook completo em **[DEPLOY.md](DEPLOY.md)** — alvo é a **Oracle Cloud Always Free**
(VM ARM Ampere, custo zero e sem expiração), com Caddy provendo HTTPS e WebSocket seguro
(WSS) automaticamente. O deploy é um único `git clone` + `docker compose`.
