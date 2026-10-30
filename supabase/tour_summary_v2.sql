-- Patch: run this in the Supabase SQL editor. It replaces the function tour_summary (the same function as in tours_schema.sql):
--   * a cow counts as seen in the tour when its visit in the tour says "seen" OR its last visit is after the start of the tour
--     (before, only the time was checked, so a phone clock that is a little behind the server hid the seen cows)
--   * the summary also returns "seen_numbers" (the numbers of the cows that were seen), for the circles of the summary screen

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

grant execute on function public.tour_summary(uuid) to authenticated;
