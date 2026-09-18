/* WAR AGAINST HENRY ANGELOS — canvas renderer.
   Neon-phonk arena: glow sprites are pre-rendered into offscreen canvases so
   the hot loop never touches shadowBlur, which is the usual 2D-canvas killer. */
(function (global) {
  'use strict';

  function build(Core) {
    const { clamp, lerp, TAU } = Core;

    const KIND_COLOR = {
      yellow: '#ffd21f', pink: '#ff2fd0', red: '#ff2f3c', blue: '#2f8bff',
      blob: '#b44dff', henry: '#ff2fd0',
    };
    const KIND_LABEL = {
      yellow: 'FALSEITY ANGELOSITY', pink: 'LOVE ANGELOSITY',
      red: 'CRULETY ANGELOSITY', blue: 'VEIRTY ANGELOSITY',
    };

    /* ── asset loading (everything is optional: the game draws without it) ── */
    const Assets = {
      images: {}, ready: false,
      load(base) {
        base = base || '../assets/';
        const files = {
          yellow: 'sphere_yellow.png', pink: 'sphere_pink.png',
          red: 'sphere_red.png', blue: 'sphere_blue.png',
          group: 'henry_group.jpg',
        };
        const jobs = Object.keys(files).map((k) => new Promise((res) => {
          const img = new Image();
          img.onload = () => { this.images[k] = img; res(); };
          img.onerror = () => res();
          img.src = base + files[k];
        }));
        return Promise.all(jobs).then(() => { this.ready = true; return this.images; });
      },
    };

    const glowCache = new Map();
    function glowSprite(color, size) {
      const key = color + '|' + size;
      let c = glowCache.get(key);
      if (c) return c;
      c = document.createElement('canvas');
      c.width = c.height = size * 2;
      const g = c.getContext('2d');
      const grd = g.createRadialGradient(size, size, 0, size, size, size);
      grd.addColorStop(0, color);
      grd.addColorStop(0.35, hexA(color, 0.42));
      grd.addColorStop(1, hexA(color, 0));
      g.fillStyle = grd;
      g.fillRect(0, 0, size * 2, size * 2);
      glowCache.set(key, c);
      return c;
    }
    function hexA(hex, a) {
      if (hex[0] !== '#') return hex;
      const n = parseInt(hex.slice(1), 16);
      const r = (n >> 16) & 255, g = (n >> 8) & 255, b = n & 255;
      return `rgba(${r},${g},${b},${a})`;
    }

    class Particles {
      constructor(max) { this.list = []; this.max = max || 900; }
      add(p) { if (this.list.length < this.max) this.list.push(p); }
      burst(x, y, n, opts) {
        opts = opts || {};
        for (let i = 0; i < n; i++) {
          const a = Math.random() * TAU;
          const s = (opts.speed || 260) * (0.35 + Math.random());
          this.add({
            x, y, vx: Math.cos(a) * s, vy: Math.sin(a) * s,
            life: (opts.life || 0.55) * (0.6 + Math.random() * 0.8),
            max: opts.life || 0.55, col: opts.col || '#fff',
            size: (opts.size || 3) * (0.6 + Math.random()), drag: opts.drag || 2.2,
            type: opts.type || 'spark',
          });
        }
      }
      ring(x, y, r, col, life) {
        this.add({ x, y, r0: 0, r1: r, life: life || 0.5, max: life || 0.5, col, type: 'ring' });
      }
      text(x, y, str, col) {
        this.add({ x, y, vx: 0, vy: -60, life: 0.9, max: 0.9, col, type: 'text', str });
      }
      update(dt) {
        const l = this.list;
        for (let i = l.length - 1; i >= 0; i--) {
          const p = l[i];
          p.life -= dt;
          if (p.life <= 0) { l.splice(i, 1); continue; }
          if (p.type === 'ring') continue;
          p.x += p.vx * dt; p.y += p.vy * dt;
          const d = 1 / (1 + (p.drag || 2) * dt);
          p.vx *= d; p.vy *= d;
        }
      }
      draw(ctx) {
        for (const p of this.list) {
          const t = clamp(p.life / p.max, 0, 1);
          if (p.type === 'ring') {
            const r = lerp(p.r1 * 0.15, p.r1, 1 - t);
            ctx.globalAlpha = t * 0.85;
            ctx.strokeStyle = p.col; ctx.lineWidth = 3 + 6 * t;
            ctx.beginPath(); ctx.arc(p.x, p.y, r, 0, TAU); ctx.stroke();
          } else if (p.type === 'text') {
            ctx.globalAlpha = t;
            ctx.fillStyle = p.col;
            ctx.font = '700 22px Impact, "Arial Black", sans-serif';
            ctx.textAlign = 'center';
            ctx.fillText(p.str, p.x, p.y);
          } else {
            ctx.globalAlpha = t;
            ctx.fillStyle = p.col;
            const s = p.size * (0.4 + t);
            ctx.fillRect(p.x - s / 2, p.y - s / 2, s, s);
          }
        }
        ctx.globalAlpha = 1;
      }
    }

    class Renderer {
      constructor(canvas) {
        this.canvas = canvas;
        this.ctx = canvas.getContext('2d', { alpha: false });
        this.parts = new Particles();
        this.cam = { x: 1700, y: 1100, zoom: 0.55, tx: 1700, ty: 1100, tz: 0.55 };
        this.shakeAmt = 0;
        this.time = 0;
        this.trails = new Map();
        this.dpr = Math.min(2, (global.devicePixelRatio || 1));
        this.arena = { w: 3400, h: 2200 };
        this.resize();
      }

      resize() {
        const c = this.canvas;
        const w = c.clientWidth || global.innerWidth;
        const h = c.clientHeight || global.innerHeight;
        c.width = Math.floor(w * this.dpr);
        c.height = Math.floor(h * this.dpr);
        this.w = w; this.h = h;
      }

      shake(a) { this.shakeAmt = Math.min(38, this.shakeAmt + a); }

      /** Turn simulation effects into things you can see and feel. */
      consume(fxList) {
        for (const f of fxList || []) {
          switch (f.t) {
            case 'shot': this.parts.burst(f.x, f.y, 3, { col: f.c, speed: 200, life: 0.18, size: 3 }); break;
            case 'spark': this.parts.burst(f.x, f.y, 7, { col: f.c || '#fff', speed: 320, life: 0.3, size: 3 }); break;
            case 'hit': this.parts.burst(f.x, f.y, 9, { col: KIND_COLOR[f.k] || '#fff', speed: 340, life: 0.34, size: 4 }); break;
            case 'crit':
              this.parts.burst(f.x, f.y, 20, { col: '#fff', speed: 520, life: 0.45, size: 5 });
              this.parts.ring(f.x, f.y, 90, '#ffffff', 0.35); this.shake(4); break;
            case 'pop':
              this.parts.burst(f.x, f.y, 26, { col: KIND_COLOR[f.k] || '#fff', speed: 520, life: 0.6, size: 5 });
              this.parts.ring(f.x, f.y, 140, KIND_COLOR[f.k] || '#fff', 0.45); this.shake(5); break;
            case 'boom':
              this.parts.burst(f.x, f.y, 60, { col: '#ff6a2f', speed: 900, life: 0.8, size: 7 });
              this.parts.ring(f.x, f.y, f.r, '#ff2f3c', 0.55); this.shake(16); break;
            case 'burst':
              this.parts.burst(f.x, f.y, 90, { col: f.k === 'henry' ? '#ff2fd0' : '#b44dff', speed: 1000, life: 1.1, size: 8 });
              this.parts.ring(f.x, f.y, f.r * 2.4, '#ff2fd0', 0.8); this.shake(f.k === 'henry' ? 34 : 18); break;
            case 'ult':
              this.parts.ring(f.x, f.y, f.r, f.c || '#2fe6ff', 0.6);
              this.parts.burst(f.x, f.y, 50, { col: f.c || '#2fe6ff', speed: 800, life: 0.7, size: 6 }); this.shake(14); break;
            case 'henrywave':
              this.parts.ring(f.x, f.y, f.r, '#ff2fd0', 0.9); this.shake(24); break;
            case 'pickup':
              this.parts.burst(f.x, f.y, 10, { col: '#ffd24d', speed: 240, life: 0.4, size: 4 });
              this.parts.ring(f.x, f.y, 46, '#ffd24d', 0.3); break;
            case 'hurt':
              this.parts.burst(f.x, f.y, 12, { col: '#ff2f3c', speed: 300, life: 0.4, size: 4 }); this.shake(3); break;
            case 'down':
              this.parts.burst(f.x, f.y, 50, { col: f.c || '#fff', speed: 640, life: 0.9, size: 6 });
              this.parts.text(f.x, f.y - 40, (f.n || 'REBEL') + ' DOWN', '#ff2f3c'); this.shake(12); break;
            case 'respawn': this.parts.ring(f.x, f.y, 120, '#2fe6ff', 0.5); break;
            case 'dash': this.parts.burst(f.x, f.y, 14, { col: f.c || '#2fe6ff', speed: 300, life: 0.3, size: 4 }); break;
            case 'spawn': this.parts.ring(f.x, f.y, 90, KIND_COLOR[f.k] || '#fff', 0.5); break;
            case 'blobspawn': this.parts.ring(f.x, f.y, f.r * 2, '#b44dff', 0.7); this.shake(8); break;
            case 'charge': this.parts.burst(f.x, f.y, 24, { col: '#ff2f3c', speed: 600, life: 0.4, size: 5 }); this.shake(6); break;
            case 'shield': this.parts.burst(f.x, f.y, 8, { col: '#2fe6ff', speed: 260, life: 0.3, size: 3 }); break;
            case 'wave': this.shake(6); break;
            default: break;
          }
        }
      }

      focus(x, y, snap) {
        this.cam.tx = x; this.cam.ty = y;
        if (snap) { this.cam.x = x; this.cam.y = y; }
      }

      draw(view, o) {
        o = o || {};
        const ctx = this.ctx, dt = o.dt || 1 / 60;
        this.time += dt;
        this.parts.update(dt);

        // camera
        const follow = 1 - Math.pow(0.0006, dt);
        this.cam.x = lerp(this.cam.x, this.cam.tx, follow);
        this.cam.y = lerp(this.cam.y, this.cam.ty, follow);
        const fit = Math.min(this.w / 1460, this.h / 920);
        this.cam.tz = clamp(fit * (o.zoom || 1), 0.28, 1.4);
        this.cam.zoom = lerp(this.cam.zoom, this.cam.tz, 1 - Math.pow(0.02, dt));
        this.shakeAmt *= Math.pow(0.02, dt);
        const sx = (Math.random() - 0.5) * this.shakeAmt;
        const sy = (Math.random() - 0.5) * this.shakeAmt;

        ctx.setTransform(this.dpr, 0, 0, this.dpr, 0, 0);
        ctx.fillStyle = '#05050a';
        ctx.fillRect(0, 0, this.w, this.h);

        const z = this.cam.zoom;
        ctx.save();
        ctx.translate(this.w / 2 + sx, this.h / 2 + sy);
        ctx.scale(z, z);
        ctx.translate(-this.cam.x, -this.cam.y);

        this.drawArena(ctx, view);
        this.drawOrbs(ctx, view);
        this.drawBlobs(ctx, view, o);
        this.drawEnemies(ctx, view);
        this.drawBolts(ctx, view);
        this.drawPlayers(ctx, view, o);
        this.parts.draw(ctx);
        if (o.cursor) this.drawCursor(ctx, o.cursor);

        ctx.restore();
        this.drawOffscreenMarkers(ctx, view, o);
        this.drawVignette(ctx);
      }

      drawArena(ctx, view) {
        const W = this.arena.w, H = this.arena.h;
        const g = ctx.createRadialGradient(W / 2, H / 2, 40, W / 2, H / 2, Math.max(W, H) * 0.8);
        g.addColorStop(0, '#1a0a2e');
        g.addColorStop(1, '#05050a');
        ctx.fillStyle = g;
        ctx.fillRect(-200, -200, W + 400, H + 400);

        ctx.lineWidth = 1;
        ctx.strokeStyle = 'rgba(255,47,208,0.07)';
        ctx.beginPath();
        for (let x = 0; x <= W; x += 120) { ctx.moveTo(x, 0); ctx.lineTo(x, H); }
        for (let y = 0; y <= H; y += 120) { ctx.moveTo(0, y); ctx.lineTo(W, y); }
        ctx.stroke();

        // arena border
        ctx.strokeStyle = '#ff2fd0';
        ctx.lineWidth = 6;
        ctx.globalAlpha = 0.55 + 0.2 * Math.sin(this.time * 2);
        ctx.strokeRect(0, 0, W, H);
        ctx.globalAlpha = 1;

        if (view && view.pillars) {
          for (const p of view.pillars) this.drawPillar(ctx, p);
        }
        if (view && view.walls) {
          ctx.strokeStyle = 'rgba(47,230,255,0.5)';
          for (const w of view.walls) {
            ctx.lineWidth = w[4] * 2;
            ctx.lineCap = 'round';
            ctx.beginPath(); ctx.moveTo(w[0], w[1]); ctx.lineTo(w[2], w[3]); ctx.stroke();
            ctx.lineWidth = 3; ctx.strokeStyle = '#2fe6ff';
            ctx.beginPath(); ctx.moveTo(w[0], w[1]); ctx.lineTo(w[2], w[3]); ctx.stroke();
            ctx.strokeStyle = 'rgba(47,230,255,0.5)';
          }
        }
      }

      drawPillar(ctx, p) {
        const gl = glowSprite('#2fe6ff', 128);
        ctx.drawImage(gl, p.x - p.r * 1.8, p.y - p.r * 1.8, p.r * 3.6, p.r * 3.6);
        ctx.fillStyle = '#0d0a1c';
        ctx.beginPath(); ctx.arc(p.x, p.y, p.r, 0, TAU); ctx.fill();
        ctx.strokeStyle = '#2fe6ff'; ctx.lineWidth = 4;
        ctx.stroke();
        ctx.strokeStyle = 'rgba(255,47,208,0.6)'; ctx.lineWidth = 2;
        ctx.beginPath(); ctx.arc(p.x, p.y, p.r * 0.66, this.time, this.time + 2.1); ctx.stroke();
      }

      drawOrbs(ctx, view) {
        const gl = glowSprite('#ffd24d', 64);
        for (const o of view.O || []) {
          const x = o[1], y = o[2];
          const pulse = 1 + 0.16 * Math.sin(this.time * 6 + x * 0.01);
          ctx.drawImage(gl, x - 48 * pulse, y - 48 * pulse, 96 * pulse, 96 * pulse);
          ctx.fillStyle = '#fff7d6';
          ctx.beginPath(); ctx.arc(x, y, 9, 0, TAU); ctx.fill();
          ctx.strokeStyle = '#ffd24d'; ctx.lineWidth = 2.5;
          ctx.beginPath(); ctx.arc(x, y, 15 * pulse, 0, TAU); ctx.stroke();
          ctx.globalAlpha = 0.6;
          ctx.beginPath(); ctx.ellipse(x, y - 17, 13, 5, 0, 0, TAU); ctx.stroke();
          ctx.globalAlpha = 1;
        }
      }

      drawBlobs(ctx, view, o) {
        for (const L of view.L || []) {
          const [id, kind, hp, maxHp, nx, ny, r0, pts] = L;
          if (!pts || pts.length < 6) continue;
          const col = kind === 'henry' ? '#ff2fd0' : '#b44dff';
          ctx.save();
          ctx.beginPath();
          const n = pts.length / 2;
          // smooth closed curve through the membrane particles
          let px = (pts[(n - 1) * 2] + pts[0]) / 2, py = (pts[(n - 1) * 2 + 1] + pts[1]) / 2;
          ctx.moveTo(px, py);
          for (let i = 0; i < n; i++) {
            const cx = pts[i * 2], cy = pts[i * 2 + 1];
            const j = (i + 1) % n;
            const mx = (cx + pts[j * 2]) / 2, my = (cy + pts[j * 2 + 1]) / 2;
            ctx.quadraticCurveTo(cx, cy, mx, my);
          }
          ctx.closePath();

          const grd = ctx.createRadialGradient(nx, ny, r0 * 0.1, nx, ny, r0 * 1.3);
          grd.addColorStop(0, hexA(col, 0.95));
          grd.addColorStop(0.55, hexA(col, 0.45));
          grd.addColorStop(1, hexA('#05050a', 0.75));
          ctx.fillStyle = grd;
          ctx.fill();
          ctx.clip();

          const face = Assets.images[kind === 'henry' ? 'red' : 'pink'];
          if (face) {
            const s = r0 * (kind === 'henry' ? 1.9 : 1.35);
            ctx.globalAlpha = kind === 'henry' ? 0.92 : 0.5;
            ctx.drawImage(face, nx - s / 2, ny - s / 2, s, s);
            ctx.globalAlpha = 1;
          }
          ctx.restore();

          ctx.strokeStyle = col;
          ctx.lineWidth = kind === 'henry' ? 7 : 4;
          ctx.globalAlpha = 0.9;
          ctx.beginPath();
          let qx = (pts[(n - 1) * 2] + pts[0]) / 2, qy = (pts[(n - 1) * 2 + 1] + pts[1]) / 2;
          ctx.moveTo(qx, qy);
          for (let i = 0; i < n; i++) {
            const cx = pts[i * 2], cy = pts[i * 2 + 1];
            const j = (i + 1) % n;
            ctx.quadraticCurveTo(cx, cy, (cx + pts[j * 2]) / 2, (cy + pts[j * 2 + 1]) / 2);
          }
          ctx.closePath(); ctx.stroke();
          ctx.globalAlpha = 1;

          // nucleus — the weak point
          const gl = glowSprite('#ffffff', 64);
          const pulse = 1 + 0.12 * Math.sin(this.time * 7);
          ctx.drawImage(gl, nx - 58 * pulse, ny - 58 * pulse, 116 * pulse, 116 * pulse);
          ctx.fillStyle = '#fff';
          ctx.beginPath(); ctx.arc(nx, ny, r0 * 0.14, 0, TAU); ctx.fill();

          this.healthArc(ctx, nx, ny, r0 * 1.12, hp / maxHp, col);
          ctx.fillStyle = '#fff';
          ctx.font = '700 26px Impact, "Arial Black", sans-serif';
          ctx.textAlign = 'center';
          ctx.fillText(kind === 'henry' ? 'HENRY ANGELOS' : 'THE BLOB', nx, ny - r0 * 1.3);
        }
      }

      drawEnemies(ctx, view) {
        for (const e of view.E || []) {
          const [id, kind, x, y, angle, hp, maxHp, state, shield, r] = e;
          const col = KIND_COLOR[kind] || '#fff';
          const gl = glowSprite(col, 128);
          ctx.drawImage(gl, x - r * 2.1, y - r * 2.1, r * 4.2, r * 4.2);

          if (kind === 'blue') {
            ctx.strokeStyle = hexA('#2f8bff', 0.28);
            ctx.lineWidth = 2;
            ctx.beginPath(); ctx.arc(x, y, 480, 0, TAU); ctx.stroke();
            if (shield > 0) {
              ctx.strokeStyle = hexA('#2fe6ff', 0.75); ctx.lineWidth = 4;
              ctx.beginPath(); ctx.arc(x, y, r + 14, 0, TAU); ctx.stroke();
            }
          }
          if (kind === 'red' && state === 1) {
            ctx.strokeStyle = '#ff2f3c'; ctx.lineWidth = 5;
            ctx.globalAlpha = 0.5 + 0.5 * Math.sin(this.time * 30);
            ctx.beginPath(); ctx.arc(x, y, r + 22, 0, TAU); ctx.stroke();
            ctx.globalAlpha = 1;
          }

          const img = Assets.images[kind];
          ctx.save();
          ctx.translate(x, y);
          ctx.rotate(angle);
          if (img) {
            ctx.drawImage(img, -r, -r, r * 2, r * 2);
          } else {
            const grd = ctx.createRadialGradient(-r * 0.3, -r * 0.35, r * 0.1, 0, 0, r);
            grd.addColorStop(0, '#fff'); grd.addColorStop(0.35, col); grd.addColorStop(1, '#100818');
            ctx.fillStyle = grd;
            ctx.beginPath(); ctx.arc(0, 0, r, 0, TAU); ctx.fill();
          }
          ctx.restore();

          if (hp < maxHp) this.healthArc(ctx, x, y, r + 10, hp / maxHp, col);

          const label = KIND_LABEL[kind];
          if (label) {
            ctx.font = '700 11px Impact, "Arial Black", sans-serif';
            ctx.textAlign = 'center';
            ctx.fillStyle = 'rgba(0,0,0,0.6)';
            ctx.fillText(label, x + 1, y - r - 13);
            ctx.fillStyle = col;
            ctx.fillText(label, x, y - r - 14);
          }
        }
      }

      healthArc(ctx, x, y, r, frac, col) {
        ctx.strokeStyle = 'rgba(0,0,0,0.55)'; ctx.lineWidth = 5;
        ctx.beginPath(); ctx.arc(x, y, r, -Math.PI * 0.75, Math.PI * 0.75); ctx.stroke();
        ctx.strokeStyle = col; ctx.lineWidth = 4;
        ctx.beginPath();
        ctx.arc(x, y, r, -Math.PI * 0.75, -Math.PI * 0.75 + Math.PI * 1.5 * clamp(frac, 0, 1));
        ctx.stroke();
      }

      drawBolts(ctx, view) {
        for (const b of view.B || []) {
          const [id, x, y, hue, r] = b;
          const prev = this.trails.get(id);
          const gl = glowSprite(hue || '#2fe6ff', 48);
          ctx.drawImage(gl, x - 30, y - 30, 60, 60);
          if (prev) {
            ctx.strokeStyle = hexA(hue || '#2fe6ff', 0.75);
            ctx.lineWidth = r * 1.2;
            ctx.lineCap = 'round';
            ctx.beginPath(); ctx.moveTo(prev.x, prev.y); ctx.lineTo(x, y); ctx.stroke();
          }
          ctx.fillStyle = '#fff';
          ctx.beginPath(); ctx.arc(x, y, r * 0.7, 0, TAU); ctx.fill();
          this.trails.set(id, { x, y, t: this.time });
        }
        for (const [id, t] of this.trails) if (this.time - t.t > 0.2) this.trails.delete(id);
      }

      drawPlayers(ctx, view, o) {
        for (const p of view.P || []) {
          const [id, px, py, vx, vy, aim, hp, alive, score, ang, dashing, ult, invuln, name, hue, angle, rope] = p;
          if (!alive) continue;
          let x = px, y = py;
          if (o.selfId === id && o.predicted) { x = o.predicted.x; y = o.predicted.y; }

          if (rope && rope.length === 2) {
            ctx.strokeStyle = hexA(hue, 0.8); ctx.lineWidth = 3;
            ctx.setLineDash([12, 8]);
            ctx.beginPath(); ctx.moveTo(x, y); ctx.lineTo(rope[0], rope[1]); ctx.stroke();
            ctx.setLineDash([]);
          }

          const gl = glowSprite(hue, 96);
          ctx.drawImage(gl, x - 62, y - 62, 124, 124);

          ctx.save();
          ctx.translate(x, y);
          // thruster
          const sp = Math.hypot(vx, vy);
          if (sp > 40) {
            const a = Math.atan2(vy, vx);
            ctx.save(); ctx.rotate(a);
            ctx.fillStyle = hexA('#2fe6ff', 0.55);
            ctx.beginPath();
            ctx.moveTo(-26, -10); ctx.lineTo(-26 - clamp(sp / 8, 10, 70), 0); ctx.lineTo(-26, 10);
            ctx.closePath(); ctx.fill();
            ctx.restore();
          }
          ctx.rotate(aim);
          ctx.fillStyle = invuln ? 'rgba(255,255,255,0.85)' : '#0d0a1c';
          ctx.strokeStyle = hue; ctx.lineWidth = 4;
          ctx.beginPath(); ctx.arc(0, 0, 26, 0, TAU); ctx.fill(); ctx.stroke();
          // barrel
          ctx.fillStyle = hue;
          ctx.fillRect(16, -5, 22, 10);
          ctx.beginPath(); ctx.arc(0, 0, 9, 0, TAU); ctx.fillStyle = '#fff'; ctx.fill();
          ctx.restore();

          this.healthArc(ctx, x, y, 34, hp / 100, hp > 40 ? '#3cff9e' : '#ff2f3c');

          ctx.fillStyle = hue;
          ctx.font = '700 18px Impact, "Arial Black", sans-serif';
          ctx.textAlign = 'center';
          ctx.fillText(name || '', x, y - 46);
          if (ult > 0) {
            ctx.fillStyle = '#ffd24d';
            ctx.font = '700 14px Impact, sans-serif';
            ctx.fillText('★'.repeat(ult), x, y - 64);
          }
        }
      }

      drawCursor(ctx, c) {
        ctx.strokeStyle = 'rgba(47,230,255,0.85)';
        ctx.lineWidth = 2;
        ctx.beginPath(); ctx.arc(c.x, c.y, 13, 0, TAU); ctx.stroke();
        ctx.beginPath();
        ctx.moveTo(c.x - 22, c.y); ctx.lineTo(c.x - 7, c.y);
        ctx.moveTo(c.x + 7, c.y); ctx.lineTo(c.x + 22, c.y);
        ctx.moveTo(c.x, c.y - 22); ctx.lineTo(c.x, c.y - 7);
        ctx.moveTo(c.x, c.y + 7); ctx.lineTo(c.x, c.y + 22);
        ctx.stroke();
      }

      /** Chevrons at the screen edge so the last sphere is never lost. */
      drawOffscreenMarkers(ctx, view, o) {
        const cx = this.w / 2, cy = this.h / 2;
        const z = this.cam.zoom;
        const margin = 54;
        const mark = (wx, wy, col) => {
          const sx = (wx - this.cam.x) * z + cx;
          const sy = (wy - this.cam.y) * z + cy;
          if (sx > margin && sx < this.w - margin && sy > margin && sy < this.h - margin) return;
          const a = Math.atan2(sy - cy, sx - cx);
          const rx = (this.w / 2 - margin), ry = (this.h / 2 - margin);
          const k = Math.min(Math.abs(rx / Math.cos(a)), Math.abs(ry / Math.sin(a)));
          const px = cx + Math.cos(a) * k, py = cy + Math.sin(a) * k;
          ctx.save();
          ctx.translate(px, py); ctx.rotate(a);
          ctx.fillStyle = col;
          ctx.globalAlpha = 0.85;
          ctx.beginPath(); ctx.moveTo(14, 0); ctx.lineTo(-10, -9); ctx.lineTo(-10, 9); ctx.closePath(); ctx.fill();
          ctx.restore();
          ctx.globalAlpha = 1;
        };
        for (const e of view.E || []) mark(e[2], e[3], KIND_COLOR[e[1]] || '#fff');
        for (const L of view.L || []) mark(L[4], L[5], L[1] === 'henry' ? '#ff2fd0' : '#b44dff');
        for (const p of view.P || []) if (p[7] && p[0] !== o.selfId) mark(p[1], p[2], p[14]);
      }

      drawVignette(ctx) {
        const g = ctx.createRadialGradient(this.w / 2, this.h / 2, Math.min(this.w, this.h) * 0.35,
          this.w / 2, this.h / 2, Math.max(this.w, this.h) * 0.75);
        g.addColorStop(0, 'rgba(0,0,0,0)');
        g.addColorStop(1, 'rgba(0,0,0,0.72)');
        ctx.fillStyle = g;
        ctx.fillRect(0, 0, this.w, this.h);
      }

      drawMinimap(ctx, view, w, h) {
        const W = this.arena.w, H = this.arena.h;
        const sx = w / W, sy = h / H;
        ctx.clearRect(0, 0, w, h);
        ctx.fillStyle = 'rgba(8,4,16,0.75)';
        ctx.fillRect(0, 0, w, h);
        ctx.strokeStyle = 'rgba(255,47,208,0.6)';
        ctx.strokeRect(0.5, 0.5, w - 1, h - 1);
        for (const o of view.O || []) { ctx.fillStyle = '#ffd24d'; ctx.fillRect(o[1] * sx - 1, o[2] * sy - 1, 3, 3); }
        for (const e of view.E || []) {
          ctx.fillStyle = KIND_COLOR[e[1]] || '#fff';
          ctx.fillRect(e[2] * sx - 2, e[3] * sy - 2, 4, 4);
        }
        for (const L of view.L || []) {
          ctx.fillStyle = L[1] === 'henry' ? '#ff2fd0' : '#b44dff';
          ctx.beginPath(); ctx.arc(L[4] * sx, L[5] * sy, 5, 0, TAU); ctx.fill();
        }
        for (const p of view.P || []) {
          if (!p[7]) continue;
          ctx.fillStyle = p[14] || '#fff';
          ctx.beginPath(); ctx.arc(p[1] * sx, p[2] * sy, 3.4, 0, TAU); ctx.fill();
        }
      }
    }

    return { Renderer, Assets, KIND_COLOR, glowSprite, hexA, Particles };
  }

  if (typeof module !== 'undefined' && module.exports) module.exports = build(require('./core.js'));
  else (global.WAR = global.WAR || {}).Render = build(global.WAR.Core);
})(typeof globalThis !== 'undefined' ? globalThis : this);
