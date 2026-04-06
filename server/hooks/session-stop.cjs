#!/usr/bin/env node
// SessionStop hook: notify relay of completion, stop relay-server if last session.
// Input (stdin): { session_id, ... }

const { execFileSync } = require('child_process');
const { sendRelay, getKuerzel } = require('./send-relay.cjs');
// relay-server lifecycle managed by launchd — no hook-based stop needed

/**
 * Get the last response(s) from a session via zellij-claude CLI.
 * Falls back to null if zellij-claude is not available.
 */
function getLastResponse(kuerzel, count = 2) {
  try {
    return execFileSync('zellij-claude', ['last', `@${kuerzel}`, String(count)], {
      encoding: 'utf8', timeout: 10000, stdio: ['pipe', 'pipe', 'pipe']
    }).trim() || null;
  } catch { return null; }
}

/**
 * Smart response sizing: tiered logic replacing legacy truncate(2000).
 * - ≤4KB: both responses included (untruncated)
 * - ≤16KB: single response only (untruncated)
 * - >16KB: single response truncated with visible marker
 */
function getSmartResponse(kuerzel) {
  const full = getLastResponse(kuerzel, 2);
  if (!full) return 'Task complete';

  const bytes = Buffer.byteLength(full, 'utf8');
  if (bytes <= 4096) return full;

  // Too large with 2 responses, try just the last one
  const single = getLastResponse(kuerzel, 1);
  if (!single) return 'Task complete';

  const singleBytes = Buffer.byteLength(single, 'utf8');
  if (singleBytes <= 16384) return single;

  // Even single response is huge, truncate
  return single.slice(0, 16384) + '\n…(truncated)';
}

async function notify(kuerzel, data) {
  if (!kuerzel) return;
  const message = getSmartResponse(kuerzel);
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
  process.exit(0);
});
