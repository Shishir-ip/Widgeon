<div align="center">

# Widgeon

**Describe a widget. AI builds it. It lands on your home screen.**

Widgeon is an Android app that turns plain-language prompts into beautiful, live home-screen widgets — rendered in a strict, professional black & white design language.

</div>

---

## ✨ What it does

Type something like *“a minimal clock with the date, dark glass style”* and Widgeon's AI designs a real Android widget for you: layout, typography, spacing, colors — all composed into a widget you can pin to your home screen and edit again with more prompts.

### Features

- 🪄 **Prompt → Widget** — one text box, one button. AI designs a complete widget spec (layout, text, colors, spacing) and Widgeon renders it pixel-perfect.
- 🖼️ **Photo widgets** — ask for a picture frame or rotating gallery, then attach photos in the editor; image slots (with optional rotation) render right on your home screen.
- 🧩 **Real home-screen widgets** — five sizes (1×1, 2×2, 4×2, 2×4, 4×4) appear in your launcher's widget picker. Resize freely; the widget re-renders at its actual size.
- ⏰ **Always-on live clock** — clock/date elements use launcher-side TextClock overlays (Android 12+), so they tick every minute even when Widgeon isn't running; battery/weather refresh periodically.
- ✏️ **Edit with AI** — every widget has an *edit with AI* chat: “make the clock bigger and add seconds”. Applied as a new version.
- ↩️ **Undo / Redo** — every change is a version in the editor's history. Don't like the AI's idea? Undo it.
- 📌 **Add to Home** — one-tap pin request for the widget's matching size (Android 8+); right after pinning, the design chooser opens so the widget is never left blank.
- 🎛️ **Advanced options** — grid size (presets or custom up to 5×5), style presets (Minimal, Dark Glass, Light Clean, Outline, Bold, Editorial), accent color, corner radius, live-data toggles and free-form notes for the AI.
- 🔔 **Background generation** — a foreground service keeps a progress notification alive if you leave the app, and posts a completion notification when your widget is ready.
- 🌗 **Themes** — light, dark or follow-system, in a monochrome professional palette.
- 📳 **Haptics everywhere** — a full vibration vocabulary for taps, toggles, undo/redo and generation milestones (toggleable).
- 🧯 **Friendly failures** — running out of free-tier quota shows a clear, styled dialog instead of a cryptic error.
- 🎬 **Premium feel** — springy press animations, staggered entrances, a breathing orb during generation, a success burst, shimmering skeletons and smooth screen transitions.

## 🤖 Supported AI providers

| Provider | Default model | Also offered | Key |
|---|---|---|---|
| **Google Gemini** (default) | `gemini-3.5-flash-lite` | `gemini-3.6-flash`, custom name | Free tier at [aistudio.google.com](https://aistudio.google.com/apikey) |
| **Groq** | `openai/gpt-oss-120b` | `qwen/qwen3.6-27b`, custom name | Free tier at [console.groq.com](https://console.groq.com/keys) |
| **OpenRouter** | `openai/gpt-oss-120b` | any model on [openrouter.ai/models](https://openrouter.ai/models) | [openrouter.ai/keys](https://openrouter.ai/keys) |
| **Custom (OpenAI-compatible)** | — | any `/v1/chat/completions` endpoint (LM Studio, Ollama, proxies…) | optional |

API keys are stored in **EncryptedSharedPreferences** and excluded from backups. All providers can be changed in **Settings → AI Providers**, each with a **Test connection** button.

## 📲 Install

1. Grab the latest signed APK from [**Releases**](../../releases).
2. Open it on your Android 8.0+ device and allow installing from your browser/files (Play Protect may ask you to confirm — Widgeon is signed but not Play-published).
3. Launch **Widgeon**, complete the 3-step setup (pick provider → paste key → permissions).
4. Describe a widget and hit **Generate**.

### Adding widgets to your home screen

**Option A (recommended):** tap **Add to Home** inside the editor and confirm your launcher's pin dialog.

**Option B:** long-press your home screen → **Widgets** → **Widgeon** → drag a size → pick a design from the attachment screen.

Placed widgets update every minute with fresh live data and re-render when you resize them.

## 🛠️ Build it yourself

Requirements: JDK 17+, Android SDK (API 35), Internet on first build.

```bash
git clone https://github.com/<you>/Widgeon.git
cd Widgeon
./gradlew assembleRelease          # or assembleDebug
```

### Signing your own release build

Create `keystore.properties` in the project root (it is git-ignored):

```properties
storeFile=widgeon-release.jks
storePassword=yourStorePassword
keyAlias=yourAlias
keyPassword=yourKeyPassword
```

Generate a keystore if you don't have one:

```bash
keytool -genkeypair -v -keystore widgeon-release.jks -alias widgeon \
  -keyalg RSA -keysize 4096 -validity 10950
```

Then `./gradlew assembleRelease` — the APK lands in `app/build/outputs/apk/release/`.

> 🔐 **Never commit `keystore.properties`, `*.jks` or `*.keystore`.** Both are already in `.gitignore`.

## 🧱 Tech notes

- **Kotlin + Jetpack Compose (Material 3)**, single-module app, minSdk 26 / targetSdk 35.
- Widgets render through a custom **Canvas layout engine** (`DesignRenderer`) that measures and draws the AI's element tree — the same code path powers the in-app preview *and* the real widget, so what you see is exactly what you get.
- The AI returns a strict JSON spec; a resilient parser salvages markdown fences, prose, truncated JSON and unknown fields, then sanitizes every value before rendering.
- Live widgets refresh via a minute-level `AlarmManager` schedule that runs only while a widget is bound; weather comes from Open-Meteo (geocoding + forecast, cached 30 min).
- Design store is a plain JSON document in app-private storage; bindings (`appWidgetId → design`) live in preferences.

## 📁 Project layout

```
app/src/main/java/com/widgeon/app/
├── ai/          # providers (Gemini, OpenAI-compatible), prompt builder, JSON parser, generation engine
├── data/        # design model, repository, encrypted prefs
├── render/      # canvas layout engine, color utils, live-data + weather
├── widgets/     # 5 AppWidgetProviders, host, config activity, updater
├── service/     # foreground keeper service (progress notification)
├── notify/      # channels + progress/result notifications
└── ui/          # theme, components, screens (onboarding/home/editor/collection/settings)
```

## 📄 License

Released under the [MIT License](LICENSE).

---

<div align="center">
Made with ✦ and a strict monochrome palette.
</div>
