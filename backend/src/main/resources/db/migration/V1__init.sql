-- Combat D&D initial schema. See the design doc's "Data model" section.

create table campaign (
    id                uuid primary key default gen_random_uuid(),
    name              text        not null,
    join_code         text        not null unique,
    dm_token_hash     text        not null,
    allow_early_rolls boolean     not null default true,
    created_at        timestamptz not null default now()
);

create table player (
    id           uuid primary key default gen_random_uuid(),
    campaign_id  uuid        not null references campaign (id) on delete cascade,
    display_name text        not null,
    token_hash   text,
    status       text        not null default 'PENDING' check (status in ('PENDING', 'APPROVED', 'REMOVED')),
    created_at   timestamptz not null default now()
);
create index player_campaign_idx on player (campaign_id);

-- Persistent characters. HP lives here so damage carries over between fights and sessions.
-- attack_presets: [{name, kind: ATTACK|SPELL|OTHER, defaultCost: ACTION|BONUS|REACTION|FREE, note}]
-- reactions:      [{name, acBonus?, halveDamage?, mode: ASK|NEVER|AUTO_IF_TURNS_MISS}]
-- conditions:     [{name, private}]
create table character (
    id                 uuid primary key default gen_random_uuid(),
    campaign_id        uuid    not null references campaign (id) on delete cascade,
    player_id          uuid    references player (id) on delete set null, -- null = DM-controlled
    name               text    not null,
    kind               text    not null default 'PC' check (kind in ('PC', 'NPC')),
    max_hp             int     not null check (max_hp > 0),
    current_hp         int     not null check (current_hp >= 0),
    temp_hp            int     not null default 0 check (temp_hp >= 0),
    ac                 int     not null,
    speed              int     not null default 30,
    initiative_bonus   int     not null default 0,
    attacks_per_action int     not null default 1,
    conditions         jsonb   not null default '[]',
    attack_presets     jsonb   not null default '[]',
    reactions          jsonb   not null default '[]',
    notes              text    not null default ''
);
create index character_campaign_idx on character (campaign_id);

create table monster_template (
    id               uuid primary key default gen_random_uuid(),
    campaign_id      uuid  not null references campaign (id) on delete cascade,
    name             text  not null,
    max_hp           int   not null check (max_hp > 0),
    ac               int   not null,
    speed            int   not null default 30,
    initiative_bonus int   not null default 0,
    multiattack      int   not null default 1,
    attack_presets   jsonb not null default '[]',
    reactions        jsonb not null default '[]',
    notes            text  not null default ''
);

create table encounter (
    id                   uuid primary key default gen_random_uuid(),
    campaign_id          uuid    not null references campaign (id) on delete cascade,
    name                 text    not null,
    status               text    not null default 'PREPARING'
        check (status in ('PREPARING', 'ROLLING_INITIATIVE', 'ACTIVE', 'ENDED')),
    round                int     not null default 0,
    current_combatant_id uuid,
    version              bigint  not null default 0,
    notes                text    not null default '', -- DM only
    auto_add_party       boolean not null default true,
    created_at           timestamptz not null default now()
);

create table combatant (
    id                  uuid primary key default gen_random_uuid(),
    encounter_id        uuid    not null references encounter (id) on delete cascade,
    side                text    not null check (side in ('PARTY', 'ENEMY', 'NEUTRAL')),
    character_id        uuid    references character (id) on delete cascade,
    monster_template_id uuid    references monster_template (id) on delete set null,
    name                text    not null,
    initiative          int,
    sort_order          int     not null default 0,
    max_hp              int, -- monsters only; PCs use character
    current_hp          int,
    ac                  int,
    ac_bonus            int     not null default 0,
    conditions          jsonb   not null default '[]',
    hidden              boolean not null default false,
    defeated            boolean not null default false,
    action_used         boolean not null default false,
    bonus_used          boolean not null default false,
    reaction_used       boolean not null default false,
    movement_used       int     not null default 0,
    dashed              boolean not null default false,
    attacks_this_turn   int     not null default 0
);
create index combatant_encounter_idx on combatant (encounter_id);

alter table encounter
    add constraint encounter_current_combatant_fk
        foreign key (current_combatant_id) references combatant (id) on delete set null;

-- Append-only. The combat log, undo and audit history all come from here.
create table encounter_event (
    encounter_id uuid        not null references encounter (id) on delete cascade,
    seq          bigint      not null,
    actor_role   text        not null check (actor_role in ('DM', 'PLAYER', 'SYSTEM')),
    actor_id     uuid,
    type         text        not null,
    payload      jsonb       not null,
    undone       boolean     not null default false,
    created_at   timestamptz not null default now(),
    primary key (encounter_id, seq)
);

-- Plans are drafts, not things that happened, so they live outside the event log.
create table turn_plan (
    encounter_id uuid        not null references encounter (id) on delete cascade,
    combatant_id uuid        not null references combatant (id) on delete cascade,
    steps        jsonb       not null default '[]',
    note         text        not null default '',
    ready        boolean     not null default false,
    updated_at   timestamptz not null default now(),
    primary key (encounter_id, combatant_id)
);
