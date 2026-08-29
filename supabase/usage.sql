-- Run this once in Supabase SQL Editor.
create table if not exists public.app_installs (
  install_id text primary key check (length(install_id) between 16 and 128),
  platform text not null default 'android',
  app_version text not null default '',
  first_seen timestamptz not null default now(),
  last_seen timestamptz not null default now()
);

alter table public.app_installs enable row level security;

create or replace function public.register_install(p_install_id text, p_app_version text default '', p_platform text default 'android')
returns void language plpgsql security definer set search_path = public
as $$
begin
  if p_install_id is null or length(p_install_id) < 16 or length(p_install_id) > 128 then
    raise exception 'invalid install id';
  end if;
  insert into public.app_installs(install_id, platform, app_version)
  values (p_install_id, coalesce(nullif(p_platform,''),'android'), coalesce(p_app_version,''))
  on conflict (install_id) do update set platform=excluded.platform, app_version=excluded.app_version, last_seen=now();
end;
$$;

revoke all on function public.register_install(text,text,text) from public;
grant execute on function public.register_install(text,text,text) to anon;

create or replace view public.app_usage_stats as
select count(*)::int as total_users,
       count(*) filter (where last_seen >= now() - interval '7 days')::int as active_7d,
       count(*) filter (where last_seen >= now() - interval '30 days')::int as active_30d
from public.app_installs;
grant select on public.app_usage_stats to anon;
