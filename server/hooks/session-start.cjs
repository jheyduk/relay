#!/usr/bin/env node
// SessionStart hook: notify relay-server that a session started working.
// Tab/session discovery is handled by the server via direct Zellij queries.
// Input (stdin): { session_id, ... }

const { execSync } = require('child_process');
const { sendRelay } = require('./send-relay.cjs');

let input = '';
const timeout = setTimeout(() => process.exit(0), 3000);
process.stdin.setEncoding('utf8');
process.stdin.on('data', chunk => (input += chunk));
process.stdin.on('end', async () => {
  clearTimeout(timeout);
  try {
    const zellijSession = process.env.ZELLIJ_SESSION_NAME;
    const paneId = process.env.ZELLIJ_PANE_ID;

    if (zellijSession) {
      // Find our tab by matching pane ID from list-panes
      const raw = execSync(
        `zellij --session ${zellijSession} action list-panes --json`,
        { encoding: 'utf8', timeout: 3000, stdio: ['pipe', 'pipe', 'pipe'] }
      );
      const panes = JSON.parse(raw);
      const pane = paneId !== undefined
        ? panes.find(p => String(p.id) === paneId && !p.is_plugin)
        : panes.find(p => p.is_focused && !p.is_plugin);
      if (pane && pane.tab_name && pane.tab_name.startsWith('@')) {
        const kuerzel = pane.tab_name.slice(1);
        await sendRelay({
          type: 'status',
          session: kuerzel,
          status: 'working',
          message: `Session ${kuerzel} started`,
          timestamp: Date.now(),
        });
      }
    }
  } catch {
    // Never crash Claude Code
  }
  process.exit(0);
});
