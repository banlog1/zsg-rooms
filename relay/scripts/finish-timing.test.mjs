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

test("obsolete clock replies are no longer accepted from guests", () => {
  const room = session();
  const value = JSON.stringify({ nonce: 1, received: -99999, sent: -99998 });
  room.handleGuestMessage(guest, { type: "finish_clock_reply", player: "Host", room: "OTHER", value });
  assert.equal(room.toHost.length, 0);
  assert.equal(room.toGuests.length, 0);
});

test("final results are forwarded intact", async () => {
  const room = session();
  const value = "Guest\tBeat the seed in 09:40.120 IGT";
  await room.handleHostMessage(host, { type: "match_result", value });
  assert.deepEqual(room.toGuests, [{ type: "match_result", room: "TEST-ROOM", player: "Host", value }]);
});

test("guests cannot inject host clock probes or match results", () => {
  const room = session();
  for (const type of ["finish_clock_probe", "match_result"]) {
    room.handleGuestMessage(guest, { type, value: "forged" });
  }
  assert.equal(room.toHost.length, 0);
  assert.equal(room.toGuests.length, 0);
});

test("nanosecond durations and race identity survive relay forwarding exactly", () => {
  const room = session();
  const value = JSON.stringify({ version: 3, raceId: "race-one", elapsedNanos: "604799999999999", igt: 999 });
  room.handleGuestMessage(guest, { type: "complete_run", player: "SomeoneElse", value });
  assert.deepEqual(room.toHost, [{ type: "complete_run", room: "TEST-ROOM", player: "Guest", value }]);
});
