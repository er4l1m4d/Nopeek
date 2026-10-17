use anchor_lang::prelude::*;
use anchor_spl::associated_token::AssociatedToken;
use anchor_spl::token::{self, Mint, Token, TokenAccount, Transfer};

// Playground rewrites the program id below at build time, anchoring on the FIRST literal
// occurrence of that macro name in the file. Do not name it in a comment above this line:
// doing so made Playground delete the `use` statements and paste the id over them.
declare_id!("11111111111111111111111111111111");

/// Oracle signer authority: the funded devnet deploy wallet, recorded in
/// `contracts/ATTESTATION_DEPLOY_NOTES.md`. Keypair lives at `~/.config/solana/id.json`
/// (outside the repo, gitignored) and held 10 devnet SOL on 2026-10-09, confirmed by a
/// `getBalance` RPC call rather than by a UI. Validity is not assumed either: the RPC
/// accepted the address, so it is a well-formed 32-byte pubkey.
///
/// This same keypair is the settlement signer, so Task 14 must export it to the Supabase
/// Edge Function as a secret. Never commit it and never put it in the client.
/// Fully-qualified on purpose: Playground's build reported "cannot find macro `pubkey`" with
/// `anchor_lang::prelude::*` in scope and suggested `solana_program::pubkey`, so this goes
/// through anchor's own re-export. It adds no dependency of ours and works on every version.
pub const ORACLE_AUTHORITY: Pubkey =
    anchor_lang::solana_program::pubkey!("6k9TDQtquf2pusu9LHMVACFRtGfCCTsvzfvuifcRWmmj");

/// Sweep destination for both-fail-locked stakes. The oracle wallet for the same reason
/// the oracle is the settlement signer: on a devnet demo there is no governance process
/// to point at, and pretending otherwise would hide a decision that production must make
/// (a DAO- or multisig-governed treasury) rather than bury it.
pub const TREASURY: Pubkey =
    anchor_lang::solana_program::pubkey!("6k9TDQtquf2pusu9LHMVACFRtGfCCTsvzfvuifcRWmmj");

pub const CHALLENGE_SEED: &[u8] = b"challenge";
pub const REPUTATION_SEED: &[u8] = b"reputation";

/// How long a both-fail-locked escrow sits before anyone can sweep it to the treasury.
/// One hour is demo-legible (a judge can watch it resolve inside a session); production
/// would use 24h. State in ATTESTATION_DEPLOY_NOTES.md, not here.
pub const LOCK_SECONDS: i64 = 3600;

/// Status codes (mirrors challenges.status in the plan schema).
pub const STATUS_CREATED: u8 = 0;
pub const STATUS_ACTIVE: u8 = 1;
pub const STATUS_SETTLED: u8 = 2;
pub const STATUS_REFUNDED: u8 = 3;
pub const STATUS_BOTH_FAIL_LOCKED: u8 = 4;
pub const STATUS_LOCKED_RESOLVED: u8 = 5;

/// Result codes (mirrors settlement_results).
pub const RESULT_NONE: u8 = 0;
pub const RESULT_CREATOR_WON: u8 = 1;
pub const RESULT_OPPONENT_WON: u8 = 2;
pub const RESULT_BOTH_PASS: u8 = 3;
pub const RESULT_BOTH_FAIL: u8 = 4;

/// Report outcome codes accepted by settle_challenge.
pub const REPORT_PASS: u8 = 1;
pub const REPORT_FAIL: u8 = 2;
pub const REPORT_UNVERIFIED: u8 = 3;

/// Zero evidence hash: a FAIL/UNVERIFIED report carries no evidence by definition, but a
/// PASS without evidence would be the on-chain version of believing the app's own number.
pub const ZERO_HASH: [u8; 32] = [0u8; 32];

#[program]
pub mod volo_challenge {
    use super::*;

    /// Creator stakes into escrow and opens the challenge.
    ///
    /// `nonce` makes the pair re-duelable: the PDA is
    /// [CHALLENGE_SEED, creator, opponent, nonce.to_le_bytes()], so a second duel between
    /// the same two wallets is a different account instead of v1's
    /// AccountAlreadyInitialized dead end. nonce 1 little-endian is `01000000`.
    pub fn create_challenge(
        ctx: Context<CreateChallenge>,
        stake_amount: u64,
        target_app: String,
        limit_seconds: u32,
        window_seconds: u32,
        nonce: u32,
    ) -> Result<()> {
        require!(stake_amount > 0, VoloError::InvalidStake);
        require!(target_app.len() <= 32, VoloError::TargetAppTooLong);
        require!(limit_seconds > 0, VoloError::InvalidWindow);
        require!(window_seconds > 0, VoloError::InvalidWindow);
        require!(
            ctx.accounts.creator.key() != ctx.accounts.opponent.key(),
            VoloError::ReputationMismatch
        );

        let clock = Clock::get()?;

        let cpi = CpiContext::new(
            ctx.accounts.token_program.to_account_info(),
            Transfer {
                from: ctx.accounts.creator_token.to_account_info(),
                to: ctx.accounts.escrow.to_account_info(),
                authority: ctx.accounts.creator.to_account_info(),
            },
        );
        token::transfer(cpi, stake_amount)?;

        let challenge = &mut ctx.accounts.challenge;
        challenge.creator = ctx.accounts.creator.key();
        challenge.opponent = ctx.accounts.opponent.key();
        challenge.token_mint = ctx.accounts.mint.key();
        challenge.stake_amount = stake_amount;
        challenge.target_app = target_app;
        challenge.limit_seconds = limit_seconds;
        challenge.window_seconds = window_seconds;
        challenge.start_time = clock.unix_timestamp;
        challenge.end_time = clock.unix_timestamp + window_seconds as i64;
        challenge.status = STATUS_CREATED;
        challenge.result = RESULT_NONE;
        challenge.bump = ctx.bumps.challenge;
        challenge.nonce = nonce;
        challenge.creator_evidence_hash = ZERO_HASH;
        challenge.opponent_evidence_hash = ZERO_HASH;
        challenge.settled_at = 0;
        Ok(())
    }

    /// Opponent stakes an equal amount and activates the challenge.
    pub fn join_challenge(ctx: Context<JoinChallenge>) -> Result<()> {
        require!(
            ctx.accounts.challenge.status == STATUS_CREATED,
            VoloError::NotCreated
        );
        require!(
            ctx.accounts.challenge.opponent == ctx.accounts.opponent.key(),
            VoloError::NotOpponent
        );

        let cpi = CpiContext::new(
            ctx.accounts.token_program.to_account_info(),
            Transfer {
                from: ctx.accounts.opponent_token.to_account_info(),
                to: ctx.accounts.escrow.to_account_info(),
                authority: ctx.accounts.opponent.to_account_info(),
            },
        );
        token::transfer(cpi, ctx.accounts.challenge.stake_amount)?;

        ctx.accounts.challenge.status = STATUS_ACTIVE;
        Ok(())
    }

    /// Oracle-only settlement, now carrying the evidence it settled from.
    ///
    /// The two hashes are the sha-256 of the exact canonical bytes each duelist's hardware
    /// attested (the oracle's `evidenceHash`), so the on-chain settlement describes the
    /// same bytes the escrow releases. A PASS with a zero hash is refused: winning requires
    /// evidence that exists, not merely an oracle's word. FAIL/UNVERIFIED may carry zero
    /// hashes — there is nothing to point at when the window was not verified.
    pub fn settle_challenge(
        ctx: Context<SettleChallenge>,
        creator_result: u8,
        opponent_result: u8,
        creator_evidence_hash: [u8; 32],
        opponent_evidence_hash: [u8; 32],
    ) -> Result<()> {
        require!(
            ctx.accounts.challenge.status == STATUS_ACTIVE,
            VoloError::NotActive
        );
        require!(
            (REPORT_PASS..=REPORT_UNVERIFIED).contains(&creator_result),
            VoloError::ResultOutOfRange
        );
        require!(
            (REPORT_PASS..=REPORT_UNVERIFIED).contains(&opponent_result),
            VoloError::ResultOutOfRange
        );
        if creator_result == REPORT_PASS {
            require!(creator_evidence_hash != ZERO_HASH, VoloError::EvidenceZero);
        }
        if opponent_result == REPORT_PASS {
            require!(opponent_evidence_hash != ZERO_HASH, VoloError::EvidenceZero);
        }

        // UNVERIFIED (3) is treated as FAIL: only an explicit PASS counts.
        let c_pass = creator_result == REPORT_PASS;
        let o_pass = opponent_result == REPORT_PASS;
        let stake = ctx.accounts.challenge.stake_amount;

        let bump = ctx.accounts.challenge.bump;
        let creator_key = ctx.accounts.challenge.creator;
        let opponent_key = ctx.accounts.challenge.opponent;
        let nonce_le = ctx.accounts.challenge.nonce.to_le_bytes();
        let seeds: &[&[u8]] = &[
            CHALLENGE_SEED,
            creator_key.as_ref(),
            opponent_key.as_ref(),
            nonce_le.as_ref(),
            &[bump],
        ];

        let token_program = ctx.accounts.token_program.to_account_info();
        let escrow = ctx.accounts.escrow.to_account_info();
        let authority = ctx.accounts.challenge.to_account_info();
        let creator_token = ctx.accounts.creator_token.to_account_info();
        let opponent_token = ctx.accounts.opponent_token.to_account_info();

        match (c_pass, o_pass) {
            (true, false) => {
                transfer_escrow(token_program, escrow, creator_token, authority, seeds, stake * 2)?;
                ctx.accounts.challenge.status = STATUS_SETTLED;
                ctx.accounts.challenge.result = RESULT_CREATOR_WON;
            }
            (false, true) => {
                transfer_escrow(token_program, escrow, opponent_token, authority, seeds, stake * 2)?;
                ctx.accounts.challenge.status = STATUS_SETTLED;
                ctx.accounts.challenge.result = RESULT_OPPONENT_WON;
            }
            (true, true) => {
                transfer_escrow(token_program.clone(), escrow.clone(), creator_token, authority.clone(), seeds, stake)?;
                transfer_escrow(token_program, escrow, opponent_token, authority, seeds, stake)?;
                ctx.accounts.challenge.status = STATUS_SETTLED;
                ctx.accounts.challenge.result = RESULT_BOTH_PASS;
            }
            (false, false) => {
                ctx.accounts.challenge.status = STATUS_BOTH_FAIL_LOCKED;
                ctx.accounts.challenge.result = RESULT_BOTH_FAIL;
            }
        }

        let clock = Clock::get()?;
        ctx.accounts.challenge.creator_evidence_hash = creator_evidence_hash;
        ctx.accounts.challenge.opponent_evidence_hash = opponent_evidence_hash;
        ctx.accounts.challenge.settled_at = clock.unix_timestamp;

        bump_reputation(&mut ctx.accounts.creator_reputation, c_pass, clock.unix_timestamp);
        bump_reputation(&mut ctx.accounts.opponent_reputation, o_pass, clock.unix_timestamp);
        Ok(())
    }

    /// Sweep a both-fail-locked escrow to the treasury once the lock hour has passed.
    ///
    /// Permissionless in spirit but oracle-signed in practice: the oracle is the only
    /// operator this deployment has, and it already holds settlement authority, so adding
    /// a second trust mode would change nothing except the audit surface.
    pub fn resolve_lock(ctx: Context<ResolveLock>) -> Result<()> {
        require!(
            ctx.accounts.challenge.status == STATUS_BOTH_FAIL_LOCKED,
            VoloError::NotLocked
        );
        let clock = Clock::get()?;
        require!(
            clock.unix_timestamp >= ctx.accounts.challenge.end_time + LOCK_SECONDS,
            VoloError::TooEarlyToResolve
        );

        let escrow_balance = ctx.accounts.escrow.amount;
        let bump = ctx.accounts.challenge.bump;
        let creator_key = ctx.accounts.challenge.creator;
        let opponent_key = ctx.accounts.challenge.opponent;
        let nonce_le = ctx.accounts.challenge.nonce.to_le_bytes();
        let seeds: &[&[u8]] = &[
            CHALLENGE_SEED,
            creator_key.as_ref(),
            opponent_key.as_ref(),
            nonce_le.as_ref(),
            &[bump],
        ];

        transfer_escrow(
            ctx.accounts.token_program.to_account_info(),
            ctx.accounts.escrow.to_account_info(),
            ctx.accounts.treasury_token.to_account_info(),
            ctx.accounts.challenge.to_account_info(),
            seeds,
            escrow_balance,
        )?;

        ctx.accounts.challenge.status = STATUS_LOCKED_RESOLVED;
        Ok(())
    }

    /// Refund the creator if the opponent never joined (status Created, >1h old).
    pub fn refund_unjoined(ctx: Context<RefundUnjoined>) -> Result<()> {
        require!(
            ctx.accounts.challenge.status == STATUS_CREATED,
            VoloError::NotCreated
        );
        let clock = Clock::get()?;
        require!(
            clock.unix_timestamp >= ctx.accounts.challenge.start_time + 3600,
            VoloError::TooEarlyToRefund
        );

        let bump = ctx.accounts.challenge.bump;
        let creator_key = ctx.accounts.challenge.creator;
        let opponent_key = ctx.accounts.challenge.opponent;
        let nonce_le = ctx.accounts.challenge.nonce.to_le_bytes();
        let seeds: &[&[u8]] = &[
            CHALLENGE_SEED,
            creator_key.as_ref(),
            opponent_key.as_ref(),
            nonce_le.as_ref(),
            &[bump],
        ];

        transfer_escrow(
            ctx.accounts.token_program.to_account_info(),
            ctx.accounts.escrow.to_account_info(),
            ctx.accounts.creator_token.to_account_info(),
            ctx.accounts.challenge.to_account_info(),
            seeds,
            ctx.accounts.challenge.stake_amount,
        )?;

        ctx.accounts.challenge.status = STATUS_REFUNDED;
        Ok(())
    }
}

fn transfer_escrow<'info>(
    token_program: AccountInfo<'info>,
    escrow: AccountInfo<'info>,
    destination: AccountInfo<'info>,
    authority: AccountInfo<'info>,
    signer_seeds: &[&[u8]],
    amount: u64,
) -> Result<()> {
    // Bound to a name rather than written inline as `&[signer_seeds]`: an inline temporary
    // is dropped at the end of the `let` statement while `cpi` still borrows it, which is
    // E0716. The binding lives to the end of the function, so the borrow is valid.
    let signer_seeds_array = [signer_seeds];
    let cpi = CpiContext::new_with_signer(
        token_program,
        Transfer {
            from: escrow,
            to: destination,
            authority,
        },
        &signer_seeds_array,
    );
    token::transfer(cpi, amount)
}

/// The spec's streak rule: a verified PASS keeps the streak alive (and raises the record);
/// FAIL or UNVERIFIED is a broken commitment and resets the streak to zero. `kept` and
/// `broken` are lifetime counters, so a reputation PDA is a wallet's on-chain track record
/// that any future counterparty can read before staking against them.
fn bump_reputation(reputation: &mut Reputation, passed: bool, settled_at: i64) {
    if passed {
        reputation.kept = reputation.kept.saturating_add(1);
        reputation.current_streak = reputation.current_streak.saturating_add(1);
        if reputation.current_streak > reputation.longest_streak {
            reputation.longest_streak = reputation.current_streak;
        }
    } else {
        reputation.broken = reputation.broken.saturating_add(1);
        reputation.current_streak = 0;
    }
    reputation.last_settled_at = settled_at;
}

// Space: 8 disc + 3*32 pubkeys + u64 stake + (4+32) string + u32 + u32 + i64 + i64 + u8 + u8 + u8 bump
//   + u32 nonce + [u8;32] + [u8;32] + i64 settled_at = 251.
// Absolute byte offsets (post 8-byte discriminator), the contract Task 11's decodeV2Layout
// reads against: nonce@175, creator_evidence_hash@179, opponent_evidence_hash@211,
// settled_at@243. The v1 field order is untouched, so the first 175 bytes stay identical
// and the new fields are a pure append.
#[account]
pub struct Challenge {
    pub creator: Pubkey,
    pub opponent: Pubkey,
    pub token_mint: Pubkey,
    pub stake_amount: u64,
    pub target_app: String,
    pub limit_seconds: u32,
    pub window_seconds: u32,
    pub start_time: i64,
    pub end_time: i64,
    pub status: u8,
    pub result: u8,
    pub bump: u8,
    pub nonce: u32,
    pub creator_evidence_hash: [u8; 32],
    pub opponent_evidence_hash: [u8; 32],
    pub settled_at: i64,
}

impl Challenge {
    pub const LEN: usize = 8 + 32 * 3 + 8 + 4 + 32 + 4 + 4 + 8 + 8 + 1 + 1 + 1 + 4 + 32 + 32 + 8;
}

/// A wallet's settled-duel record: how many commitments they kept, how many they broke,
/// and how long their current and record streaks run. Seeded [REPUTATION_SEED, wallet],
/// so it is per-WALLET and independent of any pair (Task 11: reputationPda(wallet)).
#[account]
pub struct Reputation {
    pub kept: u32,
    pub broken: u32,
    pub current_streak: u32,
    pub longest_streak: u32,
    pub last_settled_at: i64,
    pub bump: u8,
}

impl Reputation {
    pub const LEN: usize = 8 + 4 + 4 + 4 + 4 + 8 + 1;
}

#[derive(Accounts)]
// Anchor deserializes `#[instruction(..)]` args from the instruction-data prefix in
// declared order, so every handler argument before `nonce` must be listed ahead of it —
// listing only `nonce` made the seeds read the low 4 bytes of `stake_amount` instead.
#[instruction(stake_amount: u64, target_app: String, limit_seconds: u32, window_seconds: u32, nonce: u32)]
pub struct CreateChallenge<'info> {
    #[account(
        init,
        payer = creator,
        space = Challenge::LEN,
        seeds = [CHALLENGE_SEED, creator.key().as_ref(), opponent.key().as_ref(), &nonce.to_le_bytes()],
        bump
    )]
    pub challenge: Account<'info, Challenge>,

    #[account(mut)]
    pub creator: Signer<'info>,

    /// CHECK: stored only; the opponent signs at join_challenge.
    pub opponent: UncheckedAccount<'info>,

    pub mint: Account<'info, Mint>,

    #[account(
        mut,
        associated_token::mint = mint,
        associated_token::authority = creator
    )]
    pub creator_token: Account<'info, TokenAccount>,

    #[account(
        init_if_needed,
        payer = creator,
        associated_token::mint = mint,
        associated_token::authority = challenge
    )]
    pub escrow: Account<'info, TokenAccount>,

    pub token_program: Program<'info, Token>,
    pub associated_token_program: Program<'info, AssociatedToken>,
    pub system_program: Program<'info, System>,
}

#[derive(Accounts)]
pub struct JoinChallenge<'info> {
    #[account(
        mut,
        seeds = [CHALLENGE_SEED, challenge.creator.as_ref(), challenge.opponent.as_ref(), &challenge.nonce.to_le_bytes()],
        bump = challenge.bump
    )]
    pub challenge: Account<'info, Challenge>,

    #[account(mut)]
    pub opponent: Signer<'info>,

    #[account(
        mut,
        associated_token::mint = challenge.token_mint,
        associated_token::authority = opponent
    )]
    pub opponent_token: Account<'info, TokenAccount>,

    #[account(
        mut,
        associated_token::mint = challenge.token_mint,
        associated_token::authority = challenge
    )]
    pub escrow: Account<'info, TokenAccount>,

    pub token_program: Program<'info, Token>,
    pub associated_token_program: Program<'info, AssociatedToken>,
}

#[derive(Accounts)]
pub struct SettleChallenge<'info> {
    #[account(
        mut,
        seeds = [CHALLENGE_SEED, challenge.creator.as_ref(), challenge.opponent.as_ref(), &challenge.nonce.to_le_bytes()],
        bump = challenge.bump
    )]
    pub challenge: Account<'info, Challenge>,

    #[account(mut, constraint = oracle.key() == ORACLE_AUTHORITY @ VoloError::NotOracle)]
    pub oracle: Signer<'info>,

    #[account(
        mut,
        associated_token::mint = challenge.token_mint,
        associated_token::authority = challenge
    )]
    pub escrow: Account<'info, TokenAccount>,

    #[account(
        mut,
        associated_token::mint = challenge.token_mint,
        associated_token::authority = challenge.creator
    )]
    pub creator_token: Account<'info, TokenAccount>,

    #[account(
        mut,
        associated_token::mint = challenge.token_mint,
        associated_token::authority = challenge.opponent
    )]
    pub opponent_token: Account<'info, TokenAccount>,

    #[account(
        init_if_needed,
        payer = oracle,
        space = Reputation::LEN,
        seeds = [REPUTATION_SEED, challenge.creator.as_ref()],
        bump
    )]
    pub creator_reputation: Account<'info, Reputation>,

    #[account(
        init_if_needed,
        payer = oracle,
        space = Reputation::LEN,
        seeds = [REPUTATION_SEED, challenge.opponent.as_ref()],
        bump
    )]
    pub opponent_reputation: Account<'info, Reputation>,

    pub token_program: Program<'info, Token>,
    pub associated_token_program: Program<'info, AssociatedToken>,
    pub system_program: Program<'info, System>,
}

#[derive(Accounts)]
pub struct ResolveLock<'info> {
    #[account(
        mut,
        seeds = [CHALLENGE_SEED, challenge.creator.as_ref(), challenge.opponent.as_ref(), &challenge.nonce.to_le_bytes()],
        bump = challenge.bump
    )]
    pub challenge: Account<'info, Challenge>,

    #[account(mut, constraint = oracle.key() == ORACLE_AUTHORITY @ VoloError::NotOracle)]
    pub oracle: Signer<'info>,

    /// Required as an account FIELD because the treasury ATA below is init_if_needed:
    /// Anchor refuses to derive rent/space for a token initialization against a pubkey
    /// read out of another account (join/settle read challenge.token_mint instead, since
    /// those never create a token account).
    pub mint: Account<'info, Mint>,

    #[account(
        mut,
        associated_token::mint = challenge.token_mint,
        associated_token::authority = challenge
    )]
    pub escrow: Account<'info, TokenAccount>,

    /// The treasury owner, pinned to the TREASURY const. Passed as an account because
    /// Anchor's ATA init CPIs with the authority's AccountInfo — a bare pubkey const
    /// cannot be used there.
    /// CHECK: constrained below to the program's TREASURY constant.
    #[account(constraint = treasury.key() == TREASURY)]
    pub treasury: UncheckedAccount<'info>,

    /// The treasury's ATA for this challenge's mint, created on first sweep (payer =
    /// oracle) and owned by the TREASURY pubkey thereafter.
    #[account(
        init_if_needed,
        payer = oracle,
        associated_token::mint = mint,
        associated_token::authority = treasury
    )]
    pub treasury_token: Account<'info, TokenAccount>,

    pub token_program: Program<'info, Token>,
    pub associated_token_program: Program<'info, AssociatedToken>,
    pub system_program: Program<'info, System>,
}

#[derive(Accounts)]
pub struct RefundUnjoined<'info> {
    #[account(
        mut,
        seeds = [CHALLENGE_SEED, challenge.creator.as_ref(), challenge.opponent.as_ref(), &challenge.nonce.to_le_bytes()],
        bump = challenge.bump,
        constraint = challenge.creator == creator.key() @ VoloError::NotOpponent
    )]
    pub challenge: Account<'info, Challenge>,

    #[account(mut)]
    pub creator: Signer<'info>,

    #[account(
        mut,
        associated_token::mint = challenge.token_mint,
        associated_token::authority = challenge
    )]
    pub escrow: Account<'info, TokenAccount>,

    #[account(
        mut,
        associated_token::mint = challenge.token_mint,
        associated_token::authority = challenge.creator
    )]
    pub creator_token: Account<'info, TokenAccount>,

    pub token_program: Program<'info, Token>,
    pub associated_token_program: Program<'info, AssociatedToken>,
}

#[error_code]
pub enum VoloError {
    #[msg("Stake amount must be greater than zero")]
    InvalidStake,
    #[msg("Target app label exceeds 32 bytes")]
    TargetAppTooLong,
    #[msg("Window and limit must be greater than zero")]
    InvalidWindow,
    #[msg("Challenge is not in Created status")]
    NotCreated,
    #[msg("Challenge is not in Active status")]
    NotActive,
    #[msg("Signer is not the challenge opponent")]
    NotOpponent,
    #[msg("Signer is not the oracle authority")]
    NotOracle,
    #[msg("Refund is only allowed more than 1 hour after creation")]
    TooEarlyToRefund,
    #[msg("Report result code out of range")]
    ResultOutOfRange,
    #[msg("Challenge is not both-fail-locked, so there is nothing to resolve")]
    NotLocked,
    #[msg("The lock hour has not passed yet")]
    TooEarlyToResolve,
    #[msg("A duel's two parties must be distinct wallets, or the two reputation accounts collapse into one")]
    ReputationMismatch,
    #[msg("A PASS verdict requires a nonzero evidence hash")]
    EvidenceZero,
}
