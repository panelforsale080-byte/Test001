# Test001 — Smart Auto-Clicker (Gesture Record & Replay)

Android app na may floating overlay panel para mag-record, mag-save, at mag-replay ng touch gestures — may **Smart Gesture (relative anchor)** para gumana ang replay kahit iba ang posisyon.

## Setup (permissions)
1. **Overlay permission** — open app → "Enable Overlay Permission" → allow "Display over other apps".
2. **Accessibility** — "Enable Accessibility Service" → hanapin ang "Test001 Auto-Clicker" → ON → Allow.
3. Tap **"Start Floating Panel"** — lilitaw ang draggable panel + persistent notification.

## Record a gesture
1. Sa floating panel, tap **🔴 REC**.
2. Gawin ang gesture sa screen (tap/swipe/drag) — nakikita mo ang "● REC" indicator.
3. Tap ulit ang **🔴 REC** para huminto.
4. Tap **💾 Save** para i-save sa `files/gestures/<name>.json`.

## Replay
- **▶ Play** — i-replay ang huling recording (o ang napili sa Saved Gestures).
- **loops** — ilang beses ulitin; `-1` = infinite.
- **speed** — `0.5` (half speed), `1.0` (normal), `2.0` (double).
- **⏹ Stop** — itigil agad ang replay.

## Smart Gesture / Anchor (relative positioning)
Bawat recording ay may naka-save na `startX,startY`. Sa replay:
```
translatedX = recordedX - recordedStartX + anchorX
translatedY = recordedY - recordedStartY + anchorY
```
1. Tap **🎯 Set Anchor** → lilitaw ang draggable crosshair → ilagay sa kasalukuyang starting point (hal. joystick center) → **SET ANCHOR**.
2. I-play ang gesture — ililipat ang buong gesture relative sa bagong anchor.
3. **⚙ Clear** — balik sa absolute (recorded) coordinates.
- Sa **Saved Gestures** screen: **🎯▶** = pick anchor then play; may Rename/Delete din.

## Build
```bash
./gradlew assembleDebug
```
APK: `app/build/outputs/apk/debug/app-debug.apk`. CI (GitHub Actions) auto-builds sa bawat push sa `main` at nag-a-upload ng artifact na `Test001-debug-apk`; releases ay ginagawa kapag nag-push ng tag.
