// Standalone relay IPC — no external dependencies beyond node built-ins.
// Sends JSON payloads to relay-server via Unix domain socket.

const net = require('net');
const { execSync } = require('child_process');

const RELAY_SOCKET = '/tmp/zellij-claude-relay.sock';

function sendRelay(jsonPayload) {
  return new Promise((resolve) => {
    const client = net.createConnection(RELAY_SOCKET, () => {
      client.end(JSON.stringify({ action: 'send', payload: jsonPayload }));
    });
    let response = '';
    client.on('data', chunk => response += chunk);
    client.on('end', () => {
      try { resolve(JSON.parse(response)); }
      catch { resolve({ sent: false }); }
    });
    client.on('error', () => resolve({ sent: false }));
    client.setTimeout(2000, () => { client.destroy(); resolve({ sent: false }); });
  });
}

/**
 * Resolve the kuerzel for the current Claude Code session.
 * Queries Zellij panes to find the @-tab this hook is running in.
 * Falls back to reading legacy tab files if Zellij env is not available.
 */
function getKuerzel(sessionId) {
  const zellijSession = process.env.ZELLIJ_SESSION_NAME;
  const paneId = process.env.ZELLIJ_PANE_ID;

  if (zellijSession) {
    try {
      const raw = execSync(
        `zellij --session ${zellijSession} action list-panes --json`,
        { encoding: 'utf8', timeout: 3000, stdio: ['pipe', 'pipe', 'pipe'] }
      );
      const panes = JSON.parse(raw);
      const pane = paneId !== undefined
        ? panes.find(p => String(p.id) === paneId && !p.is_plugin)
        : panes.find(p => p.is_focused && !p.is_plugin);
      if (pane && pane.tab_name && pane.tab_name.startsWith('@')) {
        return pane.tab_name.slice(1);
      }
    } catch {}
  }

  return null;
}

module.exports = { sendRelay, getKuerzel };
