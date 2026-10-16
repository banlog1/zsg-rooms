package zsgrooms.modid.replay;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.netty.buffer.Unpooled;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.AbstractListTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.PacketByteBuf;

import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/** Event-only optional track. No live stacks survive the server-thread snapshot. */
final class ReplayBarterCapture {
    static final int MAX_BYTES = 4 * 1024 * 1024, MAX_EVENTS = 8192;
    static final int MAX_PENDING = 64, MAX_ITEMS = 64, MAX_TOTAL_ITEMS = 32768;
    static final int MAX_STACK_BYTES = 8192, MAX_EVENT_BYTES = 65536;
    private static final AtomicInteger PENDING = new AtomicInteger();
    private final List<Recorded> events = new ArrayList<>();
    private final Set<Ticket> pending = new HashSet<>();
    private volatile Binding binding;
    private boolean truncated, limited, finished;
    private int bytes, itemCount;

    Binding binding() { return binding; }

    synchronized void bind(Object server, Object clientWorld, Object attachment, int world, String dimension) {
        invalidate();
        binding = !finished && server != null && clientWorld != null && attachment != null && world >= 0
                ? new Binding(server, clientWorld, attachment, world, dimension) : null;
    }

    synchronized void invalidate() {
        binding = null;
        // Disconnect can discard queued callbacks, so their finally blocks cannot own cleanup alone.
        for (Ticket ticket : pending) {
            if (!ticket.completed) truncated = true;
            ticket.closed = true;
        }
        PENDING.addAndGet(-pending.size());
        pending.clear();
    }

    synchronized Ticket reserve(Binding expected) {
        if (finished || limited || expected == null || expected != binding) return null;
        if (events.size() + pending.size() >= MAX_EVENTS) { truncate(); return null; }
        int count = PENDING.get();
        while (count < MAX_PENDING) {
            if (PENDING.compareAndSet(count, count + 1)) {
                Ticket ticket = new Ticket(this, expected);
                pending.add(ticket);
                return ticket;
            }
            count = PENDING.get();
        }
        truncate();
        return null;
    }

    synchronized void truncate() { truncated = true; limited = true; }

    synchronized void complete(Ticket ticket, long time, Outcome outcome) {
        if (ticket.owner != this || ticket.closed || ticket.completed || finished || ticket.binding != binding) return;
        ticket.completed = true;
        if (time < 0 || time > Integer.MAX_VALUE || !events.isEmpty() && time < events.get(events.size() - 1).time) {
            truncate();
            return;
        }
        JsonObject event = new JsonObject();
        event.addProperty("time", time);
        event.addProperty("world", ticket.binding.world);
        event.addProperty("dimension", ticket.binding.dimension);
        event.addProperty("piglin", outcome.piglin.toString());
        event.addProperty("x", outcome.x);
        event.addProperty("y", outcome.y);
        event.addProperty("z", outcome.z);
        JsonArray items = new JsonArray();
        for (String stack : outcome.items) items.add(stack);
        event.add("items", items);
        String json = event.toString();
        // All strings in this schema are ASCII, so character and UTF-8 byte counts agree.
        if (json.length() > MAX_EVENT_BYTES || bytes + json.length() + 1 > MAX_BYTES - 256
                || itemCount + outcome.items.length > MAX_TOTAL_ITEMS) {
            truncate();
            return;
        }
        events.add(new Recorded((int) time, json));
        bytes += json.length() + 1;
        itemCount += outcome.items.length;
    }

    synchronized String finish(int duration) {
        finished = true;
        invalidate();
        StringBuilder body = new StringBuilder(bytes);
        for (Recorded event : events) {
            if (event.time > duration) { truncated = true; break; }
            if (body.length() != 0) body.append(',');
            body.append(event.json);
        }
        return "{\"version\":1,\"protocol\":736,\"truncated\":" + truncated + ",\"events\":[" + body + "]}";
    }

    static Outcome snapshot(int entityId, UUID piglin, double x, double y, double z, List<ItemStack> items) {
        if (piglin == null || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || items == null || items.size() > MAX_ITEMS) throw new IllegalArgumentException("Invalid barter");
        String[] encoded = new String[items.size()];
        int total = 0;
        PacketByteBuf data = new PacketByteBuf(Unpooled.buffer(256, MAX_STACK_BYTES));
        try {
            for (int i = 0; i < encoded.length; i++) {
                ItemStack stack = items.get(i);
                if (stack == null || !stack.isEmpty() && (stack.getCount() < 1 || stack.getCount() > 127))
                    throw new IllegalArgumentException("Invalid barter stack");
                checkTag(stack.getTag(), 0, new int[]{1024});
                data.clear();
                data.writeItemStack(stack);
                byte[] payload = new byte[data.readableBytes()];
                data.getBytes(0, payload);
                encoded[i] = Base64.getEncoder().encodeToString(payload);
                total += encoded[i].length() + 3;
                if (total > MAX_EVENT_BYTES - 1024) throw new IllegalArgumentException("Oversized barter");
            }
        } finally { data.release(); }
        return new Outcome(entityId, piglin, x, y, z, encoded);
    }

    private static void checkTag(Tag tag, int depth, int[] budget) {
        if (tag == null) return;
        if (depth > 32 || --budget[0] < 0) throw new IllegalArgumentException("Complex barter NBT");
        if (tag instanceof CompoundTag) {
            CompoundTag compound = (CompoundTag) tag;
            if (compound.getSize() > budget[0]) throw new IllegalArgumentException("Large barter NBT");
            for (String key : compound.getKeys()) {
                if (key.length() > MAX_STACK_BYTES) throw new IllegalArgumentException("Large barter NBT key");
                checkTag(compound.get(key), depth + 1, budget);
            }
        } else if (tag instanceof AbstractListTag) {
            AbstractListTag<?> list = (AbstractListTag<?>) tag;
            if (list.size() > MAX_STACK_BYTES) throw new IllegalArgumentException("Large barter NBT array");
            if (tag instanceof ListTag) {
                if (list.size() > budget[0]) throw new IllegalArgumentException("Large barter NBT list");
                for (Tag child : list) checkTag(child, depth + 1, budget);
            }
        } else if (tag.getType() == 8 && tag.asString().length() > MAX_STACK_BYTES) {
            throw new IllegalArgumentException("Large barter NBT string");
        }
    }

    static final class Binding {
        final Object server, clientWorld, attachment;
        final int world;
        final String dimension;
        Binding(Object server, Object clientWorld, Object attachment, int world, String dimension) {
            if (dimension == null || dimension.length() > 256 || !dimension.matches("[a-z0-9_.-]+:[a-z0-9/._-]+"))
                throw new IllegalArgumentException("Invalid barter dimension");
            this.server = server; this.clientWorld = clientWorld; this.attachment = attachment;
            this.world = world; this.dimension = dimension;
        }
    }

    static final class Ticket implements AutoCloseable {
        final ReplayBarterCapture owner;
        final Binding binding;
        private boolean closed, completed;
        Ticket(ReplayBarterCapture owner, Binding binding) { this.owner = owner; this.binding = binding; }
        @Override public void close() {
            synchronized (owner) {
                if (closed) return;
                closed = true;
                owner.pending.remove(this);
                PENDING.decrementAndGet();
            }
        }
    }

    static final class Outcome {
        final int entityId;
        final UUID piglin;
        final double x, y, z;
        private final String[] items;
        Outcome(int entityId, UUID piglin, double x, double y, double z, String[] items) {
            this.entityId = entityId; this.piglin = piglin;
            this.x = x; this.y = y; this.z = z; this.items = items;
        }
    }

    private static final class Recorded {
        final int time;
        final String json;
        Recorded(int time, String json) { this.time = time; this.json = json; }
    }
}
