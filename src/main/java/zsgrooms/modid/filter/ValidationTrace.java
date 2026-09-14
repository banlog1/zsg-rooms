package zsgrooms.modid.filter;

import com.google.gson.JsonObject;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/** Offline, thread-confined timings. Exclusive time excludes nested measured operations. */
final class ValidationTrace implements AutoCloseable {
    private static final ThreadLocal<ValidationTrace> CURRENT = new ThreadLocal<ValidationTrace>();
    private final Map<String, Stat> stats = new LinkedHashMap<String, Stat>();
    private final ArrayDeque<Frame> stack = new ArrayDeque<Frame>();

    ValidationTrace() {
        if (CURRENT.get() != null) throw new IllegalStateException("Nested validation trace");
        CURRENT.set(this);
    }

    static <T> T measure(String name, Supplier<T> operation) {
        ValidationTrace trace = CURRENT.get();
        if (trace == null) return operation.get();
        Stat stat = trace.stats.computeIfAbsent(name, ignored -> new Stat());
        Frame frame = new Frame();
        trace.stack.push(frame);
        stat.calls++;
        long start = System.nanoTime();
        try {
            T value = operation.get();
            if (Boolean.FALSE.equals(value)) stat.falseResults++;
            return value;
        } catch (RuntimeException | Error failure) {
            stat.errors++;
            throw failure;
        } finally {
            long elapsed = System.nanoTime() - start;
            trace.stack.pop();
            stat.inclusive += elapsed;
            stat.exclusive += elapsed - frame.children;
            if (!trace.stack.isEmpty()) trace.stack.peek().children += elapsed;
        }
    }

    static void run(String name, Runnable operation) {
        measure(name, () -> { operation.run(); return null; });
    }

    JsonObject json() {
        JsonObject result = new JsonObject();
        stats.forEach((name, stat) -> {
            JsonObject item = new JsonObject();
            item.addProperty("calls", stat.calls);
            item.addProperty("falseResults", stat.falseResults);
            item.addProperty("errors", stat.errors);
            item.addProperty("inclusiveMs", stat.inclusive / 1000000.0);
            item.addProperty("exclusiveMs", stat.exclusive / 1000000.0);
            result.add(name, item);
        });
        return result;
    }

    @Override public void close() {
        if (CURRENT.get() != this || !stack.isEmpty()) throw new IllegalStateException("Unbalanced trace");
        CURRENT.remove();
    }

    private static final class Frame { long children; }
    private static final class Stat { long calls, falseResults, errors, inclusive, exclusive; }
}
