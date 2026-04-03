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

  if (changed) {
    const dir = path.dirname(CONFIG_PATH);
    fs.mkdirSync(dir, { recursive: true });
    fs.writeFileSync(CONFIG_PATH, JSON.stringify(config, null, 2) + '\n');
  }

  return config;
}

const config = loadOrCreateConfig();

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
  try {
    const tabFiles = fs.readdirSync('/tmp').filter(f => f.startsWith('zellij-claude-tab-'));
    for (const f of tabFiles) {
      try {
        const content = fs.readFileSync(`/tmp/${f}`, 'utf8').trim();
        if (content === kuerzel) {
          return f.replace('zellij-claude-tab-', '');
        }
      } catch {}
    }
  } catch {}
  return null;
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
          p.tab_name === `@${kuerzel}` && p.is_focused
        );
        if (target) {
          resolve(String(target.pane_id));
          return;
        }
        // Fallback: any pane in the matching tab
        const anyInTab = panes.find(p => p.tab_name === `@${kuerzel}`);
        if (anyInTab) {
          resolve(String(anyInTab.pane_id));
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
    const args = ['--session', sessionId, 'action', 'write-chars', '--pane-id', paneId, chars];
    execFile('zellij', args, { timeout: 5000 }, (err) => {
      if (err) {
        process.stderr.write(`[relay-server] write-chars error: ${err.message}\n`);
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
      execFile('zellij', ['--session', sessionId, 'action', 'write-chars', message + '\n'], {
        timeout: 5000,
      }, (err2) => {
        if (err2) process.stderr.write(`[relay-server] write-chars fallback error: ${err2.message}\n`);
      });
    });
    return;
  }

  await writeCharsToPane(sessionId, paneId, message + '\n');
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

  ws.on('message', (data) => {
    try {
      const msg = JSON.parse(data.toString());

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
