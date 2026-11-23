-- Gerado por scripts/gen_upgrade_sql.py. Idempotente: pode rodar mais de uma vez.
BEGIN;
SET client_min_messages = warning;

-- CHECKs antigos (enum de Hibernate, faixa de slot): o app valida os valores.
DO $$ DECLARE r record; BEGIN
  FOR r IN SELECT conrelid::regclass AS t, conname FROM pg_constraint
           WHERE contype = 'c' AND connamespace = 'public'::regnamespace LOOP
    EXECUTE format('ALTER TABLE %s DROP CONSTRAINT %I', r.t, r.conname);
  END LOOP;
END $$;

-- Várias fichas por jogador (§52): sai o unique de character_sheets.campaign_member_id.
DO $$ DECLARE r record; BEGIN
  FOR r IN SELECT c.conname FROM pg_constraint c
           JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = ANY (c.conkey)
           WHERE c.contype = 'u' AND c.conrelid = to_regclass('public.character_sheets')
             AND array_length(c.conkey, 1) = 1 AND a.attname = 'campaign_member_id' LOOP
    EXECUTE format('ALTER TABLE public.character_sheets DROP CONSTRAINT %I', r.conname);
  END LOOP;
END $$;

CREATE TABLE IF NOT EXISTS public.attack_targets (
    auto_resolved boolean NOT NULL,
    counter_damage integer,
    damage integer,
    late_counter_damage integer,
    mitigation_pct integer,
    splash boolean DEFAULT false NOT NULL,
    responded_at timestamp(6) with time zone,
    late_counter character varying(10),
    outcome character varying(10),
    reaction character varying(10),
    attack_id uuid NOT NULL,
    defender_id uuid,
    id uuid NOT NULL,
    late_counter_roll_id uuid,
    reaction_roll_id uuid,
    reaction_technique_id uuid,
    target_id uuid NOT NULL,
    reaction_variation character varying(100)
);
ALTER TABLE public.attack_targets ADD COLUMN IF NOT EXISTS auto_resolved boolean;
ALTER TABLE public.attack_targets ADD COLUMN IF NOT EXISTS counter_damage integer;
ALTER TABLE public.attack_targets ALTER COLUMN counter_damage DROP NOT NULL;
ALTER TABLE public.attack_targets ADD COLUMN IF NOT EXISTS damage integer;
ALTER TABLE public.attack_targets ALTER COLUMN damage DROP NOT NULL;
ALTER TABLE public.attack_targets ADD COLUMN IF NOT EXISTS late_counter_damage integer;
ALTER TABLE public.attack_targets ALTER COLUMN late_counter_damage DROP NOT NULL;
ALTER TABLE public.attack_targets ADD COLUMN IF NOT EXISTS mitigation_pct integer;
ALTER TABLE public.attack_targets ALTER COLUMN mitigation_pct DROP NOT NULL;
ALTER TABLE public.attack_targets ADD COLUMN IF NOT EXISTS splash boolean DEFAULT false NOT NULL;
ALTER TABLE public.attack_targets ADD COLUMN IF NOT EXISTS responded_at timestamp(6) with time zone;
ALTER TABLE public.attack_targets ALTER COLUMN responded_at DROP NOT NULL;
ALTER TABLE public.attack_targets ADD COLUMN IF NOT EXISTS late_counter character varying(10);
ALTER TABLE public.attack_targets ALTER COLUMN late_counter DROP NOT NULL;
ALTER TABLE public.attack_targets ADD COLUMN IF NOT EXISTS outcome character varying(10);
ALTER TABLE public.attack_targets ALTER COLUMN outcome DROP NOT NULL;
ALTER TABLE public.attack_targets ADD COLUMN IF NOT EXISTS reaction character varying(10);
ALTER TABLE public.attack_targets ALTER COLUMN reaction DROP NOT NULL;
ALTER TABLE public.attack_targets ADD COLUMN IF NOT EXISTS attack_id uuid;
ALTER TABLE public.attack_targets ADD COLUMN IF NOT EXISTS defender_id uuid;
ALTER TABLE public.attack_targets ALTER COLUMN defender_id DROP NOT NULL;
ALTER TABLE public.attack_targets ADD COLUMN IF NOT EXISTS id uuid;
ALTER TABLE public.attack_targets ADD COLUMN IF NOT EXISTS late_counter_roll_id uuid;
ALTER TABLE public.attack_targets ALTER COLUMN late_counter_roll_id DROP NOT NULL;
ALTER TABLE public.attack_targets ADD COLUMN IF NOT EXISTS reaction_roll_id uuid;
ALTER TABLE public.attack_targets ALTER COLUMN reaction_roll_id DROP NOT NULL;
ALTER TABLE public.attack_targets ADD COLUMN IF NOT EXISTS reaction_technique_id uuid;
ALTER TABLE public.attack_targets ALTER COLUMN reaction_technique_id DROP NOT NULL;
ALTER TABLE public.attack_targets ADD COLUMN IF NOT EXISTS target_id uuid;
ALTER TABLE public.attack_targets ADD COLUMN IF NOT EXISTS reaction_variation character varying(100);
ALTER TABLE public.attack_targets ALTER COLUMN reaction_variation DROP NOT NULL;

CREATE TABLE IF NOT EXISTS public.bookings (
    idle_point_cost smallint NOT NULL,
    slot_number smallint NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    character_art_id uuid,
    id uuid NOT NULL,
    npc_id uuid,
    technique_id uuid,
    time_skip_activity_id uuid,
    time_skip_day_id uuid NOT NULL,
    user_id uuid NOT NULL,
    rest_quality character varying(20),
    solo_activity_type character varying(20),
    training_target character varying(20),
    activity_name character varying(100),
    interaction_name character varying(100),
    npc_name character varying(100),
    description text
);
ALTER TABLE public.bookings ADD COLUMN IF NOT EXISTS idle_point_cost smallint;
ALTER TABLE public.bookings ADD COLUMN IF NOT EXISTS slot_number smallint;
ALTER TABLE public.bookings ADD COLUMN IF NOT EXISTS created_at timestamp(6) with time zone;
ALTER TABLE public.bookings ADD COLUMN IF NOT EXISTS character_art_id uuid;
ALTER TABLE public.bookings ALTER COLUMN character_art_id DROP NOT NULL;
ALTER TABLE public.bookings ADD COLUMN IF NOT EXISTS id uuid;
ALTER TABLE public.bookings ADD COLUMN IF NOT EXISTS npc_id uuid;
ALTER TABLE public.bookings ALTER COLUMN npc_id DROP NOT NULL;
ALTER TABLE public.bookings ADD COLUMN IF NOT EXISTS technique_id uuid;
ALTER TABLE public.bookings ALTER COLUMN technique_id DROP NOT NULL;
ALTER TABLE public.bookings ADD COLUMN IF NOT EXISTS time_skip_activity_id uuid;
ALTER TABLE public.bookings ALTER COLUMN time_skip_activity_id DROP NOT NULL;
ALTER TABLE public.bookings ADD COLUMN IF NOT EXISTS time_skip_day_id uuid;
ALTER TABLE public.bookings ADD COLUMN IF NOT EXISTS user_id uuid;
ALTER TABLE public.bookings ADD COLUMN IF NOT EXISTS rest_quality character varying(20);
ALTER TABLE public.bookings ALTER COLUMN rest_quality DROP NOT NULL;
ALTER TABLE public.bookings ADD COLUMN IF NOT EXISTS solo_activity_type character varying(20);
ALTER TABLE public.bookings ALTER COLUMN solo_activity_type DROP NOT NULL;
ALTER TABLE public.bookings ADD COLUMN IF NOT EXISTS training_target character varying(20);
ALTER TABLE public.bookings ALTER COLUMN training_target DROP NOT NULL;
ALTER TABLE public.bookings ADD COLUMN IF NOT EXISTS activity_name character varying(100);
ALTER TABLE public.bookings ALTER COLUMN activity_name DROP NOT NULL;
ALTER TABLE public.bookings ADD COLUMN IF NOT EXISTS interaction_name character varying(100);
ALTER TABLE public.bookings ALTER COLUMN interaction_name DROP NOT NULL;
ALTER TABLE public.bookings ADD COLUMN IF NOT EXISTS npc_name character varying(100);
ALTER TABLE public.bookings ALTER COLUMN npc_name DROP NOT NULL;
ALTER TABLE public.bookings ADD COLUMN IF NOT EXISTS description text;
ALTER TABLE public.bookings ALTER COLUMN description DROP NOT NULL;

CREATE TABLE IF NOT EXISTS public.campaign_members (
    campaign_id uuid NOT NULL,
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    role character varying(20) NOT NULL
);
ALTER TABLE public.campaign_members ADD COLUMN IF NOT EXISTS campaign_id uuid;
ALTER TABLE public.campaign_members ADD COLUMN IF NOT EXISTS id uuid;
ALTER TABLE public.campaign_members ADD COLUMN IF NOT EXISTS user_id uuid;
ALTER TABLE public.campaign_members ADD COLUMN IF NOT EXISTS role character varying(20);

CREATE TABLE IF NOT EXISTS public.campaign_npcs (
    bookable boolean,
    visible boolean NOT NULL,
    campaign_id uuid NOT NULL,
    id uuid NOT NULL,
    npc_id uuid NOT NULL
);
ALTER TABLE public.campaign_npcs ADD COLUMN IF NOT EXISTS bookable boolean;
ALTER TABLE public.campaign_npcs ALTER COLUMN bookable DROP NOT NULL;
ALTER TABLE public.campaign_npcs ADD COLUMN IF NOT EXISTS visible boolean;
ALTER TABLE public.campaign_npcs ADD COLUMN IF NOT EXISTS campaign_id uuid;
ALTER TABLE public.campaign_npcs ADD COLUMN IF NOT EXISTS id uuid;
ALTER TABLE public.campaign_npcs ADD COLUMN IF NOT EXISTS npc_id uuid;

CREATE TABLE IF NOT EXISTS public.campaign_rule_settings (
    enabled boolean NOT NULL,
    campaign_id uuid NOT NULL,
    id uuid NOT NULL,
    rule_key character varying(40) NOT NULL,
    params jsonb
);
ALTER TABLE public.campaign_rule_settings ADD COLUMN IF NOT EXISTS enabled boolean;
ALTER TABLE public.campaign_rule_settings ADD COLUMN IF NOT EXISTS campaign_id uuid;
ALTER TABLE public.campaign_rule_settings ADD COLUMN IF NOT EXISTS id uuid;
ALTER TABLE public.campaign_rule_settings ADD COLUMN IF NOT EXISTS rule_key character varying(40);
ALTER TABLE public.campaign_rule_settings ADD COLUMN IF NOT EXISTS params jsonb;
ALTER TABLE public.campaign_rule_settings ALTER COLUMN params DROP NOT NULL;

CREATE TABLE IF NOT EXISTS public.campaigns (
    created_at timestamp(6) with time zone NOT NULL,
    id uuid NOT NULL,
    master_id uuid NOT NULL,
    name character varying(100) NOT NULL,
    description text
);
ALTER TABLE public.campaigns ADD COLUMN IF NOT EXISTS created_at timestamp(6) with time zone;
ALTER TABLE public.campaigns ADD COLUMN IF NOT EXISTS id uuid;
ALTER TABLE public.campaigns ADD COLUMN IF NOT EXISTS master_id uuid;
ALTER TABLE public.campaigns ADD COLUMN IF NOT EXISTS name character varying(100);
ALTER TABLE public.campaigns ADD COLUMN IF NOT EXISTS description text;
ALTER TABLE public.campaigns ALTER COLUMN description DROP NOT NULL;

CREATE TABLE IF NOT EXISTS public.catalog_item_images (
    campaign_id uuid,
    id uuid NOT NULL,
    kind character varying(20) NOT NULL,
    item_key character varying(40) NOT NULL,
    content_type character varying(100) NOT NULL,
    thumb_content_type character varying(100) NOT NULL,
    data bytea NOT NULL,
    thumb bytea NOT NULL
);
ALTER TABLE public.catalog_item_images ADD COLUMN IF NOT EXISTS campaign_id uuid;
ALTER TABLE public.catalog_item_images ALTER COLUMN campaign_id DROP NOT NULL;
ALTER TABLE public.catalog_item_images ADD COLUMN IF NOT EXISTS id uuid;
ALTER TABLE public.catalog_item_images ADD COLUMN IF NOT EXISTS kind character varying(20);
ALTER TABLE public.catalog_item_images ADD COLUMN IF NOT EXISTS item_key character varying(40);
ALTER TABLE public.catalog_item_images ADD COLUMN IF NOT EXISTS content_type character varying(100);
ALTER TABLE public.catalog_item_images ADD COLUMN IF NOT EXISTS thumb_content_type character varying(100);
ALTER TABLE public.catalog_item_images ADD COLUMN IF NOT EXISTS data bytea;
ALTER TABLE public.catalog_item_images ADD COLUMN IF NOT EXISTS thumb bytea;

CREATE TABLE IF NOT EXISTS public.character_arts (
    graduation_points integer DEFAULT 0 NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    art_id uuid NOT NULL,
    id uuid NOT NULL,
    sheet_id uuid NOT NULL,
    category character varying(20),
    graduation character varying(20),
    status character varying(20),
    art_name character varying(100)
);
ALTER TABLE public.character_arts ADD COLUMN IF NOT EXISTS graduation_points integer DEFAULT 0 NOT NULL;
ALTER TABLE public.character_arts ADD COLUMN IF NOT EXISTS created_at timestamp(6) with time zone;
ALTER TABLE public.character_arts ADD COLUMN IF NOT EXISTS art_id uuid;
ALTER TABLE public.character_arts ADD COLUMN IF NOT EXISTS id uuid;
ALTER TABLE public.character_arts ADD COLUMN IF NOT EXISTS sheet_id uuid;
ALTER TABLE public.character_arts ADD COLUMN IF NOT EXISTS category character varying(20);
ALTER TABLE public.character_arts ALTER COLUMN category DROP NOT NULL;
ALTER TABLE public.character_arts ADD COLUMN IF NOT EXISTS graduation character varying(20);
ALTER TABLE public.character_arts ALTER COLUMN graduation DROP NOT NULL;
ALTER TABLE public.character_arts ADD COLUMN IF NOT EXISTS status character varying(20);
ALTER TABLE public.character_arts ALTER COLUMN status DROP NOT NULL;
ALTER TABLE public.character_arts ADD COLUMN IF NOT EXISTS art_name character varying(100);
ALTER TABLE public.character_arts ALTER COLUMN art_name DROP NOT NULL;

CREATE TABLE IF NOT EXISTS public.character_builds (
    updated_at timestamp(6) with time zone,
    version bigint,
    sheet_id uuid NOT NULL,
    step character varying(20),
    settings jsonb,
    state jsonb
);
ALTER TABLE public.character_builds ADD COLUMN IF NOT EXISTS updated_at timestamp(6) with time zone;
ALTER TABLE public.character_builds ALTER COLUMN updated_at DROP NOT NULL;
ALTER TABLE public.character_builds ADD COLUMN IF NOT EXISTS version bigint;
ALTER TABLE public.character_builds ALTER COLUMN version DROP NOT NULL;
ALTER TABLE public.character_builds ADD COLUMN IF NOT EXISTS sheet_id uuid;
ALTER TABLE public.character_builds ADD COLUMN IF NOT EXISTS step character varying(20);
ALTER TABLE public.character_builds ALTER COLUMN step DROP NOT NULL;
ALTER TABLE public.character_builds ADD COLUMN IF NOT EXISTS settings jsonb;
ALTER TABLE public.character_builds ALTER COLUMN settings DROP NOT NULL;
ALTER TABLE public.character_builds ADD COLUMN IF NOT EXISTS state jsonb;
ALTER TABLE public.character_builds ALTER COLUMN state DROP NOT NULL;

CREATE TABLE IF NOT EXISTS public.character_clans (
    clan_version integer,
    acquired_at timestamp(6) with time zone NOT NULL,
    clan_id uuid,
    id uuid NOT NULL,
    sheet_id uuid NOT NULL,
    name character varying(255),
    build_gains jsonb
);
ALTER TABLE public.character_clans ADD COLUMN IF NOT EXISTS clan_version integer;
ALTER TABLE public.character_clans ALTER COLUMN clan_version DROP NOT NULL;
ALTER TABLE public.character_clans ADD COLUMN IF NOT EXISTS acquired_at timestamp(6) with time zone;
ALTER TABLE public.character_clans ADD COLUMN IF NOT EXISTS clan_id uuid;
ALTER TABLE public.character_clans ALTER COLUMN clan_id DROP NOT NULL;
ALTER TABLE public.character_clans ADD COLUMN IF NOT EXISTS id uuid;
ALTER TABLE public.character_clans ADD COLUMN IF NOT EXISTS sheet_id uuid;
ALTER TABLE public.character_clans ADD COLUMN IF NOT EXISTS name character varying(255);
ALTER TABLE public.character_clans ALTER COLUMN name DROP NOT NULL;
ALTER TABLE public.character_clans ADD COLUMN IF NOT EXISTS build_gains jsonb;
ALTER TABLE public.character_clans ALTER COLUMN build_gains DROP NOT NULL;

CREATE TABLE IF NOT EXISTS public.character_instincts (
    created_at timestamp(6) with time zone NOT NULL,
    id uuid NOT NULL,
    instinct_id uuid NOT NULL,
    sheet_id uuid NOT NULL,
    rank character varying(20),
    name character varying(100),
    description text
);
ALTER TABLE public.character_instincts ADD COLUMN IF NOT EXISTS created_at timestamp(6) with time zone;
ALTER TABLE public.character_instincts ADD COLUMN IF NOT EXISTS id uuid;
ALTER TABLE public.character_instincts ADD COLUMN IF NOT EXISTS instinct_id uuid;
ALTER TABLE public.character_instincts ADD COLUMN IF NOT EXISTS sheet_id uuid;
ALTER TABLE public.character_instincts ADD COLUMN IF NOT EXISTS rank character varying(20);
ALTER TABLE public.character_instincts ALTER COLUMN rank DROP NOT NULL;
ALTER TABLE public.character_instincts ADD COLUMN IF NOT EXISTS name character varying(100);
ALTER TABLE public.character_instincts ALTER COLUMN name DROP NOT NULL;
ALTER TABLE public.character_instincts ADD COLUMN IF NOT EXISTS description text;
ALTER TABLE public.character_instincts ALTER COLUMN description DROP NOT NULL;

CREATE TABLE IF NOT EXISTS public.character_sheet_visibility (
    hidden_by_master boolean NOT NULL,
    hidden_by_self boolean NOT NULL,
    campaign_member_id uuid NOT NULL,
    id uuid NOT NULL,
    section character varying(50) NOT NULL
);
ALTER TABLE public.character_sheet_visibility ADD COLUMN IF NOT EXISTS hidden_by_master boolean;
ALTER TABLE public.character_sheet_visibility ADD COLUMN IF NOT EXISTS hidden_by_self boolean;
ALTER TABLE public.character_sheet_visibility ADD COLUMN IF NOT EXISTS campaign_member_id uuid;
ALTER TABLE public.character_sheet_visibility ADD COLUMN IF NOT EXISTS id uuid;
ALTER TABLE public.character_sheet_visibility ADD COLUMN IF NOT EXISTS section character varying(50);

CREATE TABLE IF NOT EXISTS public.character_sheets (
    barrier_overcome_points integer DEFAULT 0 NOT NULL,
    cha integer NOT NULL,
    combat_ready boolean DEFAULT true NOT NULL,
    con integer NOT NULL,
    current_hp integer NOT NULL,
    current_kp integer NOT NULL,
    current_sp integer NOT NULL,
    dex integer NOT NULL,
    fight_experience integer NOT NULL,
    height_m double precision,
    hp_max integer NOT NULL,
    intl integer,
    knockout_sequel boolean DEFAULT false NOT NULL,
    kp_max integer NOT NULL,
    men integer NOT NULL,
    mental_fatigue integer NOT NULL,
    money integer NOT NULL,
    npc_tracking boolean DEFAULT false NOT NULL,
    open_wounds integer NOT NULL,
    pending_overcome_points integer NOT NULL,
    physical_fatigue integer NOT NULL,
    sp_max integer NOT NULL,
    sparring_experience integer DEFAULT 0 NOT NULL,
    str integer NOT NULL,
    tal integer NOT NULL,
    weight_kg double precision,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    version bigint,
    campaign_member_id uuid,
    id uuid NOT NULL,
    npc_id uuid,
    npc_owner_id uuid,
    body_type character varying(20),
    build_status character varying(20),
    dominance character varying(20),
    name character varying(100),
    doses jsonb DEFAULT '[]'::jsonb,
    limiter_adjustments jsonb,
    limiter_carry jsonb,
    missing_parts jsonb DEFAULT '[]'::jsonb
);
ALTER TABLE public.character_sheets ADD COLUMN IF NOT EXISTS barrier_overcome_points integer DEFAULT 0 NOT NULL;
ALTER TABLE public.character_sheets ADD COLUMN IF NOT EXISTS cha integer;
ALTER TABLE public.character_sheets ADD COLUMN IF NOT EXISTS combat_ready boolean DEFAULT true NOT NULL;
ALTER TABLE public.character_sheets ADD COLUMN IF NOT EXISTS con integer;
ALTER TABLE public.character_sheets ADD COLUMN IF NOT EXISTS current_hp integer;
ALTER TABLE public.character_sheets ADD COLUMN IF NOT EXISTS current_kp integer;
ALTER TABLE public.character_sheets ADD COLUMN IF NOT EXISTS current_sp integer;
ALTER TABLE public.character_sheets ADD COLUMN IF NOT EXISTS dex integer;
ALTER TABLE public.character_sheets ADD COLUMN IF NOT EXISTS fight_experience integer;
ALTER TABLE public.character_sheets ADD COLUMN IF NOT EXISTS height_m double precision;
ALTER TABLE public.character_sheets ALTER COLUMN height_m DROP NOT NULL;
ALTER TABLE public.character_sheets ADD COLUMN IF NOT EXISTS hp_max integer;
ALTER TABLE public.character_sheets ADD COLUMN IF NOT EXISTS intl integer;
ALTER TABLE public.character_sheets ALTER COLUMN intl DROP NOT NULL;
ALTER TABLE public.character_sheets ADD COLUMN IF NOT EXISTS knockout_sequel boolean DEFAULT false NOT NULL;
ALTER TABLE public.character_sheets ADD COLUMN IF NOT EXISTS kp_max integer;
ALTER TABLE public.character_sheets ADD COLUMN IF NOT EXISTS men integer;
ALTER TABLE public.character_sheets ADD COLUMN IF NOT EXISTS mental_fatigue integer;
ALTER TABLE public.character_sheets ADD COLUMN IF NOT EXISTS money integer;
ALTER TABLE public.character_sheets ADD COLUMN IF NOT EXISTS npc_tracking boolean DEFAULT false NOT NULL;
ALTER TABLE public.character_sheets ADD COLUMN IF NOT EXISTS open_wounds integer;
ALTER TABLE public.character_sheets ADD COLUMN IF NOT EXISTS pending_overcome_points integer;
ALTER TABLE public.character_sheets ADD COLUMN IF NOT EXISTS physical_fatigue integer;
ALTER TABLE public.character_sheets ADD COLUMN IF NOT EXISTS sp_max integer;
ALTER TABLE public.character_sheets ADD COLUMN IF NOT EXISTS sparring_experience integer DEFAULT 0 NOT NULL;
ALTER TABLE public.character_sheets ADD COLUMN IF NOT EXISTS str integer;
ALTER TABLE public.character_sheets ADD COLUMN IF NOT EXISTS tal integer;
ALTER TABLE public.character_sheets ADD COLUMN IF NOT EXISTS weight_kg double precision;
ALTER TABLE public.character_sheets ALTER COLUMN weight_kg DROP NOT NULL;
ALTER TABLE public.character_sheets ADD COLUMN IF NOT EXISTS created_at timestamp(6) with time zone;
ALTER TABLE public.character_sheets ADD COLUMN IF NOT EXISTS updated_at timestamp(6) with time zone;
ALTER TABLE public.character_sheets ADD COLUMN IF NOT EXISTS version bigint;
ALTER TABLE public.character_sheets ALTER COLUMN version DROP NOT NULL;
ALTER TABLE public.character_sheets ADD COLUMN IF NOT EXISTS campaign_member_id uuid;
ALTER TABLE public.character_sheets ALTER COLUMN campaign_member_id DROP NOT NULL;
ALTER TABLE public.character_sheets ADD COLUMN IF NOT EXISTS id uuid;
ALTER TABLE public.character_sheets ADD COLUMN IF NOT EXISTS npc_id uuid;
ALTER TABLE public.character_sheets ALTER COLUMN npc_id DROP NOT NULL;
ALTER TABLE public.character_sheets ADD COLUMN IF NOT EXISTS npc_owner_id uuid;
ALTER TABLE public.character_sheets ALTER COLUMN npc_owner_id DROP NOT NULL;
ALTER TABLE public.character_sheets ADD COLUMN IF NOT EXISTS body_type character varying(20);
ALTER TABLE public.character_sheets ALTER COLUMN body_type DROP NOT NULL;
ALTER TABLE public.character_sheets ADD COLUMN IF NOT EXISTS build_status character varying(20);
ALTER TABLE public.character_sheets ALTER COLUMN build_status DROP NOT NULL;
ALTER TABLE public.character_sheets ADD COLUMN IF NOT EXISTS dominance character varying(20);
ALTER TABLE public.character_sheets ALTER COLUMN dominance DROP NOT NULL;
ALTER TABLE public.character_sheets ADD COLUMN IF NOT EXISTS name character varying(100);
ALTER TABLE public.character_sheets ALTER COLUMN name DROP NOT NULL;
ALTER TABLE public.character_sheets ADD COLUMN IF NOT EXISTS doses jsonb DEFAULT '[]'::jsonb;
ALTER TABLE public.character_sheets ALTER COLUMN doses DROP NOT NULL;
ALTER TABLE public.character_sheets ADD COLUMN IF NOT EXISTS limiter_adjustments jsonb;
ALTER TABLE public.character_sheets ALTER COLUMN limiter_adjustments DROP NOT NULL;
ALTER TABLE public.character_sheets ADD COLUMN IF NOT EXISTS limiter_carry jsonb;
ALTER TABLE public.character_sheets ALTER COLUMN limiter_carry DROP NOT NULL;
ALTER TABLE public.character_sheets ADD COLUMN IF NOT EXISTS missing_parts jsonb DEFAULT '[]'::jsonb;
ALTER TABLE public.character_sheets ALTER COLUMN missing_parts DROP NOT NULL;

CREATE TABLE IF NOT EXISTS public.character_techniques (
    complexity integer NOT NULL,
    mastery_points integer,
    training_points integer,
    training_progress integer,
    created_at timestamp(6) with time zone NOT NULL,
    character_art_id uuid,
    id uuid NOT NULL,
    sheet_id uuid NOT NULL,
    graduation character varying(20),
    kind character varying(20),
    mastery character varying(20),
    state character varying(20),
    borrowed_from character varying(100),
    name character varying(100),
    taught_by character varying(100),
    base_assembly jsonb,
    bonuses jsonb,
    technique_assembly jsonb
);
ALTER TABLE public.character_techniques ADD COLUMN IF NOT EXISTS complexity integer;
ALTER TABLE public.character_techniques ADD COLUMN IF NOT EXISTS mastery_points integer;
ALTER TABLE public.character_techniques ALTER COLUMN mastery_points DROP NOT NULL;
ALTER TABLE public.character_techniques ADD COLUMN IF NOT EXISTS training_points integer;
ALTER TABLE public.character_techniques ALTER COLUMN training_points DROP NOT NULL;
ALTER TABLE public.character_techniques ADD COLUMN IF NOT EXISTS training_progress integer;
ALTER TABLE public.character_techniques ALTER COLUMN training_progress DROP NOT NULL;
ALTER TABLE public.character_techniques ADD COLUMN IF NOT EXISTS created_at timestamp(6) with time zone;
ALTER TABLE public.character_techniques ADD COLUMN IF NOT EXISTS character_art_id uuid;
ALTER TABLE public.character_techniques ALTER COLUMN character_art_id DROP NOT NULL;
ALTER TABLE public.character_techniques ADD COLUMN IF NOT EXISTS id uuid;
ALTER TABLE public.character_techniques ADD COLUMN IF NOT EXISTS sheet_id uuid;
ALTER TABLE public.character_techniques ADD COLUMN IF NOT EXISTS graduation character varying(20);
ALTER TABLE public.character_techniques ALTER COLUMN graduation DROP NOT NULL;
ALTER TABLE public.character_techniques ADD COLUMN IF NOT EXISTS kind character varying(20);
ALTER TABLE public.character_techniques ALTER COLUMN kind DROP NOT NULL;
ALTER TABLE public.character_techniques ADD COLUMN IF NOT EXISTS mastery character varying(20);
ALTER TABLE public.character_techniques ALTER COLUMN mastery DROP NOT NULL;
ALTER TABLE public.character_techniques ADD COLUMN IF NOT EXISTS state character varying(20);
ALTER TABLE public.character_techniques ALTER COLUMN state DROP NOT NULL;
ALTER TABLE public.character_techniques ADD COLUMN IF NOT EXISTS borrowed_from character varying(100);
ALTER TABLE public.character_techniques ALTER COLUMN borrowed_from DROP NOT NULL;
ALTER TABLE public.character_techniques ADD COLUMN IF NOT EXISTS name character varying(100);
ALTER TABLE public.character_techniques ALTER COLUMN name DROP NOT NULL;
ALTER TABLE public.character_techniques ADD COLUMN IF NOT EXISTS taught_by character varying(100);
ALTER TABLE public.character_techniques ALTER COLUMN taught_by DROP NOT NULL;
ALTER TABLE public.character_techniques ADD COLUMN IF NOT EXISTS base_assembly jsonb;
ALTER TABLE public.character_techniques ALTER COLUMN base_assembly DROP NOT NULL;
ALTER TABLE public.character_techniques ADD COLUMN IF NOT EXISTS bonuses jsonb;
ALTER TABLE public.character_techniques ALTER COLUMN bonuses DROP NOT NULL;
ALTER TABLE public.character_techniques ADD COLUMN IF NOT EXISTS technique_assembly jsonb;
ALTER TABLE public.character_techniques ALTER COLUMN technique_assembly DROP NOT NULL;

CREATE TABLE IF NOT EXISTS public.character_traits (
    gains_applied boolean,
    trait_version integer,
    acquired_at timestamp(6) with time zone NOT NULL,
    id uuid NOT NULL,
    sheet_id uuid NOT NULL,
    trait_id uuid,
    acquired_in character varying(20),
    category character varying(20),
    rarity character varying(20),
    description text,
    name character varying(255),
    build_gains jsonb
);
ALTER TABLE public.character_traits ADD COLUMN IF NOT EXISTS gains_applied boolean;
ALTER TABLE public.character_traits ALTER COLUMN gains_applied DROP NOT NULL;
ALTER TABLE public.character_traits ADD COLUMN IF NOT EXISTS trait_version integer;
ALTER TABLE public.character_traits ALTER COLUMN trait_version DROP NOT NULL;
ALTER TABLE public.character_traits ADD COLUMN IF NOT EXISTS acquired_at timestamp(6) with time zone;
ALTER TABLE public.character_traits ADD COLUMN IF NOT EXISTS id uuid;
ALTER TABLE public.character_traits ADD COLUMN IF NOT EXISTS sheet_id uuid;
ALTER TABLE public.character_traits ADD COLUMN IF NOT EXISTS trait_id uuid;
ALTER TABLE public.character_traits ALTER COLUMN trait_id DROP NOT NULL;
ALTER TABLE public.character_traits ADD COLUMN IF NOT EXISTS acquired_in character varying(20);
ALTER TABLE public.character_traits ALTER COLUMN acquired_in DROP NOT NULL;
ALTER TABLE public.character_traits ADD COLUMN IF NOT EXISTS category character varying(20);
ALTER TABLE public.character_traits ALTER COLUMN category DROP NOT NULL;
ALTER TABLE public.character_traits ADD COLUMN IF NOT EXISTS rarity character varying(20);
ALTER TABLE public.character_traits ALTER COLUMN rarity DROP NOT NULL;
ALTER TABLE public.character_traits ADD COLUMN IF NOT EXISTS description text;
ALTER TABLE public.character_traits ALTER COLUMN description DROP NOT NULL;
ALTER TABLE public.character_traits ADD COLUMN IF NOT EXISTS name character varying(255);
ALTER TABLE public.character_traits ALTER COLUMN name DROP NOT NULL;
ALTER TABLE public.character_traits ADD COLUMN IF NOT EXISTS build_gains jsonb;
ALTER TABLE public.character_traits ALTER COLUMN build_gains DROP NOT NULL;

CREATE TABLE IF NOT EXISTS public.combat_conditions (
    applied_turn integer NOT NULL,
    intensity integer,
    progress integer,
    turns_remaining integer,
    id uuid NOT NULL,
    participant_id uuid NOT NULL,
    source_participant_id uuid,
    expires_at character varying(30) NOT NULL,
    condition_key character varying(40) NOT NULL,
    note character varying(200)
);
ALTER TABLE public.combat_conditions ADD COLUMN IF NOT EXISTS applied_turn integer;
ALTER TABLE public.combat_conditions ADD COLUMN IF NOT EXISTS intensity integer;
ALTER TABLE public.combat_conditions ALTER COLUMN intensity DROP NOT NULL;
ALTER TABLE public.combat_conditions ADD COLUMN IF NOT EXISTS progress integer;
ALTER TABLE public.combat_conditions ALTER COLUMN progress DROP NOT NULL;
ALTER TABLE public.combat_conditions ADD COLUMN IF NOT EXISTS turns_remaining integer;
ALTER TABLE public.combat_conditions ALTER COLUMN turns_remaining DROP NOT NULL;
ALTER TABLE public.combat_conditions ADD COLUMN IF NOT EXISTS id uuid;
ALTER TABLE public.combat_conditions ADD COLUMN IF NOT EXISTS participant_id uuid;
ALTER TABLE public.combat_conditions ADD COLUMN IF NOT EXISTS source_participant_id uuid;
ALTER TABLE public.combat_conditions ALTER COLUMN source_participant_id DROP NOT NULL;
ALTER TABLE public.combat_conditions ADD COLUMN IF NOT EXISTS expires_at character varying(30);
ALTER TABLE public.combat_conditions ADD COLUMN IF NOT EXISTS condition_key character varying(40);
ALTER TABLE public.combat_conditions ADD COLUMN IF NOT EXISTS note character varying(200);
ALTER TABLE public.combat_conditions ALTER COLUMN note DROP NOT NULL;

CREATE TABLE IF NOT EXISTS public.combat_counters (
    counter_value integer NOT NULL,
    id uuid NOT NULL,
    participant_id uuid NOT NULL,
    target_participant_id uuid,
    counter_key character varying(40) NOT NULL
);
ALTER TABLE public.combat_counters ADD COLUMN IF NOT EXISTS counter_value integer;
ALTER TABLE public.combat_counters ADD COLUMN IF NOT EXISTS id uuid;
ALTER TABLE public.combat_counters ADD COLUMN IF NOT EXISTS participant_id uuid;
ALTER TABLE public.combat_counters ADD COLUMN IF NOT EXISTS target_participant_id uuid;
ALTER TABLE public.combat_counters ALTER COLUMN target_participant_id DROP NOT NULL;
ALTER TABLE public.combat_counters ADD COLUMN IF NOT EXISTS counter_key character varying(40);

CREATE TABLE IF NOT EXISTS public.combat_events (
    round integer NOT NULL,
    turn_counter integer NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    encounter_id uuid NOT NULL,
    id uuid NOT NULL,
    participant_id uuid,
    type character varying(30) NOT NULL,
    text character varying(500) NOT NULL
);
ALTER TABLE public.combat_events ADD COLUMN IF NOT EXISTS round integer;
ALTER TABLE public.combat_events ADD COLUMN IF NOT EXISTS turn_counter integer;
ALTER TABLE public.combat_events ADD COLUMN IF NOT EXISTS created_at timestamp(6) with time zone;
ALTER TABLE public.combat_events ADD COLUMN IF NOT EXISTS encounter_id uuid;
ALTER TABLE public.combat_events ADD COLUMN IF NOT EXISTS id uuid;
ALTER TABLE public.combat_events ADD COLUMN IF NOT EXISTS participant_id uuid;
ALTER TABLE public.combat_events ALTER COLUMN participant_id DROP NOT NULL;
ALTER TABLE public.combat_events ADD COLUMN IF NOT EXISTS type character varying(30);
ALTER TABLE public.combat_events ADD COLUMN IF NOT EXISTS text character varying(500);

CREATE TABLE IF NOT EXISTS public.encounter_participants (
    adrenaline integer DEFAULT 0 NOT NULL,
    damage_this_turn integer DEFAULT 0 NOT NULL,
    death_failures integer DEFAULT 0 NOT NULL,
    death_successes integer DEFAULT 0 NOT NULL,
    engaged_this_round boolean DEFAULT false NOT NULL,
    exhaustion_nd integer,
    fluid_switch_used boolean DEFAULT false NOT NULL,
    guard_changes_this_turn integer DEFAULT 0 NOT NULL,
    initiative integer,
    kp_zeroings integer DEFAULT 0 NOT NULL,
    moves_this_turn integer NOT NULL,
    npc boolean NOT NULL,
    order_index integer NOT NULL,
    pa_bonus_next integer NOT NULL,
    pa_current integer NOT NULL,
    pa_loss_next integer NOT NULL,
    readiness_used boolean DEFAULT false NOT NULL,
    rounds_at_apex integer DEFAULT 0 NOT NULL,
    save_next_used boolean DEFAULT false NOT NULL,
    stabilize_failures integer DEFAULT 0 NOT NULL,
    team integer DEFAULT 1 NOT NULL,
    trained_reload_used boolean DEFAULT false NOT NULL,
    turn_started_at integer,
    guard_changed_at timestamp(6) with time zone,
    guard character varying(10) DEFAULT 'NATURAL'::character varying NOT NULL,
    active_base_id uuid,
    encounter_id uuid NOT NULL,
    id uuid NOT NULL,
    main_art_id uuid,
    sheet_id uuid NOT NULL,
    classification character varying(20) DEFAULT 'REAL'::character varying NOT NULL,
    hysteria character varying(20),
    name character varying(100) NOT NULL,
    attackers_this_round jsonb
);
ALTER TABLE public.encounter_participants ADD COLUMN IF NOT EXISTS adrenaline integer DEFAULT 0 NOT NULL;
ALTER TABLE public.encounter_participants ADD COLUMN IF NOT EXISTS damage_this_turn integer DEFAULT 0 NOT NULL;
ALTER TABLE public.encounter_participants ADD COLUMN IF NOT EXISTS death_failures integer DEFAULT 0 NOT NULL;
ALTER TABLE public.encounter_participants ADD COLUMN IF NOT EXISTS death_successes integer DEFAULT 0 NOT NULL;
ALTER TABLE public.encounter_participants ADD COLUMN IF NOT EXISTS engaged_this_round boolean DEFAULT false NOT NULL;
ALTER TABLE public.encounter_participants ADD COLUMN IF NOT EXISTS exhaustion_nd integer;
ALTER TABLE public.encounter_participants ALTER COLUMN exhaustion_nd DROP NOT NULL;
ALTER TABLE public.encounter_participants ADD COLUMN IF NOT EXISTS fluid_switch_used boolean DEFAULT false NOT NULL;
ALTER TABLE public.encounter_participants ADD COLUMN IF NOT EXISTS guard_changes_this_turn integer DEFAULT 0 NOT NULL;
ALTER TABLE public.encounter_participants ADD COLUMN IF NOT EXISTS initiative integer;
ALTER TABLE public.encounter_participants ALTER COLUMN initiative DROP NOT NULL;
ALTER TABLE public.encounter_participants ADD COLUMN IF NOT EXISTS kp_zeroings integer DEFAULT 0 NOT NULL;
ALTER TABLE public.encounter_participants ADD COLUMN IF NOT EXISTS moves_this_turn integer;
ALTER TABLE public.encounter_participants ADD COLUMN IF NOT EXISTS npc boolean;
ALTER TABLE public.encounter_participants ADD COLUMN IF NOT EXISTS order_index integer;
ALTER TABLE public.encounter_participants ADD COLUMN IF NOT EXISTS pa_bonus_next integer;
ALTER TABLE public.encounter_participants ADD COLUMN IF NOT EXISTS pa_current integer;
ALTER TABLE public.encounter_participants ADD COLUMN IF NOT EXISTS pa_loss_next integer;
ALTER TABLE public.encounter_participants ADD COLUMN IF NOT EXISTS readiness_used boolean DEFAULT false NOT NULL;
ALTER TABLE public.encounter_participants ADD COLUMN IF NOT EXISTS rounds_at_apex integer DEFAULT 0 NOT NULL;
ALTER TABLE public.encounter_participants ADD COLUMN IF NOT EXISTS save_next_used boolean DEFAULT false NOT NULL;
ALTER TABLE public.encounter_participants ADD COLUMN IF NOT EXISTS stabilize_failures integer DEFAULT 0 NOT NULL;
ALTER TABLE public.encounter_participants ADD COLUMN IF NOT EXISTS team integer DEFAULT 1 NOT NULL;
ALTER TABLE public.encounter_participants ADD COLUMN IF NOT EXISTS trained_reload_used boolean DEFAULT false NOT NULL;
ALTER TABLE public.encounter_participants ADD COLUMN IF NOT EXISTS turn_started_at integer;
ALTER TABLE public.encounter_participants ALTER COLUMN turn_started_at DROP NOT NULL;
ALTER TABLE public.encounter_participants ADD COLUMN IF NOT EXISTS guard_changed_at timestamp(6) with time zone;
ALTER TABLE public.encounter_participants ALTER COLUMN guard_changed_at DROP NOT NULL;
ALTER TABLE public.encounter_participants ADD COLUMN IF NOT EXISTS guard character varying(10) DEFAULT 'NATURAL'::character varying NOT NULL;
ALTER TABLE public.encounter_participants ADD COLUMN IF NOT EXISTS active_base_id uuid;
ALTER TABLE public.encounter_participants ALTER COLUMN active_base_id DROP NOT NULL;
ALTER TABLE public.encounter_participants ADD COLUMN IF NOT EXISTS encounter_id uuid;
ALTER TABLE public.encounter_participants ADD COLUMN IF NOT EXISTS id uuid;
ALTER TABLE public.encounter_participants ADD COLUMN IF NOT EXISTS main_art_id uuid;
ALTER TABLE public.encounter_participants ALTER COLUMN main_art_id DROP NOT NULL;
ALTER TABLE public.encounter_participants ADD COLUMN IF NOT EXISTS sheet_id uuid;
ALTER TABLE public.encounter_participants ADD COLUMN IF NOT EXISTS classification character varying(20) DEFAULT 'REAL'::character varying NOT NULL;
ALTER TABLE public.encounter_participants ADD COLUMN IF NOT EXISTS hysteria character varying(20);
ALTER TABLE public.encounter_participants ALTER COLUMN hysteria DROP NOT NULL;
ALTER TABLE public.encounter_participants ADD COLUMN IF NOT EXISTS name character varying(100);
ALTER TABLE public.encounter_participants ADD COLUMN IF NOT EXISTS attackers_this_round jsonb;
ALTER TABLE public.encounter_participants ALTER COLUMN attackers_this_round DROP NOT NULL;

CREATE TABLE IF NOT EXISTS public.encounters (
    current_index integer NOT NULL,
    round integer NOT NULL,
    turn_counter integer NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    ended_at timestamp(6) with time zone,
    floor character varying(10) DEFAULT 'STANDARD'::character varying NOT NULL,
    campaign_id uuid NOT NULL,
    id uuid NOT NULL,
    default_classification character varying(20) DEFAULT 'REAL'::character varying NOT NULL,
    status character varying(20) NOT NULL,
    name character varying(100) NOT NULL
);
ALTER TABLE public.encounters ADD COLUMN IF NOT EXISTS current_index integer;
ALTER TABLE public.encounters ADD COLUMN IF NOT EXISTS round integer;
ALTER TABLE public.encounters ADD COLUMN IF NOT EXISTS turn_counter integer;
ALTER TABLE public.encounters ADD COLUMN IF NOT EXISTS created_at timestamp(6) with time zone;
ALTER TABLE public.encounters ADD COLUMN IF NOT EXISTS ended_at timestamp(6) with time zone;
ALTER TABLE public.encounters ALTER COLUMN ended_at DROP NOT NULL;
ALTER TABLE public.encounters ADD COLUMN IF NOT EXISTS floor character varying(10) DEFAULT 'STANDARD'::character varying NOT NULL;
ALTER TABLE public.encounters ADD COLUMN IF NOT EXISTS campaign_id uuid;
ALTER TABLE public.encounters ADD COLUMN IF NOT EXISTS id uuid;
ALTER TABLE public.encounters ADD COLUMN IF NOT EXISTS default_classification character varying(20) DEFAULT 'REAL'::character varying NOT NULL;
ALTER TABLE public.encounters ADD COLUMN IF NOT EXISTS status character varying(20);
ALTER TABLE public.encounters ADD COLUMN IF NOT EXISTS name character varying(100);

CREATE TABLE IF NOT EXISTS public.fight_records (
    day_number smallint,
    el_gained integer,
    fight_date date NOT NULL,
    ps_gained integer,
    created_at timestamp(6) with time zone NOT NULL,
    campaign_id uuid NOT NULL,
    fight_id uuid NOT NULL,
    id uuid NOT NULL,
    sheet_id uuid NOT NULL,
    time_skip_id uuid,
    classification character varying(20) NOT NULL,
    note character varying(500),
    art_el jsonb,
    milestones jsonb
);
ALTER TABLE public.fight_records ADD COLUMN IF NOT EXISTS day_number smallint;
ALTER TABLE public.fight_records ALTER COLUMN day_number DROP NOT NULL;
ALTER TABLE public.fight_records ADD COLUMN IF NOT EXISTS el_gained integer;
ALTER TABLE public.fight_records ALTER COLUMN el_gained DROP NOT NULL;
ALTER TABLE public.fight_records ADD COLUMN IF NOT EXISTS fight_date date;
ALTER TABLE public.fight_records ADD COLUMN IF NOT EXISTS ps_gained integer;
ALTER TABLE public.fight_records ALTER COLUMN ps_gained DROP NOT NULL;
ALTER TABLE public.fight_records ADD COLUMN IF NOT EXISTS created_at timestamp(6) with time zone;
ALTER TABLE public.fight_records ADD COLUMN IF NOT EXISTS campaign_id uuid;
ALTER TABLE public.fight_records ADD COLUMN IF NOT EXISTS fight_id uuid;
ALTER TABLE public.fight_records ADD COLUMN IF NOT EXISTS id uuid;
ALTER TABLE public.fight_records ADD COLUMN IF NOT EXISTS sheet_id uuid;
ALTER TABLE public.fight_records ADD COLUMN IF NOT EXISTS time_skip_id uuid;
ALTER TABLE public.fight_records ALTER COLUMN time_skip_id DROP NOT NULL;
ALTER TABLE public.fight_records ADD COLUMN IF NOT EXISTS classification character varying(20);
ALTER TABLE public.fight_records ADD COLUMN IF NOT EXISTS note character varying(500);
ALTER TABLE public.fight_records ALTER COLUMN note DROP NOT NULL;
ALTER TABLE public.fight_records ADD COLUMN IF NOT EXISTS art_el jsonb;
ALTER TABLE public.fight_records ALTER COLUMN art_el DROP NOT NULL;
ALTER TABLE public.fight_records ADD COLUMN IF NOT EXISTS milestones jsonb;
ALTER TABLE public.fight_records ALTER COLUMN milestones DROP NOT NULL;

CREATE TABLE IF NOT EXISTS public.forges (
    accumulated_points integer,
    required_points integer,
    completed_at timestamp(6) with time zone,
    started_at timestamp(6) with time zone NOT NULL,
    draft_id uuid NOT NULL,
    id uuid NOT NULL,
    sheet_id uuid NOT NULL,
    name character varying(100)
);
ALTER TABLE public.forges ADD COLUMN IF NOT EXISTS accumulated_points integer;
ALTER TABLE public.forges ALTER COLUMN accumulated_points DROP NOT NULL;
ALTER TABLE public.forges ADD COLUMN IF NOT EXISTS required_points integer;
ALTER TABLE public.forges ALTER COLUMN required_points DROP NOT NULL;
ALTER TABLE public.forges ADD COLUMN IF NOT EXISTS completed_at timestamp(6) with time zone;
ALTER TABLE public.forges ALTER COLUMN completed_at DROP NOT NULL;
ALTER TABLE public.forges ADD COLUMN IF NOT EXISTS started_at timestamp(6) with time zone;
ALTER TABLE public.forges ADD COLUMN IF NOT EXISTS draft_id uuid;
ALTER TABLE public.forges ADD COLUMN IF NOT EXISTS id uuid;
ALTER TABLE public.forges ADD COLUMN IF NOT EXISTS sheet_id uuid;
ALTER TABLE public.forges ADD COLUMN IF NOT EXISTS name character varying(100);
ALTER TABLE public.forges ALTER COLUMN name DROP NOT NULL;

CREATE TABLE IF NOT EXISTS public.interaction_logs (
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone,
    booking_id uuid,
    campaign_id uuid NOT NULL,
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    review_note character varying(500),
    narrative text NOT NULL,
    status character varying(12) DEFAULT 'PUBLISHED'::character varying NOT NULL
);
ALTER TABLE public.interaction_logs ADD COLUMN IF NOT EXISTS created_at timestamp(6) with time zone;
ALTER TABLE public.interaction_logs ADD COLUMN IF NOT EXISTS updated_at timestamp(6) with time zone;
ALTER TABLE public.interaction_logs ALTER COLUMN updated_at DROP NOT NULL;
ALTER TABLE public.interaction_logs ADD COLUMN IF NOT EXISTS booking_id uuid;
ALTER TABLE public.interaction_logs ALTER COLUMN booking_id DROP NOT NULL;
ALTER TABLE public.interaction_logs ADD COLUMN IF NOT EXISTS campaign_id uuid;
ALTER TABLE public.interaction_logs ADD COLUMN IF NOT EXISTS id uuid;
ALTER TABLE public.interaction_logs ADD COLUMN IF NOT EXISTS user_id uuid;
ALTER TABLE public.interaction_logs ADD COLUMN IF NOT EXISTS review_note character varying(500);
ALTER TABLE public.interaction_logs ALTER COLUMN review_note DROP NOT NULL;
ALTER TABLE public.interaction_logs ADD COLUMN IF NOT EXISTS narrative text;
ALTER TABLE public.interaction_logs ADD COLUMN IF NOT EXISTS status character varying(12) DEFAULT 'PUBLISHED'::character varying NOT NULL;

CREATE TABLE IF NOT EXISTS public.inventory_items (
    broken boolean DEFAULT false NOT NULL,
    cocked boolean DEFAULT false NOT NULL,
    damaged boolean DEFAULT false NOT NULL,
    jammed boolean DEFAULT false NOT NULL,
    loaded integer DEFAULT 0 NOT NULL,
    quantity integer NOT NULL,
    two_handed boolean DEFAULT false NOT NULL,
    weight_kg double precision DEFAULT 0 NOT NULL,
    location character varying(10) DEFAULT 'PACK'::character varying NOT NULL,
    container_id uuid,
    id uuid NOT NULL,
    sheet_id uuid NOT NULL,
    consumable_key character varying(40),
    equipment_key character varying(40),
    weapon_key character varying(40),
    name character varying(100) NOT NULL,
    description text
);
ALTER TABLE public.inventory_items ADD COLUMN IF NOT EXISTS broken boolean DEFAULT false NOT NULL;
ALTER TABLE public.inventory_items ADD COLUMN IF NOT EXISTS cocked boolean DEFAULT false NOT NULL;
ALTER TABLE public.inventory_items ADD COLUMN IF NOT EXISTS damaged boolean DEFAULT false NOT NULL;
ALTER TABLE public.inventory_items ADD COLUMN IF NOT EXISTS jammed boolean DEFAULT false NOT NULL;
ALTER TABLE public.inventory_items ADD COLUMN IF NOT EXISTS loaded integer DEFAULT 0 NOT NULL;
ALTER TABLE public.inventory_items ADD COLUMN IF NOT EXISTS quantity integer;
ALTER TABLE public.inventory_items ADD COLUMN IF NOT EXISTS two_handed boolean DEFAULT false NOT NULL;
ALTER TABLE public.inventory_items ADD COLUMN IF NOT EXISTS weight_kg double precision DEFAULT 0 NOT NULL;
ALTER TABLE public.inventory_items ADD COLUMN IF NOT EXISTS location character varying(10) DEFAULT 'PACK'::character varying NOT NULL;
ALTER TABLE public.inventory_items ADD COLUMN IF NOT EXISTS container_id uuid;
ALTER TABLE public.inventory_items ALTER COLUMN container_id DROP NOT NULL;
ALTER TABLE public.inventory_items ADD COLUMN IF NOT EXISTS id uuid;
ALTER TABLE public.inventory_items ADD COLUMN IF NOT EXISTS sheet_id uuid;
ALTER TABLE public.inventory_items ADD COLUMN IF NOT EXISTS consumable_key character varying(40);
ALTER TABLE public.inventory_items ALTER COLUMN consumable_key DROP NOT NULL;
ALTER TABLE public.inventory_items ADD COLUMN IF NOT EXISTS equipment_key character varying(40);
ALTER TABLE public.inventory_items ALTER COLUMN equipment_key DROP NOT NULL;
ALTER TABLE public.inventory_items ADD COLUMN IF NOT EXISTS weapon_key character varying(40);
ALTER TABLE public.inventory_items ALTER COLUMN weapon_key DROP NOT NULL;
ALTER TABLE public.inventory_items ADD COLUMN IF NOT EXISTS name character varying(100);
ALTER TABLE public.inventory_items ADD COLUMN IF NOT EXISTS description text;
ALTER TABLE public.inventory_items ALTER COLUMN description DROP NOT NULL;

CREATE TABLE IF NOT EXISTS public.notebook_drafts (
    created_at timestamp(6) with time zone NOT NULL,
    id uuid NOT NULL,
    sheet_id uuid NOT NULL,
    kind character varying(20) NOT NULL,
    status character varying(20) NOT NULL,
    review_note character varying(500),
    base jsonb,
    technique jsonb
);
ALTER TABLE public.notebook_drafts ADD COLUMN IF NOT EXISTS created_at timestamp(6) with time zone;
ALTER TABLE public.notebook_drafts ADD COLUMN IF NOT EXISTS id uuid;
ALTER TABLE public.notebook_drafts ADD COLUMN IF NOT EXISTS sheet_id uuid;
ALTER TABLE public.notebook_drafts ADD COLUMN IF NOT EXISTS kind character varying(20);
ALTER TABLE public.notebook_drafts ADD COLUMN IF NOT EXISTS status character varying(20);
ALTER TABLE public.notebook_drafts ADD COLUMN IF NOT EXISTS review_note character varying(500);
ALTER TABLE public.notebook_drafts ALTER COLUMN review_note DROP NOT NULL;
ALTER TABLE public.notebook_drafts ADD COLUMN IF NOT EXISTS base jsonb;
ALTER TABLE public.notebook_drafts ALTER COLUMN base DROP NOT NULL;
ALTER TABLE public.notebook_drafts ADD COLUMN IF NOT EXISTS technique jsonb;
ALTER TABLE public.notebook_drafts ALTER COLUMN technique DROP NOT NULL;

CREATE TABLE IF NOT EXISTS public.npc_images (
    npc_id uuid NOT NULL,
    content_type character varying(100) NOT NULL,
    data bytea NOT NULL
);
ALTER TABLE public.npc_images ADD COLUMN IF NOT EXISTS npc_id uuid;
ALTER TABLE public.npc_images ADD COLUMN IF NOT EXISTS content_type character varying(100);
ALTER TABLE public.npc_images ADD COLUMN IF NOT EXISTS data bytea;

CREATE TABLE IF NOT EXISTS public.npc_sheet_visibility (
    visible boolean NOT NULL,
    campaign_npc_id uuid NOT NULL,
    id uuid NOT NULL,
    section character varying(50) NOT NULL
);
ALTER TABLE public.npc_sheet_visibility ADD COLUMN IF NOT EXISTS visible boolean;
ALTER TABLE public.npc_sheet_visibility ADD COLUMN IF NOT EXISTS campaign_npc_id uuid;
ALTER TABLE public.npc_sheet_visibility ADD COLUMN IF NOT EXISTS id uuid;
ALTER TABLE public.npc_sheet_visibility ADD COLUMN IF NOT EXISTS section character varying(50);

CREATE TABLE IF NOT EXISTS public.overcome_distributions (
    created_at timestamp(6) with time zone NOT NULL,
    reviewed_at timestamp(6) with time zone,
    id uuid NOT NULL,
    sheet_id uuid NOT NULL,
    status character varying(20) NOT NULL,
    review_note character varying(500),
    allocations jsonb
);
ALTER TABLE public.overcome_distributions ADD COLUMN IF NOT EXISTS created_at timestamp(6) with time zone;
ALTER TABLE public.overcome_distributions ADD COLUMN IF NOT EXISTS reviewed_at timestamp(6) with time zone;
ALTER TABLE public.overcome_distributions ALTER COLUMN reviewed_at DROP NOT NULL;
ALTER TABLE public.overcome_distributions ADD COLUMN IF NOT EXISTS id uuid;
ALTER TABLE public.overcome_distributions ADD COLUMN IF NOT EXISTS sheet_id uuid;
ALTER TABLE public.overcome_distributions ADD COLUMN IF NOT EXISTS status character varying(20);
ALTER TABLE public.overcome_distributions ADD COLUMN IF NOT EXISTS review_note character varying(500);
ALTER TABLE public.overcome_distributions ALTER COLUMN review_note DROP NOT NULL;
ALTER TABLE public.overcome_distributions ADD COLUMN IF NOT EXISTS allocations jsonb;
ALTER TABLE public.overcome_distributions ALTER COLUMN allocations DROP NOT NULL;

CREATE TABLE IF NOT EXISTS public.pending_attacks (
    distance_m double precision,
    region_penalty integer NOT NULL,
    reserved_pa integer NOT NULL,
    reserved_sp integer NOT NULL,
    resolved_turn integer,
    round integer NOT NULL,
    run_meters double precision,
    squeeze boolean DEFAULT false NOT NULL,
    surface boolean DEFAULT false NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    resolved_at timestamp(6) with time zone,
    grip character varying(10),
    strike_side character varying(10),
    attack_roll_id uuid,
    attacker_id uuid NOT NULL,
    encounter_id uuid NOT NULL,
    id uuid NOT NULL,
    source_id uuid,
    action_kind character varying(20) NOT NULL,
    basic_attack character varying(20),
    source_type character varying(20) NOT NULL,
    status character varying(25) NOT NULL,
    region character varying(40),
    source_name character varying(100) NOT NULL,
    variation character varying(100),
    details character varying(300)
);
ALTER TABLE public.pending_attacks ADD COLUMN IF NOT EXISTS distance_m double precision;
ALTER TABLE public.pending_attacks ALTER COLUMN distance_m DROP NOT NULL;
ALTER TABLE public.pending_attacks ADD COLUMN IF NOT EXISTS region_penalty integer;
ALTER TABLE public.pending_attacks ADD COLUMN IF NOT EXISTS reserved_pa integer;
ALTER TABLE public.pending_attacks ADD COLUMN IF NOT EXISTS reserved_sp integer;
ALTER TABLE public.pending_attacks ADD COLUMN IF NOT EXISTS resolved_turn integer;
ALTER TABLE public.pending_attacks ALTER COLUMN resolved_turn DROP NOT NULL;
ALTER TABLE public.pending_attacks ADD COLUMN IF NOT EXISTS round integer;
ALTER TABLE public.pending_attacks ADD COLUMN IF NOT EXISTS run_meters double precision;
ALTER TABLE public.pending_attacks ALTER COLUMN run_meters DROP NOT NULL;
ALTER TABLE public.pending_attacks ADD COLUMN IF NOT EXISTS squeeze boolean DEFAULT false NOT NULL;
ALTER TABLE public.pending_attacks ADD COLUMN IF NOT EXISTS surface boolean DEFAULT false NOT NULL;
ALTER TABLE public.pending_attacks ADD COLUMN IF NOT EXISTS created_at timestamp(6) with time zone;
ALTER TABLE public.pending_attacks ADD COLUMN IF NOT EXISTS resolved_at timestamp(6) with time zone;
ALTER TABLE public.pending_attacks ALTER COLUMN resolved_at DROP NOT NULL;
ALTER TABLE public.pending_attacks ADD COLUMN IF NOT EXISTS grip character varying(10);
ALTER TABLE public.pending_attacks ALTER COLUMN grip DROP NOT NULL;
ALTER TABLE public.pending_attacks ADD COLUMN IF NOT EXISTS strike_side character varying(10);
ALTER TABLE public.pending_attacks ALTER COLUMN strike_side DROP NOT NULL;
ALTER TABLE public.pending_attacks ADD COLUMN IF NOT EXISTS attack_roll_id uuid;
ALTER TABLE public.pending_attacks ALTER COLUMN attack_roll_id DROP NOT NULL;
ALTER TABLE public.pending_attacks ADD COLUMN IF NOT EXISTS attacker_id uuid;
ALTER TABLE public.pending_attacks ADD COLUMN IF NOT EXISTS encounter_id uuid;
ALTER TABLE public.pending_attacks ADD COLUMN IF NOT EXISTS id uuid;
ALTER TABLE public.pending_attacks ADD COLUMN IF NOT EXISTS source_id uuid;
ALTER TABLE public.pending_attacks ALTER COLUMN source_id DROP NOT NULL;
ALTER TABLE public.pending_attacks ADD COLUMN IF NOT EXISTS action_kind character varying(20);
ALTER TABLE public.pending_attacks ADD COLUMN IF NOT EXISTS basic_attack character varying(20);
ALTER TABLE public.pending_attacks ALTER COLUMN basic_attack DROP NOT NULL;
ALTER TABLE public.pending_attacks ADD COLUMN IF NOT EXISTS source_type character varying(20);
ALTER TABLE public.pending_attacks ADD COLUMN IF NOT EXISTS status character varying(25);
ALTER TABLE public.pending_attacks ADD COLUMN IF NOT EXISTS region character varying(40);
ALTER TABLE public.pending_attacks ALTER COLUMN region DROP NOT NULL;
ALTER TABLE public.pending_attacks ADD COLUMN IF NOT EXISTS source_name character varying(100);
ALTER TABLE public.pending_attacks ADD COLUMN IF NOT EXISTS variation character varying(100);
ALTER TABLE public.pending_attacks ALTER COLUMN variation DROP NOT NULL;
ALTER TABLE public.pending_attacks ADD COLUMN IF NOT EXISTS details character varying(300);
ALTER TABLE public.pending_attacks ALTER COLUMN details DROP NOT NULL;

CREATE TABLE IF NOT EXISTS public.permanent_injuries (
    hp_max_reduction integer NOT NULL,
    limiter_reduction integer NOT NULL,
    treated boolean NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    id uuid NOT NULL,
    sheet_id uuid NOT NULL,
    affected_attribute character varying(20),
    body_part character varying(20),
    severity character varying(20) NOT NULL,
    type character varying(20) NOT NULL,
    region character varying(100)
);
ALTER TABLE public.permanent_injuries ADD COLUMN IF NOT EXISTS hp_max_reduction integer;
ALTER TABLE public.permanent_injuries ADD COLUMN IF NOT EXISTS limiter_reduction integer;
ALTER TABLE public.permanent_injuries ADD COLUMN IF NOT EXISTS treated boolean;
ALTER TABLE public.permanent_injuries ADD COLUMN IF NOT EXISTS created_at timestamp(6) with time zone;
ALTER TABLE public.permanent_injuries ADD COLUMN IF NOT EXISTS id uuid;
ALTER TABLE public.permanent_injuries ADD COLUMN IF NOT EXISTS sheet_id uuid;
ALTER TABLE public.permanent_injuries ADD COLUMN IF NOT EXISTS affected_attribute character varying(20);
ALTER TABLE public.permanent_injuries ALTER COLUMN affected_attribute DROP NOT NULL;
ALTER TABLE public.permanent_injuries ADD COLUMN IF NOT EXISTS body_part character varying(20);
ALTER TABLE public.permanent_injuries ALTER COLUMN body_part DROP NOT NULL;
ALTER TABLE public.permanent_injuries ADD COLUMN IF NOT EXISTS severity character varying(20);
ALTER TABLE public.permanent_injuries ADD COLUMN IF NOT EXISTS type character varying(20);
ALTER TABLE public.permanent_injuries ADD COLUMN IF NOT EXISTS region character varying(100);
ALTER TABLE public.permanent_injuries ALTER COLUMN region DROP NOT NULL;

CREATE TABLE IF NOT EXISTS public.rivals (
    fights integer NOT NULL,
    read_bonus integer,
    created_at timestamp(6) with time zone NOT NULL,
    id uuid NOT NULL,
    npc_id uuid,
    sheet_id uuid NOT NULL,
    art character varying(100),
    name character varying(100) NOT NULL,
    known_techniques jsonb
);
ALTER TABLE public.rivals ADD COLUMN IF NOT EXISTS fights integer;
ALTER TABLE public.rivals ADD COLUMN IF NOT EXISTS read_bonus integer;
ALTER TABLE public.rivals ALTER COLUMN read_bonus DROP NOT NULL;
ALTER TABLE public.rivals ADD COLUMN IF NOT EXISTS created_at timestamp(6) with time zone;
ALTER TABLE public.rivals ADD COLUMN IF NOT EXISTS id uuid;
ALTER TABLE public.rivals ADD COLUMN IF NOT EXISTS npc_id uuid;
ALTER TABLE public.rivals ALTER COLUMN npc_id DROP NOT NULL;
ALTER TABLE public.rivals ADD COLUMN IF NOT EXISTS sheet_id uuid;
ALTER TABLE public.rivals ADD COLUMN IF NOT EXISTS art character varying(100);
ALTER TABLE public.rivals ALTER COLUMN art DROP NOT NULL;
ALTER TABLE public.rivals ADD COLUMN IF NOT EXISTS name character varying(100);
ALTER TABLE public.rivals ADD COLUMN IF NOT EXISTS known_techniques jsonb;
ALTER TABLE public.rivals ALTER COLUMN known_techniques DROP NOT NULL;

CREATE TABLE IF NOT EXISTS public.roll_channels (
    created_at timestamp(6) with time zone NOT NULL,
    campaign_id uuid NOT NULL,
    id uuid NOT NULL,
    name character varying(50) NOT NULL,
    member_ids jsonb
);
ALTER TABLE public.roll_channels ADD COLUMN IF NOT EXISTS created_at timestamp(6) with time zone;
ALTER TABLE public.roll_channels ADD COLUMN IF NOT EXISTS campaign_id uuid;
ALTER TABLE public.roll_channels ADD COLUMN IF NOT EXISTS id uuid;
ALTER TABLE public.roll_channels ADD COLUMN IF NOT EXISTS name character varying(50);
ALTER TABLE public.roll_channels ADD COLUMN IF NOT EXISTS member_ids jsonb;
ALTER TABLE public.roll_channels ALTER COLUMN member_ids DROP NOT NULL;

CREATE TABLE IF NOT EXISTS public.rolls (
    advantage integer,
    critical boolean,
    damage_total integer,
    fumble boolean,
    modifier integer NOT NULL,
    natural_d20 integer,
    revealed boolean,
    total integer NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    actor_id uuid,
    campaign_id uuid,
    custom_channel_id uuid,
    id uuid NOT NULL,
    sheet_id uuid,
    source_id uuid,
    channel character varying(20),
    mode character varying(20),
    source_type character varying(20),
    source_name character varying(100),
    edit_reason character varying(200),
    expression character varying(200),
    purpose character varying(200),
    dice_results text,
    breakdown jsonb
);
ALTER TABLE public.rolls ADD COLUMN IF NOT EXISTS advantage integer;
ALTER TABLE public.rolls ALTER COLUMN advantage DROP NOT NULL;
ALTER TABLE public.rolls ADD COLUMN IF NOT EXISTS critical boolean;
ALTER TABLE public.rolls ALTER COLUMN critical DROP NOT NULL;
ALTER TABLE public.rolls ADD COLUMN IF NOT EXISTS damage_total integer;
ALTER TABLE public.rolls ALTER COLUMN damage_total DROP NOT NULL;
ALTER TABLE public.rolls ADD COLUMN IF NOT EXISTS fumble boolean;
ALTER TABLE public.rolls ALTER COLUMN fumble DROP NOT NULL;
ALTER TABLE public.rolls ADD COLUMN IF NOT EXISTS modifier integer;
ALTER TABLE public.rolls ADD COLUMN IF NOT EXISTS natural_d20 integer;
ALTER TABLE public.rolls ALTER COLUMN natural_d20 DROP NOT NULL;
ALTER TABLE public.rolls ADD COLUMN IF NOT EXISTS revealed boolean;
ALTER TABLE public.rolls ALTER COLUMN revealed DROP NOT NULL;
ALTER TABLE public.rolls ADD COLUMN IF NOT EXISTS total integer;
ALTER TABLE public.rolls ADD COLUMN IF NOT EXISTS created_at timestamp(6) with time zone;
ALTER TABLE public.rolls ADD COLUMN IF NOT EXISTS actor_id uuid;
ALTER TABLE public.rolls ALTER COLUMN actor_id DROP NOT NULL;
ALTER TABLE public.rolls ADD COLUMN IF NOT EXISTS campaign_id uuid;
ALTER TABLE public.rolls ALTER COLUMN campaign_id DROP NOT NULL;
ALTER TABLE public.rolls ADD COLUMN IF NOT EXISTS custom_channel_id uuid;
ALTER TABLE public.rolls ALTER COLUMN custom_channel_id DROP NOT NULL;
ALTER TABLE public.rolls ADD COLUMN IF NOT EXISTS id uuid;
ALTER TABLE public.rolls ADD COLUMN IF NOT EXISTS sheet_id uuid;
ALTER TABLE public.rolls ALTER COLUMN sheet_id DROP NOT NULL;
ALTER TABLE public.rolls ADD COLUMN IF NOT EXISTS source_id uuid;
ALTER TABLE public.rolls ALTER COLUMN source_id DROP NOT NULL;
ALTER TABLE public.rolls ADD COLUMN IF NOT EXISTS channel character varying(20);
ALTER TABLE public.rolls ALTER COLUMN channel DROP NOT NULL;
ALTER TABLE public.rolls ADD COLUMN IF NOT EXISTS mode character varying(20);
ALTER TABLE public.rolls ALTER COLUMN mode DROP NOT NULL;
ALTER TABLE public.rolls ADD COLUMN IF NOT EXISTS source_type character varying(20);
ALTER TABLE public.rolls ALTER COLUMN source_type DROP NOT NULL;
ALTER TABLE public.rolls ADD COLUMN IF NOT EXISTS source_name character varying(100);
ALTER TABLE public.rolls ALTER COLUMN source_name DROP NOT NULL;
ALTER TABLE public.rolls ADD COLUMN IF NOT EXISTS edit_reason character varying(200);
ALTER TABLE public.rolls ALTER COLUMN edit_reason DROP NOT NULL;
ALTER TABLE public.rolls ADD COLUMN IF NOT EXISTS expression character varying(200);
ALTER TABLE public.rolls ALTER COLUMN expression DROP NOT NULL;
ALTER TABLE public.rolls ADD COLUMN IF NOT EXISTS purpose character varying(200);
ALTER TABLE public.rolls ALTER COLUMN purpose DROP NOT NULL;
ALTER TABLE public.rolls ADD COLUMN IF NOT EXISTS dice_results text;
ALTER TABLE public.rolls ALTER COLUMN dice_results DROP NOT NULL;
ALTER TABLE public.rolls ADD COLUMN IF NOT EXISTS breakdown jsonb;
ALTER TABLE public.rolls ALTER COLUMN breakdown DROP NOT NULL;

CREATE TABLE IF NOT EXISTS public.sheet_activity_logs (
    created_at timestamp(6) with time zone NOT NULL,
    actor_id uuid,
    campaign_id uuid,
    id uuid NOT NULL,
    roll_id uuid,
    sheet_id uuid NOT NULL,
    actor_role character varying(20) NOT NULL,
    category character varying(30) NOT NULL,
    action character varying(50),
    field character varying(50),
    reason character varying(500),
    after_value text,
    before_value text
);
ALTER TABLE public.sheet_activity_logs ADD COLUMN IF NOT EXISTS created_at timestamp(6) with time zone;
ALTER TABLE public.sheet_activity_logs ADD COLUMN IF NOT EXISTS actor_id uuid;
ALTER TABLE public.sheet_activity_logs ALTER COLUMN actor_id DROP NOT NULL;
ALTER TABLE public.sheet_activity_logs ADD COLUMN IF NOT EXISTS campaign_id uuid;
ALTER TABLE public.sheet_activity_logs ALTER COLUMN campaign_id DROP NOT NULL;
ALTER TABLE public.sheet_activity_logs ADD COLUMN IF NOT EXISTS id uuid;
ALTER TABLE public.sheet_activity_logs ADD COLUMN IF NOT EXISTS roll_id uuid;
ALTER TABLE public.sheet_activity_logs ALTER COLUMN roll_id DROP NOT NULL;
ALTER TABLE public.sheet_activity_logs ADD COLUMN IF NOT EXISTS sheet_id uuid;
ALTER TABLE public.sheet_activity_logs ADD COLUMN IF NOT EXISTS actor_role character varying(20);
ALTER TABLE public.sheet_activity_logs ADD COLUMN IF NOT EXISTS category character varying(30);
ALTER TABLE public.sheet_activity_logs ADD COLUMN IF NOT EXISTS action character varying(50);
ALTER TABLE public.sheet_activity_logs ALTER COLUMN action DROP NOT NULL;
ALTER TABLE public.sheet_activity_logs ADD COLUMN IF NOT EXISTS field character varying(50);
ALTER TABLE public.sheet_activity_logs ALTER COLUMN field DROP NOT NULL;
ALTER TABLE public.sheet_activity_logs ADD COLUMN IF NOT EXISTS reason character varying(500);
ALTER TABLE public.sheet_activity_logs ALTER COLUMN reason DROP NOT NULL;
ALTER TABLE public.sheet_activity_logs ADD COLUMN IF NOT EXISTS after_value text;
ALTER TABLE public.sheet_activity_logs ALTER COLUMN after_value DROP NOT NULL;
ALTER TABLE public.sheet_activity_logs ADD COLUMN IF NOT EXISTS before_value text;
ALTER TABLE public.sheet_activity_logs ALTER COLUMN before_value DROP NOT NULL;

CREATE TABLE IF NOT EXISTS public.sheet_change_requests (
    created_at timestamp(6) with time zone NOT NULL,
    reviewed_at timestamp(6) with time zone,
    id uuid NOT NULL,
    requester_id uuid NOT NULL,
    sheet_id uuid NOT NULL,
    status character varying(20) NOT NULL,
    review_note character varying(500),
    summary text,
    changes jsonb NOT NULL
);
ALTER TABLE public.sheet_change_requests ADD COLUMN IF NOT EXISTS created_at timestamp(6) with time zone;
ALTER TABLE public.sheet_change_requests ADD COLUMN IF NOT EXISTS reviewed_at timestamp(6) with time zone;
ALTER TABLE public.sheet_change_requests ALTER COLUMN reviewed_at DROP NOT NULL;
ALTER TABLE public.sheet_change_requests ADD COLUMN IF NOT EXISTS id uuid;
ALTER TABLE public.sheet_change_requests ADD COLUMN IF NOT EXISTS requester_id uuid;
ALTER TABLE public.sheet_change_requests ADD COLUMN IF NOT EXISTS sheet_id uuid;
ALTER TABLE public.sheet_change_requests ADD COLUMN IF NOT EXISTS status character varying(20);
ALTER TABLE public.sheet_change_requests ADD COLUMN IF NOT EXISTS review_note character varying(500);
ALTER TABLE public.sheet_change_requests ALTER COLUMN review_note DROP NOT NULL;
ALTER TABLE public.sheet_change_requests ADD COLUMN IF NOT EXISTS summary text;
ALTER TABLE public.sheet_change_requests ALTER COLUMN summary DROP NOT NULL;
ALTER TABLE public.sheet_change_requests ADD COLUMN IF NOT EXISTS changes jsonb;

CREATE TABLE IF NOT EXISTS public.sheet_images (
    id uuid NOT NULL,
    sheet_id uuid NOT NULL,
    kind character varying(20) NOT NULL,
    content_type character varying(100) NOT NULL,
    data bytea NOT NULL
);
ALTER TABLE public.sheet_images ADD COLUMN IF NOT EXISTS id uuid;
ALTER TABLE public.sheet_images ADD COLUMN IF NOT EXISTS sheet_id uuid;
ALTER TABLE public.sheet_images ADD COLUMN IF NOT EXISTS kind character varying(20);
ALTER TABLE public.sheet_images ADD COLUMN IF NOT EXISTS content_type character varying(100);
ALTER TABLE public.sheet_images ADD COLUMN IF NOT EXISTS data bytea;

CREATE TABLE IF NOT EXISTS public.time_skip_activities (
    idle_point_cost smallint NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    id uuid NOT NULL,
    time_skip_id uuid NOT NULL,
    name character varying(100) NOT NULL,
    description text NOT NULL
);
ALTER TABLE public.time_skip_activities ADD COLUMN IF NOT EXISTS idle_point_cost smallint;
ALTER TABLE public.time_skip_activities ADD COLUMN IF NOT EXISTS created_at timestamp(6) with time zone;
ALTER TABLE public.time_skip_activities ADD COLUMN IF NOT EXISTS id uuid;
ALTER TABLE public.time_skip_activities ADD COLUMN IF NOT EXISTS time_skip_id uuid;
ALTER TABLE public.time_skip_activities ADD COLUMN IF NOT EXISTS name character varying(100);
ALTER TABLE public.time_skip_activities ADD COLUMN IF NOT EXISTS description text;

CREATE TABLE IF NOT EXISTS public.time_skip_days (
    day_number smallint NOT NULL,
    id uuid NOT NULL,
    time_skip_id uuid NOT NULL
);
ALTER TABLE public.time_skip_days ADD COLUMN IF NOT EXISTS day_number smallint;
ALTER TABLE public.time_skip_days ADD COLUMN IF NOT EXISTS id uuid;
ALTER TABLE public.time_skip_days ADD COLUMN IF NOT EXISTS time_skip_id uuid;

CREATE TABLE IF NOT EXISTS public.time_skip_submissions (
    reviewed_at timestamp(6) with time zone,
    submitted_at timestamp(6) with time zone NOT NULL,
    id uuid NOT NULL,
    time_skip_id uuid NOT NULL,
    user_id uuid NOT NULL,
    status character varying(20) NOT NULL,
    note character varying(500)
);
ALTER TABLE public.time_skip_submissions ADD COLUMN IF NOT EXISTS reviewed_at timestamp(6) with time zone;
ALTER TABLE public.time_skip_submissions ALTER COLUMN reviewed_at DROP NOT NULL;
ALTER TABLE public.time_skip_submissions ADD COLUMN IF NOT EXISTS submitted_at timestamp(6) with time zone;
ALTER TABLE public.time_skip_submissions ADD COLUMN IF NOT EXISTS id uuid;
ALTER TABLE public.time_skip_submissions ADD COLUMN IF NOT EXISTS time_skip_id uuid;
ALTER TABLE public.time_skip_submissions ADD COLUMN IF NOT EXISTS user_id uuid;
ALTER TABLE public.time_skip_submissions ADD COLUMN IF NOT EXISTS status character varying(20);
ALTER TABLE public.time_skip_submissions ADD COLUMN IF NOT EXISTS note character varying(500);
ALTER TABLE public.time_skip_submissions ALTER COLUMN note DROP NOT NULL;

CREATE TABLE IF NOT EXISTS public.time_skips (
    current_day smallint NOT NULL,
    processed_day smallint,
    slots_per_day smallint DEFAULT 4 NOT NULL,
    total_days smallint NOT NULL,
    closed_at timestamp(6) with time zone,
    created_at timestamp(6) with time zone NOT NULL,
    campaign_id uuid NOT NULL,
    id uuid NOT NULL,
    mode character varying(20),
    status character varying(20) NOT NULL,
    venue character varying(20),
    name character varying(100) NOT NULL
);
ALTER TABLE public.time_skips ADD COLUMN IF NOT EXISTS current_day smallint;
ALTER TABLE public.time_skips ADD COLUMN IF NOT EXISTS processed_day smallint;
ALTER TABLE public.time_skips ALTER COLUMN processed_day DROP NOT NULL;
ALTER TABLE public.time_skips ADD COLUMN IF NOT EXISTS slots_per_day smallint DEFAULT 4 NOT NULL;
ALTER TABLE public.time_skips ADD COLUMN IF NOT EXISTS total_days smallint;
ALTER TABLE public.time_skips ADD COLUMN IF NOT EXISTS closed_at timestamp(6) with time zone;
ALTER TABLE public.time_skips ALTER COLUMN closed_at DROP NOT NULL;
ALTER TABLE public.time_skips ADD COLUMN IF NOT EXISTS created_at timestamp(6) with time zone;
ALTER TABLE public.time_skips ADD COLUMN IF NOT EXISTS campaign_id uuid;
ALTER TABLE public.time_skips ADD COLUMN IF NOT EXISTS id uuid;
ALTER TABLE public.time_skips ADD COLUMN IF NOT EXISTS mode character varying(20);
ALTER TABLE public.time_skips ALTER COLUMN mode DROP NOT NULL;
ALTER TABLE public.time_skips ADD COLUMN IF NOT EXISTS status character varying(20);
ALTER TABLE public.time_skips ADD COLUMN IF NOT EXISTS venue character varying(20);
ALTER TABLE public.time_skips ALTER COLUMN venue DROP NOT NULL;
ALTER TABLE public.time_skips ADD COLUMN IF NOT EXISTS name character varying(100);

CREATE TABLE IF NOT EXISTS public.training_entries (
    day_number smallint NOT NULL,
    full_training boolean,
    gain integer NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    booking_id uuid,
    id uuid NOT NULL,
    sheet_id uuid NOT NULL,
    technique_id uuid,
    time_skip_id uuid NOT NULL,
    status character varying(20) NOT NULL,
    target character varying(20) NOT NULL,
    note character varying(300)
);
ALTER TABLE public.training_entries ADD COLUMN IF NOT EXISTS day_number smallint;
ALTER TABLE public.training_entries ADD COLUMN IF NOT EXISTS full_training boolean;
ALTER TABLE public.training_entries ALTER COLUMN full_training DROP NOT NULL;
ALTER TABLE public.training_entries ADD COLUMN IF NOT EXISTS gain integer;
ALTER TABLE public.training_entries ADD COLUMN IF NOT EXISTS created_at timestamp(6) with time zone;
ALTER TABLE public.training_entries ADD COLUMN IF NOT EXISTS booking_id uuid;
ALTER TABLE public.training_entries ALTER COLUMN booking_id DROP NOT NULL;
ALTER TABLE public.training_entries ADD COLUMN IF NOT EXISTS id uuid;
ALTER TABLE public.training_entries ADD COLUMN IF NOT EXISTS sheet_id uuid;
ALTER TABLE public.training_entries ADD COLUMN IF NOT EXISTS technique_id uuid;
ALTER TABLE public.training_entries ALTER COLUMN technique_id DROP NOT NULL;
ALTER TABLE public.training_entries ADD COLUMN IF NOT EXISTS time_skip_id uuid;
ALTER TABLE public.training_entries ADD COLUMN IF NOT EXISTS status character varying(20);
ALTER TABLE public.training_entries ADD COLUMN IF NOT EXISTS target character varying(20);
ALTER TABLE public.training_entries ADD COLUMN IF NOT EXISTS note character varying(300);
ALTER TABLE public.training_entries ALTER COLUMN note DROP NOT NULL;

CREATE TABLE IF NOT EXISTS public.users (
    created_at timestamp(6) with time zone NOT NULL,
    id uuid NOT NULL,
    name character varying(100) NOT NULL,
    email character varying(150) NOT NULL,
    password character varying(255) NOT NULL
);
ALTER TABLE public.users ADD COLUMN IF NOT EXISTS created_at timestamp(6) with time zone;
ALTER TABLE public.users ADD COLUMN IF NOT EXISTS id uuid;
ALTER TABLE public.users ADD COLUMN IF NOT EXISTS name character varying(100);
ALTER TABLE public.users ADD COLUMN IF NOT EXISTS email character varying(150);
ALTER TABLE public.users ADD COLUMN IF NOT EXISTS password character varying(255);

CREATE TABLE IF NOT EXISTS public.wound_marks (
    created_at timestamp(6) with time zone NOT NULL,
    type character varying(10) NOT NULL,
    id uuid NOT NULL,
    sheet_id uuid NOT NULL,
    part character varying(20) NOT NULL,
    severity character varying(20),
    geometry jsonb NOT NULL
);
ALTER TABLE public.wound_marks ADD COLUMN IF NOT EXISTS created_at timestamp(6) with time zone;
ALTER TABLE public.wound_marks ADD COLUMN IF NOT EXISTS type character varying(10);
ALTER TABLE public.wound_marks ADD COLUMN IF NOT EXISTS id uuid;
ALTER TABLE public.wound_marks ADD COLUMN IF NOT EXISTS sheet_id uuid;
ALTER TABLE public.wound_marks ADD COLUMN IF NOT EXISTS part character varying(20);
ALTER TABLE public.wound_marks ADD COLUMN IF NOT EXISTS severity character varying(20);
ALTER TABLE public.wound_marks ALTER COLUMN severity DROP NOT NULL;
ALTER TABLE public.wound_marks ADD COLUMN IF NOT EXISTS geometry jsonb;

-- Chaves e índices que faltarem (falha de dados só avisa, não aborta).
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE contype = 'p' AND conrelid = 'public.attack_targets'::regclass) THEN
    ALTER TABLE public.attack_targets ADD CONSTRAINT attack_targets_pkey PRIMARY KEY (id);
  END IF;
EXCEPTION WHEN others THEN RAISE WARNING 'public.attack_targets attack_targets_pkey: %', SQLERRM;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE contype = 'p' AND conrelid = 'public.bookings'::regclass) THEN
    ALTER TABLE public.bookings ADD CONSTRAINT bookings_pkey PRIMARY KEY (id);
  END IF;
EXCEPTION WHEN others THEN RAISE WARNING 'public.bookings bookings_pkey: %', SQLERRM;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'campaign_members_campaign_id_user_id_key' AND conrelid = 'public.campaign_members'::regclass) THEN
    ALTER TABLE public.campaign_members ADD CONSTRAINT campaign_members_campaign_id_user_id_key UNIQUE (campaign_id, user_id);
  END IF;
EXCEPTION WHEN others THEN RAISE WARNING 'public.campaign_members campaign_members_campaign_id_user_id_key: %', SQLERRM;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE contype = 'p' AND conrelid = 'public.campaign_members'::regclass) THEN
    ALTER TABLE public.campaign_members ADD CONSTRAINT campaign_members_pkey PRIMARY KEY (id);
  END IF;
EXCEPTION WHEN others THEN RAISE WARNING 'public.campaign_members campaign_members_pkey: %', SQLERRM;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'campaign_npcs_campaign_id_npc_id_key' AND conrelid = 'public.campaign_npcs'::regclass) THEN
    ALTER TABLE public.campaign_npcs ADD CONSTRAINT campaign_npcs_campaign_id_npc_id_key UNIQUE (campaign_id, npc_id);
  END IF;
EXCEPTION WHEN others THEN RAISE WARNING 'public.campaign_npcs campaign_npcs_campaign_id_npc_id_key: %', SQLERRM;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE contype = 'p' AND conrelid = 'public.campaign_npcs'::regclass) THEN
    ALTER TABLE public.campaign_npcs ADD CONSTRAINT campaign_npcs_pkey PRIMARY KEY (id);
  END IF;
EXCEPTION WHEN others THEN RAISE WARNING 'public.campaign_npcs campaign_npcs_pkey: %', SQLERRM;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'campaign_rule_settings_campaign_id_rule_key_key' AND conrelid = 'public.campaign_rule_settings'::regclass) THEN
    ALTER TABLE public.campaign_rule_settings ADD CONSTRAINT campaign_rule_settings_campaign_id_rule_key_key UNIQUE (campaign_id, rule_key);
  END IF;
EXCEPTION WHEN others THEN RAISE WARNING 'public.campaign_rule_settings campaign_rule_settings_campaign_id_rule_key_key: %', SQLERRM;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE contype = 'p' AND conrelid = 'public.campaign_rule_settings'::regclass) THEN
    ALTER TABLE public.campaign_rule_settings ADD CONSTRAINT campaign_rule_settings_pkey PRIMARY KEY (id);
  END IF;
EXCEPTION WHEN others THEN RAISE WARNING 'public.campaign_rule_settings campaign_rule_settings_pkey: %', SQLERRM;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE contype = 'p' AND conrelid = 'public.campaigns'::regclass) THEN
    ALTER TABLE public.campaigns ADD CONSTRAINT campaigns_pkey PRIMARY KEY (id);
  END IF;
EXCEPTION WHEN others THEN RAISE WARNING 'public.campaigns campaigns_pkey: %', SQLERRM;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'catalog_item_images_kind_item_key_campaign_id_key' AND conrelid = 'public.catalog_item_images'::regclass) THEN
    ALTER TABLE public.catalog_item_images ADD CONSTRAINT catalog_item_images_kind_item_key_campaign_id_key UNIQUE (kind, item_key, campaign_id);
  END IF;
EXCEPTION WHEN others THEN RAISE WARNING 'public.catalog_item_images catalog_item_images_kind_item_key_campaign_id_key: %', SQLERRM;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE contype = 'p' AND conrelid = 'public.catalog_item_images'::regclass) THEN
    ALTER TABLE public.catalog_item_images ADD CONSTRAINT catalog_item_images_pkey PRIMARY KEY (id);
  END IF;
EXCEPTION WHEN others THEN RAISE WARNING 'public.catalog_item_images catalog_item_images_pkey: %', SQLERRM;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE contype = 'p' AND conrelid = 'public.character_arts'::regclass) THEN
    ALTER TABLE public.character_arts ADD CONSTRAINT character_arts_pkey PRIMARY KEY (id);
  END IF;
EXCEPTION WHEN others THEN RAISE WARNING 'public.character_arts character_arts_pkey: %', SQLERRM;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'character_arts_sheet_id_art_id_key' AND conrelid = 'public.character_arts'::regclass) THEN
    ALTER TABLE public.character_arts ADD CONSTRAINT character_arts_sheet_id_art_id_key UNIQUE (sheet_id, art_id);
  END IF;
EXCEPTION WHEN others THEN RAISE WARNING 'public.character_arts character_arts_sheet_id_art_id_key: %', SQLERRM;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE contype = 'p' AND conrelid = 'public.character_builds'::regclass) THEN
    ALTER TABLE public.character_builds ADD CONSTRAINT character_builds_pkey PRIMARY KEY (sheet_id);
  END IF;
EXCEPTION WHEN others THEN RAISE WARNING 'public.character_builds character_builds_pkey: %', SQLERRM;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE contype = 'p' AND conrelid = 'public.character_clans'::regclass) THEN
    ALTER TABLE public.character_clans ADD CONSTRAINT character_clans_pkey PRIMARY KEY (id);
  END IF;
EXCEPTION WHEN others THEN RAISE WARNING 'public.character_clans character_clans_pkey: %', SQLERRM;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE contype = 'p' AND conrelid = 'public.character_instincts'::regclass) THEN
    ALTER TABLE public.character_instincts ADD CONSTRAINT character_instincts_pkey PRIMARY KEY (id);
  END IF;
EXCEPTION WHEN others THEN RAISE WARNING 'public.character_instincts character_instincts_pkey: %', SQLERRM;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'character_instincts_sheet_id_instinct_id_key' AND conrelid = 'public.character_instincts'::regclass) THEN
    ALTER TABLE public.character_instincts ADD CONSTRAINT character_instincts_sheet_id_instinct_id_key UNIQUE (sheet_id, instinct_id);
  END IF;
EXCEPTION WHEN others THEN RAISE WARNING 'public.character_instincts character_instincts_sheet_id_instinct_id_key: %', SQLERRM;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'character_sheet_visibility_campaign_member_id_section_key' AND conrelid = 'public.character_sheet_visibility'::regclass) THEN
    ALTER TABLE public.character_sheet_visibility ADD CONSTRAINT character_sheet_visibility_campaign_member_id_section_key UNIQUE (campaign_member_id, section);
  END IF;
EXCEPTION WHEN others THEN RAISE WARNING 'public.character_sheet_visibility character_sheet_visibility_campaign_member_id_section_key: %', SQLERRM;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE contype = 'p' AND conrelid = 'public.character_sheet_visibility'::regclass) THEN
    ALTER TABLE public.character_sheet_visibility ADD CONSTRAINT character_sheet_visibility_pkey PRIMARY KEY (id);
  END IF;
EXCEPTION WHEN others THEN RAISE WARNING 'public.character_sheet_visibility character_sheet_visibility_pkey: %', SQLERRM;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'character_sheets_npc_id_key' AND conrelid = 'public.character_sheets'::regclass) THEN
    ALTER TABLE public.character_sheets ADD CONSTRAINT character_sheets_npc_id_key UNIQUE (npc_id);
  END IF;
EXCEPTION WHEN others THEN RAISE WARNING 'public.character_sheets character_sheets_npc_id_key: %', SQLERRM;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE contype = 'p' AND conrelid = 'public.character_sheets'::regclass) THEN
    ALTER TABLE public.character_sheets ADD CONSTRAINT character_sheets_pkey PRIMARY KEY (id);
  END IF;
EXCEPTION WHEN others THEN RAISE WARNING 'public.character_sheets character_sheets_pkey: %', SQLERRM;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE contype = 'p' AND conrelid = 'public.character_techniques'::regclass) THEN
    ALTER TABLE public.character_techniques ADD CONSTRAINT character_techniques_pkey PRIMARY KEY (id);
  END IF;
EXCEPTION WHEN others THEN RAISE WARNING 'public.character_techniques character_techniques_pkey: %', SQLERRM;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE contype = 'p' AND conrelid = 'public.character_traits'::regclass) THEN
    ALTER TABLE public.character_traits ADD CONSTRAINT character_traits_pkey PRIMARY KEY (id);
  END IF;
EXCEPTION WHEN others THEN RAISE WARNING 'public.character_traits character_traits_pkey: %', SQLERRM;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE contype = 'p' AND conrelid = 'public.combat_conditions'::regclass) THEN
    ALTER TABLE public.combat_conditions ADD CONSTRAINT combat_conditions_pkey PRIMARY KEY (id);
  END IF;
EXCEPTION WHEN others THEN RAISE WARNING 'public.combat_conditions combat_conditions_pkey: %', SQLERRM;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE contype = 'p' AND conrelid = 'public.combat_counters'::regclass) THEN
    ALTER TABLE public.combat_counters ADD CONSTRAINT combat_counters_pkey PRIMARY KEY (id);
  END IF;
EXCEPTION WHEN others THEN RAISE WARNING 'public.combat_counters combat_counters_pkey: %', SQLERRM;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE contype = 'p' AND conrelid = 'public.combat_events'::regclass) THEN
    ALTER TABLE public.combat_events ADD CONSTRAINT combat_events_pkey PRIMARY KEY (id);
  END IF;
EXCEPTION WHEN others THEN RAISE WARNING 'public.combat_events combat_events_pkey: %', SQLERRM;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'encounter_participants_encounter_id_sheet_id_key' AND conrelid = 'public.encounter_participants'::regclass) THEN
    ALTER TABLE public.encounter_participants ADD CONSTRAINT encounter_participants_encounter_id_sheet_id_key UNIQUE (encounter_id, sheet_id);
  END IF;
EXCEPTION WHEN others THEN RAISE WARNING 'public.encounter_participants encounter_participants_encounter_id_sheet_id_key: %', SQLERRM;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE contype = 'p' AND conrelid = 'public.encounter_participants'::regclass) THEN
    ALTER TABLE public.encounter_participants ADD CONSTRAINT encounter_participants_pkey PRIMARY KEY (id);
  END IF;
EXCEPTION WHEN others THEN RAISE WARNING 'public.encounter_participants encounter_participants_pkey: %', SQLERRM;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE contype = 'p' AND conrelid = 'public.encounters'::regclass) THEN
    ALTER TABLE public.encounters ADD CONSTRAINT encounters_pkey PRIMARY KEY (id);
  END IF;
EXCEPTION WHEN others THEN RAISE WARNING 'public.encounters encounters_pkey: %', SQLERRM;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE contype = 'p' AND conrelid = 'public.fight_records'::regclass) THEN
    ALTER TABLE public.fight_records ADD CONSTRAINT fight_records_pkey PRIMARY KEY (id);
  END IF;
EXCEPTION WHEN others THEN RAISE WARNING 'public.fight_records fight_records_pkey: %', SQLERRM;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE contype = 'p' AND conrelid = 'public.forges'::regclass) THEN
    ALTER TABLE public.forges ADD CONSTRAINT forges_pkey PRIMARY KEY (id);
  END IF;
EXCEPTION WHEN others THEN RAISE WARNING 'public.forges forges_pkey: %', SQLERRM;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE contype = 'p' AND conrelid = 'public.interaction_logs'::regclass) THEN
    ALTER TABLE public.interaction_logs ADD CONSTRAINT interaction_logs_pkey PRIMARY KEY (id);
  END IF;
EXCEPTION WHEN others THEN RAISE WARNING 'public.interaction_logs interaction_logs_pkey: %', SQLERRM;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE contype = 'p' AND conrelid = 'public.inventory_items'::regclass) THEN
    ALTER TABLE public.inventory_items ADD CONSTRAINT inventory_items_pkey PRIMARY KEY (id);
  END IF;
EXCEPTION WHEN others THEN RAISE WARNING 'public.inventory_items inventory_items_pkey: %', SQLERRM;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE contype = 'p' AND conrelid = 'public.notebook_drafts'::regclass) THEN
    ALTER TABLE public.notebook_drafts ADD CONSTRAINT notebook_drafts_pkey PRIMARY KEY (id);
  END IF;
EXCEPTION WHEN others THEN RAISE WARNING 'public.notebook_drafts notebook_drafts_pkey: %', SQLERRM;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE contype = 'p' AND conrelid = 'public.npc_images'::regclass) THEN
    ALTER TABLE public.npc_images ADD CONSTRAINT npc_images_pkey PRIMARY KEY (npc_id);
  END IF;
EXCEPTION WHEN others THEN RAISE WARNING 'public.npc_images npc_images_pkey: %', SQLERRM;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'npc_sheet_visibility_campaign_npc_id_section_key' AND conrelid = 'public.npc_sheet_visibility'::regclass) THEN
    ALTER TABLE public.npc_sheet_visibility ADD CONSTRAINT npc_sheet_visibility_campaign_npc_id_section_key UNIQUE (campaign_npc_id, section);
  END IF;
EXCEPTION WHEN others THEN RAISE WARNING 'public.npc_sheet_visibility npc_sheet_visibility_campaign_npc_id_section_key: %', SQLERRM;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE contype = 'p' AND conrelid = 'public.npc_sheet_visibility'::regclass) THEN
    ALTER TABLE public.npc_sheet_visibility ADD CONSTRAINT npc_sheet_visibility_pkey PRIMARY KEY (id);
  END IF;
EXCEPTION WHEN others THEN RAISE WARNING 'public.npc_sheet_visibility npc_sheet_visibility_pkey: %', SQLERRM;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE contype = 'p' AND conrelid = 'public.overcome_distributions'::regclass) THEN
    ALTER TABLE public.overcome_distributions ADD CONSTRAINT overcome_distributions_pkey PRIMARY KEY (id);
  END IF;
EXCEPTION WHEN others THEN RAISE WARNING 'public.overcome_distributions overcome_distributions_pkey: %', SQLERRM;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE contype = 'p' AND conrelid = 'public.pending_attacks'::regclass) THEN
    ALTER TABLE public.pending_attacks ADD CONSTRAINT pending_attacks_pkey PRIMARY KEY (id);
  END IF;
EXCEPTION WHEN others THEN RAISE WARNING 'public.pending_attacks pending_attacks_pkey: %', SQLERRM;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE contype = 'p' AND conrelid = 'public.permanent_injuries'::regclass) THEN
    ALTER TABLE public.permanent_injuries ADD CONSTRAINT permanent_injuries_pkey PRIMARY KEY (id);
  END IF;
EXCEPTION WHEN others THEN RAISE WARNING 'public.permanent_injuries permanent_injuries_pkey: %', SQLERRM;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE contype = 'p' AND conrelid = 'public.rivals'::regclass) THEN
    ALTER TABLE public.rivals ADD CONSTRAINT rivals_pkey PRIMARY KEY (id);
  END IF;
EXCEPTION WHEN others THEN RAISE WARNING 'public.rivals rivals_pkey: %', SQLERRM;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'rivals_sheet_id_name_key' AND conrelid = 'public.rivals'::regclass) THEN
    ALTER TABLE public.rivals ADD CONSTRAINT rivals_sheet_id_name_key UNIQUE (sheet_id, name);
  END IF;
EXCEPTION WHEN others THEN RAISE WARNING 'public.rivals rivals_sheet_id_name_key: %', SQLERRM;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'roll_channels_campaign_id_name_key' AND conrelid = 'public.roll_channels'::regclass) THEN
    ALTER TABLE public.roll_channels ADD CONSTRAINT roll_channels_campaign_id_name_key UNIQUE (campaign_id, name);
  END IF;
EXCEPTION WHEN others THEN RAISE WARNING 'public.roll_channels roll_channels_campaign_id_name_key: %', SQLERRM;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE contype = 'p' AND conrelid = 'public.roll_channels'::regclass) THEN
    ALTER TABLE public.roll_channels ADD CONSTRAINT roll_channels_pkey PRIMARY KEY (id);
  END IF;
EXCEPTION WHEN others THEN RAISE WARNING 'public.roll_channels roll_channels_pkey: %', SQLERRM;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE contype = 'p' AND conrelid = 'public.rolls'::regclass) THEN
    ALTER TABLE public.rolls ADD CONSTRAINT rolls_pkey PRIMARY KEY (id);
  END IF;
EXCEPTION WHEN others THEN RAISE WARNING 'public.rolls rolls_pkey: %', SQLERRM;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE contype = 'p' AND conrelid = 'public.sheet_activity_logs'::regclass) THEN
    ALTER TABLE public.sheet_activity_logs ADD CONSTRAINT sheet_activity_logs_pkey PRIMARY KEY (id);
  END IF;
EXCEPTION WHEN others THEN RAISE WARNING 'public.sheet_activity_logs sheet_activity_logs_pkey: %', SQLERRM;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE contype = 'p' AND conrelid = 'public.sheet_change_requests'::regclass) THEN
    ALTER TABLE public.sheet_change_requests ADD CONSTRAINT sheet_change_requests_pkey PRIMARY KEY (id);
  END IF;
EXCEPTION WHEN others THEN RAISE WARNING 'public.sheet_change_requests sheet_change_requests_pkey: %', SQLERRM;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE contype = 'p' AND conrelid = 'public.sheet_images'::regclass) THEN
    ALTER TABLE public.sheet_images ADD CONSTRAINT sheet_images_pkey PRIMARY KEY (id);
  END IF;
EXCEPTION WHEN others THEN RAISE WARNING 'public.sheet_images sheet_images_pkey: %', SQLERRM;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'sheet_images_sheet_id_kind_key' AND conrelid = 'public.sheet_images'::regclass) THEN
    ALTER TABLE public.sheet_images ADD CONSTRAINT sheet_images_sheet_id_kind_key UNIQUE (sheet_id, kind);
  END IF;
EXCEPTION WHEN others THEN RAISE WARNING 'public.sheet_images sheet_images_sheet_id_kind_key: %', SQLERRM;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE contype = 'p' AND conrelid = 'public.time_skip_activities'::regclass) THEN
    ALTER TABLE public.time_skip_activities ADD CONSTRAINT time_skip_activities_pkey PRIMARY KEY (id);
  END IF;
EXCEPTION WHEN others THEN RAISE WARNING 'public.time_skip_activities time_skip_activities_pkey: %', SQLERRM;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'time_skip_activities_time_skip_id_name_key' AND conrelid = 'public.time_skip_activities'::regclass) THEN
    ALTER TABLE public.time_skip_activities ADD CONSTRAINT time_skip_activities_time_skip_id_name_key UNIQUE (time_skip_id, name);
  END IF;
EXCEPTION WHEN others THEN RAISE WARNING 'public.time_skip_activities time_skip_activities_time_skip_id_name_key: %', SQLERRM;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE contype = 'p' AND conrelid = 'public.time_skip_days'::regclass) THEN
    ALTER TABLE public.time_skip_days ADD CONSTRAINT time_skip_days_pkey PRIMARY KEY (id);
  END IF;
EXCEPTION WHEN others THEN RAISE WARNING 'public.time_skip_days time_skip_days_pkey: %', SQLERRM;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'time_skip_days_time_skip_id_day_number_key' AND conrelid = 'public.time_skip_days'::regclass) THEN
    ALTER TABLE public.time_skip_days ADD CONSTRAINT time_skip_days_time_skip_id_day_number_key UNIQUE (time_skip_id, day_number);
  END IF;
EXCEPTION WHEN others THEN RAISE WARNING 'public.time_skip_days time_skip_days_time_skip_id_day_number_key: %', SQLERRM;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE contype = 'p' AND conrelid = 'public.time_skip_submissions'::regclass) THEN
    ALTER TABLE public.time_skip_submissions ADD CONSTRAINT time_skip_submissions_pkey PRIMARY KEY (id);
  END IF;
EXCEPTION WHEN others THEN RAISE WARNING 'public.time_skip_submissions time_skip_submissions_pkey: %', SQLERRM;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'time_skip_submissions_time_skip_id_user_id_key' AND conrelid = 'public.time_skip_submissions'::regclass) THEN
    ALTER TABLE public.time_skip_submissions ADD CONSTRAINT time_skip_submissions_time_skip_id_user_id_key UNIQUE (time_skip_id, user_id);
  END IF;
EXCEPTION WHEN others THEN RAISE WARNING 'public.time_skip_submissions time_skip_submissions_time_skip_id_user_id_key: %', SQLERRM;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE contype = 'p' AND conrelid = 'public.time_skips'::regclass) THEN
    ALTER TABLE public.time_skips ADD CONSTRAINT time_skips_pkey PRIMARY KEY (id);
  END IF;
EXCEPTION WHEN others THEN RAISE WARNING 'public.time_skips time_skips_pkey: %', SQLERRM;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE contype = 'p' AND conrelid = 'public.training_entries'::regclass) THEN
    ALTER TABLE public.training_entries ADD CONSTRAINT training_entries_pkey PRIMARY KEY (id);
  END IF;
EXCEPTION WHEN others THEN RAISE WARNING 'public.training_entries training_entries_pkey: %', SQLERRM;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uk_booking_slot' AND conrelid = 'public.bookings'::regclass) THEN
    ALTER TABLE public.bookings ADD CONSTRAINT uk_booking_slot UNIQUE (time_skip_day_id, npc_id, slot_number);
  END IF;
EXCEPTION WHEN others THEN RAISE WARNING 'public.bookings uk_booking_slot: %', SQLERRM;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'users_email_key' AND conrelid = 'public.users'::regclass) THEN
    ALTER TABLE public.users ADD CONSTRAINT users_email_key UNIQUE (email);
  END IF;
EXCEPTION WHEN others THEN RAISE WARNING 'public.users users_email_key: %', SQLERRM;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE contype = 'p' AND conrelid = 'public.users'::regclass) THEN
    ALTER TABLE public.users ADD CONSTRAINT users_pkey PRIMARY KEY (id);
  END IF;
EXCEPTION WHEN others THEN RAISE WARNING 'public.users users_pkey: %', SQLERRM;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE contype = 'p' AND conrelid = 'public.wound_marks'::regclass) THEN
    ALTER TABLE public.wound_marks ADD CONSTRAINT wound_marks_pkey PRIMARY KEY (id);
  END IF;
EXCEPTION WHEN others THEN RAISE WARNING 'public.wound_marks wound_marks_pkey: %', SQLERRM;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk1jxsuuaa3av0jiy3dykf1xf6t' AND conrelid = 'public.fight_records'::regclass) THEN
    ALTER TABLE public.fight_records ADD CONSTRAINT fk1jxsuuaa3av0jiy3dykf1xf6t FOREIGN KEY (sheet_id) REFERENCES public.character_sheets(id);
  END IF;
EXCEPTION WHEN others THEN RAISE WARNING 'public.fight_records fk1jxsuuaa3av0jiy3dykf1xf6t: %', SQLERRM;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk2e7mxiqrga0lxqnyx1i94vtfc' AND conrelid = 'public.rivals'::regclass) THEN
    ALTER TABLE public.rivals ADD CONSTRAINT fk2e7mxiqrga0lxqnyx1i94vtfc FOREIGN KEY (sheet_id) REFERENCES public.character_sheets(id);
  END IF;
EXCEPTION WHEN others THEN RAISE WARNING 'public.rivals fk2e7mxiqrga0lxqnyx1i94vtfc: %', SQLERRM;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk2gxm6fr25wixc5kw150hlha7j' AND conrelid = 'public.character_sheet_visibility'::regclass) THEN
    ALTER TABLE public.character_sheet_visibility ADD CONSTRAINT fk2gxm6fr25wixc5kw150hlha7j FOREIGN KEY (campaign_member_id) REFERENCES public.campaign_members(id);
  END IF;
EXCEPTION WHEN others THEN RAISE WARNING 'public.character_sheet_visibility fk2gxm6fr25wixc5kw150hlha7j: %', SQLERRM;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk40wvferrfkbxseku3fgk5s64s' AND conrelid = 'public.overcome_distributions'::regclass) THEN
    ALTER TABLE public.overcome_distributions ADD CONSTRAINT fk40wvferrfkbxseku3fgk5s64s FOREIGN KEY (sheet_id) REFERENCES public.character_sheets(id);
  END IF;
EXCEPTION WHEN others THEN RAISE WARNING 'public.overcome_distributions fk40wvferrfkbxseku3fgk5s64s: %', SQLERRM;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk4ukpniw1tfxl7mhgt4vhjoc6m' AND conrelid = 'public.bookings'::regclass) THEN
    ALTER TABLE public.bookings ADD CONSTRAINT fk4ukpniw1tfxl7mhgt4vhjoc6m FOREIGN KEY (time_skip_day_id) REFERENCES public.time_skip_days(id);
  END IF;
EXCEPTION WHEN others THEN RAISE WARNING 'public.bookings fk4ukpniw1tfxl7mhgt4vhjoc6m: %', SQLERRM;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk514avvtvp6jl9y1ra5k02nbvt' AND conrelid = 'public.interaction_logs'::regclass) THEN
    ALTER TABLE public.interaction_logs ADD CONSTRAINT fk514avvtvp6jl9y1ra5k02nbvt FOREIGN KEY (user_id) REFERENCES public.users(id);
  END IF;
EXCEPTION WHEN others THEN RAISE WARNING 'public.interaction_logs fk514avvtvp6jl9y1ra5k02nbvt: %', SQLERRM;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk5bxh5ccr37wl6oxi3d5q22rda' AND conrelid = 'public.notebook_drafts'::regclass) THEN
    ALTER TABLE public.notebook_drafts ADD CONSTRAINT fk5bxh5ccr37wl6oxi3d5q22rda FOREIGN KEY (sheet_id) REFERENCES public.character_sheets(id);
  END IF;
EXCEPTION WHEN others THEN RAISE WARNING 'public.notebook_drafts fk5bxh5ccr37wl6oxi3d5q22rda: %', SQLERRM;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk5f09go3q3mue1vhlwoytja3ds' AND conrelid = 'public.character_clans'::regclass) THEN
    ALTER TABLE public.character_clans ADD CONSTRAINT fk5f09go3q3mue1vhlwoytja3ds FOREIGN KEY (sheet_id) REFERENCES public.character_sheets(id);
  END IF;
EXCEPTION WHEN others THEN RAISE WARNING 'public.character_clans fk5f09go3q3mue1vhlwoytja3ds: %', SQLERRM;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk5w3coo2tkrjs1f79wfsomlguc' AND conrelid = 'public.inventory_items'::regclass) THEN
    ALTER TABLE public.inventory_items ADD CONSTRAINT fk5w3coo2tkrjs1f79wfsomlguc FOREIGN KEY (sheet_id) REFERENCES public.character_sheets(id);
  END IF;
EXCEPTION WHEN others THEN RAISE WARNING 'public.inventory_items fk5w3coo2tkrjs1f79wfsomlguc: %', SQLERRM;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk6g0h51jps5i325wggmq063ayb' AND conrelid = 'public.character_arts'::regclass) THEN
    ALTER TABLE public.character_arts ADD CONSTRAINT fk6g0h51jps5i325wggmq063ayb FOREIGN KEY (sheet_id) REFERENCES public.character_sheets(id);
  END IF;
EXCEPTION WHEN others THEN RAISE WARNING 'public.character_arts fk6g0h51jps5i325wggmq063ayb: %', SQLERRM;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk7grcq9jrxw98cuxhbytyi4p1v' AND conrelid = 'public.character_sheets'::regclass) THEN
    ALTER TABLE public.character_sheets ADD CONSTRAINT fk7grcq9jrxw98cuxhbytyi4p1v FOREIGN KEY (campaign_member_id) REFERENCES public.campaign_members(id);
  END IF;
EXCEPTION WHEN others THEN RAISE WARNING 'public.character_sheets fk7grcq9jrxw98cuxhbytyi4p1v: %', SQLERRM;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk896heugd33cafvukpl8e37blv' AND conrelid = 'public.character_techniques'::regclass) THEN
    ALTER TABLE public.character_techniques ADD CONSTRAINT fk896heugd33cafvukpl8e37blv FOREIGN KEY (sheet_id) REFERENCES public.character_sheets(id);
  END IF;
EXCEPTION WHEN others THEN RAISE WARNING 'public.character_techniques fk896heugd33cafvukpl8e37blv: %', SQLERRM;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk8h4ll005ofk5yyl7u9mu50dj1' AND conrelid = 'public.campaigns'::regclass) THEN
    ALTER TABLE public.campaigns ADD CONSTRAINT fk8h4ll005ofk5yyl7u9mu50dj1 FOREIGN KEY (master_id) REFERENCES public.users(id);
  END IF;
EXCEPTION WHEN others THEN RAISE WARNING 'public.campaigns fk8h4ll005ofk5yyl7u9mu50dj1: %', SQLERRM;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fkbfu8xwgoi73w9xmfos58e4wvy' AND conrelid = 'public.campaign_members'::regclass) THEN
    ALTER TABLE public.campaign_members ADD CONSTRAINT fkbfu8xwgoi73w9xmfos58e4wvy FOREIGN KEY (campaign_id) REFERENCES public.campaigns(id);
  END IF;
EXCEPTION WHEN others THEN RAISE WARNING 'public.campaign_members fkbfu8xwgoi73w9xmfos58e4wvy: %', SQLERRM;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fkbmjc3jlvsg2m0rwsb5u2c0i9c' AND conrelid = 'public.training_entries'::regclass) THEN
    ALTER TABLE public.training_entries ADD CONSTRAINT fkbmjc3jlvsg2m0rwsb5u2c0i9c FOREIGN KEY (sheet_id) REFERENCES public.character_sheets(id);
  END IF;
EXCEPTION WHEN others THEN RAISE WARNING 'public.training_entries fkbmjc3jlvsg2m0rwsb5u2c0i9c: %', SQLERRM;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fkcy04x8ggte1m6tets17xcd577' AND conrelid = 'public.wound_marks'::regclass) THEN
    ALTER TABLE public.wound_marks ADD CONSTRAINT fkcy04x8ggte1m6tets17xcd577 FOREIGN KEY (sheet_id) REFERENCES public.character_sheets(id);
  END IF;
EXCEPTION WHEN others THEN RAISE WARNING 'public.wound_marks fkcy04x8ggte1m6tets17xcd577: %', SQLERRM;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fkdmqix515oth7dw7xr1m4c9wcn' AND conrelid = 'public.character_builds'::regclass) THEN
    ALTER TABLE public.character_builds ADD CONSTRAINT fkdmqix515oth7dw7xr1m4c9wcn FOREIGN KEY (sheet_id) REFERENCES public.character_sheets(id);
  END IF;
EXCEPTION WHEN others THEN RAISE WARNING 'public.character_builds fkdmqix515oth7dw7xr1m4c9wcn: %', SQLERRM;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fkdoq46jmdxbqtfls21dpo9rkem' AND conrelid = 'public.forges'::regclass) THEN
    ALTER TABLE public.forges ADD CONSTRAINT fkdoq46jmdxbqtfls21dpo9rkem FOREIGN KEY (sheet_id) REFERENCES public.character_sheets(id);
  END IF;
EXCEPTION WHEN others THEN RAISE WARNING 'public.forges fkdoq46jmdxbqtfls21dpo9rkem: %', SQLERRM;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fkeyog2oic85xg7hsu2je2lx3s6' AND conrelid = 'public.bookings'::regclass) THEN
    ALTER TABLE public.bookings ADD CONSTRAINT fkeyog2oic85xg7hsu2je2lx3s6 FOREIGN KEY (user_id) REFERENCES public.users(id);
  END IF;
EXCEPTION WHEN others THEN RAISE WARNING 'public.bookings fkeyog2oic85xg7hsu2je2lx3s6: %', SQLERRM;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fkfldoor4dto1b2vyt7yb0k1yuw' AND conrelid = 'public.campaign_rule_settings'::regclass) THEN
    ALTER TABLE public.campaign_rule_settings ADD CONSTRAINT fkfldoor4dto1b2vyt7yb0k1yuw FOREIGN KEY (campaign_id) REFERENCES public.campaigns(id);
  END IF;
EXCEPTION WHEN others THEN RAISE WARNING 'public.campaign_rule_settings fkfldoor4dto1b2vyt7yb0k1yuw: %', SQLERRM;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fkfubxpwsspnxg7m8ucu9x4af3w' AND conrelid = 'public.time_skips'::regclass) THEN
    ALTER TABLE public.time_skips ADD CONSTRAINT fkfubxpwsspnxg7m8ucu9x4af3w FOREIGN KEY (campaign_id) REFERENCES public.campaigns(id);
  END IF;
EXCEPTION WHEN others THEN RAISE WARNING 'public.time_skips fkfubxpwsspnxg7m8ucu9x4af3w: %', SQLERRM;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fkgw0mnv8ehyji5n5r64w1xxmly' AND conrelid = 'public.interaction_logs'::regclass) THEN
    ALTER TABLE public.interaction_logs ADD CONSTRAINT fkgw0mnv8ehyji5n5r64w1xxmly FOREIGN KEY (booking_id) REFERENCES public.bookings(id);
  END IF;
EXCEPTION WHEN others THEN RAISE WARNING 'public.interaction_logs fkgw0mnv8ehyji5n5r64w1xxmly: %', SQLERRM;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fkh05f9qnyl3tq13q0gdo13e55v' AND conrelid = 'public.campaign_members'::regclass) THEN
    ALTER TABLE public.campaign_members ADD CONSTRAINT fkh05f9qnyl3tq13q0gdo13e55v FOREIGN KEY (user_id) REFERENCES public.users(id);
  END IF;
EXCEPTION WHEN others THEN RAISE WARNING 'public.campaign_members fkh05f9qnyl3tq13q0gdo13e55v: %', SQLERRM;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fki1lfyc5yi5wvgcwanv3dyyno6' AND conrelid = 'public.permanent_injuries'::regclass) THEN
    ALTER TABLE public.permanent_injuries ADD CONSTRAINT fki1lfyc5yi5wvgcwanv3dyyno6 FOREIGN KEY (sheet_id) REFERENCES public.character_sheets(id);
  END IF;
EXCEPTION WHEN others THEN RAISE WARNING 'public.permanent_injuries fki1lfyc5yi5wvgcwanv3dyyno6: %', SQLERRM;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fkis1s4medtb2trlp2d8ekfmj5v' AND conrelid = 'public.character_techniques'::regclass) THEN
    ALTER TABLE public.character_techniques ADD CONSTRAINT fkis1s4medtb2trlp2d8ekfmj5v FOREIGN KEY (character_art_id) REFERENCES public.character_arts(id);
  END IF;
EXCEPTION WHEN others THEN RAISE WARNING 'public.character_techniques fkis1s4medtb2trlp2d8ekfmj5v: %', SQLERRM;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fkjwyespnqajqoab6h31ydiw1kb' AND conrelid = 'public.character_instincts'::regclass) THEN
    ALTER TABLE public.character_instincts ADD CONSTRAINT fkjwyespnqajqoab6h31ydiw1kb FOREIGN KEY (sheet_id) REFERENCES public.character_sheets(id);
  END IF;
EXCEPTION WHEN others THEN RAISE WARNING 'public.character_instincts fkjwyespnqajqoab6h31ydiw1kb: %', SQLERRM;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fkoewaicbiw7ivgif2b8219wm4s' AND conrelid = 'public.campaign_npcs'::regclass) THEN
    ALTER TABLE public.campaign_npcs ADD CONSTRAINT fkoewaicbiw7ivgif2b8219wm4s FOREIGN KEY (campaign_id) REFERENCES public.campaigns(id);
  END IF;
EXCEPTION WHEN others THEN RAISE WARNING 'public.campaign_npcs fkoewaicbiw7ivgif2b8219wm4s: %', SQLERRM;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fkon08umsfc31tko2gabxvj6h35' AND conrelid = 'public.interaction_logs'::regclass) THEN
    ALTER TABLE public.interaction_logs ADD CONSTRAINT fkon08umsfc31tko2gabxvj6h35 FOREIGN KEY (campaign_id) REFERENCES public.campaigns(id);
  END IF;
EXCEPTION WHEN others THEN RAISE WARNING 'public.interaction_logs fkon08umsfc31tko2gabxvj6h35: %', SQLERRM;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fkoxn2w1dmwjppeck8gglv40txr' AND conrelid = 'public.time_skip_activities'::regclass) THEN
    ALTER TABLE public.time_skip_activities ADD CONSTRAINT fkoxn2w1dmwjppeck8gglv40txr FOREIGN KEY (time_skip_id) REFERENCES public.time_skips(id);
  END IF;
EXCEPTION WHEN others THEN RAISE WARNING 'public.time_skip_activities fkoxn2w1dmwjppeck8gglv40txr: %', SQLERRM;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fkp41bq33q73w9ljsk2lcojq3pd' AND conrelid = 'public.npc_sheet_visibility'::regclass) THEN
    ALTER TABLE public.npc_sheet_visibility ADD CONSTRAINT fkp41bq33q73w9ljsk2lcojq3pd FOREIGN KEY (campaign_npc_id) REFERENCES public.campaign_npcs(id);
  END IF;
EXCEPTION WHEN others THEN RAISE WARNING 'public.npc_sheet_visibility fkp41bq33q73w9ljsk2lcojq3pd: %', SQLERRM;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fkrr1b5p0n7xf5fpurujqngq08r' AND conrelid = 'public.time_skip_days'::regclass) THEN
    ALTER TABLE public.time_skip_days ADD CONSTRAINT fkrr1b5p0n7xf5fpurujqngq08r FOREIGN KEY (time_skip_id) REFERENCES public.time_skips(id);
  END IF;
EXCEPTION WHEN others THEN RAISE WARNING 'public.time_skip_days fkrr1b5p0n7xf5fpurujqngq08r: %', SQLERRM;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fkt54d0prmpt3po898wfuhgeq5q' AND conrelid = 'public.character_traits'::regclass) THEN
    ALTER TABLE public.character_traits ADD CONSTRAINT fkt54d0prmpt3po898wfuhgeq5q FOREIGN KEY (sheet_id) REFERENCES public.character_sheets(id);
  END IF;
EXCEPTION WHEN others THEN RAISE WARNING 'public.character_traits fkt54d0prmpt3po898wfuhgeq5q: %', SQLERRM;
END $$;

COMMIT;
