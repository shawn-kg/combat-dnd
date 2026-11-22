-- Combat D&D initial schema: a DM-run initiative tracker. See docs/design.md, "Data model".

create table campaign (
    id            uuid primary key default gen_random_uuid(),
    name          text        not null,
    join_code     text        not null unique,
    dm_token_hash text        not null,
    created_at    timestamptz not null default now()
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

-- Created by the DM. A player claims one once (player_id goes from null to theirs); after that only the DM changes it.
-- HP lives here so damage carries over between fights and sessions.
create table character (
    id           uuid primary key default gen_random_uuid(),
    campaign_id  uuid not null references campaign (id) on delete cascade,
    player_id    uuid unique references player (id) on delete set null,
    name         text not null,
    portrait_key text, -- object-storage key; null shows initials
    ac           int  not null check (ac >= 0),
    max_hp       int  not null check (max_hp > 0),
    current_hp   int  not null check (current_hp >= 0)
);
create index character_campaign_idx on character (campaign_id);

create table monster_template (
    id           uuid primary key default gen_random_uuid(),
    campaign_id  uuid not null references campaign (id) on delete cascade,
    name         text not null,
    portrait_key text,
    ac           int  not null check (ac >= 0),
    max_hp       int  not null check (max_hp > 0)
);

create table encounter (
    id                   uuid primary key default gen_random_uuid(),
    campaign_id          uuid        not null references campaign (id) on delete cascade,
    name                 text        not null,
    status               text        not null default 'PREPARING' check (status in ('PREPARING', 'ACTIVE', 'ENDED')),
    round                int         not null default 0,
    current_combatant_id uuid,
    version              bigint      not null default 0,
    created_at           timestamptz not null default now()
);

create table combatant (
    id                  uuid primary key default gen_random_uuid(),
    encounter_id        uuid    not null references encounter (id) on delete cascade,
    side                text    not null check (side in ('PARTY', 'ENEMY')),
    character_id        uuid    references character (id) on delete cascade,
    monster_template_id uuid    references monster_template (id) on delete set null,
    name                text    not null,
    portrait_key        text,
    initiative          int     not null default 0,
    sort_order          int     not null default 0,
    ac                  int, -- enemies only; party members use character
    max_hp              int,
    current_hp          int,
    hidden              boolean not null default false,
    defeated            boolean not null default false
);
create index combatant_encounter_idx on combatant (encounter_id);

alter table encounter
    add constraint encounter_current_combatant_fk
        foreign key (current_combatant_id) references combatant (id) on delete set null;

-- Append-only: attacks logged, edits, turn changes. Last-turn summaries and undo come from here.
create table encounter_event (
    encounter_id uuid        not null references encounter (id) on delete cascade,
    seq          bigint      not null,
    actor_role   text        not null check (actor_role in ('DM', 'PLAYER')),
    actor_id     uuid,
    type         text        not null,
    payload      jsonb       not null,
    undone       boolean     not null default false,
    created_at   timestamptz not null default now(),
    primary key (encounter_id, seq)
);
