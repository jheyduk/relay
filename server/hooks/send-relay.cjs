// Standalone relay IPC — no external dependencies.
// Sends JSON payloads to relay-server via Unix domain socket.

const net = require('net');
const { readFileSync } = require('fs');

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

function getKuerzel(sessionId) {
  if (sessionId) {
    try {
      return readFileSync(`/tmp/zellij-claude-tab-${sessionId}`, 'utf8').trim();
    } catch {}
  }
  return null;
}

module.exports = { sendRelay, getKuerzel };
