/* Bridge between the sandboxed renderer and the networking in the main
   process. The renderer gets a small, explicit API — nothing else. */
'use strict';
const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('WARNET', {
  available: true,
  host: (port, name) => ipcRenderer.invoke('war:host', port, name),
  join: (host, port) => ipcRenderer.invoke('war:join', host, port),
  discover: (ms) => ipcRenderer.invoke('war:discover', ms),
  ips: () => ipcRenderer.invoke('war:ips'),
  send: (obj) => ipcRenderer.send('war:send', obj),
  sendTo: (peer, obj) => ipcRenderer.send('war:sendTo', peer, obj),
  stop: () => ipcRenderer.send('war:stop'),
  onEvent: (cb) => ipcRenderer.on('war:event', (_e, data) => cb(data)),
  platform: process.platform,
});
