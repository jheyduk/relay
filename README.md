# Relay

A mobile companion app for [Claude Code](https://claude.ai/claude-code) sessions. See all your sessions at a glance, send messages, approve permissions, and use voice input — right from your phone.

## What it does

Relay connects your Android phone to Claude Code sessions running in [Zellij](https://zellij.dev/) on your Mac via a lightweight WebSocket server. No cloud, no Telegram, no custom backend — just a direct connection over your local network.

- **Session overview** — See all active Claude Code sessions with live status
- **Text messaging** — Send messages to any session, review conversation history
- **Permission handling** — Tap Allow/Deny on permission requests from your phone
- **Interactive prompts** — Answer single-choice, multi-choice, and free-text questions
- **Voice input** — Record audio, transcribed on your Mac via whisper.cpp
- **Notifications** — Permission requests (high priority) and completions with deep links
- **Auto-discovery** — Finds your Mac automatically via mDNS on the local network

## Architecture

```
                       WebSocket
+-----------------+  <----------->  +---------------------+
|                 |  mDNS discovery |                     |
|  Android App    |                 |  relay-server       |
|                 |                 |  (Node.js)          |
|  Kotlin/        |                 |                     |
|  Compose        |                 |  +---------------+  |
|                 |                 |  | whisper.cpp   |  |
+-----------------+                 |  +---------------+  |
                                    |        |            |
                                    |  zellij write       |
                                    |        v            |
                                    |  +---------------+  |
                                    |  | Zellij        |  |
                                    |  | Sessions      |  |
                                    |  +---------------+  |
                                    +---------------------+
                                             Mac
```

**App** (Kotlin Multiplatform + Jetpack Compose):
- Shared business logic in `shared/` (Ktor WebSocket, SQLDelight, Koin DI)
- Android UI in `androidApp/` (Material 3, Dynamic Color)

**Server** (Node.js):
- `server/relay-server.cjs` — WebSocket server with mDNS, auth, command dispatch
- `server/hooks/` — Claude Code hooks for session lifecycle and notifications
- `server/install.cjs` — Registers hooks in Claude Code settings

## Requirements

**Mac:**
- Node.js 18+
- [Zellij](https://zellij.dev/) terminal multiplexer
- [Claude Code](https://claude.ai/claude-code) CLI
- Optional: [whisper.cpp](https://github.com/ggml-org/whisper.cpp) for voice transcription (`brew install whisper-cpp`)

**Android:**
- Android 9+ (API 28)
- Same WiFi network as your Mac (or WireGuard VPN)

## Setup

### 1. Install the server

```bash
cd server
npm install
```

### 2. Register Claude Code hooks

```bash
node server/install.cjs
```

This registers the relay hooks in `~/.claude/settings.json`. They'll fire automatically when Claude Code sessions start, stop, or request permissions.

### 3. Start the server

The server starts automatically when the first Claude Code session launches (via the session-start hook). You can also start it manually:

```bash
cd server
ZELLIJ_SESSION_NAME=your-session npm start
```

On first start, a shared secret is generated at `~/.config/relay/server.json`.

### 4. Install the Android app

Build from source:

```bash
./gradlew :androidApp:installDebug
```

### 5. Connect the app

1. Open the Relay app
2. Enter the shared secret from `~/.config/relay/server.json`
3. The app discovers your Mac automatically via mDNS
4. Optional: Enter your Mac's WireGuard IPv6 for remote access

## Voice Transcription

Relay supports voice input using whisper.cpp on your Mac:

```bash
brew install whisper-cpp
```

The server uses the model at `~/.cache/whisper/ggml-base.bin` by default. For better German support:

```bash
# Download the medium multilingual model
curl -L "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-medium.bin" \
  -o ~/.cache/whisper/ggml-medium.bin
```

Configure in `~/.config/relay/server.json`:

```json
{
  "whisper_model": "/path/to/ggml-medium.bin",
  "whisper_language": "de"
}
```

## Configuration

Server config at `~/.config/relay/server.json`:

| Key | Default | Description |
|-----|---------|-------------|
| `port` | `9784` | WebSocket server port |
| `secret` | auto-generated | Shared authentication secret |
| `whisper_cli` | `/opt/homebrew/bin/whisper-cli` | Path to whisper.cpp binary |
| `whisper_model` | `~/.cache/whisper/ggml-base.bin` | Path to Whisper model |
| `whisper_language` | `de` | Transcription language |

## Tech Stack

| Component | Technology |
|-----------|-----------|
| Shared logic | Kotlin Multiplatform |
| Android UI | Jetpack Compose, Material 3 |
| Networking | Ktor Client (WebSocket) |
| Database | SQLDelight |
| DI | Koin |
| Server | Node.js, ws |
| Discovery | mDNS/Bonjour |
| Voice | whisper.cpp |

## License

MIT
