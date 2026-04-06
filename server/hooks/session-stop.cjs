#!/usr/bin/env node
// SessionStop hook: notify relay of completion with smart response sizing.
// Input (stdin): { session_id, ... }

const { execFileSync } = require('child_process');
const { sendRelay, getKuerzel } = require('./send-relay.cjs');
const { extractResponses } = require('../screen-parse.cjs');

/**
 * Get the last N responses by dumping the screen buffer directly via Zellij.
 */
function getLastResponse(kuerzel, count = 2) {
  const zellijSession = process.env.ZELLIJ_SESSION_NAME;
  if (!zellijSession) return null;
  try {
    const panesRaw = execFileSync('zellij', ['--session', zellijSession, 'action', 'list-panes', '--json', '--state'], {
      encoding: 'utf8', timeout: 5000, stdio: ['pipe', 'pipe', 'pipe']
    });
    const panes = JSON.parse(panesRaw);
    const pane = panes.find(p => p.tab_name === `@${kuerzel}` && !p.is_plugin);
    if (!pane) return null;
    const screen = execFileSync('zellij', ['--session', zellijSession, 'action', 'dump-screen', '--full', '--pane-id', String(pane.id)], {
      encoding: 'utf8', timeout: 10000, stdio: ['pipe', 'pipe', 'pipe']
    });
    return extractResponses(screen, count);
  } catch { return null; }
}

/**
 * Smart response sizing: tiered logic.
 * - ≤4KB: both responses included (untruncated)
 * - ≤16KB: single response only (untruncated)
 * - >16KB: single response truncated with visible marker
 */
function getSmartResponse(kuerzel) {
  const full = getLastResponse(kuerzel, 2);
  if (!full) return 'Task complete';

  const bytes = Buffer.byteLength(full, 'utf8');
  if (bytes <= 4096) return full;

  const single = getLastResponse(kuerzel, 1);
  if (!single) return 'Task complete';

  const singleBytes = Buffer.byteLength(single, 'utf8');
  if (singleBytes <= 16384) return single;

  return single.slice(0, 16384) + '\n…(truncated)';
}

async function notify(kuerzel, data) {
  if (!kuerzel) return;
  const message = getSmartResponse(kuerzel);
  await sendRelay({
    type: 'status',
    session: kuerzel,
    status: 'ready',
    message: `Session ${kuerzel} ready`,
    timestamp: Date.now(),
  });
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
    await notify(getKuerzel(data.session_id), data);
  } catch (e) {
    process.stderr.write('session-stop error: ' + e.message + '\n');
  }
  process.exit(0);
});
