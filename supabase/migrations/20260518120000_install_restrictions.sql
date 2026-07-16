-- Temporary AI block after user-initiated data deletion (Edge Functions only).
create table if not exists public.install_restrictions (
    install_id text primary key,
    restricted_until timestamptz not null,
    created_at timestamptz not null default now (),
    constraint install_restrictions_install_id_len check (char_length(install_id) between 1 and 128)
);

comment on table public.install_restrictions is
    'Blocks chat/feedback for install_id until restricted_until; set by delete-my-data Edge Function.';

alter table public.install_restrictions enable row level security;
