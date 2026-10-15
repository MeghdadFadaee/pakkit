# Pakkit — Pocket to Wrist.

The supplied `brand-concept.png` is a creative reference, not a screen specification or feature promise. The product uses the user's official taglines, **Pocket to Wrist.** and **Pack it. Send it. Wear it.** Copy such as “Apps, on your wrist” or percentages in the reference does not override those decisions or actual platform capabilities.

## Identity

An open parcel and a floating app tile form Pakkit's mark. Cyan and mint illuminate one face; electric violet lights the other. The detached tile suggests a small app making a deliberate journey. The same silhouette appears in the adaptive launcher, monochrome icon, splash, phone illustration and watch header. The work is implemented as vector paths, native shaders and reusable Android components; the reference board is never rendered as application UI.

## Color and typography

`BrandPalette` in `shared/.../Design.kt` provides semantic tokens for light and dark canvas, cards, body text, secondary text, borders, actions, mint statuses and errors. Principal colors: midnight `#090B17`, action purple `#7150EF`, mint `#68E9CF`, cyan `#37D7F2`. Action buttons use a readable violet gradient. Text colors are adapted rather than copying a bright accent onto every surface.

Lavender light uses subtly tinted backgrounds and white cards. Midnight dark uses restrained illumination, deep surfaces and soft lavender text. The phone follows the device theme by default; its appearance control offers explicit light and dark preferences and preserves the choice. The watch stays midnight in either system theme to limit lit pixels on OLED displays.

Typography uses Android's native sans family with explicit 400/600/650 weights, slightly tightened large headings and scalable SP sizes. Copy remains real text, not outlined lettering. The scale is deliberate: phone headlines 38 SP, section titles 19–23 SP, body 14–16 SP; watch titles 21–25 SP and supporting text 12–14 SP. Small metadata remains scrollable. There are no fixed-height text containers.

## Components and interaction

- `ParcelSceneView`: prebuilt parcel geometry, static radial lighting, a floating app tile and a finite 1.1-second entrance. No bitmap assets, blur layers or continuous particle simulation.
- `GlyphDrawable`: a consistent stroke family for navigation, watch, file, trust and status icons. Icons supplement text; decorative art is excluded from accessibility focus.
- Native buttons: gradient primary, tonal secondary, ripple feedback, meaningful disabled colors and platform confirmation haptics. Minimum height 52 DP on phone and 48 DP on watch.
- Phone cards: 26-DP corners, 20-DP padding, quiet borders and consistent spacing. Content is centered and bounded on wide windows. Navigation remains semantic and selected states are exposed to accessibility.
- `RoundScrollView`: symmetric proportional watch margins, generous end padding, rotary scrolling and an on-demand scroll indicator following the bezel. TextClock updates with the platform; no custom clock loop.
- Scroll position is retained during ordinary status refreshes. New transfer stages on the watch return to the heading so the current decision remains discoverable.
- Motion respects system animation settings and battery saver. Decorative watch art is static. Phone transitions are short, finite and cancelled when detached or the window loses focus. Receiving progress remains indeterminate because the Data Layer does not supply a trustworthy byte percentage.

## Product truth

Brand language must never imply a compatibility guarantee or a security bypass. An APK is “Installed” only after Android reports success. Source approval, explicit watch review, system consent, unsupported firmware handling, signature/integrity checks and Google Play policy boundaries remain in force. The reference's installed-app catalog and imagined battery percentages are not implemented features.

## Validation

Automated contrast checks cover normal body and secondary copy on all base surfaces, status badges and both action-gradient endpoints at 4.5:1 or better. Device tests exercise the actual appearance chooser and theme persistence, navigation restoration and the unchanged installation consent boundary. Full TalkBack and physical-watch accessibility validation remain release gates.

Design references: [Wear accessibility](https://developer.android.com/training/wearables/accessibility), [round screen guidance](https://developer.android.com/design/ui/wear/guides/get-started/design-for-wearables), [Wear app quality](https://developer.android.com/docs/quality-guidelines/wear-app-quality), and [power guidance](https://developer.android.com/training/wearables/apps/power). These inform the implementation; their requirements are not a substitute for Play review or physical-device testing.
