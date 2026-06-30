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

CREATE TABLE npcs (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(100)    NOT NULL,
    description TEXT,
    -- Atributos opcionais
    attr_for    SMALLINT,
    attr_des    SMALLINT,
    attr_con    SMALLINT,
    attr_men    SMALLINT,
    attr_int    SMALLINT,
    attr_tal    SMALLINT,
    created_by  UUID NOT NULL REFERENCES users(id),
    created_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE npc_specs (
    id      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    npc_id  UUID NOT NULL REFERENCES npcs(id) ON DELETE CASCADE,
    name    VARCHAR(100) NOT NULL,
    description TEXT
);

CREATE TABLE npc_traits (
    id      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    npc_id  UUID NOT NULL REFERENCES npcs(id) ON DELETE CASCADE,
    name    VARCHAR(100) NOT NULL
);

-- Tipos de interação disponíveis por NPC (TREINO, TRABALHO, DESCANSO, OUTRO)
CREATE TABLE npc_interaction_types (
    id      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    npc_id  UUID NOT NULL REFERENCES npcs(id) ON DELETE CASCADE,
    type    VARCHAR(50) NOT NULL,
    UNIQUE (npc_id, type)
);

-- NPC associado a campanhas (compartilhável); visible controla visibilidade para jogadores
CREATE TABLE campaign_npcs (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    campaign_id  UUID NOT NULL REFERENCES campaigns(id),
    npc_id       UUID NOT NULL REFERENCES npcs(id),
    visible      BOOLEAN NOT NULL DEFAULT TRUE,
    UNIQUE (campaign_id, npc_id)
);

CREATE TABLE time_skips (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    campaign_id  UUID NOT NULL REFERENCES campaigns(id),
    name         VARCHAR(100) NOT NULL,
    total_days   SMALLINT NOT NULL CHECK (total_days > 0),
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

-- Reserva de slot (slot_number: 1 a 4)
CREATE TABLE bookings (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    time_skip_day_id UUID NOT NULL REFERENCES time_skip_days(id),
    npc_id           UUID NOT NULL REFERENCES npcs(id),
    user_id          UUID NOT NULL REFERENCES users(id),
    slot_number      SMALLINT NOT NULL CHECK (slot_number BETWEEN 1 AND 4),
    interaction_type VARCHAR(50) NOT NULL,
    created_at       TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (time_skip_day_id, npc_id, slot_number)  -- regra central de conflito
);

CREATE TABLE interaction_logs (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id   UUID NOT NULL REFERENCES bookings(id),
    user_id      UUID NOT NULL REFERENCES users(id),
    campaign_id  UUID NOT NULL REFERENCES campaigns(id),
    narrative    TEXT NOT NULL,
    created_at   TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Visibilidade de seções da ficha do jogador por campanha
CREATE TABLE character_sheet_visibility (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    campaign_member_id UUID NOT NULL REFERENCES campaign_members(id),
    section            VARCHAR(50) NOT NULL,  -- LOGS, ATTRIBUTES, SPECS, TRAITS
    hidden_by_master   BOOLEAN NOT NULL DEFAULT FALSE,
    hidden_by_self     BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE (campaign_member_id, section)
);
