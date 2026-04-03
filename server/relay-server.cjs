#!/usr/bin/env node
// Relay WebSocket server — standalone process for the relay repo.
// Accepts authenticated WebSocket connections from the Relay Android app,
// receives hook messages via Unix domain socket IPC, and advertises via mDNS.
// Dispatches commands directly via `zellij action write-chars` — no zellij-claude dependency.

const { WebSocketServer } = require('ws');
const net = require('net');
const fs = require('fs');
const path = require('path');
const os = require('os');
const crypto = require('crypto');
const { spawn, execFile } = require('child_process');

// --- Constants ---
const CONFIG_PATH = path.join(os.homedir(), '.config', 'relay', 'server.json');
const PID_FILE = '/tmp/zellij-claude-relay.pid';
const SOCK_FILE = '/tmp/zellij-claude-relay.sock';
const DEFAULT_PORT = 9784;

// --- Config ---
function loadOrCreateConfig() {
  let config = {};
  try {
    config = JSON.parse(fs.readFileSync(CONFIG_PATH, 'utf8'));
  } catch {
    // File missing or invalid — start fresh
  }

  let changed = false;

  if (!config.port) {
    config.port = DEFAULT_PORT;
    changed = true;
  }

  if (!config.secret) {
    config.secret = crypto.randomBytes(32).toString('hex');
    changed = true;
  }

  if (!config.transport) {
    config.transport = 'websocket';
    changed = true;
  }

  if (!config.whisper_cli) {
    config.whisper_cli = '/opt/homebrew/bin/whisper-cli';
    changed = true;
  }

  if (!config.whisper_model) {
    config.whisper_model = path.join(os.homedir(), '.cache', 'whisper', 'ggml-base.bin');
    changed = true;
  }

  if (changed) {
    const dir = path.dirname(CONFIG_PATH);
    fs.mkdirSync(dir, { recursive: true });
    fs.writeFileSync(CONFIG_PATH, JSON.stringify(config, null, 2) + '\n');
  }

  return config;
}

const config = loadOrCreateConfig();

// --- Whisper availability check ---
let whisperAvailable = false;
try {
  if (fs.existsSync(config.whisper_cli) && fs.existsSync(config.whisper_model)) {
    whisperAvailable = true;
    process.stderr.write(`[relay-server] Whisper available: ${config.whisper_cli} with model ${config.whisper_model}\n`);
  } else {
    if (!fs.existsSync(config.whisper_cli)) {
      process.stderr.write(`[relay-server] Warning: whisper-cli not found at ${config.whisper_cli} — audio transcription disabled\n`);
    }
    if (!fs.existsSync(config.whisper_model)) {
      process.stderr.write(`[relay-server] Warning: whisper model not found at ${config.whisper_model} — audio transcription disabled\n`);
    }
  }
} catch (err) {
  process.stderr.write(`[relay-server] Warning: whisper check failed: ${err.message}\n`);
}

// --- State ---
let appSocket = null;
let appConnected = false;
let disconnectedAt = null;
let mdnsChild = null;

// --- Zellij Dispatch Helpers ---

/**
 * Find the Zellij session ID that has a tab named @{kuerzel}.
 * Looks up /tmp/zellij-claude-tab-{sessionId} files whose content matches the kuerzel.
 */
function findSessionForKuerzel(kuerzel) {
  // Verify the kuerzel exists in tab files (session is active)
  try {
    const tabFiles = fs.readdirSync('/tmp').filter(f => f.startsWith('zellij-claude-tab-'));
    const found = tabFiles.some(f => {
      try {
        return fs.readFileSync(`/tmp/${f}`, 'utf8').trim() === kuerzel;
      } catch { return false; }
    });
    if (!found) return null;
  } catch { return null; }

  // Return the Zellij session name (not the Claude session UUID)
  // The relay-server inherits ZELLIJ_SESSION_NAME from the hook that started it
  return process.env.ZELLIJ_SESSION_NAME || null;
}

/**
 * Find the pane ID for the tab named @{kuerzel} in the given Zellij session.
 * Uses `zellij action list-panes --json --tab` to get pane/tab mapping.
 * Returns a promise that resolves to the pane ID string or null.
 */
function findPaneForTab(sessionId, kuerzel) {
  return new Promise((resolve) => {
    execFile('zellij', ['--session', sessionId, 'action', 'list-panes', '--json', '--tab', '--state'], {
      timeout: 5000,
      encoding: 'utf8',
    }, (err, stdout) => {
      if (err) {
        process.stderr.write(`[relay-server] list-panes error: ${err.message}\n`);
        resolve(null);
        return;
      }
      try {
        const panes = JSON.parse(stdout);
        // Find a pane whose tab name matches @{kuerzel} and is focused in that tab
        const target = panes.find(p =>
          p.tab_name === `@${kuerzel}` && p.is_focused && !p.is_plugin
        );
        if (target) {
          resolve(String(target.id));
          return;
        }
        // Fallback: any non-plugin pane in the matching tab
        const anyInTab = panes.find(p => p.tab_name === `@${kuerzel}` && !p.is_plugin);
        if (anyInTab) {
          resolve(String(anyInTab.id));
          return;
        }
      } catch (e) {
        process.stderr.write(`[relay-server] list-panes parse error: ${e.message}\n`);
      }
      resolve(null);
    });
  });
}

/**
 * Write chars to a specific pane in a Zellij session.
 * Uses --pane-id for targeted dispatch without switching tabs.
 */
function writeCharsToPane(sessionId, paneId, chars) {
  return new Promise((resolve) => {
    const args = ['--session', sessionId, 'action', 'write-chars', '--pane-id', `terminal_${paneId}`, chars];
    execFile('zellij', args, { timeout: 5000 }, (err) => {
      if (err) {
        process.stderr.write(`[relay-server] write-chars error: ${err.message}\n`);
      }
      resolve(!err);
    });
  });
}

/**
 * Write raw bytes to a specific pane (e.g. Enter key = byte 13).
 */
function writeToPane(sessionId, paneId, ...bytes) {
  return new Promise((resolve) => {
    const args = ['--session', sessionId, 'action', 'write', '--pane-id', `terminal_${paneId}`, ...bytes.map(String)];
    execFile('zellij', args, { timeout: 5000 }, (err) => {
      if (err) {
        process.stderr.write(`[relay-server] write error: ${err.message}\n`);
      }
      resolve(!err);
    });
  });
}

/**
 * Dispatch a command to the session identified by kuerzel.
 * Writes the message text followed by Enter to the focused pane in the matching tab.
 */
async function dispatchCommand(kuerzel, message) {
  const sessionId = findSessionForKuerzel(kuerzel);
  if (!sessionId) {
    process.stderr.write(`[relay-server] No session found for @${kuerzel}\n`);
    return;
  }

  const paneId = await findPaneForTab(sessionId, kuerzel);
  if (!paneId) {
    process.stderr.write(`[relay-server] No pane found for @${kuerzel} in session ${sessionId}\n`);
    // Fallback: switch to tab and write to focused pane
    execFile('zellij', ['--session', sessionId, 'action', 'go-to-tab-name', `@${kuerzel}`], {
      timeout: 5000,
    }, (err) => {
      if (err) {
        process.stderr.write(`[relay-server] go-to-tab-name error: ${err.message}\n`);
        return;
      }
      execFile('zellij', ['--session', sessionId, 'action', 'write-chars', message], {
        timeout: 5000,
      }, (err2) => {
        if (err2) { process.stderr.write(`[relay-server] write-chars fallback error: ${err2.message}\n`); return; }
        execFile('zellij', ['--session', sessionId, 'action', 'write', '13'], { timeout: 5000 });
      });
    });
    return;
  }

  process.stderr.write(`[relay-server] Dispatching to @${kuerzel} pane terminal_${paneId} in session ${sessionId}\n`);
  await writeCharsToPane(sessionId, paneId, message);
  await writeToPane(sessionId, paneId, 13); // Enter key (carriage return)
}

/**
 * Compute the keystroke sequence for an answer payload.
 * Returns the full string to send to the TUI pane.
 */
function computeKeystrokeSequence(msg) {
  const downArrow = '\x1b[B';

  if (msg.type === 'single') {
    // Single choice: number key + Enter
    return String(msg.selections[0]) + '\n';
  }

  if (msg.type === 'multi') {
    // Multi choice: toggle numbers + Down arrows to Submit + Enter
    // Note: individual numbers sent separately with delays (see dispatchMultiSelectAnswer)
    const toggles = msg.selections.map(n => String(n));
    const downs = downArrow.repeat(msg.option_count);
    return toggles.join('') + downs + '\n';
  }

  if (msg.type === 'text') {
    // Free text: Down past options to Other + Enter + text + Enter
    const downs = downArrow.repeat(msg.option_count);
    return downs + '\n' + msg.text + '\n';
  }

  return '';
}

/**
 * Dispatch raw keystrokes to a pane (no appended newline — sequence already has them).
 */
async function dispatchKeystrokesToPane(kuerzel, keystrokes) {
  const sessionId = findSessionForKuerzel(kuerzel);
  if (!sessionId) {
    process.stderr.write(`[relay-server] No session found for @${kuerzel}\n`);
    return;
  }

  const paneId = await findPaneForTab(sessionId, kuerzel);
  if (!paneId) {
    process.stderr.write(`[relay-server] No pane found for @${kuerzel} in session ${sessionId}\n`);
    return;
  }

  await writeCharsToPane(sessionId, paneId, keystrokes);
}

/**
 * Dispatch a multi-select answer with delays between each toggle.
 * Each number key is sent separately with a 50ms gap so the TUI can process each toggle.
 * After all toggles, sends Down arrows to Submit + Enter.
 */
async function dispatchMultiSelectAnswer(kuerzel, selections, optionCount) {
  const sessionId = findSessionForKuerzel(kuerzel);
  if (!sessionId) {
    process.stderr.write(`[relay-server] No session found for @${kuerzel}\n`);
    return;
  }

  const paneId = await findPaneForTab(sessionId, kuerzel);
  if (!paneId) {
    process.stderr.write(`[relay-server] No pane found for @${kuerzel} in session ${sessionId}\n`);
    return;
  }

  const delay = (ms) => new Promise(resolve => setTimeout(resolve, ms));
  const downArrow = '\x1b[B';

  // Send each selection number individually with 50ms delay between toggles
  for (let i = 0; i < selections.length; i++) {
    await writeCharsToPane(sessionId, paneId, String(selections[i]));
    if (i < selections.length - 1) {
      await delay(50);
    }
  }

  // Wait before sending Down arrows + Enter
  await delay(50);

  // Down arrows to reach Submit button + Enter
  const submitSequence = downArrow.repeat(optionCount) + '\n';
  await writeCharsToPane(sessionId, paneId, submitSequence);
}

/**
 * List active sessions from Zellij tabs.
 * Returns session list via the app WebSocket connection.
 */
function listSessions(sessionId) {
  execFile('zellij', ['--session', sessionId, 'action', 'list-tabs', '--json', '--state'], {
    timeout: 5000,
    encoding: 'utf8',
  }, (err, stdout) => {
    if (err) {
      process.stderr.write(`[relay-server] list-tabs error: ${err.message}\n`);
      return;
    }
    try {
      const tabs = JSON.parse(stdout);
      const sessions = tabs
        .filter(t => t.name && t.name.startsWith('@'))
        .map(t => ({ name: t.name.slice(1), active: t.active }));
      if (appSocket && appSocket.readyState === 1) {
        appSocket.send(JSON.stringify({
          type: 'session_list',
          sessions,
          timestamp: Date.now(),
        }));
      }
    } catch (e) {
      process.stderr.write(`[relay-server] list-tabs parse error: ${e.message}\n`);
    }
  });
}

/**
 * Send all active sessions to the app on reconnect.
 * Reads /tmp/zellij-claude-tab-* files to build the session list,
 * then sends a session_list message so the app doesn't need to /ls manually.
 */
function sendReconnectSync() {
  try {
    if (!appSocket || appSocket.readyState !== 1) return;

    const tabFiles = fs.readdirSync('/tmp').filter(f => f.startsWith('zellij-claude-tab-'));
    const sessions = [];

    for (const f of tabFiles) {
      try {
        const kuerzel = fs.readFileSync(`/tmp/${f}`, 'utf8').trim();
        if (kuerzel) {
          sessions.push({ name: kuerzel, active: true });
        }
      } catch {}
    }

    if (sessions.length > 0) {
      appSocket.send(JSON.stringify({
        type: 'session_list',
        sessions,
        timestamp: Date.now(),
      }));
      process.stderr.write(`[relay-server] Reconnect sync: sent ${sessions.length} active session(s)\n`);
    }
  } catch (err) {
    process.stderr.write(`[relay-server] Reconnect sync error: ${err.message}\n`);
  }
}

// --- Audio Transcription ---

/**
 * Transcribe audio data using whisper-cli and send the transcript back to the app.
 * Writes the audio buffer to a temp file, runs whisper-cli, parses output, cleans up.
 */
async function transcribeAudio(kuerzel, audioBuffer) {
  if (!whisperAvailable) {
    if (appSocket && appSocket.readyState === 1) {
      appSocket.send(JSON.stringify({
        type: 'transcript',
        session: kuerzel,
        text: '',
        error: 'whisper-cli or model not available',
      }));
    }
    return;
  }

  const tmpFile = `/tmp/relay-audio-${Date.now()}.wav`;

  try {
    fs.writeFileSync(tmpFile, audioBuffer);

    const transcript = await new Promise((resolve, reject) => {
      execFile(config.whisper_cli, ['-m', config.whisper_model, '--no-timestamps', '-nt', tmpFile], {
        timeout: 30000,
        encoding: 'utf8',
        maxBuffer: 1024 * 1024,
      }, (err, stdout, stderr) => {
        if (err) {
          reject(err);
          return;
        }
        resolve(stdout.trim());
      });
    });

    process.stderr.write(`[relay-server] Transcribed ${audioBuffer.length} bytes audio for @${kuerzel} -> "${transcript.slice(0, 80)}${transcript.length > 80 ? '...' : ''}"\n`);

    if (appSocket && appSocket.readyState === 1) {
      appSocket.send(JSON.stringify({
        type: 'transcript',
        session: kuerzel,
        text: transcript,
      }));
    }
  } catch (err) {
    process.stderr.write(`[relay-server] Transcription failed for @${kuerzel}: ${err.message}\n`);

    if (appSocket && appSocket.readyState === 1) {
      appSocket.send(JSON.stringify({
        type: 'transcript',
        session: kuerzel,
        text: '',
        error: err.message,
      }));
    }
  } finally {
    try { fs.unlinkSync(tmpFile); } catch {}
  }
}

// --- WebSocket Server ---
const wss = new WebSocketServer({ port: config.port || DEFAULT_PORT });

wss.on('connection', (ws, req) => {
  // Authenticate via query param ?token=SECRET
  const url = new URL(req.url, `http://localhost:${config.port}`);
  const token = url.searchParams.get('token');

  if (token !== config.secret) {
    ws.close(4001, 'Unauthorized');
    return;
  }

  // Only one app connection at a time
  if (appSocket && appSocket.readyState === 1) {
    appSocket.close(4002, 'Replaced by new connection');
  }

  appSocket = ws;
  appConnected = true;
  disconnectedAt = null;
  process.stderr.write(`[relay-server] App connected from ${req.socket.remoteAddress}\n`);

  // Send active sessions after a short delay so the app has time to register message handlers
  setTimeout(() => sendReconnectSync(), 500);

  ws.on('message', (data, isBinary) => {
    process.stderr.write(`[relay-server] on('message') isBinary=${isBinary}, len=${data.length}\n`);
    // Binary frame: audio data with kuerzel prefix
    if (isBinary && data.length > 2) {
      const kuerzelLen = data.readUInt16BE(0);
      if (kuerzelLen > 0 && kuerzelLen < data.length - 2) {
        const kuerzel = data.slice(2, 2 + kuerzelLen).toString('utf8');
        const audioBuffer = data.slice(2 + kuerzelLen);
        if (audioBuffer.length > 0) {
          process.stderr.write(`[relay-server] Received ${audioBuffer.length} bytes audio for @${kuerzel}\n`);
          transcribeAudio(kuerzel, audioBuffer);
        }
      }
      return;
    }

    try {
      const msg = JSON.parse(data.toString());
      process.stderr.write(`[relay-server] Received: action=${msg.action} kuerzel=${msg.kuerzel} message=${(msg.message||'').slice(0,50)}\n`);

      if (msg.action === 'command') {
        // Dispatch command directly via Zellij write-chars
        dispatchCommand(msg.kuerzel, msg.message);
      } else if (msg.action === 'raw_command') {
        if (msg.command === 'ls') {
          // List sessions directly from Zellij tabs
          const zellijSession = process.env.ZELLIJ_SESSION_NAME;
          if (zellijSession) {
            listSessions(zellijSession);
          } else {
            process.stderr.write('[relay-server] ZELLIJ_SESSION_NAME not set for ls command\n');
          }
        }
        // Other raw_command types are Telegram-era relics — ignored
      } else if (msg.action === 'answer') {
        // Answer action: translate structured answer into keystrokes for AskUserQuestion TUI
        if (!msg.kuerzel || !msg.type) {
          process.stderr.write('[relay-server] answer action missing kuerzel or type\n');
          return;
        }

        if (msg.type === 'multi') {
          // Multi-select needs per-keystroke delays for TUI toggle processing
          dispatchMultiSelectAnswer(msg.kuerzel, msg.selections || [], msg.option_count || 0);
        } else {
          // Single and text: send full keystroke sequence at once
          const keystrokes = computeKeystrokeSequence(msg);
          if (keystrokes) {
            dispatchKeystrokesToPane(msg.kuerzel, keystrokes);
          }
        }
      }
    } catch {
      // Malformed message — ignore
    }
  });

  ws.on('close', () => {
    appConnected = false;
    appSocket = null;
    disconnectedAt = Date.now();
    process.stderr.write('[relay-server] App disconnected\n');
  });

  ws.on('error', () => {
    appConnected = false;
    appSocket = null;
    disconnectedAt = Date.now();
  });
});

wss.on('listening', () => {
  process.stderr.write(`[relay-server] WebSocket server listening on port ${config.port || DEFAULT_PORT}\n`);
});

wss.on('error', (err) => {
  process.stderr.write(`[relay-server] WebSocket server error: ${err.message}\n`);
  process.exit(1);
});

// --- Unix Domain Socket IPC (for hooks) ---
try { fs.unlinkSync(SOCK_FILE); } catch {}

const hookServer = net.createServer((conn) => {
  let data = '';

  conn.setTimeout(2000, () => {
    conn.destroy();
  });

  conn.on('data', (chunk) => {
    data += chunk;
  });

  conn.on('end', () => {
    try {
      const msg = JSON.parse(data);

      if (msg.action === 'send') {
        if (appSocket && appSocket.readyState === 1) {
          appSocket.send(JSON.stringify(msg.payload));
          conn.end(JSON.stringify({ sent: true }));
        } else {
          conn.end(JSON.stringify({
            sent: false,
            disconnectedFor: disconnectedAt ? Date.now() - disconnectedAt : null,
          }));
        }
      } else if (msg.action === 'status') {
        conn.end(JSON.stringify({
          connected: appConnected,
          disconnectedAt,
        }));
      } else {
        conn.end(JSON.stringify({ error: 'unknown action' }));
      }
    } catch {
      conn.end(JSON.stringify({ error: 'invalid json' }));
    }
  });

  conn.on('error', () => {});
});

hookServer.listen(SOCK_FILE, () => {
  process.stderr.write(`[relay-server] Hook IPC listening on ${SOCK_FILE}\n`);
});

hookServer.on('error', (err) => {
  process.stderr.write(`[relay-server] Hook IPC error: ${err.message}\n`);
});

// --- mDNS Advertisement ---
function startMdns() {
  try {
    const tokenHash = crypto.createHash('sha256').update(config.secret).digest('hex').slice(0, 8);
    const port = config.port || DEFAULT_PORT;

    mdnsChild = spawn('dns-sd', ['-R', 'Relay', '_relay._tcp', 'local', String(port), `token_hash=${tokenHash}`], {
      detached: true,
      stdio: 'ignore',
    });
    mdnsChild.unref();
    process.stderr.write(`[relay-server] mDNS advertising _relay._tcp on port ${port}\n`);
  } catch (err) {
    process.stderr.write(`[relay-server] mDNS advertisement failed: ${err.message}\n`);
  }
}

startMdns();

// --- PID File ---
fs.writeFileSync(PID_FILE, String(process.pid));

// --- Graceful Shutdown ---
function cleanup() {
  process.stderr.write('[relay-server] Shutting down...\n');

  try { wss.close(); } catch {}
  try { hookServer.close(); } catch {}
  try { fs.unlinkSync(PID_FILE); } catch {}
  try { fs.unlinkSync(SOCK_FILE); } catch {}

  if (mdnsChild && mdnsChild.pid) {
    try { process.kill(mdnsChild.pid); } catch {}
  }

  process.exit(0);
}

process.on('SIGTERM', cleanup);
process.on('SIGINT', cleanup);
