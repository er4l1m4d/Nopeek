# Volo

Native Android Compose prototype for the Volo accountability app.

## Current scope

This first UI slice is demo-data only. It includes:

- Home dashboard with active duel, usage metrics, and verification state
- Challenges list with active, pending, and completed examples
- Create duel form shell
- Dark immersive active-duel screen with user progress ring and opponent bar
- Activity timeline
- Profile with wallet, Seeker, SKR, and usage-access states

Wallet, usage access, Supabase, Solana, oracle, and Seeker behavior are intentionally not connected yet.

## Build environment

The project targets Android API 33 and expects Java 17. The current workspace has the Android SDK platform and build tools installed, but no Gradle distribution or Android Studio executable was available during initial verification. Open the project in Android Studio, allow it to provision the Gradle wrapper, then run the `app` configuration on a device or API 33 emulator.

The intended SDK path is:

`C:\Users\hp\AppData\Local\Android\Sdk`
