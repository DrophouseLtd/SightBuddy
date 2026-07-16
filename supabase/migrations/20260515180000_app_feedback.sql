-- Anonymous in-app feedback (install_id = per-install UUID from the app, not user accounts).
create table if not exists public.app_feedback (
    id uuid primary key default gen_random_uuid(),
    install_id text not null,
    feedback text not null,
    created_at timestamptz not null default now(),
    constraint app_feedback_install_id_len check (char_length(install_id) between 1 and 128),
    constraint app_feedback_body_len check (char_length(feedback) between 1 and 4000)
);

create index if not exists app_feedback_install_id_created_at_idx
    on public.app_feedback (install_id, created_at desc);

comment on table public.app_feedback is 'Anonymous feedback rows; max 20 per install_id via Edge Function feedback.';

alter table public.app_feedback enable row level security;
