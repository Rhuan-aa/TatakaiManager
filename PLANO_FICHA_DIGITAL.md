# Plano — Ficha Digital no Tatakai Manager

> Fonte: *Tatakai — Especificação Técnica da Ficha Digital* (22/10/2026).
> Contexto do sistema: `graphify-out/` + `AGENTS.md` @ `b3bb48b`.
> Execução: `/dir-agents` (orquestrador Opus congela contratos, aplicadores Sonnet por diretório).

---

## 1. Diagnóstico

A spec cobre ~8 sistemas grandes (ficha, construção inicial, catálogo marcial, maestria/treino,
progressão, rolagens, motor de combate com reações assíncronas, exportação). **Não dá para entregar
de uma vez**: o motor de combate sozinho (§2–6, §19) é maior que todo o sistema atual.

O que o código atual oferece de base (do grafo):
- `CampaignMember` (comunidade "Sheet Visibility API") é o ponto natural de ancoragem: a ficha é
  **1 por membro PLAYER por campanha** (a spec põe `campaignId` no log, importa via
  `/campaigns/{id}/character-sheets/import` e usa export/import para "mudar de campanha").
- `SheetSection` / `CharacterSheetVisibility` / `SheetVisibilityService` já existem sem UI —
  passam a ter conteúdo real.
- `CampaignMemberRepository` (god node, 36 arestas) já tem tudo para `requireMembership` /
  `requireCampaignMaster`.
- WebSocket: só `/topic/.../slots`, `SlotEventPublisher` acoplado ao slot, e o
  `StompAuthChannelInterceptor` **não autoriza SUBSCRIBE** (problema conhecido #4) — a spec
  (§18.3) exige corrigir isso antes de qualquer tópico novo.
- TimeSkip/pontos de ócio já existem → é onde a progressão (§9) vai se plugar mais tarde
  (booking `TREINO` → lançamento no livro-razão de treino).

### Lacunas da spec que bloqueiam fases futuras (não a F1)
1. **Dados de catálogo** não estão na spec: tabelas de traços (d20 por raridade), Lista Oficial
   de Clãs, currículos das artes (técnicas/bases por graduação), Instintos por patente,
   catálogo de condições completo, tabelas de Sucesso/Falha Crítica. → bloqueia F2, F3, F6.
2. "Specs de história" (§13.2 passo 9) está "em definição".
3. Pop (Entertainment Wrestling) e Desequilibrado (Judô) têm ⚠ indefinidos.

---

## 2. Fases

| Fase | Entrega | Seções da spec | Depende de |
|---|---|---|---|
| **F0** | Pré-requisitos de infra: autorização de SUBSCRIBE por campanha; `CampaignEventPublisher` genérico; `@Version` | §18.3 | — |
| **F1** | **Ficha núcleo**: atributos + derivados, máximos persistidos, recursos atuais, Feridas, ferimentos, Desgaste, EL/PS/dinheiro/inventário; ferramentas do Mestre; `RollService` (dados no servidor); log de atividade; aba "Fichas" no front; visibilidade por seção ligada | §1, §4.6, §5.4 (só faixas), §11, §14, §16 (rolagem livre), RN-ATR-02..05/07/08/09, RN-REC-01/02, RN-COND-10/12 | F0 |
| F2 | Construção Inicial (`InitialBuild`) + `campaign_rule_settings` + catálogos de traços/clãs (Mongo) | §13, §15, §17 | F1 + **dados de catálogo** |
| F3 | Catálogo marcial (artes, técnicas, bases, módulos) + `TechniqueAssembly` (Complexidade/gasto/PT/tetos) + técnicas do personagem + maestria + editor de Criação Marcial | §7, §8.1, §20 | F1 + dados |
| F4 | Progressão: livro-razão de treino integrado ao TimeSkip, Repouso, consolidação (Limitador, Barreiras), EL/PS/Keiken, Forja, Rivais | §9 | F1, F3 |
| F5 | Canal de rolagens público/privado do Mestre, `RollProfile` pré-calculado, rolar rápido/detalhado | §16 | F1, F3 |
| F6 | Motor de combate: encontros, pipeline de turno, PA, condições com expiração, autômatos de desmaio, Adrenalina, `PendingAttack` + reações, interação marcial, contadores de arte | §2–6, §8, §19 | F1, F3, F5 |
| F7 | Exportar/importar JSON (+ PDF) | §21 | F1 (cresce com cada fase) |
| (transversal) | Direção visual quadrinhos/brutalismo | §12 | decidir escopo |

**Recomendação:** executar agora **F0 + F1**. É autocontida, não depende de dados que não temos,
e já dá valor na mesa (Mestre e jogadores com ficha viva, ajustes auditados, rolagens no servidor).

---

## 3. F0 + F1 em detalhe

### 3.1 Decisões de modelo (congeladas)

- **Código em inglês, mensagens/comentários pt-BR** (§12 + `AGENTS.md`). Mapeamento: `currentHp`,
  `openWounds`, `knockdownPoints` (PQ → `Kp`), `fatigue`, `fightExperience`, `overcomePoints` (PS).
- **Ficha = `character_sheets`, 1:1 com `campaign_members`** (unique em `campaign_member_id`).
  Só PLAYER tem ficha. Criada pelo próprio jogador (`POST`) com valores informados; em F1 não há
  Construção Inicial guiada → `buildStatus = DRAFT` editável pelo dono; Mestre aprova → `COMPLETED`
  (depois disso o jogador não edita corpo permanente, só o Mestre via ajustes). F2 substitui o DRAFT
  livre pelo wizard.
- **Derivados não são persistidos**: `mod`, `modQ`, `band` (faixa), `limiter`, `weightMod`,
  `carryKg`, `criticalState`, `fatigueBand`. Calculados numa classe pura `SheetRules`
  (métodos estáticos, 100% testável sem Spring). `hpMax/spMax/kpMax` **são persistidos** (RN-REC-01).
- Arredondamento: `floor` (§0), via divisão inteira / `Math.floorDiv`.
- Atributos como 7 colunas `int` (`str, dex, con, intl, cha, men, tal`) — sem tabela EAV.
- Ajustes de limitador por traço/clã/ferimento: em F1 só `permanent_injuries.limiter_reduction`
  + campo `limiter_adjustments jsonb`? → **não**: YAGNI. F1 usa
  `limiter = 40 + floor(TAL/2) + bodyTypeAdj − Σ reduções de ferimento não tratado do atributo`.
  Traço/clã entram na F2.
- Toda mutação passa por **um único método de service** que grava `sheet_activity_logs`
  (imutável) e publica `SHEET_UPDATED` **após commit** (`@TransactionalEventListener(AFTER_COMMIT)`).
  Sem `GmAdjustmentCommand` como hierarquia de classes — um enum `GmAction` + switch basta (§18.2
  "não usar padrão onde um método resolve").
- `@Version` em `CharacterSheet` (lock otimista); conflito → **409**.
- Migrações: compose usa `ddl-auto=update`; produção `validate` → atualizar `schema.sql` com o DDL
  novo (e corrigir de quebra o CHECK `slot_number` 1..4 → 1..5 e `updated_at` dos logs, armadilha #3).

### 3.2 Contratos — Banco (PostgreSQL)

```
character_sheets
  id uuid pk, campaign_member_id uuid unique not null fk campaign_members,
  name varchar(100) not null,
  build_status varchar(20) not null  -- DRAFT | PENDING_APPROVAL | COMPLETED
  body_type varchar(20) not null     -- ENDOMORPH | MESOMORPH | ECTOMORPH
  dominance varchar(20) not null     -- RIGHT | LEFT | AMBIDEXTROUS
  height_m numeric(3,2) not null, weight_kg numeric(5,1) not null,
  str int, dex int, con int, intl int, cha int, men int, tal int   (not null, >= 0)
  hp_max int, sp_max int, kp_max int, current_hp int, current_sp int, current_kp int,
  open_wounds int not null default 0,
  physical_fatigue int not null default 0, mental_fatigue int not null default 0,
  fight_experience int not null default 0, pending_overcome_points int not null default 0,
  money int not null default 0,        -- em diárias
  version bigint not null, created_at timestamptz, updated_at timestamptz

permanent_injuries
  id uuid pk, sheet_id uuid fk, type varchar(20) (CUTTING|PIERCING|BLUNT),
  region varchar(100), severity varchar(20) (MINOR|MODERATE|SEVERE|LETHAL),
  hp_max_reduction int, limiter_reduction int, affected_attribute varchar(10) null,
  treated boolean default false, created_at

inventory_items
  id uuid pk, sheet_id uuid fk, name varchar(100), quantity int >= 0, description text

sheet_activity_logs   (imutável — sem setters de update usados)
  id uuid pk, sheet_id uuid, campaign_id uuid, actor_id uuid null,
  actor_role varchar(10) (PLAYER|MASTER|SYSTEM),
  category varchar(20) (BUILD|ROLL|RESOURCE|CONDITION|INVENTORY|GM_ADJUSTMENT|PROGRESSION),
  action varchar(50), field varchar(50) null, before_value text null, after_value text null,
  roll_id uuid null, reason varchar(500) null, created_at

rolls
  id uuid pk, campaign_id uuid, sheet_id uuid null, actor_id uuid,
  expression varchar(50)  -- ex. "1d20+3", "2d6"
  dice_results text       -- JSON array de ints
  modifier int, total int, natural_d20 int null, purpose varchar(200), created_at
```

`SheetSection` ganha: `RESOURCES, INJURIES, INVENTORY, ACTIVITY` (mantém os 4 atuais; `LOGS` continua
significando logs narrativos).

### 3.3 Contratos — Regras puras (`service/rules/SheetRules.java`)

```java
static Integer mod(int v)            // null se v==0 (Estado Crítico); -4/-2/0/floor(v/10)
static int modQ(int v)               // max(0, mod) ; v==0 -> 0
static int half(int m)               // m > 0 ? m/2 : m
static AttributeBand band(int v)     // COLLAPSE(0) INEPT(1-3) DEFICIENT(4-6) UNTRAINED(7-9) FUNCTIONAL(10+)
static int attributeLimiter(Attribute a, int tal, BodyType bt, int injuryReduction)  // TAL -> sem limite (Integer.MAX_VALUE)
static int statusLimiter(Resource r, int tal, BodyType bt)   // HP/SP: 60+floor(TAL/2)+adj ; KP: 8
static int initialHpMax(int con)     // 10 + con
static int initialSpMax(int con)     // 5 + con/2
static int initialKpMax(int con)     // 3 + modQ(con)
static double initialWeight(double heightM, BodyType bt, int con)  // clamp (RN-ATR-08)
static int weightMod(double kg)
static int carryKg(int str)          // 3 * str
static boolean criticalState(int currentHp, int effectiveHpMax, int... attrs)  // hp <= max/4 || algum attr == 0
static FatigueBand fatigueBand(int points) // RESTED 0-2, TIRED 3-5, DRAINED 6-8, EXHAUSTED 9-11, COLLAPSE >=12
static int injuryHpReduction(InjurySeverity s, InjuryType t, int hpMax, int damage) // 10/25/45/100% ; BLUNT: floor(damage/2)
static int injuryLimiterReduction(InjurySeverity s)  // -5/-10/-15
```
Enums novos em `entity/`: `BuildStatus, BodyType, Dominance, Attribute, AttributeBand, FatigueBand,
InjuryType, InjurySeverity, ActorRole, ActivityCategory, GmAction`.

### 3.4 Contratos — API REST

| Método | Rota | Quem | Body → Resposta |
|---|---|---|---|
| POST | `/campaigns/{cid}/character-sheets` | PLAYER sem ficha | `CreateCharacterSheetRequest` → 201 `CharacterSheetResponse` (máximos calculados por `SheetRules.initial*`) |
| GET | `/campaigns/{cid}/character-sheets` | Membro | `CharacterSheetSummaryResponse[]` (Mestre: todas; jogador: todas, com seções filtradas por visibilidade) |
| GET | `/character-sheets/{id}` | Membro | `CharacterSheetResponse` (seções ocultas = `null`) |
| PUT | `/character-sheets/{id}` | dono, só em DRAFT | `UpdateCharacterSheetRequest` → `CharacterSheetResponse` |
| POST | `/character-sheets/{id}/submit` | dono | DRAFT → PENDING_APPROVAL |
| POST | `/character-sheets/{id}/approve` | M | `{approved: bool, reason?}` → COMPLETED ou volta a DRAFT |
| PATCH | `/character-sheets/{id}/resources` | dono ou M | `{currentHp?, currentSp?, currentKp?, reason?}` clamp 0..max (jogador fora de combate → log destacado) |
| POST | `/character-sheets/{id}/gm-adjustments` | M | `GmAdjustmentRequest {action: GmAction, field?, value?, reason*, injury?, item?}` → `CharacterSheetResponse` |
| POST | `/character-sheets/{id}/rolls` | dono ou M | `{expression*, purpose*}` → 201 `RollResponse` |
| GET | `/character-sheets/{id}/activity-log` | dono ou M | `SheetActivityLogResponse[]` (mais recente primeiro) |
| GET | `/campaigns/{cid}/activity-log?sheetId=&category=&from=&to=` | M | idem |

`GmAction`: `GRANT_FIGHT_EXPERIENCE, ADJUST_OVERCOME_POINTS, SET_RESOURCE, SET_OPEN_WOUNDS,
ADD_INJURY, TREAT_INJURY, REMOVE_INJURY, ADJUST_FATIGUE (field=PHYSICAL|MENTAL), ADJUST_MONEY,
ADD_ITEM, UPDATE_ITEM, REMOVE_ITEM, SET_ATTRIBUTE, SET_MAX` (os dois últimos = correção do Mestre
fora das regras; sempre logados).

`expression` aceita `^\d{1,2}d(4|6|8|10|12|20|100)([+-]\d{1,3})?$` (validação no DTO + no `RollService`).
`RollService` usa `SecureRandom`, é o único lugar que rola dado.

`CharacterSheetResponse` = todos os campos persistidos + bloco `derived {mods{}, bands{}, limiters{},
effectiveHpMax, weightMod, carryKg, criticalState, physicalFatigueBand, mentalFatigueBand}` +
`injuries[]`, `inventory[]`, `ownerUserId`, `ownerName`, `buildStatus`, `version`.

Exceções novas (todas registradas no `GlobalExceptionHandler`):
`CharacterSheetNotFoundException` 404, `CharacterSheetAlreadyExistsException` 409,
`InvalidCharacterSheetException` 400, `SheetLockedException` 409 (editar fora de DRAFT),
`InvalidRollException` 400, `ObjectOptimisticLockingFailureException` → 409.

### 3.5 Contratos — WebSocket

- `StompAuthChannelInterceptor`: em `SUBSCRIBE` para `/topic/campaign/{cid}/**`, exigir
  `campaignMemberRepository.existsByCampaignIdAndUserId(cid, userId)`; senão `MessagingException`.
  Em `CONNECT`, checar `userRepository.existsById` (fecha armadilha #4 inteira).
- `CampaignEventPublisher.publish(UUID campaignId, String channel, Object payload)` →
  `/topic/campaign/{cid}/{channel}`. `SlotEventPublisher` passa a delegar a ele (testes atuais
  continuam passando).
- `SheetUpdateMessage {event: SHEET_CREATED|SHEET_UPDATED|ROLL, campaignId, sheetId, category,
  action, actorName}` em `/topic/campaign/{cid}/sheets` — **só resumo**; cliente refaz `GET`
  (que já aplica visibilidade). Publicado após commit.

### 3.6 Frontend

- `api/characterSheets.js` — uma função por rota da 3.4.
- `hooks/useWebSocket.js` → generalizar para `useCampaignTopic(campaignId, channel, onMessage)`;
  `useWebSocket` vira wrapper fino para `slots` (SlotGrid não muda).
- `components/sheet/`:
  - `SheetsSection.jsx` — aba "Fichas": lista de fichas (cards), botão "Criar minha ficha".
  - `CharacterSheetForm.jsx` — criação/edição em DRAFT (identidade + 7 atributos), prévia dos
    derivados calculada no cliente (`sheetRules.js` espelhando `SheetRules` — como `SlotGrid` faz).
  - `CharacterSheetView.jsx` — atributos com mod/faixa/limitador, barras HP/SP/PQ, Feridas,
    ferimentos, Desgaste por faixa, inventário, dinheiro, EL/PS; respeita seções `null`.
  - `GmAdjustmentPanel.jsx` — ações da 3.4 com motivo obrigatório (só Mestre).
  - `RollBox.jsx` — expressão + propósito → resultado.
  - `ActivityLogPanel.jsx` — linha do tempo com filtros (categoria, ator).
  - `sheetRules.js` — `mod, modQ, band, limiter, fatigueBand` (mesmo contrato da 3.3).
- `pages/campaign/CampaignDetail.jsx` — nova aba **Fichas** (ícone `user`).
- Visual: segue os tokens atuais (`.surface`, `.btn-primary`). A direção "quadrinhos" fica para
  decisão separada.

### 3.7 Testes (TDD, padrão do repo)

- `sheet/SheetRulesTest` — tabela de `mod`, `modQ`, `half`, `band`, limitadores por tipo corporal,
  peso/clamp, `weightMod`, `fatigueBand`, `criticalState`, reduções de ferimento.
- `sheet/CharacterSheetServiceTest` — criar (1 por membro, só PLAYER), máximos iniciais, editar só
  em DRAFT, submit/approve, visibilidade aplicada, clamp de recursos, cada `GmAction` grava log e
  exige Mestre + motivo, lock otimista.
- `sheet/RollServiceTest` — parse/rejeição de expressão, faixa dos resultados, log.
- `StompAuthChannelInterceptorTest` — SUBSCRIBE negado a não-membro, permitido a membro.
- `CampaignEventPublisherTest` — destino.

### 3.8 Fatiamento para `/dir-agents`

O backend é organizado **por camada**, então as fatias são por camada com contratos 3.2–3.5
congelados no prompt de todos:

| # | Escopo | Faz |
|---|---|---|
| A | `backend/src/main/java/com/tatakai/manager/entity` | entidades + enums (3.2, 3.3 enums), `SheetSection` novos valores |
| B | `backend/src/main/java/com/tatakai/manager/repository` | repositórios das 5 tabelas |
| C | `backend/src/main/java/com/tatakai/manager/dto` | requests/responses (records + validação) |
| D | `backend/src/main/java/com/tatakai/manager/service` | `rules/SheetRules`, `CharacterSheetService`, `RollService`, `SheetActivityLogService`; ajuste em `SheetVisibilityService` se preciso |
| E | `backend/src/main/java/com/tatakai/manager/exception` | exceções + handler |
| F | `backend/src/main/java/com/tatakai/manager/websocket` | interceptor SUBSCRIBE, `CampaignEventPublisher`, `SheetEventListener` (AFTER_COMMIT) |
| G | `backend/src/main/java/com/tatakai/manager/controller` | `CharacterSheetController`, `ActivityLogController` |
| H | `backend/src/test/java/com/tatakai/manager` | testes 3.7 |
| I | `frontend/src/api` + `frontend/src/hooks` | cliente + hook genérico |
| J | `frontend/src/components/sheet` | componentes 3.6 |
| K | (orquestrador) | `CampaignDetail.jsx`, `schema.sql`, `AGENTS.md`, `README` |

Ondas (para não compilar contra vazio): **onda 1** A, C, E, I (sem dependências) →
**onda 2** B, F, J → **onda 3** D, G, H → orquestrador faz K, roda `./mvnw test`,
`npm run build && npm run lint`, corrige fronteiras.

Estimativa: ~35 arquivos novos, ~6 alterados.

---

## 4. Perguntas em aberto (para depois da F1)

1. Os dados de catálogo (traços, clãs, artes, Instintos) existem em planilha/doc? Formato?
2. A direção visual "quadrinhos/brutalismo" vale para o app todo ou só para a ficha?
3. Quer adotar Flyway agora (muitas tabelas novas em produção com `validate`) ou seguir com
   `schema.sql` manual?

---

## 5. Decisões do usuário (23/10/2026)

- Executar **F0 + F1** agora.
- Migração: **`schema.sql` manual**.
- Visual quadrinhos/brutalismo: **app todo, em paralelo à F1**.

## 6. Contrato visual (congelado) — §12 da spec

Fonte display: **Anton** (`@fontsource/anton`, já instalada) em caixa alta; corpo segue Inter.
Space Grotesk deixa de ser usada (remover import).

Tokens em `frontend/src/index.css` (`:root` = tema escuro "tinta"; `[data-theme='light']` = "papel"):

| Token | Escuro (tinta) | Claro (papel) | Uso |
|---|---|---|---|
| `--ink` | `#0b0b0c` | `#0b0b0c` | preto de impressão |
| `--paper` | `#f3ecdc` | `#f3ecdc` | papel |
| `--bg` | `#121214` | `#f3ecdc` | fundo da página |
| `--panel-bg` | `#1b1b1e` | `#fffaf0` | fundo de painel |
| `--line` | `#f3ecdc` | `#0b0b0c` | bordas e sombras duras (contrastam com o fundo) |
| `--text` | `#f3ecdc` | `#0b0b0c` | texto |
| `--accent` | `#dc2626` | `#dc2626` | vermelho atual |
| `--highlight` | `#facc15` | `#facc15` | cor de destaque (recordatório, onomatopeia) |
| `--border-w` | `3px` | `3px` | |
| `--shadow-hard` | `5px 5px 0 var(--line)` | idem | sombra dura sem desfoque |

A inversão da escala zinc no tema claro **continua** (utilities inline existentes seguem funcionando).

Classes utilitárias (em `@layer components`) — **nomes congelados**, todos os agentes usam só estes:

| Classe | Visual |
|---|---|
| `.panel` | `border: var(--border-w) solid var(--line)`, `box-shadow: var(--shadow-hard)`, `background: var(--panel-bg)`, raio 0–2px. **`.surface` vira alias de `.panel`.** |
| `.panel-title` | Anton, uppercase, letter-spacing .02em, tamanho 1.25rem+ |
| `.page-gutter` | grid/flex com gap 12–16px (calhas de quadrinho) |
| `.btn-primary` | fundo `--accent`, texto branco, borda `--line`, sombra dura 3px; `:active` translada 3px e remove sombra |
| `.btn-secondary` | fundo `--panel-bg`, texto `--text`, borda/sombra dura iguais |
| `.btn-ghost` | sem borda, uppercase, hover sublinhado grosso |
| `.field` | borda 2px `--line`, fundo `--bg`, foco: outline 3px `--accent` |
| `.tag` / `.tag-accent` | etiqueta retangular uppercase com borda 2px (substitui pílulas arredondadas) |
| `.bubble` | balão de fala (borda + rabicho via `::after`); usado em toasts e itens de log narrativo |
| `.caption` | caixa de recordatório: fundo `--highlight`, texto `--ink`, borda, uppercase pequeno — avisos do narrador/Mestre |
| `.halftone` | textura de retícula (radial-gradient de pontos, opacidade baixa) — **só em fundos sem texto por cima** |
| `.speed-lines` | linhas de velocidade (repeating-conic/linear-gradient) — fundo decorativo |
| `.meter` + `.meter-fill` | barra de painel: moldura com borda grossa, preenchimento sólido segmentado (`repeating-linear-gradient`); cor via `style={{'--meter-color': ...}}` |
| `.onomatopoeia` | Anton, `--highlight` com contorno `--ink` (`-webkit-text-stroke`), animação "pop" + leve tremor; **desligada em `prefers-reduced-motion`** |
| `.skeleton` | mantém, com bordas retas |

Acessibilidade: contraste AA de texto sobre `--panel-bg`/`--bg`; textura nunca atrás de texto;
foco visível em todos os interativos.

## 7. Fatiamento final (uma onda, contratos congelados)

| Agente | Escopo | Faz |
|---|---|---|
| A | `backend/.../entity` | entidades + enums da F1 |
| B | `backend/.../repository` | repositórios |
| C | `backend/.../dto` | requests/responses |
| D | `backend/.../service` | `rules/SheetRules`, `CharacterSheetService`, `RollService`, `SheetActivityLogService`, eventos de domínio |
| E | `backend/.../exception` | exceções + handler |
| F | `backend/.../websocket` | SUBSCRIBE por campanha, `CampaignEventPublisher`, listener AFTER_COMMIT |
| G | `backend/.../controller` | controllers |
| H | `backend/src/test/...` | testes |
| I | `frontend/src/api` | `characterSheets.js` |
| J | `frontend/src/components/sheet` | componentes da ficha (já no visual novo) |
| V0 | `frontend/src` (arquivos da raiz) + `contexts/ToastContext.jsx` + `components/ErrorBoundary.jsx` | tokens/classes (§6), fontes, toasts em balão |
| V1 | `frontend/src/components/layout` | shell/sidebar/fundo em quadrinho |
| V2 | `frontend/src/components/npc` | redesign |
| V3 | `frontend/src/components/timeskip` | redesign |
| V4 | `frontend/src/components/booking` | redesign do SlotGrid (sem mudar lógica) |
| V5 | `frontend/src/components/log` | redesign (logs em balão) |
| V6 | `frontend/src/components/campaign` | redesign |
| V7 | `frontend/src/pages` | redesign + aba **Fichas** em `CampaignDetail` |
| Orq. | `hooks/useWebSocket.js` (feito), `package.json` (feito), `schema.sql`, `AGENTS.md`, merge, build/test |

## 8. Contratos de código (congelados — fonte da verdade para os agentes)

Pacote base: `com.tatakai.manager`. Quando divergir da §3, **esta seção vence**.

### 8.1 `entity` (Lombok `@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor`, ids `@Id @GeneratedValue UUID`)

Enums (um arquivo cada):
```
Attribute        { STR, DEX, CON, INT, CHA, MEN, TAL }
AttributeBand    { COLLAPSE, INEPT, DEFICIENT, UNTRAINED, FUNCTIONAL }
BuildStatus      { DRAFT, PENDING_APPROVAL, COMPLETED }
BodyType         { ENDOMORPH, MESOMORPH, ECTOMORPH }
Dominance        { RIGHT, LEFT, AMBIDEXTROUS }
ResourceType     { HP, SP, KP }
FatigueType      { PHYSICAL, MENTAL }
FatigueBand      { RESTED, TIRED, DRAINED, EXHAUSTED, COLLAPSE }
InjuryType       { CUTTING, PIERCING, BLUNT }
InjurySeverity   { MINOR, MODERATE, SEVERE, LETHAL }
ActorRole        { PLAYER, MASTER, SYSTEM }
ActivityCategory { BUILD, ROLL, RESOURCE, CONDITION, INVENTORY, GM_ADJUSTMENT, PROGRESSION }
GmAction         { GRANT_FIGHT_EXPERIENCE, ADJUST_OVERCOME_POINTS, SET_RESOURCE, SET_MAX, SET_ATTRIBUTE,
                   SET_OPEN_WOUNDS, ADD_INJURY, TREAT_INJURY, REMOVE_INJURY, ADJUST_FATIGUE,
                   ADJUST_MONEY, ADD_ITEM, UPDATE_ITEM, REMOVE_ITEM }
SheetSection     { LOGS, ATTRIBUTES, KNOWLEDGE, TRAITS, RESOURCES, INJURIES, INVENTORY, ACTIVITY }  // só acrescenta
```

`CharacterSheet` — tabela `character_sheets`:
```
UUID id
@OneToOne(fetch=LAZY, optional=false) @JoinColumn(name="campaign_member_id", nullable=false, unique=true) CampaignMember campaignMember
String name (100)          BuildStatus buildStatus   BodyType bodyType   Dominance dominance   (enums STRING, length 20)
double heightM ("height_m")   double weightKg ("weight_kg")
int str, dex, con, intl, cha, men, tal        // coluna de intl = "intl"
int hpMax, spMax, kpMax, currentHp, currentSp, currentKp
int openWounds, physicalFatigue, mentalFatigue, fightExperience, pendingOvercomePoints, money   (default 0)
@Version Long version
Instant createdAt, updatedAt     // @PrePersist preenche os dois; @PreUpdate atualiza updatedAt
@OneToMany(mappedBy="sheet", cascade=ALL, orphanRemoval=true) @OrderBy("createdAt") @Builder.Default List<PermanentInjury> injuries = new ArrayList<>()
@OneToMany(mappedBy="sheet", cascade=ALL, orphanRemoval=true) @OrderBy("name") @Builder.Default List<InventoryItem> inventory = new ArrayList<>()
public int getAttribute(Attribute a)            // switch
public void setAttribute(Attribute a, int v)    // switch
```
`PermanentInjury` — `permanent_injuries`: `UUID id; @ManyToOne(LAZY, optional=false) @JoinColumn(name="sheet_id") CharacterSheet sheet; InjuryType type; String region(100); InjurySeverity severity; int hpMaxReduction; int limiterReduction; Attribute affectedAttribute (nullable); boolean treated; Instant createdAt (@PrePersist)`.

`InventoryItem` — `inventory_items`: `UUID id; @ManyToOne(LAZY, optional=false) @JoinColumn(name="sheet_id") CharacterSheet sheet; String name(100); int quantity; String description (columnDefinition text)`.

`SheetActivityLog` — `sheet_activity_logs` (imutável: **sem `@Setter`**): `UUID id; UUID sheetId; UUID campaignId; UUID actorId (nullable); ActorRole actorRole; ActivityCategory category; String action(50); String field(50); String beforeValue (text); String afterValue (text); UUID rollId; String reason(500); Instant createdAt (@PrePersist)`.

`Roll` — `rolls` (sem `@Setter`): `UUID id; UUID campaignId; UUID sheetId; UUID actorId; String expression(50); String diceResults (text, JSON array ex. "[3,5]"); int modifier; int total; Integer naturalD20; String purpose(200); Instant createdAt (@PrePersist)`.

### 8.2 `repository`
```java
CharacterSheetRepository extends JpaRepository<CharacterSheet, UUID>
  Optional<CharacterSheet> findByCampaignMemberId(UUID campaignMemberId);
  boolean existsByCampaignMemberId(UUID campaignMemberId);
  List<CharacterSheet> findByCampaignMemberCampaignId(UUID campaignId);
SheetActivityLogRepository extends JpaRepository<SheetActivityLog, UUID>
  List<SheetActivityLog> findBySheetIdOrderByCreatedAtDesc(UUID sheetId);
  List<SheetActivityLog> findByCampaignIdOrderByCreatedAtDesc(UUID campaignId);
RollRepository extends JpaRepository<Roll, UUID>
```
Ferimentos e itens são gravados via cascade da ficha (sem repositório próprio).

### 8.3 `dto` (records; texto curto com `@Pattern(regexp = "^[^<>]*$")`)

`dto/request`:
```
AttributesPayload(@NotNull @Min(0) @Max(999) Integer str, dex, con, intl, cha, men, tal)
CreateCharacterSheetRequest(@NotBlank @Size(max=100) String name, @NotNull BodyType bodyType, @NotNull Dominance dominance,
    @NotNull @DecimalMin("1.00") @DecimalMax("2.50") Double heightM, Double weightKg /* null => SheetRules.initialWeight */,
    @NotNull @Valid AttributesPayload attributes)
UpdateCharacterSheetRequest  — mesmos campos e validações de Create
ApproveCharacterSheetRequest(@NotNull Boolean approved, @Size(max=500) String reason)
UpdateResourcesRequest(@Min(0) Integer currentHp, @Min(0) Integer currentSp, @Min(0) Integer currentKp, @Size(max=500) String reason)
InjuryPayload(@NotNull InjuryType type, @NotBlank @Size(max=100) String region, @NotNull InjurySeverity severity,
    @Min(0) Integer damage /* usado só em BLUNT */, Attribute affectedAttribute)
ItemPayload(@NotBlank @Size(max=100) String name, @NotNull @Min(0) Integer quantity, @Size(max=2000) String description)
GmAdjustmentRequest(@NotNull GmAction action, String field, Integer value, @NotBlank @Size(max=500) String reason,
    @Valid InjuryPayload injury, @Valid ItemPayload item, UUID targetId)
RollRequest(@NotBlank @Pattern(regexp="^\\d{1,2}d(4|6|8|10|12|20|100)([+-]\\d{1,3})?$") String expression,
    @NotBlank @Size(max=200) String purpose)
```
Semântica de `GmAdjustmentRequest`:
| action | field | value | outros |
|---|---|---|---|
| GRANT_FIGHT_EXPERIENCE | — | delta > 0 | |
| ADJUST_OVERCOME_POINTS | — | delta (≠0; resultado ≥ 0) | |
| SET_RESOURCE | `HP`/`SP`/`KP` | novo atual (clamp 0..máx efetivo) | |
| SET_MAX | `HP`/`SP`/`KP` | novo máximo ≥ 1 (atual sobe junto só se estava cheio — RN-REC-02; senão clamp) | |
| SET_ATTRIBUTE | nome de `Attribute` | ≥ 0 | |
| SET_OPEN_WOUNDS | — | ≥ 0 | |
| ADD_INJURY | — | — | `injury` obrigatório; `hpMaxReduction = SheetRules.injuryHpReduction(...)`, `limiterReduction = SheetRules.injuryLimiterReduction(...)`; clamp do HP atual |
| TREAT_INJURY / REMOVE_INJURY | — | — | `targetId` = id do ferimento |
| ADJUST_FATIGUE | `PHYSICAL`/`MENTAL` | delta (resultado ≥ 0) | |
| ADJUST_MONEY | — | delta | |
| ADD_ITEM | — | — | `item` obrigatório |
| UPDATE_ITEM | — | — | `targetId` + `item` |
| REMOVE_ITEM | — | — | `targetId` |
Campo/valor faltando ou inválido → `InvalidCharacterSheetException`.

`dto/response`:
```
CharacterSheetResponse(UUID id, UUID campaignId, UUID ownerUserId, String ownerName, String name,
    BuildStatus buildStatus, BodyType bodyType, Dominance dominance, double heightM, double weightKg,
    Map<Attribute,Integer> attributes,       // null se ATTRIBUTES oculta
    SheetDerived derived,                    // null se ATTRIBUTES oculta
    SheetResources resources,                // null se RESOURCES oculta
    SheetFatigue fatigue,                    // null se RESOURCES oculta
    SheetProgress progress,                  // null se RESOURCES oculta
    Integer money,                           // null se INVENTORY oculta
    List<InjuryResponse> injuries,           // null se INJURIES oculta
    List<InventoryItemResponse> inventory,   // null se INVENTORY oculta
    long version)
SheetResources(int hpMax, int effectiveHpMax, int spMax, int kpMax, int currentHp, int currentSp, int currentKp, int openWounds)
SheetFatigue(int physical, FatigueBand physicalBand, int mental, FatigueBand mentalBand)
SheetProgress(int fightExperience, int pendingOvercomePoints)
SheetDerived(Map<Attribute,Integer> mods /* valor null p/ atributo 0 */, Map<Attribute,AttributeBand> bands,
    Map<Attribute,Integer> limiters /* sem TAL */, int hpLimiter, int spLimiter, int kpLimiter,
    int weightMod, int carryKg, boolean criticalState)
InjuryResponse(UUID id, InjuryType type, String region, InjurySeverity severity, int hpMaxReduction,
    int limiterReduction, Attribute affectedAttribute, boolean treated, Instant createdAt)
InventoryItemResponse(UUID id, String name, int quantity, String description)
CharacterSheetSummaryResponse(UUID id, UUID ownerUserId, String ownerName, String name, BuildStatus buildStatus,
    Integer currentHp, Integer effectiveHpMax, Integer currentSp, Integer spMax, Integer currentKp, Integer kpMax,
    Boolean criticalState)   // recursos/critical null se RESOURCES oculta
SheetActivityLogResponse(UUID id, UUID sheetId, UUID actorId, String actorName, ActorRole actorRole,
    ActivityCategory category, String action, String field, String beforeValue, String afterValue,
    UUID rollId, String reason, Instant createdAt)
RollResponse(UUID id, UUID sheetId, UUID actorId, String actorName, String expression, List<Integer> diceResults,
    int modifier, int total, Integer naturalD20, boolean critical, boolean fumble, String purpose, Instant createdAt)
    // naturalD20 só quando expression é exatamente 1d20(+/-n); critical = naturalD20==20; fumble = naturalD20==1
SheetUpdateMessage(SheetEvent event, UUID campaignId, UUID sheetId, ActivityCategory category, String action, String actorName)
    { public enum SheetEvent { SHEET_CREATED, SHEET_UPDATED, ROLL } }
```
Visibilidade: "oculta" = viewer não é Mestre nem dono **e** a seção tem `hiddenByMaster || hiddenBySelf`.

### 8.4 `exception`
```
CharacterSheetNotFoundException()              404 "Ficha não encontrada"
SheetEntryNotFoundException()                  404 "Ferimento ou item não encontrado"
CharacterSheetAlreadyExistsException()         409 "Você já tem uma ficha nesta campanha"
SheetLockedException(String message)           409
InvalidCharacterSheetException(String message) 400
InvalidRollException(String message)           400
org.springframework.orm.ObjectOptimisticLockingFailureException → 409 "A ficha foi alterada por outra pessoa. Recarregue e tente de novo."
```

### 8.5 `service`
`service/rules/SheetRules` — `final class`, construtor privado, só `static` (assinaturas da §3.3, com
`Attribute`/`BodyType`/`ResourceType`/`InjuryType`/`InjurySeverity`/`AttributeBand`/`FatigueBand` do 8.1):
```
static Integer mod(int v); static int modQ(int v); static int half(int m); static AttributeBand band(int v)
static int attributeLimiter(Attribute a, int tal, BodyType bt, int injuryReduction)   // TAL -> Integer.MAX_VALUE
static int statusLimiter(ResourceType r, int tal, BodyType bt)                        // HP/SP 60+tal/2+adj ; KP 8
static int initialHpMax(int con); static int initialSpMax(int con); static int initialKpMax(int con)
static double initialWeight(double heightM, BodyType bt, int con)
static int weightMod(double kg); static int carryKg(int str)
static int effectiveHpMax(int hpMax, List<PermanentInjury> injuries)                 // hpMax − Σ reduções (≥ 1)
static boolean criticalState(int currentHp, int effectiveHpMax, int... attributes)
static FatigueBand fatigueBand(int points)
static int injuryHpReduction(InjurySeverity s, InjuryType t, int hpMax, Integer damage)
static int injuryLimiterReduction(InjurySeverity s)
```
Ajustes de tipo corporal (RN-ATR-05): ENDO +10 CON, −5 DEX, +10 HP; MESO +5 STR, CON, HP, SP; ECTO +10 DEX, +10 SP, −5 CON.
Peso (RN-ATR-08): base 50/40/30; índices min–max ENDO 20–40, MESO 17–33, ECTO 15–26.

`CharacterSheetService(CharacterSheetRepository, CampaignMemberRepository, CharacterSheetVisibilityRepository, SheetActivityLogService, ApplicationEventPublisher)`:
```
CharacterSheetResponse create(UUID campaignId, CreateCharacterSheetRequest req, UUID requesterId)
List<CharacterSheetSummaryResponse> listForCampaign(UUID campaignId, UUID requesterId)
CharacterSheetResponse get(UUID sheetId, UUID requesterId)
CharacterSheetResponse update(UUID sheetId, UpdateCharacterSheetRequest req, UUID requesterId)   // dono, só DRAFT; recalcula máximos/atual = máx
CharacterSheetResponse submit(UUID sheetId, UUID requesterId)                                    // dono, DRAFT -> PENDING_APPROVAL
CharacterSheetResponse approve(UUID sheetId, ApproveCharacterSheetRequest req, UUID requesterId) // M, PENDING_APPROVAL -> COMPLETED | DRAFT
CharacterSheetResponse updateResources(UUID sheetId, UpdateResourcesRequest req, UUID requesterId) // dono ou M; clamp
CharacterSheetResponse applyGmAdjustment(UUID sheetId, GmAdjustmentRequest req, UUID requesterId)  // M
```
Regras: create só por PLAYER (Mestre → 403), 1 por membro (409). Toda mutação: `activityLog.record(...)`
+ `events.publishEvent(new SheetUpdateMessage(...))`. Não-membro → 403. Ficha de outra campanha não existe nesse modelo (sheet → member → campaign).

`SheetActivityLogService(SheetActivityLogRepository, CharacterSheetRepository, CampaignMemberRepository, UserRepository)`:
```
void record(CharacterSheet sheet, UUID actorId, ActorRole role, ActivityCategory category, String action,
            String field, Object before, Object after, UUID rollId, String reason)   // before/after via String.valueOf, null fica null
List<SheetActivityLogResponse> listForSheet(UUID sheetId, UUID requesterId)            // dono ou M
List<SheetActivityLogResponse> listForCampaign(UUID campaignId, UUID sheetId, ActivityCategory category,
            Instant from, Instant to, UUID requesterId)                                // M; filtros opcionais em memória
```
`RollService(RollRepository, CharacterSheetRepository, CampaignMemberRepository, UserRepository, SheetActivityLogService, ApplicationEventPublisher)`:
```
RollResponse roll(UUID sheetId, RollRequest req, UUID requesterId)   // dono ou M; SecureRandom; grava Roll + log ROLL + evento ROLL
public record DiceExpression(int count, int sides, int modifier) {}
public static DiceExpression parse(String expression)                // InvalidRollException se inválida (count 1..20)
```

### 8.6 `websocket`
```
CampaignEventPublisher(SimpMessagingTemplate)
  void publish(UUID campaignId, String channel, Object payload)
  static String destination(UUID campaignId, String channel)   // "/topic/campaign/{id}/{channel}"
SlotEventPublisher — API pública inalterada (publish(SlotUpdateMessage), static destination(UUID)); delega ao CampaignEventPublisher
SheetEventListener — @TransactionalEventListener(phase = AFTER_COMMIT) void on(SheetUpdateMessage m) → publish(m.campaignId(), "sheets", m)
StompAuthChannelInterceptor(JwtService, UserRepository, CampaignMemberRepository)
  CONNECT: token válido E userRepository.existsById(userId)
  SUBSCRIBE: destino precisa casar ^/topic/campaign/([0-9a-fA-F-]{36})/[a-z-]+$ e o usuário ser membro; senão MessagingException
```

### 8.7 `controller`
Rotas exatamente como §3.4, `AuthenticatedUser.id()`, `@Valid`. `POST` de criação e de rolagem → 201; resto 200.
`GET /campaigns/{campaignId}/activity-log` com `@RequestParam(required=false)` `sheetId`, `category`, `from`, `to` (`Instant` ISO-8601).

### 8.8 Frontend
`src/api/characterSheets.js` (mesmo estilo de `api/npcs.js`, retorna `data`):
```
createSheet(campaignId, data)   listSheets(campaignId)   getSheet(sheetId)   updateSheet(sheetId, data)
submitSheet(sheetId)   approveSheet(sheetId, { approved, reason })   updateResources(sheetId, data)
gmAdjust(sheetId, data)   rollDice(sheetId, { expression, purpose })
getSheetActivityLog(sheetId)   getCampaignActivityLog(campaignId, { sheetId, category, from, to })
```
`src/hooks/useWebSocket.js` (feito pelo orquestrador): `useCampaignTopic(campaignId, channel, onMessage)`; `useWebSocket(campaignId, onSlotUpdate)` mantido.
`src/components/sheet/SheetsSection.jsx`: `export default function SheetsSection({ campaignId, isMaster })`.
`src/components/layout/Sidebar.jsx`: novo ícone `sheet` em `ICONS`. `AppShell` mantém exports (`default`, `Surface`, `PageHeader`, `EmptyState`, `SkeletonCard`) e props.
Usuário logado: `useAuth().user.userId`.

---

## 9. Ficha de NPC (F1.1) — contrato congelado

Decisões do usuário (23/10/2026): ficha **por NPC no acervo** (estado compartilhado entre campanhas);
a **ficha nova substitui** os 6 atributos antigos do Mongo (que viram só pré-preenchimento);
jogadores veem a ficha de um NPC visível **por seção** (Mestre escolhe por campanha; padrão = tudo oculto).

### 9.1 Banco / entidades
- `CharacterSheet`: `campaignMember` passa a **opcional** (`optional = true`, coluna nullable, ainda `unique`).
  Novos campos: `@Column(name="npc_id", unique=true) UUID npcId` e `@Column(name="npc_owner_id") UUID npcOwnerId`.
  Exatamente um entre `campaignMember` e `npcId`. Helper `public boolean isNpcSheet() { return npcId != null; }`.
- `SheetActivityLog.campaignId` e `Roll.campaignId` passam a **nullable** (fichas de NPC não têm campanha).
- Nova entidade `NpcSheetVisibility` — tabela `npc_sheet_visibility`:
  `UUID id; @ManyToOne(LAZY, optional=false) @JoinColumn(name="campaign_npc_id") CampaignNpc campaignNpc;
  @Enumerated(STRING) SheetSection section (length 50); boolean visible (default false)`;
  unique `(campaign_npc_id, section)`.
- Seções aplicáveis a NPC: `ATTRIBUTES, RESOURCES, INJURIES, INVENTORY` (constante `NPC_SHEET_SECTIONS` no service).

### 9.2 Repositórios
```
CharacterSheetRepository  + Optional<CharacterSheet> findByNpcId(UUID npcId)
                          + boolean existsByNpcId(UUID npcId)
                          + List<CharacterSheet> findByNpcIdIn(Collection<UUID> npcIds)
CampaignNpcRepository     + List<CampaignNpc> findByNpcId(UUID npcId)
NpcSheetVisibilityRepository extends JpaRepository<NpcSheetVisibility, UUID>
                          List<NpcSheetVisibility> findByCampaignNpcId(UUID campaignNpcId)
                          Optional<NpcSheetVisibility> findByCampaignNpcIdAndSection(UUID campaignNpcId, SheetSection section)
                          @Modifying @Transactional void deleteByCampaignNpcId(UUID campaignNpcId)
```

### 9.3 DTOs
- `CharacterSheetResponse` ganha **no final** o componente `UUID npcId` (null em ficha de jogador). Para ficha de NPC:
  `campaignId = null`, `ownerUserId = npcOwnerId`, `ownerName = null`, `buildStatus = COMPLETED`.
- `NpcResponse` e `NpcSummaryResponse` ganham **no final** `UUID sheetId` (null se o NPC não tem ficha).
- Novo `dto/request/SetNpcSectionVisibilityRequest(@NotNull SheetSection section, @NotNull Boolean visible)`.
- Novo `dto/response/NpcSectionVisibilityResponse(SheetSection section, boolean visible)`.

### 9.4 Exceção
`NpcSheetAlreadyExistsException()` 409 "Este NPC já tem ficha" (orquestrador cria e registra).

### 9.5 Regras de acesso (service)
- **Ficha de NPC via `/character-sheets/{id}/...`** (get, put, resources, gm-adjustments, rolls, activity-log):
  só o dono do NPC (`npcOwnerId`); qualquer outro → `CharacterSheetNotFoundException` (404, não revela).
  O dono age como Mestre (`ActorRole.MASTER`, privilegiado). `update` é livre (sem trava de DRAFT).
  `submit`/`approve` em ficha de NPC → `InvalidCharacterSheetException("Ficha de NPC não passa por aprovação")`.
- **Eventos:** mudanças em ficha de NPC publicam `SHEET_UPDATED` (`SheetUpdateMessage` com `campaignId` de
  cada campanha onde o NPC está associado — um evento por campanha). **Rolagens de NPC não publicam evento**
  (não vazar ação do Mestre); `Roll.campaignId = null`. Log: `campaignId = null`, `actorName` = nome do dono.
- **Visão do jogador** em campanha: NPC associado (404 senão); se o requester não é Mestre da campanha,
  NPC precisa estar `visible` (404) e a seção só aparece se houver linha `visible = true` (padrão oculto).
  Mestre da campanha vê tudo.

### 9.6 Service — `CharacterSheetService`
Construtor passa a ser: `(CharacterSheetRepository, CampaignMemberRepository, CharacterSheetVisibilityRepository,
SheetActivityLogService, ApplicationEventPublisher, CampaignNpcRepository, NpcSheetVisibilityRepository, NpcRepository, UserRepository)`.
Novos métodos públicos:
```
CharacterSheetResponse createForNpc(UUID npcId, CreateCharacterSheetRequest req, UUID requesterId) // dono do NPC (NpcRepository/Mongo); 404 se NPC não existe; 403 se não é dono; 409 NpcSheetAlreadyExists
CharacterSheetResponse getForNpc(UUID npcId, UUID requesterId)                                     // dono; 404 se sem ficha
CharacterSheetResponse getNpcSheetInCampaign(UUID campaignId, UUID npcId, UUID requesterId)         // regras 9.5
List<NpcSectionVisibilityResponse> listNpcSheetVisibility(UUID campaignId, UUID npcId, UUID requesterId) // M; as 4 seções
NpcSectionVisibilityResponse setNpcSheetVisibility(UUID campaignId, UUID npcId, SetNpcSectionVisibilityRequest req, UUID requesterId) // M; publica SHEET_UPDATED
```
Métodos existentes passam a aceitar ficha de NPC conforme 9.5. `SheetActivityLogService` e `RollService` mantêm
construtor e assinaturas; tratam ficha de NPC (dono = `npcOwnerId`; `campaignId` null).
`NpcService`: preenche `sheetId` nas respostas (use `findByNpcIdIn` na listagem) e, em `removeFromCampaign`,
chama `npcSheetVisibilityRepository.deleteByCampaignNpcId(...)` antes de apagar a associação.
Construtor de `NpcService` ganha, **no final**, `CharacterSheetRepository, NpcSheetVisibilityRepository`.

### 9.7 Rotas
| Método | Rota | Quem | Body → Resposta |
|---|---|---|---|
| POST | `/npcs/{npcId}/sheet` | dono do NPC | `CreateCharacterSheetRequest` → 201 `CharacterSheetResponse` |
| GET | `/npcs/{npcId}/sheet` | dono | `CharacterSheetResponse` |
| GET | `/campaigns/{cid}/npcs/{npcId}/sheet` | membro | `CharacterSheetResponse` (seções filtradas) |
| GET | `/campaigns/{cid}/npcs/{npcId}/sheet/visibility` | M | `NpcSectionVisibilityResponse[]` |
| PATCH | `/campaigns/{cid}/npcs/{npcId}/sheet/visibility` | M | `SetNpcSectionVisibilityRequest` → `NpcSectionVisibilityResponse` |
Ficam em `CharacterSheetController`.

### 9.8 Frontend
`api/characterSheets.js` + `createNpcSheet(npcId, data)`, `getNpcSheet(npcId)`, `getCampaignNpcSheet(campaignId, npcId)`,
`getNpcSheetVisibility(campaignId, npcId)`, `setNpcSheetVisibility(campaignId, npcId, { section, visible })`.

`components/sheet/NpcSheetPanel.jsx`:
`export default function NpcSheetPanel({ campaignId, npc, isMaster })` — `npc` = `NpcResponse` (tem `id`, `name`,
`ownerId`, `attributes` legados, `sheetId`). Dono (`npc.ownerId === useAuth().user.userId`): sem ficha → botão
"Criar ficha" abre `CharacterSheetForm` pré-preenchido (forca→str, destreza→dex, constituicao→con, inteligencia→intl,
mental→men, talento→tal, cha vazio; nome = npc.name); com ficha → `CharacterSheetView` (modo NPC: sem enviar/aprovar,
edição livre, painel do Mestre, rolagens) + toggles por seção da campanha (só se `isMaster`). Não-dono: `getCampaignNpcSheet`
read-only; 404 → "Nenhuma informação da ficha disponível". Tempo real: `useCampaignTopic(campaignId, 'sheets', cb)` e recarrega
quando `msg.sheetId` for a ficha aberta. `onChanged` opcional para avisar o pai (ex.: atualizar `sheetId`).

`components/npc`: no modal de detalhe do NPC, nova seção "Ficha" renderizando
`<NpcSheetPanel campaignId={campaignId} npc={detail} isMaster={isMaster} onChanged={...} />`; quando `sheetId` existe,
**esconde o bloco de atributos legados** (detalhe e `EditNpcForm`, que mostra "Atributos agora ficam na ficha").

---

## 10. Backlog visual (pedido do usuário, 23/10/2026)

| # | Onde | Pedido |
|---|---|---|
| V-1 | `components/sheet` (barras HP/SP/PQ) | SP **marrom**, PQ **laranja** (HP segue acento). As 3 cores variam por tema: **mais escuras no claro, mais claras no escuro** (tokens `--meter-hp/sp/kp` em `index.css`). |
| V-2 | `components/sheet` (Desgaste) | Barras que enchem com os pontos (0→12+). Físico: gradiente **verde→vermelho**; Mental: **roxo→vermelho**; a cor acompanha o nível. |
| V-3 | `components/sheet` (progresso) | EL e PS como **blocos pequenos**: EL verde, PS amarelo. |
| V-4 | `components/sheet` (ActivityLogPanel, RollBox) e `components/log` | Logs e rolagens **com filtro** e leitura amigável (frases em pt-BR em vez de `FIELD before→after`, agrupamento por dia, ícone por categoria). |
| V-5 | `components/layout/Sidebar.jsx` | **Bug:** depois de clicar num item e tirar o mouse, a sidebar não recolhe (só ao sair da janela). Provável causa: `focus-within` mantém expandida após clique — recolher ao `mouseleave` e só expandir por foco via teclado (`:focus-visible`). |
| V-6 | `components/sheet` (atributos) | Distinção clara entre **valor**, **mod** e **limitador** (hierarquia visual: valor grande, mod em selo, limitador como barra/teto). |
| V-7 | `pages/auth/Login.jsx`, `Register.jsx` | Linhas de escape **até a borda da tela** (sem fade nas pontas, centro limpo mantido) e os **pontos atrás** das linhas. |
| V-8 | `components/sheet` (identidade) | Altura, tipo corporal, dominância, tamanho e peso com tratamento visual melhor (ex.: chips com ícone, régua de altura, silhueta do tipo corporal). |

Tokens congelados para o backlog (em `index.css`, por tema): `--meter-hp`, `--meter-sp` (marrom), `--meter-kp` (laranja),
`--fatigue-physical-from/to` (verde→vermelho), `--fatigue-mental-from/to` (roxo→vermelho), `--chip-el` (verde), `--chip-ps` (amarelo).
Escuro = tons claros; claro = tons escuros.

---

## 11. F2 — Construção Inicial, catálogos de traços/clãs e regras da campanha (contrato congelado)

Decisões do usuário (23/10/2026): catálogo **global + extensão por campanha**; criação livre de ficha de jogador
vira **regra opcional da campanha** (`FREE_CREATION`; padrão = wizard). Seed: o usuário enviará parte dos dados
depois; o resto entra pelo CRUD.

### 11.1 Catálogo (MongoDB)
Coleções `traitDefinitions` e `clans`. `campaignId == null` → item **global** (sistema); `campaignId != null` → extensão
daquela campanha. Visão de uma campanha = globais ∪ da campanha; em sorteio, item da campanha com mesma
`(rarity, rollNumber)` (traço) ou mesmo `rollNumber` (clã) **substitui** o global.
Editar global: e-mail do usuário em `app.catalog.editors` (env `CATALOG_EDITORS`, lista separada por vírgula).
Editar item de campanha: Mestre da campanha. Toda edição incrementa `version`.

```
entity/TraitDefinition  @Document("traitDefinitions")
  UUID id (UUID.randomUUID() na app); UUID campaignId; String name; TraitRarity rarity; TraitCategory category;
  Integer rollNumber (1–20; 1–10 se DIVINE; null = reposição "X"); String description;
  List<TraitEffect> effects; BuildGains buildGains; boolean developable; boolean upgradable; int version; Instant updatedAt
entity/Clan  @Document("clans")
  UUID id; UUID campaignId; String name; Integer rollNumber (1–20, null = só por escolha); String description;
  BuildGains buildGains; List<String> heritage (itens/conhecimentos/specs hereditárias, texto); int version; Instant updatedAt
entity/TraitEffect(String text)                                  — classe embutida (Lombok @Data @NoArgsConstructor @AllArgsConstructor)
entity/BuildGains  — embutida: Map<Attribute,Integer> attributeFlat; Map<Attribute,Integer> attributePercent;
  int maxHp; int maxSp; int maxKp; Map<String,Integer> limiters (chaves = nome de Attribute ou HP/SP/KP); int freePoints
enum TraitRarity { COMMON, RARE, EPIC, LEGENDARY, DIVINE }
enum TraitCategory { PHYSICAL, MENTAL, SPIRITUAL, HYBRID, HEREDITARY }
repository/TraitDefinitionRepository extends MongoRepository<TraitDefinition, UUID>
  List<TraitDefinition> findByCampaignIdIsNull(); List<TraitDefinition> findByCampaignId(UUID campaignId)
repository/ClanRepository extends MongoRepository<Clan, UUID>
  List<Clan> findByCampaignIdIsNull(); List<Clan> findByCampaignId(UUID campaignId)
```
**Seed:** `backend/src/main/resources/catalog/traits.json` e `clans.json` (arrays no formato do import, inicialmente `[]`),
carregados no boot por `CatalogSeeder` (`ApplicationRunner`) **só se não houver itens globais** na coleção.
**Import:** `CatalogImportRequest(List<TraitPayload> traits, List<ClanPayload> clans)`; upsert por `(name, rarity)` (traço) /
`name` (clã) no mesmo escopo.

### 11.2 Regras opcionais da campanha (PostgreSQL)
```
entity/OptionalRule { MAX_RANDOM_ATTRIBUTES, TRAIT_METHOD, DIVINE_INTERVENTION, MAX_RANDOM_CLAN, HEIGHT_CATEGORIES,
  KNOCKOUT_SEQUEL, BURNOUT, CRITICAL_CLASH, EVENTUAL_TRAITS, EXTRA_SPEC_SLOTS, GM_REROLLS, FREE_CREATION }
entity/CampaignRuleSetting  table campaign_rule_settings, unique(campaign_id, rule_key)
  UUID id; @ManyToOne(LAZY) Campaign campaign; @Enumerated OptionalRule ruleKey (col rule_key, 40);
  boolean enabled; @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition="jsonb") Map<String,String> params
repository/CampaignRuleSettingRepository: List<CampaignRuleSetting> findByCampaignId(UUID);
  Optional<CampaignRuleSetting> findByCampaignIdAndRuleKey(UUID, OptionalRule)
```
Padrões sem linha: tudo desligado, exceto `GM_REROLLS` (ligado) e `TRAIT_METHOD` (ligado, `method=CLASSIC`).
Params: `TRAIT_METHOD.method ∈ {CLASSIC, BALANCED_CHOICE, PRODIGY}`; `DIVINE_INTERVENTION.mode ∈ {ROLL_100_GRANTS_DIVINE, FREE_DIVINE_TRAIT}`.

### 11.3 Ficha: traços, clãs e ajustes de limitador
- `BuildStatus` ganha `IN_BUILD` (valores: `IN_BUILD, DRAFT, PENDING_APPROVAL, COMPLETED`).
- `CharacterSheet` ganha `@JdbcTypeCode(SqlTypes.JSON) @Column(name="limiter_adjustments", columnDefinition="jsonb") @Builder.Default Map<String,Integer> limiterAdjustments = new HashMap<>()`
  (chaves: nome de `Attribute`, `HP`, `SP`, `KP`) e as listas `traits` e `clans` (cascade ALL, orphanRemoval):
```
entity/CharacterTrait  table character_traits
  UUID id; @ManyToOne(LAZY) @JoinColumn(name="sheet_id") CharacterSheet sheet; UUID traitId; int traitVersion;
  String name; TraitRarity rarity; TraitCategory category; String description (text);
  @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition="jsonb") BuildGains buildGains;   // snapshot (RN-TRAIT-02)
  TraitAcquisition acquiredIn; boolean gainsApplied; Instant acquiredAt (@PrePersist)
enum TraitAcquisition { INITIAL_BUILD, CAMPAIGN }
entity/CharacterClan  table character_clans
  UUID id; @ManyToOne(LAZY) sheet; UUID clanId; int clanVersion; String name; BuildGains buildGains (jsonb snapshot); Instant acquiredAt
```
- `SheetRules.attributeLimiter(...)` e `statusLimiter(...)` passam a receber um `int adjustment` extra (último parâmetro), somado.
  `CharacterSheetService.toResponse` passa `limiterAdjustments.getOrDefault(key, 0)`.
- `GmAction` ganha `ADD_TRAIT` (`targetId` = id do catálogo; snapshot com `acquiredIn=CAMPAIGN`, `gainsApplied=false`,
  só efeitos — RN-TRAIT-01) e `REMOVE_TRAIT` (`targetId` = id do `CharacterTrait`). Log categoria `PROGRESSION`.
- `CharacterSheetResponse` ganha **no final**: `List<CharacterTraitResponse> traits, List<CharacterClanResponse> clans`
  (null se seção `TRAITS` oculta). `CharacterTraitResponse(UUID id, UUID traitId, int traitVersion, String name,
  TraitRarity rarity, TraitCategory category, String description, TraitAcquisition acquiredIn, boolean gainsApplied)`;
  `CharacterClanResponse(UUID id, UUID clanId, String name, List<String> heritage)` — heritage lido do catálogo na hora (pode ser vazio).

### 11.4 Construção Inicial (PostgreSQL)
```
entity/BuildStep { IDENTITY, TRAITS, BLOOD_TEST, ATTRIBUTES, FREE_POINTS, REVIEW }
entity/TraitSlotStatus { ROLLED, PENDING_GM, RESOLVED }
entity/CharacterBuild  table character_builds
  @Id UUID sheetId; @MapsId @OneToOne(LAZY) @JoinColumn(name="sheet_id") CharacterSheet sheet;
  @Enumerated BuildStep step;
  @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition="jsonb") BuildSettings settings;   // snapshot das regras no início
  @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition="jsonb") BuildState state;
  @Version Long version; Instant updatedAt
entity/BuildSettings (record) (String traitMethod, String divineMode /* null = desligado */, boolean randomClan,
  boolean randomAttributes, boolean gmRerolls)
entity/BuildState (classe mutável, Lombok @Data @NoArgsConstructor; listas/mapas inicializados vazios):
  Integer traitCountRoll; List<TraitSlot> traitSlots; boolean traitsConfirmed;
  Integer bloodTestRoll; int clanSlots; List<UUID> clanIds; List<Integer> clanRolls; boolean clansConfirmed;
  List<Integer> attributeDice; Map<Attribute,Integer> assignment /* atributo -> índice do dado */; boolean attributesConfirmed;
  Map<Attribute,Integer> freePoints; boolean freePointsConfirmed
entity/TraitSlot (classe @Data): int index; Integer rarityRoll; TraitRarity rarity; Integer tableRoll; UUID traitId;
  String traitName; TraitSlotStatus status; String note; boolean divineBonus
repository/CharacterBuildRepository extends JpaRepository<CharacterBuild, UUID>
```
Toda rolagem do wizard passa por `RollService.rollInternal(...)` (novo, ver 11.6) e é logada (`ROLL`/`BUILD`).

**Regras (puras) — `service/rules/BuildRules`** (static, testável):
```
static TraitRarity rarityFor(int d100, boolean divineOn100)        // 1–40 C, 41–70 R, 71–90 E, 91–99 L, 100 L ou DIVINE
static TraitRarity promoteForProdigy(TraitRarity r)                 // COMMON -> RARE
static int clanSlotsFor(int d20, boolean lowThreshold)              // lowThreshold (Clássico sem Épico+): 15–19 ->1; senão 18–19 ->1; 20 ->2; resto 0
static boolean crossesBarrier(int before, int after)                // cruza 3→4, 6→7 ou 9→10
static Map<Attribute, AttributeLayer> layers(Map<Attribute,Integer> base, List<BuildGains> clanGains,
        List<BuildGains> traitGains, Map<Attribute,Integer> freePoints)
static int freePointsAvailable(Dominance d, List<BuildGains> allGains, boolean randomAttributes) // 6 + Σ freePoints − (LEFT ? 3 : 0); 0 se randomAttributes
static ResourceMaxes resources(int finalCon, List<BuildGains> allGains)  // HP 10+CON, SP 5+CON/2, PQ 3+modQ(CON) + Σ maxHp/maxSp/maxKp
static Map<String,Integer> limiterAdjustments(List<BuildGains> allGains)
record AttributeLayer(int base, int clanFlat, int traitFlat, int percent, int afterPercent, int freePoints, int finalValue)
   // afterPercent = floor((base+clanFlat+traitFlat) * (100+percent) / 100); final = afterPercent + freePoints
record ResourceMaxes(int hpMax, int spMax, int kpMax)
```
Validação dos pontos livres: soma ≤ disponível; nenhum atributo pode ir de `afterPercent` para `final` cruzando barreira.
`MAX_RANDOM_ATTRIBUTES`: 7d20 em ordem STR→DEX→CON→INT→CHA→MEN→TAL, sem atribuição e sem pontos livres (passo `FREE_POINTS` pulado).

**Fluxo dos traços:**
- `CLASSIC`: 1d4 = quantidade; por slot 1d100 → raridade; 1d20 (1d10 se DIVINE) → `rollNumber`; resolve no catálogo da campanha.
  Ausente no catálogo, repetido, ou LEGENDARY 20 → `PENDING_GM` (Mestre escolhe traço "X" ou manda rerrolar, se `gmRerolls`).
- `PRODIGY`: 1d2+1 slots; raridade com mínimo RARE. (Spec Comum extra: fora do escopo — specs ainda não modeladas.)
- `BALANCED_CHOICE`: jogador escolhe exatamente 1 COMMON, 1 RARE, 1 EPIC (`traitIds`), sem rolagem.
- `DIVINE_INTERVENTION` `FREE_DIVINE_TRAIT`: slot extra DIVINE com 1d10 (`divineBonus=true`). `ROLL_100_GRANTS_DIVINE`: 100 no d100 vira DIVINE.
- Teste de Sangue: 1d20 → `clanSlots` (`lowThreshold` = método CLASSIC e nenhum traço EPIC+). `randomClan`: 1d20 por slot no
  `rollNumber` dos clãs (repete se repetido); senão o jogador escolhe `clanSlots` clãs.

**Voltar** (`back`): define `step` anterior e desmarca os `*Confirmed` dos passos seguintes; **dados já rolados são mantidos**
(o limiar do Teste de Sangue e as camadas são recalculados).

**Envio** (`submit`, só em `REVIEW`): grava na ficha atributos finais, máximos/atuais, `limiterAdjustments`, cria `CharacterTrait`
(INITIAL_BUILD, gainsApplied=true) e `CharacterClan`; `buildStatus = PENDING_APPROVAL`. Aprovação usa o endpoint existente;
rejeição volta para `IN_BUILD` (passo `REVIEW`) quando existe build. Mestre pode `reopen` uma ficha COMPLETED com build.

### 11.5 Rotas
| Método | Rota | Quem | Body → Resposta |
|---|---|---|---|
| GET | `/campaigns/{cid}/rules` | membro | `CampaignRuleResponse[]` (todas as regras, com padrão) |
| PUT | `/campaigns/{cid}/rules/{ruleKey}` | M | `UpdateCampaignRuleRequest(Boolean enabled*, Map<String,String> params)` → `CampaignRuleResponse(OptionalRule ruleKey, boolean enabled, Map<String,String> params)` |
| GET | `/catalog/permissions` | auth | `CatalogPermissionsResponse(boolean globalEditor)` |
| GET | `/catalog/traits?campaignId=` | auth (membro se campaignId) | `TraitDefinitionResponse[]` |
| POST | `/catalog/traits` | editor global | `TraitPayload` → 201 `TraitDefinitionResponse` |
| POST | `/campaigns/{cid}/catalog/traits` | M | `TraitPayload` → 201 |
| PUT / DELETE | `/catalog/traits/{id}` | editor (global) ou M (da campanha do item) | `TraitPayload` → 200 / 204 |
| (idem) | `/catalog/clans`, `/campaigns/{cid}/catalog/clans`, `/catalog/clans/{id}` | idem | `ClanPayload` / `ClanResponse` |
| POST | `/catalog/import` | editor | `CatalogImportRequest` → `CatalogImportResponse(int traits, int clans)` |
| POST | `/campaigns/{cid}/catalog/import` | M | idem |
| POST | `/campaigns/{cid}/character-sheets/build` | PLAYER sem ficha | `StartBuildRequest(name*)` → 201 `CharacterBuildResponse` |
| GET | `/character-sheets/{id}/build` | dono ou M | `CharacterBuildResponse` |
| PUT | `/character-sheets/{id}/build/identity` | dono | `BuildIdentityRequest(bodyType*, dominance*, heightM* 1.00–2.50, weightKg?)` |
| POST | `/character-sheets/{id}/build/traits/roll` | dono | — |
| PUT | `/character-sheets/{id}/build/traits/choice` | dono | `TraitChoiceRequest(List<UUID> traitIds*)` |
| POST | `/character-sheets/{id}/build/traits/slots/{index}/resolve` | M | `ResolveTraitSlotRequest(UUID traitId, Boolean reroll, String reason*)` |
| POST | `/character-sheets/{id}/build/blood-test/roll` | dono | — |
| PUT | `/character-sheets/{id}/build/clans` | dono | `ClanChoiceRequest(List<UUID> clanIds*)` |
| POST | `/character-sheets/{id}/build/clans/roll` | dono | — (só `randomClan`) |
| POST | `/character-sheets/{id}/build/attributes/roll` | dono | — |
| PUT | `/character-sheets/{id}/build/attributes/assignment` | dono | `AttributeAssignmentRequest(Map<Attribute,Integer> assignment*)` (permutação de 0..6) |
| PUT | `/character-sheets/{id}/build/free-points` | dono | `FreePointsRequest(Map<Attribute,Integer> freePoints*)` |
| POST | `/character-sheets/{id}/build/steps/{step}/confirm` | dono | — avança para o próximo passo |
| POST | `/character-sheets/{id}/build/back` | dono | `BuildBackRequest(BuildStep step*)` |
| POST | `/character-sheets/{id}/build/submit` | dono | — → `CharacterSheetResponse` |
| POST | `/character-sheets/{id}/build/reopen` | M | `ReopenBuildRequest(String reason*)` → `CharacterBuildResponse` |
Todas as mutações do wizard retornam `CharacterBuildResponse`, exceto submit. Fora de `IN_BUILD` → `SheetLockedException`.
Passo errado (ex.: rolar atributos antes de confirmar traços) → `InvalidBuildStepException` (400).
`POST /campaigns/{cid}/character-sheets` (criação livre) só se `FREE_CREATION` ligada; senão `InvalidCharacterSheetException("Esta campanha usa a Construção Inicial")`.

`CharacterBuildResponse(UUID sheetId, String name, BuildStep step, BuildSettings settings, BodyType bodyType, Dominance dominance,
  Double heightM, Double weightKg, Integer traitCountRoll, List<TraitSlot> traitSlots, boolean traitsConfirmed,
  Integer bloodTestRoll, int clanSlots, List<ClanResponse> clans, boolean clansConfirmed,
  List<Integer> attributeDice, Map<Attribute,Integer> assignment, boolean attributesConfirmed,
  Map<Attribute, BuildRules.AttributeLayer> layers /* null até haver dados */, int freePointsAvailable,
  Map<Attribute,Integer> freePoints, boolean freePointsConfirmed, BuildRules.ResourceMaxes resources /* null até atributos */,
  BuildStatus buildStatus)`
`TraitDefinitionResponse(UUID id, UUID campaignId, String name, TraitRarity rarity, TraitCategory category, Integer rollNumber,
  String description, List<TraitEffect> effects, BuildGains buildGains, boolean developable, boolean upgradable, int version)`
`ClanResponse(UUID id, UUID campaignId, String name, Integer rollNumber, String description, BuildGains buildGains, List<String> heritage, int version)`
`TraitPayload(name* ≤100, rarity*, category*, rollNumber (1–20), description ≤4000, List<TraitEffect> effects, BuildGains buildGains, Boolean developable, Boolean upgradable)`
`ClanPayload(name* ≤100, rollNumber (1–20), description ≤4000, BuildGains buildGains, List<String> heritage)`

### 11.6 Services (assinaturas congeladas)
```
CampaignRuleService(CampaignRuleSettingRepository, CampaignMemberRepository, CampaignRepository)
  List<CampaignRuleResponse> list(UUID campaignId, UUID requesterId)
  CampaignRuleResponse update(UUID campaignId, OptionalRule rule, UpdateCampaignRuleRequest req, UUID requesterId)
  BuildSettings buildSettings(UUID campaignId)            // usado pelo wizard
  boolean isEnabled(UUID campaignId, OptionalRule rule)
CatalogService(TraitDefinitionRepository, ClanRepository, CampaignMemberRepository, UserRepository,
               @Value("${app.catalog.editors:}") String editors)
  CatalogPermissionsResponse permissions(UUID requesterId)
  List<TraitDefinitionResponse> listTraits(UUID campaignIdOrNull, UUID requesterId)
  TraitDefinitionResponse createTrait(UUID campaignIdOrNull, TraitPayload p, UUID requesterId)
  TraitDefinitionResponse updateTrait(UUID id, TraitPayload p, UUID requesterId); void deleteTrait(UUID id, UUID requesterId)
  (idem listClans/createClan/updateClan/deleteClan)
  CatalogImportResponse importCatalog(UUID campaignIdOrNull, CatalogImportRequest req, UUID requesterId)
  List<TraitDefinition> traitsForCampaign(UUID campaignId)   // global ∪ campanha com substituição
  List<Clan> clansForCampaign(UUID campaignId)
  Optional<TraitDefinition> findTrait(UUID id); Optional<Clan> findClan(UUID id)
CatalogSeeder(TraitDefinitionRepository, ClanRepository, ObjectMapper) implements ApplicationRunner
CharacterBuildService(CharacterBuildRepository, CharacterSheetRepository, CampaignMemberRepository, CampaignRuleService,
                      CatalogService, RollService, SheetActivityLogService, ApplicationEventPublisher)
  CharacterBuildResponse start(UUID campaignId, StartBuildRequest req, UUID requesterId)
  CharacterBuildResponse get(UUID sheetId, UUID requesterId)
  setIdentity / rollTraits / chooseTraits / resolveTraitSlot / rollBloodTest / chooseClans / rollClans /
  rollAttributes / assignAttributes / setFreePoints / confirmStep(UUID sheetId, BuildStep step, UUID requesterId) /
  back / reopen — todos (UUID sheetId, <request se houver>, UUID requesterId) → CharacterBuildResponse
  CharacterSheetResponse submit(UUID sheetId, UUID requesterId)
RollService + public Roll rollInternal(CharacterSheet sheet, String expression, String purpose, UUID actorId, ActorRole role)
  // grava Roll + log ROLL, NÃO publica evento; parse igual ao roll()
CharacterSheetService: + public CharacterSheetResponse toResponseFor(CharacterSheet sheet, UUID requesterId)  // reuso no submit
  create(...) checa FREE_CREATION via CampaignRuleService (novo último parâmetro do construtor: CampaignRuleService, CatalogService)
  approve(...) rejeição: IN_BUILD se existir CharacterBuild (CharacterBuildRepository — também no construtor, depois de CatalogService)
```
Exceção nova: `InvalidBuildStepException(String message)` 400 (orquestrador cria e registra).
Propriedade: `app.catalog.editors=${CATALOG_EDITORS:}` (orquestrador adiciona em `application.properties.example`, compose e `.env.example`).

### 11.7 Frontend
- `api/catalog.js`: `getCatalogPermissions()`, `listTraits(campaignId?)`, `createTrait(campaignId|null, data)`, `updateTrait(id, data)`,
  `deleteTrait(id)`, `listClans(campaignId?)`, `createClan(campaignId|null, data)`, `updateClan(id, data)`, `deleteClan(id)`,
  `importCatalog(campaignId|null, data)`.
- `api/campaignRules.js`: `listRules(campaignId)`, `updateRule(campaignId, ruleKey, { enabled, params })`.
- `api/characterBuild.js`: `startBuild(campaignId, { name })`, `getBuild(sheetId)`, `setIdentity`, `rollTraits`, `chooseTraits`,
  `resolveTraitSlot(sheetId, index, data)`, `rollBloodTest`, `chooseClans`, `rollClans`, `rollAttributes`, `assignAttributes`,
  `setFreePoints`, `confirmStep(sheetId, step)`, `goBack(sheetId, step)`, `submitBuild(sheetId)`, `reopenBuild(sheetId, { reason })`.
- `components/build/` (novo): `BuildWizard.jsx` — `export default function BuildWizard({ campaignId, sheetId, isMaster, onDone })`;
  um componente por passo; trilha de passos travada; cada rolagem mostra dados e animação `.onomatopoeia`; painel de camadas
  (base → clã → traços → % → livres → final); Mestre vê botões de resolver/rerrolar slots `PENDING_GM`.
- `components/catalog/` (novo): `CatalogManager.jsx` — `export default function CatalogManager({ campaignId, isMaster })`: abas
  Traços/Clãs, lista com filtro por raridade/escopo (Sistema/Campanha), formulário com editor de `buildGains`, importação de JSON
  (arquivo lido no cliente → `importCatalog`). Editar global só se `globalEditor`.
- `components/campaign/RuleSettings.jsx` (novo): `export default function RuleSettings({ campaignId, isMaster })` — toggles + params.
- `components/sheet`: `SheetsSection` usa `listRules` para decidir: `FREE_CREATION` → formulário atual; senão "Criar minha ficha"
  chama `startBuild` e abre `BuildWizard`; fichas `IN_BUILD` abrem o wizard. `CharacterSheetView` mostra traços e clãs (seção TRAITS)
  e o Mestre tem `ADD_TRAIT`/`REMOVE_TRAIT` no painel (selecionando do catálogo), e "Reabrir construção".
- `pages/campaign/CampaignDetail.jsx`: aba **Catálogo** (ícone `book`, todos os membros; edição conforme permissão) e
  `RuleSettings` dentro de Configurações (Mestre).
- `components/layout/Sidebar.jsx`: ícone `book`.

### 11.8 Formato do seed / importação (para o usuário preencher)
```json
{
  "traits": [
    {
      "name": "Punhos Firmes", "rarity": "COMMON", "category": "PHYSICAL", "rollNumber": 1,
      "description": "Texto do traço.",
      "effects": [{ "text": "Efeito permanente descrito." }],
      "buildGains": {
        "attributeFlat": { "STR": 2 }, "attributePercent": { "TAL": 10 },
        "maxHp": 5, "maxSp": 0, "maxKp": 0,
        "limiters": { "SP": 10, "DEX": 5 }, "freePoints": 1
      },
      "developable": false, "upgradable": false
    }
  ],
  "clans": [
    { "name": "Clã Kuroi", "rollNumber": 1, "description": "Texto do clã.",
      "buildGains": { "attributeFlat": { "CON": 3 } }, "heritage": ["Item herdado", "Conhecimento do clã"] }
  ]
}
```
`rarity`: COMMON, RARE, EPIC, LEGENDARY, DIVINE · `category`: PHYSICAL, MENTAL, SPIRITUAL, HYBRID, HEREDITARY ·
atributos: STR, DEX, CON, INT, CHA, MEN, TAL · `rollNumber` 1–20 (1–10 em DIVINE; omitir = reposição "X").

---

## 12. Seeds reais: traços (teste) + artes clássicas (contrato congelado)

Fonte: `contexto/` (export do Notion, **gitignored**). Pedido do usuário (23/10/2026): traços estão
desatualizados mas servem para teste; **só as 11 artes clássicas** entram (as demais estão fora de forma).
Traços podem ter **requisitos** e ser ou não **desenvolvíveis**.

### 12.1 Traço — campos novos (append no fim)
- `entity/TraitDefinition`: `TraitActivation activation; String requirements; String developmentRequirements`.
- `enum TraitActivation { PASSIVE, ACTIVE }`.
- `TraitPayload` e `TraitDefinitionResponse` ganham **no final**: `TraitActivation activation, String requirements, String developmentRequirements`
  (texto ≤2000, `^[^<>]*$` não se aplica — pode conter `<`? não: texto limpo, sem tags).
- Requisitos são **texto** (exibidos; o Mestre decide). Validação automática fica para quando o formato for estruturado.

### 12.2 Arte marcial (MongoDB `martialArts`)
```
entity/MartialArt  @Document("martialArts")
  UUID id; UUID campaignId (null = global); String name; ArtCategory category; String rank; String martialType;
  List<String> attackSpecializations; List<String> reactionSpecializations; String requirement; String description;
  List<ArtRule> sections /* Critério de Vitória, Técnicas Emprestadas, etc. (name = título) */;
  List<ArtPassive> passives; List<ArtBase> bases; List<ArtTechnique> techniques; int version; Instant updatedAt
entity/ArtRule (@Data, no-args/all-args): String name; String text
entity/ArtPassive (@Data): String name; String group /* título do bloco, ex. "Kumite (組手)" */; String description
entity/ArtBase (@Data): String name; Graduation graduation; String description; List<ArtRule> rules; String effect;
  String restrictions; String function; String assembly; Integer complexity; Integer trainingPoints
entity/ArtTechnique (@Data): String name; Graduation graduation; String group; String description; List<ArtRule> rules;
  String effect; String restrictions; String range; String side; String assembly; Integer complexity;
  Integer trainingPoints; String spCost /* texto, ex. "21 + 10/turno" */; Integer spCostBase /* inteiro inicial */
enum Graduation { GENERIC, BEGINNER, INTERMEDIATE, MASTER }
enum ArtCategory { CLASSIC, CREATED, LOST, CLAN, VARIATION }
repository/MartialArtRepository extends MongoRepository<MartialArt, UUID>
  List<MartialArt> findByCampaignIdIsNull(); List<MartialArt> findByCampaignId(UUID campaignId)
```
`dto/request/MartialArtPayload` = todos os campos de `MartialArt` exceto `id, campaignId, version, updatedAt` (name* ≤100, category*).
`dto/response/MartialArtSummaryResponse(UUID id, UUID campaignId, String name, ArtCategory category, String martialType,
  List<String> attackSpecializations, List<String> reactionSpecializations, int bases, int techniques, int version)`.
`dto/response/MartialArtResponse` = todos os campos de `MartialArt`.
`CatalogImportRequest` ganha **no final** `List<MartialArtPayload> arts`; `CatalogImportResponse` ganha **no final** `int arts`.
Upsert de arte por `name` no escopo.

### 12.3 Rotas e service
| Método | Rota | Quem | Resposta |
|---|---|---|---|
| GET | `/catalog/arts?campaignId=` | auth (membro se campaignId) | `MartialArtSummaryResponse[]` (globais ∪ campanha; campanha substitui global de mesmo nome) |
| GET | `/catalog/arts/{id}` | auth | `MartialArtResponse` |
| DELETE | `/catalog/arts/{id}` | editor (global) ou M (campanha) | 204 |
Criação/edição de arte: **por importação** nesta fase (editor de Criação Marcial é F3).
`CatalogService` + `listArts(UUID campaignIdOrNull, UUID requesterId)`, `getArt(UUID id, UUID requesterId)`,
`deleteArt(UUID id, UUID requesterId)`; `importCatalog` trata `arts`. Construtor ganha **no final** `MartialArtRepository`.
`CatalogSeeder` ganha **no final** `MartialArtRepository` e carrega `catalog/arts.json` (List<MartialArtPayload>) se não houver artes globais.

### 12.4 Conversor (reexecutável)
`scripts/notion_seed.py` (Python 3, só stdlib): lê `contexto/` e grava
`backend/src/main/resources/catalog/traits.json` e `arts.json` no formato do import.
- **Traços:** só arquivos **diretos** de cada pasta de raridade (Comum/Raro/Épico/Lendário/Divino); subpáginas (variações como
  Sayajin, Caninutropia) viram uma linha de efeito "Variações: …" no traço pai. Categoria = seção do índice da raridade onde o nome
  aparece primeiro (FISICO→PHYSICAL, MENTAL, HIBRIDO→HYBRID, ESPIRITUAL→SPIRITUAL; ausente → HYBRID). `rollNumber` = ordem de
  primeira aparição no índice (sem duplicatas), 1..20 (DIVINE 1..10); excedentes → null (reposição "X"). Estatísticas: Rank, Tipo
  (Passivo/Passiva→PASSIVE, Ativo/Ativa→ACTIVE), Desenvolvível/Aprimorável (Sim/Não), Requisitos, Requisitos para Desen.
  Ganhos: cada bloco "➥ Título" vira um `effect` (texto); linhas "+N [pontos] de/em <atributos>" (lista com vírgula/"e", "todos os
  atributos", "atributos físicos" = FOR/DES/CON), "+N% de …", "+N de HP/HPmax/SP/PQ/SA" viram `buildGains` (SA ignorado → só texto).
  Atributos pt-BR: Força→STR, Destreza→DEX, Constituição→CON, Inteligência→INT, Carisma→CHA, Mental→MEN, Talento→TAL.
- **Artes:** só `Clássicas/`. Índice da arte: Descrição, Rank, Tipo (→ `martialType`), Especialização de Ataque/Reação (listas
  separadas por vírgula/"e"), Requisito(s), e cada `### SEÇÃO` restante → `sections`. Subpáginas: blocos "➥ Nome" dentro de cada
  `<aside>` (título do aside = `group`); bloco com **Função** → base; com **Gasto de Uso** ou **Alcance** → técnica; senão → passiva.
  Graduação: subtítulo `<u>***Iniciante|Intermediário|Mestre***</u>` vigente, senão nome do arquivo; nada → GENERIC.
  Linhas `**Nome:** texto` viram `rules`, exceto Efeito/Restrições (campos próprios).
- Limpeza de texto: remover `ㅤ` (U+3164), `<u>`, `<aside>`, imagens, links do Notion, `**`, crases e `>` de citação; manter quebras.
- Imprime um resumo (contagens por raridade/arte, traços sem rollNumber, blocos não classificados).

---

## 13. Refinos do catálogo + contas de teste (contrato congelado)

Pedidos do usuário (23/10/2026), resumidos:
1. Tags de traço na cor da raridade: Comum cinza, Raro azul, Épico roxo, Lendário laranja, Divino rosa.
2. Variações de traço acessíveis (hoje viram só uma linha de texto).
3. Bullets e tabelas dos textos renderizados de verdade (hoje tabela vira barras `|`).
4. Card de traço: descrição à esquerda, efeitos à direita, cada efeito com seu nome.
5. Topo do card de traço: caixa "Requisitos" recolhida, que expande ao clicar.
6. Regras opcionais em duas colunas balanceadas.
7. Numeração dos traços: da raridade mais baixa para a mais alta, ordem alfabética dentro da raridade.
8. Montagem de base/técnica oculta até clicar num botão.
9. Espaço depois do ":" nas caixas ("Função: Postura").
10. Cores das caixas: Função rosa, Complexidade roxo, Pontos de Treino cinza, Montagem vermelho, Gasto marrom, Alcance verde.
11. Contas de teste para as próximas fases.

### 13.1 Peças compartilhadas (já feitas pelo orquestrador)
- Tokens em `index.css` (por tema): `--rarity-common|rare|epic|legendary|divine`, `--stat-function|complexity|training|assembly|cost|range`.
- `frontend/src/components/ui/rarity.js`: `RARITY` (`{label, color, order}`), `rarityTagStyle(rarity)` → `{borderColor, color}`, `compareTraits(a, b)`.
- `frontend/src/components/ui/RichText.jsx`: `export default function RichText({ text, className })` — parágrafos, listas `- ` (2 espaços = 1 nível) e tabelas `|`.
  **Todo texto longo do catálogo (descrição, efeitos, regras, seções, passivas) deve ser renderizado com `RichText`.**

### 13.2 Backend
- `entity/TraitEffect`: ganha `String name` (o texto continua em `text`); manter um construtor de 1 argumento `TraitEffect(String text)`.
- Nova `entity/TraitVariation` (@Data, no-args/all-args): `String name; String description; List<TraitEffect> effects; String requirements`.
- `TraitDefinition` ganha no final `List<TraitVariation> variations` (vazia por padrão); `TraitPayload` e `TraitDefinitionResponse`
  ganham **no final** `List<TraitVariation> variations`. `CatalogService` (create/update/import/toResponse) e `CatalogSeeder` mapeiam.
- Listagem de traços (`listTraits`) passa a vir ordenada: raridade (COMMON→DIVINE) e depois nome.

### 13.3 Conversor (`scripts/notion_seed.py`)
- Efeitos: `{name, text}` — `name` = título do bloco "➥ Título" (sem os ":"), `text` = corpo.
- Variações: cada subpágina vira um `TraitVariation` (nome, descrição, efeitos, requisitos), não mais a linha "Variações: …".
- `rollNumber`: ordem **alfabética** (pt-BR, sem acento para ordenar) dentro de cada raridade, 1..20 (DIVINE 1..10); excedentes → null.
- Preservar estrutura: itens de lista como `- texto` (aninhamento = 2 espaços por nível) e tabelas como linhas `| a | b |` com a
  linha separadora `|---|---|`, em **todos** os campos de texto (traços e artes).

### 13.4 Contas de teste (`scripts/dev_fixtures.py`)
Python 3 stdlib, contra `http://localhost:8080` (arg `--api`), **reexecutável** (login se a conta já existir; pula o que já existe).
Senha de todas: `tatakai123`.
| Conta | Papel |
|---|---|
| `mestre@tatakai.dev` (Mestre Kenji) | Mestre da "Mesa de Testes"; editor do catálogo global (`CATALOG_EDITORS`) |
| `jogador1@tatakai.dev` (Akira) | ficha **COMPLETED** via Construção Inicial (método Clássico; slots pendentes resolvidos pelo Mestre) |
| `jogador2@tatakai.dev` (Bianca) | ficha **IN_BUILD**, parada no passo ATTRIBUTES (dados já rolados) |
| `jogador3@tatakai.dev` (Caio) | sem ficha (para testar o wizard do zero) |
Campanha "Mesa de Testes": os 3 jogadores; clã de campanha "Clã Kuroi" (rollNumber 1, +3 CON) para o Teste de Sangue nunca travar;
NPC "Sensei Kuro" (interações Treino custo 1 e Sparring custo 2) associado e visível, **com ficha de NPC** e seção ATTRIBUTES
liberada; TimeSkip ativo "Semana de Treino" (7 dias) com um agendamento do Akira no NPC e um log dele.
Imprime ao final uma tabela de contas/senhas e os ids úteis.

---

## 14. Próximos passos (backlog do usuário, 23/10/2026)

Ordem sugerida: primeiro os itens visuais rápidos (14.3), depois a unificação NPC/ficha (14.1–14.2), depois F3/F4.

### 14.1 NPC ligado ao catálogo — ✅ feito (24/10/2026)
> Criação "Completa" usa `TraitPicker` (catálogo + "Novo traço de campanha"). Ficha de NPC aceita traço de campanha
> em que o NPC esteja associado. Sem migração automática: traços livres antigos continuam só leitura.

- **Traços dos NPCs vindos do catálogo:** hoje o NPC (Mongo `npcs`) tem `traits` como texto livre `{name, description}`.
  Trocar por referência ao catálogo (`ADD_TRAIT`/`REMOVE_TRAIT` na ficha de NPC, com snapshot como na ficha de jogador) e
  migrar os traços livres existentes (casar por nome; o que não casar vira traço de campanha ou fica como nota).

### 14.2 Ficha de NPC = ficha de jogador — ✅ feito (24/10/2026)
> `CreateNpcForm` tem modos Simples/Completa e "NPC aleatório" (`npc/randomNpc.js`: nome, corpo, 7d12, traços pela tabela).
> O modal do NPC já mostra o card + `CharacterSheetView`.

- **Criar ficha como Mestre cria o NPC automaticamente:** novo fluxo "Nova ficha de NPC" na aba Fichas/NPCs → cria o `Npc`
  (nome, descrição, interações) + a `CharacterSheet` (npcId) numa ação só.
- **Mesma visualização da ficha de jogador** para NPC, somando as informações que hoje ficam nos cards: retrato, descrição,
  interações (com custo em pontos de ócio), conhecimentos, specs, visibilidade na campanha. Avaliar se os cards da aba NPCs
  passam a abrir essa ficha completa.

### 14.3 Ajustes visuais da ficha e do site — ✅ feito (24/10/2026)
| # | Pedido |
|---|---|
| V-9 | Nome do jogador na ficha com tratamento visual (hoje texto cru "Jogador: …"). |
| V-10 | Atributo em faixa de inaptidão (Inepto, Deficiente, Colapso) com a barra do limitador **vermelha**. |
| V-11 | Recursos (PV/SP/PQ) editáveis clicando no número da barra (edição inline, Enter salva, Esc cancela); remover os inputs soltos. |
| V-12 | Nome dos traços na ficha sem tratamento → mesmo estilo do catálogo (título + tag de raridade colorida). |
| V-13 | Traços na ficha **recolhidos** (nome + tags); clique expande descrição, efeitos e variações. |
| V-14 | Dinheiro vira uma caixinha **verde** ao lado de Inventário, rotulada **"Diárias"**. |
| V-15 | Todos os `<select>` do site com estilo próprio (borda grossa, seta customizada, foco visível, tema claro/escuro) — classe `.field` + regra global em `index.css`. |

### 14.4 Depois disso
- F3 — editor de Criação Marcial e técnicas na ficha (§2).
- F4 — progressão no TimeSkip (§2).

---

## 15. F3a — Montagem estruturada e editor de Criação Marcial (contrato, 24/10/2026)

Decisões do usuário: jogador cria **Arte Criada** (rascunho → enviar → Mestre aprova/devolve); Mestre/editores editam direto.
Montagens em texto das clássicas são **convertidas no seed** (`notion_seed.py`). F3b = técnicas na ficha + maestria.
F3c (contadores de arte, §8.1) vai para a F6 (depende dos eventos do motor).

### 15.1 Modelo (Mongo, embutido em `MartialArt`)
```
enum TechniqueCore { STRIKE, CONTROL, REACTION, PREPARATION }        // Golpe, Controle, Reação, Preparação
enum AssemblyItemKind { MODULE, EFFECT, UNIQUE, LIMITATION }          // LIMITATION = limitação (técnica) ou redução (base)
enum ArtStatus { DRAFT, PENDING_APPROVAL, APPROVED }                  // null = APPROVED (artes antigas)
AssemblyItem      { String module /* nome no catálogo de módulos; null = único/livre */; String label; int qty = 1;
                    int cost /* custo final do item, negativo em LIMITATION */; AssemblyItemKind kind;
                    boolean nonAffine; boolean onDie; String note }
AssemblyVariation { String name; TechniqueCore core; List<AssemblyItem> items }
ArtTechnique  += List<AssemblyVariation> variations; List<AssemblyItem> extras   // extras somam direto no final (ex.: Chute Fantasma)
ArtBase       += List<AssemblyItem> items
MartialArt    += ArtStatus status; UUID authorId; String reviewNote
```
O texto `assembly` continua (exibição/legado); `complexity`, `trainingPoints`, `spCostBase` guardados são os do livro.

### 15.2 Regras puras — `service/rules/AssemblyRules`
- `C_bruta = Σ custo > 0`; `C_final = C_bruta − min(Σ|limitações|, floor(C_bruta/2))` (RN-TEC-01 / RN-BASE-01).
- Técnica: 1 variação → `C_final`; 2 → `max(C_final_i) + 1`; `+ Σ extras`. Mais de 2 variações → issue.
- Gasto por variação (RN-TEC-02, §20): CLASSIC/CREATED/VARIATION `4 + C_bruta`; LOST/CLAN `max(0, 2 + C_bruta)`. Base: 0.
- PT = `max(2, C_final)` (RN-TEC-04).
- Teto (RN-TEC-03, RN-ART-05): técnica GENERIC 3; CREATED 6/10/14; CLASSIC 8/12/16; LOST/CLAN 10/14/18; base GENERIC 2;
  CREATED 4/6/8; CLASSIC 5/7/9; LOST/CLAN 6/8/10. ponytail: VARIATION usa o teto de CREATED (piso da regra) até existir `parentArtId`.
- Issues (texto pt-BR): acima do teto; > 2 EFFECT numa variação (salvo item "Pés no Ar"); dependências RN-TEC-05 por nome:
  Ímpeto→Investida, Arremesso→Derrubar, Cravar→Arremesso, Empurrão Contínuo→Sequência+Empurrar, Acompanhar→Empurrar,
  Sufocar→núcleo CONTROL, Círculo proíbe Varredura, Paralisar→graduação MASTER, Mutilar→≥ INTERMEDIATE.
- `AssemblyCheck(int rawComplexity, int finalComplexity, List<Integer> spCosts, int trainingPoints, int ceiling, List<String> issues)`.

### 15.3 Catálogo de módulos
`backend/src/main/resources/catalog/modules.json` (estático, §7.4 + módulos/reduções de base):
`{ name, group, kind, cost, perUnit, maxQty, appliesTo: "TECHNIQUE"|"BASE" }`. `GET /catalog/modules`.
O editor calcula o custo do item (`cost × qty`, +1 se não afim, efeito no dado = ceil(custo/2) mín. 1); o usuário pode ajustar.

### 15.4 Rotas
| Método | Rota | Quem | Efeito |
|---|---|---|---|
| GET | `/catalog/modules` | auth | lista de módulos |
| POST | `/catalog/assembly/check` | auth | `{category, technique?, base?}` → `AssemblyCheck` |
| POST | `/campaigns/{cid}/catalog/arts` | membro | Mestre: qualquer categoria, APPROVED. Jogador: CREATED, DRAFT, `authorId` |
| POST | `/catalog/arts` | editor global | APPROVED |
| PUT | `/catalog/arts/{id}` | autor (DRAFT) ou escrita do escopo | substitui o conteúdo |
| POST | `/catalog/arts/{id}/submit` | autor, DRAFT, sem issues | → PENDING_APPROVAL (issues → 400) |
| POST | `/catalog/arts/{id}/review` | Mestre do escopo, PENDING | `{approved, reason}` → APPROVED ou DRAFT + `reviewNote` |
Visibilidade: APPROVED (ou null) para membros; rascunho só para o autor; pendente para autor e Mestre; senão 404.
`DELETE` também vale para o autor no próprio rascunho. `MartialArtResponse` ganha no final
`ArtStatus status, UUID authorId, String reviewNote, List<AssemblyCheck> techniqueChecks, List<AssemblyCheck> baseChecks`;
`MartialArtSummaryResponse` ganha no final `ArtStatus status, UUID authorId`. Nova exceção `InvalidMartialArtException` (400).

### 15.5 Frontend
`components/catalog/ArtEditor.jsx` (cabeçalho da arte + listas de técnicas/bases; editor de variações/itens com módulo do
catálogo ou item único; `AssemblyCheck` ao vivo via `/catalog/assembly/check` com debounce). `ArtsPanel`: "Nova arte",
editar, enviar, aprovar/devolver, tags de status, e no detalhe o check calculado vs. números do livro.

---

## 16. F3b — Artes e técnicas na ficha, maestria (contrato, 24/10/2026)

Decisão do usuário: **jogador escolhe, Mestre aprova** (antes do treino no TimeSkip, que é F4). Ficha de NPC: o dono age como Mestre.

### 16.1 Banco (PostgreSQL)
```
enum CharacterArtStatus { REQUESTED, ACTIVE }
enum MoveKind { TECHNIQUE, BASE }
enum TechniqueState { REQUESTED, IN_TRAINING, ACTIVE, STORED }
enum Mastery { SKETCH, UNCORRECTED, STANDARD, IMPROVED, SPECIALIZED, MASTERED }          // RN-MAE-04
enum MasteryBonus { DAMAGE, HIT, COST, IMPACT, POWER, JOKER, REFINE, TRACTION, BONUS, REDUCTION, APEX }
character_arts       (id, sheet_id, art_id, art_name, category, graduation, status, created_at)  unique(sheet_id, art_id)
character_techniques (id, sheet_id, character_art_id, kind, name, graduation, complexity, training_points,
                      training_progress, state, mastery, mastery_points, bonuses jsonb, created_at)
```
`name`, `graduation`, `complexity`, `training_points` são snapshot da arte (número do livro; sem ele, o calculado pela montagem).

### 16.2 Regras puras — `service/rules/MasteryRules`
- Custo do próximo nível (RN-MAE-01): Melhorada `PT`; Especializada `PT + floor(PT/2)`; Masterizada `2·PT`. Só sobe por pontos a
  partir de Padrão; Esboço/Não Corrigida mudam por ação do Mestre (correção).
- Ganho de treino (RN-MAE-02 / §7.7): `1 + floor(modQ(TAL)/2)` (usado pela F4; aqui o Mestre lança os pontos).
- Bonificações (RN-MAE-03): técnica {DAMAGE, HIT, COST, IMPACT, POWER, JOKER, REFINE, TRACTION}; base {BONUS, JOKER, REFINE,
  TRACTION, REDUCTION}; JOKER no máx. 2; APEX automático ao chegar em Masterizada.
- Espaços: no máx. **3 ACTIVE** e **2 STORED** técnicas por (arte, graduação). Bases sem limite.

### 16.3 Rotas (`/character-sheets/{sid}/...`, só dono e Mestre; outros 403)
| Método | Rota | Quem | Efeito |
|---|---|---|---|
| GET | `/martial` | dono/M | `{arts[], techniques[]}` |
| POST | `/arts` `{artId}` | dono/M | dono → REQUESTED; Mestre → ACTIVE (graduação Iniciante) |
| POST | `/arts/{caId}/review` `{approved}` | M | ACTIVE ou remove |
| PATCH | `/arts/{caId}/graduation` `{graduation}` | M | muda a graduação na arte |
| DELETE | `/arts/{caId}` | M | remove a arte e as técnicas dela |
| POST | `/techniques` `{characterArtId, kind, name}` | dono/M | arte ACTIVE, movimento existe, graduação ≤ a da arte; dono → REQUESTED, Mestre → IN_TRAINING |
| POST | `/techniques/{tid}/review` `{approved}` | M | IN_TRAINING ou remove |
| POST | `/techniques/{tid}/training` `{points>0}` | M | progresso; ao atingir PT → ACTIVE (Padrão) se houver espaço, senão STORED |
| PATCH | `/techniques/{tid}/state` `{state: ACTIVE\|STORED}` | dono/M | troca respeitando espaços |
| POST | `/techniques/{tid}/mastery-points` `{points}` | M | soma pontos de maestria (pode ser negativo, mín. 0) |
| POST | `/techniques/{tid}/level-up` `{bonus}` | dono/M | consome o custo e sobe um nível; bônus válido para o tipo |
| PATCH | `/techniques/{tid}/mastery` `{mastery}` | M | define o degrau (correção de Esboço/Não Corrigida) |
| DELETE | `/techniques/{tid}` | M | remove |
Toda mutação grava `sheet_activity_logs` (PROGRESSION) e publica `SHEET_UPDATED`. Erros de regra → `InvalidCharacterSheetException` (400).

### 16.4 Frontend
`components/sheet/MartialPanel.jsx` como bloco "Artes marciais" em `CharacterSheetView` (dono/Mestre).

---

## 17. F4a — Treino e Repouso no TimeSkip (contrato, 24/10/2026)

Decisões do usuário: o treino vira ganho pendente **ao avançar o dia**; o **Repouso é agendado** na Agenda (ação solo de
1 ponto, com qualidade); o **local de treino é por TimeSkip**. F4b = EL/PS/Keiken/graduação; F4c = Forja/Caderno/Rivais.

### 17.1 Modelo
- `TimeSkip` + `TrainingVenue venue` (`FIELD | COMMON_GYM | FIGHT_GYM | HIGH_PERFORMANCE`, null = COMMON_GYM) e
  `short processedDay` (último dia já processado; evita reprocessar se o Mestre voltar e avançar de novo).
- `SoloActivityType` + `REPOUSO` (custo 1). `Booking` + `trainingTarget` (`STR|DEX|CON|INT|CHA|MEN|HP|SP|KP|TECHNIQUE`),
  `techniqueId` (CharacterTechnique), `restQuality` (`PRECARIOUS|SIMPLE|COMFORTABLE|TREATED`).
  TREINO exige `trainingTarget` (e `techniqueId` se TECHNIQUE; a técnica tem de ser da ficha do jogador); descrição opcional.
  REPOUSO exige `restQuality`.
- `training_entries`: `id, sheet_id, time_skip_id, booking_id, day_number, target, technique_id, gain, full (bool),
  status (PENDING | CONSOLIDATED | APPLIED | LOST), note, created_at`. Técnica é `APPLIED` na hora (progresso/maestria).
- `CharacterSheet` + `limiterCarry` (jsonb `Map<String,Integer>`): meio-ponto guardado além do Limitador por alvo.

### 17.2 Regras puras — `service/rules/TrainingRules`
- Ganho (RN-PROG-01): base FOR/DES/CON/INT/MEN 1, CAR 2, HP/SP 2; `+ floor(modQ(TAL)/2)`; `+1` do tipo corporal
  (Endo CON, Meso FOR, Ecto DES). PQ: 1 a cada 4º treino de PQ, sem TAL. TAL não treina.
- Sobrecarga (RN-PROG-02): 1º treino do dia no alvo normal; 2º normal +1 Desgaste extra; 3º+ ganho 0.
- Plenos por semana (semana = 7 dias do TimeSkip) e alvo físico (FOR, DES, CON, HP, SP, PQ): Campo 2, Academias 3,
  Centro 4. Excedente = Parcial `floor(ganho/2)`; Parcial de PQ = 0. ponytail: Platô, Lotada e teste de CON ND 12 ficam para depois.
- Desgaste (RN-PROG-05): +1 do tipo (mental: INT, CAR, MEN; resto físico). Esgotado: físico −1 Pleno na semana,
  mental −1 de ganho. Exaurido: todo treino Parcial. Colapso: treino do tipo bloqueado (ganho 0, `LOST`).
- Repouso (RN-PROG-06): dado d6/d8/d10/d12; HP += 2 dados + modQ(CON); SP += 4 dados + modQ(CON); PQ = máximo;
  Desgaste físico e mental −0/1/1/2. Rolagens via `RollService.rollInternal`. Dia sem Repouso: +1 Desgaste Físico.
- Consolidação (RN-PROG-04) no Repouso: Precário −1 por entrada (mín. 1); Barreiras (RN-ATR-06) truncam em 3/6/9 (excedente
  perdido); além do Limitador cada entrada vale meio ponto (`limiterCarry`). Máximo de recurso sobe; o atual acompanha se estava cheio.
- Técnica (§7.7): `1 + floor(modQ(TAL)/2)` no progresso (IN_TRAINING, ativa ao completar) ou nos pontos de maestria (aprendida).

### 17.3 Processamento — `TrainingService.processDays(timeSkip, toDayExclusive)`
Chamado por `TimeSkipService.setCurrentDay` quando o dia avança além de `processedDay`. Para cada dia e ficha de jogador
COMPLETED da campanha, em ordem de slot: treinos → entradas; Repouso → consolida + recupera; sem Repouso → +1 Desgaste Físico.
Tudo vai para o log de atividade (PROGRESSION/RESOURCE) e publica `SHEET_UPDATED`.

### 17.4 Rotas
`PATCH /timeskips/{id}/venue {venue}` (M). `GET /character-sheets/{sid}/training` (dono/M) → entradas.
`CreateBookingRequest` e `BookingResponse` ganham no final `trainingTarget, techniqueId, restQuality`.

### 17.5 Frontend
`SlotGrid`: TREINO com alvo (atributos, recursos, técnicas da ficha) e REPOUSO com qualidade. `TimeSkipPanel`: local de treino
(Mestre). Ficha: bloco "Treinos" com pendentes e histórico.

---

## 18. F4b — Lutas, Keiken e graduação (contrato, 24/10/2026)

Decisões do usuário: o **Mestre registra a luta** num menu pós-luta com os jogadores; para cada um, "Dar Pontos de
Superação" abre a lista de **Marcos** (catálogo Mongo, pressão/desvantagem, máx. 4 contam). PS base por classificação
**Fácil 1 / Real 2 / Letal 3** (Fácil = luta entre amigos/que ambos querem evitar; **não é sparring**). O **jogador
distribui** os PS e o **Mestre aprova** (aplica na aprovação). **Instintos** e **Marcos** vêm de catálogos no Mongo.
Pontos de graduação: **1 por treino** da graduação (TimeSkip) + **1 por EL** de luta com a arte (distribuído entre as artes,
nunca duplicado). Sparring dá 1 EL até um teto **acumulado** (não reseta) por patente: 3/5/10/14/18/23/30.

### 18.1 Modelo
- Mongo `instincts` (`Instinct`: id, campaignId, name, rank `KeikenRank`, description, version, updatedAt) e
  `fightMilestones` (`FightMilestone`: id, campaignId, name, category `PRESSURE|DISADVANTAGE`, description,
  `autoTrigger` `CRITICAL_STATE|null`, version, updatedAt). Global + extensão da campanha (mesmo nome substitui).
  Entram no `CatalogImportRequest` (`instincts`, `milestones`). Leitura: `GET /catalog/instincts|milestones?campaignId`.
  Marco também tem `grantsArtPoint` (Assinatura: +1 ponto de graduação à arte escolhida para o EL), `seriousOnly`
  (Elo Fraco: só Real/Letal) e `minOtherDisadvantage` (Zebra: 2). Seed global: `catalog/instincts.json` (24, 4 por
  patente de Calejado a Deus-Dragão) e `catalog/milestones.json` (5 de Pressão, 5 de Desvantagem).
- `KeikenRank` = `NOVICE 0, SEASONED 5, VETERAN 15, HISTORIC 30, LEGEND 55, MYTHIC 80, DRAGON_GOD 120` (EL total).
- `CharacterSheet` + `sparringExperience` (EL acumulado vindo de sparring) e `barrierOvercomePoints` (dos PS pendentes,
  quantos vieram de luta Real/Letal e podem cruzar Barreira).
- `fight_records`: id, fight_id (agrupa os participantes de uma luta), campaign_id, sheet_id, classification
  (`EASY|REAL|LETHAL|SPARRING`), el_gained, ps_gained, milestones (jsonb `List<String>`), art_el (jsonb
  `Map<UUID,Integer>` characterArtId→EL), time_skip_id, day_number (dia atual do TimeSkip ativo, se houver), fight_date,
  note, created_at.
- `overcome_distributions`: id, sheet_id, allocations (jsonb `Map<String,Integer>` alvo→PS), status
  (`PENDING|APPROVED|REJECTED`), review_note, created_at, reviewed_at. Só 1 PENDING por ficha.
- `character_instincts`: id, sheet_id, instinct_id, name, rank, description (snapshot), created_at.
- `CharacterArt` + `graduationPoints`. `Booking` + `characterArtId` (TREINO com alvo `GRADUATION`).

### 18.2 Regras puras — `service/rules/ProgressionRules`
- EL por arte: o pedido `artEl` é cortado no EL que a luta rendeu (Fácil repetida/sparring no teto → 0).
- EL (RN-PROG-07): Fácil 1 (0 se a ficha já teve luta Fácil no mesmo dia — dia do TimeSkip ativo, senão data), Real 2,
  Letal 3; Sparring 1 se `sparringExperience < teto(patente atual)`, senão 0.
- PS: `base(1/2/3; sparring 0) + min(marcos, 4) + floor(modQ(TAL)/2)` (sparring: 0). Real/Letal somam em
  `barrierOvercomePoints`.
- Distribuição: alvos `STR, DEX, CON, INT, CHA, MEN` (1 PS = +1) e `HP, SP` (1 PS = +3 no máximo); TAL e KP não recebem;
  INT e CAR máx. 2 PS por distribuição; soma ≤ PS pendentes. Cruzar Barreira (3→4, 6→7, 9→10) consome PS de Real/Letal
  (sem eles → 400). Além do Limitador: só numa distribuição de alvo único com **todos** os PS pendentes, e vale +1.
- Keiken (RN-PROG-08): patente pelo EL; vagas de Instinto = ordinal da patente (Novato 0, Calejado 1 …); Instinto
  escolhido tem patente ≤ a atual.
- Graduação (RN-PROG-09): custo Iniciante→Intermediário 8, Intermediário→Mestre 12 (arte "Geral" 9/18). Requisitos:
  (1) 3 técnicas aprendidas da graduação atual na arte; (2) entre elas ≥ 3 Melhoradas+ e ≥ 1 Especializada+;
  (3) Keiken: Intermediário ≥ Calejado; Mestre: n-ésima arte Mestre exige Veterano/Histórico/Lenda/Mitológico/Deus-Dragão;
  (4) pontos ≥ custo; (5) Mestre: duelo vencido (o Mestre confirma). Graduar zera os pontos e concede as bases da nova
  graduação (ACTIVE, Padrão). ponytail: bônus de instrutor (Educador +1 / Sábio +2) é lançado à mão pelo Mestre.

### 18.3 Rotas
| Método | Rota | Quem | Efeito |
|---|---|---|---|
| POST | `/campaigns/{cid}/fights` `{note, participants[{sheetId, classification, milestones[], artEl{caid:n}}]}` | M | grava os registros, soma EL/PS pendentes e pontos de graduação |
| GET | `/character-sheets/{sid}/progression` | dono/M | `{fights, distributions, instincts}` (todas as mutações abaixo devolvem isso) |
| POST | `/character-sheets/{sid}/overcome` `{allocations}` | dono/M | pede distribuição (fica PENDING) |
| POST | `/character-sheets/{sid}/overcome/{id}/review` `{approved, note}` | M | aplica ou recusa |
| POST/DELETE | `/character-sheets/{sid}/instincts` `{instinctId}` / `.../instincts/{id}` | dono/M · M | escolhe / remove |
| POST | `/character-sheets/{sid}/arts/{caid}/graduate` `{duelWon}` | M | gradua se os requisitos fecham |
| POST | `/character-sheets/{sid}/arts/{caid}/graduation-points` `{points}` | M | ajuste (instrutor) |
`SheetProgress` ganha `keiken, nextKeikenAt, sparringExperience, sparringCap, barrierOvercomePoints, instinctSlots`.
`CharacterArtResponse` ganha `graduationPoints, graduationCost, requirements[{key, met, detail}]`.
Toda mutação: `sheet_activity_logs` (PROGRESSION) + `SHEET_UPDATED`.

### 18.4 Frontend
`SheetsSection` (Mestre): "Fechar luta" → `FightClosePanel` (jogadores, classificação, Marcos com sugestão
`CRITICAL_STATE` = HP ≤ 25%, EL por arte, prévia). Ficha: bloco "Progresso" (EL verde, PS amarelo, Keiken, Instintos,
distribuição de PS, lutas). `MartialPanel`: pontos/requisitos de graduação e "Graduar". `SlotGrid`: TREINO de graduação.

Pendências abertas: divergências de Complexidade de 7 técnicas (§15) e
simplificações da F4a (Platô, Lotada, teste de CON ND 12, ferimento prejudicial, 1 Barreira por Repouso).

---

## 19. F4c — Caderno Marcial, Forja e Rivais (contrato, 24/10/2026)

Decisões do usuário: o que sai da Forja é **avulso** (fora das artes); a sessão de Forja é a **ação solo FORJA** na
Agenda; o Caderno **reusa o editor de Criação Marcial**; Rivais são registrados no **Fechar luta**.

### 19.1 Modelo
- `CharacterTechnique.characterArt` passa a ser **opcional** (null = avulsa). Avulsas não ocupam espaços de arte e não
  contam para Completude/Especialização. Bancos existentes: `ALTER TABLE character_techniques ALTER COLUMN
  character_art_id DROP NOT NULL` (§11.11).
- `notebook_drafts` (máx. 3 por ficha): id, sheet_id, kind (`TECHNIQUE|BASE`), `technique` (jsonb `ArtTechnique`) ou
  `base` (jsonb `ArtBase`), status (`DRAFT|PENDING_APPROVAL|APPROVED`), review_note, created_at. Montagem checada por
  `AssemblyRules` com categoria **CREATED** (tetos de Arte Criada). Só rascunho aprovado vai à Forja; consumido ao
  concluir. ponytail: a criação em combate (Improviso) fica para a F6.
- `forges` (máx. 1 em andamento por ficha): id, sheet_id, draft_id, name, required_points (= 3 × C_final),
  accumulated_points, started_at, completed_at.
- `SoloActivityType.FORJA` (custo 1). Bancos existentes: dropar `bookings_solo_activity_type_check`.
- `rivals`: id, sheet_id, name, npc_id (opcional), art, fights, read_bonus (0–3), known_techniques (jsonb `List<String>`),
  created_at; único (sheet_id, name).

### 19.2 Regras
- Iniciar Forja: rascunho APPROVED e sem Forja em andamento; graduação do rascunho ≤ maior graduação das artes ativas
  (Genérica sempre); Keiken mínima: Genérica nenhuma, Iniciante Calejado, Intermediário Veterano, Mestre Histórico.
- Sessão (ao avançar o dia, como o Treino): `+1 + floor(modQ(TAL)/2)` pontos e **+2 Desgaste Mental** (1 do treino + 1
  extra). Sem Forja em andamento → entrada `LOST`. Ao completar: técnica/base avulsa ACTIVE em **Esboço**, rascunho apagado.
- Rival: no Fechar luta, cada participante pode indicar o oponente (NPC da campanha ou nome livre); cria o Rival se não
  existir; `fights + 1`, `readBonus = min(3, readBonus + 1)`. Dono/Mestre editam arte e técnicas conhecidas; Mestre remove.

### 19.3 Rotas
| Método | Rota | Quem | Efeito |
|---|---|---|---|
| GET | `/character-sheets/{sid}/notebook` | dono/M | `{drafts, forge}` (todas as mutações abaixo devolvem isso) |
| POST/PUT | `/character-sheets/{sid}/notebook[/{id}]` `{kind, technique, base}` | dono/M | cria/edita rascunho (só DRAFT) |
| DELETE | `/character-sheets/{sid}/notebook/{id}` | dono/M | apaga (não se estiver na Forja) |
| POST | `/character-sheets/{sid}/notebook/{id}/submit` | dono | DRAFT → PENDING_APPROVAL (bloqueado com issues) |
| POST | `/character-sheets/{sid}/notebook/{id}/review` `{approved, reason}` | M | APPROVED ou volta a DRAFT |
| POST | `/character-sheets/{sid}/forge` `{draftId}` | dono/M | inicia a Forja |
| DELETE | `/character-sheets/{sid}/forge` | M | abandona a Forja em andamento |
| PUT/DELETE | `/character-sheets/{sid}/rivals/{id}` `{art, knownTechniques}` | dono/M · M | edita / remove |
`FightRequest.Participant` ganha `rival {name, npcId}`; `SheetProgressionResponse` ganha `rivals`.

### 19.4 Frontend
Ficha: bloco "Caderno Marcial e Forja" (rascunhos com o editor de técnica/base da Criação Marcial, envio, aprovação,
Forja com medidor). `MartialPanel`: grupo "Avulsas". `SlotGrid`: ação solo Forja. `FightClosePanel`: oponente por
jogador. `ProgressPanel`: Rivais (lutas, Leitura 0–3, técnicas conhecidas).


---

## 20. F5 — Canais de rolagem e RollProfile (contrato, 24/10/2026)

Spec §16 + RN-ACAO-05, RN-ATQ-01/02/04, RN-DESG-01, RN-MAE-03/04.
ponytail: o `RollProfile` é **calculado sob demanda** (GET) a partir da ficha + catálogo, não persistido nem
recalculado por evento — o resultado é o mesmo para o usuário. Bases não têm perfil próprio: entram como
modificador opcional (escolhida no modo detalhado). Guarda, Adrenalina, interação marcial e altura ficam para a F6.

### 20.1 Regras (puras, em `service/rules/RollRules`)
- **Vantagem líquida** (−3…+3): `≥1` → 2d20 pega o maior e soma `min(n−1, 2)`; `≤−1` → 2d20 pega o menor e soma
  `−min(|n|−1, 2)`; 0 → 1d20. O d20 usado é o `naturalD20`.
- **Crítico:** `natural ≥ critMargin` (20 na F5; fontes de margem ampliada ficam para a F6). **Falha crítica:** natural 1.
- **Escala de dado da técnica** (`degrau` 0–8 = qtd. do módulo "Dado Maior" na variação):
  `1d4, 1d6, 1d8, 1d10, 1d12, 2d6, 2d8, 2d10, 2d12`.
- **Acerto (RN-ATQ-01):** `d20 + mod(DES)` + Precisão da variação (qtd. do módulo "Precisão") + 1 por `HIT` nas
  bonificações + Precisão da base escolhida (qtd. do módulo de base "Precisão"; base em Esboço = floor/2) +
  penalidade de Desgaste **Físico** (Cansado −1, Esgotado −2, Exaurido/Colapso −3). Técnica em Esboço → −1 de vantagem.
  Situacionais (modo detalhado): alvo derrubado = +1 vantagem; atacante derrubado = mod(DES) pela metade
  (`SheetRules.half`); ataque localizado = penalidade numérica informada + rótulo da região (sem tabela na F5).
- **Dano (RN-ATQ-02):** `(1+E)·dado` (só esse termo dobra no crítico: dobra a quantidade de dados) + mod(FOR) +
  2 por `DAMAGE` + "Força do Golpe" da base (qtd.) . `E = floor(FOR/30)` para ataque básico e técnica (todas tratadas
  como desarmadas na F5) + 1 se a técnica tem `APEX`. mod nulo (atributo 0) conta como 0.
- **Ataques básicos** (livro, "Ataques Básicos"): Soco 1d3 · Chute 1d4 · Cotovelada 1d3 · Joelhada 1d4+1 (−1 acerto)
  · Cabeçada 1d4 · Agarrão 1d2+1. Mesmas regras de acerto/dano (sem Precisão/maestria).
- **Teste de atributo:** `d20 + mod(attr)` + Desgaste da trilha (FOR/DES/CON → Físico; INT/MEN/CAR → Mental; TAL → nenhum).
- **Gasto de SP** exibido no perfil (`spCostBase` da técnica no catálogo − 2 por `COST`, mín. 0, + 2/4/6 do Desgaste
  Físico); **não é debitado** na F5 (débito é do motor de combate, F6).
- Montagem da técnica: buscar a `ArtTechnique` no catálogo (arte do `CharacterArt` ou `borrowedFrom`, por nome).
  Técnica sem montagem estruturada (avulsa/sem catálogo) → degrau 0 e Precisão 0.

### 20.2 Modelo (`rolls`, colunas novas; bancos existentes: `ddl update` adiciona, nulos = rolagem antiga PUBLIC/FREE)
`channel` (`PUBLIC|GM_PRIVATE`), `mode` (`QUICK|DETAILED|FREE`), `source_type`
(`TECHNIQUE|BASIC_ATTACK|ATTRIBUTE_TEST|CUSTOM`), `source_id` (uuid; técnica), `source_name` (100),
`advantage` int, `critical`/`fumble` bool, `breakdown` jsonb `List<RollPart>`, `damage_total` Integer,
`edit_reason` (200), `revealed` bool. `RollPart = {part: HIT|DAMAGE, source, value, dice: List<Integer>|null,
edited: bool}` (parcela de dado: `source` = "2d4 (Oi-Zuki)", `value` = soma, `dice` = faces).

### 20.3 Rotas
| Método | Rota | Quem | Efeito |
|---|---|---|---|
| GET | `/character-sheets/{sid}/roll-profiles` | dono/M (NPC: dono) | `RollProfileResponse[]` (técnicas ACTIVE por variação + 6 ataques básicos) |
| POST | `/character-sheets/{sid}/rolls/test` | dono/M | `RollTestRequest` → `RollMessage` |
| POST | `/character-sheets/{sid}/rolls` | (existente) | ganha `channel?` e `campaignId?` (para NPC) |
| GET | `/campaigns/{cid}/rolls?channel=PUBLIC\|GM_PRIVATE` | membro · M | últimas 100, mais recente primeiro |
| POST | `/campaigns/{cid}/rolls` `{expression, purpose, channel}` | M | rolagem livre do Mestre sem ficha |
| POST | `/campaigns/{cid}/rolls/{rid}/reveal` | M | GM_PRIVATE → `revealed=true` e publica no público |

`RollTestRequest = {sourceType*, sourceId?, variation?(nome), basicAttack?(SOCO|CHUTE|COTOVELADA|JOELHADA|CABECADA|AGARRAO),
attribute?(Attribute), mode* QUICK|DETAILED, channel? (default PUBLIC; GM_PRIVATE só M), campaignId? (obrigatório em NPC;
requester = M dessa campanha e dono do NPC), purpose ≤200 sem <>, advantage −3..3, baseId?, targetDowned, attackerDowned,
localizedRegion? ≤40, localizedPenalty? ≤0, overrides: [{source, value}] (só DETAILED; substitui o valor de uma parcela
não-dado com esse `source` ou adiciona parcela nova em HIT), editReason (obrigatório se houver override; 400)}`.
`RollProfileResponse = {sourceType, sourceId, name, variation, basicAttack, spCost, critMargin, advantage,
hit: RollPart[], damageDice: {count, sides, source}, damage: RollPart[], hitFormula, damageFormula}`
(fórmulas em texto, ex.: "1d20+3", "2d4+2").
`RollMessage = {id, campaignId, sheetId, actorId, actorName, characterName, channel, mode, purpose, sourceType, sourceId,
sourceName, formula, breakdown: RollPart[], diceResults, advantage, total, naturalD20, critical, fumble, damageTotal,
edited, editReason, revealed, createdAt}`.

Canais: PUBLIC → `CampaignEventPublisher.publish(cid, "rolls", msg)` (AFTER_COMMIT, como as fichas). GM_PRIVATE →
`convertAndSendToUser(masterUserId, "/queue/gm-rolls", msg)` para cada MASTER da campanha; broker passa a ter
`/queue`; `StompAuthChannelInterceptor` permite SUBSCRIBE em `/user/queue/gm-rolls`. Rolagem de ficha de NPC sem
`campaignId` continua só no log da ficha (comportamento atual). A rolagem livre atual (`POST /rolls`) também publica
no canal (default PUBLIC).

### 20.4 Frontend
- Ficha: bloco "Rolagens" substitui o `RollBox` solto: lista de perfis (técnicas por variação + ataques básicos) com
  **Rolar** (QUICK) e **Detalhar** (abre o construtor: parcelas com origem, vantagem −3..+3, base, alvo/atacante
  derrubado, localizado, edição de valores com motivo obrigatório); testes de atributo; rolagem livre por expressão
  (mantém o `RollBox`). Mestre escolhe o canal (Público/Privado).
- Aba **Rolagens** na campanha: feed público em tempo real (`useCampaignTopic(cid, 'rolls')`), cartão com total,
  dano, d20 natural, selo CRÍTICO/FALHA, "editado" (+ motivo) e "revelada"; parcelas expansíveis. Mestre: segunda
  coluna "Privado" (`/user/queue/gm-rolls`), filtro por texto (propósito/personagem), botão **Revelar** e rolagem
  livre sem ficha.

### 20.5 Backlog visual (pedido do usuário, 24/10/2026)
- ✅ Artes marciais recolhíveis no `MartialPanel` (estado por arte em localStorage).
- ✅ Animação de crítico / falha crítica no `RollCard` (respeita `prefers-reduced-motion`).
- ✅ Barras de Desgaste rotuladas como desgaste (cansaço).
- ✅ Animação de rolagem (`RollReveal`: d20 girando ~900 ms antes do resultado). Refinamento em §26 (item 5).

---

## 21. F7 — Exportar / importar ficha (contrato, 24/10/2026)

Spec §21 (RN-EXP-01..03). ponytail: o **PDF é a impressão do navegador** (`window.print()` + folha `@media print`
no estilo do app, "Salvar como PDF") em vez de OpenPDF no servidor — mesmo resultado para a mesa, zero dependência.
Só JSON passa pelo backend; `GET /character-sheets/{id}/export?format=pdf` não existe.

### 21.1 Formato `.tatakai.json` (`SheetExportFile`, record Jackson)
```
{ schemaVersion: 1, exportedAt, exportedBy: {userId, name}, source: {campaignId|null, sheetId, npcId|null},
  sheet: { name, bodyType, dominance, heightM, weightKg,
           attributes: {STR..TAL},                 // omitido se ATTRIBUTES oculta p/ quem exporta
           hpMax, spMax, kpMax, currentHp, currentSp, currentKp, openWounds,
           physicalFatigue, mentalFatigue,         // RESOURCES
           fightExperience, sparringExperience, pendingOvercomePoints, barrierOvercomePoints,
           limiterAdjustments, limiterCarry,
           money, inventory[],                     // INVENTORY
           injuries[] },                           // INJURIES
  traits[]:  {traitId, traitVersion, name, rarity, category, description, buildGains, acquiredIn, gainsApplied},
  clans[]:   {clanId, clanVersion, name, buildGains},
  arts[]:    {artId, artName, category, graduation, status, graduationPoints,
              techniques[]: {kind, name, graduation, complexity, trainingPoints, trainingProgress, state, mastery,
                             masteryPoints, bonuses[], borrowedFrom}},
  looseTechniques[]: (mesmo formato, técnicas avulsas),
  instincts[]: {instinctId, name, rank, description},
  rivals[]: {name, art, fights, readBonus, knownTechniques[]} }   // npcId não sai (é da campanha de origem)
```
Seção oculta (`hiddenByMaster`) para quem exporta → campos da seção ficam `null`/ausentes (RN-EXP-01).
Treino pendente, Forja, Caderno, rolagens e log **não** saem (são histórico da mesa de origem).

### 21.2 Regras
- **Exportar:** dono da ficha ou MASTER da campanha; ficha de NPC: dono do NPC. Outros → 404 (como o GET da ficha).
  Grava log (categoria `BUILD`, ação `EXPORT`).
- **Importar:** `POST /campaigns/{cid}/character-sheets/import` (multipart `file`, ≤ 1 MB, `.json`). Requester é
  PLAYER da campanha **sem ficha** (409 se já tem; 403 se MASTER/não membro). `schemaVersion != 1` → 400.
  Cria a ficha em `PENDING_APPROVAL` com os dados do arquivo; seções ausentes ficam no padrão de ficha nova.
  Derivados nunca vêm do arquivo (já são calculados). Revalidação → **avisos** (não bloqueiam; o Mestre decide):
  atributo fora de 0..999, recurso atual > máximo (corta no máximo), traço/clã/arte/instinto inexistente no catálogo
  ou com versão diferente (mantém o snapshot do arquivo), técnica sem arte correspondente vira avulsa.
  Grava log (`BUILD`, `IMPORT`, com nome do arquivo). O Mestre aprova/recusa pelo fluxo existente (`/approve`).
- Resposta do import: `SheetImportResponse {sheet: CharacterSheetResponse, warnings: string[]}` (201).

### 21.3 Rotas
| Método | Rota | Quem | Efeito |
|---|---|---|---|
| GET | `/character-sheets/{sid}/export` | dono/M (NPC: dono) | `SheetExportFile` com `Content-Disposition: attachment; filename="<nome>.tatakai.json"` |
| POST | `/campaigns/{cid}/character-sheets/import` | PLAYER sem ficha | `SheetImportResponse` 201 |

### 21.4 Frontend
- Ficha: botões **Exportar JSON** (baixa o arquivo via blob autenticado) e **Imprimir / PDF** (`window.print()`;
  antes de imprimir, todos os blocos recolhíveis abrem; `@media print` esconde sidebar, navegação, botões e toasts,
  fundo branco, tinta preta, quebras de página entre blocos).
- Aba Fichas: jogador sem ficha vê **Importar ficha (.tatakai.json)** ao lado de "Criar ficha"; após importar, mostra
  os avisos. Mestre vê os avisos? não (não são persistidos) — ponytail: ele revisa a ficha pendente como hoje.

### 20.6 Canais personalizados e feed compacto (pedido do usuário, 24/10/2026)
- **Canais = abas** na aba Rolagens: "Público", "Privado do Mestre" (só M) e um por canal personalizado.
- **Canal personalizado** (`roll_channels`): id, campaign_id, name (≤50, sem `<>`, único por campanha),
  member_ids (jsonb `List<UUID>` de usuários da campanha), created_at. Só o **Mestre** cria/edita/apaga; o Mestre
  sempre vê todos; jogador vê e rola só nos canais em que está.
  `RollChannel` ganha `CUSTOM`; `rolls.custom_channel_id` (uuid, nullable). `RollMessage` ganha `customChannelId`.
  Apagar o canal apaga as rolagens dele.
- Rotas: `GET /campaigns/{cid}/roll-channels` (membro: os seus; M: todos) · `POST` `{name, memberIds}` (M) ·
  `PUT /campaigns/{cid}/roll-channels/{id}` (M) · `DELETE` (M, 204).
  Rolar no canal: `channel: "CUSTOM", customChannelId` em `RollTestRequest`, `RollRequest` (livre da ficha) e
  `CampaignRollRequest` (livre do Mestre); requester precisa ser membro do canal ou M (403); canal de outra campanha → 404.
  Histórico: `GET /campaigns/{cid}/rolls?channel=CUSTOM&customChannelId=…` (mesma regra).
  Revelar (M) funciona também para CUSTOM → publica no público com `revealed=true`.
- WS: rolagem CUSTOM vai por `convertAndSendToUser(uid, "/queue/rolls", msg)` para cada membro do canal e cada
  MASTER (sem duplicar); SUBSCRIBE em `/user/queue/rolls` permitido. GM_PRIVATE continua em `/user/queue/gm-rolls`.
- **Feed compacto** (todas as abas): a última rolagem em destaque (cartão grande, com a animação de crítico/falha);
  o histórico abaixo fica **recolhido** por padrão (botão "Histórico (N)"), em linhas compactas expansíveis.
- Na ficha, o seletor de canal do `RollsPanel` lista Público / Privado (M) / canais do usuário.
- **Impressão:** blocos recolhíveis (`Block` da ficha, `ArtCard`) passam a **sempre montar o conteúdo** e só
  escondê-lo com CSS quando recolhidos; `@media print` força exibir. Remove `usePrinting`.

---

## 22. F6 — Motor de combate (fatiamento) e F6a (contrato, 24/10/2026)

| Fatia | Entrega | Spec |
|---|---|---|
| **F6a** | Encontro, participantes, iniciativa, turnos/rodadas, PA, ações com custo de PA/SP, condições com expiração, linha do tempo | §2.1–2.3 (RN-ACAO-01..05), §3.1–3.6, §4.1 |
| F6b | Transação de ataque: alvo, região, reação declarada (Defender/Esquiva/Contra), rolagens ligadas ao `RollProfile`, dano e mitigação, Timing, Luta contra Grupo | RN-ATQ-*, RN-REA-*, §19 |
| F6c | Autômatos de desmaio (PQ/SP/HP), danos periódicos, Adrenalina, Desgaste em combate, ferimentos, fim de combate → Fechar luta | §3.3 passos 2–7, §4.2–4.7, §5 |
| F6d | Interação marcial, especialização, guarda/dominância, contadores de arte | §6, §8 |

### 22.1 F6a — Modelo (Postgres)
- `encounters`: id, campaign_id, name (≤100), status `SETUP|ACTIVE|ENDED`, round (int, 0 em SETUP), turn_counter
  (índice global de turno, cresce a cada turno iniciado), current_index (posição na ordem), created_at, ended_at.
  No máximo **1 encontro não encerrado por campanha** (409).
- `encounter_participants`: id, encounter_id, sheet_id (ficha de jogador COMPLETED da campanha ou ficha de NPC
  associado à campanha), name (snapshot), npc (bool), initiative (Integer), order_index, pa_current, pa_bonus_next,
  pa_loss_next, moves_this_turn, turn_started_at (turn_counter do último turno iniciado; null antes do 1º).
  Único (encounter_id, sheet_id).
- `combat_conditions`: id, participant_id, condition_key, source_participant_id?, expires_at
  (`START_OF_TARGET_TURN|END_OF_TARGET_TURN|END_OF_ROUND|END_OF_COMBAT|MANUAL|COUNTER`), applied_turn (turn_counter no
  momento), turns_remaining?, intensity?, note (≤200). **Stack:** mesma `condition_key` no mesmo alvo **renova**
  (substitui), exceto `stack: SUM` no catálogo (soma `intensity`).
- `combat_events` (linha do tempo, imutável): id, encounter_id, round, turn_counter, participant_id?, type
  (`START|INITIATIVE|TURN_START|TURN_END|ROUND_END|ACTION|CONDITION_ADDED|CONDITION_EXPIRED|CONDITION_REMOVED|
  TURN_LOST|END|GM`), text (pt-BR, ≤500), created_at.
- Catálogo estático `catalog/conditions.json` (§4.1): `{key, name, effect, exit, defaultExpiresAt, stack}`;
  `GET /catalog/conditions`. Desequilibrado: `MANUAL`.

### 22.2 F6a — Regras (puras em `service/rules/CombatRules`)
- **Iniciativa (RN-ACAO-04):** `1d20 + mod(DES)` se a ficha tem alguma `CharacterArt` ACTIVE, senão
  `1d20 + half(mod(DES))`; + penalidade de Desgaste Físico (−1/−2/−3). Rolada no servidor via
  `RollService.rollInternal` (fica no log da ficha, purpose "Iniciativa — <encontro>"). Ordem: maior primeiro;
  empate → maior DES → sorteio. Mestre pode rerrolar ou definir a iniciativa de um participante (log GM).
- **PA (RN-ACAO-01):** no início do turno `pa = 5 + bonus_next − loss_next` (mín. 0); zera bonus/loss; moves = 0.
- **Ações (RN-ACAO-02)** — só no turno do participante; executa o dono da ficha ou o Mestre:
  | `action` | PA | SP | Observação |
  |---|---|---|---|
  | MAJOR | 3 | `spCost` (padrão 1) | `label` livre ("Técnica X"). `slow: true` → 4 PA |
  | PREPARED | 3 | `spCost` | |
  | BREATHE | 4 | — | recupera `2 + modQ(CON)` SP (até o máximo) |
  | MINOR | 1 | — | |
  | MINOR_OFFENSIVE | 2 | `spCost` (padrão 1) | |
  | DISENGAGE | 2 | — | |
  | MOVE | 1 | — | distância RN-ACAO-03: `d1 = max(1,5; 4 + modQ(DES))`, `dn = max(1,5; d(n−1)/2)`; log "Moveu X m" |
  | STAND_UP | 2 | — | remove a condição Derrubado |
  | SHOVE | 1 | — | Atravessar |
  | FREE | 0 | — | fala/gatilho |
  `pa_current ≥ custo` e `currentSp ≥ spCost` (senão 400 com motivo). SP debitado na ficha (`currentSp`), com log
  de atividade da ficha (categoria RESOURCE) e evento ACTION na linha do tempo.
- **Avanço (`next-turn`)** — Mestre, ou o dono da ficha do participante atual:
  1. Fim do turno do atual: expira `END_OF_TARGET_TURN` do atual com `applied_turn < turn_started_at`
     (RN-TURNO-01: aplicada durante o próprio turno do alvo → `applied_turn == turn_started_at` → só no fim do próximo);
     `COUNTER` do atual: `turns_remaining − 1`, expira em 0; descarta PA.
  2. Próximo da ordem; se deu a volta → fim da rodada: expira `END_OF_ROUND`, `round + 1`, evento ROUND_END.
  3. Início do turno do novo: `turn_counter + 1`, `turn_started_at = turn_counter`; expira `START_OF_TARGET_TURN`
     dele; recarga de PA; se tem **Atordoado** → evento TURN_LOST, PA = 0 (o turno continua para o Mestre avançar).
- **Encerrar:** status ENDED, apaga condições `END_OF_COMBAT` (todas, na prática), evento END. O "Fechar luta"
  (F4b) continua separado.
- Mestre pode ajustar PA de qualquer participante e reordenar (log GM).

### 22.3 F6a — Rotas (Mestre = M da campanha)
| Método | Rota | Quem | Efeito |
|---|---|---|---|
| GET | `/campaigns/{cid}/encounters` | membro | lista (mais recente primeiro) |
| POST | `/campaigns/{cid}/encounters` `{name}` | M | cria em SETUP |
| GET | `/encounters/{eid}` | membro | `EncounterResponse` |
| POST | `/encounters/{eid}/participants` `{sheetId}` | M | só em SETUP/ACTIVE (entra sem iniciativa → M define) |
| DELETE | `/encounters/{eid}/participants/{pid}` | M | |
| POST | `/encounters/{eid}/start` | M | rola iniciativa de quem não tem, ordena, round 1, inicia o 1º turno |
| PATCH | `/encounters/{eid}/participants/{pid}` `{initiative?, paCurrent?, paBonusNext?, paLossNext?, orderIndex?}` | M | ajuste (log GM) |
| POST | `/encounters/{eid}/participants/{pid}/reroll-initiative` | M | |
| POST | `/encounters/{eid}/actions` `{participantId, action, spCost?, slow?, label?}` | dono/M | ver tabela |
| POST | `/encounters/{eid}/next-turn` | M ou dono do atual | |
| POST | `/encounters/{eid}/participants/{pid}/conditions` `{key, expiresAt?, turnsRemaining?, intensity?, sourceParticipantId?, note?}` | M | |
| DELETE | `/encounters/{eid}/conditions/{condId}` | M | |
| POST | `/encounters/{eid}/end` | M | |
| GET | `/encounters/{eid}/events` | membro | linha do tempo (ordem cronológica) |
`EncounterResponse = {id, campaignId, name, status, round, turnCounter, currentParticipantId, participants:
[{id, sheetId, name, npc, initiative, orderIndex, paCurrent, paBonusNext, paLossNext, movesThisTurn, ownerUserId,
resources: {currentHp, hpMax, currentSp, spMax, currentKp, kpMax} | null, conditions: [{id, key, name, expiresAt,
turnsRemaining, intensity, sourceParticipantId, note}]}]}`. `resources` só para o Mestre e o dono da ficha
(NPC: só o Mestre). WS: após cada mutação publica `{encounterId, type}` em `/topic/campaign/{cid}/combat`
(AFTER_COMMIT); o cliente refaz o GET (ponytail: snapshot por refetch, sem diff).

### 22.4 F6a — Frontend
Aba **Combate** na campanha: Mestre cria encontro, adiciona participantes (fichas de jogador COMPLETED + fichas dos
NPCs da campanha), inicia. Rastreador em ordem de iniciativa com o atual destacado (painel quadrinhos), pips de PA,
barras HP/SP/PQ quando visíveis, chips de condição com rótulo de expiração ("até o fim do seu próximo turno",
"2 turnos"…). Barra de ações do participante atual (dono/Mestre): botões com custo de PA/SP, desabilitados com
motivo (RN-ACAO-02). Mestre: aplicar/remover condição (catálogo), ajustar PA/iniciativa, avançar turno, encerrar.
Linha do tempo à direita, ao vivo. Jogador sem participante vê o rastreador em modo leitura.

## 23. F6b — Ataque e reação (contrato, 24/10/2026)

Spec §19 + RN-ATQ-01..05/10, RN-REA-01..03/05, RN-ATQ-09 (Timing).
ponytail: **sem grade/alcance** (o app não modela posições) — o atacante escolhe 1..N alvos livremente, exceto ele
mesmo e quem está **Furtivo**. Sem fila `/user/queue/reactions`: o pedido de reação chega pelo tópico `combat`
(evento) + `GET` filtrado por quem vê (nada vaza: o ataque só rola depois das reações). Ficam para depois:
Contra-Ataque Tardio (F6d), técnicas de núcleo Reação (F6d), Redução de Dano/Calejamento (F6d), efeitos de módulo
(Derrubar, Atordoar…) e PQ (F6c), prazo de reação (sem resolução automática — o Mestre responde por quem faltar).

### 23.1 Modelo
- `pending_attacks`: id, encounter_id, attacker_id (participant), source_type (`TECHNIQUE|BASIC_ATTACK`), source_id,
  variation, basic_attack, source_name, action_kind (`MAJOR|MINOR_OFFENSIVE`), region (≤40), region_penalty (≤0),
  reserved_pa, reserved_sp, status (`AWAITING_REACTIONS|RESOLVED|CANCELLED`), round, attack_roll_id, created_at,
  resolved_at.
- `attack_targets`: id, attack_id, target_id (participant), reaction (`NONE|DEFEND|DODGE|COUNTER`, null = aguardando),
  auto_resolved, responded_at, reaction_roll_id, outcome (`HIT|MISS|DODGED|DEFENDED|COUNTERED`), mitigation_pct,
  damage (aplicado ao alvo), counter_damage (aplicado ao atacante).
- `encounter_participants.attackers_this_round` (jsonb `List<UUID>` de participantes que o atacaram desde o início do
  turno dele) — zera no **início do turno do defensor** (Luta contra Grupo).

### 23.2 Fluxo e regras (puras em `CombatRules` / `AttackRules`)
1. **Declarar** (`POST /encounters/{eid}/attacks`) — dono do atacante ou M, no turno do atacante, encontro ACTIVE.
   Custo: MAJOR 3 PA (+1 se a variação tem a limitação "Lenta"), MINOR_OFFENSIVE 2 PA (só técnica com módulo
   "Técnica Rápida" ou ataque básico SOCO; senão 400). SP = `spCost` do `RollProfile` (ataque básico = 1).
   Valida PA/SP (400) e **debita na hora** (reserva). Alvo **Vulnerável/Atordoado/Inconsciente** → reação NONE
   automática (`auto_resolved`). Se todos já resolvidos → resolve na hora.
2. **Responder** (`POST /attacks/{aid}/targets/{tid}/reaction {reaction}`) — dono da ficha do alvo (jogador) ou M
   (NPC sempre M; M pode responder por qualquer um). COUNTER exige `currentSp ≥ 1` do defensor (400). Quando o último
   alvo responde → **resolver**.
3. **Cancelar** (`POST /attacks/{aid}/cancel`) — atacante (dono/M) **antes de qualquer resposta não automática**:
   devolve PA e SP, status CANCELLED. Depois → 409.
4. **Resolver** (uma rolagem de ataque para todos os alvos; uma de reação por alvo):
   - **Ataque:** perfil (`RollProfileService`) com região/penalidade e vantagem líquida: −1 técnica em Esboço;
     −1 atacante Imobilizado; −1 atacante Desorientado; atacante Derrubado → mod pela metade (`attackerDowned`).
     Contra o alvo: +1 se o alvo está Agarrado, Imobilizado ou Paralisado (um único teste: usa a vantagem do 1º alvo
     — ponytail). Rolagem publicada no canal **público** (`RollMessage`, purpose "Ataque: A → B[, C]").
   - **Reação** (`d20` + vantagem líquida + penalidades): DEFEND `mod(CON)`; DODGE e COUNTER `mod(DES)`;
     − Desgaste Físico; Luta contra Grupo `−(n_atacantes_na_rodada − 1)` (contando este); Timing do defensor
     `+max(0, usos − 1)` (usos = ataques RESOLVED do mesmo atacante, mesma fonte, mesmo alvo, no encontro, incluindo
     este); desvantagem em DODGE/COUNTER se Derrubado, em DODGE se Desorientado. Publicada no público
     ("Defender: B vs A"). COUNTER debita 1 SP do defensor.
   - **Desfecho por alvo:** ataque com 1 natural → MISS (falha crítica), sem dano, defensor não rola.
     NONE → HIT. DODGE: reação **>** ataque → DODGED (0); senão HIT. DEFEND: reação ≥ ataque → DEFENDED com
     Δ 0–3 = 50%, 4–7 = 75%, ≥8 = 100%; falha → HIT com 25% (módulo "Romper Guarda" na variação remove os 25%).
     COUNTER: reação > ataque → COUNTERED: defensor causa o dano do seu ataque básico SOCO no atacante e **também**
     recebe o dano; reação ≥ 1,5 × ataque → não recebe. Falha → HIT.
     **Crítico vs crítico (RN-ATQ-05):** ataque crítico e reação crítica → a reação falha, exceto DEFEND (fica 50%).
   - **Dano:** total de dano do perfil (crítico dobra os dados, já em `RollRules`) × (1 − mitigação), arredondado para
     baixo; mínimo 1 se conectou e a mitigação < 100%. Aplicado em `currentHp` (mín. 0) da ficha do alvo (log de
     atividade RESOURCE). Consequências de HP 0 → F6c.
   - Registra o atacante em `attackers_this_round` de cada alvo; eventos `ACTION` na linha do tempo com o resumo
     ("Akira acertou Kuro com Jab: 7 de dano (Defender 50%)").

### 23.3 Rotas
| Método | Rota | Quem |
|---|---|---|
| POST | `/encounters/{eid}/attacks` `{attackerId, sourceType, sourceId?, variation?, basicAttack?, actionKind, targetIds[], region?, regionPenalty?}` | dono/M |
| GET | `/encounters/{eid}/attacks?status=AWAITING_REACTIONS` | membro (todos veem quem ataca quem; opções de reação só para quem pode responder) |
| POST | `/attacks/{aid}/targets/{tid}/reaction` `{reaction}` | dono do alvo / M |
| POST | `/attacks/{aid}/cancel` | dono do atacante / M |
`AttackResponse = {id, encounterId, attackerId, attackerName, sourceName, actionKind, region, reservedPa, reservedSp,
status, round, attackRoll: RollMessage|null, targets: [{id, targetId, targetName, reaction, autoResolved, canRespond,
options: [{reaction, available, reason, predictedBonus}] | null, reactionRoll: RollMessage|null, outcome,
mitigationPct, damage, counterDamage}]}`. Todas as mutações publicam `{encounterId, type: "ATTACK"}` no `combat`.
Ataque pendente bloqueia `next-turn` (409 "Há ataque aguardando reação").

### 23.4 Frontend
`ActionBar`: botão **Atacar** abre o construtor (técnica/variação ou ataque básico a partir de `GET roll-profiles`,
tipo de ação, alvos marcáveis, região/penalidade) mostrando custo e fórmulas. Painel **Ataques pendentes** no
rastreador: para quem pode responder, cartão "X ataca você com Y" + botões Defender/Esquiva/Contra/Sem reação com o
bônus previsto e motivo quando indisponível; atacante vê "aguardando" + Cancelar. Ataque resolvido: resumo por alvo
(desfecho, %, dano) e os `RollCard` do ataque/reações; HP atualiza no rastreador.

## 24. F6c — Desmaios, Adrenalina, Sangramento e Desgaste em combate (contrato, 24/10/2026)

Spec §3.3 passos 2–5, §3.6–3.7, §4.2–4.6, §5. **Teste de Vontade** (não definido na spec/livro — assumido):
`1d20 + mod(MEN)` − penalidade de Desgaste **Mental** (RN-DESG-01); vantagem no Fluxo (RN-ADR-04); +4 em Muralha.
Todos os testes automáticos rolam no servidor (`RollService`), publicados no canal **público** com purpose claro
("Vontade (PQ 0): Kuro — ND 14") e viram evento na linha do tempo.
ponytail: sem tipo de dano (cortante/contundente) no modelo → ferimento permanente vira **aviso ao Mestre**
(evento `GM` "possível ferimento permanente") quando há crítico com algum dado de dano no máximo; o Mestre registra
pelo ajuste de ficha existente. Burnout (regra opcional), Estabilizar por aliado (RN-DESM-04), Tolerância à Dor e
Sequela de Nocaute ficam fora.

### 24.1 Modelo (colunas novas)
- `encounters.default_classification` (`EASY|REAL|LETHAL`, padrão REAL) — definido na criação/edição pelo Mestre.
- `encounter_participants`: `classification` (copiada do encontro ao entrar; Mestre escala por participante —
  RN-DESM-05), `team` int (jogadores 1, NPCs 2 por padrão; Mestre edita), `adrenaline` (0–10), `rounds_at_apex`,
  `hysteria` (`FURY|WALL|LIGHTNING`, null), `save_next_used` bool, `engaged_this_round` bool (atacou ou foi atacado),
  `damage_this_turn` int (dano sofrido desde o início do turno global atual — zera a cada turno iniciado),
  `kp_zeroings` int, `death_successes`, `death_failures`, `exhaustion_nd` (Integer; não nulo = Exausto-consciente).
- Condições novas no catálogo: `BEIRA_DA_MORTE` (MANUAL), `ESTABILIZADO` (MANUAL), `MORTO` (MANUAL),
  `EXAUSTO` (MANUAL — sem técnicas nem ações com custo de SP), `DISSOCIADO` (MANUAL). `INCONSCIENTE` ganha `note`
  com a causa (`HP|SP|PQ`) — a nota é o que decide o despertar.

### 24.2 Regras (puras em `CombatRules`/`VitalRules`)
- **Aplicação de dano/perda** passa por um único ponto `VitalService.applyHp/applySp/applyKp(participant, delta,
  ctx)` usado por ataques (F6b), sangramento, testes e pelo ajuste manual do Mestre; dispara os autômatos abaixo e a
  Adrenalina de crise. `damage_this_turn += dano`; `engaged_this_round` marcado em ataque/defesa.
- **HP 0 (RN-DESM-01..03):** EASY/REAL → `INCONSCIENTE (HP)`, MANUAL ("até o próximo Repouso"). LETHAL →
  `INCONSCIENTE (HP)` + `BEIRA_DA_MORTE`; se um único golpe zerou e `excedente ≥ hpMax/2` → `MORTO` direto.
  Já em Beira da Morte: dano sofrido +1 falha (crítico +2). Início do turno: CON ND 12 (`1d20 + mod(CON)` − Desgaste
  Físico) → sucesso/falha; 3 sucessos → remove Beira, põe `ESTABILIZADO`; 3 falhas → `MORTO`.
- **SP 0 (§4.4):** Vontade ND 12 → sucesso: `exhaustion_nd = 14` e condição `EXAUSTO` (bloqueia ações/ataques com SP
  > 0: 400 "Exausto"); a cada início de turno novo teste com `exhaustion_nd` (+2 a cada teste); falha →
  `INCONSCIENTE (SP)` COUNTER 3 (rodadas = turnos do alvo), acorda com SP 1. Recuperar SP > 0 (Respirar) remove
  `EXAUSTO`. Desgaste Físico ≥ 12 (Colapso): SP fica 0 e não recupera (Respirar → 400).
- **PQ 0 (§4.3):** Vontade ND `14 + 2 × kp_zeroings` (depois incrementa) → sucesso: PQ = 2; falha →
  `INCONSCIENTE (PQ)` COUNTER `1d4 + 2`. Ao expirar o COUNTER de `INCONSCIENTE (PQ)`: PQ = 1 + `DESORIENTADO`
  END_OF_TARGET_TURN (aplicado no despertar → dura até o fim do próximo turno). `INCONSCIENTE (SP)` expira → SP 1.
- **Perda de PQ por golpe (RN-ATQ-07, simplificada):** ataque que conecta (HIT/DEFENDED < 100%/COUNTERED recebido)
  com `region` ∈ {Queixo, Têmpora, Nuca, Fígado} (comparação sem acento/caixa) → `1 + qtd "Impacto na Cabeça" na
  variação + qtd POWER nas bonificações`, × 2 se crítico em Queixo/Nuca; −1 se o alvo está em Muralha; mín. 0.
- **Dissociação (Desgaste Mental ≥ 12, RN-DESG-02):** condição `DISSOCIADO` aplicada no início do turno/ao entrar;
  iniciativa vai para o fim na largada; Ações Menores bloqueadas (MINOR, MINOR_OFFENSIVE, ataque MINOR_OFFENSIVE →
  400); −1 vantagem em ataque e reação; SP de técnicas × 2; zerar HP/SP/PQ derruba **sem** teste de Vontade.
- **Sangramento (RN-COND-02/03):** início do turno: `currentHp −= openWounds` (pelo `applyHp`). Ação nova `STANCH`
  (Estancar, 3 PA): `openWounds = 0` de si **ou** de `targetParticipantId` (aliado, mesmo `team`); bloqueada se o
  executor está Agarrado/Imobilizado. Fim do combate: `openWounds = 0` de todos.
- **Adrenalina (RN-ADR-01..04):** início do turno `+2`; crise → 10: (a) `damage_this_turn ≥ hpMax/2`, (b) começar o
  turno ou ser reduzido a `HP ≤ hpMax/4` (HP > 0); Salvar o Próximo: quando um aliado (mesmo `team`) chega a HP 0,
  cada outro participante do time ganha +5 uma vez por combate. Fim da rodada: −2 para quem não tem
  `engaged_this_round`; depois zera a flag. Fim do combate → 0.
  **Fluxo (≥5):** −1 SP em ações/técnicas/ataques (mín. 0) e vantagem em Vontade. **Ápice (10):** margem de crítico
  19 nos ataques; `rounds_at_apex + 1` no fim da rodada; no início do turno o dono/Mestre escolhe a Histeria
  (`PATCH .../hysteria`), que vale até mudar ou sair do Ápice: FURY +4 acerto, +1d6 dano (não dobra no crítico),
  −2 reações; WALL +4 reações e Vontade, −1 PQ por golpe recebido; LIGHTNING +2 acerto e reações, +1 PA no início do
  turno (ponytail: sem restringir o uso a Movimento/Ação Menor).
- Integração F6b: os bônus/penalidades acima entram como parcelas nomeadas no `Calc` (ex.: "Fúria +4",
  "Adrenalina: Fluxo −1 SP") para aparecerem no breakdown da rolagem.
- **Fim do combate (§3.7):** Adrenalina 0, Feridas 0, remove `EXAUSTO`, `DISSOCIADO` e condições não MANUAL;
  `INCONSCIENTE (HP)`, `BEIRA_DA_MORTE`, `ESTABILIZADO`, `MORTO` ficam na linha do tempo/resumo (não há estado de
  ficha fora do encontro para eles — ponytail).

### 24.3 Rotas
| Método | Rota | Quem | Efeito |
|---|---|---|---|
| PATCH | `/encounters/{eid}` `{name?, defaultClassification?}` | M | |
| PATCH | `/encounters/{eid}/participants/{pid}` | M | + `classification?, team?, adrenaline?` (log GM) |
| PATCH | `/encounters/{eid}/participants/{pid}/hysteria` `{hysteria}` | dono/M | só no Ápice (400) |
| POST | `/encounters/{eid}/participants/{pid}/vitals` `{resource: HP\|SP\|KP, delta, reason}` | M | ajuste manual passando pelos autômatos |
| — | `actions` ganha `STANCH` (+ `targetParticipantId?`) | dono/M | |
`EncounterResponse.participants[]` ganha `classification, team, adrenaline, adrenalineTier (NONE|FLOW|APEX),
hysteria, roundsAtApex, openWounds, deathSuccesses, deathFailures, exhausted (bool)`; `resources` também passa a ter
`physicalFatigue, mentalFatigue` (mesma regra de visibilidade). `adrenaline`/`hysteria` são públicos (são visíveis
na mesa). `EncounterResponse` ganha `defaultClassification`.

### 24.4 Frontend
Rastreador: medidor de Adrenalina (0–10, marcas em 5 e 10, rótulos Fluxo/Ápice), seletor de Histeria quando no Ápice
(dono/Mestre), selo de classificação (Fácil/Real/Letal) editável pelo Mestre, time, Feridas abertas, contadores de
morte (●●○ sucessos / falhas) quando em Beira da Morte, selos Exausto/Dissociado/Morto. Mestre: ajuste manual de
HP/SP/PQ com motivo; classificação padrão no criar/editar encontro. `ActionBar`: Estancar (alvo: si ou aliado),
custo de SP já com Fluxo; ações bloqueadas por Exausto/Dissociado com motivo. Ao encerrar: botão "Fechar luta"
que abre o `FightClosePanel` existente com os participantes jogadores pré-selecionados (se o componente aceitar;
senão, só navega para a aba Fichas).

## 25. F6d — Interação marcial, guarda, base ativa, contadores de arte e Contra Tardio (contrato, 24/10/2026)

Spec §6.1–6.3, §8.1–8.2, RN-REA-04/05, RN-BASE-03, RN-ATQ-03/09. ponytail: **só desarmado** (armas, Pólvora,
Artesanais, híbridos e Gadget ficam fora — o app não modela armas); altura (opcional) fora; técnicas de núcleo Reação,
Malícia/Pânico/Ás Marcial/Quebra de Paradigma fora; efeitos de **gastar** contadores ficam manuais (o motor só
ganha/perde/zera e mostra).

### 25.1 Interação marcial (`MartialRules`, pura)
- **Tipo efetivo:** atacante = `martialType` da arte do catálogo de onde vem a técnica (`CharacterArt` → `MartialArt`;
  emprestada → arte hospedeira; avulsa → arte principal); ataque básico → arte principal. Defensor → arte principal.
  **Arte principal** do participante = `mainArtId` (campo novo, Mestre/dono escolhe entre as `CharacterArt` ACTIVE;
  padrão: a de maior graduação, empate pela mais antiga). Sem arte → sem tipo (sem vantagens).
  Normalizar: "Trocação"→STRIKING, "Derrubada"→TAKEDOWN, "Raspagem"→SWEEP, qualquer texto com "Geral"→GENERAL.
- **Matriz:** STRIKING > TAKEDOWN > SWEEP > STRIKING (vencedor tem a vantagem). GENERAL vence os três e não perde
  para nenhum; GENERAL × GENERAL = nada.
  Atacante vence → +1 vantagem no acerto. Defensor vence → +1 vantagem na reação dele. Só o vencedor é afetado.
  **Coringa** (`MasteryBonus.JOKER` na técnica/base usada pelo perdedor; ataque básico não tem) anula a vantagem do
  oponente.
- **Especialização (RN-INT-03):** união de `attackSpecializations`/`reactionSpecializations` das artes ACTIVE.
  Normalização (sem acento/caixa, plural): soco(s)→SOCO, chute(s)→CHUTE, cotovelada(s)→COTOVELADA, joelhada→JOELHADA,
  cabeçada(s)→CABECADA, agarrão/agarrões/clinch/constrição/projeção/arremesso→AGARRAO, "geral"→todas;
  reações: defesa/defender/desvio→DEFEND, esquiva→DODGE, contra-ataque→COUNTER, geral→todas.
  Ataque básico fora da especialização → mod pela metade (`SheetRules.half`). Técnica de uma arte do personagem conta
  como especializada (ponytail: o catálogo não tem família por técnica). Reação fora → mod pela metade. Sem arte → metade.

### 25.2 Guarda e dominância (§6.3)
- Participante: `guard` (`NATURAL|INVERTED|FRONTAL`, padrão NATURAL). Derrubado ⇒ trata como FRONTAL.
- Ataque declara `strikeSide` (`FRONT|BACK`, opcional) — só vale para ataques básicos SOCO/CHUTE/COTOVELADA/JOELHADA
  e para técnicas quando informado. NATURAL: frente +1 acerto; trás +1d2 dano. INVERTED: frente +1 acerto; trás +1d2
  dano e −1 acerto. FRONTAL: nada. Ambidestro: sem o −1.
- **Pé à frente** (RN-GUA-01): `(dominance == RIGHT) XOR (guard == INVERTED) ? LEFT : RIGHT` (AMBIDEXTROUS conta como
  RIGHT). **Vantagem do Incomum:** defensor com pé direito à frente contra atacante com pé esquerdo à frente → +1 na
  reação; não vale com FRONTAL nem se o atacante tem Leitura > 0 contra o defensor.
- **Leitura de Oponente (RN-REA-05):** defensor que tem um `Rival` com `npcId` = NPC do atacante (ou mesmo nome) soma
  `readBonus` (0–3) nas reações contra ele.
- Ação nova `CHANGE_GUARD` (1 PA, `guard`): troca a guarda e **reinicia o Timing** do lutador (Timing passa a contar só
  ataques resolvidos depois da última troca — `guard_changed_at` timestamp). Ambidestro: a 1ª troca do turno custa 0.

### 25.3 Base ativa (RN-BASE-03/05, RN-ATQ-03)
- Participante: `active_base_id` (uma `CharacterTechnique` kind BASE e state ACTIVE da ficha, ou null). Ação nova
  `CHANGE_BASE` (1 PA, `baseId` ou null para sair). Quebra de Base ativa → 400.
- Ataque usa a base ativa nas `Opts` do perfil (Precisão e Força do Golpe — já calculado em `RollProfileService`).
- **Calejamento** (módulo de base, qtd × 2) do defensor: Redução de Dano **depois** da % da reação e antes do mínimo 1.
  Base em Esboço: metade (floor) — RN-BASE-05.
- Ao ficar Derrubado ou com Quebra de Base, `active_base_id` = null.

### 25.4 Contadores de arte (§8)
- Catálogo estático `catalog/art_counters.json`: `{key, art (nome da arte), name, min, max, perTarget,
  increments: [{event, delta}], resets: [{event, value}], note}`. Eventos do motor: `DEFEND_SUCCESS`, `DODGE_SUCCESS`,
  `COUNTER_SUCCESS`, `HIT` (ataque do dono conectou), `GOT_HIT` (dono recebeu dano), `ART_TECHNIQUE_USED` (técnica da
  arte do contador usada), `KNOCKED_DOWN` (Derrubado aplicado), `GRAPPLED` (Agarrado/Imobilizado aplicado),
  `COMBAT_END`. Seed (§8.2): Foco (Karatê 0–1: DEFEND_SUCCESS +1), Ritmo (Capoeira 0–5: DODGE/COUNTER_SUCCESS +1, HIT
  +1, GOT_HIT −2, KNOCKED_DOWN/GRAPPLED → 0), Pop (Entertainment Wrestling 0–5, manual), Embalo (Boxe 0–5:
  DODGE_SUCCESS +1, HIT com técnica de Boxe +1 via ART_TECHNIQUE_USED+HIT → modelar como `HIT` + `art` igual; GOT_HIT →
  0), Força Centrífuga (Taekwondo 0–10: ART_TECHNIQUE_USED +2), Terreno Perdido (Sumo 0–5, manual, perTarget),
  Luta em Cadeia (Wrestling 0–6, manual, perTarget). Todos resetam em COMBAT_END.
- Tabela `combat_counters`: id, participant_id, counter_key, target_participant_id (perTarget), value. Criados ao entrar
  no encontro para cada contador cuja `art` o participante tem ACTIVE (perTarget: criados sob demanda).
- `PATCH /encounters/{eid}/counters/{id}` `{value}` (dono/M) para gastar/ajustar (log ACTION "Ritmo 3 → 1").
  Mudanças automáticas viram evento na linha do tempo.

### 25.5 Contra-Ataque Tardio (RN-REA-04)
- Depois de um alvo terminar em DEFENDED ou DODGED, e até o próximo `next-turn`, o defensor (dono/M) pode
  `POST /attacks/{aid}/targets/{tid}/late-counter {sourceType, sourceId?, variation?, basicAttack?}` (1× por alvo).
  Debita o SP do perfil (0 PA). Rola o acerto do perfil do defensor (público); **> total do ataque original** →
  dano do perfil no atacante (com reação NONE; mitigação por Calejamento do atacante). Senão → a reação vira falha
  crítica: o alvo recebe o dano integral do ataque original (sem a mitigação), salvo se a variação da técnica do
  contra tem "Resposta Segura" (aí só falha, sem punição). `AttackTarget` ganha `lateCounter` (`null|SUCCESS|FAIL|SAFE`)
  e `lateCounterDamage`.

### 25.6 Resposta (`EncounterResponse.participants[]` ganha)
`mainArtId, mainArtName, martialType (STRIKING|TAKEDOWN|SWEEP|GENERAL|null), guard, leadFoot (LEFT|RIGHT),
activeBaseId, activeBaseName, counters: [{id, key, name, value, min, max, targetParticipantId}]`, e `arts:
[{id, name}]` / `bases: [{id, name}]` (as ACTIVE da ficha) só para dono/M. `AttackResponse.targets[]` ganha
`lateCounter, lateCounterDamage, canLateCounter`. O `breakdown` das rolagens mostra cada parcela nova com nome
("Interação: Trocação > Derrubada", "Guarda: frente +1", "Fora da especialização: DES/2", "Leitura de Oponente +2",
"Vantagem do Incomum +1", "Calejamento −4").

### 25.7 Frontend
Rastreador: tipo marcial (selo), arte principal (select dono/M), guarda + pé à frente, base ativa, contadores de arte
como medidores (dono/M ajustam com −/+). `ActionBar`: Trocar guarda (1 PA; 0 para ambidestro na 1ª), Trocar base
(1 PA). `AttackBuilder`: lado do golpe (frente/trás) quando aplicável. `AttackPanel`: botão **Contra-ataque tardio**
quando `canLateCounter` (escolhe perfil), resultado mostrado no resumo.

---

## 26. Backlog — próximos itens (pedido do usuário, 24/10/2026)

Ainda **sem contrato**. Cada item vira uma seção própria (modelo/regras/rotas/front) antes de ser implementado.

### 26.1 Motor de combate
1. ✅ **Efeitos de módulo automáticos no acerto** — Derrubar, Atordoar, Empurrar etc. aplicam a condição sozinhos quando o
   golpe acerta (hoje o Mestre aplica à mão via `addCondition`).
2. **Técnicas de núcleo Reação** — aparecem como opção no pedido de reação (fora da F6d; Malícia/Pânico também).
3. **Armas e interação armada** — combate com arma, Pólvora, Artesanais (F6d é só desarmado; não há modelo de arma/alcance).
4. ✅ **Base atual com efeito real** — Precisão e Força do Golpe da base ativa entram como parcelas nomeadas no breakdown
   de técnicas **e** ataques básicos. `CombatRules.baseChangeCost`: **entrar** = 1 PA, **trocar** = 2 PA, **sair** = 1 PA.
   Outros módulos de base (além de Precisão, Força do Golpe e Calejamento) continuam sem efeito automático (item 1).
5. ✅ **Última luta continua visível** — a lista vem `createdAt desc` e o painel mantém o encerrado selecionado (prefere
   um não encerrado, senão o mais recente); cabeçalho "Última luta — encerrada em …" (`EncounterResponse.endedAt`).

### 26.2 HUD de combate (UX)
6. ✅ **Menu lateral de ações por tipo**, com cor fixa por categoria: **Ação Maior** (vermelho), **Ação Menor** (verde),
   **Movimento** (azul), **Livre** (rosa). A linha do tempo desce para baixo do HUD. Cores como tokens no `index.css`.
7. ✅ **HUD mais amigável** — revisão geral de hierarquia: turno atual e PA em destaque, ação disponível óbvia, menos
   campos soltos; ações indisponíveis desabilitadas com o motivo.
8. ✅ **Animações de ataque** durante o combate (golpe saindo do atacante para o alvo, impacto no acerto, esquiva/defesa).
9. ✅ **Tela de reação estilizada** — quando o usuário recebe um ataque, abre por cima da área de combate uma seleção de
   reação em destaque (estilo quadrinho), com as opções e o custo de cada uma; hoje é um item dentro do `AttackPanel`.

### 26.3 Rolagens
10. ✅ **Animação de rolagem em duas etapas** — o dado gira, o número sai **no centro da tela**, depois se move/encolhe
    até montar o card grande do feed (`RollCard` em destaque). Respeitar `prefers-reduced-motion` (pula direto ao card).

### 26.4 Fichas e campanha
11. ✅ **Fichas do Mestre (instantâneas)** — o Mestre cria fichas na hora, como quem cria NPC, sem Construção Inicial nem
    aprovação; cada ficha pode ser **visível ou oculta** para os jogadores (reaproveitar o modelo de ficha de NPC e a
    visibilidade por seção, se couber).
12. ✅ **Listagem de membros da campanha** — `GET /campaigns/{cid}/members` (membro; senão 403) → `MemberResponse[]`.
    O formulário de canal personalizado usa os PLAYERs da campanha (antes: só quem tinha ficha).
13. ✅ **Impressão dos `<details>`** — `beforeprint`/`afterprint` em `main.jsx` abrem e fecham de volta; o botão
    "Imprimir / PDF" abre antes e espera 600 ms para os traços carregarem efeitos/variações.

## 27. HUD de combate e tela de reação (itens 6, 7 e 9 do §26 — só front, 25/10/2026)

- **Tokens** `--act-major` (vermelho), `--act-minor` (verde), `--act-move` (azul), `--act-free` (rosa) nos dois temas;
  classes `.act-group`, `.act-btn`, `.act-cost`, `.pa-pip`. Categoria de cada ação em `combatLabels.ACTIONS[].cat` e
  grupos em `ACTION_CATEGORIES`.
- **Agrupamento:** Maior = Atacar, Estancar, Ação maior, Preparada, Respirar · Menor = Golpe rápido (ataque
  `MINOR_OFFENSIVE`), Ação menor, Menor ofensiva, Atravessar, Trocar guarda, Entrar/Trocar/Sair da base · Movimento =
  Mover, Desengajar, Levantar · Livre = Livre.
- **Layout (`CombatPanel`):** faixa de turno (rodada, de quem é a vez, PA em losangos, SP) → grade com `ActionBar` como
  menu lateral fixo (`lg:sticky`, só para quem controla o participante da vez) + coluna com construtor de ataque, ataques
  e rastreador → **linha do tempo embaixo**, largura total. Ação indisponível fica desabilitada com o motivo visível.
- **Tela de reação (`ReactionOverlay`):** abre por cima quando há alvo `canRespond` sem reação num ataque
  `AWAITING_REACTIONS`; cartões Defender/Esquiva/Contra-ataque/Sem reação com atributo + bônus previsto
  (`predictedBonus`), resumo da regra (`REACTION_HINT`, espelha `AttackRules.resolve`) e custo (Contra = 1 SP).
  "Decidir depois" / Esc adia aquele alvo (os botões inline do `AttackPanel` continuam). Sem mudança de backend.

## 28. Animações de rolagem e de ataque (itens 8 e 10 do §26 — só front, 25/10/2026)

- **`RollReveal`** (rolagem nova com a página aberta): `waiting → rolling → result → fly → done`. O d20 gira no centro da
  tela (900 ms), o total aparece grande no centro com quem rolou e a fonte (550 ms) e voa/encolhe até o
  `[data-roll-total]` do `RollCard` (450 ms, FLIP com Web Animations). O card fica invisível no lugar durante a animação
  (mantém a altura e dá o alvo). Card sem caixa (ex.: `<details>` fechado) → o número só some. Crítico em amarelo com
  brilho, falha em vermelho. Camada em portal no `body` (`.roll-stage`, `pointer-events: none`).
- **Fila** (`rolls/revealQueue.js`, `queueSlot(ms)`): animações de centro de tela entram em sequência (ataque → reações →
  golpe), nunca sobrepostas.
- **Golpe** (`combat/attackFx.js`, `playAttack`): ataque que acabou de resolver (não os da 1ª carga do encontro) anima
  nos cartões do rastreador (`[data-participant-id]`) depois das rolagens: atacante avança na direção do alvo; alvo
  treme (HIT), desliza (DODGED) ou encolhe (DEFENDED); COUNTERED treme o atacante; onomatopeia sobre o alvo
  (`POW! −dano`, `ESQUIVOU!`, `BLOQUEIO!`, `CONTRA!`, `ERROU!`).
- `prefers-reduced-motion`: pula tudo direto para o card, sem golpe.

## 29. Fichas do Mestre instantâneas (item 11 do §26, 25/10/2026)

- **Modelo:** reaproveita NPC + ficha de NPC (F1.1), sem tabela nova. A ficha instantânea é um NPC (Mongo) sem
  interações, com dono = Mestre, associado à campanha com `bookable = false` (fora da Agenda) e ficha `COMPLETED`.
- **Rota:** `POST /campaigns/{cid}/gm-sheets` `{sheet: CreateCharacterSheetRequest, visible}` → 201
  `CharacterSheetResponse` (com `npcId`). Só MASTER (403). `visible = true` → `campaign_npcs.visible = true` e as 4
  seções de `npc_sheet_visibility` liberadas; `false` → NPC oculto e seções ocultas (padrão).
  `CharacterSheetService.createGmSheet` → `createForNpc` (log `BUILD/CREATE_SHEET` e `SHEET_CREATED`).
- **Front:** bloco "Fichas do Mestre" (`components/sheet/GmSheets.jsx`) na aba Fichas: lista os NPCs da campanha com
  ficha; Mestre cria pela "Ficha rápida" (`CharacterSheetForm` com `create`), alterna Visível/Oculta
  (`PATCH .../npcs/{nid}/visibility`) e abre a ficha no `NpcSheetPanel` (com os toggles por seção). Jogador vê só
  as visíveis, com as seções liberadas.
- Depois da criação dá para editar como qualquer NPC (aba NPCs) e colocar no combate (NPC com `sheetId`).

## 30. Efeitos de módulo no acerto (item 1 do §26, 25/10/2026)

Fonte: capítulo de Condições (Regras Gerais: *"quando vier de uma técnica, [o alvo rola] contra o resultado do teste
da técnica"*), tabela de Condições de Combate e Danos de Peso (Derrubada). O capítulo de Criação Marcial não está no
repositório: o atributo de resistência de cada efeito foi deduzido das técnicas do livro (Liver Shot: FOR; Khao Loy: CON;
Empurrar: FOR) e fica num só lugar (`EffectRules`).

- **Quando:** técnica (não ataque básico) com desfecho `HIT` no alvo. Itens `kind = EFFECT` da variação usada + extras
  (`RollProfileService.effects`). `onDie` ("no dado"): só com d20 natural ≥ 17 ou crítico.
- **Resistência:** crítico → entra sem teste. Senão o alvo rola `RollProfileService.attribute` (mod. + Desgaste) contra
  o **total do ataque**; empate resiste. Paralisado falha sozinho em FOR/DES. A rolagem sai no canal público.
- **Mapa (`service/rules/EffectRules`):**

| Módulo | Resiste | Resultado na falha |
|---|---|---|
| Derrubar | FOR | `DERRUBADO` + dano de Derrubada `max(1, 2 + mod. de Peso)` de quem cai; perde a base |
| Quebrar Base | FOR | `QUEBRA_DE_BASE` (perde a base) |
| Empurrar | FOR | só registra (sem grade; o Mestre posiciona) |
| Prender | FOR | `AGARRADO` |
| Desorientar | CON | `DESORIENTADO` |
| Expor | DES | `VULNERAVEL` |
| Atordoar | CON | `ATORDOADO` |
| Paralisar | CON | `PARALISADO` (contador 1 turno) |
| Cegar | CON | `MEIO_CEGO`; já Meio-Cego → `CEGO` |
| Sufocar | CON | `SUFOCANDO` |
| Roubar Tempo | CON | perde `qtd` PA no próximo turno (`paLossNext`) |
| Abrir Ferida | — | `qtd` Feridas abertas (`VitalService.openWounds`), sem teste |
| demais (Interromper, Aleijar, Enfraquecer, Esmagar, Aprofundar Ferida, Marcar) | — | evento "aplicar manualmente" |

- Duração: padrão do `conditions.json` (bate com a tabela do livro). Derrubado/Agarrado disparam os contadores de arte.
- Fora do escopo: bônus de base "+N para resistir a Empurrar, Derrubar e Quebrar Base" (texto livre no catálogo),
  Arremesso/Montinho/Impacto (dano por tipo de chão) e Ferimentos Permanentes completos (hoje só o aviso ao Mestre).
- **Vulnerável (regra da mesa, 25/10/2026):** o alvo não reage, mas o ataque só acerta com **total ≥ 10**
  (`AttackRules.VULNERABLE_ND`); abaixo disso o desfecho é `MISS` (sem dano nem efeitos). Crítico acerta sempre.
  Vale só para Vulnerável (Atordoado/Inconsciente seguem acertando direto).
- **Atordoado (regra da mesa, 25/10/2026):** continua perdendo o próximo turno, mas **pode reagir** — com
  **desvantagem** e **sem Contra-ataque** (opção indisponível; `respond` COUNTER → 400). Saiu de `NO_REACTION`.
- **Paralisado (texto novo):** deslocamento zero e **nenhuma ação** (`act`/`declare` → 400). Pode tentar reagir:
  antes da rolagem passa em **Vontade ND 15** (`VitalService.willTest`); falhou → reação vira `NONE`. Reage com
  **desvantagem**; falha automática em testes de FOR/DES (resistência a efeitos); ataques contra ele têm vantagem.

## 31. Base ativa com todos os números (continuação do item 4 do §26, 25/10/2026)

Bug corrigido: o valor dos itens de base era lido da **quantidade**, que nos itens não afins guarda o **custo**
("Precisão +1 (não afim, 2)" valia +2; "Força do Golpe +3 (não afim, 4)" valia +4), e o Calejamento usava qtd × 2
("Calejamento 3" virava RD 6; o livro diz RD 3). Agora `service/rules/BaseRules` lê **valor e escopo do rótulo**
(sem número no rótulo → quantidade). Escopo: `em Defender/Esquiva/Contra-Ataque`, `em/contra Trocação/Derrubada/
Raspagem`, `fora de …` (ignorado).

| Item de base | Onde entra |
|---|---|
| Precisão ±N [em tipo] | acerto do golpe do tipo (genérico sempre); Esboço metade |
| Força do Golpe +N [em tipo] | dano do golpe do tipo |
| Penalidade −N em Trocação/… | acerto do golpe daquele tipo |
| Guarda +N / Penalidade −N em reação | rolagem da reação e `predictedBonus` da tela de reação |
| Fraqueza / Leitura de Estilo contra tipo | desvantagem / vantagem nas reações contra golpe daquele tipo |
| Calejamento N | Redução de Dano N (Esboço metade) |
| Enraizado +N | resistência a Empurrar, Derrubar e Quebrar Base (§30) |
| Calma +N | Testes de Vontade (`VitalService`) |
| Passada +1,5 m / Pesada −1,5 m | cada Movimentação (mín. 1,5 m) |

Tipo do golpe: arte da técnica, senão a arte principal (`MartialService.attackType`), passado em
`RollProfileService.Opts.attackType`; nas rolagens fora do combate (teste da ficha) só os itens genéricos valem.
Fora: Tração (sem motor de competição de agarre), "Penalidade fora de chutes e joelhadas", módulos únicos (texto).

