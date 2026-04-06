#!/usr/bin/env node
// Permission notification hook: sends permission requests to relay via IPC.
// Input (stdin): { session_id, message, ... }

const { readFileSync } = require('fs');
const { sendRelay, getKuerzel } = require('./send-relay.cjs');

function loadToolDetails(sessionId) {
  if (!sessionId) return null;
  try {
    // Tool cache written by zellij-claude's pretool-cache hook (optional dependency)
    const raw = readFileSync(`/tmp/zellij-claude-pending-tool-${sessionId}.json`, 'utf8');
    const info = JSON.parse(raw);
    if (Date.now() - info.ts > 10000) return null;
    return info;
  } catch { return null; }
}

function formatToolDetail(toolInfo) {
  if (!toolInfo) return '';
  const name = toolInfo.tool_name;
  const inp = toolInfo.tool_input || {};
  if (name === 'Bash' && inp.command) {
    const cmd = inp.command.length > 200 ? inp.command.slice(0, 200) + '...' : inp.command;
    return `\n\n$ ${cmd}`;
  }
  if (name === 'Edit' && inp.file_path) return `\n\n${inp.file_path}`;
  if (name === 'Write' && inp.file_path) return `\n\n${inp.file_path}`;
  if (inp.command || inp.file_path) return `\n\n${inp.command || inp.file_path}`;
  return '';
}

let input = '';
const timeout = setTimeout(() => {
  // No session_id available, skip
  process.exit(0);
}, 3000);

process.stdin.setEncoding('utf8');
process.stdin.on('data', chunk => (input += chunk));
process.stdin.on('end', async () => {
  clearTimeout(timeout);
  try {
    const data = JSON.parse(input || '{}');
    const kuerzel = getKuerzel(data.session_id);
    if (!kuerzel) { process.exit(0); }
    const message = data.message || 'Permission required';
    const toolInfo = loadToolDetails(data.session_id);
    // Skip AskUserQuestion — already handled by ask-notify hook
    if (toolInfo && toolInfo.tool_name === 'AskUserQuestion') { process.exit(0); }
    if (message.includes('Claude Code needs your attention')) { process.exit(0); }
    await sendRelay({
      type: 'permission',
      session: kuerzel,
      message: message + formatToolDetail(toolInfo),
      tool_details: toolInfo ? {
        tool_name: toolInfo.tool_name,
        command: toolInfo.tool_input?.command,
        file_path: toolInfo.tool_input?.file_path,
      } : undefined,
      timestamp: Date.now(),
    });
  } catch {
    // No valid session data, skip
  }
  process.exit(0);
});
