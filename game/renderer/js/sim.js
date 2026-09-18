/* WAR AGAINST HENRY ANGELOS — the authoritative simulation.

   Runs on the host (and on a solo machine, which is just a host with no
   clients). Clients never run this: they render interpolated snapshots.
   It is pure logic — no DOM, no canvas — so the headless tests in
   game/test can play entire matches at a few thousand ticks a second. */
(function (global) {
  'use strict';

  function build(Core, Physics, Soft) {
    const { clamp, len, dist, TAU, rng } = Core;
    const { Body, World, CAT, ALL } = Physics;
    const { Blob } = Soft;

    const K = {
      TICK: 1 / 60,
      ARENA_W: 2800, ARENA_H: 1800,
      WALL_R: 40,

      PLAYER_R: 26, PLAYER_M: 1.5, PLAYER_HP: 100,
      THRUST: 3000, PLAYER_DAMP: 2.4,
      DASH_IMPULSE: 1500, DASH_CD: 1.5, DASH_IFRAMES: 0.22,
      FIRE_CD: 0.14, BOLT_SPEED: 1650, BOLT_R: 8, BOLT_M: 0.2, BOLT_DMG: 13, BOLT_LIFE: 1.5,
      RESPAWN: 5,
      REGEN_DELAY: 6, REGEN_RATE: 5,
      ULT_COST: 10, ULT_MAX: 3, ULT_R: 560, ULT_POWER: 2600, ULT_DMG: 60,
      GRAPPLE_RANGE: 620, GRAPPLE_PULL: 2400,

      ORB_LIFE: 30, ORB_MAGNET: 210, ORB_HEAL: 4,
      INTERMISSION: 6,
    };

    /* Henry's guard — the four spheres from the photograph. */
    const GUARDS = {
      yellow: { r: 25, m: 0.9,  hp: 30,  e: 0.94, mu: 0.15, dmg: 9,  speed: 2400, drop: 1, score: 10,
                damping: 0.9,  name: 'SPARK' },
      pink:   { r: 34, m: 2.0,  hp: 62,  e: 0.55, mu: 0.3,  dmg: 12, speed: 1500, drop: 2, score: 20,
                damping: 1.6,  name: 'CHARMER' },
      red:    { r: 46, m: 5.5,  hp: 125, e: 0.6,  mu: 0.35, dmg: 24, speed: 1900, drop: 3, score: 35,
                damping: 1.2,  name: 'WRATH' },
      blue:   { r: 55, m: 9.0,  hp: 210, e: 0.35, mu: 0.5,  dmg: 18, speed: 1500, drop: 4, score: 55,
                damping: 1.5,  name: 'WARDEN' },
    };

    const HUES = ['#4dffd2', '#ffd24d', '#7cff4d', '#4db8ff', '#ff7c4d', '#e04dff', '#ffffff', '#ff4d6e'];

    let uid = 1000;
    const newId = () => ++uid;

    class Game {
      constructor(o) {
        o = o || {};
        this.seed = o.seed || 1337;
        this.tick = 0;
        this.time = 0;
        this.players = new Map();
        this.enemies = new Map();
        this.bolts = new Map();
        this.orbs = new Map();
        this.blobs = new Map();
        this.fx = [];
        this.fxSeq = 0;
        this.phase = 'fight';
        this.wave = 0;
        this.waveTimer = 3;
        this.collected = 0;
        this.needed = 0;
        this.banner = '';
        this.endless = false;
        this.contactCd = new Map();
        this.pendingBolt = [];
        this.pendingBoom = [];
        this.world = null;
        this.reset(this.seed);
      }

      /* ── setup ─────────────────────────────────────────────── */
      reset(seed) {
        this.seed = seed || this.seed;
        this.rand = rng(this.seed);
        this.world = new World({ cell: 120, iterations: 8, posIterations: 3, substeps: 2 });
        this.world.onContact = (a, b, j, nx, ny, px, py) => this.onContact(a, b, j, nx, ny, px, py);
        this.enemies.clear(); this.bolts.clear(); this.orbs.clear(); this.blobs.clear();
        this.fx.length = 0; this.contactCd.clear();
        this.tick = 0; this.time = 0;
        this.wave = 0; this.waveTimer = 4; this.phase = 'intermission';
        this.collected = 0; this.needed = 0;
        this.banner = 'THE RULE OF HENRY ANGELOS BEGINS';
        this.buildArena();
        for (const p of this.players.values()) this.spawnPlayer(p, true);
      }

      buildArena() {
        const w = this.world, W = K.ARENA_W, H = K.ARENA_H, r = K.WALL_R;
        this.pillars = [];
        this.walls = [];
        const wall = (x1, y1, x2, y2, rr, o) => { this.walls.push([x1, y1, x2, y2, rr]); return w.addWall(x1, y1, x2, y2, rr, o); };
        wall(-r, -r, W + r, -r, r, { e: 0.55, mu: 0.25 });
        wall(-r, H + r, W + r, H + r, r, { e: 0.55, mu: 0.25 });
        wall(-r, -r, -r, H + r, r, { e: 0.55, mu: 0.25 });
        wall(W + r, -r, W + r, H + r, r, { e: 0.55, mu: 0.25 });

        // Verity pylons: heavy static bumpers to carom the spheres off.
        const rand = this.rand;
        const spots = [
          [W * 0.24, H * 0.26], [W * 0.76, H * 0.26],
          [W * 0.24, H * 0.74], [W * 0.76, H * 0.74],
          [W * 0.5, H * 0.5],
        ];
        for (const [x, y] of spots) {
          const rad = 60 + rand() * 46;
          const b = new Body({ x, y, r: rad, isStatic: true, e: 0.92, mu: 0.2, cat: CAT.WALL });
          w.add(b);
          this.pillars.push({ x, y, r: rad });
        }
        // two diagonal girders to break line of sight
        wall(W * 0.38, H * 0.08, W * 0.38, H * 0.30, 26, { e: 0.7 });
        wall(W * 0.62, H * 0.70, W * 0.62, H * 0.92, 26, { e: 0.7 });
      }

      /* ── players ───────────────────────────────────────────── */
      addPlayer(id, name) {
        if (this.players.has(id)) return this.players.get(id);
        const idx = this.players.size;
        const p = {
          id, name: (name || 'REBEL').slice(0, 14).toUpperCase(),
          hue: HUES[idx % HUES.length],
          hp: K.PLAYER_HP, alive: true, respawn: 0,
          score: 0, angelos: 0, ult: 0, ultCharge: 0,
          fireCd: 0, dashCd: 0, invuln: 0, hurtTimer: 0,
          aim: 0, input: { mx: 0, my: 0, aim: 0, fire: 0, dash: 0, ult: 0, grap: 0 },
          rope: null, body: null, kills: 0,
        };
        this.players.set(id, p);
        this.spawnPlayer(p, true);
        return p;
      }

      removePlayer(id) {
        const p = this.players.get(id);
        if (!p) return;
        if (p.rope) { this.world.removeRope(p.rope); p.rope = null; }
        if (p.body) this.world.remove(p.body);
        this.players.delete(id);
      }

      spawnPlayer(p, fresh) {
        const spot = this.safeSpot();
        if (!p.body) {
          p.body = this.world.add(new Body({
            x: spot.x, y: spot.y, r: K.PLAYER_R, m: K.PLAYER_M,
            e: 0.45, mu: 0.25, damping: K.PLAYER_DAMP, angularDamping: 2.2,
            cat: CAT.PLAYER, mask: ALL & ~CAT.BOLT, owner: p,
          }));
        } else {
          p.body.x = spot.x; p.body.y = spot.y; p.body.vx = 0; p.body.vy = 0; p.body.av = 0;
        }
        p.alive = true;
        p.hp = K.PLAYER_HP;
        p.invuln = 1.2;
        p.respawn = 0;
        if (fresh) { p.ult = 0; p.ultCharge = 0; }
      }

      safeSpot() {
        const rand = this.rand;
        for (let i = 0; i < 60; i++) {
          const x = 240 + rand() * (K.ARENA_W - 480);
          const y = 240 + rand() * (K.ARENA_H - 480);
          let ok = true;
          for (const e of this.enemies.values()) if (dist(x, y, e.body.x, e.body.y) < 460) { ok = false; break; }
          if (ok) for (const b of this.blobs.values()) {
            const c = b.center();
            if (dist(x, y, c.x, c.y) < b.r0 + 320) { ok = false; break; }
          }
          if (ok) for (const p of this.pillars) if (dist(x, y, p.x, p.y) < p.r + 90) { ok = false; break; }
          if (ok) return { x, y };
        }
        return { x: K.ARENA_W * 0.5, y: K.ARENA_H * 0.85 };
      }

      setInput(id, inp) {
        const p = this.players.get(id);
        if (!p) return;
        p.input.mx = clamp(inp.mx || 0, -1, 1);
        p.input.my = clamp(inp.my || 0, -1, 1);
        p.input.aim = inp.aim || 0;
        p.input.fire = inp.fire ? 1 : 0;
        p.input.dash = inp.dash ? 1 : 0;
        p.input.ult = inp.ult ? 1 : 0;
        p.input.grap = inp.grap ? 1 : 0;
      }

      /* ── waves ─────────────────────────────────────────────── */
      waveTable(n) {
        // n is 1-based. Every fourth wave is a blob; wave 10 is Henry himself.
        const heads = Math.max(1, this.players.size);
        const s = (v) => Math.round(v * (0.75 + 0.25 * heads));
        if (n === 10) return { henry: true, yellow: s(4), pink: s(2), blue: s(1) };
        if (n % 4 === 0) return { blobs: n >= 8 ? 2 : 1, yellow: s(3 + n), pink: s(1 + n * 0.3) };
        return {
          yellow: s(4 + n * 1.6),
          pink: s(n * 0.8),
          red: s(Math.max(0, n - 1) * 0.5),
          blue: s(Math.max(0, n - 2) * 0.32),
        };
      }

      startWave(n) {
        this.wave = n;
        this.phase = 'fight';
        const t = this.waveTable(n);
        let drops = 0;
        const spawnEdge = () => {
          const rand = this.rand;
          const side = rand.int(0, 3);
          const m = 120;
          if (side === 0) return { x: rand.range(m, K.ARENA_W - m), y: m };
          if (side === 1) return { x: K.ARENA_W - m, y: rand.range(m, K.ARENA_H - m) };
          if (side === 2) return { x: rand.range(m, K.ARENA_W - m), y: K.ARENA_H - m };
          return { x: m, y: rand.range(m, K.ARENA_H - m) };
        };
        for (const kind of ['yellow', 'pink', 'red', 'blue']) {
          const count = t[kind] || 0;
          for (let i = 0; i < count; i++) {
            const at = spawnEdge();
            this.spawnGuard(kind, at.x, at.y);
            drops += GUARDS[kind].drop;
          }
        }
        for (let i = 0; i < (t.blobs || 0); i++) {
          const at = spawnEdge();
          this.spawnBlob(at.x, at.y, 135 + this.rand() * 40, 300 + n * 30);
          drops += 8;
        }
        if (t.henry) {
          this.spawnBlob(K.ARENA_W / 2, K.ARENA_H / 2, 230, 1100, 'henry');
          drops += 30;
        }
        this.needed = Math.max(4, Math.round(drops * 0.6));
        this.collected = 0;
        this.banner = t.henry ? 'HENRY ANGELOS DESCENDS' : 'WAVE ' + n + ' — COLLECT THE ANGELOS';
        this.pushFx({ t: 'wave', n });
      }

      spawnGuard(kind, x, y) {
        const g = GUARDS[kind];
        const e = {
          id: newId(), kind, hp: g.hp, maxHp: g.hp, state: 0, timer: this.rand.range(0, 2),
          shield: kind === 'blue' ? 70 : 0, maxShield: kind === 'blue' ? 70 : 0,
          target: null, wobble: this.rand.angle(),
        };
        e.body = this.world.add(new Body({
          x, y, r: g.r, m: g.m, e: g.e, mu: g.mu,
          damping: g.damping, angularDamping: 0.9,
          cat: CAT.ENEMY, mask: ALL, owner: e,
        }));
        e.body.av = this.rand.range(-4, 4);
        this.enemies.set(e.id, e);
        this.pushFx({ t: 'spawn', x, y, k: kind });
        return e;
      }

      spawnBlob(x, y, r, hp, kind) {
        const b = new Blob(this.world, {
          x, y, r, hp, kind: kind || 'blob',
          stiff: kind === 'henry' ? 2300 : 1500,
          pressure: kind === 'henry' ? 7 : 5.2,
        });
        b.id = newId();
        b.timer = 0; b.pulse = 0; b.spawnTimer = 6;
        this.blobs.set(b.id, b);
        this.pushFx({ t: 'blobspawn', x, y, r });
        return b;
      }

      /* ── per-tick ──────────────────────────────────────────── */
      step(dt) {
        dt = dt || K.TICK;
        this.tick++;
        this.time += dt;

        this.stepPlayers(dt);
        this.stepGuards(dt);
        this.stepBlobs(dt);
        this.stepBolts(dt);
        this.stepOrbs(dt);

        this.world.step(dt);

        this.flushBolts();
        this.flushBooms();
        this.stepPhase(dt);
      }

      stepPlayers(dt) {
        for (const p of this.players.values()) {
          if (!p.alive) {
            p.respawn -= dt;
            if (p.respawn <= 0) { this.spawnPlayer(p); this.pushFx({ t: 'respawn', x: p.body.x, y: p.body.y }); }
            continue;
          }
          const b = p.body, i = p.input;
          p.aim = i.aim;
          p.fireCd -= dt; p.dashCd -= dt; p.invuln -= dt; p.hurtTimer -= dt;

          const m = len(i.mx, i.my);
          if (m > 0.02) {
            const s = Math.min(1, m);
            b.applyForce((i.mx / m) * K.THRUST * s, (i.my / m) * K.THRUST * s);
            b.av += (i.mx / m) * 0.6 * dt;   // lean into the turn
          }

          if (i.dash && p.dashCd <= 0) {
            let dx = i.mx, dy = i.my;
            if (len(dx, dy) < 0.05) { dx = Math.cos(p.aim); dy = Math.sin(p.aim); }
            const l = Math.max(1e-4, len(dx, dy));
            b.applyImpulse((dx / l) * K.DASH_IMPULSE, (dy / l) * K.DASH_IMPULSE);
            p.dashCd = K.DASH_CD;
            p.invuln = Math.max(p.invuln, K.DASH_IFRAMES);
            this.pushFx({ t: 'dash', x: b.x, y: b.y, a: Math.atan2(dy, dx), c: p.hue });
          }

          if (i.fire && p.fireCd <= 0) {
            this.fireBolt(p);
            p.fireCd = K.FIRE_CD;
          }

          if (i.ult && p.ult >= 1) {
            p.ult--;
            this.shockwave(b.x, b.y, K.ULT_R, K.ULT_POWER, K.ULT_DMG, p);
            this.pushFx({ t: 'ult', x: b.x, y: b.y, c: p.hue, r: K.ULT_R });
          }

          // grapple: a real rope constraint onto the nearest heavy thing
          if (i.grap) {
            if (!p.rope) {
              const tgt = this.grappleTarget(p);
              if (tgt) {
                p.rope = this.world.addRope({ a: b, b: tgt.body || null, px: tgt.x, py: tgt.y, max: Math.max(120, tgt.d * 0.92) });
                p.ropeTo = tgt;
                this.pushFx({ t: 'grap', x: b.x, y: b.y, tx: tgt.x, ty: tgt.y, c: p.hue });
              }
            } else {
              // reel in
              p.rope.max = Math.max(90, p.rope.max - K.GRAPPLE_PULL * dt * 0.35);
              if (p.rope.b) { p.rope.px = p.rope.b.x; p.rope.py = p.rope.b.y; }
            }
          } else if (p.rope) {
            this.world.removeRope(p.rope); p.rope = null; p.ropeTo = null;
          }

          if (p.hurtTimer <= 0 && p.hp < K.PLAYER_HP) {
            p.hp = Math.min(K.PLAYER_HP, p.hp + K.REGEN_RATE * dt);
          }

          b.x = clamp(b.x, 10, K.ARENA_W - 10);
          b.y = clamp(b.y, 10, K.ARENA_H - 10);
        }
      }

      grappleTarget(p) {
        const b = p.body;
        let best = null;
        const consider = (x, y, body) => {
          const d = dist(b.x, b.y, x, y);
          if (d > K.GRAPPLE_RANGE || d < 60) return;
          if (!best || d < best.d) best = { x, y, d, body };
        };
        for (const e of this.enemies.values()) consider(e.body.x, e.body.y, e.body);
        for (const bl of this.blobs.values()) consider(bl.nucleus.x, bl.nucleus.y, bl.nucleus);
        for (const pl of this.pillars) consider(pl.x, pl.y, null);
        return best;
      }

      fireBolt(p) {
        const b = p.body;
        const a = p.aim;
        const dx = Math.cos(a), dy = Math.sin(a);
        const bolt = {
          id: newId(), owner: p.id, hue: p.hue, dmg: K.BOLT_DMG,
          life: K.BOLT_LIFE, bounces: 1, type: 'bolt',
        };
        bolt.body = this.world.add(new Body({
          x: b.x + dx * (K.PLAYER_R + K.BOLT_R + 2),
          y: b.y + dy * (K.PLAYER_R + K.BOLT_R + 2),
          vx: b.vx + dx * K.BOLT_SPEED, vy: b.vy + dy * K.BOLT_SPEED,
          r: K.BOLT_R, m: K.BOLT_M, e: 0.75, mu: 0.05, damping: 0.02, angularDamping: 0.2,
          cat: CAT.BOLT, mask: ALL & ~CAT.PLAYER & ~CAT.BOLT & ~CAT.PICKUP, owner: bolt,
        }));
        this.bolts.set(bolt.id, bolt);
        // recoil — momentum has to come from somewhere
        b.applyImpulse(-dx * K.BOLT_M * K.BOLT_SPEED, -dy * K.BOLT_M * K.BOLT_SPEED);
        this.pushFx({ t: 'shot', x: bolt.body.x, y: bolt.body.y, a, c: p.hue });
      }

      /** Enemy fire: slow homing orbs from the pink Charmers. */
      firePinkOrb(e, target) {
        const b = e.body;
        const a = Math.atan2(target.body.y - b.y, target.body.x - b.x);
        const orb = { id: newId(), owner: null, hue: '#ff4db3', dmg: 14, life: 4, bounces: 3, type: 'pink', homing: target.id };
        orb.body = this.world.add(new Body({
          x: b.x + Math.cos(a) * (e.body.r + 14), y: b.y + Math.sin(a) * (e.body.r + 14),
          vx: Math.cos(a) * 520, vy: Math.sin(a) * 520,
          r: 12, m: 0.4, e: 0.8, mu: 0.1, damping: 0.05,
          cat: CAT.BOLT, mask: ALL & ~CAT.ENEMY & ~CAT.BOLT & ~CAT.PICKUP, owner: orb,
        }));
        this.bolts.set(orb.id, orb);
        this.pushFx({ t: 'shot', x: orb.body.x, y: orb.body.y, a, c: '#ff4db3' });
      }

      nearestPlayer(x, y) {
        let best = null, bd = Infinity;
        for (const p of this.players.values()) {
          if (!p.alive) continue;
          const d = dist(x, y, p.body.x, p.body.y);
          if (d < bd) { bd = d; best = p; }
        }
        return best ? { p: best, d: bd } : null;
      }

      stepGuards(dt) {
        // When only stragglers are left they stop playing coy and hunt, so a
        // wave never drags on while someone chases one sphere round a pylon.
        const hunt = this.enemies.size <= 3 && this.blobs.size === 0 ? 1.7 : 1;
        for (const e of this.enemies.values()) {
          const g = GUARDS[e.kind];
          const b = e.body;
          e.timer -= dt;
          const near = this.nearestPlayer(b.x, b.y);
          if (!near) continue;
          const p = near.p, d = Math.max(1, near.d);
          const tx = (p.body.x - b.x) / d, ty = (p.body.y - b.y) / d;

          if (e.kind === 'yellow') {
            e.wobble += dt * 6;
            const j = 0.45 / hunt;
            const jx = Math.cos(e.wobble) * j, jy = Math.sin(e.wobble * 1.3) * j;
            b.applyForce((tx + jx) * g.speed * hunt, (ty + jy) * g.speed * hunt);
          } else if (e.kind === 'pink') {
            const want = 330;
            const radial = clamp((d - want) / 200, -1, 1) * hunt;
            const px = -ty, py = tx;
            b.applyForce((tx * radial + px * 0.85) * g.speed, (ty * radial + py * 0.85) * g.speed);
            if (e.timer <= 0) { this.firePinkOrb(e, p); e.timer = 2.4; }
          } else if (e.kind === 'red') {
            if (e.state === 0) {                       // stalk
              b.applyForce(tx * g.speed * 0.45 * hunt, ty * g.speed * 0.45 * hunt);
              if (d < 780 * hunt && e.timer <= 0) { e.state = 1; e.timer = 0.75; this.pushFx({ t: 'wind', x: b.x, y: b.y }); }
            } else if (e.state === 1) {                // wind up
              b.vx *= 0.9; b.vy *= 0.9;
              if (e.timer <= 0) {
                b.applyImpulse(tx * 9000, ty * 9000);
                e.state = 2; e.timer = 1.1;
                this.pushFx({ t: 'charge', x: b.x, y: b.y, a: Math.atan2(ty, tx) });
              }
            } else {                                   // recover
              if (e.timer <= 0) { e.state = 0; e.timer = 0.6; }
            }
          } else if (e.kind === 'blue') {
            b.applyForce(tx * g.speed * 0.6 * hunt, ty * g.speed * 0.6 * hunt);
            // gravity well: drags every rebel in range toward the Warden
            for (const q of this.players.values()) {
              if (!q.alive) continue;
              const dd = dist(b.x, b.y, q.body.x, q.body.y);
              if (dd < 480 && dd > 1) {
                const pull = 1700 * (1 - dd / 480);
                q.body.applyForce(((b.x - q.body.x) / dd) * pull, ((b.y - q.body.y) / dd) * pull);
              }
            }
            if (e.shield < e.maxShield && e.timer <= 0) e.shield = Math.min(e.maxShield, e.shield + 14 * dt);
          }
        }
      }

      stepBlobs(dt) {
        for (const bl of this.blobs.values()) {
          bl.applyPressure();
          bl.timer -= dt;
          const c = bl.center();
          const near = this.nearestPlayer(c.x, c.y);
          if (near) {
            const d = Math.max(1, near.d);
            const s = bl.kind === 'henry' ? 5200 : 2600;
            bl.drive(((near.p.body.x - c.x) / d) * s, ((near.p.body.y - c.y) / d) * s);
          }
          // engulfing: anything inside the membrane is crushed and slowed
          for (const p of this.players.values()) {
            if (!p.alive || p.invuln > 0) continue;
            if (bl.contains(p.body.x, p.body.y)) {
              this.hurtPlayer(p, (bl.kind === 'henry' ? 26 : 16) * dt, c.x, c.y);
              p.body.vx *= 0.94; p.body.vy *= 0.94;
            }
          }
          if (bl.kind === 'henry') {
            bl.spawnTimer -= dt;
            if (bl.spawnTimer <= 0) {
              bl.spawnTimer = 7;
              const a = this.rand.angle();
              this.spawnGuard(this.rand.pick(['yellow', 'yellow', 'pink', 'red']),
                c.x + Math.cos(a) * (bl.r0 + 90), c.y + Math.sin(a) * (bl.r0 + 90));
            }
            if (bl.timer <= 0) {
              bl.timer = 9;
              this.shockwave(c.x, c.y, 760, 2200, 22, null);
              this.pushFx({ t: 'henrywave', x: c.x, y: c.y, r: 760 });
            }
          }
          // keep it inside the arena
          for (const p of bl.particles.concat([bl.nucleus])) {
            p.x = clamp(p.x, 6, K.ARENA_W - 6);
            p.y = clamp(p.y, 6, K.ARENA_H - 6);
          }
        }
      }

      stepBolts(dt) {
        for (const b of this.bolts.values()) {
          b.life -= dt;
          if (b.type === 'pink' && b.homing) {
            const t = this.players.get(b.homing);
            if (t && t.alive) {
              const d = Math.max(1, dist(b.body.x, b.body.y, t.body.x, t.body.y));
              const s = 900;
              b.body.applyForce(((t.body.x - b.body.x) / d) * s, ((t.body.y - b.body.y) / d) * s);
            }
          }
          if (b.life <= 0) this.killBolt(b, false);
        }
      }

      stepOrbs(dt) {
        for (const o of this.orbs.values()) {
          o.life -= dt;
          if (o.life <= 0) { this.world.remove(o.body); this.orbs.delete(o.id); continue; }
          const near = this.nearestPlayer(o.body.x, o.body.y);
          if (!near) continue;
          if (near.d < K.ORB_MAGNET) {
            const pull = 2200 * (1 - near.d / K.ORB_MAGNET);
            const d = Math.max(1, near.d);
            o.body.applyForce(((near.p.body.x - o.body.x) / d) * pull, ((near.p.body.y - o.body.y) / d) * pull);
          }
          if (near.d < K.PLAYER_R + 16) {
            const p = near.p;
            p.angelos++; p.score += 15;
            this.collected++;
            p.hp = Math.min(K.PLAYER_HP, p.hp + K.ORB_HEAL);
            p.ultCharge++;
            if (p.ultCharge >= K.ULT_COST) { p.ultCharge = 0; p.ult = Math.min(K.ULT_MAX, p.ult + 1); }
            this.world.remove(o.body);
            this.orbs.delete(o.id);
            this.pushFx({ t: 'pickup', x: o.body.x, y: o.body.y, c: p.hue });
          }
        }
      }

      stepPhase(dt) {
        if (this.phase === 'intermission') {
          this.waveTimer -= dt;
          if (this.waveTimer <= 0) this.startWave(this.wave + 1);
          return;
        }
        if (this.phase === 'fight') {
          const clear = this.enemies.size === 0 && this.blobs.size === 0;
          if (clear) {
            const orbsLeft = this.orbs.size;
            if (orbsLeft === 0 || this.collected >= this.needed) {
              if (this.wave >= 10 && !this.endless) {
                this.phase = 'victory';
                this.banner = "HENRY'S RULE IS BROKEN";
                this.pushFx({ t: 'victory' });
              } else {
                this.phase = 'intermission';
                this.waveTimer = K.INTERMISSION;
                this.banner = 'WAVE ' + this.wave + ' CLEARED — ' + this.collected + ' ANGELOS TAKEN';
              }
            }
          }
          const anyAlive = [...this.players.values()].some((p) => p.alive);
          if (!anyAlive && this.players.size > 0) {
            this.phase = 'defeat';
            this.banner = 'THE RULE HOLDS — HENRY ANGELOS WINS';
            this.pushFx({ t: 'defeat' });
          }
        }
      }

      /* ── damage & contacts ─────────────────────────────────── */
      onContact(a, b, j, nx, ny, px, py) {
        const oa = a.owner, ob = b.owner;
        if (!oa && !ob) return;
        // bolt hits
        if (oa && oa.type && this.bolts.has(oa.id)) this.boltHit(oa, b, px, py, j);
        if (ob && ob.type && this.bolts.has(ob.id)) this.boltHit(ob, a, px, py, j);
        // sphere hits rebel
        this.maybeCrush(oa, ob, j, px, py);
        this.maybeCrush(ob, oa, j, px, py);
      }

      maybeCrush(attacker, victim, j, px, py) {
        if (!attacker || !victim) return;
        if (!this.enemies.has(attacker.id)) return;
        if (!victim.id || !this.players.has(victim.id)) return;
        const p = victim;
        if (!p.alive || p.invuln > 0) return;
        const key = attacker.id * 100003 + p.id;
        const last = this.contactCd.get(key) || 0;
        if (this.time - last < 0.55) return;
        this.contactCd.set(key, this.time);
        const g = GUARDS[attacker.kind];
        const dmg = g.dmg * (1 + clamp(j / 900, 0, 1.6));
        this.hurtPlayer(p, dmg, attacker.body.x, attacker.body.y);
      }

      boltHit(bolt, other, px, py, j) {
        const o = other.owner;
        if (o && this.enemies.has(o.id)) {
          this.damageGuard(o, bolt.dmg, bolt.owner, px, py);
          this.killBolt(bolt, true);
          return;
        }
        if (o && o instanceof Blob) {
          const isNucleus = other.isNucleus;
          const mult = isNucleus ? 1 : 0.4;
          this.damageBlob(o, bolt.dmg * mult, bolt.owner, px, py, isNucleus);
          this.killBolt(bolt, true);
          return;
        }
        if (bolt.type === 'pink' && o && this.players.has(o.id)) {
          if (o.alive && o.invuln <= 0) this.hurtPlayer(o, bolt.dmg, bolt.body.x, bolt.body.y);
          this.killBolt(bolt, true);
          return;
        }
        if (other.isStatic) {
          bolt.bounces--;
          if (bolt.bounces < 0) this.killBolt(bolt, true);
        }
      }

      hurtPlayer(p, dmg, fromX, fromY) {
        p.hp -= dmg;
        p.hurtTimer = K.REGEN_DELAY;
        this.pushFx({ t: 'hurt', x: p.body.x, y: p.body.y, c: p.hue, d: dmg });
        if (p.hp <= 0) {
          p.hp = 0; p.alive = false; p.respawn = K.RESPAWN;
          if (p.rope) { this.world.removeRope(p.rope); p.rope = null; }
          this.world.radialImpulse(p.body.x, p.body.y, 260, 700, { mask: CAT.ENEMY | CAT.BLOB });
          this.pushFx({ t: 'down', x: p.body.x, y: p.body.y, c: p.hue, n: p.name });
          p.body.x = -9999; p.body.y = -9999; p.body.vx = 0; p.body.vy = 0;
        }
      }

      damageGuard(e, dmg, byId, px, py) {
        if (e.kind === 'blue' && e.shield > 0) {
          const use = Math.min(e.shield, dmg);
          e.shield -= use; dmg -= use; e.timer = 3;
          this.pushFx({ t: 'shield', x: px, y: py });
          if (dmg <= 0) return;
        }
        e.hp -= dmg;
        const by = this.players.get(byId);
        if (by) by.score += Math.round(dmg);
        this.pushFx({ t: 'hit', x: px, y: py, k: e.kind });
        if (e.hp <= 0) this.killGuard(e, byId);
      }

      killGuard(e, byId) {
        const g = GUARDS[e.kind];
        const by = this.players.get(byId);
        if (by) { by.score += g.score; by.kills++; }
        const x = e.body.x, y = e.body.y;
        if (e.kind === 'red') {
          // queued, not recursive: a Wrath that pops a Wrath sets off a chain
          // reaction and the chain has to unwind iteratively.
          this.pendingBoom.push({ x, y, r: 300, power: 1900, dmg: 30, hurtsPlayers: true });
          this.pushFx({ t: 'boom', x, y, r: 300 });
        }
        for (let i = 0; i < g.drop; i++) this.spawnOrb(x, y);
        this.pushFx({ t: 'pop', x, y, k: e.kind });
        this.world.remove(e.body);
        this.enemies.delete(e.id);
      }

      damageBlob(bl, dmg, byId, px, py, nucleus) {
        bl.damage(dmg);
        const by = this.players.get(byId);
        if (by) by.score += Math.round(dmg);
        this.pushFx({ t: nucleus ? 'crit' : 'hit', x: px, y: py, k: 'blob' });
        if (bl.dead) this.killBlob(bl, byId);
      }

      killBlob(bl, byId) {
        const c = bl.center();
        const by = this.players.get(byId);
        if (by) { by.score += bl.kind === 'henry' ? 1200 : 220; by.kills++; }
        const drops = bl.kind === 'henry' ? 30 : 8;
        for (let i = 0; i < drops; i++) this.spawnOrb(c.x, c.y);
        this.shockwave(c.x, c.y, bl.r0 * 2.2, 1500, 0, null);
        this.pushFx({ t: 'burst', x: c.x, y: c.y, r: bl.r0, k: bl.kind });
        const splitR = bl.r0 * 0.58;
        const canSplit = bl.kind !== 'henry' && bl.r0 > 105;
        bl.destroy();
        this.blobs.delete(bl.id);
        if (canSplit) {
          for (let i = 0; i < 2; i++) {
            const a = this.rand.angle();
            this.spawnBlob(c.x + Math.cos(a) * splitR, c.y + Math.sin(a) * splitR, splitR, bl.maxHp * 0.45);
          }
        }
      }

      spawnOrb(x, y) {
        const a = this.rand.angle();
        const s = this.rand.range(120, 420);
        const o = { id: newId(), life: K.ORB_LIFE };
        o.body = this.world.add(new Body({
          x: x + Math.cos(a) * 12, y: y + Math.sin(a) * 12,
          vx: Math.cos(a) * s, vy: Math.sin(a) * s,
          r: 13, m: 0.25, e: 0.85, mu: 0.1, damping: 0.55,
          cat: CAT.PICKUP, mask: CAT.WALL | CAT.PICKUP, owner: o,
        }));
        this.orbs.set(o.id, o);
        return o;
      }

      shockwave(x, y, radius, power, dmg, byPlayer, hurtsPlayers) {
        const hits = this.world.radialImpulse(x, y, radius, power, { mask: CAT.ENEMY | CAT.BLOB | CAT.PLAYER | CAT.PICKUP });
        if (dmg > 0) {
          for (const h of hits) {
            const o = h.body.owner;
            if (!o) continue;
            if (this.enemies.has(o.id)) this.damageGuard(o, dmg * h.falloff, byPlayer ? byPlayer.id : null, h.body.x, h.body.y);
            else if (o instanceof Blob) this.damageBlob(o, dmg * h.falloff * 0.5, byPlayer ? byPlayer.id : null, h.body.x, h.body.y, false);
            else if (hurtsPlayers && this.players.has(o.id) && o.alive && o.invuln <= 0) {
              this.hurtPlayer(o, dmg * h.falloff, x, y);
            }
          }
        }
      }

      killBolt(bolt, hit) {
        if (!this.bolts.has(bolt.id)) return;
        this.pendingBolt.push(bolt);
        this.bolts.delete(bolt.id);
        if (hit) this.pushFx({ t: 'spark', x: bolt.body.x, y: bolt.body.y, c: bolt.hue });
      }

      flushBolts() {
        for (const b of this.pendingBolt) this.world.remove(b.body);
        this.pendingBolt.length = 0;
      }

      flushBooms() {
        let guard = 0;
        while (this.pendingBoom.length && guard++ < 64) {
          const b = this.pendingBoom.shift();
          this.shockwave(b.x, b.y, b.r, b.power, b.dmg, null, b.hurtsPlayers);
        }
        this.pendingBoom.length = 0;
      }

      /* Effects are a log, not a queue: the renderer and the network each
         keep their own cursor so neither steals events from the other. */
      pushFx(f) {
        f.s = ++this.fxSeq;
        this.fx.push(f);
        if (this.fx.length > 240) this.fx.splice(0, this.fx.length - 240);
      }
      fxSince(seq) {
        if (!seq) return this.fx.slice(-24);
        const out = [];
        for (let i = this.fx.length - 1; i >= 0; i--) {
          if (this.fx[i].s <= seq) break;
          out.push(this.fx[i]);
        }
        return out.reverse();
      }

      /* ── networking payloads ───────────────────────────────── */
      snapshot(sinceFx) {
        const q = Core.q1;
        const P = [];
        for (const p of this.players.values()) {
          P.push([p.id, q(p.body.x), q(p.body.y), q(p.body.vx), q(p.body.vy), q(p.aim),
            Math.round(p.hp), p.alive ? 1 : 0, p.score, p.angelos, +(p.dashCd > 0), p.ult,
            +(p.invuln > 0), p.name, p.hue, q(p.body.angle),
            p.rope && p.ropeTo ? [q(p.rope.b ? p.rope.b.x : p.rope.px), q(p.rope.b ? p.rope.b.y : p.rope.py)] : 0]);
        }
        const E = [];
        for (const e of this.enemies.values()) {
          E.push([e.id, e.kind, q(e.body.x), q(e.body.y), q(e.body.angle), Math.round(e.hp), e.maxHp,
            e.state, Math.round(e.shield), e.body.r]);
        }
        const B = [];
        for (const b of this.bolts.values()) B.push([b.id, q(b.body.x), q(b.body.y), b.hue, b.body.r]);
        const O = [];
        for (const o of this.orbs.values()) O.push([o.id, q(o.body.x), q(o.body.y)]);
        const L = [];
        for (const bl of this.blobs.values()) {
          L.push([bl.id, bl.kind, Math.round(bl.hp), bl.maxHp, q(bl.nucleus.x), q(bl.nucleus.y), bl.r0,
            bl.outline().map(q)]);
        }
        const s = {
          k: this.tick, ph: this.phase, wv: this.wave, wt: Core.q1(this.waveTimer),
          co: this.collected, nd: this.needed, bn: this.banner,
          P, E, B, O, L, F: this.fxSince(sinceFx), fs: this.fxSeq,
        };
        return s;
      }

      arena() {
        return { w: K.ARENA_W, h: K.ARENA_H, pillars: this.pillars, walls: this.walls };
      }

      stats() {
        return [...this.players.values()]
          .map((p) => ({ id: p.id, name: p.name, hue: p.hue, score: p.score, angelos: p.angelos, kills: p.kills, alive: p.alive }))
          .sort((a, b) => b.score - a.score);
      }
    }

    return { Game, K, GUARDS, HUES };
  }

  if (typeof module !== 'undefined' && module.exports) {
    module.exports = build(require('./core.js'), require('./physics.js'), require('./softbody.js'));
  } else {
    (global.WAR = global.WAR || {}).Sim = build(global.WAR.Core, global.WAR.Physics, global.WAR.Soft);
  }
})(typeof globalThis !== 'undefined' ? globalThis : this);
