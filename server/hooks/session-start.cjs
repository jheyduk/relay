#!/usr/bin/env node
// SessionStart hook: cache the zellij tab name for other hooks,
// reconcile stale tab files, and ensure relay-server is running.
// Input (stdin): { session_id, ... }

const { execSync } = require('child_process');
const { writeFileSync, readFileSync } = require('fs');
const { reconcile } = require('./reconcile-tabs.cjs');
const { sendRelay } = require('./send-relay.cjs');

const RELAY_PIDFILE = '/tmp/zellij-claude-relay.pid';

// Ensure relay-server.cjs singleton is running.
// Spawns as detached process if not already alive.
function ensureRelayServer() {
  try {
    const pid = parseInt(readFileSync(RELAY_PIDFILE, 'utf8').trim());
    process.kill(pid, 0); // Throws if process doesn't exist
    return; // Already running
  } catch {
    // Not running or stale PID — start it
  }
  try {
    const { spawn } = require('child_process');
    const serverPath = require('path').join(__dirname, '..', 'relay-server.cjs');
    const child = spawn('node', [serverPath], {
      detached: true,
      stdio: 'ignore',
    });
    child.unref();
  } catch {}
}

let input = '';
const timeout = setTimeout(() => process.exit(0), 3000);
process.stdin.setEncoding('utf8');
process.stdin.on('data', chunk => (input += chunk));
process.stdin.on('end', async () => {
  clearTimeout(timeout);
  try {
    const data = JSON.parse(input || '{}');
    const sessionId = data.session_id ?? 'unknown';
    const zellijSession = process.env.ZELLIJ_SESSION_NAME;
    const paneId = process.env.ZELLIJ_PANE_ID;

    if (zellijSession) {
      // Find our tab by matching pane ID from list-panes (not list-tabs)
      const raw = execSync(
        `zellij --session ${zellijSession} action list-panes --json`,
        { encoding: 'utf8', timeout: 3000, stdio: ['pipe', 'pipe', 'pipe'] }
      );
      const panes = JSON.parse(raw);
      // Match by ZELLIJ_PANE_ID if set, otherwise find the focused pane
      const pane = paneId !== undefined
        ? panes.find(p => String(p.id) === paneId && !p.is_plugin)
        : panes.find(p => p.is_focused && !p.is_plugin);
      if (pane && pane.tab_name && pane.tab_name.startsWith('@')) {
        const kuerzel = pane.tab_name.slice(1);
        writeFileSync(`/tmp/zellij-claude-tab-${sessionId}`, kuerzel);
        await sendRelay({
          type: 'status',
          session: kuerzel,
          status: 'working',
          message: `Session ${kuerzel} started`,
          timestamp: Date.now(),
        });
      }
    }

    // Clean up stale tab files from old/dead sessions
    reconcile();

    // Start relay WebSocket server if not already running
    ensureRelayServer();
  } catch {
    // Never crash Claude Code
  }
  process.exit(0);
});
