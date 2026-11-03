-- The mother of a calf. Until now a calf kept only the NUMBER of its mother (number_mom), but the same number is used again
-- in other years (12-22, 12-24, ...), so the number does not say which cow is the mother. This adds the id of the mother's
-- row. Run it once in the Supabase SQL editor. The app works also without it (a calf is then shown with the number of
-- its mother, and with the year when only one cow of that number was born before the calf).

alter table "CowDetails"
  add column if not exists mom_id integer references "CowDetails"(id) on delete set null;

-- Optional: fills mom_id for the calves that already exist, only where the mother is clear: exactly one cow / first-cow
-- of the same user has the mother number and was born before the calf. Calves with an unclear mother stay empty.
update "CowDetails" calf
set mom_id = (
  select m.id
  from "CowDetails" m
  where m.user_id = calf.user_id
    and m.number = calf.number_mom
    and m.cow_type in ('פרה', 'מבכירה')
    and (calf.created_at is null or m.created_at is null or m.created_at <= calf.created_at)
)
where calf.cow_type = 'עגל'
  and calf.mom_id is null
  and calf.number_mom is not null
  and (
    select count(*)
    from "CowDetails" m
    where m.user_id = calf.user_id
      and m.number = calf.number_mom
      and m.cow_type in ('פרה', 'מבכירה')
      and (calf.created_at is null or m.created_at is null or m.created_at <= calf.created_at)
  ) = 1;
