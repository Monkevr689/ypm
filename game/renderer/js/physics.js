/* WAR AGAINST HENRY ANGELOS — rigid body physics.

   Impulse-based sequential solver with warm starting, restitution, Coulomb
   friction, rotational inertia (the spheres really do spin when they scrape),
   sub-stepping and a uniform-grid broadphase. Shapes are circles and static
   capsules — everything in this game is round, so contacts stay exact rather
   than being approximated by boxes. */
(function (global) {
  'use strict';

  function build(Core) {
    const { clamp, len } = Core;

    // Collision categories (bitmask).
    const CAT = {
      WALL:   1 << 0,
      PLAYER: 1 << 1,
      ENEMY:  1 << 2,
      BOLT:   1 << 3,
      PICKUP: 1 << 4,
      BLOB:   1 << 5,
      DEBRIS: 1 << 6,
    };
    const ALL = 0xffff;

    let bodySeq = 0;

    class Body {
      constructor(o) {
        o = o || {};
        this.id = o.id != null ? o.id : ++bodySeq;
        this.x = o.x || 0; this.y = o.y || 0;
        this.vx = o.vx || 0; this.vy = o.vy || 0;
        this.fx = 0; this.fy = 0;                 // force accumulator
        this.angle = o.angle || 0;
        this.av = o.av || 0;                      // angular velocity
        this.torque = 0;
        this.r = o.r != null ? o.r : 16;
        this.isStatic = !!o.isStatic;
        this.sensor = !!o.sensor;
        this.e = o.e != null ? o.e : 0.35;        // restitution
        this.mu = o.mu != null ? o.mu : 0.28;     // friction
        this.damping = o.damping != null ? o.damping : 0.6;
        this.angularDamping = o.angularDamping != null ? o.angularDamping : 1.4;
        this.cat = o.cat || CAT.DEBRIS;
        this.mask = o.mask != null ? o.mask : ALL;
        this.owner = o.owner || null;             // game entity behind the body
        this.shape = 'circle';
        this.setMass(o.m != null ? o.m : 1);
        if (this.isStatic) { this.invM = 0; this.invI = 0; }
      }
      setMass(m) {
        this.m = m;
        this.invM = this.isStatic || m <= 0 ? 0 : 1 / m;
        // Solid disc: I = 1/2 m r^2
        const I = 0.5 * m * this.r * this.r;
        this.invI = this.isStatic || I <= 0 ? 0 : 1 / I;
      }
      get speed() { return len(this.vx, this.vy); }
      applyForce(fx, fy) { this.fx += fx; this.fy += fy; }
      applyImpulse(ix, iy, px, py) {
        if (this.invM === 0) return;
        this.vx += ix * this.invM;
        this.vy += iy * this.invM;
        if (px !== undefined) {
          const rx = px - this.x, ry = py - this.y;
          this.av += (rx * iy - ry * ix) * this.invI;
        }
      }
      setVel(vx, vy) { this.vx = vx; this.vy = vy; }
    }

    /** Static capsule (a thick line segment) — arena walls and girders. */
    class Capsule {
      constructor(x1, y1, x2, y2, r, o) {
        o = o || {};
        this.id = ++bodySeq;
        this.x1 = x1; this.y1 = y1; this.x2 = x2; this.y2 = y2;
        this.r = r;
        this.isStatic = true; this.sensor = false;
        this.invM = 0; this.invI = 0; this.m = Infinity;
        this.e = o.e != null ? o.e : 0.42;
        this.mu = o.mu != null ? o.mu : 0.35;
        this.cat = CAT.WALL; this.mask = ALL;
        this.owner = o.owner || null;
        this.shape = 'capsule';
        this.x = (x1 + x2) / 2; this.y = (y1 + y2) / 2;
        this.vx = 0; this.vy = 0; this.av = 0; this.angle = 0;
      }
      applyImpulse() { /* static: absorbs everything */ }
      applyForce() {}
      setVel() {}
      closest(px, py) {
        const dx = this.x2 - this.x1, dy = this.y2 - this.y1;
        const l2 = dx * dx + dy * dy;
        let t = l2 > 0 ? ((px - this.x1) * dx + (py - this.y1) * dy) / l2 : 0;
        t = clamp(t, 0, 1);
        return { x: this.x1 + dx * t, y: this.y1 + dy * t };
      }
    }

    class Contact {
      constructor(a, b) {
        this.a = a; this.b = b;
        this.nx = 0; this.ny = 0; this.depth = 0;
        this.px = 0; this.py = 0;
        this.jn = 0; this.jt = 0;     // accumulated impulses (warm starting)
        this.e = 0; this.mu = 0;
        this.stamp = 0;
      }
    }

    class World {
      constructor(o) {
        o = o || {};
        this.bodies = [];
        this.statics = [];
        this.springs = [];
        this.ropes = [];
        this.cell = o.cell || 110;
        this.iterations = o.iterations || 8;
        this.posIterations = o.posIterations || 3;
        this.substeps = o.substeps || 2;
        this.grid = new Map();
        this.contacts = new Map();     // pairKey -> Contact (persists for warm starting)
        this.stamp = 0;
        this.onContact = null;         // (a, b, impulse, nx, ny, px, py)
        this.onSensor = null;          // (sensorBody, otherBody)
        this.slop = 0.4;
        this.baumgarte = 0.22;
        this.restitutionFloor = 45;    // below this closing speed, bounces are killed
      }

      add(b) { (b.isStatic ? this.statics : this.bodies).push(b); return b; }
      remove(b) {
        const arr = b.isStatic ? this.statics : this.bodies;
        const i = arr.indexOf(b);
        if (i >= 0) arr.splice(i, 1);
        this.springs = this.springs.filter((s) => s.a !== b && s.b !== b);
        this.ropes = this.ropes.filter((s) => s.a !== b && s.b !== b);
      }
      clear() {
        this.bodies.length = 0; this.statics.length = 0;
        this.springs.length = 0; this.ropes.length = 0;
        this.contacts.clear(); this.grid.clear();
      }
      addWall(x1, y1, x2, y2, r, o) { const c = new Capsule(x1, y1, x2, y2, r, o); this.statics.push(c); return c; }
      addSpring(s) {
        // { a, b, rest, k, damp } — force based, used by the soft-body blobs.
        s.k = s.k != null ? s.k : 900;
        s.damp = s.damp != null ? s.damp : 18;
        this.springs.push(s); return s;
      }
      addRope(r) {
        // { a, b | point, max } — hard max-distance constraint (the grapple).
        this.ropes.push(r); return r;
      }
      removeRope(r) { const i = this.ropes.indexOf(r); if (i >= 0) this.ropes.splice(i, 1); }

      /** Push everything in range away from a point — explosions, shockwaves. */
      radialImpulse(x, y, radius, power, opts) {
        opts = opts || {};
        const hit = [];
        for (const b of this.bodies) {
          if (b.invM === 0) continue;
          if (opts.mask && !(b.cat & opts.mask)) continue;
          const dx = b.x - x, dy = b.y - y;
          const d = Math.max(1e-3, len(dx, dy));
          if (d > radius + b.r) continue;
          const falloff = Math.pow(1 - clamp((d - b.r) / radius, 0, 1), opts.curve || 1.4);
          const j = power * falloff;
          b.applyImpulse((dx / d) * j, (dy / d) * j, b.x - (dy / d) * b.r * 0.3, b.y + (dx / d) * b.r * 0.3);
          hit.push({ body: b, dist: d, falloff });
        }
        return hit;
      }

      queryCircle(x, y, r, mask) {
        const out = [];
        const r2 = r * r;
        for (const b of this.bodies) {
          if (mask && !(b.cat & mask)) continue;
          const dx = b.x - x, dy = b.y - y;
          const rr = r + b.r;
          if (dx * dx + dy * dy <= Math.max(r2, rr * rr)) out.push(b);
        }
        return out;
      }

      /* ── broadphase ───────────────────────────────────────────── */
      _hash(cx, cy) { return cx * 73856093 ^ cy * 19349663; }
      _rebuildGrid() {
        this.grid.clear();
        const cs = this.cell;
        const span = (lo, hi) => (hi - lo > 512 ? lo + 512 : hi);
        const put = (b) => {
          if (!isFinite(b.x) || !isFinite(b.y)) { b.x = 0; b.y = 0; b.vx = 0; b.vy = 0; }
          const minx = Math.floor((b.shape === 'capsule' ? Math.min(b.x1, b.x2) - b.r : b.x - b.r) / cs);
          const maxx = Math.floor((b.shape === 'capsule' ? Math.max(b.x1, b.x2) + b.r : b.x + b.r) / cs);
          const miny = Math.floor((b.shape === 'capsule' ? Math.min(b.y1, b.y2) - b.r : b.y - b.r) / cs);
          const maxy = Math.floor((b.shape === 'capsule' ? Math.max(b.y1, b.y2) + b.r : b.y + b.r) / cs);
          const mx = span(minx, maxx), my = span(miny, maxy);
          for (let cx = minx; cx <= mx; cx++) {
            for (let cy = miny; cy <= my; cy++) {
              const k = this._hash(cx, cy);
              let bucket = this.grid.get(k);
              if (!bucket) { bucket = []; this.grid.set(k, bucket); }
              bucket.push(b);
            }
          }
        };
        for (const b of this.bodies) put(b);
        for (const s of this.statics) put(s);
      }

      _pairKey(a, b) {
        const lo = Math.min(a.id, b.id), hi = Math.max(a.id, b.id);
        return lo * 1000003 + hi;
      }

      _collide(a, b, list, seen) {
        if (a.isStatic && b.isStatic) return;
        if (!(a.cat & b.mask) || !(b.cat & a.mask)) return;
        const key = this._pairKey(a, b);
        if (seen.has(key)) return;
        seen.add(key);

        let nx, ny, depth, px, py;
        if (a.shape === 'capsule' || b.shape === 'capsule') {
          const cap = a.shape === 'capsule' ? a : b;
          const cir = a.shape === 'capsule' ? b : a;
          const c = cap.closest(cir.x, cir.y);
          let dx = cir.x - c.x, dy = cir.y - c.y;
          let d = len(dx, dy);
          const rr = cap.r + cir.r;
          if (d >= rr) return;
          if (d < 1e-6) { dx = 0; dy = -1; d = 1e-6; }
          // normal always points from a -> b
          const sx = dx / d, sy = dy / d;
          const flip = (cap === a) ? 1 : -1;
          nx = sx * flip; ny = sy * flip;
          depth = rr - d;
          px = c.x + sx * cap.r; py = c.y + sy * cap.r;
        } else {
          let dx = b.x - a.x, dy = b.y - a.y;
          let d = len(dx, dy);
          const rr = a.r + b.r;
          if (d >= rr) return;
          if (d < 1e-6) { dx = 0.01; dy = 0; d = 0.01; }
          nx = dx / d; ny = dy / d;
          depth = rr - d;
          px = a.x + nx * (a.r - depth * 0.5);
          py = a.y + ny * (a.r - depth * 0.5);
        }

        if (a.sensor || b.sensor) {
          if (this.onSensor) this.onSensor(a.sensor ? a : b, a.sensor ? b : a);
          return;
        }

        let c = this.contacts.get(key);
        if (!c || c.a !== a || c.b !== b) { c = new Contact(a, b); this.contacts.set(key, c); }
        c.nx = nx; c.ny = ny; c.depth = depth; c.px = px; c.py = py;
        c.e = Math.max(a.e, b.e);
        c.mu = Math.sqrt(a.mu * b.mu);
        c.stamp = this.stamp;
        list.push(c);
      }

      _narrowphase() {
        const list = [];
        const seen = new Set();
        for (const bucket of this.grid.values()) {
          const n = bucket.length;
          if (n < 2) continue;
          for (let i = 0; i < n; i++) {
            for (let j = i + 1; j < n; j++) this._collide(bucket[i], bucket[j], list, seen);
          }
        }
        // drop stale warm-start entries
        if (this.contacts.size > 4096) {
          for (const [k, c] of this.contacts) if (c.stamp < this.stamp - 3) this.contacts.delete(k);
        }
        return list;
      }

      /* ── solver ───────────────────────────────────────────────── */
      _relVel(c) {
        const { a, b } = c;
        const rax = c.px - a.x, ray = c.py - a.y;
        const rbx = c.px - b.x, rby = c.py - b.y;
        const vax = a.vx - a.av * ray, vay = a.vy + a.av * rax;
        const vbx = b.vx - b.av * rby, vby = b.vy + b.av * rbx;
        return { rax, ray, rbx, rby, vx: vbx - vax, vy: vby - vay };
      }

      _solveVelocity(list, dt) {
        // cache the approach speed once so restitution isn't re-applied per iteration
        for (const c of list) {
          const rv = this._relVel(c);
          c._approach = rv.vx * c.nx + rv.vy * c.ny;
          c._bias = c.depth > this.slop
            ? (this.baumgarte / dt) * (c.depth - this.slop)
            : 0;
          c._restitution = (-c._approach > this.restitutionFloor) ? c.e * -c._approach : 0;
          // warm start
          const jx = c.nx * c.jn - c.ny * c.jt;
          const jy = c.ny * c.jn + c.nx * c.jt;
          c.a.applyImpulse(-jx, -jy, c.px, c.py);
          c.b.applyImpulse(jx, jy, c.px, c.py);
        }

        for (let it = 0; it < this.iterations; it++) {
          for (const c of list) {
            const { a, b } = c;
            const rv = this._relVel(c);
            const vn = rv.vx * c.nx + rv.vy * c.ny;
            const rnA = rv.rax * c.ny - rv.ray * c.nx;
            const rnB = rv.rbx * c.ny - rv.rby * c.nx;
            const kn = a.invM + b.invM + rnA * rnA * a.invI + rnB * rnB * b.invI;
            if (kn <= 0) continue;

            let dJn = (-(vn) + c._bias + c._restitution) / kn;
            const oldJn = c.jn;
            c.jn = Math.max(0, oldJn + dJn);
            dJn = c.jn - oldJn;

            // friction along the tangent
            const tx = -c.ny, ty = c.nx;
            const vt = rv.vx * tx + rv.vy * ty;
            const rtA = rv.rax * ty - rv.ray * tx;
            const rtB = rv.rbx * ty - rv.rby * tx;
            const kt = a.invM + b.invM + rtA * rtA * a.invI + rtB * rtB * b.invI;
            let dJt = kt > 0 ? -vt / kt : 0;
            const maxF = c.mu * c.jn;
            const oldJt = c.jt;
            c.jt = clamp(oldJt + dJt, -maxF, maxF);
            dJt = c.jt - oldJt;

            const jx = c.nx * dJn + tx * dJt;
            const jy = c.ny * dJn + ty * dJt;
            a.applyImpulse(-jx, -jy, c.px, c.py);
            b.applyImpulse(jx, jy, c.px, c.py);
          }
          this._solveRopes();
        }
      }

      _solveRopes() {
        for (const rp of this.ropes) {
          const a = rp.a;
          const bx = rp.b ? rp.b.x : rp.px, by = rp.b ? rp.b.y : rp.py;
          const bInvM = rp.b ? rp.b.invM : 0;
          let dx = bx - a.x, dy = by - a.y;
          let d = len(dx, dy);
          if (d < 1e-4) continue;
          const nx = dx / d, ny = dy / d;
          const over = d - rp.max;
          if (over <= 0) continue;                  // slack rope does nothing
          const rvx = (rp.b ? rp.b.vx : 0) - a.vx;
          const rvy = (rp.b ? rp.b.vy : 0) - a.vy;
          const vn = rvx * nx + rvy * ny;
          const k = a.invM + bInvM;
          if (k <= 0) continue;
          const bias = 0.35 * over / Math.max(1e-4, rp._dt || 1 / 120);
          let j = (vn + bias) / k;
          if (j < 0) j = 0;
          a.applyImpulse(nx * j, ny * j);
          if (rp.b) rp.b.applyImpulse(-nx * j, -ny * j);
        }
      }

      _solvePosition(list) {
        for (let it = 0; it < this.posIterations; it++) {
          for (const c of list) {
            const { a, b } = c;
            const k = a.invM + b.invM;
            if (k <= 0) continue;
            const corr = Math.max(c.depth - this.slop, 0) * 0.42 / k;
            a.x -= c.nx * corr * a.invM; a.y -= c.ny * corr * a.invM;
            b.x += c.nx * corr * b.invM; b.y += c.ny * corr * b.invM;
          }
        }
      }

      _applySprings() {
        for (const s of this.springs) {
          const a = s.a, b = s.b;
          let dx = b.x - a.x, dy = b.y - a.y;
          let d = len(dx, dy);
          if (d < 1e-5) { dx = 1e-5; d = 1e-5; }
          const nx = dx / d, ny = dy / d;
          const ext = d - s.rest;
          const rvn = (b.vx - a.vx) * nx + (b.vy - a.vy) * ny;
          const f = s.k * ext + s.damp * rvn;
          a.applyForce(nx * f, ny * f);
          b.applyForce(-nx * f, -ny * f);
        }
      }

      step(dt) {
        const n = this.substeps;
        const h = dt / n;
        for (let s = 0; s < n; s++) this._substep(h);
      }

      _substep(dt) {
        this.stamp++;
        this._applySprings();

        for (const b of this.bodies) {
          if (b.invM === 0) continue;
          b.vx += b.fx * b.invM * dt;
          b.vy += b.fy * b.invM * dt;
          b.av += b.torque * b.invI * dt;
          b.fx = 0; b.fy = 0; b.torque = 0;
          // exponential drag — stable at any dt, unlike v *= (1 - k*dt)
          const ld = 1 / (1 + b.damping * dt);
          b.vx *= ld; b.vy *= ld;
          b.av *= 1 / (1 + b.angularDamping * dt);
          if (!isFinite(b.vx) || !isFinite(b.vy)) { b.vx = 0; b.vy = 0; }
          if (!isFinite(b.av)) b.av = 0;
          const sp = b.vx * b.vx + b.vy * b.vy;
          if (sp > World.MAX_SPEED * World.MAX_SPEED) {
            const k = World.MAX_SPEED / Math.sqrt(sp);
            b.vx *= k; b.vy *= k;
          }
          if (b.av > World.MAX_SPIN) b.av = World.MAX_SPIN;
          else if (b.av < -World.MAX_SPIN) b.av = -World.MAX_SPIN;
        }

        for (const rp of this.ropes) rp._dt = dt;

        this._rebuildGrid();
        const list = this._narrowphase();
        this._solveVelocity(list, dt);

        for (const b of this.bodies) {
          if (b.invM === 0) continue;
          b.x += b.vx * dt;
          b.y += b.vy * dt;
          b.angle += b.av * dt;
        }

        this._solvePosition(list);

        if (this.onContact) {
          for (const c of list) {
            if (c.jn > 0) this.onContact(c.a, c.b, c.jn, c.nx, c.ny, c.px, c.py);
          }
        }
      }
    }

    World.MAX_SPEED = 9000;
    World.MAX_SPIN = 42;

    return { Body, Capsule, World, CAT, ALL };
  }

  if (typeof module !== 'undefined' && module.exports) module.exports = build(require('./core.js'));
  else (global.WAR = global.WAR || {}).Physics = build(global.WAR.Core);
})(typeof globalThis !== 'undefined' ? globalThis : this);
