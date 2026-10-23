-- ============================================================
-- TATAKAI MANAGER — Schema inicial
-- ============================================================

CREATE TABLE users (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(100)        NOT NULL,
    email       VARCHAR(150)        NOT NULL UNIQUE,
    password    VARCHAR(255)        NOT NULL,
    created_at  TIMESTAMP           NOT NULL DEFAULT NOW()
);

CREATE TABLE campaigns (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(100)        NOT NULL,
    description TEXT,
    master_id   UUID                NOT NULL REFERENCES users(id),
    created_at  TIMESTAMP           NOT NULL DEFAULT NOW()
);

CREATE TABLE campaign_members (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    campaign_id  UUID NOT NULL REFERENCES campaigns(id),
    user_id      UUID NOT NULL REFERENCES users(id),
    role         VARCHAR(20) NOT NULL CHECK (role IN ('MASTER', 'PLAYER')),
    UNIQUE (campaign_id, user_id)
);

-- A ficha textual do NPC (nome, descrição, atributos, conhecimentos, traços, specs,
-- interações) NÃO vive mais aqui — foi migrada para o MongoDB (coleção `npcs`, ver
-- entity/Npc.java e DEPLOY.md). Só a imagem e as referências por id continuam no
-- Postgres, então npc_id abaixo é um UUID solto (sem FK — o NPC pode estar em outro banco).

-- Imagem (retrato) do NPC — tabela separada para não pesar as consultas
CREATE TABLE npc_images (
    npc_id       UUID PRIMARY KEY,
    content_type VARCHAR(100) NOT NULL,
    data         BYTEA NOT NULL
);

-- NPC associado a campanhas (compartilhável); visible controla visibilidade para jogadores
CREATE TABLE campaign_npcs (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    campaign_id  UUID NOT NULL REFERENCES campaigns(id),
    npc_id       UUID NOT NULL,
    visible      BOOLEAN NOT NULL DEFAULT TRUE,
    UNIQUE (campaign_id, npc_id)
);

CREATE TABLE time_skips (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    campaign_id  UUID NOT NULL REFERENCES campaigns(id),
    name         VARCHAR(100) NOT NULL,
    total_days   SMALLINT NOT NULL CHECK (total_days > 0),
    current_day  SMALLINT NOT NULL DEFAULT 1 CHECK (current_day >= 1),
    status       VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'CLOSED')),
    created_at   TIMESTAMP NOT NULL DEFAULT NOW(),
    closed_at    TIMESTAMP
);

CREATE TABLE time_skip_days (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    time_skip_id  UUID NOT NULL REFERENCES time_skips(id) ON DELETE CASCADE,
    day_number    SMALLINT NOT NULL,
    UNIQUE (time_skip_id, day_number)
);

-- Atividade solo customizada, cadastrada pelo Mestre e exclusiva de um TimeSkip
-- (ex.: "Reconstrução da vila") — ao contrário de Treino/Estudo/Ação geral, que são
-- fixos e valem para qualquer TimeSkip (ver enum solo_activity_type em bookings).
CREATE TABLE time_skip_activities (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    time_skip_id    UUID NOT NULL REFERENCES time_skips(id) ON DELETE CASCADE,
    name            VARCHAR(100) NOT NULL,
    description     TEXT NOT NULL,
    idle_point_cost SMALLINT NOT NULL DEFAULT 0,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (time_skip_id, name)
);

-- Reserva de slot (slot_number: 1 a 4 + slot extra 5). npc_id é UUID solto (NPC vive no Mongo).
-- Atividade solo (sem NPC): npc_id/npc_name/interaction_name nulos, e exatamente um dos
-- dois caminhos preenchido — solo_activity_type (tipo fixo) ou time_skip_activity_id
-- (customizada, cadastrada no TimeSkip; activity_name é o snapshot do nome).
CREATE TABLE bookings (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    time_skip_day_id       UUID NOT NULL REFERENCES time_skip_days(id),
    npc_id                 UUID,
    npc_name               VARCHAR(100),
    user_id                UUID NOT NULL REFERENCES users(id),
    slot_number            SMALLINT NOT NULL CHECK (slot_number BETWEEN 1 AND 5),
    interaction_name       VARCHAR(100),
    idle_point_cost        SMALLINT NOT NULL DEFAULT 0,
    solo_activity_type     VARCHAR(20) CHECK (solo_activity_type IN ('TREINO', 'ESTUDO', 'ACAO_GERAL')),
    time_skip_activity_id  UUID REFERENCES time_skip_activities(id),
    activity_name          VARCHAR(100),
    description            TEXT,
    created_at             TIMESTAMP NOT NULL DEFAULT NOW(),
    CHECK ( num_nonnulls(npc_id, solo_activity_type, time_skip_activity_id) = 1 ),
    UNIQUE (time_skip_day_id, npc_id, slot_number)  -- regra central de conflito (NPC); solo checado em app
);

CREATE TABLE interaction_logs (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id   UUID REFERENCES bookings(id),  -- nulo em logs livres do Mestre
    user_id      UUID NOT NULL REFERENCES users(id),
    campaign_id  UUID NOT NULL REFERENCES campaigns(id),
    narrative    TEXT NOT NULL,
    created_at   TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMP
);

-- Visibilidade de seções da ficha do jogador por campanha
CREATE TABLE character_sheet_visibility (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    campaign_member_id UUID NOT NULL REFERENCES campaign_members(id),
    section            VARCHAR(50) NOT NULL,  -- LOGS, ATTRIBUTES, KNOWLEDGE, TRAITS, RESOURCES, INJURIES, INVENTORY, ACTIVITY
    hidden_by_master   BOOLEAN NOT NULL DEFAULT FALSE,
    hidden_by_self     BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE (campaign_member_id, section)
);

-- ============================================================
-- Ficha digital (F1) — 1 ficha por membro PLAYER da campanha, ou 1 por NPC do acervo
-- (npc_id; estado compartilhado entre as campanhas onde o NPC aparece).
-- Máximos (hp/sp/kp_max) são persistidos: não recalculam com CON (RN-REC-01).
-- ============================================================

CREATE TABLE character_sheets (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    campaign_member_id       UUID UNIQUE REFERENCES campaign_members(id),
    npc_id                   UUID UNIQUE,  -- UUID solto (NPC vive no Mongo)
    npc_owner_id             UUID REFERENCES users(id),
    name                     VARCHAR(100) NOT NULL,
    build_status             VARCHAR(20) NOT NULL CHECK (build_status IN ('IN_BUILD', 'DRAFT', 'PENDING_APPROVAL', 'COMPLETED')),
    body_type                VARCHAR(20) NOT NULL CHECK (body_type IN ('ENDOMORPH', 'MESOMORPH', 'ECTOMORPH')),
    dominance                VARCHAR(20) NOT NULL CHECK (dominance IN ('RIGHT', 'LEFT', 'AMBIDEXTROUS')),
    height_m                 DOUBLE PRECISION NOT NULL,
    weight_kg                DOUBLE PRECISION NOT NULL,
    str                      INTEGER NOT NULL CHECK (str >= 0),
    dex                      INTEGER NOT NULL CHECK (dex >= 0),
    con                      INTEGER NOT NULL CHECK (con >= 0),
    intl                     INTEGER NOT NULL CHECK (intl >= 0),
    cha                      INTEGER NOT NULL CHECK (cha >= 0),
    men                      INTEGER NOT NULL CHECK (men >= 0),
    tal                      INTEGER NOT NULL CHECK (tal >= 0),
    hp_max                   INTEGER NOT NULL,
    sp_max                   INTEGER NOT NULL,
    kp_max                   INTEGER NOT NULL,
    current_hp               INTEGER NOT NULL,
    current_sp               INTEGER NOT NULL,
    current_kp               INTEGER NOT NULL,
    open_wounds              INTEGER NOT NULL DEFAULT 0,
    physical_fatigue         INTEGER NOT NULL DEFAULT 0,
    mental_fatigue           INTEGER NOT NULL DEFAULT 0,
    fight_experience         INTEGER NOT NULL DEFAULT 0,
    pending_overcome_points  INTEGER NOT NULL DEFAULT 0,
    money                    INTEGER NOT NULL DEFAULT 0,  -- em diárias
    limiter_adjustments      JSONB NOT NULL DEFAULT '{}', -- ajustes de traço/clã: {"STR": 5, "HP": 10}
    version                  BIGINT NOT NULL DEFAULT 0,   -- lock otimista (@Version)
    created_at               TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMP NOT NULL DEFAULT NOW(),
    CHECK ( num_nonnulls(campaign_member_id, npc_id) = 1 )
);

-- Seções da ficha de um NPC que os jogadores de uma campanha podem ver (padrão: ocultas).
CREATE TABLE npc_sheet_visibility (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    campaign_npc_id  UUID NOT NULL REFERENCES campaign_npcs(id) ON DELETE CASCADE,
    section          VARCHAR(50) NOT NULL,
    visible          BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE (campaign_npc_id, section)
);

CREATE TABLE permanent_injuries (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sheet_id            UUID NOT NULL REFERENCES character_sheets(id) ON DELETE CASCADE,
    type                VARCHAR(20) NOT NULL CHECK (type IN ('CUTTING', 'PIERCING', 'BLUNT')),
    region              VARCHAR(100) NOT NULL,
    severity            VARCHAR(20) NOT NULL CHECK (severity IN ('MINOR', 'MODERATE', 'SEVERE', 'LETHAL')),
    hp_max_reduction    INTEGER NOT NULL,
    limiter_reduction   INTEGER NOT NULL,
    affected_attribute  VARCHAR(10),
    treated             BOOLEAN NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE inventory_items (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sheet_id     UUID NOT NULL REFERENCES character_sheets(id) ON DELETE CASCADE,
    name         VARCHAR(100) NOT NULL,
    quantity     INTEGER NOT NULL CHECK (quantity >= 0),
    description  TEXT
);

-- Log de atividade da ficha: imutável, sem FK para sobreviver à remoção da ficha.
CREATE TABLE sheet_activity_logs (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sheet_id      UUID NOT NULL,
    campaign_id   UUID,  -- nulo em ficha de NPC
    actor_id      UUID,
    actor_role    VARCHAR(10) NOT NULL CHECK (actor_role IN ('PLAYER', 'MASTER', 'SYSTEM')),
    category      VARCHAR(30) NOT NULL,
    action        VARCHAR(50) NOT NULL,
    field         VARCHAR(50),
    before_value  TEXT,
    after_value   TEXT,
    roll_id       UUID,
    reason        VARCHAR(500),
    created_at    TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_sheet_activity_logs_campaign ON sheet_activity_logs (campaign_id, created_at DESC);
CREATE INDEX idx_sheet_activity_logs_sheet ON sheet_activity_logs (sheet_id, created_at DESC);

-- Rolagens geradas no servidor (o cliente nunca envia resultado de dado).
CREATE TABLE rolls (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    campaign_id   UUID,  -- nulo em rolagem de ficha de NPC
    sheet_id      UUID,
    actor_id      UUID NOT NULL,
    expression    VARCHAR(50) NOT NULL,
    dice_results  TEXT NOT NULL,  -- JSON array, ex.: [3,5]
    modifier      INTEGER NOT NULL,
    total         INTEGER NOT NULL,
    natural_d20   INTEGER,
    purpose       VARCHAR(200) NOT NULL,
    created_at    TIMESTAMP NOT NULL DEFAULT NOW()
);

-- ============================================================
-- F2 — Construção Inicial, traços/clãs da ficha e regras opcionais da campanha.
-- Catálogos (traitDefinitions, clans) vivem no MongoDB; aqui ficam só snapshots.
-- ============================================================

CREATE TABLE campaign_rule_settings (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    campaign_id  UUID NOT NULL REFERENCES campaigns(id) ON DELETE CASCADE,
    rule_key     VARCHAR(40) NOT NULL,
    enabled      BOOLEAN NOT NULL DEFAULT FALSE,
    params       JSONB,
    UNIQUE (campaign_id, rule_key)
);

-- Traço adquirido: snapshot do catálogo na versão usada (RN-TRAIT-02).
CREATE TABLE character_traits (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sheet_id       UUID NOT NULL REFERENCES character_sheets(id) ON DELETE CASCADE,
    trait_id       UUID NOT NULL,  -- id no Mongo
    trait_version  INTEGER NOT NULL,
    name           VARCHAR(100) NOT NULL,
    rarity         VARCHAR(20) NOT NULL,
    category       VARCHAR(20) NOT NULL,
    description    TEXT,
    build_gains    JSONB,
    acquired_in    VARCHAR(20) NOT NULL CHECK (acquired_in IN ('INITIAL_BUILD', 'CAMPAIGN')),
    gains_applied  BOOLEAN NOT NULL DEFAULT FALSE,
    acquired_at    TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE character_clans (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sheet_id      UUID NOT NULL REFERENCES character_sheets(id) ON DELETE CASCADE,
    clan_id       UUID NOT NULL,  -- id no Mongo
    clan_version  INTEGER NOT NULL,
    name          VARCHAR(100) NOT NULL,
    build_gains   JSONB,
    acquired_at   TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Estado do wizard de Construção Inicial (1:1 com a ficha).
CREATE TABLE character_builds (
    sheet_id    UUID PRIMARY KEY REFERENCES character_sheets(id) ON DELETE CASCADE,
    step        VARCHAR(20) NOT NULL,
    settings    JSONB NOT NULL,  -- regras da campanha no momento do início
    state       JSONB NOT NULL,  -- dados rolados e escolhas
    version     BIGINT NOT NULL DEFAULT 0,
    updated_at  TIMESTAMP NOT NULL DEFAULT NOW()
);
