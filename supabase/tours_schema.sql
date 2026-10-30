-- =====================================================================================================================
-- CowboyFriend: ranches (several cowboys), tours, visit history, alerts and tour summary
--
-- PROPOSAL: nothing in the app uses this file yet. run it in the Supabase SQL editor, step by step, after reading it.
-- the migration is written so the CURRENT app keeps working while it runs (new columns are nullable / have defaults, and
-- triggers fill the new columns for the old app).
--
-- assumptions (please check before running):
--   * the table "CowDetails" has a primary key column "id" (int / bigint) and the columns of CowDto
--     (number, number_mom, gender, cow_type, corpse, marked_tag, with_mom, last_seen_at, latitude, longitude, comment,
--      num_of_calvings, user_id, created_at). last_seen_at / with_mom / location_updated_at are epoch MILLISECONDS (bigint).
--   * cow_type / gender are saved as the Hebrew texts (see CowLabels in the app); English texts are understood too.
--   * the column "CowDetails".health_issues exists (run supabase/health_issues.sql first).
--   * the table "UsersInfo" has the columns email and name (the farm name).
-- check the type of "CowDetails".id:
--   select data_type from information_schema.columns where table_name = 'CowDetails' and column_name = 'id';
-- =====================================================================================================================


-- ---------------------------------------------------------------------------------------------------------------------
-- 1. helpers
-- ---------------------------------------------------------------------------------------------------------------------

-- the e-mail of the logged in user (lower case), null when there is no session (SQL editor / service role)
create or replace function public.auth_email() returns text
language sql stable as $$
  select lower(auth.jwt() ->> 'email')
$$;

-- generic "touch" trigger: keeps updated_at fresh (the SERVER clock, not the clock of the phone)
create or replace function public.set_updated_at() returns trigger
language plpgsql as $$
begin
  new.updated_at := now();
  return new;
end
$$;

-- the cow type texts (Hebrew as saved today, or English) -> one code, so reports do not depend on the language
create or replace function public.cow_kind(p_type text) returns text
language sql immutable as $$
  select case lower(btrim(coalesce(p_type, '')))
    when 'עגל'       then 'calf'      when 'calf'      then 'calf'
    when 'מבכירה'    then 'first_cow' when 'first-cow' then 'first_cow'
    when 'פרה'       then 'cow'       when 'cow'       then 'cow'
    when 'שור'       then 'bull'      when 'bull'      then 'bull'
    else 'unknown'
  end
$$;


-- ---------------------------------------------------------------------------------------------------------------------
-- 2. ranches and their members (a coordinator sees the data of all the cowboys of the ranch)
-- ---------------------------------------------------------------------------------------------------------------------

create table if not exists public.ranches (
  id           uuid primary key default gen_random_uuid(),
  name         text not null,
  legacy_email text unique,              -- only for the migration of the old one-user-per-farm data
  created_at   timestamptz not null default now()
);

create table if not exists public.ranch_members (
  ranch_id   uuid not null references public.ranches(id) on delete cascade,
  user_email text not null,              -- lower case, the same e-mail as the login
  role       text not null default 'cowboy' check (role in ('cowboy', 'coordinator', 'owner')),
  created_at timestamptz not null default now(),
  primary key (ranch_id, user_email)
);
create index if not exists ranch_members_email_idx on public.ranch_members (lower(user_email));

-- is the logged in user a member of the ranch / a coordinator (or owner) of it.
-- SECURITY DEFINER so the policies of ranch_members itself do not call themselves forever
create or replace function public.is_ranch_member(p_ranch uuid) returns boolean
language sql stable security definer set search_path = public as $$
  select exists (
    select 1 from public.ranch_members m
    where m.ranch_id = p_ranch and lower(m.user_email) = public.auth_email()
  )
$$;

create or replace function public.is_ranch_coordinator(p_ranch uuid) returns boolean
language sql stable security definer set search_path = public as $$
  select exists (
    select 1 from public.ranch_members m
    where m.ranch_id = p_ranch and lower(m.user_email) = public.auth_email()
      and m.role in ('coordinator', 'owner')
  )
$$;

-- the ranch of the logged in user (the first one, owners first); a cowboy normally belongs to one ranch
create or replace function public.my_ranch_id() returns uuid
language sql stable security definer set search_path = public as $$
  select m.ranch_id from public.ranch_members m
  where lower(m.user_email) = public.auth_email()
  order by (m.role = 'owner') desc, m.created_at
  limit 1
$$;

-- migration of the existing data: one ranch for every farm (UsersInfo.email) and for every user_id found in the cows
insert into public.ranches (name, legacy_email)
select coalesce(nullif(btrim(u.name), ''), lower(u.email)), lower(u.email)
from public."UsersInfo" u
where u.email is not null
on conflict (legacy_email) do nothing;

insert into public.ranches (name, legacy_email)
select distinct lower(c.user_id), lower(c.user_id)
from public."CowDetails" c
where c.user_id is not null
on conflict (legacy_email) do nothing;

insert into public.ranch_members (ranch_id, user_email, role)
select r.id, r.legacy_email, 'owner'
from public.ranches r
where r.legacy_email is not null
on conflict do nothing;

-- to add another cowboy to a ranch (example, run by hand):
--   insert into public.ranch_members (ranch_id, user_email, role)
--   values ('<ranch uuid>', 'cowboy2@example.com', 'cowboy');      -- role: cowboy | coordinator | owner


-- ---------------------------------------------------------------------------------------------------------------------
-- 3. "CowDetails": ranch, updated_at / updated_by, and the triggers
-- ---------------------------------------------------------------------------------------------------------------------

alter table public."CowDetails"
  add column if not exists ranch_id   uuid references public.ranches(id),
  add column if not exists updated_at timestamptz,
  add column if not exists updated_by text;

-- backfill (before the triggers exist, so nothing is logged and updated_at is not overwritten)
update public."CowDetails" c
   set ranch_id = r.id
  from public.ranches r
 where c.ranch_id is null and lower(c.user_id) = r.legacy_email;

update public."CowDetails"
   set updated_at = coalesce(to_timestamp(last_seen_at / 1000.0), created_at, now())
 where updated_at is null;

alter table public."CowDetails"
  alter column updated_at set default now(),
  alter column updated_at set not null;

create index if not exists cowdetails_ranch_idx on public."CowDetails" (ranch_id);
create index if not exists cowdetails_updated_idx on public."CowDetails" (ranch_id, updated_at desc);

-- BEFORE insert / update: server clock, who changed it, and (for the old app, which does not send them) the ranch and user
create or replace function public.cow_before_write() returns trigger
language plpgsql as $$
begin
  new.updated_at := now();
  new.updated_by := public.auth_email();
  if tg_op = 'INSERT' then
    if new.ranch_id is null then new.ranch_id := public.my_ranch_id(); end if;
    if new.user_id  is null then new.user_id  := public.auth_email();  end if;
  else
    new.ranch_id := old.ranch_id;       -- a cow never moves to another ranch by an update
  end if;
  return new;
end
$$;

drop trigger if exists trg_cow_before_write on public."CowDetails";
create trigger trg_cow_before_write
  before insert or update on public."CowDetails"
  for each row execute function public.cow_before_write();


-- ---------------------------------------------------------------------------------------------------------------------
-- 4. tours and visits
-- ---------------------------------------------------------------------------------------------------------------------

create table if not exists public.tours (
  id         uuid primary key default gen_random_uuid(),   -- the app may send its own id (safe to retry)
  ranch_id   uuid not null references public.ranches(id),
  started_by text not null default public.auth_email(),
  started_at timestamptz not null default now(),
  ended_at   timestamptz,
  status     text not null default 'open' check (status in ('open', 'closed', 'cancelled')),
  notes      text,
  summary    jsonb,                                        -- the summary saved when the tour is closed (see tour_summary)
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
-- a cowboy has at most one open tour in a ranch
create unique index if not exists tours_one_open_per_user on public.tours (ranch_id, started_by) where status = 'open';
create index if not exists tours_ranch_started_idx on public.tours (ranch_id, started_at desc);

drop trigger if exists trg_tours_touch on public.tours;
create trigger trg_tours_touch before update on public.tours
  for each row execute function public.set_updated_at();

-- one row for every cow that changed (or was seen) in a tour: what it looked like after the tour, and what changed
create table if not exists public.cow_visits (
  id          uuid primary key default gen_random_uuid(),
  ranch_id    uuid not null references public.ranches(id),
  tour_id     uuid not null references public.tours(id) on delete cascade,
  cow_id      bigint not null references public."CowDetails"(id) on delete cascade,
  cow_number  integer,
  visited_by  text not null default public.auth_email(),
  first_at    timestamptz not null default now(),
  last_at     timestamptz not null default now(),
  seen        boolean not null default false,       -- "seen today" was recorded in this tour
  -- the state of the cow at the last change of the tour
  cow_type    text,
  corpse      boolean,
  with_mom    boolean,
  marked_tag  boolean,
  latitude    double precision,
  longitude   double precision,
  comment     text,
  -- what changed in the tour: {"corpse": [false, true], "comment": ["", "limping"]}; {"created": true} for a new cow
  changes     jsonb not null default '{}'::jsonb,
  unique (tour_id, cow_id)
);
create index if not exists cow_visits_cow_idx on public.cow_visits (cow_id, last_at desc);
create index if not exists cow_visits_ranch_idx on public.cow_visits (ranch_id, last_at desc);

-- merges two "changes" objects of the same cow in the same tour: the first "from" value and the last "to" value are kept
create or replace function public.merge_changes(a jsonb, b jsonb) returns jsonb
language sql immutable as $$
  select coalesce(jsonb_object_agg(k,
    case
      when a ? k and b ? k and jsonb_typeof(a -> k) = 'array' and jsonb_typeof(b -> k) = 'array'
        then jsonb_build_array(a -> k -> 0, b -> k -> 1)
      when a ? k then a -> k
      else b -> k
    end), '{}'::jsonb)
  from (select jsonb_object_keys(a) as k union select jsonb_object_keys(b)) keys
$$;

-- AFTER insert / update of a cow: when its owner has an OPEN tour, the change is written to the visit of that tour.
-- the app does not have to do anything: it updates the cow as it does today, and the tour collects the changes
create or replace function public.cow_log_visit() returns trigger
language plpgsql as $$
declare
  v_tour    uuid;
  v_new     jsonb := to_jsonb(new);
  v_old     jsonb;
  v_changes jsonb := '{}'::jsonb;
  v_seen    boolean := false;
  k         text;
  -- the fields that count as a change of the cow (updated_at / updated_by are not)
  v_tracked text[] := array['number', 'number_mom', 'gender', 'cow_type', 'comment', 'latitude', 'longitude',
                            'corpse', 'last_seen_at', 'with_mom', 'marked_tag', 'num_of_calvings', 'health_issues'];
begin
  select t.id into v_tour
    from public.tours t
   where t.ranch_id = new.ranch_id and t.started_by = public.auth_email() and t.status = 'open'
   limit 1;
  if v_tour is null then return null; end if;

  if tg_op = 'INSERT' then
    v_changes := jsonb_build_object('created', true);
    v_seen := true;
  else
    v_old := to_jsonb(old);
    foreach k in array v_tracked loop
      if v_old -> k is distinct from v_new -> k then
        v_changes := v_changes || jsonb_build_object(k, jsonb_build_array(v_old -> k, v_new -> k));
      end if;
    end loop;
    if v_changes = '{}'::jsonb then return null; end if;      -- nothing that matters changed
    v_seen := v_changes ? 'last_seen_at';
  end if;

  insert into public.cow_visits as cv
    (ranch_id, tour_id, cow_id, cow_number, seen, cow_type, corpse, with_mom, marked_tag, latitude, longitude, comment, changes)
  values
    (new.ranch_id, v_tour, new.id, new.number, v_seen, new.cow_type, coalesce(new.corpse, false),
     coalesce(new.with_mom = new.last_seen_at, false), coalesce(new.marked_tag, false),
     new.latitude, new.longitude, new.comment, v_changes)
  on conflict (tour_id, cow_id) do update set
    cow_number = excluded.cow_number,
    last_at    = now(),
    seen       = cv.seen or excluded.seen,
    cow_type   = excluded.cow_type,
    corpse     = excluded.corpse,
    with_mom   = excluded.with_mom,
    marked_tag = excluded.marked_tag,
    latitude   = excluded.latitude,
    longitude  = excluded.longitude,
    comment    = excluded.comment,
    changes    = public.merge_changes(cv.changes, excluded.changes);
  return null;
end
$$;

drop trigger if exists trg_cow_log_visit on public."CowDetails";
create trigger trg_cow_log_visit
  after insert or update on public."CowDetails"
  for each row execute function public.cow_log_visit();


-- ---------------------------------------------------------------------------------------------------------------------
-- 5. alerts: which cows need attention (computed, nothing is stored, so a new rule needs no data change)
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
    -- the cow has at least one health problem (see supabase/health_issues.sql)
    case when not k.dead and jsonb_array_length(coalesce(c.health_issues, '[]'::jsonb)) > 0
         then 'health_issue' end,
    -- not seen for 14 days or more: change the number here to change the rule
    case when not k.dead and c.last_seen_at is not null
              and (extract(epoch from now()) * 1000 - c.last_seen_at) / 86400000 >= 14
         then 'not_seen_14d' end
  ], null) as issues
from public."CowDetails" c
cross join lateral (
  select public.cow_kind(c.cow_type) as kind, coalesce(c.corpse, false) as dead
) k;

-- the herd by type
create or replace view public.herd_overview with (security_invoker = true) as
select ranch_id, kind,
       count(*) filter (where not dead) as alive,
       count(*) filter (where dead)     as dead
from public.cow_status
group by ranch_id, kind;

-- the herd by year of birth (for the dashboard: how many were born / died in each year)
create or replace view public.herd_birth_years with (security_invoker = true) as
select ranch_id, birth_year,
       count(*) as registered,                 -- every cow of that birth year (alive or dead)
       count(*) filter (where dead) as dead
from public.cow_status
group by ranch_id, birth_year;


-- ---------------------------------------------------------------------------------------------------------------------
-- 6. start / end of a tour and the summary
-- ---------------------------------------------------------------------------------------------------------------------

-- the summary of a tour (also usable while the tour is open: the "so far" screen). returns one json object
create or replace function public.tour_summary(p_tour uuid) returns jsonb
language plpgsql stable as $$
declare
  t        public.tours%rowtype;
  v_start  bigint;
  v        jsonb;
  v_prev   jsonb;
  v_seen_ids bigint[];
begin
  select * into t from public.tours where id = p_tour;       -- row level security applies: only my ranch
  if not found then return null; end if;
  v_start := (extract(epoch from t.started_at) * 1000)::bigint;

  -- the living cows that were seen in this tour: the visit of the tour says "seen", or the last visit is after the start
  -- (the visit does not depend on the clock of the phone, the time does: both are checked)
  select coalesce(array_agg(s.id), '{}') into v_seen_ids
    from public.cow_status s
   where s.ranch_id = t.ranch_id and not s.dead
     and (s.last_seen_at >= v_start
          or exists (select 1 from public.cow_visits cv where cv.tour_id = t.id and cv.cow_id = s.id and cv.seen));

  v := jsonb_build_object(
    'tour_id', t.id, 'status', t.status, 'started_by', t.started_by, 'notes', t.notes,
    'started_at', t.started_at, 'ended_at', t.ended_at,
    'duration_minutes', round(extract(epoch from (coalesce(t.ended_at, now()) - t.started_at)) / 60)::int,

    -- the herd at this moment
    'herd', (select jsonb_build_object(
               'alive',      count(*) filter (where not dead),
               'calves',     count(*) filter (where not dead and kind = 'calf'),
               'first_cows', count(*) filter (where not dead and kind = 'first_cow'),
               'cows',       count(*) filter (where not dead and kind = 'cow'),
               'bulls',      count(*) filter (where not dead and kind = 'bull'),
               'dead',       count(*) filter (where dead))
             from public.cow_status where ranch_id = t.ranch_id),

    -- coverage: how many of the living cows were seen in the tour, and which ones were not
    'seen', coalesce(array_length(v_seen_ids, 1), 0),
    'seen_numbers', (select coalesce(jsonb_agg(number order by number) filter (where number is not null), '[]'::jsonb)
                     from public.cow_status where id = any(v_seen_ids)),
    'not_seen', (select jsonb_build_object(
                   'count', count(*),
                   'numbers', coalesce(jsonb_agg(number order by number) filter (where number is not null), '[]'::jsonb))
                 from public.cow_status
                 where ranch_id = t.ranch_id and not dead and not (id = any(v_seen_ids))),

    -- what changed in the tour
    'changed', (select count(*) from public.cow_visits where tour_id = t.id),
    'new_calves', (select jsonb_build_object(
                     'count', count(*),
                     'numbers', coalesce(jsonb_agg(cow_number order by cow_number), '[]'::jsonb))
                   from public.cow_visits
                   where tour_id = t.id and changes ? 'created' and public.cow_kind(cow_type) = 'calf'),
    'new_corpses', (select jsonb_build_object(
                      'count', count(*),
                      'numbers', coalesce(jsonb_agg(cow_number order by cow_number), '[]'::jsonb))
                    from public.cow_visits
                    where tour_id = t.id and changes -> 'corpse' ->> 1 = 'true'),
    'comments_added', (select count(*) from public.cow_visits where tour_id = t.id and changes ? 'comment'),

    -- open problems now (see the view cow_status), with the cow numbers of the most important ones
    'problems', (select jsonb_build_object(
                   'calf_without_mom', count(*) filter (where 'calf_without_mom' = any(issues)),
                   'calf_without_tag', count(*) filter (where 'calf_without_tag' = any(issues)),
                   'no_location',      count(*) filter (where 'no_location' = any(issues)),
                   'not_seen_14d',     count(*) filter (where 'not_seen_14d' = any(issues)),
                   'never_seen',       count(*) filter (where 'never_seen' = any(issues)),
                   'health_issue',     count(*) filter (where 'health_issue' = any(issues)),
                   'calf_without_mom_numbers',
                       coalesce(jsonb_agg(number order by number)
                                filter (where 'calf_without_mom' = any(issues) and number is not null), '[]'::jsonb))
                 from public.cow_status where ranch_id = t.ranch_id)
  );

  -- comparison with the previous closed tour of the ranch
  select summary into v_prev
    from public.tours
   where ranch_id = t.ranch_id and status = 'closed' and id <> t.id and ended_at < t.started_at
   order by ended_at desc limit 1;
  if v_prev is not null then
    v := v || jsonb_build_object('vs_previous', jsonb_build_object(
      'previous_tour_id', v_prev ->> 'tour_id',
      'alive',            (v -> 'herd' ->> 'alive')::int - (v_prev -> 'herd' ->> 'alive')::int,
      'seen',             (v ->> 'seen')::int - (v_prev ->> 'seen')::int,
      'not_seen',         (v -> 'not_seen' ->> 'count')::int - (v_prev -> 'not_seen' ->> 'count')::int,
      'new_calves',       (v -> 'new_calves' ->> 'count')::int - (v_prev -> 'new_calves' ->> 'count')::int,
      'calf_without_mom', (v -> 'problems' ->> 'calf_without_mom')::int - (v_prev -> 'problems' ->> 'calf_without_mom')::int
    ));
  end if;
  return v;
end
$$;

-- start a tour (or continue the open one): returns the id of the tour
create or replace function public.start_tour(p_notes text default null) returns uuid
language plpgsql as $$
declare
  v_ranch uuid := public.my_ranch_id();
  v_id    uuid;
begin
  if v_ranch is null then raise exception 'this user does not belong to a ranch'; end if;
  select id into v_id from public.tours
   where ranch_id = v_ranch and started_by = public.auth_email() and status = 'open';
  if v_id is not null then return v_id; end if;
  insert into public.tours (ranch_id, notes) values (v_ranch, p_notes) returning id into v_id;
  return v_id;
end
$$;

-- end a tour: closes it, saves the summary in the tour, and returns the summary (for the summary screen)
create or replace function public.end_tour(p_tour uuid, p_notes text default null) returns jsonb
language plpgsql as $$
declare
  v jsonb;
begin
  update public.tours
     set ended_at = now(), status = 'closed', notes = coalesce(p_notes, notes)
   where id = p_tour and status = 'open'
     and (started_by = public.auth_email() or public.is_ranch_coordinator(ranch_id));
  if not found then raise exception 'tour not found, not yours, or already closed'; end if;
  v := public.tour_summary(p_tour);
  update public.tours set summary = v where id = p_tour;
  return v;
end
$$;

-- cancel a tour (nothing is lost: the changes of the cows stay, only the tour is marked as cancelled)
create or replace function public.cancel_tour(p_tour uuid) returns void
language plpgsql as $$
begin
  update public.tours set ended_at = now(), status = 'cancelled'
   where id = p_tour and status = 'open'
     and (started_by = public.auth_email() or public.is_ranch_coordinator(ranch_id));
end
$$;


-- ---------------------------------------------------------------------------------------------------------------------
-- 7. row level security: everybody sees and changes only the data of the ranches they belong to
-- ---------------------------------------------------------------------------------------------------------------------

alter table public.ranches        enable row level security;
alter table public.ranch_members  enable row level security;
alter table public."CowDetails"   enable row level security;
alter table public.tours          enable row level security;
alter table public.cow_visits     enable row level security;

-- ranches
drop policy if exists ranches_select on public.ranches;
create policy ranches_select on public.ranches for select to authenticated
  using (public.is_ranch_member(id));
drop policy if exists ranches_update on public.ranches;
create policy ranches_update on public.ranches for update to authenticated
  using (public.is_ranch_coordinator(id)) with check (public.is_ranch_coordinator(id));

-- members: a member sees the other members of the ranch; only a coordinator / owner adds or removes members
drop policy if exists ranch_members_select on public.ranch_members;
create policy ranch_members_select on public.ranch_members for select to authenticated
  using (public.is_ranch_member(ranch_id));
drop policy if exists ranch_members_write on public.ranch_members;
create policy ranch_members_write on public.ranch_members for all to authenticated
  using (public.is_ranch_coordinator(ranch_id)) with check (public.is_ranch_coordinator(ranch_id));

-- cows: members read, add and update; only a coordinator / owner deletes. a cow without a ranch (not migrated yet) is
-- still visible to the user in its user_id column. the policies are checked AFTER the trigger filled ranch_id
drop policy if exists cow_select_ranch on public."CowDetails";
create policy cow_select_ranch on public."CowDetails" for select to authenticated
  using (public.is_ranch_member(ranch_id) or (ranch_id is null and lower(user_id) = public.auth_email()));
drop policy if exists cow_insert_ranch on public."CowDetails";
create policy cow_insert_ranch on public."CowDetails" for insert to authenticated
  with check (public.is_ranch_member(ranch_id));
drop policy if exists cow_update_ranch on public."CowDetails";
create policy cow_update_ranch on public."CowDetails" for update to authenticated
  using (public.is_ranch_member(ranch_id) or (ranch_id is null and lower(user_id) = public.auth_email()))
  with check (public.is_ranch_member(ranch_id));
drop policy if exists cow_delete_ranch on public."CowDetails";
create policy cow_delete_ranch on public."CowDetails" for delete to authenticated
  using (public.is_ranch_coordinator(ranch_id));

-- tours: members read the tours of the ranch; a member starts a tour in his name; he (or a coordinator) updates it
drop policy if exists tours_select on public.tours;
create policy tours_select on public.tours for select to authenticated
  using (public.is_ranch_member(ranch_id));
drop policy if exists tours_insert on public.tours;
create policy tours_insert on public.tours for insert to authenticated
  with check (public.is_ranch_member(ranch_id) and started_by = public.auth_email());
drop policy if exists tours_update on public.tours;
create policy tours_update on public.tours for update to authenticated
  using (started_by = public.auth_email() or public.is_ranch_coordinator(ranch_id))
  with check (public.is_ranch_member(ranch_id));

-- visits: members read; a member writes his own visits (the trigger above writes them in his name)
drop policy if exists cow_visits_select on public.cow_visits;
create policy cow_visits_select on public.cow_visits for select to authenticated
  using (public.is_ranch_member(ranch_id));
drop policy if exists cow_visits_insert on public.cow_visits;
create policy cow_visits_insert on public.cow_visits for insert to authenticated
  with check (public.is_ranch_member(ranch_id) and visited_by = public.auth_email());
drop policy if exists cow_visits_update on public.cow_visits;
create policy cow_visits_update on public.cow_visits for update to authenticated
  using (public.is_ranch_member(ranch_id) and visited_by = public.auth_email())
  with check (public.is_ranch_member(ranch_id));


-- ---------------------------------------------------------------------------------------------------------------------
-- 8. permissions (the table GRANTs: a policy is not enough without them, error 42501)
-- ---------------------------------------------------------------------------------------------------------------------

grant usage on schema public to authenticated;
grant select, update                 on public.ranches        to authenticated;
grant select, insert, update, delete on public.ranch_members  to authenticated;
grant select, insert, update, delete on public."CowDetails"   to authenticated;
grant select, insert, update         on public.tours          to authenticated;
grant select, insert, update         on public.cow_visits     to authenticated;
grant select                         on public.cow_status, public.herd_overview, public.herd_birth_years to authenticated;

-- functions: only logged in users (not the anonymous key)
revoke execute on function
  public.auth_email(), public.cow_kind(text), public.is_ranch_member(uuid), public.is_ranch_coordinator(uuid),
  public.my_ranch_id(), public.tour_summary(uuid), public.start_tour(text), public.end_tour(uuid, text),
  public.cancel_tour(uuid), public.merge_changes(jsonb, jsonb)
  from public, anon;
grant execute on function
  public.auth_email(), public.cow_kind(text), public.is_ranch_member(uuid), public.is_ranch_coordinator(uuid),
  public.my_ranch_id(), public.tour_summary(uuid), public.start_tour(text), public.end_tour(uuid, text),
  public.cancel_tour(uuid), public.merge_changes(jsonb, jsonb)
  to authenticated;


-- ---------------------------------------------------------------------------------------------------------------------
-- 9. IMPORTANT: the old policies
-- policies are added together (OR): an old, wider policy on "CowDetails" would still let everybody in.
-- list them, and drop the ones that the new policies replace:
--   select policyname, cmd, qual from pg_policies where tablename = 'CowDetails';
--   drop policy "<old policy name>" on public."CowDetails";
-- ---------------------------------------------------------------------------------------------------------------------


-- ---------------------------------------------------------------------------------------------------------------------
-- 10. quick checks (run as a logged in user from the app, or with "set role authenticated" and a jwt in the SQL editor)
-- ---------------------------------------------------------------------------------------------------------------------
--   select public.start_tour('morning tour');                         -- returns the tour id
--   -- ... edit some cows in the app ...
--   select * from public.cow_visits where tour_id = '<tour id>';      -- one row for every changed cow
--   select public.tour_summary('<tour id>');                          -- the summary so far
--   select public.end_tour('<tour id>');                              -- closes the tour, returns and saves the summary
--   select * from public.cow_status where cardinality(issues) > 0 order by number;   -- the cows that need attention

-- undo (if needed, before anything depends on it):
--   drop trigger trg_cow_log_visit on public."CowDetails"; drop trigger trg_cow_before_write on public."CowDetails";
--   drop table public.cow_visits, public.tours cascade; drop view public.cow_status cascade;
