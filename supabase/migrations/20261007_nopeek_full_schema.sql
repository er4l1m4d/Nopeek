-- =====================================================================
-- Nopeek oracle schema - ONE-PASTE SETUP FILE
-- Run in: Supabase Dashboard -> Project Settings -> SQL Editor -> New query
-- Safe to run more than once (every statement is idempotent).
--
-- This replaces the ordered two-file path (20261001_volo_init.sql then the
-- Task 5 delta) because these run by hand in a web dashboard, and "paste the
-- other one first" is exactly the kind of step that silently gets skipped.
-- The 20261001 file stays in the repo as history; on a fresh project only
-- THIS file needs to be run.
--
-- What is new here versus 20261001: the attestation tables (install_keys,
-- report_nonces) and the evidence columns on usage_reports / settlement_results.
-- These back the compliance proof: a device signs a canonical payload with a
-- key attested by Google, the oracle verifies the chain, and the SHA-256 of the
-- signed bytes is what gets written on-chain at settlement.
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. Base tables (unchanged from 20261001_volo_init.sql)
--
-- RLS is enabled with no policies on every table below: only the service-role
-- key, which lives inside the Edge Function and never ships to the phone, can
-- touch them. The app reads settlement status through the function, not directly.
-- ---------------------------------------------------------------------

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

-- The one intentionally public table: pubkey + verdicts only, no usage detail.
-- This is what lets a judge or an opponent check an outcome without trusting us.
drop policy if exists "settlement_results public read" on settlement_results;
create policy "settlement_results public read"
  on settlement_results for select
  using (true);

-- GRANT is a separate layer from the policy just above: the policy decides WHICH
-- ROWS a role sees, the grant decides whether that role may address the TABLE at
-- all. PostgREST needs both. When Project Settings -> API -> "Automatically
-- expose new tables" is off, tables created afterwards get no SELECT to anon and
-- the app's settlement polling silently returns nothing -- so it is granted
-- explicitly here, which makes this file correct under either setting. GRANT is
-- idempotent, so re-running is safe.
grant select on public.settlement_results to anon;

-- ---------------------------------------------------------------------
-- 2. Attestation: enrolled device keys
--
-- One row per wallet that has enrolled a hardware keystore key. The oracle
-- compares the leaf public key of a submitted chain against keystore_spki_b64
-- here, so a report signed by some other device's key is rejected even if that
-- chain is otherwise perfectly valid.
-- ---------------------------------------------------------------------

create table if not exists install_keys (
  wallet_pubkey     text primary key check (wallet_pubkey ~ '^[1-9A-HJ-NP-Za-km-z]{32,44}$'),
  -- DER SubjectPublicKeyInfo of the attested EC P-256 key, base64.
  keystore_spki_b64 text not null check (length(keystore_spki_b64) between 40 and 256),
  -- Full PEM chain as presented by the device, for re-verification later.
  cert_chain_pem    text not null check (cert_chain_pem like '%BEGIN CERTIFICATE%'),
  -- SHA-256 of the leaf certificate DER, base64. Identifies THIS leaf cert.
  -- NOTE: this is not the APK signing-cert digest used by spec 4.3 check 8,
  -- which is read out of the attestation extension instead.
  cert_digest_b64   text not null check (length(cert_digest_b64) between 40 and 64),
  enrolled_at       timestamptz not null default now(),
  -- Set, never deleted: a revoked key must keep failing rather than silently
  -- becoming re-enrollable under the same wallet.
  revoked_at        timestamptz
);

alter table install_keys enable row level security;

-- ---------------------------------------------------------------------
-- 3. Attestation: single-use nonces
--
-- Each commitment window gets one nonce, which the device embeds in the signed
-- payload as the attestation challenge. This is what stops a captured report
-- being replayed onto a different challenge or wallet: the nonce is bound to
-- both at issue time and marked consumed on use.
-- ---------------------------------------------------------------------

create table if not exists report_nonces (
  id               bigint generated always as identity primary key,
  challenge_pubkey text not null references challenges (challenge_pubkey),
  wallet_pubkey    text not null check (wallet_pubkey ~ '^[1-9A-HJ-NP-Za-km-z]{32,44}$'),
  nonce_b64        text not null unique check (length(nonce_b64) >= 32),
  issued_at        timestamptz not null default now(),
  expires_at       timestamptz not null,
  consumed_at      timestamptz,
  check (expires_at > issued_at)
);

create index if not exists report_nonces_lookup
  on report_nonces (challenge_pubkey, wallet_pubkey, nonce_b64);

alter table report_nonces enable row level security;

-- ---------------------------------------------------------------------
-- 4. Evidence columns on reports and settlements
--
-- All nullable / defaulted so the existing submission path keeps working
-- unchanged while attestation is being wired.
-- ---------------------------------------------------------------------

alter table usage_reports add column if not exists signature_b64   text;
alter table usage_reports add column if not exists cert_chain_pem  text;
-- The exact bytes the device signed. Never re-derived, never re-encoded.
alter table usage_reports add column if not exists canonical_bytes bytea;
alter table usage_reports add column if not exists evidence_hash   text;
alter table usage_reports add column if not exists package_name    text;
alter table usage_reports add column if not exists device_active_seconds bigint;
alter table usage_reports add column if not exists event_count     integer;
alter table usage_reports add column if not exists event_set_digest text;
-- The oracle's verdict on the evidence. Default false: unverified is the safe
-- default, and absence of a verified report already means UNVERIFIED -> loss.
alter table usage_reports add column if not exists verified        boolean not null default false;
alter table usage_reports add column if not exists reject_reason   text;

-- used_seconds / event_count are counters, so negative is nonsense; these
-- mirror the bounds the client and oracle encoders enforce on the payload.
alter table usage_reports drop constraint if exists usage_reports_evidence_bounds;
alter table usage_reports add constraint usage_reports_evidence_bounds check (
  (device_active_seconds is null or device_active_seconds >= 0) and
  (event_count is null or event_count >= 0)
);

-- Two distinct reports must never both be accepted for the same side of the
-- same duel. This is the replay/second-chance guard.
create unique index if not exists usage_reports_one_verified_per_side
  on usage_reports (challenge_pubkey, wallet_pubkey)
  where verified;

-- Same bytes presented twice is the same evidence, not a second report.
create unique index if not exists usage_reports_evidence_hash_unique
  on usage_reports (evidence_hash)
  where evidence_hash is not null;

alter table settlement_results add column if not exists creator_evidence_hash  text;
alter table settlement_results add column if not exists opponent_evidence_hash text;

-- ---------------------------------------------------------------------
-- 5. Housekeeping: reject_reason is only ever meaningful on a rejected row,
-- and verified only on an accepted one. Keeps a bad write from being silently
-- optimistic about itself.
-- ---------------------------------------------------------------------

alter table usage_reports drop constraint if exists usage_reports_verdict_shape;
alter table usage_reports add constraint usage_reports_verdict_shape check (
  not (verified and reject_reason is not null)
);

-- =====================================================================
-- VERIFICATION — one statement, one result grid
-- The Supabase SQL Editor only shows the result of the LAST statement in
-- a multi-statement run, so five separate selects here would display one
-- row and hide the other four. Everything is unioned into a single
-- statement instead. Safe to re-run: the schema above is idempotent.
--
-- Read 'app_read_path_ok' first — that is the one row the phone polls.
-- =====================================================================

select check_name, result from (
  select 1 as ord, 'tables_present' as check_name,
    coalesce((select string_agg(table_name, ', ' order by table_name)
       from information_schema.tables
      where table_schema = 'public'
        and table_name in ('challenges','usage_reports','settlement_results','install_keys','report_nonces')),
      'MISSING — the paste was truncated') as result
  union all
  select 2, 'rls_enabled', coalesce((
      select string_agg(relname || '=' || (case when relrowsecurity then 'on' else 'OFF' end), ', ' order by relname)
       from pg_class
      where relnamespace = 'public'::regnamespace
        and relkind = 'r'
        and relname in ('challenges','usage_reports','settlement_results','install_keys','report_nonces')),
      'MISSING — the paste was truncated')
  union all
  -- Expect exactly one policy: settlement_results SELECT. Anything else here
  -- is a policy someone added by hand and needs a look at.
  select 3, 'policies', coalesce((
      select string_agg(tablename || ' -> ' || policyname || ' (' || lower(cmd) || ')', ', ' order by tablename, policyname)
       from pg_policies where schemaname = 'public'), 'none')
  union all
  select 4, 'evidence_columns', coalesce((
      select string_agg(column_name, ', ' order by column_name)
       from information_schema.columns
      where table_name = 'usage_reports'
        and column_name in ('signature_b64','cert_chain_pem','canonical_bytes','evidence_hash',
                            'package_name','device_active_seconds','event_count','event_set_digest',
                            'verified','reject_reason')),
      'MISSING — the paste was truncated')
  union all
  -- The GRANT layer, separate from RLS. PostgREST only ever issues SELECT /
  -- INSERT / UPDATE / DELETE (and RPC), so those are the privileges that are
  -- actually reachable by whoever holds the key shipped in the APK;
  -- REFERENCES / TRIGGER / TRUNCATE are not reachable through the API at all.
  -- Expected now: exactly one data privilege, settlement_results:SELECT.
  -- Anything else in this list is a hole worth closing.
  select 5, 'anon_grants', coalesce((
      select string_agg(table_name || ':' || privilege_type, ', ' order by table_name, privilege_type)
       from information_schema.role_table_grants
      where grantee = 'anon' and table_schema = 'public'), 'none')
  union all
  -- The app's only direct table read (SettlementClient.kt). Needs BOTH the grant
  -- and the policy; either one missing and settlement polling returns nothing.
  select 6, 'app_read_path_ok',
    case when exists (select 1 from information_schema.role_table_grants
                       where grantee = 'anon' and table_schema = 'public'
                         and table_name = 'settlement_results' and privilege_type = 'SELECT')
          and exists (select 1 from pg_policies
                       where schemaname = 'public' and tablename = 'settlement_results'
                         and cmd = 'SELECT')
         then 'YES — anon can SELECT settlement_results under RLS'
         else 'NO — the app will silently never see a settlement' end
  union all
  -- service_role must be true: that is why the oracle function works on tables
  -- that have no policies at all.
  select 7, 'roles_bypassrls', coalesce((
      select string_agg(rolname || '=' || (case when rolbypassrls then 'bypass' else 'restricted' end), ', ' order by rolname)
       from pg_roles where rolname in ('anon','authenticated','service_role')), 'none')
  union all
  -- Records whether the two Project Settings toggles are armed. 'none' means
  -- neither automatic RLS nor automatic exposure is switched on.
  select 8, 'event_triggers', coalesce((
      select string_agg(evtname, ', ' order by evtname) from pg_event_trigger), 'none')
  union all
  -- The replay / second-chance guard, and the same-bytes-is-one-report guard.
  select 9, 'partial_indexes', coalesce((
      select string_agg(indexname, ', ' order by indexname)
       from pg_indexes
      where schemaname = 'public'
        and indexname in ('usage_reports_one_verified_per_side','usage_reports_evidence_hash_unique')),
      'MISSING — the paste was truncated')
) checks order by ord;
