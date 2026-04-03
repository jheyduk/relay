#!/usr/bin/env node
// PreToolUse(AskUserQuestion) hook: sends question notifications to relay via IPC.
// Includes structured question_data for interactive UI rendering in Phase 8.

const { sendRelay, getKuerzel } = require('./send-relay.cjs');

function formatQuestion(kuerzel, q) {
  const header = q.header ? `[${q.header}] ` : '';
  const multi = q.multiSelect ? ' (multiple choice: e.g. 1,3)' : '';
  let text = `@${kuerzel}: ${header}${q.question}${multi}`;

  if (q.options && q.options.length > 0) {
    text += '\n';
    q.options.forEach((opt, i) => {
      text += `\n${i + 1}. ${opt.label}`;
      if (opt.description) text += ` - ${opt.description}`;
    });
  }

  return text;
}

let input = '';
const timeout = setTimeout(() => process.exit(0), 3000);
process.stdin.setEncoding('utf8');
process.stdin.on('data', chunk => (input += chunk));
process.stdin.on('end', async () => {
  clearTimeout(timeout);
  try {
    const data = JSON.parse(input || '{}');
    const kuerzel = getKuerzel(data.session_id);
    if (!kuerzel) { process.exit(0); } // Not a zellij-claude session

    const questions = data.tool_input?.questions;
    if (questions && questions.length > 0) {
      for (const q of questions) {
        const text = formatQuestion(kuerzel, q);
        await sendRelay({
          type: 'question',
          session: kuerzel,
          message: text,
          question_data: {
            question: q.question,
            header: q.header || null,
            multiSelect: q.multiSelect || false,
            options: q.options || [],
          },
          timestamp: Date.now(),
        });
      }
    } else {
      // Fallback: simple question
      const question = data.tool_input?.question || 'Needs your input!';
      const fallbackText = `@${kuerzel}: ${question}`;
      await sendRelay({
        type: 'question',
        session: kuerzel,
        message: fallbackText,
        question_data: {
          question: question,
          header: null,
          multiSelect: false,
          options: [],
        },
        timestamp: Date.now(),
      });
    }
  } catch (e) { process.stderr.write('ask-notify error: ' + e.message + '\n'); }
  process.exit(0);
});
