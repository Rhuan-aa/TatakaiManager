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

-- Reserva de slot (slot_number: 1 a 4). npc_id é UUID solto (NPC vive no Mongo).
-- Atividade solo (sem NPC): npc_id/npc_name/interaction_name nulos,
-- solo_activity_type + description preenchidos (ver US de treino solo).
CREATE TABLE bookings (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    time_skip_day_id    UUID NOT NULL REFERENCES time_skip_days(id),
    npc_id              UUID,
    npc_name            VARCHAR(100),
    user_id             UUID NOT NULL REFERENCES users(id),
    slot_number         SMALLINT NOT NULL CHECK (slot_number BETWEEN 1 AND 4),
    interaction_name    VARCHAR(100),
    idle_point_cost     SMALLINT NOT NULL DEFAULT 0,
    solo_activity_type  VARCHAR(20) CHECK (solo_activity_type IN ('TREINO', 'ESTUDO', 'ACAO_GERAL')),
    description         TEXT,
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    CHECK ( (npc_id IS NOT NULL AND solo_activity_type IS NULL)
         OR (npc_id IS NULL AND solo_activity_type IS NOT NULL) ),
    UNIQUE (time_skip_day_id, npc_id, slot_number)  -- regra central de conflito (NPC); solo checado em app
);

CREATE TABLE interaction_logs (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id   UUID REFERENCES bookings(id),  -- nulo em logs livres do Mestre
    user_id      UUID NOT NULL REFERENCES users(id),
    campaign_id  UUID NOT NULL REFERENCES campaigns(id),
    narrative    TEXT NOT NULL,
    created_at   TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Visibilidade de seções da ficha do jogador por campanha
CREATE TABLE character_sheet_visibility (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    campaign_member_id UUID NOT NULL REFERENCES campaign_members(id),
    section            VARCHAR(50) NOT NULL,  -- LOGS, ATTRIBUTES, KNOWLEDGE, TRAITS
    hidden_by_master   BOOLEAN NOT NULL DEFAULT FALSE,
    hidden_by_self     BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE (campaign_member_id, section)
);
