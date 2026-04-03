#!/usr/bin/env node
// Hook installer: registers relay server hooks in Claude Code settings.json.
// Removes old zellij-claude hook entries for migrated hooks, adds relay hooks.
// Usage: node server/install.cjs

const { readFileSync, writeFileSync, mkdirSync } = require('fs');
const { join } = require('path');
const { homedir } = require('os');

const SETTINGS_PATH = join(homedir(), '.claude', 'settings.json');

// Hook names that have been migrated from zellij-claude to relay
const MIGRATED_HOOKS = ['session-start', 'session-stop', 'permission-notify', 'ask-notify'];

function loadSettings() {
  try {
    return JSON.parse(readFileSync(SETTINGS_PATH, 'utf8'));
  } catch {
    return {};
  }
}

function saveSettings(settings) {
  // Ensure ~/.claude/ directory exists
  mkdirSync(join(homedir(), '.claude'), { recursive: true });
  writeFileSync(SETTINGS_PATH, JSON.stringify(settings, null, 2) + '\n');
}

// Remove old zellij-claude hook entries for migrated hooks only.
// Keeps pretool-cache and any other zellij-claude hooks intact.
function removeOldHooks(settings) {
  if (!settings.hooks) return [];

  const removed = [];

  for (const [hookType, entries] of Object.entries(settings.hooks)) {
    if (!Array.isArray(entries)) continue;

    const before = entries.length;
    settings.hooks[hookType] = entries.filter(entry => {
      if (!entry.hooks || !Array.isArray(entry.hooks)) return true;
      // Check if any hook command references zellij-claude AND one of our migrated hooks
      const isOldMigrated = entry.hooks.some(h => {
        if (!h.command || !h.command.includes('zellij-claude')) return false;
        return MIGRATED_HOOKS.some(name => h.command.includes(name));
      });
      if (isOldMigrated) {
        const cmd = entry.hooks.map(h => h.command).join(', ');
        removed.push(`  - Removed from ${hookType}: ${cmd}`);
      }
      return !isOldMigrated;
    });

    // Clean up empty arrays
    if (settings.hooks[hookType].length === 0) {
      delete settings.hooks[hookType];
    }
  }

  return removed;
}

// Check if a relay hook is already registered (idempotency)
function hasRelayHook(entries, hookFile) {
  if (!Array.isArray(entries)) return false;
  return entries.some(entry =>
    entry.hooks && entry.hooks.some(h =>
      h.command && h.command.includes('relay') && h.command.includes(hookFile)
    )
  );
}

function addRelayHooks(settings) {
  if (!settings.hooks) settings.hooks = {};

  const hooksDir = join(__dirname, 'hooks');
  const added = [];

  // SessionStart: sync (no async flag -- must finish before Claude starts)
  if (!settings.hooks.SessionStart) settings.hooks.SessionStart = [];
  if (!hasRelayHook(settings.hooks.SessionStart, 'session-start.cjs')) {
    settings.hooks.SessionStart.push({
      hooks: [{
        type: 'command',
        command: `node "${join(hooksDir, 'session-start.cjs')}"`,
      }],
    });
    added.push('  - SessionStart -> session-start.cjs');
  }

  // Notification(permission_prompt): async
  if (!settings.hooks.Notification) settings.hooks.Notification = [];
  if (!hasRelayHook(settings.hooks.Notification, 'permission-notify.cjs')) {
    settings.hooks.Notification.push({
      matcher: 'permission_prompt',
      hooks: [{
        type: 'command',
        command: `node "${join(hooksDir, 'permission-notify.cjs')}"`,
        async: true,
      }],
    });
    added.push('  - Notification(permission_prompt) -> permission-notify.cjs');
  }

  // Stop: async
  if (!settings.hooks.Stop) settings.hooks.Stop = [];
  if (!hasRelayHook(settings.hooks.Stop, 'session-stop.cjs')) {
    settings.hooks.Stop.push({
      hooks: [{
        type: 'command',
        command: `node "${join(hooksDir, 'session-stop.cjs')}"`,
        async: true,
      }],
    });
    added.push('  - Stop -> session-stop.cjs');
  }

  // PreToolUse(AskUserQuestion): async
  if (!settings.hooks.PreToolUse) settings.hooks.PreToolUse = [];
  if (!hasRelayHook(settings.hooks.PreToolUse, 'ask-notify.cjs')) {
    settings.hooks.PreToolUse.push({
      matcher: 'AskUserQuestion',
      hooks: [{
        type: 'command',
        command: `node "${join(hooksDir, 'ask-notify.cjs')}"`,
        async: true,
      }],
    });
    added.push('  - PreToolUse(AskUserQuestion) -> ask-notify.cjs');
  }

  return added;
}

// Main
const settings = loadSettings();
const removed = removeOldHooks(settings);
const added = addRelayHooks(settings);
saveSettings(settings);

console.log('Relay hook installer');
console.log('====================');

if (removed.length > 0) {
  console.log('\nRemoved old zellij-claude hooks:');
  removed.forEach(r => console.log(r));
} else {
  console.log('\nNo old zellij-claude hooks to remove.');
}

if (added.length > 0) {
  console.log('\nRegistered relay hooks:');
  added.forEach(a => console.log(a));
} else {
  console.log('\nRelay hooks already registered (no changes needed).');
}

console.log(`\nSettings written to: ${SETTINGS_PATH}`);
