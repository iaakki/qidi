# Qidi launcher icon — direction 1c, "Beacon"

A solid dot transmitting two arcs: the signal you notice by its absence. It matches what the
app actually is — a heartbeat whose disappearance is the alarm — and it is the same silhouette
as the status notification's small icon, so the two read as one mark.

## Colours

| Role | Hex |
| --- | --- |
| Ground | `#2e2b25` warm near-black |
| Dot + inner arc | `#c67139` terracotta |
| Outer arc | `#728157` sage |

Flat fills only — no gradients, no shadows. Taken from the Organic design system's tokens.

## Geometry (108dp canvas)

Centre 54,54. Dot r8. Inner arc r21, outer arc r33, both sweeping the right half only
(-90° to +90°), stroke 6.5, round caps. Outer extent 72.5dp — sits inside the adaptive safe
circle, so no mask clips the mark.

## Files

```
android/res/
  drawable/ic_launcher_background.xml    flat ground, full bleed
  drawable/ic_launcher_foreground.xml    the mark, centred in the safe zone
  drawable/ic_launcher_monochrome.xml    Android 13 themed-icon layer
  drawable/ic_stat_qidi.xml              24dp notification small icon
  mipmap-anydpi-v26/ic_launcher.xml      adaptive-icon manifest
svg/                                     the same shapes as plain SVG, plus a rounded preview
png/qidi-icon-512.png                    store listing / previews
png/qidi-icon-192.png, -48.png           legacy density fallbacks
png/qidi-foreground-432.png              transparent foreground layer @4x
png/qidi-monochrome-432.png              white-on-transparent mono layer @4x
```

## Notes

- Drop `android/res/` straight into the module's `src/main/res/`; point
  `android:icon="@mipmap/ic_launcher"` at it in the manifest.
- Pre-API-26 devices need square PNG fallbacks in `mipmap-*dpi/` — generate them from
  `png/qidi-icon-192.png` (48/72/96/144/192 for mdpi→xxxhdpi).
- The notification icon must stay a single flat shape: Android tints it, so any colour in
  `ic_stat_qidi.xml` is discarded.
- The dot in the vector drawables is written as an arc pair rather than a circle element —
  VectorDrawable has no `<circle>`.
