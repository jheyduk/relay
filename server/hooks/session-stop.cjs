#!/usr/bin/env node
// SessionStop hook: notify relay of completion, stop relay-server if last session.
// Input (stdin): { session_id, ... }

const { readFileSync, readdirSync, unlinkSync } = require('fs');
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

async function notify(kuerzel) {
  if (!kuerzel) return;
  await sendRelay({
    type: 'completion',
    session: kuerzel,
    message: 'Task complete',
    timestamp: Date.now(),
  });
}

let input = '';
const timeout = setTimeout(async () => {
  await notify(getKuerzel(null));
  process.exit(0);
}, 3000);

process.stdin.setEncoding('utf8');
process.stdin.on('data', chunk => (input += chunk));
process.stdin.on('end', async () => {
  clearTimeout(timeout);
  try {
    const data = JSON.parse(input || '{}');
    await notify(getKuerzel(data.session_id));
  } catch {
    // No valid session data, skip
  }
  // Stop relay server if this was the last session
  stopRelayServerIfEmpty();
  process.exit(0);
});
