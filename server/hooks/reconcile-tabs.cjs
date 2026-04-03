#!/usr/bin/env node
// Reconcile /tmp/zellij-claude-tab-* files against actual Zellij tabs.
// Removes stale tab files that don't match any running @-prefixed tab.
// Can be run standalone or called from other hooks.

const { execSync } = require('child_process');
const { readdirSync, readFileSync, unlinkSync } = require('fs');
const { join } = require('path');

const PREFIX = 'zellij-claude-tab-';

function getActiveTabs(zellijSession) {
  try {
    const raw = execSync(
      `zellij --session ${zellijSession} action list-tabs --json --state`,
      { encoding: 'utf8', timeout: 3000, stdio: ['pipe', 'pipe', 'pipe'] }
    );
    const tabs = JSON.parse(raw);
    return new Set(tabs.filter(t => t.name.startsWith('@')).map(t => t.name.slice(1)));
  } catch {
    return null; // Can't list tabs — don't clean up
  }
}

function reconcile(zellijSession) {
  if (!zellijSession) zellijSession = process.env.ZELLIJ_SESSION_NAME;
  if (!zellijSession) return { removed: 0 };

  const activeKuerzels = getActiveTabs(zellijSession);
  if (!activeKuerzels) return { removed: 0 };

  let removed = 0;
  try {
    const files = readdirSync('/tmp').filter(f => f.startsWith(PREFIX));

    // Group files by kuerzel, keeping only the newest per kuerzel
    const byKuerzel = new Map();
    for (const file of files) {
      const path = join('/tmp', file);
      try {
        const kuerzel = readFileSync(path, 'utf8').trim();
        const stat = require('fs').statSync(path);
        if (!byKuerzel.has(kuerzel)) byKuerzel.set(kuerzel, []);
        byKuerzel.get(kuerzel).push({ path, mtime: stat.mtimeMs });
      } catch {
        // File disappeared or unreadable — remove it
        try { unlinkSync(path); removed++; } catch {}
      }
    }

    for (const [kuerzel, entries] of byKuerzel) {
      if (!activeKuerzels.has(kuerzel)) {
        // Kuerzel not active — remove all files
        for (const e of entries) {
          try { unlinkSync(e.path); removed++; } catch {}
        }
      } else if (entries.length > 1) {
        // Multiple files for same kuerzel — keep only the newest
        entries.sort((a, b) => b.mtime - a.mtime);
        for (let i = 1; i < entries.length; i++) {
          try { unlinkSync(entries[i].path); removed++; } catch {}
        }
      }
    }
  } catch {
    // /tmp read failed — skip
  }
  return { removed };
}

// Run standalone
if (require.main === module) {
  const result = reconcile();
  console.log(`Removed ${result.removed} stale tab file(s).`);
}

module.exports = { reconcile };
