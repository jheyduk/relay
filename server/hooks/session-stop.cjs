#!/usr/bin/env node
// SessionStop hook: notify relay of completion, stop relay-server if last session.
// Input (stdin): { session_id, ... }

const { readFileSync, readdirSync, unlinkSync } = require('fs');
const { execFileSync } = require('child_process');
const { sendRelay, getKuerzel } = require('./send-relay.cjs');

const RELAY_PIDFILE = '/tmp/zellij-claude-relay.pid';

// Stop relay-server if no zellij-claude sessions remain
function stopRelayServerIfEmpty() {
  try {
    const remaining = readdirSync('/tmp').filter(f => f.startsWith('zellij-claude-tab-'));
    if (remaining.length > 0) return; // Sessions still active

    // No sessions left — stop the relay server
    try {
      const pid = parseInt(readFileSync(RELAY_PIDFILE, 'utf8').trim());
      process.kill(pid, 'SIGTERM');
    } catch {}
    try { unlinkSync(RELAY_PIDFILE); } catch {}
  } catch {}
}

/**
 * Get the last response(s) from a session via zellij-claude CLI.
 * Falls back to null if zellij-claude is not available.
 */
function getLastResponse(kuerzel, count = 2) {
  try {
    return execFileSync('npx', ['zellij-claude', 'last', `@${kuerzel}`, String(count)], {
      encoding: 'utf8', timeout: 10000, stdio: ['pipe', 'pipe', 'pipe']
    }).trim() || null;
  } catch { return null; }
}

function truncate(text, max) {
  if (text.length <= max) return text;
  return text.slice(0, max) + '\n…(truncated)';
}

async function notify(kuerzel, data) {
  if (!kuerzel) return;
  const lastResponse = getLastResponse(kuerzel, 2);
  const message = lastResponse
    ? truncate(lastResponse, 2000)
    : 'Task complete';
  // Send status back to ready
  await sendRelay({
    type: 'status',
    session: kuerzel,
    status: 'ready',
    message: `Session ${kuerzel} ready`,
    timestamp: Date.now(),
  });
  // Send completion with last responses
  await sendRelay({
    type: 'completion',
    session: kuerzel,
    message,
    timestamp: Date.now(),
  });
}

let input = '';
const timeout = setTimeout(async () => {
  await notify(getKuerzel(null), {});
  process.exit(0);
}, 3000);

process.stdin.setEncoding('utf8');
process.stdin.on('data', chunk => (input += chunk));
process.stdin.on('end', async () => {
  clearTimeout(timeout);
  try {
    const data = JSON.parse(input || '{}');
    // Log available fields for debugging
    process.stderr.write('session-stop data keys: ' + Object.keys(data).join(', ') + '\n');
    await notify(getKuerzel(data.session_id), data);
  } catch (e) {
    process.stderr.write('session-stop error: ' + e.message + '\n');
  }
  // Stop relay server if this was the last session
  stopRelayServerIfEmpty();
  process.exit(0);
});
