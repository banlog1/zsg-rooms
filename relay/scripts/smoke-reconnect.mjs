import WebSocket from "ws";

const relayUrl = (process.argv[2] || "https://zsg-rooms-relay.sashko-ato.workers.dev")
  .replace(/^http/, "ws")
  .replace(/\/$/, "");
const roomCode = `RECONNECT-${crypto.randomUUID().slice(0, 8)}`.toUpperCase();

class TestSocket {
  constructor(socket) {
    this.socket = socket;
    this.messages = [];
    this.waiters = [];
    socket.addEventListener("message", (event) => {
      const value = JSON.parse(event.data);
      const waiter = this.waiters.shift();
      if (waiter) {
        waiter.resolve(value);
      } else {
        this.messages.push(value);
      }
    });
  }

  static connect(url, authorization) {
    return new Promise((resolve, reject) => {
      const headers = authorization ? { Authorization: `Bearer ${authorization}` } : undefined;
      const socket = new WebSocket(url, { headers });
      socket.addEventListener("open", () => resolve(new TestSocket(socket)), { once: true });
      socket.addEventListener("error", () => reject(new Error(`Could not connect to ${url}`)), { once: true });
    });
  }

  next(timeoutMillis = 10000) {
    if (this.messages.length > 0) {
      return Promise.resolve(this.messages.shift());
    }
    return new Promise((resolve, reject) => {
      const waiter = { resolve, reject };
      this.waiters.push(waiter);
      const timeout = setTimeout(() => {
        const index = this.waiters.indexOf(waiter);
        if (index >= 0) {
          this.waiters.splice(index, 1);
        }
        reject(new Error("Timed out waiting for a relay message"));
      }, timeoutMillis);
      waiter.resolve = (value) => {
        clearTimeout(timeout);
        resolve(value);
      };
    });
  }

  send(value) {
    this.socket.send(JSON.stringify(value));
  }

  close(code = 1000) {
    if (this.socket.readyState < WebSocket.CLOSING) {
      this.socket.close(code, "relay smoke test");
    }
  }
}

function roomUrl(role, player) {
  const url = new URL(`${relayUrl}/room/${roomCode}`);
  url.searchParams.set("role", role);
  url.searchParams.set("player", player);
  return url;
}

function requireMessage(condition, message) {
  if (!condition) {
    throw new Error(message);
  }
}

let host;
let guest;
let reconnectedHost;
try {
  const hostToken = `${crypto.randomUUID()}${crypto.randomUUID()}`.replaceAll("-", "");
  host = await TestSocket.connect(roomUrl("host", "Host"), hostToken);
  const welcome = await host.next();
  requireMessage(welcome.type === "welcome", "Host did not receive a welcome message");

  host.send({ type: "snapshot", value: JSON.stringify({ roomCode, players: [] }) });
  guest = await TestSocket.connect(roomUrl("guest", "Guest"));
  requireMessage((await guest.next()).type === "welcome", "Guest did not receive a welcome message");
  const join = await host.next();
  requireMessage(join.type === "join_room" && join.player === "Guest", "Host did not receive guest join");

  host.close(1011);
  host = undefined;
  const waiting = await guest.next();
  requireMessage(
    waiting.type === "relay_status" && waiting.value?.includes("60 seconds"),
    `Guest did not receive the reconnect grace status: ${JSON.stringify(waiting)}`,
  );

  reconnectedHost = await TestSocket.connect(roomUrl("host", "Host"), hostToken);
  requireMessage((await reconnectedHost.next()).type === "welcome", "Reconnected host did not receive a welcome message");
  requireMessage((await reconnectedHost.next()).type === "snapshot", "Reconnected host did not receive the stored snapshot");

  const restored = await guest.next();
  requireMessage(
    restored.type === "relay_status" && restored.value?.includes("reconnected"),
    `Guest did not receive the host reconnected status: ${JSON.stringify(restored)}`,
  );

  console.log(`Relay reconnect smoke test passed for ${roomCode}`);
} finally {
  guest?.close();
  host?.close();
  reconnectedHost?.close();
}
