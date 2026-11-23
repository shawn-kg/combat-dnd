-- Portraits are 256 x 256 JPEGs of a few KB, so they live in Postgres: no separate storage service, and
-- they're in every backup. The free web host's disk is wiped on each restart, so a local folder won't do.
create table portrait (
    key        text primary key, -- <campaign id>/<random>.jpg
    bytes      bytea       not null,
    created_at timestamptz not null default now()
);
