/* WAR AGAINST HENRY ANGELOS — bootstrap, input and the game loop. */
(function (global) {
  'use strict';

  const { Core, Physics, Sim, Net: NetMod, Render, Audio, UI } = global.WAR;
  const { Game, K } = Sim;
  const { Net } = NetMod;
  const { clamp } = Core;

  const state = {
    mode: 'menu',          // menu | solo | host | client
    running: false,
    paused: false,
    game: null,
    selfId: 1,
    arena: null,
    view: null,
    renderFx: 0,
    netFx: 0,
    snapAcc: 0,
    inputAcc: 0,
    acc: 0,
    last: performance.now(),
    peerPlayers: new Map(),   // transport peer id -> sim player id
    resultShown: false,
    mouse: { x: 0, y: 0, down: false, right: false },
    keys: Object.create(null),
    input: { mx: 0, my: 0, aim: 0, fire: 0, dash: 0, ult: 0, grap: 0, pick: 0, buy: 0, ready: 0 },
    shopOpen: false,
  };

  const canvas = document.getElementById('game');
  const renderer = new Render.Renderer(canvas);
  const miniCtx = document.getElementById('minimap').getContext('2d');

  /* ── input ───────────────────────────────────────────── */
  const KEYMAP = {
    KeyW: 'up', ArrowUp: 'up', KeyS: 'down', ArrowDown: 'down',
    KeyA: 'left', ArrowLeft: 'left', KeyD: 'right', ArrowRight: 'right',
  };

  addEventListener('keydown', (e) => {
    if (e.code === 'Escape') { togglePause(); e.preventDefault(); return; }
    if (e.code === 'KeyM') { UI.toast(Audio.toggleMute() ? 'MUTED' : 'SOUND ON', 1200); return; }
    if (e.code === 'KeyF') { toggleFullscreen(); return; }
    if (document.activeElement && document.activeElement.tagName === 'INPUT') return;
    if (state.shopOpen) {
      if (e.code === 'Digit1') { state.pendPick = 1; e.preventDefault(); }
      if (e.code === 'Digit2') { state.pendPick = 2; e.preventDefault(); }
      if (e.code === 'Digit3') { state.pendPick = 3; e.preventDefault(); }
      if (e.code === 'Space') { state.readyHeld = !state.readyHeld; e.preventDefault(); }
    }
    state.keys[e.code] = 1;
    if (KEYMAP[e.code] || ['Space', 'ShiftLeft', 'ShiftRight', 'KeyQ', 'KeyE', 'Tab'].includes(e.code)) e.preventDefault();
  });
  addEventListener('keyup', (e) => { state.keys[e.code] = 0; });
  addEventListener('blur', () => { state.keys = Object.create(null); state.mouse.down = false; });

  canvas.addEventListener('mousemove', (e) => { state.mouse.x = e.clientX; state.mouse.y = e.clientY; });
  canvas.addEventListener('mousedown', (e) => {
    Audio.init();
    if (e.button === 0) state.mouse.down = true;
    if (e.button === 2) state.mouse.right = true;
  });
  addEventListener('mouseup', (e) => {
    if (e.button === 0) state.mouse.down = false;
    if (e.button === 2) state.mouse.right = false;
  });
  canvas.addEventListener('contextmenu', (e) => e.preventDefault());
  addEventListener('resize', () => renderer.resize());

  function readGamepad(inp) {
    const pads = navigator.getGamepads ? navigator.getGamepads() : [];
    for (const p of pads) {
      if (!p) continue;
      const dz = (v) => (Math.abs(v) < 0.18 ? 0 : v);
      const lx = dz(p.axes[0] || 0), ly = dz(p.axes[1] || 0);
      const rx = dz(p.axes[2] || 0), ry = dz(p.axes[3] || 0);
      if (lx || ly) { inp.mx = lx; inp.my = ly; }
      if (rx || ry) inp.aim = Math.atan2(ry, rx);
      const btn = (i) => p.buttons[i] && p.buttons[i].pressed;
      if (btn(7) || btn(5)) inp.fire = 1;
      if (btn(6) || btn(4) || btn(0)) inp.dash = 1;
      if (btn(3)) inp.ult = 1;
      if (btn(2)) inp.grap = 1;
      break;
    }
  }

  function gatherInput() {
    const i = state.input;
    // shop actions are edge-triggered: set for one frame, then cleared
    i.pick = state.pendPick || 0;
    i.buy = state.pendBuy || 0;
    state.pendPick = 0; state.pendBuy = 0;
    i.ready = state.readyHeld ? 1 : 0;
    i.mx = (state.keys.KeyD || state.keys.ArrowRight ? 1 : 0) - (state.keys.KeyA || state.keys.ArrowLeft ? 1 : 0);
    i.my = (state.keys.KeyS || state.keys.ArrowDown ? 1 : 0) - (state.keys.KeyW || state.keys.ArrowUp ? 1 : 0);
    // mouse aim, converted from screen space back into the arena
    const z = renderer.cam.zoom;
    const wx = renderer.cam.x + (state.mouse.x - renderer.w / 2) / z;
    const wy = renderer.cam.y + (state.mouse.y - renderer.h / 2) / z;
    state.cursorWorld = { x: wx, y: wy };
    const me = selfPosition();
    if (me) i.aim = Math.atan2(wy - me.y, wx - me.x);
    i.fire = state.mouse.down ? 1 : 0;
    i.dash = (state.keys.ShiftLeft || state.keys.ShiftRight || state.keys.Space || state.mouse.right) ? 1 : 0;
    i.ult = state.keys.KeyQ ? 1 : 0;
    i.grap = state.keys.KeyE ? 1 : 0;
    readGamepad(i);
    return i;
  }

  function selfPosition() {
    if (state.mode === 'client') {
      const p = Net.client.pred;
      return p.on ? p : null;
    }
    const g = state.game;
    if (!g) return null;
    const p = g.players.get(state.selfId);
    return p && p.alive ? p.body : (p ? { x: renderer.cam.tx, y: renderer.cam.ty } : null);
  }

  /* ── modes ───────────────────────────────────────────── */
  function newGame() {
    const g = new Game({ seed: (Math.random() * 1e9) | 0 });
    state.game = g;
    state.arena = g.arena();
    renderer.arena = { w: state.arena.w, h: state.arena.h };
    state.renderFx = 0; state.netFx = 0;
    state.resultShown = false;
    return g;
  }

  function startSolo() {
    Audio.init(); Audio.music('../assets/henry_theme.mp3');
    const g = newGame();
    state.mode = 'solo';
    state.selfId = 1;
    g.addPlayer(1, UI.name());
    state.running = true;
    state.paused = false;
    UI.show(null);
    UI.toast('COLLECT THE ANGELOS. BREAK THE RULE.', 3200);
    focusSelf(true);
  }

  async function startHost() {
    Audio.init(); Audio.music('../assets/henry_theme.mp3');
    const g = newGame();
    state.mode = 'host';
    state.selfId = 1;
    g.addPlayer(1, UI.name());
    state.running = false;      // lobby until the host starts the war
    UI.show('host');
    UI.hostInfo({ status: 'starting…', port: 47861, peers: 1 });
    refreshRoster();

    if (!Net.available) {
      UI.hostInfo({ status: 'no network layer — run the desktop app for multiplayer', addr: 'local only', port: '—', peers: 1 });
      return;
    }
    const res = await Net.host(47861, UI.name() + "'S WAR");
    if (!res.ok) {
      UI.hostInfo({ status: 'failed: ' + (res.reason || 'unknown'), addr: '—', port: 47861, peers: 1 });
      return;
    }
    UI.hostInfo({ status: 'waiting for rebels', addr: (res.ips && res.ips[0]) || 'localhost', port: res.port, peers: 1 });
  }

  function hostStart() {
    state.running = true;
    state.paused = false;
    UI.show(null);
    focusSelf(true);
    broadcast({ t: 'go' });
  }

  async function openJoin() {
    UI.show('join');
    UI.joinError(Net.available ? '' : 'multiplayer needs the desktop app (browser builds are solo)');
    rescan();
  }

  async function rescan() {
    if (!Net.available) { UI.servers([], () => {}); return; }
    const list = await Net.discover(1000);
    UI.servers(list, (s) => joinGame(s.ip, s.port));
  }

  async function joinGame(host, port) {
    if (!Net.available) { UI.joinError('multiplayer needs the desktop app'); return; }
    UI.joinError('connecting to ' + host + ':' + port + '…');
    const res = await Net.join(host, port, UI.name());
    if (!res.ok) { UI.joinError('could not connect: ' + (res.reason || 'refused')); return; }
    Audio.init(); Audio.music('../assets/henry_theme.mp3');
    state.mode = 'client';
    state.running = true;
    state.paused = false;
    state.game = null;
    state.resultShown = false;
    Net.client = new NetMod.Client();
    Net.send({ t: 'hello', name: UI.name() });
    UI.show(null);
    UI.toast('CONNECTED — WAITING FOR THE HOST', 2600);
  }

  function leave() {
    if (state.mode === 'client') Net.send({ t: 'bye' });
    Net.stop();
    state.mode = 'menu';
    state.running = false;
    state.game = null;
    state.peerPlayers.clear();
    UI.show('title');
  }

  function again() {
    if (state.mode === 'client') { UI.toast('WAITING FOR THE HOST', 2000); UI.show(null); return; }
    const g = state.game;
    const names = [...g.players.values()].map((p) => ({ id: p.id, name: p.name }));
    g.reset((Math.random() * 1e9) | 0);
    for (const n of names) { const p = g.players.get(n.id); if (p) g.spawnPlayer(p, true); }
    state.arena = g.arena();
    renderer.arena = { w: state.arena.w, h: state.arena.h };
    state.resultShown = false;
    state.running = true;
    UI.show(null);
    broadcast({ t: 'welcome-again', arena: state.arena });
  }

  function togglePause() {
    if (state.mode === 'menu') return;
    if (UI.screen === 'result') return;
    if (state.paused) { state.paused = false; UI.show(null); }
    else { state.paused = true; UI.show('pause'); }
  }

  function toggleFullscreen() {
    if (!document.fullscreenElement) document.documentElement.requestFullscreen().catch(() => {});
    else document.exitFullscreen().catch(() => {});
  }

  function focusSelf(snap) {
    const me = selfPosition();
    if (me) renderer.focus(me.x, me.y, snap);
  }

  function refreshRoster() {
    const g = state.game;
    if (!g) return;
    const list = [...g.players.values()].map((p) => ({ name: p.name, hue: p.hue, host: p.id === 1 }));
    UI.roster(list);
    UI.hostInfo({ peers: list.length });
  }

  /* ── networking glue ─────────────────────────────────── */
  function broadcast(obj) { if (state.mode === 'host' && Net.available) Net.send(obj); }

  Net.on('join', (ev) => {
    if (state.mode !== 'host') return;
    UI.toast('A REBEL IS CONNECTING…', 1800);
  });

  Net.on('leave', (ev) => {
    if (state.mode === 'host') {
      const pid = state.peerPlayers.get(ev.peer);
      if (pid && state.game) state.game.removePlayer(pid);
      state.peerPlayers.delete(ev.peer);
      refreshRoster();
      UI.toast('A REBEL LEFT', 1600);
    }
  });

  Net.on('closed', () => {
    if (state.mode === 'client') { UI.toast('HOST CLOSED THE WAR', 3000); leave(); }
  });

  Net.on('msg', (ev) => {
    const m = ev.data;
    if (!m || !m.t) return;
    if (state.mode === 'host') {
      if (m.t === 'hello') {
        const pid = 100 + (ev.peer % 1000);
        state.peerPlayers.set(ev.peer, pid);
        const p = state.game.addPlayer(pid, m.name || 'REBEL');
        Net.sendTo(ev.peer, { t: 'welcome', id: pid, arena: state.arena, running: state.running });
        refreshRoster();
        UI.toast(p.name + ' JOINED THE UPRISING', 2400);
      } else if (m.t === 'in') {
        const pid = state.peerPlayers.get(ev.peer);
        if (pid) state.game.setInput(pid, m);
      } else if (m.t === 'bye') {
        const pid = state.peerPlayers.get(ev.peer);
        if (pid) state.game.removePlayer(pid);
        state.peerPlayers.delete(ev.peer);
        refreshRoster();
      }
    } else if (state.mode === 'client') {
      if (m.t === 'welcome' || m.t === 'welcome-again') {
        if (m.id) state.selfId = m.id;
        state.arena = m.arena;
        renderer.arena = { w: m.arena.w, h: m.arena.h };
        state.resultShown = false;
        UI.show(null);
      } else if (m.t === 'snap') {
        Net.client.push(m.s);
      } else if (m.t === 'go') {
        UI.toast('THE WAR BEGINS', 2000);
      }
    }
  });

  /* ── effects → sound ─────────────────────────────────── */
  const FX_SOUND = {
    shot: 'shot', hit: 'hit', crit: 'hit', pop: 'pop', boom: 'boom', burst: 'boom',
    pickup: 'pickup', ult: 'ult', dash: 'dash', hurt: 'hurt', down: 'down',
    wave: 'wave', henrywave: 'boom', victory: 'victory', defeat: 'defeat',
  };
  let lastSound = 0;
  function playFx(list) {
    const now = performance.now();
    for (const f of list || []) {
      const s = FX_SOUND[f.t];
      if (!s) continue;
      if (s === 'shot' && now - lastSound < 40) continue;
      if (s === 'shot') lastSound = now;
      Audio.play(s);
    }
  }

  /* ── loop ────────────────────────────────────────────── */
  function frame(now) {
    requestAnimationFrame(frame);
    const dt = Math.min(0.05, (now - state.last) / 1000);
    state.last = now;

    if (state.mode === 'menu') {
      renderer.draw({ P: [], E: [], B: [], O: [], L: [] }, { dt });
      return;
    }

    const input = gatherInput();

    if (state.mode === 'solo' || state.mode === 'host') {
      const g = state.game;
      if (g && state.running && !state.paused) {
        g.setInput(state.selfId, input);
        state.acc += dt;
        let steps = 0;
        while (state.acc >= K.TICK && steps < 5) { g.step(K.TICK); state.acc -= K.TICK; steps++; }
        if (steps === 5) state.acc = 0;
      }
      if (g) {
        const view = g.snapshot(state.renderFx);
        state.renderFx = view.fs;
        view.pillars = state.arena.pillars;
        view.walls = state.arena.walls;
        state.view = view;
        renderer.consume(view.F);
        playFx(view.F);

        // ship snapshots to the clients at a steady 30Hz
        if (state.mode === 'host' && Net.available) {
          state.snapAcc += dt;
          if (state.snapAcc >= 1 / NetMod.SNAP_HZ) {
            state.snapAcc = 0;
            const snap = g.snapshot(state.netFx);
            state.netFx = snap.fs;
            broadcast({ t: 'snap', s: snap });
          }
        }
        const me = g.players.get(state.selfId);
        if (me && me.alive) renderer.focus(me.body.x, me.body.y);
        renderer.draw(view, { dt, selfId: state.selfId, cursor: state.cursorWorld });
        UI.hud(view, state.selfId, netLabel());
        renderer.drawMinimap(miniCtx, view, 220, 142);
        syncShop(view);
        checkResult(view);
      }
    } else if (state.mode === 'client') {
      // input up at 60Hz, view down at 30Hz
      state.inputAcc += dt;
      if (state.inputAcc >= 1 / 60) {
        state.inputAcc = 0;
        Net.send({ t: 'in', mx: input.mx, my: input.my, aim: input.aim, fire: input.fire,
          dash: input.dash, ult: input.ult, grap: input.grap,
          pick: input.pick, buy: input.buy, ready: input.ready });
      }
      const view = Net.client.sample(dt);
      if (view) {
        view.pillars = state.arena ? state.arena.pillars : [];
        view.walls = state.arena ? state.arena.walls : [];
        state.view = view;
        const fx = Net.client.fx.splice(0, Net.client.fx.length);
        renderer.consume(fx);
        playFx(fx);
        for (const p of view.P || []) {
          if (p[0] === state.selfId) { Net.client.reconcile(p[1], p[2], p[3], p[4]); break; }
        }
        const pred = Net.client.predict(input, dt, K);
        if (pred) renderer.focus(pred.x, pred.y);
        renderer.draw(view, { dt, selfId: state.selfId, predicted: pred, cursor: state.cursorWorld });
        UI.hud(view, state.selfId, netLabel());
        renderer.drawMinimap(miniCtx, view, 220, 142);
        syncShop(view);
        checkResult(view);
      } else {
        renderer.draw({ P: [], E: [], B: [], O: [], L: [] }, { dt });
      }
    }
  }

  function netLabel() {
    if (state.mode === 'host') return 'HOSTING · ' + (state.game ? state.game.players.size : 1) + ' REBELS';
    if (state.mode === 'client') return 'CONNECTED · ' + ((Net.client.latest && Net.client.latest.P) ? Net.client.latest.P.length : 1) + ' REBELS';
    return 'SOLO';
  }

  /** The shop screen is owned by the simulation's phase, not by a click. */
  function syncShop(view) {
    const wantShop = view.ph === 'shop' && !state.paused && UI.screen !== 'result';
    if (wantShop && !state.shopOpen) {
      state.shopOpen = true;
      state.readyHeld = false;
      UI.show('shop');
    } else if (!wantShop && state.shopOpen) {
      state.shopOpen = false;
      state.readyHeld = false;
      if (UI.screen === 'shop') UI.show(null);
    }
    if (state.shopOpen) {
      UI.shop(view, state.selfId, Sim.UPGRADES,
        (n) => { state.pendPick = n; },
        (n) => { state.pendBuy = n; });
    }
  }

  function checkResult(view) {
    if (state.resultShown) return;
    if (view.ph !== 'victory' && view.ph !== 'defeat') return;
    state.resultShown = true;
    const stats = (view.P || []).map((p) => ({
      name: p[13], hue: p[14], score: p[8], angelos: p[9], kills: 0,
    })).sort((a, b) => b.score - a.score);
    if (state.game) {
      const s = state.game.stats();
      UI.result(view.ph === 'victory' ? "HENRY'S RULE IS BROKEN" : 'THE RULE HOLDS', s);
    } else {
      UI.result(view.ph === 'victory' ? "HENRY'S RULE IS BROKEN" : 'THE RULE HOLDS', stats);
    }
    UI.show('result');
  }

  /* ── boot ────────────────────────────────────────────── */
  UI.init({
    solo: startSolo,
    host: startHost,
    hostStart,
    join: openJoin,
    joinGo: joinGame,
    rescan,
    leave,
    resume: () => togglePause(),
    again,
    volume: (v) => Audio.setVolume(v),
    ready: () => { state.readyHeld = !state.readyHeld; },
  });

  Render.Assets.load('../assets/').then(() => {
    document.getElementById('build-note').textContent =
      (Net.available ? 'DESKTOP BUILD — LAN MULTIPLAYER READY' : 'BROWSER BUILD — SOLO ONLY');
  });

  UI.show('title');
  renderer.focus(K.ARENA_W / 2, K.ARENA_H / 2, true);
  requestAnimationFrame(frame);

  global.WARDEBUG = state;
})(typeof globalThis !== 'undefined' ? globalThis : this);
