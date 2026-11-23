# Tatakai Manager

Sistema web para **mesas de RPG de luta**: ficha digital de jogadores e NPCs, Construção
Inicial guiada, Criação Marcial, motor de combate, rolagens no servidor, agendamento de
interações em slots de *TimeSkip* e logs narrativos — com sincronização **em tempo real**
entre Mestre e jogadores.

Monorepo com backend (Spring Boot), frontend (React) e a orquestração de deploy num
repositório só.

---

## Funcionalidades

**Campanha e agenda**
- **Autenticação** por JWT (cadastro/login) e **campanhas** com papéis por membro (Mestre / jogador).
- **NPCs**: acervo reutilizável, associação por campanha, retrato, visibilidade e ficha própria
  (leve ou pronta para combate), com o que o jogador vê liberado **por seção** pelo Mestre.
- **TimeSkip** em dois modos: *Período Ocioso* (4 pontos de ócio por dia + slot extra, avanço de
  dia) e *TimeSkip* (salto de vários dias com envio para aprovação). Slots por dia configuráveis.
- **Agendamento em tempo real** (WebSocket/STOMP): interações com NPC, ações solo (Treino,
  Estudo, Repouso, Forja…) e atividades customizadas; custo N ocupa N slots consecutivos.
- **Logs narrativos** em feed: Mestre narra livremente; jogador registra o próprio agendamento
  ou pede um log livre, que o Mestre aprova. Texto sanitizado contra XSS no backend.
- **Aprovações**: uma aba do Mestre reúne tudo que espera decisão (fichas, artes, técnicas,
  logs, envios do TimeSkip…).

**Ficha digital**
- **Construção Inicial** passo a passo (identidade, traços, teste de sangue, atributos, pontos
  livres), com toda rolagem feita e registrada no servidor; ou criação livre, se a regra permitir.
- Ficha em abas: recursos (HP/SP/PQ), Desgaste, atributos e limitadores (radar), traços e clãs,
  boneco de ferimentos desenhado pelo Mestre, inventário com itens equipados, artes marciais,
  progressão (Keiken, PS, Instintos, Rivais) e log de atividade auditável.
- **Treino e Repouso** processados pela agenda; **consumíveis** fora de combate com Regra de Dose.
- **Exportar/importar** a ficha (`.tatakai.json`) e imprimir/PDF.

**Artes marciais e combate**
- **Catálogo** global + extensões por campanha: traços, clãs, artes, armas, equipamentos,
  consumíveis, instintos e marcos (com imagem opcional por item).
- **Criação Marcial**: editor de técnicas e bases com cálculo de Complexidade; Caderno e Forja.
- **Motor de combate**: iniciativa, turnos e PA, ataque e reação, guarda e base, interação
  marcial, Adrenalina, desmaios, condições, Agarre, armas e proteções.
- **Rolagens** sempre no servidor, em canais (público, privado do Mestre, personalizados), com
  animação de revelação.

**UI**: estilo quadrinhos/brutalista, tema claro/escuro, responsiva, efeitos de condição
(com opção de efeitos reduzidos).

---

## Stack

| Camada    | Tecnologias |
|-----------|-------------|
| Backend   | Java 21, Spring Boot 3.3 (Web, Data JPA, Security, WebSocket) |
| Bancos    | PostgreSQL (fichas, agenda, combate, rolagens, logs, imagens) e MongoDB (NPCs e catálogos) |
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
├── Caddyfile                # reverse proxy com TLS/WSS automáticos (self-host)
└── scripts/                 # dados de teste, seeds do catálogo e migração de produção
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

Para já ter dados de teste (campanha "Mesa de Testes" com Mestre, jogadores, NPCs, fichas e
TimeSkip), rode com a stack no ar:

```bash
python3 scripts/dev_fixtures.py --api http://localhost:8080   # reexecutável
```

Contas criadas (senha `tatakai123`): `mestre@tatakai.dev`, `jogador1@tatakai.dev` (ficha
aprovada), `jogador2@tatakai.dev` (em construção) e `jogador3@tatakai.dev` (sem ficha).
Para o Mestre editar o catálogo global, coloque o e-mail dele em `CATALOG_EDITORS` no `.env`.

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
./mvnw test                  # ~940 testes unitários (Mockito), não requer bancos
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
npm run dev                  # servidor de desenvolvimento Vite (:5173)
npm run build && npm run lint
```

O `FRONTEND_URL` do backend precisa incluir a origem do Vite (`http://localhost:5173`).

---

## Configuração (variáveis de ambiente)

Tudo é injetado por env — nada de endpoint hard-coded.

| Variável | Onde | Descrição |
|----------|------|-----------|
| `DB_URL` / `DB_USER` / `DB_PASS` | backend | conexão com o Postgres |
| `MONGO_URI` | backend | conexão com o MongoDB (com o nome do banco no path) |
| `JWT_SECRET` | backend | segredo do JWT (≥ 32 caracteres em produção) |
| `FRONTEND_URL` | backend | origem liberada para CORS e handshake WebSocket |
| `DDL_AUTO` | backend | `update` em desenvolvimento (cria tabelas/colunas); `validate` em produção |
| `CATALOG_EDITORS` | backend | e-mails (separados por vírgula) que podem editar o catálogo global |
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

### Atualizando um banco existente

Com `DDL_AUTO=validate` o Hibernate **não** cria tabelas nem colunas. Por isso o backend roda
sozinho, a cada boot e antes do Hibernate, o script idempotente
`backend/src/main/resources/db/upgrade.sql`: ele cria o que falta, recoloca chaves e índices e
remove os CHECKs antigos de enum. **Basta subir a versão nova** — não há passo manual no deploy.
Para desligar (ex.: aplicar à mão), use `APP_SCHEMA_UPGRADE=false`.

Ao mudar entidades, regenere o script com `scripts/gen_upgrade_sql.py` (passo a passo no topo do
arquivo) no mesmo commit. Backup antes de versões grandes continua recomendado:

```bash
# PG = URL do Postgres no formato do psql (postgresql://usuario:senha@host:5432/banco)
pg_dump "$PG" > backup.sql
```

> O runbook detalhado de produção (provisionamento, DNS, credenciais, backup) é mantido
> **fora do repositório**, apenas localmente.
