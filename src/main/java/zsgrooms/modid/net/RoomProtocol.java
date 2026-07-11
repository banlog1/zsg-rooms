package zsgrooms.modid.net;

import java.util.LinkedHashMap;
import java.util.Map;

public class RoomProtocol {
    private RoomProtocol() {
    }

    public static String encode(String type, String room, String player, String value) {
        Map<String, String> message = new LinkedHashMap<String, String>();
        message.put("type", safe(type));
        message.put("room", safe(room));
        message.put("player", safe(player));
        message.put("value", safe(value));
        return encode(message);
    }

    public static String encode(Map<String, String> message) {
        StringBuilder builder = new StringBuilder();
        builder.append('{');
        boolean first = true;
        for (Map.Entry<String, String> entry : message.entrySet()) {
            if (!first) {
                builder.append(',');
            }
            first = false;
            builder.append('"').append(escape(entry.getKey())).append('"');
            builder.append(':');
            builder.append('"').append(escape(entry.getValue())).append('"');
        }
        builder.append('}');
        return builder.toString();
    }

    public static Map<String, String> decode(String raw) {
        Map<String, String> values = new LinkedHashMap<String, String>();
        if (raw == null) {
            return values;
        }
        String text = raw.trim();
        if (text.startsWith("{")) {
            text = text.substring(1);
        }
        if (text.endsWith("}")) {
            text = text.substring(0, text.length() - 1);
        }

        int index = 0;
        while (index < text.length()) {
            index = skipSeparators(text, index);
            ParsedString key = readString(text, index);
            if (key == null) {
                break;
            }
            index = skipSeparators(text, key.nextIndex);
            if (index >= text.length() || text.charAt(index) != ':') {
                break;
            }
            index += 1;
            index = skipSeparators(text, index);
            ParsedString value = readString(text, index);
            if (value == null) {
                break;
            }
            values.put(key.value, value.value);
            index = value.nextIndex;
        }
        return values;
    }

    private static int skipSeparators(String text, int index) {
        while (index < text.length()) {
            char c = text.charAt(index);
            if (c != ' ' && c != '\t' && c != '\r' && c != '\n' && c != ',') {
                break;
            }
            index += 1;
        }
        return index;
    }

    private static ParsedString readString(String text, int index) {
        if (index >= text.length() || text.charAt(index) != '"') {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        index += 1;
        while (index < text.length()) {
            char c = text.charAt(index);
            if (c == '"') {
                return new ParsedString(builder.toString(), index + 1);
            }
            if (c == '\\' && index + 1 < text.length()) {
                index += 1;
                char escaped = text.charAt(index);
                if (escaped == 'n') {
                    builder.append('\n');
                } else if (escaped == 'r') {
                    builder.append('\r');
                } else if (escaped == 't') {
                    builder.append('\t');
                } else {
                    builder.append(escaped);
                }
            } else {
                builder.append(c);
            }
            index += 1;
        }
        return null;
    }

    private static String escape(String value) {
        return safe(value)
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static class ParsedString {
        private final String value;
        private final int nextIndex;

        private ParsedString(String value, int nextIndex) {
            this.value = value;
            this.nextIndex = nextIndex;
        }
    }
}
