/* Headless test suite: physics, soft bodies, a full simulated match and the
   LAN transport. Run with `npm test` from the game/ directory. */
'use strict';
const assert = require('assert');
const net = require('net');
const path = require('path');

const Core = require('../renderer/js/core.js');
const Physics = require('../renderer/js/physics.js');
const Soft = require('../renderer/js/softbody.js');
const SimMod = require('../renderer/js/sim.js');
const NetMod = require('../renderer/js/net.js');
const netcore = require('../netcore.js');

const { Body, World } = Physics;
const { Game, K } = SimMod;

let passed = 0, failed = 0;
const tests = [];
const test = (name, fn) => tests.push({ name, fn });
const approx = (a, b, tol, msg) =>
  assert.ok(Math.abs(a - b) <= tol, `${msg || ''} expected ${b}±${tol}, got ${a}`);

/* ── physics ─────────────────────────────────────────── */
test('a dropped ball settles on a wall without sinking through it', () => {
  const w = new World({});
  w.addWall(0, 500, 1000, 500, 20, {});
  const b = w.add(new Body({ x: 200, y: 100, r: 20, m: 1, e: 0.5, damping: 0 }));
  for (let i = 0; i < 600; i++) { b.applyForce(0, 900 * b.m); w.step(1 / 60); }
  approx(b.y, 460, 1.5, 'resting height');
  approx(b.vy, 0, 1, 'resting velocity');
});

test('restitution controls bounce height', () => {
  const drop = (e) => {
    const w = new World({});
    w.addWall(0, 600, 1200, 600, 20, { e });
    const b = w.add(new Body({ x: 300, y: 200, r: 20, m: 1, e, damping: 0 }));
    let apex = 600;
    let hitFloor = false;
    for (let i = 0; i < 400; i++) {
      b.applyForce(0, 1200 * b.m);
      w.step(1 / 60);
      if (b.vy < 0) hitFloor = true;
      if (hitFloor) apex = Math.min(apex, b.y);
    }
    return 580 - apex;
  };
  const bouncy = drop(0.9), dead = drop(0.05);
  assert.ok(bouncy > dead * 3, `bouncy ${bouncy.toFixed(1)} should clear dead ${dead.toFixed(1)}`);
});

test('head-on collision conserves momentum', () => {
  const w = new World({});
  const a = w.add(new Body({ x: 0, y: 0, r: 20, m: 2, e: 1, mu: 0, damping: 0, angularDamping: 0, vx: 300 }));
  const b = w.add(new Body({ x: 200, y: 0, r: 20, m: 1, e: 1, mu: 0, damping: 0, angularDamping: 0, vx: -100 }));
  const p0 = a.m * a.vx + b.m * b.vx;
  for (let i = 0; i < 120; i++) w.step(1 / 60);
  const p1 = a.m * a.vx + b.m * b.vx;
  approx(p1, p0, Math.abs(p0) * 0.05, 'momentum');
  assert.ok(a.vx < b.vx, 'bodies separated after the hit');
});

test('friction turns spin into rolling', () => {
  const w = new World({});
  w.addWall(0, 600, 2400, 600, 20, { mu: 0.9 });
  const b = w.add(new Body({ x: 300, y: 560, r: 40, m: 2, e: 0.2, mu: 0.8, damping: 0.02, angularDamping: 0.02 }));
  b.av = 12;
  for (let i = 0; i < 180; i++) { b.applyForce(0, 900 * b.m); w.step(1 / 60); }
  assert.ok(b.x > 600, `ball should roll forward, x=${b.x.toFixed(1)}`);
  approx(b.vx, b.av * b.r, Math.abs(b.vx) * 0.15, 'rolling without slipping');
});

test('a stack of balls stays stacked', () => {
  const w = new World({});
  w.addWall(0, 800, 1600, 800, 20, {});
  const col = [];
  for (let i = 0; i < 5; i++) col.push(w.add(new Body({ x: 400, y: 760 - i * 41, r: 20, m: 1, e: 0.02, damping: 0.1 })));
  for (let i = 0; i < 900; i++) { for (const b of col) b.applyForce(0, 900 * b.m); w.step(1 / 60); }
  for (const b of col) assert.ok(Math.abs(b.x - 400) < 60, `stack drifted to ${b.x.toFixed(1)}`);
  assert.ok(col[4].y < col[0].y, 'stack kept its order');
});

test('explosions push outward with distance falloff', () => {
  const w = new World({});
  const near = w.add(new Body({ x: 100, y: 0, r: 15, m: 1, damping: 0 }));
  const far = w.add(new Body({ x: 380, y: 0, r: 15, m: 1, damping: 0 }));
  w.radialImpulse(0, 0, 500, 1000, {});
  assert.ok(near.vx > far.vx && far.vx > 0, `near ${near.vx.toFixed(1)} > far ${far.vx.toFixed(1)} > 0`);
});

test('the grapple rope stops the swing extending past its length', () => {
  const w = new World({});
  const b = w.add(new Body({ x: 0, y: 0, r: 12, m: 1, damping: 0, vx: 900 }));
  w.addRope({ a: b, px: 0, py: 0, max: 200 });
  for (let i = 0; i < 240; i++) w.step(1 / 60);
  assert.ok(Math.hypot(b.x, b.y) < 230, `rope held: ${Math.hypot(b.x, b.y).toFixed(1)}`);
});

test('nothing in the physics world can reach a non-finite state', () => {
  const w = new World({});
  const b = w.add(new Body({ x: 0, y: 0, r: 20, m: 1, damping: 0 }));
  b.vx = 1e12; b.vy = -1e12;
  for (let i = 0; i < 60; i++) w.step(1 / 60);
  assert.ok(isFinite(b.x) && isFinite(b.y) && isFinite(b.vx), 'clamped back to something sane');
});

/* ── soft bodies ─────────────────────────────────────── */
test('a blob holds its volume and squashes against a wall', () => {
  const w = new World({});
  w.addWall(900, 0, 900, 900, 24, {});
  const b = new Soft.Blob(w, { x: 600, y: 400, r: 120, hp: 200 });
  const a0 = b.polygonArea();
  for (let i = 0; i < 420; i++) { b.applyPressure(); b.drive(9000, 0); w.step(1 / 60); }
  const c = b.center();
  const radii = b.particles.map((p) => Math.hypot(p.x - c.x, p.y - c.y));
  assert.ok(b.polygonArea() > a0 * 0.75, 'kept most of its volume');
  assert.ok(Math.max(...radii) - Math.min(...radii) > 4, 'actually deformed against the wall');
  assert.ok(radii.every((r) => isFinite(r)), 'membrane stayed finite');
});

test('a blob knows what is inside it', () => {
  const w = new World({});
  const b = new Soft.Blob(w, { x: 500, y: 500, r: 100, hp: 100 });
  assert.ok(b.contains(500, 500), 'its own centre');
  assert.ok(!b.contains(900, 500), 'a point well outside');
});

/* ── the game ────────────────────────────────────────── */
function botMatch(opts) {
  const g = new Game({ seed: opts.seed || 7 });
  for (let i = 1; i <= (opts.players || 2); i++) g.addPlayer(i, 'BOT' + i);
  const maxTicks = opts.ticks || 60 * 60 * 6;
  for (let i = 0; i < maxTicks && g.phase !== 'victory' && g.phase !== 'defeat'; i++) {
    for (const p of g.players.values()) {
      let tx = null, td = 1e9;
      for (const e of g.enemies.values()) {
        const d = Math.hypot(e.body.x - p.body.x, e.body.y - p.body.y);
        if (d < td) { td = d; tx = e.body; }
      }
      for (const b of g.blobs.values()) {
        const d = Math.hypot(b.nucleus.x - p.body.x, b.nucleus.y - p.body.y);
        if (d < td) { td = d; tx = b.nucleus; }
      }
      const aim = tx ? Math.atan2(tx.y - p.body.y, tx.x - p.body.x) : 0;
      let mx = 0, my = 0;
      if (tx) { const want = td < 380 ? -1 : 1; mx = Math.cos(aim) * want; my = Math.sin(aim) * want; }
      let od = 1e9, og = null;
      for (const o of g.orbs.values()) {
        const d = Math.hypot(o.body.x - p.body.x, o.body.y - p.body.y);
        if (d < od) { od = d; og = o.body; }
      }
      if (og && od < 800) { const a = Math.atan2(og.y - p.body.y, og.x - p.body.x); mx = Math.cos(a); my = Math.sin(a); }
      g.setInput(p.id, { mx, my, aim, fire: 1, dash: p.hp < 55 ? 1 : 0, ult: p.ult > 0 && td < 520 ? 1 : 0 });
    }
    g.step(K.TICK);
  }
  return g;
}

test('a two-player match runs, spawns waves and scores points', () => {
  const g = botMatch({ players: 2, ticks: 60 * 60 * 4 });
  assert.ok(g.wave >= 2, `reached wave ${g.wave}`);
  const stats = g.stats();
  assert.ok(stats[0].score > 0, 'somebody scored');
  assert.ok(stats.some((s) => s.kills > 0), 'spheres were popped');
  assert.ok(g.world.bodies.every((b) => isFinite(b.x) && isFinite(b.y)), 'world stayed finite');
});

test('every guard kind and the blob can be killed', () => {
  const g = new Game({ seed: 11 });
  g.addPlayer(1, 'TESTER');
  for (const kind of ['yellow', 'pink', 'red', 'blue']) {
    const e = g.spawnGuard(kind, 1000, 1000);
    g.damageGuard(e, 10000, 1, e.body.x, e.body.y);
    g.flushBooms();
    assert.ok(!g.enemies.has(e.id), kind + ' died');
  }
  const b = g.spawnBlob(2000, 1000, 130, 200);
  g.damageBlob(b, 10000, 1, 2000, 1000, true);
  assert.ok(!g.blobs.has(b.id), 'blob burst');
  assert.ok(g.orbs.size > 0, 'angelos dropped');
});

test('a chain of exploding Wraths terminates instead of recursing', () => {
  const g = new Game({ seed: 3 });
  g.addPlayer(1, 'TESTER');
  const line = [];
  for (let i = 0; i < 12; i++) line.push(g.spawnGuard('red', 1200 + i * 120, 1100));
  g.damageGuard(line[0], 10000, 1, line[0].body.x, line[0].body.y);
  for (let i = 0; i < 120; i++) g.step(K.TICK);   // must not blow the stack
  assert.ok(g.enemies.size < line.length, 'the chain reaction took some of them out');
});

test('collecting an angelos heals, scores and charges the shockwave', () => {
  const g = new Game({ seed: 5 });
  const p = g.addPlayer(1, 'TESTER');
  p.hp = 50;
  const before = p.angelos;
  for (let i = 0; i < K.ULT_COST; i++) {
    const o = g.spawnOrb(p.body.x + 30, p.body.y);
    o.body.x = p.body.x + 20; o.body.y = p.body.y;
    for (let t = 0; t < 30 && g.orbs.has(o.id); t++) g.step(K.TICK);
  }
  assert.ok(p.angelos > before, 'picked angelos up');
  assert.ok(p.hp > 50, 'healed');
  assert.ok(p.ult >= 1, 'shockwave charged');
});

test('a downed rebel respawns', () => {
  const g = new Game({ seed: 8 });
  const p = g.addPlayer(1, 'TESTER');
  g.hurtPlayer(p, 999, 0, 0);
  assert.ok(!p.alive, 'went down');
  for (let i = 0; i < 60 * (K.RESPAWN + 1); i++) g.step(K.TICK);
  assert.ok(p.alive && p.hp > 0, 'came back');
});

test('the whole team going down mid-fight ends the war', () => {
  const g = new Game({ seed: 4 });
  const a = g.addPlayer(1, 'A'), b = g.addPlayer(2, 'B');
  g.startWave(1);
  g.hurtPlayer(a, 999, 0, 0);
  g.hurtPlayer(b, 999, 0, 0);
  g.step(K.TICK);
  assert.strictEqual(g.phase, 'defeat');
});

test('a wipe between waves is survivable — everyone just respawns', () => {
  const g = new Game({ seed: 4 });
  const a = g.addPlayer(1, 'A');
  g.phase = 'intermission'; g.waveTimer = 30;
  g.hurtPlayer(a, 999, 0, 0);
  for (let i = 0; i < 60 * (K.RESPAWN + 1); i++) g.step(K.TICK);
  assert.strictEqual(g.phase, 'intermission');
  assert.ok(a.alive, 'back on their feet');
});

test('snapshots are compact and complete', () => {
  const g = botMatch({ players: 4, ticks: 60 * 40 });
  const snap = g.snapshot(0);
  const bytes = JSON.stringify(snap).length;
  assert.ok(bytes < 60000, `snapshot is ${bytes} bytes`);
  assert.strictEqual(snap.P.length, 4, 'all four rebels in the snapshot');
  assert.ok(snap.E.length >= 0 && Array.isArray(snap.L), 'enemies and blobs present');
  for (const L of snap.L) assert.ok(L[7].length >= 12, 'blob outline travels with it');
});

test('effect cursors let the renderer and the network read the same log', () => {
  const g = new Game({ seed: 12 });
  g.addPlayer(1, 'A');
  for (let i = 0; i < 120; i++) { g.setInput(1, { mx: 1, my: 0, aim: 0, fire: 1 }); g.step(K.TICK); }
  const a = g.snapshot(0);
  const b = g.snapshot(a.fs);
  assert.ok(a.F.length > 0, 'first reader saw effects');
  assert.strictEqual(b.F.length, 0, 'second read from the same cursor sees nothing new');
  const c = g.snapshot(0);
  assert.ok(c.F.length > 0, 'a fresh cursor still sees history');
});

/* ── client view ─────────────────────────────────────── */
test('client interpolation lands between two snapshots', () => {
  const c = new NetMod.Client();
  const row = (x, y) => ({ k: 1, P: [[1, x, y, 0, 0, 0, 100, 1, 0, 0, 0, 0, 0, 'A', '#fff', 0, 0]], E: [], B: [], O: [], L: [] });
  c.push(row(0, 0));
  c.now = 0.2;
  c.push(row(200, 100));
  const v = c.sample(0.1);            // now 0.3, target 0.18 → 90% of the way
  assert.ok(v.P[0][1] > 100 && v.P[0][1] <= 200, `interpolated x=${v.P[0][1]}`);
});

test('client prediction converges on the authoritative position', () => {
  const c = new NetMod.Client();
  c.reconcile(100, 100, 0, 0);
  for (let i = 0; i < 60; i++) c.predict({ mx: 1, my: 0 }, 1 / 60, K);
  assert.ok(c.pred.x > 100, 'predicted forward');
  for (let i = 0; i < 40; i++) c.reconcile(100, 100, 0, 0);
  assert.ok(Math.abs(c.pred.x - 100) < 5, `snapped back to the host: ${c.pred.x.toFixed(1)}`);
});

/* ── transport ───────────────────────────────────────── */
test('TCP framing survives split and merged packets', async () => {
  const got = [];
  const server = net.createServer((sock) => {
    netcore.attach(sock, (m) => got.push(m));
  });
  await new Promise((r) => server.listen(0, '127.0.0.1', r));
  const port = server.address().port;
  const client = net.connect({ host: '127.0.0.1', port });
  await new Promise((r) => client.once('connect', r));
  const payload = JSON.stringify({ t: 'in', mx: 1 }) + '\n' + JSON.stringify({ t: 'in', mx: -1 }) + '\n';
  client.write(payload.slice(0, 9));                 // deliberately mid-frame
  await new Promise((r) => setTimeout(r, 40));
  client.write(payload.slice(9));
  await new Promise((r) => setTimeout(r, 120));
  client.destroy(); server.close();
  assert.strictEqual(got.length, 2, 'both frames arrived');
  assert.strictEqual(got[0].mx, 1);
  assert.strictEqual(got[1].mx, -1);
});

test('LAN discovery answers a probe', async () => {
  const PORT = 47899;
  const responder = netcore.startResponder(() => ({ name: 'TEST WAR', port: 47861, players: 3 }), PORT);
  await new Promise((r) => setTimeout(r, 120));
  const found = await netcore.discover(500, PORT, ['127.0.0.1']);
  responder.close();
  assert.ok(found.some((f) => f.name === 'TEST WAR' && f.players === 3), `discovered: ${JSON.stringify(found)}`);
});

test('a host and a client play the same match over a socket', async () => {
  const g = new Game({ seed: 21 });
  g.addPlayer(1, 'HOST');
  const peerPlayers = new Map();
  const server = net.createServer((sock) => {
    netcore.attach(sock, (m) => {
      if (m.t === 'hello') {
        const pid = 101;
        peerPlayers.set(sock, pid);
        g.addPlayer(pid, m.name);
        netcore.write(sock, { t: 'welcome', id: pid, arena: g.arena() });
      } else if (m.t === 'in') {
        g.setInput(peerPlayers.get(sock), m);
      }
    });
    sock._isPeer = true;
    server._peer = sock;
  });
  await new Promise((r) => server.listen(0, '127.0.0.1', r));
  const port = server.address().port;

  const view = new NetMod.Client();
  let selfId = null;
  const client = net.connect({ host: '127.0.0.1', port });
  await new Promise((r) => client.once('connect', r));
  netcore.attach(client, (m) => {
    if (m.t === 'welcome') selfId = m.id;
    else if (m.t === 'snap') view.push(m.s);
  });
  netcore.write(client, { t: 'hello', name: 'REMOTE' });
  await new Promise((r) => setTimeout(r, 120));
  assert.strictEqual(selfId, 101, 'client was assigned a rebel');

  let cursor = 0;
  for (let i = 0; i < 180; i++) {
    netcore.write(client, { t: 'in', mx: 1, my: 0, aim: 0, fire: 1 });
    g.setInput(1, { mx: -1, my: 0, aim: Math.PI, fire: 1 });
    g.step(K.TICK);
    if (i % 2 === 0 && server._peer) {
      const snap = g.snapshot(cursor);
      cursor = snap.fs;
      netcore.write(server._peer, { t: 'snap', s: snap });
    }
  }
  await new Promise((r) => setTimeout(r, 200));
  client.destroy(); server.close();

  assert.ok(view.buf.length > 10, `client received ${view.buf.length} snapshots`);
  const last = view.latest;
  assert.strictEqual(last.P.length, 2, 'both rebels are in the host view');
  const remote = last.P.find((p) => p[0] === 101);
  assert.ok(remote && remote[1] > 0, 'the remote rebel moved under its own input');
});

/* ── runner ──────────────────────────────────────────── */
(async () => {
  for (const t of tests) {
    try {
      await t.fn();
      passed++;
      console.log('  \x1b[32m✓\x1b[0m ' + t.name);
    } catch (e) {
      failed++;
      console.log('  \x1b[31m✗\x1b[0m ' + t.name + '\n      ' + (e && e.message));
    }
  }
  console.log(`\n${passed} passed, ${failed} failed`);
  process.exit(failed ? 1 : 0);
})();
