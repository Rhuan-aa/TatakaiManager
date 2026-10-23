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

### 14.1 NPC ligado ao catálogo
- **Traços dos NPCs vindos do catálogo:** hoje o NPC (Mongo `npcs`) tem `traits` como texto livre `{name, description}`.
  Trocar por referência ao catálogo (`ADD_TRAIT`/`REMOVE_TRAIT` na ficha de NPC, com snapshot como na ficha de jogador) e
  migrar os traços livres existentes (casar por nome; o que não casar vira traço de campanha ou fica como nota).

### 14.2 Ficha de NPC = ficha de jogador
- **Criar ficha como Mestre cria o NPC automaticamente:** novo fluxo "Nova ficha de NPC" na aba Fichas/NPCs → cria o `Npc`
  (nome, descrição, interações) + a `CharacterSheet` (npcId) numa ação só.
- **Mesma visualização da ficha de jogador** para NPC, somando as informações que hoje ficam nos cards: retrato, descrição,
  interações (com custo em pontos de ócio), conhecimentos, specs, visibilidade na campanha. Avaliar se os cards da aba NPCs
  passam a abrir essa ficha completa.

### 14.3 Ajustes visuais da ficha e do site
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
