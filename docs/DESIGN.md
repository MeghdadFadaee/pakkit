# Pakkit design

**Pakkit — Pocket to Wrist.** Secondary tagline: **Pack it. Send it. Wear it.** The identity is friendly, playful and polished: an open parcel with a floating app tile, soft geometry, violet lighting and mint/cyan accents.

## Implemented assets

- `app/src/main/res/drawable/pakkit_icon.xml`: gradient parcel artwork used by the watch header, splash and adaptive launcher.
- `pakkit_mark.xml`: monochrome launcher/notification mark.
- `app/src/main/assets/web/index.html`: original inline vector parcel illustration and compact wordmark icon. These are real SVG elements, not screenshots.
- `ui/WatchDesign.kt`: only the native watch’s actual text, action, badge, color and shape primitives.
- `ui/GlyphDrawable.kt`: check/error glyphs, paired with explanatory text.
- `ui/RoundScrollView.kt`: bezel position indication and native rotary/touch scrolling, redrawn in response to scroll changes.

## Themes and layout

The native watch UI uses midnight `#090 B 17` to limit lit OLED pixels, lavender-white text and semantic mint/error surfaces. Body and secondary copy, badges and action gradient endpoints have automated contrast checks. Actions have a minimum 48 dp height and scalable text. Proportional margins and bottom padding allow controls to move into the usable center of a round display. The QR is sized to screen width and positioned within the circular safe area; the full URL and trust disclosure remain scrollable below it.

The browser follows prefers-color-scheme with an explicit in-session toggle. Dark is midnight with violet/cyan lighting; light uses soft lavender and white surfaces. A two-column desktop layout becomes a single-column layout at smaller widths. The maintained CSS, HTML and JavaScript are readable source files, formatted with the optional web development tools. Native browser controls, semantic headings, focus rings, file labels and a live status region support keyboard and assistive access.

## Motion and product states

The watch has static decorative artwork. Browser motion is a finite parcel entrance and short hover/press feedback; prefers-reduced-motion disables these. There are no continuous particle effects or decorative render loops. Polling pauses in hidden tabs.

Upload progress reflects actual XHR bytes sent and socket bytes read. “100% sent” transitions to package checking, never to an installation-success claim. The browser distinguishes upload, verification, watch review, system approval, failure and success. Android STATUS_SUCCESS is the authority for “installed”. Cancelling an HTTP request does not imply a rollback of a file the watch may already have received.

Keep copy precise: preflight does not certify safety or runtime compatibility. Source approval and system consent are explicit decisions. The trust disclosure describes unencrypted local HTTP without claiming encryption.
