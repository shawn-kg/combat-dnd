-- A DM can be signed in on several devices. Each device has its own secret (only its hash is stored),
-- so one can be signed out without the others. campaign.dm_token_hash was the first device; it moves here.
create table dm_device (
    id          uuid primary key default gen_random_uuid(),
    campaign_id uuid        not null references campaign (id) on delete cascade,
    token_hash  text        not null unique,
    created_at  timestamptz not null default now()
);
create index dm_device_campaign_idx on dm_device (campaign_id);
insert into dm_device (campaign_id, token_hash) select id, dm_token_hash from campaign;

-- The DM's sign-in link: opening it signs that browser in as a new DM device. Only its hash is kept,
-- so it can't be shown again; making a new one replaces it. Null until the DM makes one.
alter table campaign add column dm_link_hash text unique;

-- No longer read; kept so the column's NOT NULL insert in older code paths can't fail.
alter table campaign alter column dm_token_hash drop not null;
