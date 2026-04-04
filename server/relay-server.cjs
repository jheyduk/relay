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
 * Helper: send Down arrow key to a pane.
 * Down arrow = ESC [ B = bytes 27, 91, 66
 */
function sendDownArrow(sessionId, paneId) {
  return writeToPane(sessionId, paneId, 27, 91, 66);
}

/**
 * Helper: send Enter key to a pane.
 */
function sendEnter(sessionId, paneId) {
  return writeToPane(sessionId, paneId, 13);
}

const KEYSTROKE_DELAY = 80; // ms between keystrokes for TUI to process
const delay = (ms) => new Promise(resolve => setTimeout(resolve, ms));

// Track pending questions per session for auto-submit after the last answer
// Key: kuerzel, Value: { total: number, answered: number }
const pendingQuestions = new Map();

/**
 * Dispatch an answer to an AskUserQuestion prompt.
 * Resolves pane once, then sends the appropriate keystroke sequence.
 */
async function dispatchAnswer(kuerzel, msg) {
  const sessionId = findSessionForKuerzel(kuerzel);
  if (!sessionId) {
    process.stderr.write(`[relay-server] answer: no session for @${kuerzel}\n`);
    return;
  }
  const paneId = await findPaneForTab(sessionId, kuerzel);
  if (!paneId) {
    process.stderr.write(`[relay-server] answer: no pane for @${kuerzel}\n`);
    return;
  }

  process.stderr.write(`[relay-server] answer: type=${msg.type} selections=${JSON.stringify(msg.selections)} text=${msg.text || ''} options=${msg.option_count}\n`);

  if (msg.type === 'single') {
    // Single choice: number key selects the option
    await writeCharsToPane(sessionId, paneId, String(msg.selections[0]));
    // Track answered count and auto-submit if this was the last question
    const pending = pendingQuestions.get(kuerzel);
    if (pending) {
      pending.answered++;
      process.stderr.write(`[relay-server] Answered ${pending.answered}/${pending.total} for @${kuerzel}\n`);
      if (pending.answered >= pending.total && pending.total > 1) {
        // Last question in a multi-question set — confirm "Ready to submit?"
        await delay(500);
        await writeCharsToPane(sessionId, paneId, '1');
        process.stderr.write(`[relay-server] Auto-submitted for @${kuerzel}\n`);
        pendingQuestions.delete(kuerzel);
      } else if (pending.answered >= pending.total) {
        pendingQuestions.delete(kuerzel);
      }
    }
    return;
  }

  if (msg.type === 'multi') {
    // Multi choice: cursor starts at option 1.
    // Navigate with Down arrows, toggle with Space (byte 32).
    // Selections are 1-based indices. Cursor starts at position 1.
    const sorted = [...msg.selections].sort((a, b) => a - b);
    let cursorPos = 1;

    for (const sel of sorted) {
      // Move cursor to the target option
      while (cursorPos < sel) {
        await sendDownArrow(sessionId, paneId);
        await delay(KEYSTROKE_DELAY);
        cursorPos++;
      }
      // Toggle with Space
      await writeToPane(sessionId, paneId, 32);
      await delay(KEYSTROKE_DELAY);
    }
    // Move cursor past all options AND past "Other" to the submit area
    // Layout: options (1..N) → Other → implicit submit
    while (cursorPos <= msg.option_count + 1) {
      await sendDownArrow(sessionId, paneId);
      await delay(KEYSTROKE_DELAY);
      cursorPos++;
    }
    // Enter to confirm selection
    await sendEnter(sessionId, paneId);
    // "Ready to submit?" will appear as a new single-choice question via the hook
    return;
  }

  if (msg.type === 'text') {
    // Free text "Other": navigate Down past all options to "Other", Enter, type, Enter.
    // Cursor starts at option 1, "Other" is after all options.
    for (let i = 0; i < msg.option_count; i++) {
      await sendDownArrow(sessionId, paneId);
      await delay(KEYSTROKE_DELAY);
    }
    await sendEnter(sessionId, paneId); // Select "Other"
    await delay(200); // Wait for text input to appear
    await writeCharsToPane(sessionId, paneId, msg.text);
    await delay(KEYSTROKE_DELAY);
    await sendEnter(sessionId, paneId); // Confirm text
    return;
  }
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

// --- Status Polling ---

/**
 * Derive session status from Zellij pane title.
 * Spinner characters = working, permission keywords = waiting, else = ready.
 */
function deriveStatus(title) {
  if (!title) return 'ready';
  if (/[\u280b\u2819\u2839\u2838\u283c\u2834\u2826\u2827\u2807\u280f\u2810]/.test(title)) return 'working';
  if (/\bpermission\b|\bAllow\b|\bDeny\b/i.test(title) || /\?\s*$/.test(title)) return 'waiting';
  return 'ready';
}

let statusPollTimer = null;
let lastStatusMap = new Map(); // kuerzel -> status
let anySessionWorking = false;

const POLL_IDLE = 30000;  // 30s when all sessions are ready
const POLL_ACTIVE = 3000; // 3s when any session is working

/**
 * Adaptive status polling — fast when sessions are working, slow when idle.
 * Only sends updates for changed statuses to minimize traffic.
 */
function startStatusPolling() {
  if (statusPollTimer) return;
  const zellijSession = process.env.ZELLIJ_SESSION_NAME;
  if (!zellijSession) return;

  function poll() {
    if (!appSocket || appSocket.readyState !== 1) {
      statusPollTimer = setTimeout(poll, POLL_IDLE);
      return;
    }

    // Skip polling during suppression (hooks handle status)
    if (Date.now() < pollSuppressedUntil) {
      statusPollTimer = setTimeout(poll, POLL_ACTIVE);
      return;
    }

    execFile('zellij', ['--session', zellijSession, 'action', 'list-panes', '--json', '--tab', '--state'], {
      timeout: 5000,
      encoding: 'utf8',
    }, (err, stdout) => {
      if (err) {
        statusPollTimer = setTimeout(poll, POLL_IDLE);
        return;
      }
      try {
        const panes = JSON.parse(stdout);
        const tabStatus = new Map();
        for (const p of panes) {
          if (!p.tab_name || !p.tab_name.startsWith('@') || p.is_plugin) continue;
          const kuerzel = p.tab_name.slice(1);
          if (!tabStatus.has(kuerzel) || p.is_focused) {
            tabStatus.set(kuerzel, deriveStatus(p.title));
          }
        }

        // Send updates only for changed statuses
        anySessionWorking = false;
        for (const [kuerzel, status] of tabStatus) {
          if (status === 'working' || status === 'waiting') anySessionWorking = true;
          if (lastStatusMap.get(kuerzel) !== status) {
            lastStatusMap.set(kuerzel, status);
            appSocket.send(JSON.stringify({
              type: 'status',
              session: kuerzel,
              status,
              message: `Session ${kuerzel} ${status}`,
              timestamp: Date.now(),
              __relay: true,
            }));
          }
        }
      } catch {}

      // Schedule next poll — fast if active, slow if idle
      const nextInterval = anySessionWorking ? POLL_ACTIVE : POLL_IDLE;
      statusPollTimer = setTimeout(poll, nextInterval);
    });
  }

  // Start first poll immediately
  poll();
}

function stopStatusPolling() {
  if (statusPollTimer) {
    clearTimeout(statusPollTimer);
    statusPollTimer = null;
  }
  lastStatusMap.clear();
  anySessionWorking = false;
}

let pollSuppressedUntil = 0;

/**
 * Suppress polling for a duration (ms). During suppression, hooks handle status updates.
 * After suppression ends, resume with fast polling to catch the transition back to ready.
 */
function suppressPollingFor(ms) {
  pollSuppressedUntil = Date.now() + ms;
}

// --- Attachments ---

/**
 * Save an attachment from the app and dispatch the file path to the session.
 * The file is saved to /tmp/relay-attachments/ and the path is typed into the terminal
 * so Claude Code can read it with the Read tool.
 */
async function handleAttachment(kuerzel, filename, base64Data) {
  if (!kuerzel || !filename || !base64Data) {
    process.stderr.write('[relay-server] attachment: missing kuerzel, filename, or data\n');
    return;
  }

  const dir = '/tmp/relay-attachments';
  try { fs.mkdirSync(dir, { recursive: true }); } catch {}

  try {
    const buffer = Buffer.from(base64Data, 'base64');

    // Detect file extension from magic bytes if not in filename
    let ext = '';
    const safeName = filename.replace(/[^a-zA-Z0-9._-]/g, '_');
    if (!safeName.includes('.')) {
      if (buffer[0] === 0x89 && buffer[1] === 0x50) ext = '.png';
      else if (buffer[0] === 0xFF && buffer[1] === 0xD8) ext = '.jpg';
      else if (buffer[0] === 0x47 && buffer[1] === 0x49) ext = '.gif';
      else if (buffer[0] === 0x25 && buffer[1] === 0x50) ext = '.pdf';
      else ext = '.bin';
    }
    const destPath = `${dir}/${Date.now()}-${safeName}${ext}`;

    fs.writeFileSync(destPath, buffer);
    process.stderr.write(`[relay-server] Attachment saved: ${destPath} (${buffer.length} bytes)\n`);

    // Type the file path into the session and send an extra Enter so Claude Code processes it
    await dispatchCommand(kuerzel, destPath);
    await delay(500);
    const sessionId = findSessionForKuerzel(kuerzel);
    const paneId = sessionId ? await findPaneForTab(sessionId, kuerzel) : null;
    if (sessionId && paneId) await sendEnter(sessionId, paneId);
  } catch (err) {
    process.stderr.write(`[relay-server] attachment error: ${err.message}\n`);
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
      execFile(config.whisper_cli, ['-m', config.whisper_model, '--no-timestamps', '-nt', '-l', config.whisper_language || 'de', tmpFile], {
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
  startStatusPolling();

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
      process.stderr.write(`[relay-server] Received: ${JSON.stringify(msg).slice(0,200)}\n`);

      if (msg.action === 'command') {
        // Optimistic status: set session to working immediately
        if (msg.kuerzel && lastStatusMap.get(msg.kuerzel) !== 'working') {
          lastStatusMap.set(msg.kuerzel, 'working');
          appSocket.send(JSON.stringify({
            type: 'status',
            session: msg.kuerzel,
            status: 'working',
            message: `Session ${msg.kuerzel} working`,
            timestamp: Date.now(),
            __relay: true,
          }));
        }
        // Dispatch command directly via Zellij write-chars
        dispatchCommand(msg.kuerzel, msg.message);
        // Suppress polling for 10s — hooks handle the working→ready transition
        suppressPollingFor(10000);
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

        dispatchAnswer(msg.kuerzel, msg);
      } else if (msg.action === 'attachment') {
        handleAttachment(msg.kuerzel, msg.filename, msg.data);
      }
    } catch (e) {
      process.stderr.write(`[relay-server] message parse error: ${e.message}\n`);
    }
  });

  ws.on('close', () => {
    appConnected = false;
    appSocket = null;
    disconnectedAt = Date.now();
    process.stderr.write('[relay-server] App disconnected\n');
    stopStatusPolling();
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
          // Track question count per session for auto-submit after last answer
          if (msg.payload && msg.payload.type === 'question' && msg.payload.session) {
            const session = msg.payload.session;
            const current = pendingQuestions.get(session) || { total: 0, answered: 0 };
            current.total++;
            pendingQuestions.set(session, current);
            process.stderr.write(`[relay-server] Question ${current.total} queued for @${session}\n`);
          }
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
