# AGENTS.md — Referência técnica do Tatakai Manager

> Documento para **agentes de IA** (e devs) entenderem o sistema até o nível de
> implementação sem precisar ler todo o código antes. Reflete o código em
> `main` @ `b3bb48b` (2026-10-22). Quando este arquivo e o código divergirem,
> **o código vence** — atualize este arquivo no mesmo commit da mudança.

---

## 0. TL;DR

- **O que é:** web app para Mestres de RPG de mesa gerenciarem NPCs, um calendário
  de "TimeSkip" (dias × 4 slots + 1 slot extra) onde jogadores agendam interações
  com NPCs ou atividades solo, e um feed de logs narrativos. Agenda sincroniza em
  tempo real via WebSocket/STOMP.
- **Monorepo:** `backend/` (Java 21, Spring Boot 3.3.0) + `frontend/` (React 19 +
  Vite 8, Tailwind v4) + orquestração Docker na raiz.
- **Persistência dupla:** PostgreSQL (tudo relacional + bytes da imagem do NPC) e
  **MongoDB** (somente a ficha textual do NPC, coleção `npcs`). Não há FK entre os
  dois: `npc_id` no Postgres é um UUID "solto".
- **Auth:** JWT HS256 stateless (24h), enviado em `Authorization: Bearer`. No
  WebSocket, o JWT vai no header nativo do frame STOMP `CONNECT`.
- **Autorização:** sempre dentro dos *services* (não há `@PreAuthorize` em uso),
  baseada em `campaign_members.role` (`MASTER`/`PLAYER`) e em `npc.ownerId`.
- **Testes:** 379 testes unitários JUnit 5 + Mockito, só backend
  (`cd backend && ./mvnw test`, não precisa de banco). Frontend não tem testes.
- **Idioma:** domínio, mensagens de erro e comentários em **português**. Mantenha.

---

## 1. Mapa do repositório

```
TatakaiManager/
├── AGENTS.md                  # este arquivo
├── README.md                  # passo a passo para humanos (rodar local / deploy)
├── .env.example               # variáveis lidas pelo docker compose
├── docker-compose.yml         # dev: postgres + mongo + backend + frontend
├── docker-compose.prod.yml    # self-host: postgres + backend + frontend + caddy (Mongo externo/Atlas)
├── Caddyfile                  # reverse proxy TLS/WSS (domínios DuckDNS hard-coded)
├── backend/
│   ├── pom.xml
│   ├── Dockerfile             # multi-stage temurin 21; roda como usuário não-root
│   └── src/
│       ├── main/java/com/tatakai/manager/
│       │   ├── TatakaiManagerApplication.java
│       │   ├── config/SecurityConfig.java
│       │   ├── security/      # JwtService, JwtAuthenticationFilter, AuthenticatedUser, TextSanitizer
│       │   ├── websocket/     # WebSocketConfig, StompAuthChannelInterceptor, SlotEventPublisher
│       │   ├── controller/    # 8 controllers REST (finos: só delegam ao service)
│       │   ├── service/       # 8 services — TODA regra de negócio e autorização mora aqui
│       │   ├── repository/    # Spring Data JPA (+ 1 MongoRepository: NpcRepository)
│       │   ├── entity/        # entidades JPA + documento Mongo Npc + enums
│       │   ├── dto/request/   # records com Bean Validation
│       │   ├── dto/response/  # records de saída
│       │   └── exception/     # exceções de domínio + GlobalExceptionHandler
│       ├── main/resources/
│       │   ├── application.properties.example   # o .properties real NÃO é versionado
│       │   └── schema.sql                        # DDL de referência (desatualizado, ver §11)
│       └── test/                                 # Mockito, um pacote por domínio
├── frontend/
│   ├── package.json, vite.config.js, index.html, nginx.conf, Dockerfile
│   ├── public/env.js                     # placeholder; em container é regenerado no boot
│   ├── docker-entrypoint.d/40-render-env.sh
│   └── src/
│       ├── main.jsx, App.jsx, config.js, index.css
│       ├── api/          # um módulo por recurso, todos usam api/client.js (axios)
│       ├── contexts/     # AuthContext, ThemeContext, ToastContext
│       ├── hooks/useWebSocket.js
│       ├── routes/PrivateRoute.jsx
│       ├── pages/        # auth/Login, auth/Register, campaign/Dashboard, campaign/CampaignDetail, NotFound
│       └── components/   # layout/, campaign/, npc/, timeskip/, booking/SlotGrid, log/LogPanel, ErrorBoundary
└── graphify-out/          # (gitignored) grafo de conhecimento gerado localmente
```

Arquivos locais **ignorados pelo git** que podem existir na máquina do dono:
`.env`, `HANDOFF.md`, `DEPLOY.md` (runbook do deploy real), `graphify-out/`,
`backend/src/main/resources/application.properties`.

---

## 2. Glossário de domínio

| Termo | Significado | Onde no código |
|---|---|---|
| **Campanha** | Uma mesa de RPG. Tem um Mestre (criador) e jogadores convidados. | `Campaign`, `CampaignMember` |
| **Mestre / MASTER** | Papel por campanha. Criador da campanha é inserido como `MASTER`. | `Role.MASTER` |
| **Jogador / PLAYER** | Papel de quem foi convidado por e-mail. | `Role.PLAYER` |
| **NPC** | Personagem não-jogador. Pertence a um **dono** (`ownerId`) num **acervo global** e é **associado** a N campanhas. | `Npc` (Mongo), `CampaignNpc` |
| **Acervo** | Lista de NPCs criados por um usuário (`GET /npcs`). | `NpcService.listOwned` |
| **Visível** | Flag por associação campanha↔NPC; jogador não vê NPC oculto (recebe 404). | `CampaignNpc.visible` |
| **Interação** | Item oferecido por um NPC: `{type, name, description, idlePointCost}`. Agendar com NPC = escolher uma interação pelo `name`. | `NpcInteraction` |
| **TimeSkip** | Período de "tempo livre" in-game com `totalDays` dias. Só 1 `ACTIVE` por campanha. | `TimeSkip`, `TimeSkipDay` |
| **Dia atual** (`currentDay`) | Tempo de jogo, avançado manualmente pelo Mestre. Dias `< currentDay` já passaram (não se agenda nem cancela). | `TimeSkip.currentDay` |
| **Slot** | Cada dia tem slots 1..4 (`SLOTS_PER_DAY`) + slot extra 5 (`EXTRA_SLOT_NUMBER`). | `TimeSkipDay` |
| **Pontos de ócio** (`idlePointCost`) | Custo de uma ação. **Custo N ocupa N slots consecutivos** a partir do slot escolhido. Custo 0 só pode ir no slot extra. | `BookingService.validatePlacementAndConflicts` |
| **Agendamento / Booking** | Reserva de um slot de um dia. Três variantes mutuamente exclusivas: com NPC, solo fixa, solo customizada. | `Booking` |
| **Atividade solo fixa** | `TREINO`, `ESTUDO`, `ACAO_GERAL` — sem NPC, custo fixo **1**, descrição livre obrigatória. | `SoloActivityType` |
| **Atividade solo customizada** | Criada pelo Mestre e válida só dentro de um TimeSkip, com custo próprio. | `TimeSkipActivity` |
| **Log** | Texto narrativo. Jogador: vinculado a um booking próprio. Mestre: livre ou vinculado a qualquer booking; só o Mestre edita/exclui. | `InteractionLog` |
| **Visibilidade de ficha** | Ocultar seções (`LOGS`, `ATTRIBUTES`, `KNOWLEDGE`, `TRAITS`) da ficha de um jogador. **Só backend** — não há UI. | `CharacterSheetVisibility` |

Atributos do NPC (todos opcionais, 0..999): `forca, destreza, constituicao,
mental, inteligencia, talento`.

---

## 3. Arquitetura em execução

```
Navegador (SPA React)
   │  HTTP JSON  (axios, Authorization: Bearer <jwt>)
   │  SockJS → STOMP  (CONNECT com header Authorization)
   ▼
[nginx :80 serve dist/ + env.js]      [Spring Boot :8080]
                                         ├── SecurityFilterChain + JwtAuthenticationFilter
                                         ├── @RestController → @Service → Repository
                                         ├── SimpleBroker em memória (/topic)
                                         ├── JPA/Hibernate ──► PostgreSQL 16
                                         └── Spring Data Mongo ──► MongoDB 7 (coleção npcs)
```

- Em produção self-host, **Caddy** termina TLS e faz proxy de dois domínios:
  front → `frontend:80`, API → `backend:8080` (WebSocket passa transparente).
- **Não escale o backend horizontalmente:** o broker STOMP é in-memory; um
  broadcast só chega aos clientes conectados à mesma instância.
- O front **não** usa proxy de dev: chama a API por URL absoluta (CORS).

---

## 4. Backend em detalhe

### 4.1 Padrão de camadas (siga ao adicionar features)

1. **Controller**: mapeia rota, aplica `@Valid` no body, pega o usuário com
   `AuthenticatedUser.id()` e chama **um** método do service. Retorna `201` em
   criação, `204` em delete/upload, `200` no resto.
2. **Service** (`@Transactional`, `readOnly = true` em leituras): carrega
   entidades, **checa autorização** com helpers privados (cópias locais em cada
   service, não há classe compartilhada):
   - `requireMembership(campaignId, userId)` → `Role` ou `AccessDeniedException` (403)
   - `requireCampaignMaster(campaignId, userId)` → usa `existsByCampaignIdAndUserIdAndRole(..., MASTER)`
   - `requireOwner(npc, userId)` → compara `npc.ownerId`
   - `loadTimeSkipOfCampaign(tsId, campaignId)` → 404 se o TimeSkip não for da campanha
   Mapeia para DTO num `toResponse(...)` privado.
3. **Exceções**: cada erro de domínio é uma `RuntimeException` própria em
   `exception/`, registrada num grupo de status no `GlobalExceptionHandler`.
   Ao criar uma nova exceção, **adicione-a no handler** ou ela vira 500.
4. **DTOs** são `record`s. Texto curto (nomes, descrições) é validado com
   `@Pattern("^[^<>]*$")`; texto narrativo longo (logs) aceita `<>` mas passa
   pelo `TextSanitizer` (OWASP, política sem nenhuma tag) antes de persistir.

Recurso oculto ao jogador → **404, nunca 403** (para não revelar existência).

### 4.2 Segurança

`config/SecurityConfig.java`
- CSRF desabilitado, sessão `STATELESS`, CORS com origens de
  `app.cors.allowed-origins` (= env `FRONTEND_URL`, aceita lista separada por vírgula),
  métodos GET/POST/PUT/PATCH/DELETE/OPTIONS, headers `Authorization` e `Content-Type`.
- Públicos: `POST /auth/register`, `POST /auth/login`, `/ws/**` (handshake).
  Todo o resto exige autenticação; sem auth → **401** (`HttpStatusEntryPoint`).
- Headers: `Content-Security-Policy: default-src 'self'`, `X-Frame-Options: DENY`.
- `PasswordEncoder` = BCrypt.

`security/JwtService.java` — HS256 com `security.jwt.secret` (≥ 32 bytes).
Claims: `sub` = userId (UUID), `email`, `name`, `iat`, `exp`
(`security.jwt.expiration-ms`, 86400000 = 24h). Não há refresh token.

`security/JwtAuthenticationFilter.java` — se `Authorization: Bearer x` for válido
**e o usuário ainda existir no banco** (`userRepository.existsById`), coloca um
`UsernamePasswordAuthenticationToken(principal = userId string, authorities = [])`
no `SecurityContext`. Token de usuário apagado → 401 → front força novo login.

`security/AuthenticatedUser.id()` — lê o principal e devolve `UUID`.

### 4.3 WebSocket (tempo real da agenda)

`websocket/WebSocketConfig.java`
- Endpoint STOMP `/ws` **com SockJS**, origens = `app.cors.allowed-origins`.
- Simple broker em `/topic`; prefixo de app `/app` (nenhum `@MessageMapping` existe —
  o canal é **somente servidor → cliente**).
- `StompAuthChannelInterceptor` no canal de entrada: no `CONNECT`, exige header
  nativo `Authorization: Bearer <jwt>` válido, senão lança `MessagingException`.
  (Não verifica se o usuário ainda existe nem restringe `SUBSCRIBE` por campanha.)

`websocket/SlotEventPublisher.java` — `convertAndSend("/topic/campaign/{campaignId}/slots", SlotUpdateMessage)`.
Publicado por `BookingService.book` (evento `BOOKED`) e `BookingService.cancel`
(evento `CANCELLED`). Exclusão de TimeSkip, remoção de NPC da campanha e mudança
de dia **não** publicam evento.

Payload `SlotUpdateMessage` (JSON):
```json
{
  "event": "BOOKED" | "CANCELLED",
  "campaignId": "uuid", "timeSkipId": "uuid", "bookingId": "uuid",
  "npcId": "uuid|null", "dayNumber": 1, "slotNumber": 1,
  "userId": "uuid|null", "userName": "str|null", "interactionName": "str|null",
  "idlePointCost": 1, "soloActivityType": "TREINO|ESTUDO|ACAO_GERAL|null",
  "activityId": "uuid|null", "activityName": "str|null", "description": "str|null"
}
```
Em `CANCELLED`, só `event, campaignId, timeSkipId, bookingId, npcId, dayNumber, slotNumber` vêm preenchidos.

### 4.4 Modelo de dados

#### PostgreSQL (JPA; todos os ids são `UUID` gerados por `@GeneratedValue`)

| Tabela / Entidade | Colunas relevantes | Restrições / notas |
|---|---|---|
| `users` / `User` | name(100), email(150, unique), password (bcrypt), created_at | |
| `campaigns` / `Campaign` | name(100), description(text), master_id → users | |
| `campaign_members` / `CampaignMember` | campaign_id, user_id, role (`MASTER`/`PLAYER`) | unique(campaign_id, user_id) |
| `campaign_npcs` / `CampaignNpc` | campaign_id, npc_id (UUID solto → Mongo), visible (default true) | unique(campaign_id, npc_id) |
| `npc_images` / `NpcImage` | **PK = npc_id**, content_type, data (`bytea`, byte[]) | 1:1 com NPC; sem FK |
| `time_skips` / `TimeSkip` | campaign_id, name(100), total_days, current_day (default 1), status (`ACTIVE`/`CLOSED`), created_at, closed_at | `@OneToMany days` com `cascade=ALL, orphanRemoval` |
| `time_skip_days` / `TimeSkipDay` | time_skip_id, day_number | unique(time_skip_id, day_number). Constantes `SLOTS_PER_DAY=4`, `EXTRA_SLOT_NUMBER=5` |
| `time_skip_activities` / `TimeSkipActivity` | time_skip_id, name(100), description(text), idle_point_cost, created_at | unique(time_skip_id, name) |
| `bookings` / `Booking` | time_skip_day_id, user_id, slot_number (slot **inicial**), idle_point_cost, npc_id, npc_name, interaction_name, solo_activity_type, time_skip_activity_id, activity_name, description, created_at | unique `uk_booking_slot`(time_skip_day_id, npc_id, slot_number) |
| `interaction_logs` / `InteractionLog` | campaign_id, user_id (autor), booking_id (nullable), narrative(text), created_at, updated_at | |
| `character_sheet_visibility` / `CharacterSheetVisibility` | campaign_member_id, section, hidden_by_master, hidden_by_self | unique(campaign_member_id, section) |

Variantes de `Booking` (exatamente uma):
| Variante | Campos preenchidos | Custo |
|---|---|---|
| Com NPC | `npcId`, `npcName` (snapshot), `interactionName` (snapshot) | `interaction.idlePointCost` do NPC |
| Solo fixa | `soloActivityType`, `description` (do jogador) | **1** (`FIXED_SOLO_ACTIVITY_COST`) |
| Solo customizada | `timeSkipActivityId`, `activityName` + `description` (snapshots do catálogo) | `activity.idlePointCost` |

Snapshots existem para que editar/remover o NPC ou a atividade não altere
reservas antigas e para listar sem ir ao Mongo.

Timestamps: `@PrePersist` preenche `createdAt = Instant.now()` quando nulo.
Schema é criado pelo Hibernate (`spring.jpa.hibernate.ddl-auto = ${DDL_AUTO:validate}`;
compose usa `update`). Não há Flyway/Liquibase.

#### MongoDB — coleção `npcs` (`entity/Npc.java`, `@Document`)
```json
{
  "_id": "UUID (gerado na app: UUID.randomUUID(); uuid-representation=standard)",
  "name": "str", "description": "str|null",
  "attributes": { "forca": 0, "destreza": 0, "constituicao": 0, "mental": 0, "inteligencia": 0, "talento": 0 } | null,
  "ownerId": "UUID do users.id",
  "knowledge": [{ "name": "", "description": "" }],
  "traits":    [{ "name": "", "description": "" }],
  "specs":     [{ "name": "", "description": "" }],
  "interactions": [{ "type": "Treino", "name": "título", "description": "", "idlePointCost": 1 }],
  "createdAt": "Instant"
}
```
`NpcRepository extends MongoRepository<Npc, UUID>` com `findByOwnerId`.
Transações `@Transactional` **não** cobrem o Mongo (só o `DataSource` JPA).

### 4.5 Referência da API REST

Base: `API_URL` (dev `http://localhost:8080`). Todas exigem Bearer, exceto `/auth/*`.
"M" = exige ser MASTER da campanha; "Membro" = qualquer membro; "Dono" = `npc.ownerId`.

**Auth** — `AuthController` / `AuthService`
| Método | Rota | Body | Resposta | Regras |
|---|---|---|---|---|
| POST | `/auth/register` | `{name≤100 sem <>, email≤150, password 8..100}` | 201 `AuthResponse` | e-mail duplicado → 409 |
| POST | `/auth/login` | `{email, password}` | 200 `AuthResponse` | erro → 401 "E-mail ou senha inválidos" |

`AuthResponse = {token, userId, name, email}`

**Campanhas** — `CampaignController` / `CampaignService`
| Método | Rota | Body | Resposta | Regras |
|---|---|---|---|---|
| POST | `/campaigns` | `{name≤100, description≤2000}` (sem `<>`) | 201 `CampaignResponse` | criador vira MASTER |
| GET | `/campaigns` | — | 200 `CampaignResponse[]` | campanhas em que o usuário é membro |
| POST | `/campaigns/{cid}/members` | `{email}` | 201 `MemberResponse` | só `campaign.master`; e-mail inexistente → 404; já membro → 409; entra como PLAYER |

`CampaignResponse = {id, name, description, currentUserRole}` ·
`MemberResponse = {userId, name, email, role}`.
**Não existe** `GET /campaigns/{id}` nem listagem de membros.

**NPCs** — `NpcController` / `NpcService`
| Método | Rota | Body | Resposta | Regras |
|---|---|---|---|---|
| GET | `/npcs` | — | `NpcSummaryResponse[]` | acervo do usuário (`visible` sempre true) |
| POST | `/npcs` | `CreateNpcRequest` | 201 `NpcResponse` | dono = usuário; **não associa** a campanha |
| PUT | `/npcs/{nid}` | `UpdateNpcRequest` (mesmo formato) | `NpcResponse` | Dono; substitui tudo (listas são limpas e recriadas) |
| POST | `/npcs/{nid}/image` | multipart `file` | 204 | Dono; ≤5 MB, `image/*`, não vazio (400); excesso no multipart → 413 |
| DELETE | `/npcs/{nid}/image` | — | 204 | Dono |
| GET | `/campaigns/{cid}/npcs/{nid}/image` | — | bytes com `Content-Type` salvo | Membro; oculto p/ jogador → 404 |
| POST | `/campaigns/{cid}/npcs/{nid}` | — | 201 `CampaignNpcResponse` | Dono **e** M; já associado → 409; entra `visible=true` |
| DELETE | `/campaigns/{cid}/npcs/{nid}` | — | 204 | M; apaga em cascata bookings do NPC na campanha e logs ligados a eles; NPC continua no acervo |
| PATCH | `/campaigns/{cid}/npcs/{nid}/visibility` | `{visible: bool}` | `CampaignNpcResponse` | M |
| GET | `/campaigns/{cid}/npcs` | — | `NpcSummaryResponse[]` | Membro; MASTER vê todos, PLAYER só visíveis |
| GET | `/campaigns/{cid}/npcs/{nid}` | — | `NpcResponse` (com `visible`) | Membro; oculto p/ jogador → 404 |

`CreateNpcRequest = {name*, description, attributes{6×0..999}, knowledge[], traits[], specs[], interactions[]* (≥1)}`;
cada detalhe `{name* ≤100, description ≤2000}`; cada interação
`{type ≤50, name* ≤100, description ≤2000, idlePointCost* 0..9999}`.
`NpcSummaryResponse = {id, name, visible, hasImage, interactions[]}` ·
`NpcResponse = {id, name, description, attributes, knowledge, traits, specs, interactions, ownerId, hasImage, visible|null}` ·
`CampaignNpcResponse = {campaignId, npcId, visible}`.

**TimeSkips** — `TimeSkipController` / `TimeSkipService`
| Método | Rota | Body | Resposta | Regras |
|---|---|---|---|---|
| POST | `/campaigns/{cid}/timeskips` | `{name ≤100, totalDays 1..365}` | 201 `TimeSkipResponse` | M; já existe ACTIVE → 409; gera dias 1..N; `currentDay=1` |
| GET | `/campaigns/{cid}/timeskips` | — | `TimeSkipResponse[]` (mais recente primeiro) | Membro |
| PATCH | `/timeskips/{tid}/close` | — | `TimeSkipResponse` | M; já CLOSED → 409; seta `closedAt` |
| PATCH | `/timeskips/{tid}/current-day` | `{currentDay ≥1}` | `TimeSkipResponse` | M; CLOSED → 409; fora de 1..totalDays → 400. Pode voltar dias |
| DELETE | `/timeskips/{tid}` | — | 204 | M; apaga logs dos bookings → bookings → TimeSkip (dias por orphanRemoval) |

`TimeSkipResponse = {id, campaignId, name, totalDays, currentDay, status, createdAt, closedAt}`.
Nota: rotas `/timeskips/{tid}/...` não têm `campaignId` no path — a campanha é
derivada do TimeSkip.

**Atividades customizadas do TimeSkip** — `TimeSkipActivityController` / `TimeSkipActivityService`
| Método | Rota | Body | Resposta | Regras |
|---|---|---|---|---|
| POST | `/campaigns/{cid}/timeskips/{tid}/activities` | `{name* ≤100, description* ≤2000, idlePointCost* 0..9999}` | 201 | M; TimeSkip ACTIVE (senão 409); nome duplicado no TS → 409 |
| GET | `.../activities` | — | lista por `createdAt` | Membro |
| PUT | `.../activities/{aid}` | mesmo body | 200 | M; ACTIVE |
| DELETE | `.../activities/{aid}` | — | 204 | M; ACTIVE |

`TimeSkipActivityResponse = {id, timeSkipId, name, description, idlePointCost, createdAt}`.

**Agendamentos** — `BookingController` / `BookingService` (ver regras em §4.6)
| Método | Rota | Body | Resposta |
|---|---|---|---|
| POST | `/campaigns/{cid}/timeskips/{tid}/bookings` | `CreateBookingRequest` | 201 `BookingResponse` + evento WS `BOOKED` |
| GET | `/campaigns/{cid}/timeskips/{tid}/bookings` | — | `BookingResponse[]` de todos os dias e usuários |
| DELETE | `/bookings/{bid}` | — | 204 + evento WS `CANCELLED` |

`CreateBookingRequest = {npcId?, dayNumber* ≥1, slotNumber* 1..5, interactionName?, soloActivityType?, activityId?, description?}`
`BookingResponse = {id, npcId, npcName, userId, userName, dayNumber, slotNumber, interactionName, idlePointCost, soloActivityType, activityId, activityName, description, createdAt}`

**Logs** — `LogController` / `LogService`
| Método | Rota | Body | Resposta | Regras |
|---|---|---|---|---|
| POST | `/campaigns/{cid}/logs` | `{bookingId?, narrative* ≤5000}` | 201 `LogResponse` | Membro. Com `bookingId`: booking deve ser da campanha (senão 404); jogador só no próprio booking (403). Sem `bookingId`: só MASTER (403) |
| GET | `/campaigns/{cid}/logs` | — | `LogResponse[]` (mais recente primeiro) | Membro |
| PUT | `/campaigns/{cid}/logs/{lid}` | `{narrative*}` | `LogResponse` | M; seta `updatedAt` |
| DELETE | `/campaigns/{cid}/logs/{lid}` | — | 204 | M |

`LogResponse = {id, authorId, authorName, narrative, createdAt, updatedAt, bookingId, npcId, npcName, dayNumber, slotNumber, interactionName, idlePointCost}` (campos de booking nulos em log livre).
`narrative` é sanitizada (todas as tags HTML removidas) em create e update.

**Visibilidade de ficha** — `SheetVisibilityController` / `SheetVisibilityService` (sem UI)
| Método | Rota | Body | Resposta | Regras |
|---|---|---|---|---|
| GET | `/campaigns/{cid}/members/{uid}/sheet/visibility` | — | `SectionVisibilityResponse[]` (todas as 4 seções) | Membro; `visibleToMe = privilegiado ∨ (¬hiddenByMaster ∧ ¬hiddenBySelf)`, privilegiado = MASTER ou o próprio |
| PATCH | mesma rota | `{section, hidden}` | `SectionVisibilityResponse` | MASTER grava `hiddenByMaster`; o próprio grava `hiddenBySelf`; outros → 403 |

**Formato de erro** (todas as exceções mapeadas):
```json
{ "timestamp": "ISO", "status": 400, "error": "Bad Request", "message": "texto pt-BR",
  "fields": { "campo": "mensagem" } }   // "fields" só em erro de @Valid
```
| Status | Exceções |
|---|---|
| 400 | `InvalidBookingException`, `InvalidTimeSkipException`, `InvalidImageException`, `MethodArgumentNotValidException` |
| 401 | `InvalidCredentialsException`, ou request sem auth válida |
| 403 | `AccessDeniedException` (classe **própria** em `exception/`, não a do Spring) |
| 404 | `Campaign/User/Npc/TimeSkip/Booking/Member/TimeSkipActivity/LogNotFoundException` |
| 409 | `EmailAlreadyUsed`, `AlreadyMember`, `NpcAlreadyAssociated`, `ActiveTimeSkipExists`, `TimeSkipClosed`, `SlotTaken`, `BookingAlreadyPassed`, `InvalidActivityName` |
| 413 | `MaxUploadSizeExceededException` |
| 500 | qualquer exceção não mapeada (ex.: `DataIntegrityViolationException` fora dos try/catch) |

### 4.6 Algoritmo de agendamento (`BookingService.book`) — regra central

Ordem exata das verificações:
1. Requester é membro da campanha → senão 403. Guarda o `Role`.
2. TimeSkip existe e é da campanha → senão 404. Status `ACTIVE` → senão 409.
3. `TimeSkipDay(tid, dayNumber)` existe → senão 400 "Dia inválido".
4. `dayNumber >= timeSkip.currentDay` → senão 400 (dia passado).
5. Monta o booking:
   - **`npcId != null` → com NPC**: `interactionName` obrigatório (400); NPC
     associado à campanha (404); se oculto e requester não é MASTER → 404; busca
     NPC no Mongo; interação com `name` igual (match exato, case-sensitive) →
     senão 400. Custo = custo da interação.
   - **`npcId == null` → solo**: exatamente um de `soloActivityType` / `activityId`
     (400 se nenhum ou ambos). Custom: atividade tem que ser do TimeSkip (404),
     copia nome/descrição/custo. Fixa: `description` obrigatória (400), custo 1.
6. **Posição** (`validatePlacementAndConflicts`), com `slot` = slot inicial e `cost`:
   - `slot == 5` (extra): exige `cost == 0` (400) e o usuário ainda não ter
     nenhum booking com slot 5 nesse dia (com NPC ou solo) (400).
   - `slot ∈ 1..4`: exige `cost > 0` (400 "ações de custo zero devem usar o slot extra")
     e `slot + cost - 1 <= 4` (400 "não cabe").
7. **Conflito** (409 `SlotTakenException`): faixa ocupada por um booking =
   `[slot, slot + max(cost,1) - 1]`. Compara com:
   - booking com NPC → todos os bookings **daquele NPC no dia** (de qualquer jogador);
   - booking solo → só os bookings **solo do próprio jogador no dia**.
   Ou seja: dois jogadores diferentes podem fazer solo no mesmo slot; um mesmo
   jogador **pode** ter um booking com NPC e um solo sobrepostos (não há checagem
   cruzada NPC × solo do mesmo jogador).
8. `save`; `DataIntegrityViolationException` (corrida no unique
   `(dia, npc, slot inicial)`) → 409. A sobreposição de faixas em concorrência
   **não** é protegida pelo banco (só o slot inicial).
9. Publica `BOOKED` no tópico da campanha.

Exemplo: NPC com interação de custo 2 agendada no slot 2 ocupa 2 e 3; outro
jogador tentando custo 1 no slot 3 do mesmo NPC/dia → 409.

`cancel(bookingId)`: booking existe (404); requester é o **dono do booking**
(403 — nem o Mestre cancela de outros); dia `>= currentDay` (409
`BookingAlreadyPassedException`); deleta; publica `CANCELLED`. Não exige TimeSkip ACTIVE.

### 4.7 Configuração (`application.properties.example`)

| Propriedade | Env | Default |
|---|---|---|
| `spring.datasource.url/username/password` | `DB_URL`, `DB_USER`, `DB_PASS` | `jdbc:postgresql://localhost:5432/tatakai`, postgres/postgres |
| `spring.jpa.hibernate.ddl-auto` | `DDL_AUTO` | `validate` (compose: `update`) |
| `spring.data.mongodb.uri` | `MONGO_URI` | `mongodb://localhost:27017/tatakai` |
| `spring.data.mongodb.uuid-representation` | — | `standard` (**obrigatório** para `UUID` como `_id`) |
| `spring.servlet.multipart.max-file-size/request-size` | — | 5MB |
| `server.port` | `PORT` | 8080 |
| `app.cors.allowed-origins` | `FRONTEND_URL` | `http://localhost:5173` (compose: `:3001`) |
| `security.jwt.secret` | `JWT_SECRET` | placeholder — troque em produção |
| `security.jwt.expiration-ms` | — | 86400000 |

O Dockerfile copia o `.example` para `application.properties` se este não existir.
Testes usam `src/test/resources/application.yml` (H2 em memória, segredo de teste),
mas os testes atuais são unitários com Mockito e não sobem contexto Spring.

---

## 5. Frontend em detalhe

### 5.1 Bootstrap e config
- `main.jsx`: `StrictMode` → `ErrorBoundary` → `App`. Importa fontes Inter e
  Space Grotesk (auto-hospedadas via `@fontsource-variable`).
- `App.jsx`: `ThemeProvider` → `AuthProvider` → `ToastProvider` → `BrowserRouter`.
  Rotas: `/login`, `/register`, `/dashboard` (privada), `/campaigns/:id` (privada),
  `/` → `/dashboard`, `*` → `NotFound`.
- `config.js`: `API_URL` = `window.__ENV__.API_URL` → `VITE_API_URL` →
  `http://localhost:8080`. `WS_URL` = `window.__ENV__.WS_URL` → `VITE_WS_URL` →
  `${API_URL}/ws` (SockJS usa http/https, não ws/wss).
- `window.__ENV__` vem de `/env.js`, carregado antes do bundle em `index.html`.
  No container nginx, `docker-entrypoint.d/40-render-env.sh` gera esse arquivo a
  partir das envs `API_URL`/`WS_URL` → troca de endpoint sem rebuild. `nginx.conf`
  serve `env.js` com `no-store` e faz fallback SPA para `index.html`.
- `vite.config.js` define `global = globalThis` (exigência do `sockjs-client`).

### 5.2 Estado de sessão
- `AuthContext`: no mount lê `localStorage.token` e `localStorage.user`
  (`{userId, name, email}`); expõe `user, login(token, userData), logout(), loading`.
  **O papel (MASTER/PLAYER) não está no `user`** — vem por campanha em
  `campaign.currentUserRole`.
- `api/client.js` (axios): injeta `Authorization: Bearer` de `localStorage.token`;
  em qualquer **401** remove o token e faz `window.location.href = '/login'`.
- `parseApiError(err)` → `{message, fields}` a partir do formato de erro do backend.
- `ThemeContext`: `dark`/`light` em `localStorage.theme`, aplicado como
  `data-theme` no `<html>` (o `index.css` inverte a escala zinc no tema claro).
  Um script inline no `index.html` aplica o tema antes do render.
- `ToastContext`: `const toast = useToast(); toast(msg, 'success'|'error'|'info')`, some em 3,5s.

### 5.3 Telas e componentes
| Arquivo | Responsabilidade |
|---|---|
| `pages/auth/Login.jsx`, `Register.jsx` | chamam `client.post('/auth/...')` direto e `login(...)`; navegam p/ `/dashboard` |
| `pages/campaign/Dashboard.jsx` | lista `GET /campaigns`, cria campanha (`CreateCampaignForm`) |
| `pages/campaign/CampaignDetail.jsx` | carrega campanha (via `getCampaign` = busca na lista, pois não há GET por id) + NPCs; abas via sidebar: **NPCs**, **Agenda**, **Logs**, **Configurações** (só Mestre: `InviteMemberForm`). `npcs` é estado compartilhado entre abas |
| `components/layout/AppShell.jsx` | shell com `Sidebar` retrátil + fundo decorativo; exporta `Surface`, `PageHeader`, `EmptyState`, `SkeletonCard` |
| `components/npc/NpcSection.jsx` | grade de cards de NPC, modal de detalhe (`getCampaignNpc`), toggle de visibilidade, remover da campanha, associar do acervo (`listOwnedNpcs` + `associateNpc`), criar/editar |
| `components/npc/CreateNpcForm.jsx` | `POST /npcs` → (opcional) upload de imagem → `POST /campaigns/{cid}/npcs/{nid}` (3 chamadas, não atômicas) |
| `components/npc/EditNpcForm.jsx` | `PUT /npcs/{id}` → upload ou delete de imagem |
| `components/npc/InteractionListEditor.jsx` | tipos predefinidos `Treino, Sparring, Negociação, Aprendizado` + "Outro" (texto livre) |
| `components/npc/DetailListEditor.jsx` | editor genérico `{name, description}[]` (conhecimentos, traços, specs) |
| `components/npc/NpcImagePicker.jsx` | preview; reporta `{file, remove}` ao form; imagem carregada como blob autenticado → `URL.createObjectURL` |
| `components/timeskip/TimeSkipPanel.jsx` | lista/seleciona TimeSkip, criar (Mestre), "avançar dia" (`currentDay + 1`), encerrar, excluir; renderiza `TimeSkipActivityManager` (Mestre) e `SlotGrid`. `activitiesVersion` força o `SlotGrid` a recarregar o catálogo |
| `components/timeskip/TimeSkipActivityManager.jsx` | CRUD do catálogo de atividades customizadas |
| `components/booking/SlotGrid.jsx` | **a grade de agendamento** (ver §5.4) |
| `components/log/LogPanel.jsx` | feed de logs; Mestre posta log livre, edita, exclui; jogador escolhe um dos próprios bookings do TimeSkip ativo |
| `components/ErrorBoundary.jsx` | captura erro de render; botões "Recarregar" e "Sair e entrar de novo" |

Código morto: `components/layout/AppLayout.jsx` e `MasterRoute` em
`routes/PrivateRoute.jsx` não são usados (e `MasterRoute` lê `user.role`, que não existe).

### 5.4 `SlotGrid.jsx` (espelha as regras do backend no cliente)
- Constantes: `SLOTS=[1..4]`, `EXTRA_SLOT=5`, `COLUMNS=[1,2,3,4,5]`.
- Estado: `bookings` (NPC) indexado por `"${day}:${npcId}:${slotInicial}"`;
  `soloBookings` indexado por `booking.id`; `day` inicia em `timeSkip.currentDay`.
- Helpers que replicam o backend: `bookingEnd(b)`, `covers(b, slot)`,
  `fits(slot, cost)` (custo 0 só no extra; senão `slot+cost-1 <= 4`).
- Uma célula mostra: card completo no slot inicial, "continuação" nos slots
  seguintes cobertos, botão "+ Agendar" que abre as interações do NPC filtradas
  por `fits`, ou "—". Linha "Ação solo": tipos fixos (1 pt, nunca no extra) +
  atividades customizadas que cabem; select usa valores `fixed:TIPO` / `custom:<id>`.
- `canBook = timeSkip.status === 'ACTIVE' && day >= currentDay`.
- `usedExtraToday` bloqueia o extra no cliente se o usuário já tem slot 5 no dia.
- Tempo real: `useWebSocket(campaignId, handleSlotUpdate)`; ignora mensagens de
  outro `timeSkipId`; aplica BOOKED/CANCELLED de forma idempotente. Ao agendar
  ou cancelar localmente, o estado também é atualizado otimisticamente com a
  resposta HTTP (o eco do WS é descartado por já existir a chave).

`hooks/useWebSocket.js`: `@stomp/stompjs` `Client` com `SockJS(WS_URL)`,
`connectHeaders.Authorization`, `reconnectDelay 3000`, assina
`/topic/campaign/{id}/slots`; desativa no unmount. O callback deve ser estável
(`useCallback`) ou o socket reconecta a cada render.

### 5.5 Estilo
Tailwind v4 via `@tailwindcss/vite`, tokens em `src/index.css` (`@theme`):
fontes `--font-sans` (Inter) / `--font-display` (Space Grotesk), acento red-600,
fundo zinc-950, classes utilitárias próprias (`.surface`, `.btn-primary`,
`.field`, `.skeleton`). Lint: `npm run lint` (oxlint, regras react hooks).

---

## 6. Fluxos ponta a ponta

**Primeiro uso (Mestre)**: register → `POST /campaigns` (vira MASTER) → aba NPCs:
criar NPC (create + image + associate) → aba Configurações: convidar jogador por
e-mail (o jogador precisa já ter conta) → aba Agenda: criar TimeSkip (N dias) →
opcional: cadastrar atividades customizadas.

**Jogador agenda**: aba Agenda → `SlotGrid` carrega `GET .../bookings` e
`GET .../activities`, conecta WS → clica "+ Agendar" num NPC/slot → escolhe
interação → `POST .../bookings` → backend valida (§4.6) → salva → broadcast
`BOOKED` → as outras telas atualizam a célula.

**Mestre avança o tempo**: "avançar dia" → `PATCH /timeskips/{id}/current-day`
→ dias anteriores ficam somente-leitura (não agenda, não cancela). Não há
broadcast: outros clientes só veem ao recarregar o TimeSkip.

**Log**: jogador escolhe um booking próprio do TimeSkip ativo e escreve →
`POST /campaigns/{cid}/logs {bookingId, narrative}`. Mestre posta sem booking
(narração livre), edita (`PUT`) e exclui (`DELETE`).

---

## 7. Como rodar e verificar

```bash
cp .env.example .env && docker compose up --build   # front :3001, API :8080, Postgres :5433
cd backend && ./mvnw test                            # 379 testes, sem banco; requer JDK 21 (com javac)
cd backend && ./mvnw spring-boot:run                 # requer Postgres + Mongo + application.properties
cd frontend && npm install && npm run dev            # Vite :5173 (FRONTEND_URL do backend deve incluir essa origem)
cd frontend && npm run build && npm run lint
```
Na máquina do dono, o Java padrão pode ser um JRE sem `javac`; use um JDK 21
(`JAVA_HOME=~/.jdks/jbr-21.0.11`).

Produção (`docker-compose.prod.yml`): sem container Mongo (usa `MONGO_URI`
externo), Postgres só na rede interna, Caddy em 80/443, `restart: unless-stopped`,
backend em **1 réplica**.

---

## 8. Testes

`backend/src/test/java/com/tatakai/manager/<domínio>/*Test.java`, todos com
`@ExtendWith(MockitoExtension.class)` mockando repositórios e o publisher.
`@DisplayName` referencia sprint e user story (US-xx) / requisito não funcional (NFR-xx).

| Classe | Testes | Cobre |
|---|---|---|
| `BookingServiceTest` | 27 | agendamento NPC/solo/custom, faixas de custo, slot extra, dias passados, cancelamento, broadcast |
| `NpcServiceTest` | 17 | CRUD, acervo, associação, visibilidade, imagem, remoção em cascata |
| `TimeSkipServiceTest` | 16 | criação, um ativo por campanha, close, current-day, delete em cascata |
| `LogServiceTest` | 14 | permissões de log, sanitização, edição/exclusão pelo Mestre |
| `TimeSkipActivityServiceTest` | 9 | CRUD do catálogo, nome duplicado, TS encerrado |
| `SheetVisibilityServiceTest` | 8 | regras de visibilidade de ficha |
| `CampaignServiceTest` | 7 | criação, convite, listagem |
| `AuthServiceTest` | 5 | register/login |
| `StompAuthChannelInterceptorTest` / `TextSanitizerTest` / `SlotEventPublisherTest` | 3 / 3 / 2 | auth STOMP, XSS, destino do tópico |

Convenção do projeto: **TDD** — ao mudar regra de service, escreva/ajuste o teste
no `*ServiceTest` correspondente primeiro.

---

## 9. Receitas para mudanças comuns

- **Novo endpoint**: request record em `dto/request` (com validação e
  `@Pattern("^[^<>]*$")` em texto curto) → método no service com checagem de
  papel → response record → rota no controller → exceção nova registrada no
  `GlobalExceptionHandler` → teste Mockito → função em `frontend/src/api/<recurso>.js`.
- **Nova coluna/tabela**: altere a entidade JPA (o compose usa `DDL_AUTO=update`);
  produção com `validate` precisa de migração manual — atualize também `schema.sql`.
- **Novo campo na ficha do NPC**: `entity/Npc.java` (Mongo, sem migração) +
  `CreateNpcRequest`/`UpdateNpcRequest`/`NpcResponse` + mapeamentos em
  `NpcService` (`create`, `update`, `toResponse`) + `CreateNpcForm`/`EditNpcForm`/`NpcDetailView`.
- **Mudar regra de slots/custo**: altere `BookingService.validatePlacementAndConflicts`
  **e** os helpers `bookingEnd/covers/fits` em `SlotGrid.jsx` (a regra está duplicada).
- **Novo evento em tempo real**: estender `SlotUpdateMessage.SlotEvent`, publicar
  via `SlotEventPublisher`, tratar em `SlotGrid.handleSlotUpdate`.

---

## 10. Convenções

- Commits: **Conventional Commits** (`feat(booking): ...`, `fix(...)`, `docs(...)`),
  mensagem em português, **sem trailer `Co-Authored-By`**.
- Injeção por construtor, sem `@Autowired` em campo. Lombok nas entidades
  (`@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor`); records nos DTOs.
- Comentários e mensagens ao usuário em pt-BR.
- Frontend: componentes funcionais, estado local com `useState`, efeitos com flag
  `active` para ignorar respostas após unmount, erros exibidos via `parseApiError`.

---

## 11. Problemas conhecidos e armadilhas (verificados lendo o código)

1. **Log de jogador quebrado no front** — `LogPanel.jsx` (`handleSubmit`) envia
   `bookingId: Number(selectedBookingId)`. Ids são UUID → `NaN` → serializa como
   `null` → backend trata como log livre → **403 "Apenas o Mestre pode adicionar
   um log livre"**. Correção: enviar a string do UUID sem `Number(...)`.
2. **Cancelar booking que já tem log** — `BookingService.cancel` deleta o booking
   sem apagar logs que o referenciam (`interaction_logs.booking_id` FK) → erro de
   integridade no Postgres (500). Delete de TimeSkip e remoção de NPC tratam isso;
   `cancel` não.
3. **`schema.sql`** — corrigido (slot 1..5, `updated_at` em logs) e com as tabelas
   da ficha. Bancos antigos criados com o DDL velho ainda têm o CHECK 1..4:
   Hibernate `update` não remove CHECKs antigos — dropar manualmente.
4. ~~WebSocket sem autorização por campanha~~ — **corrigido na F0**: `SUBSCRIBE` só
   em `/topic/campaign/{id}/{canal}` para membros; `CONNECT` exige usuário existente.
5. **Sobreposição de faixas em concorrência** — só o slot inicial é protegido pelo
   unique do banco; dois requests simultâneos com faixas sobrepostas e slots
   iniciais diferentes podem passar.
6. **Mongo fora da transação** — criação/edição de NPC e o cascade de remoção não
   são atômicos entre Mongo e Postgres; NPC órfão no Mongo é filtrado em
   `listForCampaign` (`npcById.containsKey`).
7. **Visibilidade de ficha sem UI de toggle** — a ficha (F1) já respeita
   `SheetSection` (ATTRIBUTES, RESOURCES, INJURIES, INVENTORY), mas ainda não há
   tela para ocultar/exibir seções.
8. **Sem eventos WS para mudança de dia / exclusão / remoção de NPC** — outros
   clientes ficam com estado velho até recarregar.
9. **Caddyfile** tem domínios DuckDNS fixos — ajuste por ambiente.
11. **CHECK de enum gerado pelo Hibernate** — com `ddl-auto=update`, o Hibernate 6 cria
    `CHECK (col IN (...))` para colunas `@Enumerated(STRING)` ao criar a tabela, e **não
    atualiza** quando um valor novo entra no enum (ex.: `BuildStatus.IN_BUILD`). Em bancos
    existentes, dropar o CHECK antigo (`ALTER TABLE ... DROP CONSTRAINT <tabela>_<coluna>_check`).
    O mesmo vale para colunas que deixaram de ser `NOT NULL`.
12. **Dockerfile** empacota com `-Dmaven.test.skip=true` (não compila testes): com
    `-DskipTests`, um teste quebrado fazia o build da imagem falhar e o container antigo
    continuava rodando sem aviso.
10. JWT em `localStorage`, sem refresh; expira em 24h → próximo request dá 401 e
    o front redireciona para `/login`.

---

## 12. Ficha digital (F1) — `feat/ficha-digital`

Plano completo, fases futuras (F2–F7) e contratos: `PLANO_FICHA_DIGITAL.md`.

- **Modelo:** `CharacterSheet` 1:1 com `CampaignMember` (só PLAYER). Atributos em 7
  colunas (`intl` = INT). `hpMax/spMax/kpMax` persistidos (RN-REC-01); derivados
  (mod, faixa, limitador, peso, carga, estado crítico, faixa de desgaste) só em
  `service/rules/SheetRules` (puro) e espelhados em `components/sheet/sheetRules.js`.
- **Fluxo:** jogador cria (DRAFT, editável) → `submit` → Mestre `approve` (COMPLETED)
  ou rejeita (volta a DRAFT). Depois disso só o Mestre altera corpo via
  `POST /character-sheets/{id}/gm-adjustments` (`GmAction`, motivo obrigatório).
- **Auditoria:** toda mutação grava `sheet_activity_logs` (imutável) e publica
  `SheetUpdateMessage` → `SheetEventListener` (AFTER_COMMIT) → `/topic/campaign/{id}/sheets`.
- **Ficha de NPC (F1.1):** mesma entidade, com `npcId`/`npcOwnerId` no lugar de
  `campaignMember` (1 por NPC do acervo, estado compartilhado entre campanhas).
  Só o dono do NPC acessa via `/character-sheets/{id}` (outros → 404); jogadores
  veem via `GET /campaigns/{cid}/npcs/{nid}/sheet`, por seção liberada pelo Mestre
  em `npc_sheet_visibility` (padrão oculto). Rolagem de NPC não publica evento.
  Quando o NPC tem ficha (`sheetId`), os 6 atributos antigos do Mongo ficam ocultos.
- **Rolagens:** só no servidor (`RollService`, `SecureRandom`), tabela `rolls`.
- **Concorrência:** `@Version` na ficha → 409.
- **WebSocket:** `CampaignEventPublisher.publish(campaignId, canal, payload)`;
  front usa `useCampaignTopic(campaignId, canal, cb)`.
- **Visual:** design system quadrinhos/brutalismo em `index.css` (tokens `--ink`,
  `--paper`, `--line`, `--accent`, `--highlight`; classes `.panel`, `.bubble`,
  `.caption`, `.meter`, `.onomatopoeia`…). Fonte display: Anton.

## 13. Construção Inicial, catálogo e regras (F2)

- **Regras da campanha:** `campaign_rule_settings` + `CampaignRuleService` (padrão sem linha:
  tudo desligado, exceto `GM_REROLLS` e `TRAIT_METHOD=CLASSIC`). `FREE_CREATION` libera a
  criação livre; senão o jogador só cria pela Construção Inicial.
- **Catálogo (Mongo `traitDefinitions`, `clans`):** global (`campaignId` null, editável por
  e-mails em `CATALOG_EDITORS`) + extensão por campanha (Mestre). Na campanha, item com mesma
  `(raridade, número)` substitui o global. Seed: `backend/src/main/resources/catalog/*.json`
  (carregado no boot só se não houver globais) ou `POST /catalog/import` /
  `POST /campaigns/{cid}/catalog/import` com `{ "traits": [...], "clans": [...] }`
  (formato em `PLANO_FICHA_DIGITAL.md` §11.8).
- **Wizard:** `character_builds` (estado em jsonb) + `CharacterBuildService`; passos
  IDENTITY → TRAITS → BLOOD_TEST → ATTRIBUTES → FREE_POINTS → REVIEW. Toda rolagem via
  `RollService.rollInternal` (servidor + log). Contas em `service/rules/BuildRules` (puro).
  Envio grava atributos/máximos/`limiterAdjustments` e snapshots em `character_traits` /
  `character_clans` (versão do catálogo congelada — RN-TRAIT-02) → `PENDING_APPROVAL`.
- **Front:** `components/build/BuildWizard`, `components/catalog/CatalogManager` (aba
  Catálogo), `components/campaign/RuleSettings` (em Configurações).
- **Seeds reais:** `scripts/notion_seed.py` (stdlib) converte o export do Notion em `contexto/`
  (gitignored) para `backend/src/main/resources/catalog/traits.json` e `arts.json`
  (`python3 scripts/notion_seed.py`, reexecutável). Só artes **clássicas**. Artes ficam no Mongo
  `martialArts` (`MartialArt` com bases/técnicas/passivas em texto + números); leitura em
  `GET /catalog/arts[/{id}]`, edição por importação (editor de Criação Marcial = F3).
  O seeder só carrega se a coleção global estiver vazia — para atualizar um banco existente,
  use `POST /catalog/import` (ou, em dev, apague os globais no Mongo e reinicie o backend).
- **Contas de teste (dev):** `python3 scripts/dev_fixtures.py` (reexecutável) cria a "Mesa de
  Testes": `mestre@tatakai.dev` (Mestre, editor do catálogo se estiver em `CATALOG_EDITORS`),
  `jogador1` (ficha COMPLETED), `jogador2` (IN_BUILD), `jogador3` (sem ficha) — todos
  `@tatakai.dev`, senha `tatakai123`; NPC com ficha, TimeSkip ativo, agendamento e log.
- **Front compartilhado:** `components/ui/RichText` (parágrafos, listas `- `, tabelas `|`) e
  `components/ui/rarity` (cores por raridade via tokens `--rarity-*`; caixas de base/técnica
  usam `--stat-*`).

## 14. Criação Marcial (F3a)

Contrato em `PLANO_FICHA_DIGITAL.md` §15.
- **Montagem estruturada** nas artes (Mongo): `ArtTechnique.variations[]` (núcleo + `AssemblyItem[]`) + `extras[]`;
  `ArtBase.items[]`. O texto `assembly` e os números do livro continuam guardados.
- **Regras puras:** `service/rules/AssemblyRules` (Complexidade bruta/final, gasto por categoria, PT, tetos, dependências
  RN-TEC-05). Sem espelho no front: o editor chama `POST /catalog/assembly/check` (debounce).
- **Módulos:** `catalog/modules.json` estático (`GET /catalog/modules`), gerado a partir da §7.4 da spec.
- **Fluxo:** jogador cria Arte Criada (`DRAFT`, `authorId`) → `submit` (bloqueado se houver issues) → Mestre `review`.
  Rascunho só o autor vê; pendente, autor e Mestre (outros → 404).
- **Seed:** `scripts/notion_seed.py` converte o texto das montagens e imprime divergências de Complexidade vs. livro.
- **Front:** `components/catalog/ArtEditor.jsx`, `ArtsPanel.jsx` (novo/editar/enviar/aprovar), `artLabels.js`.

## 15. Artes e técnicas na ficha (F3b)

Contrato em `PLANO_FICHA_DIGITAL.md` §16. Tabelas `character_arts` e `character_techniques` (snapshot de nome, graduação,
complexidade e PT do catálogo). `CharacterTechniqueService` + `CharacterTechniqueController` (`/character-sheets/{sid}/martial`,
`/arts`, `/techniques/...`): jogador pede arte/técnica, Mestre aprova, lança treino e pontos de maestria; dono/Mestre alternam
ativa/guardada (máx. 3 ativas e 2 guardadas por arte e graduação) e sobem nível escolhendo bonificação (`MasteryRules`).
Front: `components/sheet/MartialPanel.jsx` no bloco "Artes marciais" da ficha.
