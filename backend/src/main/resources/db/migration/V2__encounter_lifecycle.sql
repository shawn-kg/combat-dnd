-- Start combat writes the roster as the fight's first batch. Undo must not go back past it, or the tracker
-- would be emptied while the encounter is already under way. Batches up to this number can't be undone.
alter table encounter add column locked_batch bigint not null default 0;
