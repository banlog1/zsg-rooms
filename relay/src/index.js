const MAX_MESSAGE_BYTES = 256 * 1024;
const DISCONNECT_GRACE_MS = 60 * 1000;
const DISCONNECT_STATE_KEY = "disconnectDeadlines";
const GUEST_ACTIONS = new Set([
  "chat",
  "profile",
  "share_seed",
  "seed_change",
  "forfeit",
  "progress",
  "reset_run",
  "advancement",
  "world_ready",
  "leave_room"
]);

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    const match = url.pathname.match(/^\/room\/([A-Za-z0-9_-]{4,64})$/);
    if (!match) {
      return new Response("ZSG Rooms relay", { status: 200 });
    }
    if (request.headers.get("Upgrade")?.toLowerCase() !== "websocket") {
      return new Response("WebSocket upgrade required", { status: 426 });
    }

    const roomCode = match[1];
    const roomId = env.ROOMS.idFromName(roomCode);
    return env.ROOMS.get(roomId).fetch(request);
  }
};

export class RoomSession {
  constructor(ctx, env) {
    this.ctx = ctx;
    this.env = env;
    this.ctx.setWebSocketAutoResponse(
      new WebSocketRequestResponsePair("ping", "pong")
    );
  }

  async fetch(request) {
    const url = new URL(request.url);
    const roomCode = url.pathname.substring(url.pathname.lastIndexOf("/") + 1);
    const role = url.searchParams.get("role");
    const player = cleanPlayer(url.searchParams.get("player"));
    if ((role !== "host" && role !== "guest") || !player) {
      return new Response("Invalid room identity", { status: 400 });
    }

    const pair = new WebSocketPair();
    const [client, server] = Object.values(pair);

    const disconnects = await this.getDisconnects();
    const hostOnline = this.openSockets("host").length > 0;
    const hostInGrace = disconnects.host > Date.now();
    const guestInGrace = (disconnects.guests[player] || 0) > Date.now();

    if (role === "host") {
      const authorization = request.headers.get("Authorization") || "";
      const token = authorization.startsWith("Bearer ") ? authorization.substring(7) : "";
      if (token.length < 24) {
        return new Response("Missing host token", { status: 401 });
      }
      const storedToken = await this.ctx.storage.get("hostToken");
      if (storedToken && storedToken !== token) {
        return new Response("Room code is already owned", { status: 409 });
      }
      if (hostOnline) {
        return new Response("Host is already connected", { status: 409 });
      }
      if (!storedToken) {
        await this.ctx.storage.put({ hostToken: token, hostName: player, roomCode });
      }
    } else {
      if (!hostOnline && !(hostInGrace && guestInGrace)) {
        return new Response("Room host is offline", { status: 404 });
      }
      if (this.findGuest(player)) {
        return new Response("That player is already connected", { status: 409 });
      }
      const maxPlayers = (await this.ctx.storage.get("maxPlayers")) || 8;
      if (this.openSockets("guest").length + 1 >= maxPlayers) {
        return new Response("Room is full", { status: 409 });
      }
    }

    this.ctx.acceptWebSocket(server, [role]);
    server.serializeAttachment({ role, player, roomCode });
    await this.clearDisconnect(role, player);
    server.send(encodeMessage("welcome", roomCode, "relay", role));

    if (role === "host") {
      const snapshot = await this.ctx.storage.get("snapshot");
      if (snapshot) {
        server.send(encodeMessage("snapshot", roomCode, player, snapshot));
      }
      if (hostInGrace) {
        this.broadcastGuests(encodeMessage(
          "relay_status",
          roomCode,
          "relay",
          "Host reconnected"
        ));
      }
      await this.flushExpiredGuestLeaves(roomCode);
    } else {
      if (this.openSockets("host").length > 0) {
        this.sendToHost(encodeMessage("join_room", roomCode, player, ""));
      }
    }

    return new Response(null, { status: 101, webSocket: client });
  }

  async webSocketMessage(ws, rawMessage) {
    const attachment = ws.deserializeAttachment();
    if (!attachment) {
      ws.close(1008, "Missing session identity");
      return;
    }

    const text = typeof rawMessage === "string"
      ? rawMessage
      : new TextDecoder().decode(rawMessage);
    if (new TextEncoder().encode(text).length > MAX_MESSAGE_BYTES) {
      ws.close(1009, "Message too large");
      return;
    }

    let message;
    try {
      message = JSON.parse(text);
    } catch {
      ws.close(1007, "Invalid JSON");
      return;
    }
    if (!message || typeof message.type !== "string") {
      return;
    }

    if (attachment.role === "host") {
      await this.handleHostMessage(attachment, message);
    } else {
      this.handleGuestMessage(attachment, message);
    }
  }

  async handleHostMessage(attachment, message) {
    const normalized = encodeMessage(
      message.type,
      attachment.roomCode,
      message.player || attachment.player,
      typeof message.value === "string" ? message.value : ""
    );

    if (message.type === "snapshot") {
      await this.ctx.storage.put("snapshot", message.value || "");
      try {
        const snapshot = JSON.parse(message.value || "{}");
        if (Number.isInteger(snapshot.maxPlayers)) {
          await this.ctx.storage.put("maxPlayers", Math.max(2, Math.min(100, snapshot.maxPlayers)));
        }
      } catch {
        return;
      }
      this.broadcastGuests(normalized);
      return;
    }

    if (message.type === "error") {
      const guest = this.findGuest(message.player);
      if (guest) {
        guest.send(normalized);
      }
      return;
    }
    if (message.type === "kick") {
      const guest = this.findGuest(message.player);
      if (guest) {
        guest.close(1008, message.value || "Removed from room");
      }
      return;
    }

    this.broadcastGuests(normalized);
  }

  handleGuestMessage(attachment, message) {
    if (!GUEST_ACTIONS.has(message.type)) {
      return;
    }
    this.sendToHost(encodeMessage(
      message.type,
      attachment.roomCode,
      attachment.player,
      typeof message.value === "string" ? message.value : ""
    ));
  }

  async webSocketClose(ws, code, reason) {
    const attachment = ws.deserializeAttachment();
    if (!attachment) {
      return;
    }
    if (attachment.role === "host") {
      if (this.openSockets("host").length > 0) {
        return;
      }
      if (code === 1000) {
        await this.finalizeHostDisconnect(attachment);
        return;
      }
      await this.markDisconnected("host", attachment.player);
      this.broadcastGuests(encodeMessage(
        "relay_status",
        attachment.roomCode,
        "relay",
        "Host connection interrupted - waiting up to 60 seconds"
      ));
    } else {
      if (this.findGuest(attachment.player)) {
        return;
      }
      if (code === 1000) {
        this.notifyGuestLeft(attachment);
        return;
      }
      await this.markDisconnected("guest", attachment.player);
    }
  }

  async alarm() {
    const disconnects = await this.getDisconnects();
    const now = Date.now();

    if (disconnects.host > 0 && disconnects.host <= now) {
      if (this.openSockets("host").length === 0) {
        const roomCode = (await this.ctx.storage.get("roomCode")) || "";
        const hostName = (await this.ctx.storage.get("hostName")) || "Host";
        await this.finalizeHostDisconnect({ roomCode, player: hostName });
        return;
      }
      disconnects.host = 0;
    }

    for (const [player, deadline] of Object.entries(disconnects.guests)) {
      if (deadline <= now) {
        if (!this.findGuest(player)) {
          const roomCode = (await this.ctx.storage.get("roomCode")) || "";
          if (this.openSockets("host").length > 0) {
            this.notifyGuestLeft({ roomCode, player });
          } else if (!disconnects.expiredGuests.includes(player)) {
            disconnects.expiredGuests.push(player);
          }
        }
        delete disconnects.guests[player];
      }
    }

    await this.saveDisconnects(disconnects);
  }

  webSocketError(ws) {
    try {
      ws.close(1011, "Relay connection error");
    } catch {
      // The socket may already be closed.
    }
  }

  sendToHost(message) {
    const hosts = this.openSockets("host");
    if (hosts.length > 0) {
      hosts[0].send(message);
    }
  }

  broadcastGuests(message) {
    for (const guest of this.openSockets("guest")) {
      guest.send(message);
    }
  }

  findGuest(player) {
    if (!player) {
      return null;
    }
    for (const guest of this.openSockets("guest")) {
      const attachment = guest.deserializeAttachment();
      if (attachment?.player === player) {
        return guest;
      }
    }
    return null;
  }

  openSockets(tag) {
    return this.ctx.getWebSockets(tag).filter(ws => ws.readyState === 1);
  }

  async getDisconnects() {
    const saved = await this.ctx.storage.get(DISCONNECT_STATE_KEY);
    return {
      host: Number(saved?.host) || 0,
      guests: saved?.guests && typeof saved.guests === "object" ? { ...saved.guests } : {},
      expiredGuests: Array.isArray(saved?.expiredGuests) ? [...saved.expiredGuests] : []
    };
  }

  async markDisconnected(role, player) {
    const disconnects = await this.getDisconnects();
    const deadline = Date.now() + DISCONNECT_GRACE_MS;
    if (role === "host") {
      disconnects.host = deadline;
    } else {
      disconnects.guests[player] = deadline;
    }
    await this.saveDisconnects(disconnects);
  }

  async clearDisconnect(role, player) {
    const disconnects = await this.getDisconnects();
    if (role === "host") {
      disconnects.host = 0;
    } else {
      delete disconnects.guests[player];
    }
    await this.saveDisconnects(disconnects);
  }

  async saveDisconnects(disconnects) {
    const deadlines = [disconnects.host, ...Object.values(disconnects.guests)]
      .filter(deadline => Number(deadline) > 0);
    if (deadlines.length === 0) {
      if (disconnects.expiredGuests.length > 0) {
        await this.ctx.storage.put(DISCONNECT_STATE_KEY, disconnects);
      } else {
        await this.ctx.storage.delete(DISCONNECT_STATE_KEY);
      }
      await this.ctx.storage.deleteAlarm();
      return;
    }
    await this.ctx.storage.put(DISCONNECT_STATE_KEY, disconnects);
    await this.ctx.storage.setAlarm(Math.min(...deadlines));
  }

  notifyGuestLeft(attachment) {
    this.sendToHost(encodeMessage(
      "leave_room",
      attachment.roomCode,
      attachment.player,
      ""
    ));
  }

  async flushExpiredGuestLeaves(roomCode) {
    const disconnects = await this.getDisconnects();
    for (const player of disconnects.expiredGuests) {
      this.notifyGuestLeft({ roomCode, player });
    }
    disconnects.expiredGuests = [];
    await this.saveDisconnects(disconnects);
  }

  async finalizeHostDisconnect(attachment) {
    const guests = this.openSockets("guest");
    let matchWasRunning = false;
    try {
      const snapshot = JSON.parse((await this.ctx.storage.get("snapshot")) || "{}");
      matchWasRunning = snapshot.inGame === true;
    } catch {
      matchWasRunning = false;
    }
    if (matchWasRunning && guests.length === 1) {
      const winner = guests[0].deserializeAttachment()?.player || "Remaining player";
      guests[0].send(encodeMessage(
        "match_result",
        attachment.roomCode,
        attachment.player,
        `${winner}\t${attachment.player} left the match`
      ));
    }
    for (const guest of guests) {
      guest.close(1012, "Room host did not reconnect");
    }
    await this.ctx.storage.deleteAll();
  }
}

function cleanPlayer(value) {
  if (!value) {
    return null;
  }
  const clean = value.trim();
  return clean.length > 0 && clean.length <= 32 ? clean : null;
}

function encodeMessage(type, room, player, value) {
  return JSON.stringify({
    type: type || "",
    room: room || "",
    player: player || "",
    value: value || ""
  });
}
