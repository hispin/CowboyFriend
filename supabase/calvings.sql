-- Calvings: one row for every calving, and how it went (a hard calving is watched for 3 weeks, see the rule hard_calving).
-- Run it once in the Supabase SQL editor, after tours_schema.sql (it uses the ranch functions of that file).
-- The app works also without it: the calf is saved as before, only the difficulty is not recorded.
--
-- difficulty: 0 no help, 1 an easy pull, 2 a hard pull, 3 a caesarean, 4 a calf in a wrong position
-- (the numbers are the ones of CalvingDifficulty.kt in the app: do not change them)

create table if not exists public.calvings (
  id          uuid primary key default gen_random_uuid(),
  ranch_id    uuid not null default public.my_ranch_id() references public.ranches(id),
  mother_id   bigint not null references public."CowDetails"(id) on delete cascade,
  calf_id     bigint references public."CowDetails"(id) on delete set null,
  calved_at   timestamptz not null default now(),
  difficulty  smallint not null default 0 check (difficulty between 0 and 4),
  recorded_by text not null default public.auth_email(),
  created_at  timestamptz not null default now()
);
-- how the calf was born: 'alive' (a calf that is registered in the app) or 'dead' (a calf that was born dead and has no row
-- in CowDetails, recorded from the edit window of the cow). a separate statement, so it also fits a table that exists already
alter table public.calvings
  add column if not exists calf_outcome text not null default 'alive' check (calf_outcome in ('alive', 'dead'));

create index if not exists calvings_mother_idx on public.calvings (mother_id, calved_at desc);
create index if not exists calvings_ranch_idx  on public.calvings (ranch_id, calved_at desc);

-- ---------------------------------------------------------------------------------------------------------------------
-- permissions: members of the ranch read and write the calvings of their ranch, a coordinator / owner may also delete
-- ---------------------------------------------------------------------------------------------------------------------
alter table public.calvings enable row level security;

drop policy if exists calvings_select on public.calvings;
create policy calvings_select on public.calvings for select to authenticated
  using (public.is_ranch_member(ranch_id));
drop policy if exists calvings_insert on public.calvings;
create policy calvings_insert on public.calvings for insert to authenticated
  with check (public.is_ranch_member(ranch_id) and recorded_by = public.auth_email());
drop policy if exists calvings_update on public.calvings;
create policy calvings_update on public.calvings for update to authenticated
  using (recorded_by = public.auth_email() or public.is_ranch_coordinator(ranch_id))
  with check (public.is_ranch_member(ranch_id));
drop policy if exists calvings_delete on public.calvings;
create policy calvings_delete on public.calvings for delete to authenticated
  using (public.is_ranch_coordinator(ranch_id));

-- the table GRANTs (a policy is not enough without them, error 42501)
grant select, insert, update, delete on public.calvings to authenticated;

-- ---------------------------------------------------------------------------------------------------------------------
-- the rule "hard_calving" in the view cow_status: a cow with a hard calving (difficulty 2 or more) in the last 21 days
-- needs attention, so the tour summary and the ranch dashboard agree with the list of the app (CowIssues.kt).
-- this is the view of tours_schema.sql with one more line in the list of the issues; the columns are the same
-- ---------------------------------------------------------------------------------------------------------------------
create or replace view public.cow_status with (security_invoker = true) as
select
  c.id, c.ranch_id, c.number, c.number_mom, c.gender, c.cow_type, k.kind, k.dead,
  c.marked_tag, c.with_mom, c.last_seen_at, c.latitude, c.longitude, c.num_of_calvings, c.health_issues,
  c.created_at, c.updated_at,
  extract(year from c.created_at)::int as birth_year,
  case when c.last_seen_at is null then null
       else floor((extract(epoch from now()) * 1000 - c.last_seen_at) / 86400000)::int end as days_since_seen,
  array_remove(array[
    -- a calf that was not seen together with its mother at its last visit
    case when not k.dead and k.kind = 'calf' and not coalesce(c.with_mom = c.last_seen_at, false)
         then 'calf_without_mom' end,
    case when not k.dead and k.kind = 'calf' and not coalesce(c.marked_tag, false)
         then 'calf_without_tag' end,
    case when not k.dead and (c.latitude is null or c.longitude is null)
         then 'no_location' end,
    case when not k.dead and c.last_seen_at is null
         then 'never_seen' end,
    -- the cow has at least one health problem (see supabase/health_issues.sql). "pregnant" is only a state of the cow, not a
    -- problem, so a cow with only that is not counted (HealthIssues.NOT_PROBLEMS in the app)
    case when not k.dead and exists (
           select 1 from jsonb_array_elements_text(coalesce(c.health_issues, '[]'::jsonb)) as h(entry)
           where h.entry <> 'pregnant'
         ) then 'health_issue' end,
    -- the last calving of the cow was hard (a hard pull or worse) and it was less than 21 days ago
    case when not k.dead and exists (
           select 1 from public.calvings v
           where v.mother_id = c.id and v.difficulty >= 2 and v.calved_at > now() - interval '21 days'
         ) then 'hard_calving' end,
    -- not seen for 14 days or more: change the number here to change the rule
    case when not k.dead and c.last_seen_at is not null
              and (extract(epoch from now()) * 1000 - c.last_seen_at) / 86400000 >= 14
         then 'not_seen_14d' end
  ], null) as issues
from public."CowDetails" c
cross join lateral (
  select public.cow_kind(c.cow_type) as kind, coalesce(c.corpse, false) as dead
) k;
