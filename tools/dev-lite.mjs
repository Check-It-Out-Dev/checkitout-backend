#!/usr/bin/env node
/**
 * checkItOut dev-lite wizard — brings the whole platform up locally with no
 * credentials, no accounts and no vendor sign-ups.
 *
 * It narrates what it is about to do, checks whether each thing is already in
 * place, does the missing part, then verifies the result before moving on. Any
 * step can be re-run: the wizard is idempotent, so if something fails you fix
 * it and start it again.
 *
 *   node tools/dev-lite.mjs            interactive
 *   node tools/dev-lite.mjs --yes      accept every prompt (CI / scripted)
 *   node tools/dev-lite.mjs --no-fe    backend + database only
 *   node tools/dev-lite.mjs --stop     stop everything it started
 *   node tools/dev-lite.mjs --status   report what is running
 *
 * Only Node built-ins are used, so it runs from a fresh clone before any
 * `npm install`. Full walkthrough + troubleshooting: docs/DEV-LITE.md
 */
import { spawn, spawnSync } from 'node:child_process';
import { setDefaultResultOrder } from 'node:dns';
import { createWriteStream, existsSync, mkdirSync, readFileSync, readdirSync, rmSync, writeFileSync } from 'node:fs';
import { createInterface } from 'node:readline/promises';
import { createConnection } from 'node:net';
import { request as httpsRequest } from 'node:https';
import { homedir, platform } from 'node:os';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

// Tomcat binds IPv4 here, while modern Node resolves "localhost" to ::1 first
// on Linux and WSL — the health probe would then never connect to a backend
// that is perfectly healthy, and the wizard would wait forever.
setDefaultResultOrder('ipv4first');

const WIN = platform() === 'win32';
const BE_ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const STATE_DIR = join(BE_ROOT, '.dev-lite');
const LOG_FILE = join(STATE_DIR, 'wizard.log');
const BE_LOG = join(STATE_DIR, 'backend.log');
const FE_LOG = join(STATE_DIR, 'frontend.log');
const PID_FILE = join(STATE_DIR, 'pids.json');

const args = new Set(process.argv.slice(2));
const AUTO = args.has('--yes') || args.has('-y');
const NO_FE = args.has('--no-fe');

// ── terminal helpers ───────────────────────────────────────────────────────
const COLOR = process.stdout.isTTY && !process.env.NO_COLOR;
const c = (code, s) => (COLOR ? `\x1b[${code}m${s}\x1b[0m` : s);
const bold = (s) => c('1', s);
const dim = (s) => c('2', s);
const green = (s) => c('32', s);
const yellow = (s) => c('33', s);
const red = (s) => c('31', s);
const cyan = (s) => c('36', s);

mkdirSync(STATE_DIR, { recursive: true });
const logStream = createWriteStream(LOG_FILE, { flags: 'a' });
function log(line) {
  logStream.write(`${new Date().toISOString()} ${line}\n`);
}
function say(line = '') {
  console.log(line);
  log(line.replace(/\x1b\[[0-9;]*m/g, ''));
}

let stepNo = 0;
let stepTotal = 0;
function heading(title, explanation) {
  stepNo += 1;
  say('');
  say(bold(`[${stepNo}/${stepTotal}] ${title}`));
  if (explanation) say(dim(`      ${explanation}`));
}
const ok = (msg) => say(`      ${green('✓')} ${msg}`);
const warn = (msg) => say(`      ${yellow('!')} ${msg}`);
const fail = (msg) => say(`      ${red('✗')} ${msg}`);

async function ask(question, fallback = true) {
  if (AUTO) return fallback;
  const rl = createInterface({ input: process.stdin, output: process.stdout });
  const suffix = fallback ? '[Y/n]' : '[y/N]';
  const answer = (await rl.question(`      ${cyan('?')} ${question} ${suffix} `)).trim().toLowerCase();
  rl.close();
  if (!answer) return fallback;
  return answer.startsWith('y');
}

/** Thrown by die() so nothing runs after a fatal; caught only in main. */
class Fatal extends Error {}

function die(message, hint) {
  fail(message);
  if (hint) say(dim(`      → ${hint}`));
  say('');
  say(dim(`Full log: ${LOG_FILE}`));
  // Flush the log before leaving — an immediate exit() drops buffered writes,
  // which would hide the very command that failed.
  logStream.end(() => process.exit(1));
  setTimeout(() => process.exit(1), 1000).unref();
  // The exit above is asynchronous, so without this the caller would carry on
  // for another second and print steps that are not happening.
  throw new Fatal(message);
}

// ── process / network helpers ──────────────────────────────────────────────
function run(cmd, argv, opts = {}) {
  log(`$ ${cmd} ${argv.join(' ')}`);
  // Windows needs a shell (.cmd wrappers), and a shell plus a separate argv is
  // what trips Node's escaping warning — pass one command string there.
  const r = WIN
    ? spawnSync([cmd, ...argv].join(' '), { encoding: 'utf8', shell: true, ...opts })
    : spawnSync(cmd, argv, { encoding: 'utf8', ...opts });
  // Log the tail, not the head: the reason a command failed is at the end.
  const out = `${(r.stdout || '').trim()}\n${(r.stderr || '').trim()}`.trim();
  log(`  exit=${r.status} ${out.length > 1500 ? `…${out.slice(-1500)}` : out}`);
  return r;
}
const runOk = (cmd, argv, opts) => run(cmd, argv, opts).status === 0;

function portInUseOn(port, host) {
  return new Promise((done) => {
    const socket = createConnection({ port, host });
    const finish = (v) => {
      socket.destroy();
      done(v);
    };
    socket.setTimeout(700);
    socket.on('connect', () => finish(true));
    socket.on('timeout', () => finish(false));
    socket.on('error', () => finish(false));
  });
}

/**
 * Busy on EITHER loopback stack counts as busy.
 *
 * A server bound only to ::1 is invisible to an IPv4 probe, and the tools we
 * launch resolve "localhost" their own way — the Angular dev-server binds the
 * IPv6 address and then fails with "port already in use" for a port this
 * wizard had just declared free. Checking one family is how you promise a free
 * port and hand over a busy one.
 */
async function portInUse(port) {
  const [v4, v6] = await Promise.all([portInUseOn(port, '127.0.0.1'), portInUseOn(port, '::1')]);
  return v4 || v6;
}

/** GET/POST over https ignoring the self-signed dev certificate. */
function httpsCall(url, { method = 'GET', body, cookies } = {}) {
  return new Promise((done) => {
    const u = new URL(url);
    const payload = body ? JSON.stringify(body) : null;
    const req = httpsRequest(
      {
        hostname: u.hostname,
        port: u.port,
        path: u.pathname + u.search,
        method,
        rejectUnauthorized: false,
        headers: {
          // A session is bound to a request fingerprint that includes the
          // User-Agent; Node sends none by default, and an absent one does not
          // match the absent one recorded at mint time — every later call
          // would come back 401.
          'User-Agent': 'checkitout-dev-lite-wizard',
          ...(payload ? { 'Content-Type': 'application/json', 'Content-Length': Buffer.byteLength(payload) } : {}),
          ...(cookies ? { Cookie: cookies } : {}),
        },
      },
      (res) => {
        let data = '';
        res.on('data', (chunk) => (data += chunk));
        res.on('end', () => done({ status: res.statusCode, body: data, headers: res.headers }));
      },
    );
    req.on('error', (err) => done({ status: 0, body: String(err.message), headers: {} }));
    req.setTimeout(8000, () => {
      req.destroy();
      done({ status: 0, body: 'timeout', headers: {} });
    });
    if (payload) req.write(payload);
    req.end();
  });
}

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

async function waitFor(label, probe, { attempts = 60, everyMs = 3000, onTick } = {}) {
  for (let i = 1; i <= attempts; i += 1) {
    if (await probe()) return true;
    if (onTick) onTick(i, attempts);
    else if (i % 5 === 0) say(dim(`      …still waiting for ${label} (${i * everyMs / 1000}s)`));
    await sleep(everyMs);
  }
  return false;
}

function readState() {
  try {
    const raw = JSON.parse(readFileSync(PID_FILE, 'utf8'));
    // The first version of this file was a flat pid map. Keep a stack started
    // by that version stoppable instead of orphaning it on an upgrade.
    return raw && raw.pids ? raw : { pids: raw || {}, ports: null };
  } catch {
    return { pids: {}, ports: null };
  }
}
const readPids = () => readState().pids;
/**
 * Records what this run started, and the ports it settled on. `--stop` needs
 * both: without the ports a remapped stack would be stopped by number alone —
 * missing its own services while reaching for someone else's.
 */
function writeState(patch) {
  const state = readState();
  writeFileSync(PID_FILE, JSON.stringify({ ...state, ...patch, ports: { ...ports } }, null, 2));
}
const writePids = (next) => writeState({ pids: { ...readState().pids, ...next } });
function alive(pid) {
  if (!pid) return false;
  try {
    process.kill(pid, 0);
    return true;
  } catch {
    return false;
  }
}
function killTree(pid) {
  if (!alive(pid)) return;
  if (WIN) run('taskkill', ['/PID', String(pid), '/T', '/F']);
  else {
    try {
      process.kill(-pid, 'SIGTERM');
    } catch {
      try {
        process.kill(pid, 'SIGTERM');
      } catch {
        /* already gone */
      }
    }
  }
}

/** Spawns a long-running process detached, piping output to a log file. */
function spawnBackground(cmd, argv, { cwd, env, logFile, onSpawnError }) {
  const out = createWriteStream(logFile, { flags: 'a' });
  log(`$ (background) ${cmd} ${argv.join(' ')}  > ${logFile}`);
  // On Windows the command goes through a shell (the Maven wrapper is a .cmd),
  // and passing a separate argv there triggers Node's escaping warning — build
  // one command string instead. Elsewhere spawn the binary directly.
  const child = WIN
    ? spawn([cmd, ...argv].join(' '), { cwd, env, shell: true, stdio: ['ignore', 'pipe', 'pipe'] })
    : spawn(cmd, argv, { cwd, env, detached: true, stdio: ['ignore', 'pipe', 'pipe'] });
  child.on('error', (err) => {
    log(`  spawn error: ${err.message}`);
    if (onSpawnError) onSpawnError(err);
    else die(`Could not start ${cmd}: ${err.message}`);
  });
  child.stdout.pipe(out);
  child.stderr.pipe(out);
  child.unref();
  return child;
}

/**
 * A POSIX script cloned on Windows can carry CRLF line endings, and the kernel
 * then hunts for an interpreter named "/bin/sh\r". .gitattributes prevents that
 * for fresh clones; this repairs a working copy that predates it.
 */
function ensureRunnableScript(path) {
  if (WIN) return;
  run('chmod', ['+x', path]); // git does not always preserve the executable bit
  try {
    const body = readFileSync(path, 'utf8');
    if (body.includes('\r\n')) {
      writeFileSync(path, body.replace(/\r\n/g, '\n'));
      log(`  normalized CRLF line endings in ${path}`);
    }
  } catch {
    /* unreadable — let the spawn report it */
  }
}

/** One line describing what the backend is busy with, for the waiting spinner. */
function backendActivity() {
  const text = tail(BE_LOG, 40);
  if (/Started InstagramPlatformApplication/.test(text)) return 'application started, waiting for health';
  if (/Liquibase|ChangeSet|liquibase/.test(text)) return 'running database migrations';
  if (/Tomcat initialized|Starting .*Application/.test(text)) return 'booting Spring';
  if (/BUILD FAILURE|ERROR\]/.test(text)) return 'the build reported an error — see the log';
  if (/Compiling|compiler:compile/.test(text)) return 'compiling sources';
  if (/Downloading|Downloaded/.test(text)) return 'downloading Maven dependencies';
  return 'still working';
}

function tail(file, lines = 25) {
  try {
    return readFileSync(file, 'utf8').split('\n').slice(-lines).join('\n');
  } catch {
    return '(no output captured)';
  }
}

// ── toolchain discovery ────────────────────────────────────────────────────
/**
 * A JDK 21 — the build enforces [21,22) via maven-enforcer, and it must be a
 * full JDK: a JRE runs the app but has no `javac`, so Maven dies mid-compile
 * with a confusing "Cannot run program javac".
 */
function findJdk21() {
  const exe = (home, name) => join(home, 'bin', WIN ? `${name}.exe` : name);
  const probe = (home) => {
    if (!home) return null;
    if (!existsSync(exe(home, 'java')) || !existsSync(exe(home, 'javac'))) return null;
    // Quote only where a shell is involved (Windows); a bare spawn treats the
    // quotes as part of the filename and fails with ENOENT.
    const bin = exe(home, 'java');
    const r = run(WIN ? `"${bin}"` : bin, ['-version']);
    const version = `${r.stderr || ''}${r.stdout || ''}`;
    return /\b21[."]/.test(version) ? home : null;
  };

  const candidates = [process.env.BE_JAVA_HOME, process.env.JAVA_HOME];
  const roots = [
    // A JDK this wizard downloaded earlier — look here first so a re-run never
    // fetches 190 MB twice.
    join(STATE_DIR, 'jdk'),
    join(homedir(), '.jdks'),
    '/usr/lib/jvm',
    '/Library/Java/JavaVirtualMachines',
    'C:\\Program Files\\Java',
    'C:\\Program Files\\Eclipse Adoptium',
    'C:\\Program Files\\Amazon Corretto',
  ];
  for (const root of roots) {
    try {
      for (const entry of readdirSync(root)) {
        if (/21/.test(entry)) {
          candidates.push(join(root, entry));
          // macOS bundles live one level deeper.
          candidates.push(join(root, entry, 'Contents', 'Home'));
        }
      }
    } catch {
      /* root absent — fine */
    }
  }
  for (const home of candidates) {
    const found = probe(home);
    if (found) return found;
  }
  // Last resort: a JDK 21 already on PATH (javac must be there too).
  const javacOnPath = run('javac', ['-version']);
  if (javacOnPath.status === 0 && /\b21[."]/.test(`${javacOnPath.stderr}${javacOnPath.stdout}`)) {
    return null; // usable as-is, no JAVA_HOME juggling needed
  }
  return undefined; // nothing found
}

/**
 * Follows redirects and streams a URL to disk, printing progress. A stalled
 * connection is the common failure on a large download — it produces no error
 * event, it simply stops delivering — so a silence timer aborts and the caller
 * retries.
 */
function downloadOnce(url, destination, label, stallMs = 30_000) {
  return new Promise((done, failed) => {
    let stall;
    const get = (target, hops = 0) => {
      if (hops > 5) return failed(new Error('too many redirects'));
      const req = httpsRequest(target, { method: 'GET' }, (res) => {
        if ([301, 302, 303, 307, 308].includes(res.statusCode)) {
          res.resume();
          return get(new URL(res.headers.location, target).toString(), hops + 1);
        }
        if (res.statusCode !== 200) {
          res.resume();
          return failed(new Error(`HTTP ${res.statusCode}`));
        }
        const total = Number(res.headers['content-length'] || 0);
        let seen = 0;
        let lastPrint = 0;
        const file = createWriteStream(destination);
        const abort = () => {
          clearTimeout(stall);
          req.destroy();
          file.destroy();
          failed(new Error(`stalled after ${Math.round(seen / 1e6)} MB`));
        };
        const bump = () => {
          clearTimeout(stall);
          stall = setTimeout(abort, stallMs);
        };
        bump();
        res.on('data', (chunk) => {
          seen += chunk.length;
          bump();
          const pct = total ? Math.floor((seen / total) * 100) : 0;
          if (pct >= lastPrint + 10) {
            lastPrint = pct;
            say(dim(`      …${label} ${pct}%`));
          }
        });
        res.pipe(file);
        file.on('finish', () => {
          clearTimeout(stall);
          file.close(() => done(destination));
        });
        file.on('error', (err) => {
          clearTimeout(stall);
          failed(err);
        });
      });
      req.on('error', (err) => {
        clearTimeout(stall);
        failed(err);
      });
      req.end();
    };
    get(url);
  });
}

async function download(url, destination, label, attempts = 3) {
  for (let attempt = 1; attempt <= attempts; attempt += 1) {
    try {
      return await downloadOnce(url, destination, label);
    } catch (err) {
      rmSync(destination, { force: true });
      if (attempt === attempts) throw err;
      warn(`${label} failed (${err.message}) — retrying (${attempt + 1}/${attempts})`);
    }
  }
  throw new Error('unreachable');
}

/**
 * Downloads a Temurin JDK 21 into .dev-lite/jdk — no administrator rights, no
 * system-wide install, nothing outside this repository. Returns its JAVA_HOME.
 */
async function downloadJdk21() {
  const osName = WIN ? 'windows' : platform() === 'darwin' ? 'mac' : 'linux';
  const arch = process.arch === 'arm64' ? 'aarch64' : 'x64';
  const ext = WIN ? 'zip' : 'tar.gz';
  const url = `https://api.adoptium.net/v3/binary/latest/21/ga/${osName}/${arch}/jdk/hotspot/normal/eclipse`;
  const jdkDir = join(STATE_DIR, 'jdk');
  const archive = join(STATE_DIR, `jdk21.${ext}`);

  mkdirSync(jdkDir, { recursive: true });
  say(dim(`      Downloading Eclipse Temurin 21 for ${osName}/${arch} (~190 MB)…`));
  await download(url, archive, 'download');

  say(dim('      Extracting…'));
  const extracted = WIN
    ? run('powershell', ['-NoProfile', '-Command', `Expand-Archive -Force -Path "${archive}" -DestinationPath "${jdkDir}"`])
    : run('tar', ['-xzf', archive, '-C', jdkDir]);
  if (extracted.status !== 0) throw new Error('could not extract the JDK archive');
  rmSync(archive, { force: true });

  // The archive contains a single top-level directory; on macOS the real home
  // sits deeper inside the bundle.
  const [top] = readdirSync(jdkDir);
  const home = osName === 'mac' ? join(jdkDir, top, 'Contents', 'Home') : join(jdkDir, top);
  if (!existsSync(join(home, 'bin', WIN ? 'javac.exe' : 'javac'))) {
    throw new Error(`unexpected archive layout under ${jdkDir}`);
  }
  return home;
}

/**
 * The `ssl` profile expects a keystore that is deliberately NOT in the
 * repository (it is a certificate, however self-signed). Generate one into
 * .dev-lite with the JDK's own keytool — the frontend proxy needs an HTTPS
 * backend, and browsers only need the warning accepted once.
 */
function ensureKeystore(javaHome) {
  const keystore = join(STATE_DIR, 'keystore.p12');
  if (existsSync(keystore)) return keystore;
  const keytool = javaHome ? join(javaHome, 'bin', WIN ? 'keytool.exe' : 'keytool') : 'keytool';
  const r = run(WIN && javaHome ? `"${keytool}"` : keytool, [
    '-genkeypair',
    '-alias', 'tomcat',
    '-keyalg', 'RSA',
    '-keysize', '2048',
    '-storetype', 'PKCS12',
    '-keystore', WIN ? `"${keystore}"` : keystore,
    '-validity', '3650',
    '-storepass', 'changeit',
    '-dname', WIN ? '"CN=localhost, OU=dev-lite, O=checkItOut, C=PL"' : 'CN=localhost, OU=dev-lite, O=checkItOut, C=PL',
    '-ext', WIN ? '"SAN=dns:localhost,ip:127.0.0.1"' : 'SAN=dns:localhost,ip:127.0.0.1',
  ]);
  if (r.status !== 0 || !existsSync(keystore)) {
    die('Could not generate the development certificate.', `keytool output is in ${LOG_FILE}`);
  }
  return keystore;
}

function dockerComposeCmd() {
  if (runOk('docker', ['compose', 'version'])) return ['docker', ['compose']];
  if (runOk('docker-compose', ['version'])) return ['docker-compose', []];
  return null;
}

// ── the steps ──────────────────────────────────────────────────────────────
// Ports are overridable because a busy 5432 (a system PostgreSQL) is the most
// common thing standing between a stranger and a running stack. Overriding
// also isolates a second stack on the same machine.
const DEFAULT_PORTS = { pg: 5432, redis: 6379, be: 8080, fe: 4201, smtp: 3025 };
const ALT_PORTS = { pg: 5442, redis: 6389, be: 8081, fe: 4211, smtp: 3035 };
const ports = {
  pg: Number(process.env.DEV_LITE_PG_PORT || DEFAULT_PORTS.pg),
  redis: Number(process.env.DEV_LITE_REDIS_PORT || DEFAULT_PORTS.redis),
  be: Number(process.env.DEV_LITE_BE_PORT || DEFAULT_PORTS.be),
  fe: Number(process.env.DEV_LITE_FE_PORT || DEFAULT_PORTS.fe),
  // GreenMail's in-process SMTP listener — the backend binds it itself.
  smtp: Number(process.env.DEV_LITE_SMTP_PORT || DEFAULT_PORTS.smtp),
};
const COMPOSE_PROJECT = process.env.DEV_LITE_COMPOSE_PROJECT || '';
// This one reaches a Windows command line, so keep it to the shape compose
// accepts anyway — no quoting, no separators, nothing a shell would reinterpret.
if (COMPOSE_PROJECT && !/^[a-z0-9][a-z0-9_-]*$/.test(COMPOSE_PROJECT)) {
  fail(`DEV_LITE_COMPOSE_PROJECT="${COMPOSE_PROJECT}" is not a valid compose project name.`);
  say(dim('      Allowed: lowercase letters, digits, dash and underscore, starting with a letter or digit.'));
  process.exit(1);
}
// Readiness (database, disk, migrations) is the honest "can I use it" signal.
// The aggregate /health also folds in optional vendor probes, which are DOWN
// by design in dev-lite — a credential-less clone has no Google Cloud.
const healthUrl = () => `https://localhost:${ports.be}/api/actuator/health/readiness`;
const api = (path) => `https://localhost:${ports.be}/api${path}`;
const SEEDED_ACTORS = [
  { email: 'company@checkitout.app', role: 'COMPANY', label: 'Company — owns the seeded campaigns' },
  { email: 'test.influencer@test.com', role: 'INFLUENCER', label: 'Influencer — applies to campaigns' },
  { email: 'test.admin@test.com', role: 'ADMIN', label: 'Admin — user management, dictionary, tickets' },
];

async function stepIntro() {
  say('');
  say(bold('  checkItOut — dev-lite wizard'));
  say(dim('  Brings up the full platform locally: PostgreSQL + Redis in Docker, the Spring Boot'));
  say(dim('  backend in its credential-less "dev-lite" profile, and the Angular frontend.'));
  say('');
  say(dim('  No accounts, no API keys, no vendor sign-ups. Payments and invoicing stay off,'));
  say(dim('  company-registry lookups and e-mail are simulated locally, uploads land on disk.'));
  say(dim('  What is real and what is simulated: docs/DEV-LITE.md'));
}

async function stepPrereqs(ctx) {
  heading('Checking the tools this needs', 'git, Docker, a JDK 21 and (for the frontend) Node.js');

  if (!runOk('git', ['--version'])) die('git not found on PATH.', 'Install git, reopen the terminal, run this again.');
  ok('git');

  const compose = dockerComposeCmd();
  if (!compose) {
    die(
      'Docker not available (neither `docker compose` nor `docker-compose` responded).',
      'Install Docker Desktop (Windows/macOS) or the docker engine + compose plugin (Linux), start it, then re-run.',
    );
  }
  if (!runOk('docker', ['info'])) {
    die('Docker is installed but the daemon is not responding.', 'Start Docker Desktop / `sudo systemctl start docker`, then re-run.');
  }
  ctx.compose = compose;
  ok(`Docker (${compose[0]} ${compose[1].join(' ')})`);

  let jdk = findJdk21();
  if (jdk === undefined) {
    warn('No JDK 21 found — the build enforces Java 21, and needs a full JDK (a JRE cannot compile).');
    say(dim('      The wizard can fetch one into .dev-lite/jdk: no administrator rights,'));
    say(dim('      nothing installed system-wide, deleted whenever you delete this directory.'));
    if (await ask('Download Eclipse Temurin 21 (~190 MB)?', true)) {
      try {
        jdk = await downloadJdk21();
      } catch (err) {
        fail(`Download failed: ${err.message}`);
        say(dim('      Install a JDK 21 yourself and re-run:'));
        say(dim(WIN ? '        winget install EclipseAdoptium.Temurin.21.JDK' : '        sudo apt install openjdk-21-jdk    # or: brew install openjdk@21'));
        process.exit(1);
      }
    } else {
      say(dim('      Install one yourself, then re-run:'));
      say(dim(WIN ? '        winget install EclipseAdoptium.Temurin.21.JDK' : '        sudo apt install openjdk-21-jdk    # or: brew install openjdk@21'));
      say(dim('      Already have it somewhere unusual? Re-run with BE_JAVA_HOME=/path/to/jdk-21'));
      process.exit(1);
    }
  }
  ctx.javaHome = jdk || undefined; // null = a suitable JDK is already on PATH
  ok(jdk ? `JDK 21 at ${jdk}` : 'JDK 21 on PATH');

  if (!NO_FE) {
    const node = run('node', ['--version']);
    const version = (node.stdout || '').trim();
    const [major, minor] = version.replace(/^v/, '').split('.').map(Number);
    // The dev-server reads the system trust store through tls.getCACertificates,
    // which landed in 22.22 and 24.15. On 23.x it does not exist at all: the
    // server exits during startup with an error that reads like a project fault,
    // so refuse that line explicitly rather than letting it look broken.
    const usable =
      Number.isFinite(major) &&
      Number.isFinite(minor) &&
      ((major === 22 && minor >= 22) || (major === 24 && minor >= 15) || major > 24);
    if (!usable) {
      warn(`Node ${version || '?'} cannot run the frontend dev-server (needs 22.22+ or 24.15+; the 23.x line never got tls.getCACertificates).`);
      warn('Continuing with the backend only — install a supported Node and re-run to get the UI.');
      ctx.skipFe = true;
    } else {
      ok(`Node ${version}`);
    }
  }
}

async function stepPorts(ctx) {
  heading(
    'Checking the ports',
    `${ports.pg} PostgreSQL · ${ports.redis} Redis · ${ports.be} backend · ${ports.smtp} mailbox · ${ports.fe} frontend`,
  );
  const wanted = [
    ['pg', 'PostgreSQL'],
    ['redis', 'Redis'],
    ['be', 'backend'],
    ['smtp', 'local mailbox (SMTP)'],
    ...(NO_FE || ctx.skipFe ? [] : [['fe', 'frontend']]),
  ];
  const busy = [];
  for (const [key, label] of wanted) {
    if (await portInUse(ports[key])) busy.push([key, label]);
  }
  if (busy.length === 0) {
    ok('all free');
    return;
  }

  // A clash on the database ports may simply be this wizard's own containers
  // from an earlier run. compose can answer that precisely — `ps -q` lists
  // containers of THIS project only — which beats guessing from the port.
  const infraBusy = busy.filter(([key]) => key === 'pg' || key === 'redis');
  const ownInfra = infraBusy.length > 0 && infraIsOurs(ctx);
  if (ownInfra) {
    ok('the databases on these ports are this wizard\'s own containers — reusing them');
  }

  // A live backend on the expected port is a re-run — but only if the rest of
  // the stack is ours too. A healthy backend proves something is listening, not
  // that it belongs to this project: a second dev-lite stack answers the same
  // way while its databases sit on ports we are about to hand to compose.
  const remaining = busy.filter(([key]) => !(ownInfra && (key === 'pg' || key === 'redis')));
  const beBusy = remaining.some(([key]) => key === 'be');
  if (beBusy && (ownInfra || infraBusy.length === 0) && (await httpsCall(healthUrl())).status === 200) {
    ok('a healthy backend is already on this port — this is a re-run, reusing it');
    return;
  }
  if (remaining.length === 0) {
    return;
  }

  for (const [key, label] of remaining) warn(`port ${ports[key]} (${label}) is in use`);

  say(dim('      Most often this is a system PostgreSQL, or a second copy of this stack.'));
  say(dim(`      The wizard can move its own services to spare ports: ${ALT_PORTS.pg} / ${ALT_PORTS.redis} / ${ALT_PORTS.be} / ${ALT_PORTS.fe}.`));
  // Unattended runs move out of the way. Staying put would mean migrating a
  // stranger's PostgreSQL and seeding demo data into it — silently, since
  // nobody is watching the prompt.
  if (AUTO) say(dim('      Unattended run (--yes): moving rather than assuming these services are ours.'));
  if (AUTO || (await ask('Move to the spare ports?', true))) {
    for (const [key] of remaining) ports[key] = ALT_PORTS[key];
    ctx.remapped = true;
    // The spare set is one fixed alternative, so it can be occupied too — most
    // obviously when the ports being vacated ARE the spare ones. Say so here
    // rather than letting docker fail three steps later with a port message
    // that names nothing the reader chose.
    const stillBusy = [];
    for (const [key, label] of remaining) {
      if (await portInUse(ports[key])) stillBusy.push(`${ports[key]} (${label})`);
    }
    if (stillBusy.length > 0) {
      fail(`the spare ports are taken as well: ${stillBusy.join(', ')}`);
      say(dim('      Free them, stop the other stack, or pick your own with'));
      say(dim('        DEV_LITE_PG_PORT=… DEV_LITE_REDIS_PORT=… DEV_LITE_BE_PORT=… DEV_LITE_FE_PORT=… DEV_LITE_SMTP_PORT=…'));
      die('No free ports to run on.', `Full log: ${LOG_FILE}`);
    }
    ok(`using ${ports.pg} / ${ports.redis} / ${ports.be} / ${ports.smtp} / ${ports.fe}`);
    return;
  }
  say(dim('      Keeping the standard ports — the wizard will reuse whatever is already there.'));
}

/**
 * True when the databases on these ports are containers this wizard created.
 *
 * Two conditions, because either alone has been wrong: the containers must
 * carry this stack's own name (so a developer's identically-built database is
 * not mistaken for ours), and they must be published on the ports we are asking
 * about (so a stack of ours on other ports does not vouch for someone else's).
 */
function infraIsOurs(ctx) {
  if (!ctx.compose) return false;
  const [cmd, base] = ctx.compose;
  const suffix = COMPOSE_PROJECT || `p${ports.pg}`;
  const r = run(cmd, [...base, ...composeArgs(), 'ps', '--format', '{{.Name}} {{.Publishers}}'], { cwd: BE_ROOT });
  if (r.status !== 0) return false;
  const lines = (r.stdout || '').split('\n').filter((l) => l.includes(`checkitout-devlite-${suffix}-`));
  const publishes = (port) => lines.some((l) => l.includes(`:${port}->`) || l.includes(`${port}/tcp`));
  return lines.length > 0 && publishes(ports.pg) && publishes(ports.redis);
}

/**
 * compose argv: the base file, always an override, always our own project.
 *
 * The override is not optional. The base file pins `container_name`, and a
 * pinned name is global: a container someone else started from this same file
 * answers `compose ps` no matter which project asks, so the wizard would take
 * a developer's own database for one of its own and try to adopt it. Naming
 * every container after this stack keeps the two apart and makes ownership a
 * fact rather than an inference.
 */
function composeArgs() {
  const argv = ['-f', join(BE_ROOT, 'docker-compose-dev-redis.yml')];
  {
    const suffix = COMPOSE_PROJECT || `p${ports.pg}`;
    const override = join(STATE_DIR, 'compose.override.yml');
    writeFileSync(
      override,
      [
        '# Generated by tools/dev-lite.mjs — ports/container names for this stack.',
        '# `!override` is required: compose APPENDS port lists by default, so a',
        '# plain mapping would keep the base 5432/6379 bindings and clash with',
        '# whatever already holds them (needs compose v2.24+).',
        'services:',
        '  postgres:',
        `    container_name: checkitout-devlite-${suffix}-postgres`,
        '    ports: !override',
        `      - "${ports.pg}:5432"`,
        '  redis:',
        `    container_name: checkitout-devlite-${suffix}-redis`,
        '    ports: !override',
        `      - "${ports.redis}:6379"`,
        // The base file pins the network name; a second stack must not try to
        // adopt the first one's network (compose refuses across projects).
        'networks:',
        '  default:',
        `    name: checkitout-devlite-${suffix}-network`,
        '',
      ].join('\n'),
    );
    argv.push('-f', override);
  }
  // Always our own project too, so `compose ps` answers about this stack and
  // nothing else. Derived from the port when unset, which keeps two wizard
  // stacks on one machine apart as well.
  argv.push('-p', COMPOSE_PROJECT || `checkitout-devlite-p${ports.pg}`);
  return argv;
}

async function stepInfra(ctx) {
  heading('Starting PostgreSQL and Redis', 'docker compose, just those two services — no application image is built');
  // Adopting a database that was already up is fine; claiming it at --stop time
  // is not. Record which of the two happened.
  const wasAlreadyUp = await portInUse(ports.pg);
  const [cmd, base] = ctx.compose;
  const r = run(cmd, [...base, ...composeArgs(), 'up', '-d', 'postgres', 'redis'], { cwd: BE_ROOT });
  if (r.status !== 0) {
    const reason = `${r.stderr || ''}${r.stdout || ''}`.trim().split('\n').slice(-3).join('\n');
    if (reason) say(dim(`      ${reason}`));
    die('docker compose could not start the databases.', `Full output: ${LOG_FILE}`);
  }
  const up = await waitFor('PostgreSQL', () => portInUse(ports.pg), { attempts: 30, everyMs: 1000 });
  if (!up) die(`PostgreSQL did not start listening on ${ports.pg}.`, 'Check `docker ps` and the container logs.');
  writeState({ startedInfra: !wasAlreadyUp });
  ok(`postgres (:${ports.pg}) + redis (:${ports.redis}) are ${wasAlreadyUp ? 'already running' : 'up'}`);
  say(dim('      The backend creates its database, user and extensions on first boot.'));
}

async function stepBackend(ctx) {
  heading('Starting the backend', 'profile dev-lite,ssl — first run downloads Maven dependencies (a few minutes)');

  const already = (await httpsCall(healthUrl())).status === 200;
  if (already) {
    ok(`a backend is already answering on ${ports.be} — reusing it`);
    return;
  }

  const mvnw = join(BE_ROOT, WIN ? 'mvnw.cmd' : 'mvnw');
  ensureRunnableScript(mvnw);
  const keystore = ensureKeystore(ctx.javaHome);
  const env = {
    ...process.env,
    SPRING_PROFILES_ACTIVE: 'dev-lite,ssl',
    SERVER_PORT: String(ports.be),
    SPRING_DATASOURCE_PORT: String(ports.pg),
    SPRING_DATA_REDIS_PORT: String(ports.redis),
    // GreenMail binds this itself; spring.mail.port must agree with it.
    GREENMAIL_SMTP_PORT: String(ports.smtp),
    SPRING_MAIL_PORT: String(ports.smtp),
    // dev-lite pulls in dev through a profile group, and group members are
    // applied AFTER the profile that pulled them — so application-dev.yml
    // would otherwise win here and the offline-image changeset would never
    // run. An environment variable beats both files.
    SPRING_LIQUIBASE_CONTEXTS: 'dev,local,dev-lite',
    // The repository ships no certificate; point the ssl profile at ours.
    SERVER_SSL_KEY_STORE: `file:${keystore.replace(/\\/g, '/')}`,
    SERVER_SSL_KEY_STORE_PASSWORD: 'changeit',
    SERVER_SSL_KEY_STORE_TYPE: 'PKCS12',
    SERVER_SSL_KEY_ALIAS: 'tomcat',
  };
  if (ctx.javaHome) {
    env.JAVA_HOME = ctx.javaHome;
    env.PATH = `${join(ctx.javaHome, 'bin')}${WIN ? ';' : ':'}${process.env.PATH}`;
  }

  const child = spawnBackground(WIN ? `"${mvnw}"` : mvnw, ['spring-boot:run'], {
    cwd: BE_ROOT,
    env,
    logFile: BE_LOG,
    onSpawnError: (err) =>
      die(
        `Could not start the Maven wrapper: ${err.message}`,
        'Check that ./mvnw exists and is executable (chmod +x mvnw).',
      ),
  });
  writePids({ backend: child.pid });
  say(dim(`      log: ${BE_LOG}`));

  // Generous budget: a first run downloads the Maven dependencies (~200 MB),
  // compiles ~500 classes and then boots Spring. 15 minutes covers a slow
  // connection; later runs are healthy in well under a minute.
  const healthy = await waitFor(
    'the backend',
    async () => (await httpsCall(healthUrl())).status === 200,
    {
      attempts: 300,
      everyMs: 3000,
      onTick: (i) => {
        if (i % 10 !== 0) return;
        say(dim(`      …${i * 3}s — ${backendActivity()}`));
      },
    },
  );
  if (!healthy) {
    fail('The backend did not become healthy.');
    say(dim(tail(BE_LOG, 30)));
    die('Backend startup failed.', `Full output: ${BE_LOG}`);
  }
  ok(`backend healthy on https://localhost:${ports.be}/api`);
}

async function stepVerifyWorld(ctx) {
  heading('Signing in as the demo users', 'test sessions minted locally — no Firebase, no passwords');

  const sessions = {};
  for (const actor of SEEDED_ACTORS) {
    const res = await httpsCall(api('/test/auth/mock-session'), {
      method: 'POST',
      body: { email: actor.email, role: actor.role },
    });
    if (res.status !== 200) {
      fail(`could not mint a session for ${actor.email} (HTTP ${res.status})`);
      say(dim(`      ${res.body.slice(0, 200)}`));
      die('The dev-lite test-session endpoint is not answering as expected.', 'Is the backend really on the dev-lite profile?');
    }
    const cookie = (res.headers['set-cookie'] || []).map((s) => s.split(';')[0]).join('; ');
    sessions[actor.role] = cookie;
    ok(`${actor.email} (${actor.role})`);
  }
  ctx.sessions = sessions;

  const me = await httpsCall(api('/users/me'), { cookies: sessions.COMPANY });
  if (me.status !== 200) die(`Session check failed: /users/me returned ${me.status}.`);

  const campaigns = await httpsCall(
    api('/partnership-opportunity/paged?page=0&size=5&active=true'),
    { cookies: sessions.COMPANY },
  );
  let count = '?';
  try {
    count = JSON.parse(campaigns.body).totalElements ?? '?';
  } catch {
    /* leave as ? */
  }
  if (campaigns.status !== 200) warn(`campaign list returned HTTP ${campaigns.status} — the UI may look empty`);
  else ok(`the seeded world is there — ${count} campaigns`);
}

async function stepFrontend(ctx) {
  if (NO_FE || ctx.skipFe) return;
  heading('Starting the frontend', `Angular dev-server on https://localhost:${ports.fe}, proxying /api to the backend`);

  const feRoot = [
    process.env.FE_ROOT,
    join(BE_ROOT, '..', 'checkitout-frontend'),
    join(BE_ROOT, '..', 'checkItOut-fe-greenfield'),
  ]
    .filter(Boolean)
    .map((p) => resolve(p))
    .find((p) => existsSync(join(p, 'angular.json')));

  if (!feRoot) {
    warn('Frontend repository not found next to this one.');
    say(dim('      Clone it as a sibling directory, or re-run with FE_ROOT=/path/to/frontend'));
    say(dim('      The backend and its API are fully usable without it.'));
    return;
  }
  ctx.feRoot = feRoot;

  if (await portInUse(ports.fe)) {
    ok(`something is already serving ${ports.fe} — reusing it`);
    ctx.feUrl = `https://localhost:${ports.fe}`;
    return;
  }

  if (!existsSync(join(feRoot, 'node_modules'))) {
    say(dim('      Installing frontend dependencies (npm ci, a few minutes)…'));
    const install = run('npm', ['ci'], { cwd: feRoot, stdio: 'inherit' });
    if (install.status !== 0) die('npm ci failed in the frontend repository.', 'Run it by hand there to see the error.');
    ok('dependencies installed');
  }

  // Re-check here, not only at the ports step: a first install takes minutes,
  // and on a shared machine something can claim the port in between. The
  // dev-server's own reaction is to exit with a stack trace, which reads like
  // a broken project rather than a busy port.
  if (await portInUse(ports.fe)) {
    const alt = ALT_PORTS.fe;
    warn(`port ${ports.fe} was taken while the dependencies installed`);
    if (await portInUse(alt)) {
      warn(`the spare frontend port ${alt} is busy too — start the frontend yourself with DEV_LITE_FE_PORT=<free port>`);
      return;
    }
    ports.fe = alt;
    ok(`serving the frontend on ${alt} instead`);
  }

  // The dev-server proxy follows the backend wherever the wizard put it.
  const feEnv = { ...process.env, BE_PROXY_TARGET: `https://localhost:${ports.be}` };
  const child = spawnBackground(
    'npm',
    ports.fe === DEFAULT_PORTS.fe ? ['run', 'start'] : ['run', 'start', '--', '--port', String(ports.fe)],
    { cwd: feRoot, env: feEnv, logFile: FE_LOG },
  );
  writePids({ frontend: child.pid });
  say(dim(`      log: ${FE_LOG}`));

  // Stop waiting the moment the dev-server dies: without this, an immediate
  // exit still costs three minutes of silence before anyone sees why.
  let exited = false;
  child.on('exit', (code) => {
    exited = true;
    log(`  frontend dev-server exited with code ${code}`);
  });
  const up = await waitFor('the frontend', async () => exited || (await portInUse(ports.fe)), {
    attempts: 60,
    everyMs: 3000,
  });
  if (!up || exited) {
    fail(exited ? 'The frontend dev-server exited during startup.' : 'The frontend dev-server did not come up.');
    say(dim(tail(FE_LOG, 20)));
    warn('Continuing — the backend is still usable.');
    return; // ctx.feUrl stays unset: the closing banner must not point at a dead URL
  }
  ctx.feUrl = `https://localhost:${ports.fe}`;
  ok(`frontend serving ${ctx.feUrl}`);
}

function stepFinish(ctx) {
  heading('Ready', 'everything below runs on your machine only');
  say('');
  // Entry point is whatever actually answered — never merely what was attempted.
  const entry = ctx.feUrl || `https://localhost:${ports.be}/api/swagger-ui/index.html`;
  say(`      ${bold('Open')}          ${cyan(entry)}`);
  if (!ctx.feUrl && ctx.feRoot) {
    say(dim(`                    (the frontend did not start — see ${FE_LOG})`));
  }
  if (ctx.feUrl) {
    say(dim('                    (self-signed certificate — accept the browser warning once)'));
  }
  say('');
  say(`      ${bold('Sign in as')}`);
  for (const actor of SEEDED_ACTORS) {
    say(`        · ${actor.email.padEnd(26)} ${dim(actor.label)}`);
  }
  say(dim('        There are no passwords in dev-lite. Mint a session for any of them with:'));
  say(dim(`        curl -k -X POST ${api('/test/auth/mock-session')} \\`));
  say(dim(`             -H 'Content-Type: application/json' -d '{"email":"company@checkitout.app","role":"COMPANY"}'`));
  say(dim('        In the browser: run the same fetch() from the app origin (docs/DEV-LITE.md).'));
  say('');
  say(`      ${bold('Mailbox')}       ${cyan(api('/test/email'))}`);
  say(dim('                    Every e-mail the app sends (verification links, step-up codes,'));
  say(dim('                    ticket notifications) lands in an in-memory inbox and is readable there.'));
  say('');
  say(`      ${bold('Uploads')}       stored under ${join(BE_ROOT, 'dev-lite-storage')}`);
  say(`      ${bold('Flags')}         docs/DEV-LITE.md — what is real, what is simulated, what to switch on`);
  say('');
  say(`      ${bold('Stop')}          node tools/dev-lite.mjs --stop`);
  say('');
}

/** Whatever holds this port, kill it — the recorded pid is only the launcher. */
function killListener(port, label) {
  if (WIN) {
    const r = run('netstat', ['-ano', '|', 'findstr', `:${port}`]);
    const pids = new Set(
      (r.stdout || '')
        .split('\n')
        .filter((line) => /LISTENING/i.test(line))
        .map((line) => line.trim().split(/\s+/).pop())
        .filter((pid) => pid && pid !== '0'),
    );
    for (const pid of pids) run('taskkill', ['/PID', pid, '/T', '/F']);
    return pids.size > 0 ? label : null;
  }
  const r = run('sh', ['-c', `lsof -ti tcp:${port} 2>/dev/null || true`]);
  const pids = (r.stdout || '').split('\n').map((s) => s.trim()).filter(Boolean);
  for (const pid of pids) run('kill', ['-TERM', pid]);
  return pids.length > 0 ? label : null;
}

/**
 * Proof that the backend on this port is a dev-lite one: the placeholder
 * endpoint is registered under the simulator profile and nowhere else, so a
 * 200 here cannot come from a production build or an unrelated Spring app.
 */
async function backendIsOurs() {
  const r = await httpsCall(`https://localhost:${ports.be}/api/dev-lite/placeholder/stop-probe`);
  return r.status === 200 && String(r.headers['content-type'] || '').includes('svg');
}

async function stop() {
  const state = readState();
  const pids = state.pids;
  // Ports come from the run being stopped. A stack that moved to the spare set
  // is otherwise invisible to --stop, which would then go after the defaults.
  if (state.ports) Object.assign(ports, state.ports);
  say(bold('Stopping dev-lite…'));

  // Both ownership questions must be answered while the stack is still up.
  const beIsOurs = 'backend' in pids ? await backendIsOurs() : false;
  const feLauncherAlive = alive(pids.frontend);

  for (const [name, pid] of Object.entries(pids)) {
    if (alive(pid)) {
      killTree(pid);
      ok(`${name} (pid ${pid})`);
    }
  }
  // The backend runs as a grandchild of the Maven wrapper, so killing the
  // launcher can leave the JVM holding the port. Finish the job by port — but
  // only after proving the listener is ours. pids.json can be stale, and a
  // recorded role plus a matching number is not evidence that the process
  // answering now is the one this wizard started.
  if ('backend' in pids) {
    if (beIsOurs) {
      if (killListener(ports.be, 'backend')) ok(`backend listening on ${ports.be}`);
    } else if (await portInUse(ports.be)) {
      warn(`port ${ports.be} is held by something that is not a dev-lite backend — left alone`);
    }
  }
  if ('frontend' in pids) {
    // A dev-server has no such fingerprint, so the weaker proof has to do: only
    // clean up the port when the launcher recorded here was still running.
    if (feLauncherAlive) {
      if (killListener(ports.fe, 'frontend')) ok(`frontend listening on ${ports.fe}`);
    } else if (await portInUse(ports.fe)) {
      warn(`port ${ports.fe} is in use, but the dev-server this wizard started is already gone — left alone`);
    }
  }
  // Same rule for the databases: stop them only if this wizard started them.
  // Adopting a PostgreSQL that was already running does not make it ours to
  // shut down — something else on the machine is probably still using it.
  const compose = dockerComposeCmd();
  if (compose && state.startedInfra !== false) {
    const [cmd, base] = compose;
    run(cmd, [...base, ...composeArgs(), 'stop', 'postgres', 'redis'], { cwd: BE_ROOT });
    ok('postgres + redis stopped (data volumes kept)');
  } else if (compose) {
    warn('postgres + redis were already running before the wizard started — left alone');
    say(dim(`      Stop them yourself with: docker compose -f docker-compose-dev-redis.yml stop postgres redis`));
  }
  try {
    rmSync(PID_FILE);
  } catch {
    /* nothing to clean */
  }
  say(dim('Databases keep their data. To wipe them: docker compose -f docker-compose-dev-redis.yml down -v'));
}

async function status() {
  say(bold('dev-lite status'));
  const state = readState();
  const pids = state.pids;
  // Report on the ports the recorded run actually used, not today's defaults.
  if (state.ports) Object.assign(ports, state.ports);
  say(`  backend process   ${alive(pids.backend) ? green('running') : dim('not started by the wizard')}`);
  say(`  frontend process  ${alive(pids.frontend) ? green('running') : dim('not started by the wizard')}`);
  for (const [port, label] of [
    [ports.pg, 'PostgreSQL'],
    [ports.redis, 'Redis'],
    [ports.be, 'backend'],
    [ports.smtp, 'mailbox'],
    [ports.fe, 'frontend'],
  ]) {
    say(`  port ${String(port).padEnd(5)}        ${(await portInUse(port)) ? green('listening') : dim('free')}`);
  }
  const health = await httpsCall(healthUrl());
  say(`  backend health    ${health.status === 200 ? green('UP') : dim(`unreachable (${health.status})`)}`);
}

// ── main ───────────────────────────────────────────────────────────────────
const ctx = {};
try {
  if (args.has('--stop')) {
    await stop();
  } else if (args.has('--status')) {
    await status();
  } else {
    // prereqs, ports, databases, backend, demo users [, frontend], ready
    stepTotal = NO_FE ? 6 : 7;
    await stepIntro();
    await stepPrereqs(ctx);
    await stepPorts(ctx);
    await stepInfra(ctx);
    await stepBackend(ctx);
    await stepVerifyWorld(ctx);
    await stepFrontend(ctx);
    stepFinish(ctx);
  }
  logStream.end();
} catch (err) {
  // die() already printed and is flushing the log on its way out; anything
  // else is a bug in the wizard and should say so rather than vanish.
  if (!(err instanceof Fatal)) {
    fail(`Unexpected wizard error: ${err && err.message}`);
    say(dim(String((err && err.stack) || err)));
    logStream.end(() => process.exit(1));
    setTimeout(() => process.exit(1), 1000).unref();
  }
}
