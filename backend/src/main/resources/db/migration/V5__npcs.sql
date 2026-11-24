-- NPCs: the DM's other people, neither party members nor monsters. Like characters, they're individuals
-- whose HP carries over between fights; unlike characters, the DM runs them and decides whether players
-- see their AC and HP.
create table npc (
    id            uuid primary key default gen_random_uuid(),
    campaign_id   uuid        not null references campaign (id) on delete cascade,
    name          text        not null,
    portrait_key  text,
    ac            int         not null check (ac >= 0),
    max_hp        int         not null check (max_hp > 0),
    current_hp    int         not null check (current_hp >= 0),
    stats_visible boolean     not null default false,
    created_at    timestamptz not null default now()
);
create index npc_campaign_idx on npc (campaign_id);

-- NPCs in an encounter's saved roster. Opt-in: a row means the NPC is in the fight.
create table encounter_npc (
    encounter_id uuid    not null references encounter (id) on delete cascade,
    npc_id       uuid    not null references npc (id) on delete cascade,
    hidden       boolean not null default false,
    sort_order   int     not null default 0,
    primary key (encounter_id, npc_id)
);
