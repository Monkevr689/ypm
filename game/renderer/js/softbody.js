/* WAR AGAINST HENRY ANGELOS — soft-body blobs.

   A pressurised membrane: a ring of particles held together by structural
   springs, kept round by shear springs and spokes to a heavy nucleus, and
   inflated by a gas-law pressure force over the enclosed polygon. It wobbles,
   squashes through gaps, swallows players and bursts. */
(function (global) {
  'use strict';

  function build(Core, Physics) {
    const { TAU, len, clamp } = Core;
    const { Body, CAT } = Physics;

    class Blob {
      constructor(world, o) {
        o = o || {};
        this.world = world;
        this.id = o.id || Core.nextId();
        this.r0 = o.r || 130;
        this.count = o.count || Math.max(14, Math.round(this.r0 / 6));
        this.hp = this.maxHp = o.hp || 260;
        this.pressure = o.pressure != null ? o.pressure : 5.2;
        this.stiff = o.stiff != null ? o.stiff : 1500;
        this.tint = o.tint || '#b44dff';
        this.kind = o.kind || 'blob';
        this.owner = o.owner || null;
        this.dead = false;

        const cat = CAT.BLOB;
        const mask = Physics.ALL & ~CAT.PICKUP;

        this.nucleus = world.add(new Body({
          x: o.x, y: o.y, r: Math.max(18, this.r0 * 0.26), m: this.r0 * 0.06,
          e: 0.1, mu: 0.5, damping: 1.1, cat, mask, owner: this,
        }));
        this.nucleus.isNucleus = true;

        this.particles = [];
        for (let i = 0; i < this.count; i++) {
          const a = (i / this.count) * TAU;
          const p = world.add(new Body({
            x: o.x + Math.cos(a) * this.r0,
            y: o.y + Math.sin(a) * this.r0,
            r: Math.max(9, this.r0 * 0.14),
            m: 0.55, e: 0.05, mu: 0.6, damping: 1.4, angularDamping: 4,
            cat, mask, owner: this,
          }));
          p.membraneIndex = i;
          this.particles.push(p);
        }

        this.springs = [];
        // Damping is derived from the pair's effective mass so the explicit
        // integrator stays stable: c = 2*zeta*sqrt(k*m_eff), capped below m/dt.
        const damping = (k, a, b, zeta) => {
          const ma = a.m, mb = b.m;
          const me = (ma * mb) / (ma + mb);
          return Math.min(2 * (zeta || 0.32) * Math.sqrt(k * me), me * 100);
        };
        const chord = 2 * this.r0 * Math.sin(Math.PI / this.count);
        for (let i = 0; i < this.count; i++) {
          const a = this.particles[i];
          const b = this.particles[(i + 1) % this.count];
          this.springs.push(world.addSpring({ a, b, rest: chord, k: this.stiff, damp: damping(this.stiff, a, b) }));
          // shear springs (skip one) stop the ring folding in on itself
          const c = this.particles[(i + 2) % this.count];
          const chord2 = 2 * this.r0 * Math.sin((2 * Math.PI) / this.count);
          this.springs.push(world.addSpring({ a, b: c, rest: chord2, k: this.stiff * 0.45, damp: damping(this.stiff * 0.45, a, c) }));
          // spoke to the nucleus: shape memory
          this.springs.push(world.addSpring({ a: this.nucleus, b: a, rest: this.r0, k: this.stiff * 0.30, damp: damping(this.stiff * 0.30, this.nucleus, a, 0.45) }));
        }
        this.area0 = this.polygonArea();
      }

      polygonArea() {
        let a = 0;
        const p = this.particles, n = p.length;
        for (let i = 0; i < n; i++) {
          const q = p[(i + 1) % n];
          a += p[i].x * q.y - q.x * p[i].y;
        }
        return Math.abs(a) * 0.5;
      }

      center() {
        let x = 0, y = 0;
        for (const p of this.particles) { x += p.x; y += p.y; }
        return { x: x / this.particles.length, y: y / this.particles.length };
      }

      /** Gas pressure: the smaller the enclosed area gets, the harder it pushes
          the membrane back out. This is what makes it squash instead of fold. */
      applyPressure() {
        const A = Math.max(1, this.polygonArea());
        const health = clamp(this.hp / this.maxHp, 0.25, 1);
        const target = this.area0 * (0.55 + 0.45 * health);
        const p = this.particles, n = p.length;
        const push = this.pressure * (target - A) / target;
        if (!isFinite(push)) return;
        for (let i = 0; i < n; i++) {
          const a = p[i], b = p[(i + 1) % n];
          const ex = b.x - a.x, ey = b.y - a.y;
          const l = Math.max(1e-4, len(ex, ey));
          // outward normal for a counter-clockwise ring
          const nx = ey / l, ny = -ex / l;
          const f = push * l;
          a.applyForce(nx * f, ny * f);
          b.applyForce(nx * f, ny * f);
        }
      }

      /** Move the whole creature by driving the nucleus; the membrane follows. */
      drive(fx, fy) {
        this.nucleus.applyForce(fx, fy);
        const per = 0.35 / this.particles.length;
        for (const p of this.particles) p.applyForce(fx * per, fy * per);
      }

      damage(n) {
        this.hp -= n;
        if (this.hp <= 0) { this.hp = 0; this.dead = true; }
        return this.dead;
      }

      contains(x, y) {
        const p = this.particles, n = p.length;
        let inside = false;
        for (let i = 0, j = n - 1; i < n; j = i++) {
          const xi = p[i].x, yi = p[i].y, xj = p[j].x, yj = p[j].y;
          if ((yi > y) !== (yj > y) && x < ((xj - xi) * (y - yi)) / (yj - yi) + xi) inside = !inside;
        }
        return inside;
      }

      outline() {
        const out = new Array(this.particles.length * 2);
        for (let i = 0; i < this.particles.length; i++) {
          out[i * 2] = this.particles[i].x;
          out[i * 2 + 1] = this.particles[i].y;
        }
        return out;
      }

      destroy() {
        for (const s of this.springs) {
          const i = this.world.springs.indexOf(s);
          if (i >= 0) this.world.springs.splice(i, 1);
        }
        for (const p of this.particles) this.world.remove(p);
        this.world.remove(this.nucleus);
        this.particles.length = 0;
      }
    }

    return { Blob };
  }

  if (typeof module !== 'undefined' && module.exports) {
    module.exports = build(require('./core.js'), require('./physics.js'));
  } else {
    (global.WAR = global.WAR || {}).Soft = build(global.WAR.Core, global.WAR.Physics);
  }
})(typeof globalThis !== 'undefined' ? globalThis : this);
