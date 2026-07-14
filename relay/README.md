# ZSG Rooms Relay

This Worker routes room JSON messages through one Durable Object per room code.
Players connect using outbound secure WebSockets, so hosts do not need port
forwarding, Playit, or a public IP address.

## Deploy

1. Install Node.js 18 or newer.
2. Run `npm install` in this directory.
3. Run `npx wrangler login` and approve the Cloudflare login.
4. Run `npm run deploy`.
5. Enter the resulting `https://zsg-rooms-relay.<account>.workers.dev` URL in
   the mod's Relay field.

Durable Objects require the SQLite backend configured in `wrangler.jsonc`.
The service is designed to stay within the Cloudflare Workers Free limits for
small private race rooms.

The relay keeps abnormal host and guest disconnects in a 60-second grace
period. Reconnecting clients reclaim the same room identity; deliberate clean
disconnects still leave immediately.
