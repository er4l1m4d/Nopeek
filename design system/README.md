# Volo Design System 1.0

Open volo-design-system.html for the full spec. This folder is what you build with.

- tokens.json     all tokens (reference, system, space, radius, size, type, motion)
- kotlin/         Color.kt, Type.kt, Dimens.kt, Theme.kt for com.volo.app.ui.theme
- fonts/          static TTFs, already named for Android res/font, plus the SIL OFL licenses

Setup
1. Copy fonts/*.ttf into app/src/main/res/font/
2. Copy kotlin/*.kt into ui/theme/ and check the R import and package name
3. Wrap the app in VoloTheme { }

Fonts: Big Shoulders Display (ExtraBold 800, Black 900) and Schibsted Grotesk (Medium 500, SemiBold 600, Bold 700).
Both are SIL Open Font License 1.1, free for commercial use. Keep the license files with the fonts.

Rule of three tiers: VoloRef.* is never used in components. Components read Volo.colors.* and MaterialTheme only.
