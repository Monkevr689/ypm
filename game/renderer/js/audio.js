/* WAR AGAINST HENRY ANGELOS — sound.
   Henry's own track loops underneath; every weapon, impact and pickup is
   synthesised live so the download stays small. Volume is sane by default —
   the original page ran the track through 35x gain, which this does not. */
(function (global) {
  'use strict';

  function build() {
    const Audio2 = {
      ctx: null, master: null, musicGain: null, sfxGain: null,
      el: null, started: false, muted: false,
      volume: 0.55, musicVolume: 0.42,

      init() {
        if (this.ctx) return this.ctx;
        const AC = global.AudioContext || global.webkitAudioContext;
        if (!AC) return null;
        this.ctx = new AC();
        this.master = this.ctx.createGain();
        this.master.gain.value = this.volume;
        const comp = this.ctx.createDynamicsCompressor();
        comp.threshold.value = -12; comp.ratio.value = 6; comp.knee.value = 18;
        this.master.connect(comp); comp.connect(this.ctx.destination);
        this.musicGain = this.ctx.createGain();
        this.musicGain.gain.value = this.musicVolume;
        this.musicGain.connect(this.master);
        this.sfxGain = this.ctx.createGain();
        this.sfxGain.gain.value = 0.75;
        this.sfxGain.connect(this.master);
        this.noise = this._noiseBuffer();
        return this.ctx;
      },

      _noiseBuffer() {
        const len = this.ctx.sampleRate * 1.2;
        const buf = this.ctx.createBuffer(1, len, this.ctx.sampleRate);
        const d = buf.getChannelData(0);
        for (let i = 0; i < len; i++) d[i] = Math.random() * 2 - 1;
        return buf;
      },

      music(src) {
        this.init();
        if (!this.el) {
          this.el = new global.Audio();
          this.el.loop = true;
          this.el.src = src;
          // On a file:// page (the packaged app, or the HTML opened directly)
          // routing media through Web Audio taints the graph and plays
          // silence, so the element drives its own volume there instead.
          const viaGraph = global.location && global.location.protocol !== 'file:';
          if (viaGraph && this.ctx) {
            try {
              this.node = this.ctx.createMediaElementSource(this.el);
              this.node.connect(this.musicGain);
              this.routed = true;
            } catch (e) { this.routed = false; }
          }
          if (!this.routed) this.el.volume = this.muted ? 0 : this.musicVolume * this.volume * 1.6;
        }
        if (!this.ctx) { const p0 = this.el.play(); if (p0 && p0.catch) p0.catch(() => {}); return; }
        const p = this.el.play();
        if (p && p.catch) p.catch(() => {});
        if (this.ctx.state === 'suspended') this.ctx.resume();
      },
      stopMusic() { if (this.el) this.el.pause(); },
      setVolume(v) {
        this.volume = v;
        if (this.master) this.master.gain.value = this.muted ? 0 : v;
        if (this.el && !this.routed) this.el.volume = this.muted ? 0 : Math.min(1, this.musicVolume * v * 1.6);
      },
      toggleMute() {
        this.muted = !this.muted;
        if (this.master) this.master.gain.value = this.muted ? 0 : this.volume;
        if (this.el && !this.routed) this.el.volume = this.muted ? 0 : Math.min(1, this.musicVolume * this.volume * 1.6);
        return this.muted;
      },

      _env(node, gain, dur, attack) {
        const t = this.ctx.currentTime;
        const g = this.ctx.createGain();
        g.gain.setValueAtTime(0.0001, t);
        g.gain.exponentialRampToValueAtTime(Math.max(0.0002, gain), t + (attack || 0.005));
        g.gain.exponentialRampToValueAtTime(0.0001, t + dur);
        node.connect(g); g.connect(this.sfxGain);
        return g;
      },

      blip(freq, type, dur, gain, slideTo) {
        if (!this.ctx || this.muted) return;
        const o = this.ctx.createOscillator();
        o.type = type || 'square';
        const t = this.ctx.currentTime;
        o.frequency.setValueAtTime(freq, t);
        if (slideTo) o.frequency.exponentialRampToValueAtTime(Math.max(20, slideTo), t + dur);
        this._env(o, gain || 0.2, dur);
        o.start(t); o.stop(t + dur + 0.02);
      },

      noiseHit(dur, cutoff, gain, sweepTo) {
        if (!this.ctx || this.muted) return;
        const s = this.ctx.createBufferSource();
        s.buffer = this.noise;
        const f = this.ctx.createBiquadFilter();
        f.type = 'lowpass';
        const t = this.ctx.currentTime;
        f.frequency.setValueAtTime(cutoff, t);
        if (sweepTo) f.frequency.exponentialRampToValueAtTime(Math.max(60, sweepTo), t + dur);
        s.connect(f);
        this._env(f, gain || 0.3, dur);
        s.start(t); s.stop(t + dur + 0.02);
      },

      play(name) {
        if (!this.ctx || this.muted) return;
        switch (name) {
          case 'shot': this.blip(880, 'square', 0.07, 0.12, 220); break;
          case 'hit': this.noiseHit(0.08, 2600, 0.18, 400); break;
          case 'pop': this.noiseHit(0.22, 1800, 0.3, 180); this.blip(320, 'sawtooth', 0.18, 0.12, 80); break;
          case 'boom': this.noiseHit(0.6, 900, 0.5, 60); this.blip(120, 'sine', 0.5, 0.35, 36); break;
          case 'pickup': this.blip(880, 'triangle', 0.09, 0.16, 1320); this.blip(1320, 'triangle', 0.13, 0.1, 1760); break;
          case 'ult': this.blip(180, 'sawtooth', 0.55, 0.28, 1800); this.noiseHit(0.5, 4000, 0.3, 300); break;
          case 'dash': this.noiseHit(0.16, 5200, 0.16, 800); break;
          case 'hurt': this.blip(220, 'square', 0.14, 0.2, 90); break;
          case 'down': this.blip(160, 'sawtooth', 0.7, 0.3, 50); break;
          case 'wave': this.blip(440, 'square', 0.12, 0.2); setTimeout(() => this.blip(660, 'square', 0.18, 0.2), 130); break;
          case 'victory':
            [523, 659, 784, 1047].forEach((f, i) => setTimeout(() => this.blip(f, 'triangle', 0.35, 0.22), i * 150));
            break;
          case 'defeat':
            [392, 330, 262, 196].forEach((f, i) => setTimeout(() => this.blip(f, 'sawtooth', 0.45, 0.22), i * 200));
            break;
          default: break;
        }
      },
    };
    return Audio2;
  }

  if (typeof module !== 'undefined' && module.exports) module.exports = build();
  else (global.WAR = global.WAR || {}).Audio = build();
})(typeof globalThis !== 'undefined' ? globalThis : this);
