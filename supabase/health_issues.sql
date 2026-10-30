-- =====================================================================================================================
-- Health problems of a cow: the column "health_issues" in "CowDetails"
--
-- run this in the Supabase SQL editor BEFORE saving health problems from the app. the app works without it (the rest of the
-- edit window saves), but saving a health problem fails until the column exists.
--
-- the value is a json array of codes: ["lameness", "swollen_udder", "other:limping since yesterday"]
-- the codes: lameness, swollen_udder, eye, respiratory, diarrhea, bloat, wound_skin, thin, lethargic, parasites, calving,
--            weak_calf, and "other" / "other:<free text>"
-- (the list is defined in the app, in HealthIssues.kt; the column accepts any code)
-- =====================================================================================================================

alter table public."CowDetails"
  add column if not exists health_issues jsonb default '[]'::jsonb;

-- a quick filter: the cows that have a problem of a code, for example lameness
--   select number, health_issues from public."CowDetails" where health_issues ? 'lameness';
-- the cows that have any health problem
--   select number, health_issues from public."CowDetails" where jsonb_array_length(coalesce(health_issues, '[]')) > 0;

-- if the old row level security policies of "CowDetails" allow updating only some columns, add this column to them
-- (a normal "for update" policy needs no change)

-- ---------------------------------------------------------------------------------------------------------------------
-- if you also use supabase/tours_schema.sql: the tour log and the alerts understand the column. run that file after this one
-- ---------------------------------------------------------------------------------------------------------------------
