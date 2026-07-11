const MAX_MESSAGE_BYTES = 256 * 1024;
const GUEST_ACTIONS = new Set([
  "chat",
  "share_seed",
  "seed_change",
  "forfeit",
  "progress",
  "advancement",
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
      if (this.ctx.getWebSockets("host").length > 0) {
        return new Response("Host is already connected", { status: 409 });
      }
      if (!storedToken) {
        await this.ctx.storage.put({ hostToken: token, hostName: player, roomCode });
      }
    } else {
      if (this.ctx.getWebSockets("host").length === 0) {
        return new Response("Room host is offline", { status: 404 });
      }
      if (this.findGuest(player)) {
        return new Response("That player is already connected", { status: 409 });
      }
      const maxPlayers = (await this.ctx.storage.get("maxPlayers")) || 8;
      if (this.ctx.getWebSockets("guest").length + 1 >= maxPlayers) {
        return new Response("Room is full", { status: 409 });
      }
    }

    this.ctx.acceptWebSocket(server, [role]);
    server.serializeAttachment({ role, player, roomCode });
    server.send(encodeMessage("welcome", roomCode, "relay", role));

    if (role === "host") {
      const snapshot = await this.ctx.storage.get("snapshot");
      if (snapshot) {
        server.send(encodeMessage("snapshot", roomCode, player, snapshot));
      }
    } else {
      this.sendToHost(encodeMessage("join_room", roomCode, player, ""));
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
      const guests = this.ctx.getWebSockets("guest");
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
        guest.close(1012, "Room host disconnected");
      }
      await this.ctx.storage.deleteAll();
    } else {
      this.sendToHost(encodeMessage(
        "leave_room",
        attachment.roomCode,
        attachment.player,
        ""
      ));
    }
  }

  webSocketError(ws) {
    try {
      ws.close(1011, "Relay connection error");
    } catch {
      // The socket may already be closed.
    }
  }

  sendToHost(message) {
    const hosts = this.ctx.getWebSockets("host");
    if (hosts.length > 0) {
      hosts[0].send(message);
    }
  }

  broadcastGuests(message) {
    for (const guest of this.ctx.getWebSockets("guest")) {
      guest.send(message);
    }
  }

  findGuest(player) {
    if (!player) {
      return null;
    }
    for (const guest of this.ctx.getWebSockets("guest")) {
      const attachment = guest.deserializeAttachment();
      if (attachment?.player === player) {
        return guest;
      }
    }
    return null;
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
