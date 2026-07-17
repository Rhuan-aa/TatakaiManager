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
- **TimeSkip**: 4 slots de interação por dia + um slot extra diário, avanço de dia e
  encerramento, com histórico.
- **Agendamento em tempo real**: grade de NPCs × slots sincronizada via **WebSocket**
  (STOMP) — o que um cliente agenda aparece na hora para os outros.
- **Ações solo**: jogador ocupa um slot sem NPC (treino, estudo ou ação geral), e o
  Mestre pode criar **atividades customizadas por TimeSkip** com custo próprio.
- **Custo em pontos de ociosidade**: uma ação de custo N ocupa N slots consecutivos do
  dia; ações de custo zero vão para o slot extra (limite de uma por jogador por dia).
- **Logs narrativos**: feed da campanha; jogadores vinculam o log a um agendamento,
  Mestre narra livremente e pode editar/excluir. Sanitização de XSS no backend.
- **UI**: tema claro/escuro, layout responsivo, toasts e estados de carregamento.

---

## Stack

| Camada    | Tecnologias |
|-----------|-------------|
| Backend   | Java 21, Spring Boot 3.3 (Web, Data JPA, Security, WebSocket) |
| Bancos    | PostgreSQL (dados relacionais + imagem de NPC) e MongoDB (ficha textual de NPC) |
| Frontend  | React + Vite, React Router, Tailwind CSS v4, STOMP/SockJS |
| Infra     | Docker + Docker Compose; Caddy (TLS/WSS) para self-host |

---

## Estrutura do monorepo

```
TatakaiManager/
├── backend/                 # API Spring Boot (repo original preservado via git subtree)
├── frontend/                # SPA React + Vite
├── docker-compose.yml       # ambiente de desenvolvimento (Postgres + Mongo + back + front)
├── docker-compose.prod.yml  # self-host (Caddy à frente; back/DB só na rede interna)
└── Caddyfile                # reverse proxy com TLS/WSS automáticos (self-host)
```

---

## Rodando localmente (passo a passo)

### Pré-requisitos

- **Docker** com o plugin **Docker Compose** (`docker compose version` deve funcionar).
- **Git**.

Nada mais — Java, Node, Postgres e Mongo sobem dentro dos containers.

### 1. Clonar o repositório

```bash
git clone https://github.com/Rhuan-aa/TatakaiManager.git
cd TatakaiManager
```

### 2. Criar o arquivo de ambiente

```bash
cp .env.example .env
```

Os defaults já funcionam para desenvolvimento local. Ajuste apenas se alguma porta
estiver ocupada na sua máquina (ver tabela de variáveis abaixo).

### 3. Subir a stack

```bash
docker compose up --build
```

Isso constrói e sobe **4 containers**: `postgres`, `mongo`, `backend` e `frontend`.
Na primeira subida o build do backend (Maven) demora alguns minutos; o backend espera
os bancos ficarem saudáveis antes de iniciar e cria o schema automaticamente
(`DDL_AUTO=update`).

### 4. Acessar

| Serviço  | URL                   |
|----------|-----------------------|
| Frontend | http://localhost:3001 |
| Backend  | http://localhost:8080 |
| Postgres | localhost:5433        |
| MongoDB  | interno ao compose (sem porta publicada por padrão) |

Abra http://localhost:3001, crie uma conta e uma campanha — pronto.

### 5. Parar / resetar

```bash
docker compose down        # para os containers (dados preservados nos volumes)
docker compose down -v     # para e APAGA os dados (Postgres + Mongo)
```

---

## Rodando as partes isoladamente (desenvolvimento)

### Backend (IntelliJ / Maven)

O backend exige **JDK 21**:

```bash
cd backend
./mvnw test                  # suíte de testes (não requer bancos)
./mvnw spring-boot:run       # requer Postgres + Mongo + envs (DB_URL, MONGO_URI, ...)
```

Para os bancos, o mais simples é reaproveitar os do compose
(`docker compose up postgres mongo`) apontando `DB_URL` para `localhost:5433` e
`MONGO_URI` para `mongodb://localhost:27017/tatakai` (publique a porta do Mongo se
precisar).

> A config fica em `backend/src/main/resources/application.properties`, que **não é
> versionado**. Use `application.properties.example` como base
> (`cp application.properties.example application.properties`).

### Frontend

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
| `MONGO_URI` | backend | conexão com o MongoDB (com o nome do banco no path) |
| `JWT_SECRET` | backend | segredo do JWT (≥ 32 caracteres em produção) |
| `FRONTEND_URL` | backend | origem liberada para CORS e handshake WebSocket |
| `DDL_AUTO` | backend | `update` no 1º boot (cria schema), depois `validate` |
| `API_URL` / `WS_URL` | frontend | endpoints que o navegador usa (injetados em runtime) |
| `DB_HOST_PORT` / `BACKEND_HOST_PORT` / `FRONTEND_HOST_PORT` | compose | portas publicadas no host (mude se houver conflito) |

O frontend gera `/env.js` na subida do container a partir de `API_URL`/`WS_URL`, então
dá para trocar o endpoint **sem rebuildar a imagem**.

---

## Deploy

Como toda a configuração é por env, o projeto roda em qualquer infraestrutura. Dois
modelos suportados:

- **Serviços gerenciados** (modelo usado em produção): backend como container, frontend
  como site estático e bancos Postgres/MongoDB gerenciados — basta apontar as variáveis
  de ambiente de cada parte.
- **Self-host**: qualquer servidor Linux com Docker, via `docker-compose.prod.yml` com
  **Caddy** provendo HTTPS e WebSocket seguro (WSS) automaticamente — um único
  `git clone` + `docker compose -f docker-compose.prod.yml up --build -d`.

> O runbook detalhado de produção (provisionamento, DNS, credenciais, backup) é mantido
> **fora do repositório**, apenas localmente.
