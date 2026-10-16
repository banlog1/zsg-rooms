// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import io.netty.buffer.Unpooled;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.registry.Registry;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.RandomAccess;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Authoritative completed outcomes, independent of packet indexing and playback order. */
public final class BarterHistory {
    static final String ENTRY = "zsg-rooms/barters.json";
    static final int MAX_BYTES = 4 * 1024 * 1024, MAX_EVENTS = 8192;
    static final int MAX_ITEMS = 64, MAX_TOTAL_ITEMS = 32768, MAX_STACK_BYTES = 8192;
    public final boolean available, truncated;
    public final List<Event> events;

    private BarterHistory(boolean available, boolean truncated, List<Event> events) {
        this.available = available;
        this.truncated = truncated;
        this.events = Collections.unmodifiableList(new ArrayList<>(events));
    }

    public static BarterHistory read(Path path, int duration) throws IOException {
        checkCancelled();
        if (duration < 0) throw new IOException("Invalid barter recording duration");
        try (ZipFile zip = new ZipFile(path.toFile())) {
            ZipEntry entry = zip.getEntry(ENTRY);
            if (entry == null) return new BarterHistory(false, false, Collections.emptyList());
            if (entry.isDirectory() || entry.getSize() > MAX_BYTES) throw new IOException("Barter track too large");
            try (InputStream input = zip.getInputStream(entry)) { return read(input, duration); }
        }
    }

    static BarterHistory read(InputStream input, int duration) throws IOException {
        if (duration < 0) throw new IOException("Invalid barter recording duration");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) {
            checkCancelled();
            if (bytes.size() + count > MAX_BYTES) throw new IOException("Barter track too large");
            bytes.write(buffer, 0, count);
        }
        try (JsonReader json = new JsonReader(new InputStreamReader(new ByteArrayInputStream(bytes.toByteArray()),
                StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)))) {
            json.setLenient(false);
            json.beginObject();
            Set<String> fields = new HashSet<>();
            int version = -1, protocol = -1;
            boolean truncated = false;
            List<Event> events = new ArrayList<>();
            int totalItems = 0;
            while (json.hasNext()) {
                String name = field(json, fields);
                switch (name) {
                    case "version": version = integer(json); break;
                    case "protocol": protocol = integer(json); break;
                    case "truncated":
                        require(json, JsonToken.BOOLEAN);
                        truncated = json.nextBoolean();
                        break;
                    case "events":
                        json.beginArray();
                        while (json.hasNext()) {
                            checkCancelled();
                            if (events.size() >= MAX_EVENTS) throw new IOException("Too many barter events");
                            Event event = event(json, duration);
                            totalItems += event.items.size();
                            if (totalItems > MAX_TOTAL_ITEMS) throw new IOException("Too many barter items");
                            if (!events.isEmpty()) {
                                Event previous = events.get(events.size() - 1);
                                if (event.time < previous.time || event.world < previous.world)
                                    throw new IOException("Unordered barter events");
                            }
                            events.add(event);
                        }
                        json.endArray();
                        break;
                    default: throw new IOException("Unknown barter field");
                }
            }
            json.endObject();
            if (version != 1 || protocol != 736 || fields.size() != 4 || json.peek() != JsonToken.END_DOCUMENT)
                throw new IOException("Unsupported barter track");
            return new BarterHistory(true, truncated, events);
        } catch (RuntimeException error) {
            throw new IOException("Malformed barter track", error);
        }
    }

    private static Event event(JsonReader json, int duration) throws IOException {
        checkCancelled();
        json.beginObject();
        Set<String> fields = new HashSet<>();
        int time = -1, world = -1;
        String dimension = null;
        UUID piglin = null;
        double x = Double.NaN, y = Double.NaN, z = Double.NaN;
        List<ItemStack> items = new ArrayList<>();
        int itemBytes = 0;
        while (json.hasNext()) {
            switch (field(json, fields)) {
                case "time": time = integer(json); break;
                case "world": world = integer(json); break;
                case "dimension":
                    dimension = string(json, 256);
                    if (!dimension.matches("[a-z0-9_.-]+:[a-z0-9/._-]+")) throw new IOException("Invalid barter dimension");
                    break;
                case "piglin":
                    String id = string(json, 36);
                    piglin = UUID.fromString(id);
                    if (!piglin.toString().equalsIgnoreCase(id)) throw new IOException("Invalid piglin UUID");
                    break;
                case "x": x = coordinate(json); break;
                case "y": y = coordinate(json); break;
                case "z": z = coordinate(json); break;
                case "items":
                    json.beginArray();
                    while (json.hasNext()) {
                        checkCancelled();
                        if (items.size() >= MAX_ITEMS) throw new IOException("Too many barter output stacks");
                        String encoded = string(json, ((MAX_STACK_BYTES + 2) / 3) * 4);
                        itemBytes += encoded.length() + 3;
                        if (itemBytes > 65536 - 1024) throw new IOException("Oversized barter event");
                        items.add(stack(encoded));
                    }
                    json.endArray();
                    break;
                default: throw new IOException("Unknown barter event field");
            }
        }
        json.endObject();
        if (fields.size() != 8 || time < 0 || time > duration || world < 0 || dimension == null || piglin == null
                || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z))
            throw new IOException("Invalid barter event");
        return new Event(time, world, dimension, piglin, x, y, z, items);
    }

    private static String field(JsonReader json, Set<String> seen) throws IOException {
        String name = json.nextName();
        if (name.length() > 16 || !seen.add(name)) throw new IOException("Invalid or duplicate barter field");
        return name;
    }

    private static void checkCancelled() throws java.io.InterruptedIOException {
        if (Thread.currentThread().isInterrupted()) throw new java.io.InterruptedIOException("Barter history read cancelled");
    }

    private static void require(JsonReader json, JsonToken token) throws IOException {
        if (json.peek() != token) throw new IOException("Invalid barter value type");
    }

    private static int integer(JsonReader json) throws IOException {
        require(json, JsonToken.NUMBER);
        String value = json.nextString();
        if (value.length() > 10 || !value.matches("0|[1-9][0-9]*")) throw new IOException("Invalid barter integer");
        return Integer.parseInt(value);
    }

    private static String string(JsonReader json, int max) throws IOException {
        require(json, JsonToken.STRING);
        String value = json.nextString();
        if (value.length() > max) throw new IOException("Oversized barter string");
        return value;
    }

    private static double coordinate(JsonReader json) throws IOException {
        require(json, JsonToken.NUMBER);
        String value = json.nextString();
        if (value.length() > 64) throw new IOException("Invalid barter coordinate");
        double result = Double.parseDouble(value);
        if (!Double.isFinite(result)) throw new IOException("Nonfinite barter coordinate");
        return result;
    }

    private static ItemStack stack(String encoded) throws IOException {
        byte[] payload = Base64.getDecoder().decode(encoded);
        if (payload.length == 0 || payload.length > MAX_STACK_BYTES
                || !Base64.getEncoder().encodeToString(payload).equals(encoded)) throw new IOException("Invalid barter payload");
        PacketByteBuf data = new PacketByteBuf(Unpooled.wrappedBuffer(payload));
        try {
            int present = data.readUnsignedByte();
            if (present == 1) {
                int start = data.readerIndex(), id = data.readVarInt();
                if (id < 0 || data.readerIndex() - start != PacketByteBuf.getVarIntSizeBytes(id))
                    throw new IOException("Invalid barter item ID");
                Item item = Registry.ITEM.get(id);
                if (item == null || item == Items.AIR || Item.getRawId(item) != id || data.readByte() <= 0)
                    throw new IOException("Invalid barter item");
                // Preflight before vanilla NBT decoding: declared lengths must fit before allocation.
                DataInputStream nbt = new DataInputStream(new ByteArrayInputStream(payload, data.readerIndex(), data.readableBytes()));
                int type = nbt.readUnsignedByte();
                if (type != 0) {
                    if (type != 10) throw new IOException("Invalid barter NBT root");
                    skip(nbt, nbt.readUnsignedShort());
                    tag(nbt, type, 0, new int[]{1024});
                }
                if (nbt.available() != 0) throw new IOException("Trailing barter item bytes");
            } else if (present != 0 || data.isReadable()) throw new IOException("Invalid empty barter item");
            data.readerIndex(0);
            ItemStack result = data.readItemStack();
            if (data.isReadable()) throw new IOException("Trailing barter item bytes");
            return result;
        } finally { data.release(); }
    }

    private static void skip(DataInputStream input, int count) throws IOException {
        if (count < 0 || count > input.available() || input.skipBytes(count) != count) throw new IOException("Truncated barter NBT");
    }

    private static void tag(DataInputStream input, int type, int depth, int[] budget) throws IOException {
        if (depth > 32 || --budget[0] < 0) throw new IOException("Complex barter NBT");
        switch (type) {
            case 1: skip(input, 1); break;
            case 2: skip(input, 2); break;
            case 3: case 5: skip(input, 4); break;
            case 4: case 6: skip(input, 8); break;
            case 7: case 11: case 12:
                int length = input.readInt(), width = type == 7 ? 1 : type == 11 ? 4 : 8;
                if (length < 0 || length > input.available() / width) throw new IOException("Invalid barter NBT array");
                skip(input, length * width);
                break;
            case 8: skip(input, input.readUnsignedShort()); break;
            case 9:
                int element = input.readUnsignedByte(), size = input.readInt();
                if (size < 0 || size > budget[0] || element > 12 || element == 0 && size != 0)
                    throw new IOException("Invalid barter NBT list");
                for (int i = 0; i < size; i++) tag(input, element, depth + 1, budget);
                break;
            case 10:
                int child;
                while ((child = input.readUnsignedByte()) != 0) {
                    skip(input, input.readUnsignedShort());
                    tag(input, child, depth + 1, budget);
                }
                break;
            default: throw new IOException("Invalid barter NBT type");
        }
    }

    public List<Event> world(int world, int time) {
        List<Event> result = new ArrayList<>();
        for (Event event : events) {
            if (event.time > time) break;
            if (event.world == world) result.add(event);
        }
        return Collections.unmodifiableList(result);
    }

    public static final class Event {
        public final int time, world;
        public final String dimension;
        public final UUID piglin;
        public final double x, y, z;
        public final List<ItemStack> items;
        private Event(int time, int world, String dimension, UUID piglin, double x, double y, double z, List<ItemStack> items) {
            this.time = time; this.world = world; this.dimension = dimension; this.piglin = piglin;
            this.x = x; this.y = y; this.z = z;
            this.items = Collections.unmodifiableList(new StackCopies(items));
        }
    }

    private static final class StackCopies extends AbstractList<ItemStack> implements RandomAccess {
        private final ItemStack[] stacks;
        StackCopies(List<ItemStack> items) {
            stacks = new ItemStack[items.size()];
            for (int i = 0; i < stacks.length; i++) stacks[i] = items.get(i).copy();
        }
        @Override public ItemStack get(int index) { return stacks[index].copy(); }
        @Override public int size() { return stacks.length; }
    }
}
