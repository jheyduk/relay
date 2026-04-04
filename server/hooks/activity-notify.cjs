#!/usr/bin/env node
// PreToolUse hook: notify relay that this session is actively working.
// Fires on every tool use — sends a "working" status update.
// Debounced: only sends if last update was >5s ago to avoid flooding.

const { sendRelay, getKuerzel } = require('./send-relay.cjs');
const { readFileSync, writeFileSync } = require('fs');

const DEBOUNCE_FILE = '/tmp/relay-activity-last';
const DEBOUNCE_MS = 5000; // 5 seconds

let input = '';
const timeout = setTimeout(() => process.exit(0), 2000);
process.stdin.setEncoding('utf8');
process.stdin.on('data', chunk => (input += chunk));
process.stdin.on('end', async () => {
  clearTimeout(timeout);
  try {
    const data = JSON.parse(input || '{}');
    const kuerzel = getKuerzel(data.session_id);
    if (!kuerzel) { process.exit(0); }

    // Debounce: skip if we sent an update recently
    try {
      const last = parseInt(readFileSync(DEBOUNCE_FILE, 'utf8').trim());
      if (Date.now() - last < DEBOUNCE_MS) { process.exit(0); }
    } catch {}

    writeFileSync(DEBOUNCE_FILE, String(Date.now()));

    await sendRelay({
      type: 'status',
      session: kuerzel,
      status: 'working',
      message: `Session ${kuerzel} working`,
      timestamp: Date.now(),
    });
  } catch {}
  process.exit(0);
});
