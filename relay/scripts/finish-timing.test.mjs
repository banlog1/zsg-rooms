import assert from "node:assert/strict";
import test from "node:test";
import { RoomSession } from "../src/index.js";

function session() {
  const room = Object.create(RoomSession.prototype);
  room.toHost = [];
  room.toGuests = [];
  room.sendToHost = message => room.toHost.push(JSON.parse(message));
  room.broadcastGuests = message => room.toGuests.push(JSON.parse(message));
  return room;
}

const guest = { role: "guest", player: "Guest", roomCode: "TEST-ROOM" };
const host = { role: "host", player: "Host", roomCode: "TEST-ROOM" };

test("clock replies go only to the host with authenticated guest identity", () => {
  const room = session();
  const value = JSON.stringify({ nonce: 1, received: -99999, sent: -99998 });
  room.handleGuestMessage(guest, { type: "finish_clock_reply", player: "Host", room: "OTHER", value });
  assert.deepEqual(room.toHost, [{ type: "finish_clock_reply", room: "TEST-ROOM", player: "Guest", value }]);
  assert.equal(room.toGuests.length, 0);
});

test("host clock probes are forwarded intact", async () => {
  const room = session();
  const value = JSON.stringify({ nonce: 1 });
  await room.handleHostMessage(host, { type: "finish_clock_probe", value });
  assert.deepEqual(room.toGuests, [{ type: "finish_clock_probe", room: "TEST-ROOM", player: "Host", value }]);
});

test("guests cannot inject host clock probes or match results", () => {
  const room = session();
  for (const type of ["finish_clock_probe", "match_result"]) {
    room.handleGuestMessage(guest, { type, value: "forged" });
  }
  assert.equal(room.toHost.length, 0);
  assert.equal(room.toGuests.length, 0);
});

test("completion timestamps and race identity survive relay forwarding", () => {
  const room = session();
  const value = JSON.stringify({ version: 2, raceId: "race-one", entered: 12345, igt: 999 });
  room.handleGuestMessage(guest, { type: "complete_run", player: "SomeoneElse", value });
  assert.deepEqual(room.toHost, [{ type: "complete_run", room: "TEST-ROOM", player: "Guest", value }]);
});
