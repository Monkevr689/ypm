/* WAR AGAINST HENRY ANGELOS — menus, HUD and everything DOM. */
(function (global) {
  'use strict';

  function build(Core) {
    const $ = (id) => document.getElementById(id);
    const UI = {
      screen: 'title',
      handlers: {},
      init(h) {
        this.handlers = h || {};
        const bind = (id, fn) => { const el = $(id); if (el) el.addEventListener('click', fn); };
        bind('btn-solo', () => h.solo());
        bind('btn-host', () => h.host());
        bind('btn-join', () => h.join());
        bind('btn-help', () => this.show('help'));
        bind('btn-help-back', () => this.show('title'));
        bind('btn-host-start', () => h.hostStart());
        bind('btn-host-back', () => h.leave());
        bind('btn-join-go', () => h.joinGo($('join-host').value.trim(), parseInt($('join-port').value, 10) || 47861));
        bind('btn-join-rescan', () => h.rescan());
        bind('btn-join-back', () => h.leave());
        bind('btn-ready', () => h.ready());
        bind('btn-resume', () => h.resume());
        bind('btn-quit', () => h.leave());
        bind('btn-again', () => h.again());
        bind('btn-result-menu', () => h.leave());
        const vol = $('vol-slider');
        if (vol) vol.addEventListener('input', () => h.volume(parseInt(vol.value, 10) / 100));
        const nameEl = $('name-input');
        if (nameEl) {
          nameEl.value = (localStorage.getItem('war_name') || 'REBEL').toUpperCase();
          nameEl.addEventListener('change', () => localStorage.setItem('war_name', nameEl.value.toUpperCase()));
        }
      },
      name() { const el = $('name-input'); return ((el && el.value) || 'REBEL').toUpperCase().slice(0, 14); },

      show(name) {
        this.screen = name;
        for (const s of document.querySelectorAll('.screen')) s.classList.remove('active');
        const ov = $('overlay'), hud = $('hud');
        if (!name) { ov.classList.add('hidden'); hud.classList.remove('hidden'); return; }
        ov.classList.remove('hidden');
        const el = $('screen-' + name);
        if (el) el.classList.add('active');
        hud.classList.toggle('hidden', name === 'title' || name === 'host' || name === 'join' || name === 'help');
      },

      toast(msg, ms) {
        const t = $('toast');
        t.textContent = msg;
        t.classList.add('show');
        clearTimeout(this._tt);
        this._tt = setTimeout(() => t.classList.remove('show'), ms || 2600);
      },

      hostInfo(info) {
        $('host-status').textContent = info.status || '';
        $('host-addr').textContent = info.addr || '—';
        $('host-port').textContent = info.port || 47861;
        $('host-peers').textContent = info.peers != null ? info.peers : 1;
      },
      roster(list) {
        const ul = $('host-roster');
        ul.innerHTML = '';
        for (const r of list) {
          const li = document.createElement('li');
          li.innerHTML = `<span style="color:${r.hue || '#fff'}">${esc(r.name)}</span><span>${r.host ? 'HOST' : 'READY'}</span>`;
          ul.appendChild(li);
        }
      },
      servers(list, onPick) {
        const ul = $('server-list');
        ul.innerHTML = '';
        if (!list.length) {
          const li = document.createElement('li');
          li.className = 'dim';
          li.textContent = 'no games found — enter an address below';
          ul.appendChild(li);
          return;
        }
        for (const s of list) {
          const li = document.createElement('li');
          li.innerHTML = `<span>${esc(s.name || 'HENRY ANGELOS WAR')}</span><span>${esc(s.ip)}:${s.port} · ${s.players || 1} REBELS</span>`;
          li.addEventListener('click', () => onPick(s));
          ul.appendChild(li);
        }
      },
      joinError(msg) { $('join-error').textContent = msg || ''; },

      result(title, stats) {
        $('result-title').textContent = title;
        const ul = $('result-stats');
        ul.innerHTML = '';
        for (const s of stats) {
          const li = document.createElement('li');
          li.innerHTML = `<span style="color:${s.hue}">${esc(s.name)}</span>` +
            `<span>${s.score} PTS · ${s.angelos} ANGELOS · ${s.kills} POPPED</span>`;
          ul.appendChild(li);
        }
      },

      /** Renders the between-wave upgrade screen from snapshot data alone,
          so a connected client sees exactly what the host thinks it has. */
      shop(view, selfId, UPGRADES, onPick, onBuy) {
        const me = (view.P || []).find((r) => r[0] === selfId);
        if (!me) return;
        const offers = me[17] || [], up = me[18] || {}, bank = me[19] || 0;
        const picked = me[20], ready = me[21];

        $('shop-bank').textContent = bank;
        $('shop-timer').textContent = view.wt > 0 ? Math.ceil(view.wt) + 's' : '';

        const cards = $('shop-cards');
        if (this._cardsKey !== JSON.stringify([offers, picked])) {
          this._cardsKey = JSON.stringify([offers, picked]);
          cards.innerHTML = '';
          if (picked || !offers.length) {
            const d = document.createElement('div');
            d.className = 'lede';
            d.textContent = picked ? 'Card taken. Spend your Angelos below, then READY.'
                                   : 'Nothing left to learn — you have it all.';
            cards.appendChild(d);
          } else {
            offers.forEach((id, i) => {
              const u = UPGRADES[id]; if (!u) return;
              const el = document.createElement('div');
              el.className = 'card';
              el.innerHTML = `<div class="key">PRESS ${i + 1}</div>` +
                `<div class="nm">${esc(u.name)}</div>` +
                `<div class="ds">${esc(u.desc)}</div>` +
                `<div class="lvl">${(up[id] || 0)} / ${u.max}</div>`;
              el.addEventListener('click', () => onPick(i + 1));
              cards.appendChild(el);
            });
          }
        }

        const stock = $('shop-stock');
        const stockKey = JSON.stringify([view.st, bank, up]);
        if (this._stockKey !== stockKey) {
          this._stockKey = stockKey;
          stock.innerHTML = '';
          (view.st || []).forEach((it, i) => {
            const u = UPGRADES[it.id]; if (!u) return;
            const maxed = (up[it.id] || 0) >= u.max;
            const broke = bank < it.cost || maxed;
            const el = document.createElement('div');
            el.className = 'buy' + (broke ? ' broke' : '');
            el.innerHTML = `<div class="bn">${esc(u.name)}</div>` +
              `<div class="bd">${esc(u.desc)}</div>` +
              `<div class="bc">${maxed ? 'MAXED' : '◈ ' + it.cost}</div>`;
            if (!broke) el.addEventListener('click', () => onBuy(i + 1));
            stock.appendChild(el);
          });
        }

        const owned = $('shop-owned');
        const ownKey = JSON.stringify(up);
        if (this._ownKey !== ownKey) {
          this._ownKey = ownKey;
          owned.innerHTML = Object.keys(up).map((id) =>
            `<span>${esc((UPGRADES[id] || {}).name || id)} ×${up[id]}</span>`).join('');
        }

        const rb = $('btn-ready');
        rb.textContent = ready ? 'READY ✓' : 'READY';
        rb.className = ready ? 'primary readied' : 'primary';
      },

      hud(view, selfId, netLabel) {
        if (!view) return;
        $('wave-label').textContent = view.ph === 'intermission'
          ? 'WAVE ' + (view.wv + 1) + ' INCOMING'
          : 'WAVE ' + view.wv;
        $('banner').textContent = view.bn || '';
        $('angelos-count').textContent = view.co;
        $('angelos-need').textContent = '/ ' + view.nd + ' ANGELOS';
        $('wave-timer').textContent = view.ph === 'intermission' && view.wt > 0
          ? 'NEXT WAVE IN ' + Math.ceil(view.wt) : '';
        const lives = $('lives');
        if (lives) lives.textContent = '♥ '.repeat(Math.max(0, view.lv || 0)).trim();

        let me = null;
        const rows = (view.P || []).slice().sort((a, b) => b[8] - a[8]);
        for (const p of rows) if (p[0] === selfId) me = p;
        if (me) {
          const hp = Math.max(0, me[6]);
          const maxHp = me[22] || 100;
          $('hp-bar').style.width = (hp / maxHp * 100) + '%';
          $('hp-bar').style.background = hp > 40
            ? 'linear-gradient(90deg,#3cff9e,#2fe6ff)' : 'linear-gradient(90deg,#ff2f3c,#ff2fd0)';
          $('hp-text').textContent = me[7] ? hp + ' / ' + maxHp : 'DOWN';
          const dash = $('dash-pip');
          dash.className = 'pip ' + (me[10] ? 'cool' : 'ready');
          const ult = $('ult-pips');
          ult.className = 'pip ' + (me[11] > 0 ? 'ready' : 'cool');
          ult.textContent = 'VERITY ' + '★'.repeat(me[11]) + (me[11] ? '' : ' 0');
        }
        const sb = $('scoreboard');
        sb.innerHTML = rows.map((p) =>
          `<div class="row-s ${p[7] ? '' : 'dead'}"><span class="nm" style="color:${p[14]}">${esc(p[13] || '')}</span>` +
          `<span>${p[9]}◈ ${p[8]}</span></div>`).join('');
        $('netstatus').textContent = netLabel;
      },
    };

    function esc(s) {
      return String(s == null ? '' : s).replace(/[&<>"']/g, (c) =>
        ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
    }

    return UI;
  }

  if (typeof module !== 'undefined' && module.exports) module.exports = build(require('./core.js'));
  else (global.WAR = global.WAR || {}).UI = build(global.WAR.Core);
})(typeof globalThis !== 'undefined' ? globalThis : this);
