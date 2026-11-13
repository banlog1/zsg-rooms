package zsgrooms.modid.settings;

import com.google.gson.*;
import java.util.*;

/** Only settings with known 1.16.1 representations may cross an instance boundary. */
public final class ImportSettingsSchema {
    public enum Category {
        CONTROLS("Controls & mouse"), VIDEO("Video"), AUDIO("Audio"),
        CHAT("Chat & accessibility"), APPEARANCE("Language & appearance"), RESETS("Reset behavior");
        public final String label;
        Category(String label) { this.label = label; }
    }

    private interface Validator { boolean accepts(String value); }
    private static final class Rule {
        final Category category;
        final Validator validator;
        Rule(Category category, Validator validator) { this.category = category; this.validator = validator; }
    }
    private static final Map<String, Rule> RULES = new LinkedHashMap<>();
    private static final Validator BOOLEAN = v -> v.equals("true") || v.equals("false");
    private static final Set<String> KEYS = new HashSet<>(Arrays.asList((
            "jump sneak sprint left right back forward attack pickItem use drop inventory swapOffhand "
            + "loadToolbarActivator saveToolbarActivator playerlist chat command advancements spectatorOutlines "
            + "screenshot smoothCamera fullscreen togglePerspective").split(" ")));
    private static final Set<String> NAMED_KEYS = new HashSet<>(Arrays.asList((
            "unknown num.lock down left right up apostrophe backslash comma equal grave.accent left.bracket minus period "
            + "right.bracket semicolon slash space tab left.alt left.control left.shift left.win right.alt right.control right.shift right.win "
            + "enter escape backspace delete end home insert page.down page.up caps.lock pause scroll.lock menu print.screen world.1 world.2 "
            + "keypad.add keypad.decimal keypad.enter keypad.equal keypad.multiply keypad.divide keypad.subtract").split(" ")));
    static {
        bool(Category.CONTROLS, "autoJump invertYMouse discrete_mouse_scroll touchscreen rawMouseInput toggleCrouch toggleSprint");
        number(Category.CONTROLS, "mouseSensitivity", 0, 1, false);
        number(Category.CONTROLS, "mouseWheelSensitivity", 0.01, 10, false);
        bool(Category.VIDEO, "enableVsync entityShadows fullscreen bobView");
        number(Category.VIDEO, "fov", -1, 1, false);
        number(Category.VIDEO, "gamma", 0, 5, false);
        number(Category.VIDEO, "renderDistance", 2, 32, true);
        number(Category.VIDEO, "entityDistanceScaling", 0.5, 5, false);
        number(Category.VIDEO, "guiScale", 0, 16, true);
        number(Category.VIDEO, "maxFps", 10, 260, true);
        number(Category.VIDEO, "particles graphicsMode ao attackIndicator", 0, 2, true);
        number(Category.VIDEO, "biomeBlendRadius", 0, 7, true);
        number(Category.VIDEO, "mipmapLevels", 0, 4, true);
        rule(Category.VIDEO, "renderClouds", v -> Arrays.asList("true", "fast", "false").contains(v));
        for (String sound : "master music record weather block hostile neutral player ambient voice".split(" "))
            number(Category.AUDIO, "soundCategory_" + sound, 0, 1, false);
        bool(Category.CHAT, "showSubtitles autoSuggestions chatColors chatLinks chatLinksPrompt reducedDebugInfo backgroundForChatOnly");
        number(Category.CHAT, "chatOpacity chatLineSpacing textBackgroundOpacity chatScale chatWidth chatHeightFocused chatHeightUnfocused", 0, 1, false);
        number(Category.CHAT, "chatVisibility", 0, 2, true);
        number(Category.CHAT, "narrator", 0, 3, true);
        number(Category.CHAT, "chatDelay", 0, 6, false);
        bool(Category.APPEARANCE, "forceUnicodeFont advancedItemTooltips heldItemTooltips realmsNotifications");
        rule(Category.APPEARANCE, "lang", v -> v.matches("[a-z]{2,3}_[a-z0-9]{2,8}"));
        rule(Category.APPEARANCE, "mainHand", v -> v.equals("left") || v.equals("right"));
        for (String part : "cape jacket left_sleeve right_sleeve left_pants_leg right_pants_leg hat".split(" "))
            bool(Category.APPEARANCE, "modelPart_" + part);
        bool(Category.RESETS, "pauseOnLostFocus");
    }

    private static void bool(Category c, String names) { rule(c, names, BOOLEAN); }
    private static void number(Category c, String names, double min, double max, boolean integer) {
        rule(c, names, v -> {
            try {
                double n = Double.parseDouble(v);
                return Double.isFinite(n) && n >= min && n <= max && (!integer || n == Math.rint(n));
            } catch (NumberFormatException e) { return false; }
        });
    }
    private static void rule(Category c, String names, Validator validator) {
        for (String name : names.split(" ")) RULES.put(name, new Rule(c, validator));
    }

    public static Category category(String key, boolean standard, Set<String> bindings) {
        if (standard && (key.equals("lang") || key.equals("heldItemTooltips"))) return null;
        String base = standard ? baseKey(key) : key;
        if (base.startsWith("key_") && (bindings.contains(base) || vanillaBinding(base))) return Category.CONTROLS;
        Rule rule = RULES.get(base);
        if (rule != null) return rule.category;
        if (standard && Arrays.asList("toggleStandardSettings", "autoF3Esc", "firstAutoF3EscDelay", "triggerOnResize",
                "hitboxes", "chunkborders", "pieDirectory", "perspective", "f1", "sneaking", "sprinting").contains(key))
            return Category.RESETS;
        return null;
    }

    private static boolean vanillaBinding(String key) {
        return key.startsWith("key_key.") && (KEYS.contains(key.substring(8)) || key.matches("key_key\\.hotbar\\.[1-9]"));
    }

    private static String baseKey(String key) {
        if (key.equals("language")) return "lang";
        if (Arrays.asList("fovOnWorldJoin", "renderDistanceOnWorldJoin", "entityDistanceScalingOnWorldJoin", "guiScaleOnWorldJoin").contains(key))
            return key.substring(0, key.length() - "OnWorldJoin".length());
        return key;
    }

    public static String standardKey(String key) { return key.equals("lang") ? "language" : key; }

    public static String vanillaValue(String key, String value, Set<String> bindings) {
        if (category(key, false, bindings) == null || value.length() > 256) return null;
        if (key.startsWith("key_")) {
            // Pre-1.13 numeric key codes and modifier expressions are not equivalent to GLFW bindings.
            if (value.matches("key\\.mouse\\.(left|right|middle|[4-8])")) return value;
            if (!value.startsWith("key.keyboard.")) return null;
            String name = value.substring("key.keyboard.".length());
            return NAMED_KEYS.contains(name) || name.matches("[a-z0-9]|f([1-9]|1[0-9]|2[0-5])|keypad\\.[0-9]") ? value : null;
        }
        Rule rule = RULES.get(key);
        if (!rule.validator.accepts(value)) return null;
        if (Arrays.asList("renderDistance", "guiScale", "maxFps", "particles", "graphicsMode", "ao",
                "attackIndicator", "biomeBlendRadius", "mipmapLevels", "chatVisibility", "narrator").contains(key))
            return Integer.toString((int) Double.parseDouble(value));
        return value;
    }

    public static JsonElement standardValue(String key, JsonElement input, Set<String> bindings) {
        if (category(key, true, bindings) == null || input == null) return null;
        try {
            JsonElement value = input;
            Boolean enabled = null;
            if (input.isJsonObject()) {
                if (Arrays.asList("toggleStandardSettings", "autoF3Esc", "firstAutoF3EscDelay", "triggerOnResize").contains(key)) return null;
                JsonObject wrapper = input.getAsJsonObject();
                if (wrapper.size() != 2 || !wrapper.has("enabled") || !wrapper.has("value")
                        || !wrapper.get("enabled").isJsonPrimitive() || !wrapper.get("enabled").getAsJsonPrimitive().isBoolean()) return null;
                enabled = wrapper.get("enabled").getAsBoolean(); value = wrapper.get("value");
            }
            if (!value.isJsonPrimitive()) return null;
            String raw = value.getAsString();
            String base = baseKey(key);
            String converted = raw;
            if (base.equals("fov")) converted = Double.toString((Double.parseDouble(raw) - 70) / 40);
            else if (base.equals("mainHand")) converted = raw.equals("0") ? "left" : raw.equals("1") ? "right" : "invalid";
            else if (base.equals("renderClouds")) converted = raw.equals("0") ? "false" : raw.equals("1") ? "fast" : raw.equals("2") ? "true" : "invalid";
            else if (Arrays.asList("toggleCrouch", "toggleSprint", "backgroundForChatOnly").contains(base))
                converted = raw.equals("0") ? "false" : raw.equals("1") ? "true" : "invalid";
            boolean valid;
            if (RULES.containsKey(base) || base.startsWith("key_")) valid = vanillaValue(base, converted, bindings) != null;
            else if (base.equals("pieDirectory")) valid = raw.matches("root(?:\\.[A-Za-z0-9_ -]+)*") && raw.length() <= 128;
            else if (base.equals("firstAutoF3EscDelay")) valid = raw.matches("[0-9]{1,3}") && Integer.parseInt(raw) <= 100;
            else if (base.equals("perspective")) valid = raw.matches("[0-2]");
            else valid = BOOLEAN.accepts(raw);
            if (!valid) return null;
            JsonElement result = primitive(raw);
            if (enabled != null) {
                JsonObject wrapper = new JsonObject(); wrapper.addProperty("enabled", enabled); wrapper.add("value", result); return wrapper;
            }
            return result;
        } catch (RuntimeException e) { return null; }
    }

    public static JsonElement fromVanilla(String key, String value) {
        if (key.equals("fov")) return new JsonPrimitive(Double.parseDouble(value) * 40 + 70);
        if (key.equals("mainHand")) return new JsonPrimitive(value.equals("left") ? 0 : 1);
        if (key.equals("renderClouds")) return new JsonPrimitive(value.equals("false") ? 0 : value.equals("fast") ? 1 : 2);
        if (Arrays.asList("toggleCrouch", "toggleSprint", "backgroundForChatOnly").contains(key))
            return new JsonPrimitive(Boolean.parseBoolean(value) ? 1 : 0);
        return primitive(value);
    }

    public static boolean enabled(JsonElement value) {
        return value != null && (!value.isJsonObject() || !value.getAsJsonObject().has("enabled") || value.getAsJsonObject().get("enabled").getAsBoolean());
    }

    private static JsonElement primitive(String value) {
        if (BOOLEAN.accepts(value)) return new JsonPrimitive(Boolean.parseBoolean(value));
        try { return new JsonPrimitive(new java.math.BigDecimal(value)); }
        catch (NumberFormatException ignored) { return new JsonPrimitive(value); }
    }

    private ImportSettingsSchema() {}
}
