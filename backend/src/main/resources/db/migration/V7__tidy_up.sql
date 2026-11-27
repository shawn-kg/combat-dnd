-- Tidying up the gaps listed in the guide's Chapter 1 ("Honest gaps").

-- 1. Indexes for lookups the app makes, and for foreign keys that cascading deletes have to search.
--    Postgres indexes primary keys and unique columns by itself, but not the column that points at another table.
create index encounter_campaign_idx on encounter (campaign_id);                    -- a campaign's encounters
create index monster_template_campaign_idx on monster_template (campaign_id);      -- a campaign's monsters
create index encounter_party_character_idx on encounter_party (character_id);      -- deleting a character
create index encounter_enemy_template_idx on encounter_enemy (monster_template_id); -- deleting a monster
create index encounter_npc_npc_idx on encounter_npc (npc_id);                      -- deleting an NPC

-- 2. The "contract" half of V4: nothing has read or written this since DM devices moved to dm_device.
alter table campaign drop column dm_token_hash;

-- 3. Portraits get real links.
--    a) Each portrait belongs to a campaign (its key starts with the campaign id), so deleting a campaign
--       deletes its pictures. Pictures of campaigns that no longer exist are removed first.
--       campaign_id stays optional for now ("expand"): while this version deploys, the previous one is still
--       serving and saves portraits without it. The code from this version always fills it in; a later
--       migration can backfill any gaps and make it not null ("contract").
delete from portrait p where not exists (select 1 from campaign c where c.id::text = split_part(p.key, '/', 1));
alter table portrait add column campaign_id uuid;
update portrait set campaign_id = split_part(key, '/', 1)::uuid;
alter table portrait add constraint portrait_campaign_id_fkey foreign key (campaign_id) references campaign (id) on delete cascade;
create index portrait_campaign_idx on portrait (campaign_id);

--    b) portrait_key must point at a real picture. Keys that point at nothing (e.g. local pictures saved as
--       files before V3) are cleared first, so the constraint can be added; those show initials instead.
update character set portrait_key = null where portrait_key is not null and not exists (select 1 from portrait p where p.key = portrait_key);
update npc set portrait_key = null where portrait_key is not null and not exists (select 1 from portrait p where p.key = portrait_key);
update monster_template set portrait_key = null where portrait_key is not null and not exists (select 1 from portrait p where p.key = portrait_key);
alter table character add constraint character_portrait_key_fkey foreign key (portrait_key) references portrait (key) on delete set null;
alter table npc add constraint npc_portrait_key_fkey foreign key (portrait_key) references portrait (key) on delete set null;
alter table monster_template add constraint monster_template_portrait_key_fkey foreign key (portrait_key) references portrait (key) on delete set null;

-- 4. V1 describes portrait_key as an "object-storage key". Applied migrations can't be edited (Flyway checks
--    their checksums), so the up-to-date description is stored in the database itself: see \d+ character.
comment on column character.portrait_key is 'A picture in the portrait table (<campaign id>/<random>.jpg); null shows initials.';
comment on column npc.portrait_key is 'A picture in the portrait table (<campaign id>/<random>.jpg); null shows initials.';
comment on column monster_template.portrait_key is 'A picture in the portrait table (<campaign id>/<random>.jpg); null shows initials.';
