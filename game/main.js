/* WAR AGAINST HENRY ANGELOS — Electron main process.

   Owns the window and all networking. Multiplayer is plain TCP between Macs
   (newline-delimited JSON) plus a small UDP responder so games on the same
   Wi-Fi show up in the join list without anyone typing an IP address. */
'use strict';

const { app, BrowserWindow, ipcMain, Menu, shell } = require('electron');
const path = require('path');
const net = require('net');
const {
  MAGIC, GAME_PORT, DISCOVERY_PORT, attach, write, localIPs, broadcastAddresses,
  startResponder, discover,
} = require('./netcore');

let win = null;
let server = null;            // TCP server when hosting
let discovery = null;         // UDP socket when hosting
let client = null;            // TCP socket when joining
const peers = new Map();      // peerId -> socket
let peerSeq = 0;
let hostName = 'HENRY ANGELOS WAR';

function createWindow() {
  win = new BrowserWindow({
    width: 1440, height: 900, minWidth: 1024, minHeight: 640,
    backgroundColor: '#05050a',
    title: 'War Against Henry Angelos',
    show: false,
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
      backgroundThrottling: false,
    },
  });
  win.once('ready-to-show', () => win.show());
  win.loadFile(path.join(__dirname, 'renderer', 'index.html'));
  win.webContents.setWindowOpenHandler(({ url }) => { shell.openExternal(url); return { action: 'deny' }; });
}

function buildMenu() {
  const isMac = process.platform === 'darwin';
  const template = [
    ...(isMac ? [{
      label: app.name,
      submenu: [{ role: 'about' }, { type: 'separator' }, { role: 'hide' }, { role: 'hideOthers' },
        { type: 'separator' }, { role: 'quit' }],
    }] : []),
    {
      label: 'Game',
      submenu: [
        { label: 'Reload', accelerator: 'CmdOrCtrl+R', click: () => win && win.reload() },
        { label: 'Toggle Full Screen', accelerator: isMac ? 'Ctrl+Cmd+F' : 'F11',
          click: () => win && win.setFullScreen(!win.isFullScreen()) },
        { type: 'separator' },
        { label: 'Developer Tools', accelerator: isMac ? 'Alt+Cmd+I' : 'Ctrl+Shift+I',
          click: () => win && win.webContents.toggleDevTools() },
        ...(isMac ? [] : [{ role: 'quit' }]),
      ],
    },
    { label: 'Edit', submenu: [{ role: 'undo' }, { role: 'redo' }, { type: 'separator' },
      { role: 'cut' }, { role: 'copy' }, { role: 'paste' }, { role: 'selectAll' }] },
  ];
  Menu.setApplicationMenu(Menu.buildFromTemplate(template));
}

function send(ev, data) {
  if (win && !win.isDestroyed()) win.webContents.send('war:event', Object.assign({ type: ev }, data));
}

function stopAll() {
  for (const [, s] of peers) { try { s.destroy(); } catch (e) {} }
  peers.clear();
  if (server) { try { server.close(); } catch (e) {} server = null; }
  if (discovery) { try { discovery.close(); } catch (e) {} discovery = null; }
  if (client) { try { client.destroy(); } catch (e) {} client = null; }
}

ipcMain.handle('war:host', async (_e, port, name) => {
  stopAll();
  hostName = name || hostName;
  return new Promise((resolve) => {
    server = net.createServer((socket) => {
      const id = ++peerSeq;
      peers.set(id, socket);
      send('join', { peer: id, ip: socket.remoteAddress });
      attach(socket, (msg) => send('msg', { peer: id, data: msg }), () => {
        peers.delete(id);
        send('leave', { peer: id });
      });
    });
    server.on('error', (err) => resolve({ ok: false, reason: err.code || String(err) }));
    server.listen(port || GAME_PORT, () => {
      // answer LAN scans so other Macs see this game without typing an IP
      discovery = startResponder(() => ({
        name: hostName, port: port || GAME_PORT, players: peers.size + 1,
      }), DISCOVERY_PORT);
      resolve({ ok: true, port: server.address().port, ips: localIPs() });
    });
  });
});

ipcMain.handle('war:join', async (_e, host, port) => {
  stopAll();
  return new Promise((resolve) => {
    const socket = net.connect({ host, port: port || GAME_PORT }, () => {
      client = socket;
      resolve({ ok: true });
    });
    const fail = (err) => resolve({ ok: false, reason: (err && err.code) || 'unreachable' });
    socket.once('error', fail);
    socket.setTimeout(6000, () => { socket.destroy(); fail({ code: 'timeout' }); });
    attach(socket, (msg) => send('msg', { peer: 0, data: msg }), () => {
      if (client === socket) { client = null; send('closed', {}); }
    });
    socket.on('connect', () => socket.setTimeout(0));
  });
});

ipcMain.on('war:send', (_e, obj) => {
  if (client) write(client, obj);
  else for (const [, s] of peers) write(s, obj);
});

ipcMain.on('war:sendTo', (_e, peer, obj) => {
  const s = peers.get(peer);
  if (s) write(s, obj);
});

ipcMain.on('war:stop', () => stopAll());

ipcMain.handle('war:ips', async () => localIPs());

ipcMain.handle('war:discover', async (_e, ms) => discover(ms));

app.whenReady().then(() => {
  buildMenu();
  createWindow();
  app.on('activate', () => { if (BrowserWindow.getAllWindows().length === 0) createWindow(); });
});

app.on('window-all-closed', () => { stopAll(); if (process.platform !== 'darwin') app.quit(); });
app.on('before-quit', stopAll);
