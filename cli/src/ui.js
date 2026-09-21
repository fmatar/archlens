/**
 * Archlens Terminal UI Toolkit
 * Styled ANSI colors, banners, badges, and interactive prompts.
 * Zero external dependencies.
 */

import * as readline from 'node:readline';

export const colors = {
  reset: '\x1b[0m',
  bold: '\x1b[1m',
  dim: '\x1b[2m',
  italic: '\x1b[3m',
  underline: '\x1b[4m',

  // Foreground
  black: '\x1b[30m',
  red: '\x1b[31m',
  green: '\x1b[32m',
  yellow: '\x1b[33m',
  blue: '\x1b[34m',
  magenta: '\x1b[35m',
  cyan: '\x1b[36m',
  white: '\x1b[37m',
  gray: '\x1b[90m',

  // Bright Foreground
  brightRed: '\x1b[91m',
  brightGreen: '\x1b[92m',
  brightYellow: '\x1b[93m',
  brightBlue: '\x1b[94m',
  brightMagenta: '\x1b[95m',
  brightCyan: '\x1b[96m',
  brightWhite: '\x1b[97m',

  // Background
  bgBlue: '\x1b[44m',
  bgMagenta: '\x1b[45m',
  bgCyan: '\x1b[46m',
  bgDark: '\x1b[100m'
};

const c = colors;

export function banner() {
  const art = `
${c.brightCyan}${c.bold}    _             _     _                    ____  _     _ _ _ 
   / \\   _ __ ___| |__ | | ___ _ __  ___    / ___|| | _ (_) | |
  / _ \\ | '__/ __| '_ \\| |/ _ \\ '_ \\/ __|   \\___ \\| |/ / | | |
 / ___ \\| | | (__| | | | |  __/ | | \\__ \\    ___) |   <| | | |
/_/   \\_\\_|  \\___|_| |_|_|\\___|_| |_|___/___|____/|_|\\_\\_|_|_|
                                       |_____|                 ${c.reset}
${c.dim} Clean Architecture Policy Governance & Multi-Agent Installer${c.reset}
`;
  console.log(art);
}

export function info(message) {
  console.log(`${c.brightCyan}ℹ${c.reset}  ${message}`);
}

export function success(message) {
  console.log(`${c.brightGreen}✔${c.reset}  ${c.bold}${message}${c.reset}`);
}

export function warn(message) {
  console.log(`${c.brightYellow}▲${c.reset}  ${message}`);
}

export function error(message) {
  console.error(`${c.brightRed}✖  ${message}${c.reset}`);
}

export function step(current, total, title) {
  console.log(`\n${c.cyan}[${current}/${total}]${c.reset} ${c.bold}${title}${c.reset}`);
}

export function divider() {
  console.log(`${c.gray}${'─'.repeat(64)}${c.reset}`);
}

export function badge(label, color = c.bgCyan + c.black) {
  return `${color} ${label} ${c.reset}`;
}

export function table(rows) {
  const maxKeyLen = Math.max(...rows.map(([k]) => k.length), 0);
  for (const [key, value] of rows) {
    console.log(`  ${c.dim}${key.padEnd(maxKeyLen + 2)}${c.reset} ${c.brightWhite}${value}${c.reset}`);
  }
}

export function createPrompt() {
  return readline.createInterface({
    input: process.stdin,
    output: process.stdout
  });
}

export async function askQuestion(query, defaultVal = '') {
  const rl = createPrompt();
  const suffix = defaultVal ? ` ${c.dim}(default: ${defaultVal})${c.reset}: ` : ': ';
  return new Promise((resolve) => {
    rl.question(`${c.brightCyan}?${c.reset} ${query}${suffix}`, (answer) => {
      rl.close();
      resolve(answer.trim() || defaultVal);
    });
  });
}

export async function askChoice(title, options) {
  console.log(`\n${c.brightCyan}?${c.reset} ${c.bold}${title}${c.reset}`);
  options.forEach((opt, idx) => {
    console.log(`  ${c.cyan}${idx + 1})${c.reset} ${opt.label} ${c.dim}${opt.description || ''}${c.reset}`);
  });

  const rl = createPrompt();
  return new Promise((resolve) => {
    const ask = () => {
      rl.question(`\n${c.dim}Select an option [1-${options.length}]: ${c.reset}`, (answer) => {
        const num = parseInt(answer.trim(), 10);
        if (num >= 1 && num <= options.length) {
          rl.close();
          resolve(options[num - 1].value);
        } else {
          console.log(`${c.yellow}Invalid choice. Please enter a number between 1 and ${options.length}.${c.reset}`);
          ask();
        }
      });
    };
    ask();
  });
}

export async function askConfirm(query, defaultYes = true) {
  const rl = createPrompt();
  const hint = defaultYes ? '[Y/n]' : '[y/N]';
  return new Promise((resolve) => {
    rl.question(`${c.brightCyan}?${c.reset} ${query} ${c.dim}${hint}${c.reset}: `, (answer) => {
      rl.close();
      const val = answer.trim().toLowerCase();
      if (!val) {
        resolve(defaultYes);
      } else {
        resolve(val === 'y' || val === 'yes');
      }
    });
  });
}
