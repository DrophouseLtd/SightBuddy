-- Per-install LLM token quota (Edge Function uses service_role; RLS blocks direct client access).
create table if not exists public.llm_quota (
    install_id text primary key,
    tier text not null default 'free',
    daily_tokens_used integer not null default 0,
    daily_token_limit integer not null default 50000,
    last_reset_at timestamptz not null default (timezone ('utc', now ())),
    created_at timestamptz not null default now ()
);

comment on table public.llm_quota is 'Daily OpenAI token budget per app install_id; maintained by Edge Function chat.';

alter table public.llm_quota enable row level security;
