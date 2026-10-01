-- Volo oracle schema (Phase 6).
-- Written for local execution: paste into Supabase Dashboard -> SQL Editor and run.
-- RLS is enabled with no policies: only the service-role key (used exclusively by
-- the oracle edge function) can touch these tables; the app never connects directly.

create table if not exists challenges (
  challenge_pubkey text primary key check (challenge_pubkey ~ '^[1-9A-HJ-NP-Za-km-z]{32,44}$'),
  creator_pubkey   text not null check (creator_pubkey ~ '^[1-9A-HJ-NP-Za-km-z]{32,44}$'),
  opponent_pubkey  text not null check (opponent_pubkey ~ '^[1-9A-HJ-NP-Za-km-z]{32,44}$'),
  target_app       text not null check (char_length(target_app) between 1 and 200),
  limit_seconds    integer not null check (limit_seconds > 0),
  window_seconds   integer not null check (window_seconds > 0),
  stake_amount     bigint not null check (stake_amount > 0),
  end_time         timestamptz,
  status           text not null default 'ACTIVE'
                   check (status in ('CREATED', 'ACTIVE', 'SETTLED', 'REFUNDED', 'BOTH_FAIL_LOCKED')),
  created_at       timestamptz not null default now()
);

create table if not exists usage_reports (
  id               uuid primary key default gen_random_uuid(),
  challenge_pubkey text not null references challenges (challenge_pubkey),
  wallet_pubkey    text not null check (wallet_pubkey ~ '^[1-9A-HJ-NP-Za-km-z]{32,44}$'),
  used_seconds     bigint not null check (used_seconds >= 0),
  tamper_signals   text[] not null default '{}',
  window_start     timestamptz,
  window_end       timestamptz,
  reported_at      timestamptz not null default now()
);

create index if not exists usage_reports_lookup
  on usage_reports (challenge_pubkey, wallet_pubkey, reported_at desc);

create table if not exists settlement_results (
  challenge_pubkey text primary key references challenges (challenge_pubkey),
  status           text not null
                   check (status in ('CREATOR_WON', 'OPPONENT_WON', 'BOTH_PASS_REFUND', 'BOTH_FAIL_LOCKED')),
  both_fail        boolean not null default false,
  settled          boolean not null default false,
  creator_pubkey   text not null,
  opponent_pubkey  text not null,
  creator_result   text not null check (creator_result in ('PASS', 'FAIL', 'UNVERIFIED')),
  opponent_result  text not null check (opponent_result in ('PASS', 'FAIL', 'UNVERIFIED')),
  creator_code     smallint not null check (creator_code between 1 and 3),
  opponent_code    smallint not null check (opponent_code between 1 and 3),
  status_code      smallint not null check (status_code between 1 and 4),
  settled_at       timestamptz not null default now()
);

alter table challenges enable row level security;
alter table usage_reports enable row level security;
alter table settlement_results enable row level security;

-- Public read-only settlement status (plan 8.2 Step 1: the app polls for
-- SETTLED=true / both_fail after the window). Rows are pubkey + verdicts only,
-- no PII. Writes stay service-role only (used by the oracle function).
create policy "settlement_results public read"
  on settlement_results for select
  using (true);
