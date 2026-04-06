// Screen buffer parser — extracts Claude Code responses from zellij dump-screen output.
// Shared by relay-server.cjs and hooks.

const SEPARATOR = /^\s*[─]{10,}\s*$/;
const STATUSBAR = /^\s*(Opus|Sonnet|Haiku|Claude)\s/;
const UI_CHROME = /^\s*(⏵|shift\+tab|ctrl\+o|Enter to continue|Esc to exit|Rewind|Restore the code)/;
const SPINNER = /^\s*[·⠋⠙⠹⠸⠼⠴⠦⠧⠇⠏]\s/;
const HOOK_STATUS = /running\s+(stop|start)\s+hooks/i;
const THINKING = /^\s*thinking\s*$/i;
const EMPTY_PROMPT = /^❯\s*$/;
const RESPONSE_START = /^[⏺✻]/;
const WORKED_FOR = /^✻\s+Worked for/;
const PROMPT_START = /^❯\s+\S/;

function isNoise(line) {
  return SEPARATOR.test(line) || STATUSBAR.test(line) || UI_CHROME.test(line)
    || SPINNER.test(line) || HOOK_STATUS.test(line) || THINKING.test(line) || WORKED_FOR.test(line);
}

function cleanBlock(lines) {
  return lines
    .map(l => l.replace(/^[⏺✻]\s*/, '').replace(/^\s{2}⎿\s{1,2}/, '  ').trimStart())
    .join('\n').trim();
}

/**
 * Extract the last N Claude response blocks from a screen dump.
 * Returns the concatenated text, or null if no responses found.
 */
function extractResponses(screenText, count = 1) {
  if (!screenText) return null;
  const lines = screenText.split('\n');

  const blocks = [];
  let i = 0;
  while (i < lines.length) {
    if (!RESPONSE_START.test(lines[i])) { i++; continue; }
    const start = i;
    const block = [];
    for (; i < lines.length; i++) {
      if (i > start && (RESPONSE_START.test(lines[i]) || PROMPT_START.test(lines[i]) || EMPTY_PROMPT.test(lines[i]))) break;
      if (isNoise(lines[i])) continue;
      block.push(lines[i]);
    }
    const text = cleanBlock(block);
    if (text) blocks.push(text);
  }

  if (blocks.length === 0) {
    // Fallback: extract text before the last prompt
    let end = lines.length;
    for (let j = lines.length - 1; j >= 0; j--) {
      if (EMPTY_PROMPT.test(lines[j]) || PROMPT_START.test(lines[j])) { end = j; break; }
    }
    const content = [];
    for (let j = end - 1; j >= 0; j--) {
      if (isNoise(lines[j]) || !lines[j].trim()) { if (content.length > 0) break; continue; }
      content.unshift(lines[j]);
    }
    const text = content.map(l => l.trimStart()).join('\n').trim();
    return text || null;
  }

  return blocks.slice(-count).join('\n\n---\n\n');
}

module.exports = { extractResponses };
