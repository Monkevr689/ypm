/* WAR AGAINST HENRY ANGELOS — core maths, RNG and helpers.
   Loads as a plain <script> in the browser and as a CommonJS module in Node
   (the headless tests reuse the exact same code the game runs). */
(function (global) {
  'use strict';

  const TAU = Math.PI * 2;

  const clamp = (v, lo, hi) => (v < lo ? lo : v > hi ? hi : v);
  const lerp = (a, b, t) => a + (b - a) * t;
  const smooth = (a, b, t) => a + (b - a) * (1 - Math.pow(1 - clamp(t, 0, 1), 3));
  const len = (x, y) => Math.sqrt(x * x + y * y);
  const dist = (ax, ay, bx, by) => len(bx - ax, by - ay);
  const dist2 = (ax, ay, bx, by) => (bx - ax) * (bx - ax) + (by - ay) * (by - ay);

  /** Shortest signed difference between two angles. */
  function angleDelta(a, b) {
    let d = (b - a) % TAU;
    if (d > Math.PI) d -= TAU;
    if (d < -Math.PI) d += TAU;
    return d;
  }

  /** Deterministic PRNG — the host seeds it and every client replays the same
      arena layout from the same seed, so nothing has to be sent over the wire. */
  function rng(seed) {
    let s = (seed >>> 0) || 1;
    const f = function () {
      s |= 0; s = (s + 0x6d2b79f5) | 0;
      let t = Math.imul(s ^ (s >>> 15), 1 | s);
      t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
      return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
    };
    f.range = (a, b) => a + f() * (b - a);
    f.int = (a, b) => Math.floor(a + f() * (b - a + 1));
    f.pick = (arr) => arr[Math.floor(f() * arr.length) % arr.length];
    f.sign = () => (f() < 0.5 ? -1 : 1);
    f.angle = () => f() * TAU;
    return f;
  }

  let _id = 0;
  const nextId = () => ++_id;
  const resetIds = (v) => { _id = v || 0; };

  /** Rounds for the wire: one decimal is well under a pixel at play scale. */
  const q1 = (v) => Math.round(v * 10) / 10;

  const Core = { TAU, clamp, lerp, smooth, len, dist, dist2, angleDelta, rng, nextId, resetIds, q1 };

  if (typeof module !== 'undefined' && module.exports) module.exports = Core;
  else (global.WAR = global.WAR || {}).Core = Core;
})(typeof globalThis !== 'undefined' ? globalThis : this);
