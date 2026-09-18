/* WAR AGAINST HENRY ANGELOS — multiplayer.

   The host runs the simulation and ships snapshots; clients send inputs and
   render an interpolated view of what the host reports, with local prediction
   for their own rebel so aiming and dashing stay instant.

   Transport lives in the Electron main process (raw TCP + UDP discovery),
   exposed here through window.WARNET. In a plain browser there is no
   transport, so the game runs solo. */
(function (global) {
  'use strict';

  function build(Core) {
    const { clamp, lerp, len } = Core;
    const api = (typeof window !== 'undefined' && window.WARNET) || null;

    const SNAP_HZ = 30;
    const INTERP_DELAY = 0.12;   // render this far in the past to hide jitter

    class Client {
      constructor() {
        this.buf = [];           // [{t, snap}]
        this.selfId = null;
        this.now = 0;
        this.latest = null;
        this.pred = { x: 0, y: 0, vx: 0, vy: 0, on: false };
        this.arena = null;
        this.stats = [];
        this.fx = [];
      }
      push(snap) {
        this.latest = snap;
        this.buf.push({ t: this.now, snap });
        if (this.buf.length > 40) this.buf.shift();
        if (snap.F) for (const f of snap.F) this.fx.push(f);
      }
      /** Interpolated world state for rendering. */
      sample(dt) {
        this.now += dt;
        const target = this.now - INTERP_DELAY;
        if (this.buf.length === 0) return null;
        let a = this.buf[0], b = this.buf[this.buf.length - 1];
        for (let i = 0; i < this.buf.length - 1; i++) {
          if (this.buf[i].t <= target && this.buf[i + 1].t >= target) { a = this.buf[i]; b = this.buf[i + 1]; break; }
        }
        const span = Math.max(1e-4, b.t - a.t);
        const u = clamp((target - a.t) / span, 0, 1);
        return this.blend(a.snap, b.snap, u);
      }
      blend(A, B, u) {
        const out = {
          k: B.k, ph: B.ph, wv: B.wv, wt: B.wt, co: B.co, nd: B.nd, bn: B.bn,
          P: [], E: [], B: [], O: [], L: [],
        };
        const index = (rows) => { const m = new Map(); if (rows) for (const r of rows) m.set(r[0], r); return m; };
        const pa = index(A.P), ea = index(A.E), ba = index(A.B), oa = index(A.O), la = index(A.L);
        for (const r of B.P || []) {
          const o = pa.get(r[0]);
          const row = r.slice();
          if (o) { row[1] = lerp(o[1], r[1], u); row[2] = lerp(o[2], r[2], u); row[15] = lerpAngle(o[15], r[15], u); }
          out.P.push(row);
        }
        for (const r of B.E || []) {
          const o = ea.get(r[0]); const row = r.slice();
          if (o) { row[2] = lerp(o[2], r[2], u); row[3] = lerp(o[3], r[3], u); row[4] = lerpAngle(o[4], r[4], u); }
          out.E.push(row);
        }
        for (const r of B.B || []) {
          const o = ba.get(r[0]); const row = r.slice();
          if (o) { row[1] = lerp(o[1], r[1], u); row[2] = lerp(o[2], r[2], u); }
          out.B.push(row);
        }
        for (const r of B.O || []) {
          const o = oa.get(r[0]); const row = r.slice();
          if (o) { row[1] = lerp(o[1], r[1], u); row[2] = lerp(o[2], r[2], u); }
          out.O.push(row);
        }
        for (const r of B.L || []) {
          const o = la.get(r[0]); const row = r.slice();
          if (o) {
            row[4] = lerp(o[4], r[4], u); row[5] = lerp(o[5], r[5], u);
            if (o[7] && o[7].length === r[7].length) {
              row[7] = r[7].map((v, i) => lerp(o[7][i], v, u));
            }
          }
          out.L.push(row);
        }
        return out;
      }
      /** Dead reckoning for the local rebel: same movement model as the host. */
      predict(input, dt, K) {
        const p = this.pred;
        if (!p.on) return null;
        const m = len(input.mx, input.my);
        if (m > 0.02) {
          const s = Math.min(1, m);
          p.vx += ((input.mx / m) * K.THRUST * s / K.PLAYER_M) * dt;
          p.vy += ((input.my / m) * K.THRUST * s / K.PLAYER_M) * dt;
        }
        const ld = 1 / (1 + K.PLAYER_DAMP * dt);
        p.vx *= ld; p.vy *= ld;
        p.x += p.vx * dt; p.y += p.vy * dt;
        return p;
      }
      reconcile(sx, sy, svx, svy) {
        const p = this.pred;
        if (!p.on) { p.x = sx; p.y = sy; p.vx = svx; p.vy = svy; p.on = true; return; }
        const err = Math.hypot(sx - p.x, sy - p.y);
        const k = err > 260 ? 1 : 0.22;       // big desync (a wall, a blast): snap
        p.x = lerp(p.x, sx, k); p.y = lerp(p.y, sy, k);
        p.vx = lerp(p.vx, svx, 0.5); p.vy = lerp(p.vy, svy, 0.5);
      }
    }

    function lerpAngle(a, b, u) { return a + Core.angleDelta(a, b) * u; }

    /** Thin wrapper over the Electron transport with an offline fallback. */
    const Net = {
      available: !!api,
      mode: 'solo',
      peers: new Map(),
      client: new Client(),
      handlers: {},
      lastSnap: 0,
      myId: 1,

      on(type, fn) { (this.handlers[type] = this.handlers[type] || []).push(fn); },
      emit(type, data) { for (const fn of this.handlers[type] || []) fn(data); },

      async host(port, name) {
        if (!api) { this.mode = 'solo'; return { ok: false, reason: 'no-transport' }; }
        const res = await api.host(port || 47861, name || 'HENRY ANGELOS WAR');
        if (res.ok) { this.mode = 'host'; this.bind(); }
        return res;
      },
      async join(host, port, name) {
        if (!api) return { ok: false, reason: 'no-transport' };
        const res = await api.join(host, port || 47861, name || 'REBEL');
        if (res.ok) { this.mode = 'client'; this.bind(); }
        return res;
      },
      async discover(ms) {
        if (!api) return [];
        return api.discover(ms || 900);
      },
      stop() {
        if (api) api.stop();
        this.mode = 'solo';
        this.peers.clear();
      },
      bind() {
        if (this._bound || !api) return;
        this._bound = true;
        api.onEvent((ev) => {
          if (ev.type === 'join') { this.peers.set(ev.peer, { id: ev.peer, name: 'REBEL' }); this.emit('join', ev); }
          else if (ev.type === 'leave') { this.peers.delete(ev.peer); this.emit('leave', ev); }
          else if (ev.type === 'msg') this.emit('msg', ev);
          else if (ev.type === 'closed') { this.mode = 'solo'; this.emit('closed', ev); }
          else this.emit(ev.type, ev);
        });
      },
      send(obj) { if (api) api.send(obj); },
      sendTo(peer, obj) { if (api) api.sendTo(peer, obj); },
      localIPs() { return api ? api.ips() : Promise.resolve([]); },
    };

    return { Net, Client, SNAP_HZ, INTERP_DELAY };
  }

  if (typeof module !== 'undefined' && module.exports) module.exports = build(require('./core.js'));
  else (global.WAR = global.WAR || {}).Net = build(global.WAR.Core);
})(typeof globalThis !== 'undefined' ? globalThis : this);
