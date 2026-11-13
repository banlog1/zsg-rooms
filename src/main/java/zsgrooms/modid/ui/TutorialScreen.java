package zsgrooms.modid.ui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.RotatingCubeMapRenderer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.AbstractButtonWidget;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CheckboxWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;
import net.minecraft.text.StringRenderable;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import zsgrooms.modid.ZsgRooms;
import zsgrooms.modid.replay.ReplayPreferences;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static zsgrooms.modid.ui.TutorialOverlay.Box;

/** A guided, read-only practice view. Backdrop screens never receive input or lifecycle ticks. */
final class TutorialScreen extends Screen {
    static final int QUICK_STEPS = 8;
    private static final String[] TITLES = {
            "Bring your settings", "Your personal settings", "Find the right category",
            "Create a room", "Your room, your settings", "Join with a code", "Invite your friends", "Room settings",
            "Choose your seeds", "Check the game rules", "Seeds per race", "More than one finisher",
            "Run a tournament", "Start together", "Record your runs", "Choose recording detail",
            "Smoother camera capture", "Keep seed-change recordings", "Watch your replays",
            "Keep your recordings", "Review your runs"
    };
    private static final String[] BODIES = {
            "Import compatible settings from another instance, or save your current setup. Review changes first. Imports apply after you close Minecraft completely and relaunch.",
            "This cog opens your HUD, loading and recording preferences. These affect your setup, not everyone else's race. Click the highlighted cog to look inside.",
            "Open the highlighted category and try the different pages. These are your personal settings, separate from room rules. This is practice: nothing you choose here changes your setup.",
            "Create Room connects and opens your lobby in one click. It does not start a race. You can cancel while connecting. This practice tour creates no real room.",
            "New rooms use ZSG Rooms Mode, Standard ZSG Rooms rules, 1 seed and 1 finisher. Change these in the lobby before starting. Options also includes player capacity and tournaments.",
            "Paste your friend's room code and select Join. The host supplies the rules and filter. Connection settings is only needed for a custom relay; everyone must use the same one.",
            "Copy the room code and send it to friends. They use Join Room. The code stays hidden on screen unless you select Show. This example does not copy a real code.",
            "Options selects the seed filter, race format and tournament setup. Game Rules changes how the race plays. These are host controls for the room, separate from personal settings.",
            "Choose a filter for the next race. ZSG Rooms Mode mixes the Rooms seed collection with a small Ruined Portal Seedbank share. Everyone races the same seed or sequence.",
            "Open the highlighted preset to compare the choices and see their rules. The host chooses these for everyone. This is only a preview; your room stays unchanged.",
            "One seed is a normal race. Multiple seeds form a shared sequence; runners advance independently. The shared race clock includes later loading. Reset retries your current seed.",
            "Finishers is separate from seed count. Default 1 ends at the qualifying winner; increase it for more placements. Multi-seed skips add 30 minutes. Single-seed Forfeit loses with no time penalty.",
            "Options also opens Tournament. Choose a best-of bracket or scored rounds with placement points. Agree player order and byes before starting. Increase Finishers when awarding multiple places.",
            "Once everyone is in the room and agrees on the rules, the host starts the race. Worlds load before the shared start is released. Never share seeds during an ongoing race.",
            "Try the recording checkbox. In your real settings, enable it before a run to watch that run later. First use may download libraries. This practice checkbox will not start recording.",
            "Try each detail level in the menu. More detail helps the viewer follow what you saw and did, but costs recorder work and storage. Real changes affect the next recording; this is only practice.",
            "Try each camera rate in the menu. More look-angle samples can make first-person viewing smoother. Works with every detail level; real changes affect the next recording. Practice only.",
            "Try the checkbox for unfinished attempts when switching to an unrelated seed. Resets and assigned race-sequence stages stay in one replay either way. Your real preference stays unchanged.",
            "Browse opens the replay picker to watch, rename or delete recordings. Playback needs ZSG Replay Viewer and ReplayMod. In the viewer, use the timeline to seek and the camera menu to follow.",
            "Choose a folder for new recordings. Existing files are not moved; active recordings keep their current folder. Pick a location with enough free space, especially for longer, detailed runs.",
            "Recent Runs shows saved results and splits. You can reopen this tour in Settings, General. You are ready to create or join a room; the practice tour has changed no race settings."
    };
    private static final String[] RECORDING_ROWS = {
            "Record local worlds", "Recording detail", "Camera sampling", "Save on seed change", "Replay library"
    };
    private static final int[] RECORDING_INDICES = {0, 2, 3, 6, 7};
    private static final RoomSettingsScreen.Page[] CATEGORIES = {
            RoomSettingsScreen.Page.GENERAL, RoomSettingsScreen.Page.HUD, RoomSettingsScreen.Page.LOADING,
            RoomSettingsScreen.Page.RECORDING, RoomSettingsScreen.Page.ADVANCED
    };

    private final Screen parent;
    private final TutorialPreferences preferences;
    private final Map<Integer, PracticeChoice> practice = practiceChoices();
    private RoomRulePreset previewRules = RoomRulePreset.STANDARD_ZSG_ROOMS;
    final List<ButtonWidget> optionButtons = new ArrayList<>();
    Box optionMenu;
    private int optionFocus;
    int step = -1;
    boolean full;
    Screen backdrop;
    Box target, panel;
    private boolean unavailable;

    TutorialScreen(Screen parent, TutorialPreferences preferences) {
        super(new LiteralText("ZSG Rooms tutorial"));
        this.parent = parent;
        this.preferences = preferences;
    }

    int steps() { return full ? TITLES.length : QUICK_STEPS; }

    void showStep(int next) {
        if (next >= steps()) { onClose(); return; }
        step = next;
        init(client, width, height);
    }

    @Override protected void init() {
        closeOptions();
        unavailable = false;
        target = null;
        if (step < 0) {
            backdrop = new TitleBackdrop();
            backdrop.init(client, width, height);
            int w = Math.min(386, width - 24), h = Math.min(228, height - 12);
            panel = new Box((width - w) / 2, (height - h) / 2, w, h);
            int x = panel.x + 14, y = panel.y + 79, rowWidth = w - 28;
            choice(x, y, rowWidth, "No", "I'll explore on my own.", () -> choose(TutorialPreferences.Choice.NONE));
            choice(x, y + 44, rowWidth, "A quick one", "Settings, creating and joining rooms.", () -> choose(TutorialPreferences.Choice.QUICK));
            choice(x, y + 88, rowWidth, "Yes, full", "Also filters, rules, races and replays.", () -> choose(TutorialPreferences.Choice.FULL));
            return;
        }
        try {
            createBackdrop();
            backdrop.init(client, width, height);
            target = findTarget();
            panel = TutorialOverlay.panel(width, height, target, textRenderer, TITLES[step], body());
            if (!textFits()) throw new IllegalStateException("Tutorial text does not fit");
            addPracticeControl();
        } catch (RuntimeException error) {
            // Custom fonts or unusually small windows must not trap the player or crash the menu.
            ZsgRooms.LOGGER.warn("Tutorial step {} could not be laid out", step, error);
            unavailable = true;
            backdrop = null;
            target = null;
            int w = Math.min(300, width - 16), h = Math.min(180, height - 16);
            panel = new Box((width - w) / 2, (height - h) / 2, w, h);
        }
        TourButton back = addButton(new TourButton(panel.x + 12, panel.y + panel.height - 26, 64, 20,
                "Back", -1, () -> showStep(step - 1)));
        back.active = step > 0;
        addButton(new TourButton(panel.x + panel.width - 76, panel.y + panel.height - 26, 64, 20,
                step == steps() - 1 ? "Finish" : "Next", 1, () -> showStep(step + 1)));
        addButton(new TourButton(panel.x + panel.width - 24, panel.y + 4, 20, 20,
                "Close tour", 2, this::onClose));
    }

    private void createBackdrop() {
        Screen menu = new RoomMenuScreen(parent);
        switch (step) {
            case 0: backdrop = new TitleBackdrop(); break;
            case 1: case 3: case 20: backdrop = menu; break;
            case 2: backdrop = new RoomSettingsScreen(menu, CATEGORIES[practice.get(2).selected], 0); break;
            case 4: backdrop = RoomLobbyScreen.forTutorial(menu); break;
            case 5: backdrop = RoomEntryScreen.forTutorial(menu); break;
            case 6: case 7: case 12: case 13: backdrop = RoomLobbyScreen.forTutorial(menu); break;
            case 8: backdrop = new FilterPickerScreen(menu, "rooms-mix", ignored -> {}); break;
            case 9:
                RoomRulePreset p = previewRules;
                backdrop = new RoomGameRulesScreen(new RoomSetupScreen(menu, true), p.allowsCheats(),
                        p.standardizesRng(), p.boostsBarters(), p.guaranteesBastionIron(), p.removesBastionZombifiedPiglins(),
                        p.removesNaturalStriderJockeys(), p.spawnsNearFilterStructure(), p.guaranteesNearbyAnimals(),
                        p.warmsNetherEntry(), p.disablesPauseWorldSaves(), p.reducesZeroCycleFlyAways(),
                        p.sharesNetherEntry(), p.preventsTempleHostileSpawns(), RoomRulePreset.values()[practice.get(9).selected]);
                break;
            case 10: case 11: backdrop = new RaceFormatScreen(menu, 1, 1, 4, (s, f) -> {}); break;
            case 14: case 15: case 16: case 17: case 18:
                backdrop = new RoomSettingsScreen(menu, RoomSettingsScreen.Page.RECORDING, RECORDING_INDICES[step - 14]);
                break;
            case 19: backdrop = new ReplayStorageScreen(menu); break;
            default: throw new IllegalStateException("Unknown tutorial step");
        }
    }

    private Box findTarget() {
        boolean compactLobby = width < 560 || height < 360;
        switch (step) {
            case 0:
                for (Element e : backdrop.children()) if (e instanceof ImportSettingsButton) return bounds((AbstractButtonWidget) e);
                throw new IllegalStateException("Missing import button");
            case 1: return bounds(((RoomMenuScreen) backdrop).tutorialSettingsButton());
            case 2:
                String category = CATEGORIES[practice.get(2).selected].label;
                return bounds(find(category + (new PersonalSettingsLayout(width, height).sidebar ? "" : "  v")));
            case 3: return bounds(find("Create Room"));
            case 4: return bounds(find("Options"));
            case 5: return bounds(find("Room Code"));
            case 6: return bounds(find("Copy"));
            case 7: return union(bounds(find("Options")), bounds(find(compactLobby ? "Rules" : "Game Rules")));
            case 8:
                Box card = bounds(findStarts("ZSG Rooms Mode"));
                return width < 500 && card.height > 30 ? new Box(card.x, card.y + card.height - 24, card.width, 24) : card;
            case 9: return bounds(findStarts("Preset:"));
            case 10: return bounds(find("Seeds per race"));
            case 11: return bounds(find("Finishers"));
            case 12: return bounds(find("Options"));
            case 13: return bounds(find(compactLobby ? "Start" : "Start Race"));
            case 14: case 15: case 16: case 17: case 18:
                return bounds(((RoomSettingsScreen) backdrop).tutorialRow(RECORDING_ROWS[step - 14]));
            case 19: return bounds(findStarts("Choose"));
            case 20: return bounds(((RoomMenuScreen) backdrop).tutorialHistoryButton());
            default: throw new IllegalStateException();
        }
    }

    private void choose(TutorialPreferences.Choice choice) {
        remember(choice);
        if (choice == TutorialPreferences.Choice.NONE) client.openScreen(parent);
        else { full = choice == TutorialPreferences.Choice.FULL; showStep(0); }
    }

    private void remember(TutorialPreferences.Choice choice) {
        try { preferences.choose(choice); }
        catch (IOException error) { ZsgRooms.LOGGER.warn("Could not save tutorial choice; remembered for this session", error); }
    }

    private String body() {
        PracticeChoice choice = practice.get(step);
        return choice != null && choice.tried ? choice.details[choice.selected] : BODIES[step];
    }

    boolean hasPracticeControl() { return practice.containsKey(step); }

    private void addPracticeControl() {
        PracticeChoice choice = practice.get(step);
        if (choice == null) return;
        // These controls replace input, not the settings backend. Never invoke a backdrop callback.
        if (choice.toggle) {
            addButton(new CheckboxWidget(target.x, target.y, target.width, target.height,
                    new LiteralText(RECORDING_ROWS[step - 14]), choice.selected == 1, false) {
                @Override public void onPress() { selectOption(1 - choice.selected); }
            });
        } else {
            String label = step == 9 ? findStarts("Preset:").getMessage().getString()
                    : choice.labels[choice.selected] + "  v";
            addButton(new ButtonWidget(target.x, target.y, target.width, target.height,
                    new LiteralText(label), ignored -> openOptions()));
        }
    }

    private void selectOption(int index) {
        PracticeChoice choice = practice.get(step);
        choice.selected = index;
        choice.tried = true;
        if (step == 9 && !RoomRulePreset.values()[index].isCustom()) previewRules = RoomRulePreset.values()[index];
        init(client, width, height);
        clearStaleFocus();
    }

    private void openOptions() {
        closeOptions();
        PracticeChoice choice = practice.get(step);
        int w = target.width;
        for (String label : choice.labels) w = Math.max(w, textRenderer.getWidth(label) + 32);
        w = Math.min(w, width - 16);
        int h = choice.labels.length * 22 + 4;
        int x = Math.max(8, Math.min(target.x, width - w - 8));
        int y = target.y + target.height + 3;
        if (y + h > height - 8) y = Math.max(8, target.y - h - 3);
        optionMenu = new Box(x, y, w, h);
        optionFocus = choice.selected;
        for (int i = 0; i < choice.labels.length; i++) {
            final int index = i;
            optionButtons.add(new ButtonWidget(x + 2, y + 2 + i * 22, w - 4, 22,
                    new LiteralText(choice.labels[i]), ignored -> selectOption(index)) {
                @Override public void renderButton(MatrixStack m, int mouseX, int mouseY, float delta) {
                    boolean selected = choice.selected == index;
                    if (isHovered() || optionFocus == index) fill(m, this.x, this.y, this.x + getWidth(), this.y + getHeight(), 0xFF35474A);
                    if (selected) {
                        fill(m, this.x + 6, this.y + 10, this.x + 8, this.y + 12, 0xFF8FE2C8);
                        fill(m, this.x + 8, this.y + 12, this.x + 10, this.y + 14, 0xFF8FE2C8);
                        for (int n = 0; n < 5; n++) fill(m, this.x + 10 + n, this.y + 11 - n, this.x + 11 + n, this.y + 13 - n, 0xFF8FE2C8);
                    }
                    textRenderer.drawWithShadow(m, getMessage(), this.x + 22, this.y + 7, selected ? 0x8FE2C8 : 0xEDF4F1);
                }
            });
        }
    }

    private void closeOptions() {
        optionButtons.clear();
        optionMenu = null;
    }

    private static Map<Integer, PracticeChoice> practiceChoices() {
        Map<Integer, PracticeChoice> choices = new LinkedHashMap<>();
        String[] categories = new String[CATEGORIES.length];
        for (int i = 0; i < categories.length; i++) categories[i] = CATEGORIES[i].label;
        choices.put(2, new PracticeChoice(false, categories,
                "General is for importing or saving your setup, update checks and reopening this tutorial. Try another category, or choose Next. No settings are changed here.",
                "HUD changes the race overlay: its size, position and which player details appear. These are only for your screen. Try another category, or choose Next.",
                "Loading changes the logo, images and progress position. Your local Nether warmup switch can allow the room's preload rule, but cannot enable it for the host. Preview only.",
                "Recording is where you enable replays, choose capture quality and find your saved runs. The full tour explores these controls later. Try another category, or choose Next.",
                "Advanced contains service addresses, replay library setup and testing tools. Normal play does not need changes here. Try another category, or choose Next."));
        String[] presets = new String[RoomRulePreset.values().length];
        for (int i = 0; i < presets.length; i++) presets[i] = RoomRulePreset.values()[i].getLabel();
        choices.put(9, new PracticeChoice(false, presets,
                "Standard ZSG Rooms enables the room adjustments, including boosted barters, but not cheats. The rules behind this preview now show that preset. Your actual room is unchanged.",
                "Standard - Vanilla Barters keeps the standard room adjustments but uses normal piglin trades. Compare the rules behind this preview. Your actual room is unchanged.",
                "Raw turns off the preset's room adjustments. Use individual rule help to understand what changes. This preview does not change your room or guarantee run verification.",
                "Custom keeps the current rule choices so the host can tune them individually. In a real room, use the rule controls and their question-mark help. This is only a preview."));
        choices.put(14, new PracticeChoice(true, new String[]{"Off", "On"},
                "Off: no new Rooms replays are recorded. Existing recordings stay available to watch. Try the checkbox again, or choose Next. This is only practice.",
                "On: future local runs can be recorded. Returning to the room or title saves normally. First use may download libraries. This is practice; actual recording is still unchanged."));
        String[] detail = new String[ReplayPreferences.RecordingDetail.values().length];
        for (int i = 0; i < detail.length; i++) detail[i] = ReplayPreferences.RecordingDetail.values()[i].label;
        choices.put(15, new PracticeChoice(false, detail,
                "Standard keeps normal replay capture without extra Faithful Follow data. It is the lighter detail option. Camera sampling is separate. This preview is not saved.",
                "Detailed adds view settings and inventory/container changes so Faithful Follow can show screens as you used them. More recording work and storage. This preview is not saved.",
                "Super Detailed also captures first-person presentation and original player sounds for closer playback. The most demanding detail level, with larger files. Preview only."));
        String[] rates = new String[ReplayPreferences.CameraRate.values().length];
        for (int i = 0; i < rates.length; i++) rates[i] = ReplayPreferences.CameraRate.values()[i].label;
        choices.put(16, new PracticeChoice(false, rates,
                "Standard uses normal camera capture, without extra high-rate samples. The lightest camera option; recording detail is separate. This preview is not saved.",
                "60 Hz targets 60 look-angle samples a second for smoother first-person viewing. Limited by live FPS; adds recorder work and storage. This preview is not saved.",
                "120 Hz can smooth fast camera turns when you play at high FPS. More recorder work and storage than 60 Hz. This preview is not saved.",
                "240 Hz is for very high-FPS capture, limited by live FPS. Highest camera work and storage cost; it does not raise game FPS. This preview is not saved."));
        choices.put(17, new PracticeChoice(true, new String[]{"Off", "On"},
                "Off: unrelated seed changes discard unfinished attempts. Completed runs and room returns still save. Resets and assigned sequence stages stay together. This preview is not saved.",
                "On: unrelated seed changes save unfinished attempts as separate files, using more disk space. Resets and assigned sequence stages stay together. This preview is not saved."));
        return choices;
    }

    private static final class PracticeChoice {
        final boolean toggle;
        final String[] labels, details;
        int selected;
        boolean tried;
        PracticeChoice(boolean toggle, String[] labels, String... details) {
            if (labels.length != details.length) throw new IllegalArgumentException("Missing tutorial option help");
            this.toggle = toggle; this.labels = labels; this.details = details;
        }
    }

    private void choice(int x, int y, int w, String title, String detail, Runnable action) {
        addButton(new ButtonWidget(x, y, w, 38, new LiteralText(title), ignored -> action.run()) {
            @Override public void renderButton(MatrixStack m, int mouseX, int mouseY, float delta) {
                boolean focus = isHovered() || isFocused();
                fill(m, x, y, x + getWidth(), y + 38, focus ? 0xE02B363B : 0xA023272B);
                textRenderer.drawWithShadow(m, title, x + 12, y + 6, 0xFFFFFF);
                textRenderer.drawWithShadow(m, detail, x + 12, y + 23, 0xB7C5CB);
            }
        });
    }

    @Override public void render(MatrixStack m, int mouseX, int mouseY, float delta) {
        if (backdrop != null) backdrop.render(m, -1000, -1000, delta);
        else renderBackground(m);
        m.push();
        m.translate(0, 0, 400);
        RenderSystem.disableDepthTest();
        try {
            if (step < 0 || unavailable) {
                fill(m, 0, 0, width, height, 0xB807090B);
                fill(m, panel.x, panel.y, panel.x + panel.width, panel.y + panel.height, 0xF0181C20);
                int right = panel.x + panel.width, bottom = panel.y + panel.height;
                fill(m, panel.x, panel.y, right, panel.y + 1, 0x704F6666);
                fill(m, panel.x, bottom - 1, right, bottom, 0x704F6666);
                fill(m, panel.x, panel.y, panel.x + 1, bottom, 0x704F6666);
                fill(m, right - 1, panel.y, right, bottom, 0x704F6666);
                textRenderer.drawWithShadow(m, unavailable ? "Tutorial" : "WELCOME TO ZSG ROOMS", panel.x + 16, panel.y + 14, 0x8FE2C8);
                if (!unavailable) textRenderer.drawWithShadow(m, "Would you like a guided tour?", panel.x + 16, panel.y + 35, 0xFFFFFF);
                String message = unavailable ? "This step needs more space. Try a smaller GUI scale, or skip it with Next."
                        : "Get to know the mod and try its options. There is a little reading along the way.";
                int y = panel.y + (unavailable ? 38 : 52);
                for (StringRenderable line : textRenderer.wrapLines(new LiteralText(message), panel.width - 32)) {
                    textRenderer.drawWithShadow(m, line, panel.x + 16, y, 0xB7C5CB); y += 10;
                }
            } else {
                TutorialOverlay.render(m, textRenderer, width, height, target, panel,
                        TITLES[step], body(), step + 1, steps());
            }
            super.render(m, mouseX, mouseY, delta);
            for (Element e : children()) if (optionMenu == null && e instanceof TourButton && ((TourButton) e).glyph == 2
                    && ((TourButton) e).isHovered()) renderTooltip(m, new LiteralText("Close tour"), mouseX, mouseY);
            if (optionMenu != null) {
                Box b = optionMenu;
                fill(m, b.x - 1, b.y - 1, b.x + b.width + 1, b.y + b.height + 1, 0xFF586E6C);
                fill(m, b.x, b.y, b.x + b.width, b.y + b.height, 0xFF202628);
                for (ButtonWidget button : optionButtons) button.render(m, mouseX, mouseY, delta);
            }
        } finally { RenderSystem.enableDepthTest(); m.pop(); }
    }

    @Override public boolean mouseClicked(double x, double y, int button) {
        if (optionMenu != null) {
            if (button == 0) {
                for (ButtonWidget option : optionButtons) if (option.mouseClicked(x, y, button)) return true;
                closeOptions();
            }
            return true;
        }
        if (super.mouseClicked(x, y, button)) { clearStaleFocus(); return true; }
        if (step >= 0 && button == 0 && inside(target, x, y)) showStep(step + 1);
        return true;
    }
    @Override public boolean mouseScrolled(double x, double y, double amount) { return true; }
    @Override public boolean charTyped(char c, int modifiers) { return true; }
    @Override public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (optionMenu != null) {
            int count = optionButtons.size();
            if (key == GLFW.GLFW_KEY_ESCAPE) closeOptions();
            else if (key == GLFW.GLFW_KEY_DOWN || key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_TAB) {
                int direction = key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_TAB && (modifiers & GLFW.GLFW_MOD_SHIFT) != 0 ? -1 : 1;
                optionFocus = (optionFocus + direction + count) % count;
            } else if (key == GLFW.GLFW_KEY_HOME) optionFocus = 0;
            else if (key == GLFW.GLFW_KEY_END) optionFocus = count - 1;
            else if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER || key == GLFW.GLFW_KEY_SPACE) selectOption(optionFocus);
            return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; }
        boolean handled = super.keyPressed(key, scanCode, modifiers);
        clearStaleFocus();
        return handled;
    }
    private void clearStaleFocus() {
        // Mouse dispatch can restore the old button after its callback rebuilt this screen.
        if (getFocused() != null && !children().contains(getFocused())) {
            setFocused(null);
            setDragging(false);
        }
    }
    @Override public void onClose() {
        if (step < 0 && preferences.shouldPrompt()) remember(TutorialPreferences.Choice.NONE);
        client.openScreen(parent);
    }

    boolean textFits() {
        return !unavailable && (step < 0 || TutorialOverlay.fitsText(textRenderer, panel, TITLES[step], body()));
    }
    static boolean inside(Box b, double x, double y) {
        return b != null && x >= b.x && x < b.x + b.width && y >= b.y && y < b.y + b.height;
    }
    private AbstractButtonWidget find(String label) {
        for (Element e : backdrop.children()) if (e instanceof AbstractButtonWidget
                && ((AbstractButtonWidget) e).getMessage().getString().equals(label)) return (AbstractButtonWidget) e;
        throw new IllegalStateException("Missing tutorial control " + label);
    }
    private AbstractButtonWidget findStarts(String label) {
        for (Element e : backdrop.children()) if (e instanceof AbstractButtonWidget
                && ((AbstractButtonWidget) e).getMessage().getString().startsWith(label)) return (AbstractButtonWidget) e;
        throw new IllegalStateException("Missing tutorial control " + label);
    }
    private static Box bounds(AbstractButtonWidget b) { return new Box(b.x, b.y, b.getWidth(), b.getHeight()); }
    private static Box union(Box a, Box b) {
        int x = Math.min(a.x, b.x), y = Math.min(a.y, b.y);
        return new Box(x, y, Math.max(a.x + a.width, b.x + b.width) - x, Math.max(a.y + a.height, b.y + b.height) - y);
    }

    /** Reuses Minecraft assets without initializing title services, updates or Realms. */
    private static final class TitleBackdrop extends Screen {
        private final RotatingCubeMapRenderer panorama = new RotatingCubeMapRenderer(TitleScreen.PANORAMA_CUBE_MAP);
        private static final Identifier LOGO = new Identifier("textures/gui/title/minecraft.png");

        TitleBackdrop() { super(new LiteralText("ZSG Rooms")); }

        @Override protected void init() {
            int w = Math.min(200, width - 64), x = (width - w) / 2, y = Math.max(10, height / 4 + 24);
            addButton(new ButtonWidget(x, y, w, 20, new LiteralText("ZSG Rooms"), b -> {}));
            addButton(new ImportSettingsButton(x + w + 4, y, b -> {}));
            String[] labels = {"menu.singleplayer", "menu.multiplayer", "menu.online"};
            for (int i = 0; i < labels.length; i++) {
                addButton(new ButtonWidget(x, y + 24 * (i + 1), w, 20, new TranslatableText(labels[i]), b -> {}));
            }
        }

        @Override public void render(MatrixStack m, int mouseX, int mouseY, float delta) {
            panorama.render(delta, 1.0F);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.color4f(1, 1, 1, 1);
            client.getTextureManager().bindTexture(LOGO);
            drawTexture(m, width / 2 - 155, 30, 0, 0, 155, 44);
            drawTexture(m, width / 2, 30, 0, 45, 155, 44);
            super.render(m, mouseX, mouseY, delta);
        }
    }

    private final class TourButton extends ButtonWidget {
        final int glyph;
        TourButton(int x, int y, int w, int h, String label, int glyph, Runnable action) {
            super(x, y, w, h, new LiteralText(label), ignored -> action.run()); this.glyph = glyph;
        }
        @Override public void renderButton(MatrixStack m, int mouseX, int mouseY, float delta) {
            boolean hover = active && (isHovered() || isFocused());
            fill(m, x, y, x + getWidth(), y + getHeight(), hover ? 0xFF39494F : 0xA0272E33);
            int color = active ? (glyph == 1 ? 0x8FE2C8 : 0xDEE7EB) : 0x626D73;
            if (glyph == 2) {
                for (int i = 0; i < 7; i++) {
                    fill(m, x + 7 + i, y + 7 + i, x + 8 + i, y + 8 + i, 0xFF000000 | color);
                    fill(m, x + 13 - i, y + 7 + i, x + 14 - i, y + 8 + i, 0xFF000000 | color);
                }
            } else textRenderer.drawWithShadow(m, getMessage(), x + (getWidth() - textRenderer.getWidth(getMessage())) / 2F, y + 6, color);
        }
    }
}
