/* Transport primitives shared by the Electron main process and the tests:
   newline-delimited JSON framing, LAN address helpers and the UDP discovery
   protocol. Kept out of main.js so it can be exercised headlessly. */
'use strict';
const os = require('os');
const dgram = require('dgram');

const MAGIC = 'HENRY-ANGELOS-WAR-1';
const GAME_PORT = 47861;
const DISCOVERY_PORT = 47862;

/** Attach a line-framed JSON reader to a socket. */
function attach(socket, onMessage, onClose) {
  socket.setNoDelay(true);
  socket.setEncoding('utf8');
  let buf = '';
  socket.on('data', (chunk) => {
    buf += chunk;
    let i;
    while ((i = buf.indexOf('\n')) >= 0) {
      const line = buf.slice(0, i);
      buf = buf.slice(i + 1);
      if (!line) continue;
      try { onMessage(JSON.parse(line)); } catch (e) { /* malformed frame */ }
    }
    if (buf.length > 4 * 1024 * 1024) buf = '';
  });
  socket.on('error', () => { try { socket.destroy(); } catch (e) {} });
  if (onClose) socket.on('close', onClose);
  return socket;
}

function write(socket, obj) {
  if (!socket || socket.destroyed) return false;
  try { socket.write(JSON.stringify(obj) + '\n'); return true; } catch (e) { return false; }
}

function localIPs() {
  const out = [];
  const ifaces = os.networkInterfaces();
  for (const name of Object.keys(ifaces)) {
    for (const i of ifaces[name] || []) if (i.family === 'IPv4' && !i.internal) out.push(i.address);
  }
  return out;
}

function broadcastAddresses() {
  const out = ['255.255.255.255'];
  const ifaces = os.networkInterfaces();
  for (const name of Object.keys(ifaces)) {
    for (const i of ifaces[name] || []) {
      if (i.family !== 'IPv4' || i.internal) continue;
      const ip = i.address.split('.').map(Number);
      const mask = (i.netmask || '255.255.255.0').split('.').map(Number);
      out.push(ip.map((v, k) => (v & mask[k]) | (~mask[k] & 255)).join('.'));
    }
  }
  return [...new Set(out)];
}

/** Host side: answer "is there a game here?" probes. */
function startResponder(info, port) {
  const sock = dgram.createSocket({ type: 'udp4', reuseAddr: true });
  sock.on('message', (msg, rinfo) => {
    if (String(msg) !== MAGIC + '?') return;
    const payload = Buffer.from(JSON.stringify(Object.assign({ magic: MAGIC }, info())));
    sock.send(payload, rinfo.port, rinfo.address);
  });
  sock.on('error', () => {});
  sock.bind(port || DISCOVERY_PORT, () => { try { sock.setBroadcast(true); } catch (e) {} });
  return sock;
}

/** Client side: shout on the LAN and collect whoever answers. */
function discover(ms, port, extraTargets) {
  return new Promise((resolve) => {
    const sock = dgram.createSocket({ type: 'udp4', reuseAddr: true });
    const found = new Map();
    sock.on('message', (msg, rinfo) => {
      try {
        const info = JSON.parse(String(msg));
        if (info && info.magic === MAGIC) {
          found.set(rinfo.address + ':' + info.port,
            { ip: rinfo.address, port: info.port, name: info.name, players: info.players });
        }
      } catch (e) { /* not ours */ }
    });
    sock.on('error', () => resolve([]));
    sock.bind(() => {
      try { sock.setBroadcast(true); } catch (e) {}
      const probe = Buffer.from(MAGIC + '?');
      const targets = broadcastAddresses().concat(extraTargets || []);
      for (const addr of targets) { try { sock.send(probe, port || DISCOVERY_PORT, addr); } catch (e) {} }
      setTimeout(() => {
        try { sock.close(); } catch (e) {}
        resolve([...found.values()]);
      }, Math.max(200, Math.min(4000, ms || 900)));
    });
  });
}

module.exports = { MAGIC, GAME_PORT, DISCOVERY_PORT, attach, write, localIPs, broadcastAddresses, startResponder, discover };
