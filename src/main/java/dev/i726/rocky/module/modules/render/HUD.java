package dev.i726.rocky.module.modules.render;

import dev.i726.rocky.Rocky;
import dev.i726.rocky.event.events.ButtonListener;
import dev.i726.rocky.event.events.HudListener;
import dev.i726.rocky.gui.ClickGuiScreen;
import dev.i726.rocky.gui.HudEditorScreen;
import dev.i726.rocky.gui.GuiTheme;
import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.module.setting.BooleanSetting;
import dev.i726.rocky.module.setting.KeybindSetting;
import dev.i726.rocky.utils.EncryptedString;
import dev.i726.rocky.utils.NotificationManager;
import dev.i726.rocky.utils.TextRenderer;
import org.lwjgl.glfw.GLFW;

import java.awt.Color;
import java.io.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

public final class HUD extends Module implements HudListener, ButtonListener {

    // ── Panel IDs ────────────────────────────────────────────────────────────
    public static final int P_INFO    = 0;
    public static final int P_COORDS  = 1;
    public static final int P_ARMOR   = 2;
    public static final int P_POTIONS = 3;
    public static final int P_MODULES = 4;
    public static final int NUM_PANELS = 5;
    public static final String[] PANEL_NAMES = {
            "Info Bar", "Coords / BPS", "Armor", "Potions", "Module List"
    };

    /** Approximate panel sizes used by the HUD editor drag handles. */
    public static final int[] APPROX_W = { 220, 200, 140, 170, 150 };
    public static final int[] APPROX_H = { 24,  24,   74,  80, 200 };

    // ── Stored positions (NaN = use default layout) ───────────────────────
    public static float[] storedX = new float[NUM_PANELS];
    public static float[] storedY = new float[NUM_PANELS];

    static {
        Arrays.fill(storedX, Float.NaN);
        Arrays.fill(storedY, Float.NaN);
    }

    private static final File POSITIONS_FILE = new File("rocky/hud_positions.txt");

    public static void loadPositions() {
        if (!POSITIONS_FILE.exists()) return;
        try (BufferedReader br = new BufferedReader(new FileReader(POSITIONS_FILE))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length != 3) continue;
                int id = Integer.parseInt(parts[0].trim());
                if (id < 0 || id >= NUM_PANELS) continue;
                storedX[id] = Float.parseFloat(parts[1].trim());
                storedY[id] = Float.parseFloat(parts[2].trim());
            }
        } catch (Exception ignored) {}
    }

    public static void savePositions() {
        try {
            POSITIONS_FILE.getParentFile().mkdirs();
            try (PrintWriter pw = new PrintWriter(new FileWriter(POSITIONS_FILE))) {
                for (int i = 0; i < NUM_PANELS; i++) {
                    if (!Float.isNaN(storedX[i]) || !Float.isNaN(storedY[i])) {
                        pw.println(i + "," + storedX[i] + "," + storedY[i]);
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    /** Returns stored X if set, otherwise the given default. */
    public static int px(int id, int def) {
        return Float.isNaN(storedX[id]) ? def : (int) storedX[id];
    }
    /** Returns stored Y if set, otherwise the given default. */
    public static int py(int id, int def) {
        return Float.isNaN(storedY[id]) ? def : (int) storedY[id];
    }

    // ── Settings ──────────────────────────────────────────────────────────
    private final BooleanSetting info = new BooleanSetting(
            EncryptedString.of("Info Bar"), true)
            .setDescription(EncryptedString.of("Shows ROCKY / FPS / Ping / Server top-left"));

    private final BooleanSetting showFps = new BooleanSetting(
            EncryptedString.of("  FPS"), true)
            .setDescription(EncryptedString.of("Show frames per second in the info bar"));

    private final BooleanSetting showPing = new BooleanSetting(
            EncryptedString.of("  Ping"), true)
            .setDescription(EncryptedString.of("Show network latency in the info bar"));

    private final BooleanSetting showServer = new BooleanSetting(
            EncryptedString.of("  Server"), true)
            .setDescription(EncryptedString.of("Show server address in the info bar"));

    private final BooleanSetting clock = new BooleanSetting(
            EncryptedString.of("  Clock"), true)
            .setDescription(EncryptedString.of("Show real-world time in the info bar"));

    private final BooleanSetting modules = new BooleanSetting(
            EncryptedString.of("Modules"), true)
            .setDescription(EncryptedString.of("Renders the enabled module list top-right"));

    private final BooleanSetting coords = new BooleanSetting(
            EncryptedString.of("Coordinates"), true)
            .setDescription(EncryptedString.of("Shows XYZ position and facing direction"));

    private final BooleanSetting bpsHud = new BooleanSetting(
            EncryptedString.of("  BPS"), true)
            .setDescription(EncryptedString.of("Blocks-per-second speed meter"));

    private final BooleanSetting armorHud = new BooleanSetting(
            EncryptedString.of("Armor"), true)
            .setDescription(EncryptedString.of("Shows armor piece durability bars"));

    private final BooleanSetting effectsHud = new BooleanSetting(
            EncryptedString.of("Potions"), true)
            .setDescription(EncryptedString.of("Lists active potion effects and remaining durations"));

    private final BooleanSetting toasts = new BooleanSetting(
            EncryptedString.of("Notifications"), true)
            .setDescription(EncryptedString.of("Shows toast pop-ups when modules toggle"));

    private final KeybindSetting editorKey = new KeybindSetting(
            EncryptedString.of("HUD Editor Key"), GLFW.GLFW_KEY_KP_0, false)
            .setDescription(EncryptedString.of("Open the HUD layout editor to drag panels"));

    // ── State ─────────────────────────────────────────────────────────────
    private Vec3 lastPos;
    private long  lastPosTime;
    private double currentBps;

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    public HUD() {
        super(EncryptedString.of("HUD"),
                EncryptedString.of("Heads-up display"),
                -1,
                CategoryManager.GUI);
        addSettings(info, showFps, showPing, showServer, clock,
                modules, coords, bpsHud, armorHud, effectsHud, toasts, editorKey);
    }

    @Override
    public void onEnable() {
        // Defensive: remove before add so a double-enable never leaves two copies registered
        eventManager.remove(HudListener.class, this);
        eventManager.remove(ButtonListener.class, this);
        eventManager.add(HudListener.class, this);
        eventManager.add(ButtonListener.class, this);
        loadPositions();
        lastPos     = null;
        lastPosTime = System.currentTimeMillis();
        super.onEnable();
    }

    @Override
    public void onDisable() {
        eventManager.remove(HudListener.class, this);
        eventManager.remove(ButtonListener.class, this);
        super.onDisable();
    }

    // ── ButtonListener — open HUD editor ─────────────────────────────────
    @Override
    public void onButtonPress(ButtonEvent event) {
        if (event.action != GLFW.GLFW_PRESS) return;
        if (event.button != editorKey.getKey()) return;
        if (mc != null && !(mc.screen instanceof HudEditorScreen)) {
            mc.setScreen(new HudEditorScreen());
        }
    }

    // ── HUD Render ────────────────────────────────────────────────────────
    @Override
    public void onRenderHud(HudEvent event) {
        try {
            if (mc == null) return;
            if (mc.screen instanceof ClickGuiScreen) return;
            if (mc.screen instanceof HudEditorScreen) return;
            renderHudInternal(event);
        } catch (Exception ignored) {
            // Prevent rendering exceptions from propagating to the event system.
            // The mc null check must live inside here — a NPE before the try-catch
            // would escape the listener and could leave the render frame broken.
        }
    }

    private void renderHudInternal(HudEvent event) {

        GuiGraphicsExtractor ctx       = event.context;
        Color       ac        = GuiTheme.accent();
        int         accentInt = GuiTheme.accentInt();
        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();

        updateBps();

        // ── 1. Info Bar ────────────────────────────────────────────────────
        if (info.getValue() && mc.player != null) {
            StringBuilder sfx = new StringBuilder();
            if (showFps.getValue()) {
                sfx.append("  |  ").append(mc.getFps()).append(" FPS");
            }
            if (showPing.getValue() && mc.getConnection() != null) {
                PlayerInfo entry = mc.getConnection().getPlayerInfo(mc.player.getUUID());
                String ping = entry != null ? entry.getLatency() + "ms" : "0ms";
                sfx.append("  |  ").append(ping);
            }
            if (showServer.getValue()) {
                String server = mc.getCurrentServer() == null
                        ? "Singleplayer" : mc.getCurrentServer().ip;
                sfx.append("  |  ").append(server);
            }
            if (clock.getValue()) {
                sfx.append("  |  ").append(LocalTime.now().format(TIME_FMT));
            }

            String suffix = sfx.toString();
            int rockyW = TextRenderer.getWidth("ROCKY");
            int bw = rockyW + TextRenderer.getWidth(suffix) + 20;
            int bh = 20;
            int bx = px(P_INFO, 8), by = py(P_INFO, 8);

            ctx.fill(bx + 2, by + 2, bx + bw + 2, by + bh + 2, GuiTheme.rgba(0, 0, 0, 50));
            ctx.fill(bx - 1, by - 1, bx + bw + 1, by + bh + 1, GuiTheme.border());
            ctx.fill(bx, by, bx + bw, by + bh, GuiTheme.panelBg());
            ctx.fill(bx, by, bx + 3, by + bh, accentInt);
            ctx.fillGradient(bx + 3, by, bx + bw, by + 1,
                    GuiTheme.rgba(ac.getRed(), ac.getGreen(), ac.getBlue(), 70),
                    GuiTheme.rgba(ac.getRed(), ac.getGreen(), ac.getBlue(), 0));
            TextRenderer.text("ROCKY",  ctx, bx + 9,           by + 6, accentInt);
            TextRenderer.text(suffix,   ctx, bx + 9 + rockyW,  by + 6, GuiTheme.textPrimary());
        }

        // ── 2. Coordinates / BPS ──────────────────────────────────────────
        if ((coords.getValue() || bpsHud.getValue()) && mc.player != null) {
            List<String> lines = new ArrayList<>();
            if (coords.getValue()) {
                int x = (int) mc.player.getX();
                int y = (int) mc.player.getY();
                int z = (int) mc.player.getZ();
                lines.add("XYZ  " + x + " / " + y + " / " + z + "  [" + getFacing(mc.player.yRot()) + "]");
            }
            if (bpsHud.getValue()) {
                lines.add("BPS  " + String.format("%.1f", currentBps));
            }
            if (!lines.isEmpty()) {
                int bw = lines.stream().mapToInt(TextRenderer::getWidth).max().orElse(0) + 20;
                int bh = lines.size() * 13 + 8;
                int bx = px(P_COORDS, 8), by = py(P_COORDS, 34);

                ctx.fill(bx + 2, by + 2, bx + bw + 2, by + bh + 2, GuiTheme.rgba(0, 0, 0, 40));
                ctx.fill(bx - 1, by - 1, bx + bw + 1, by + bh + 1, GuiTheme.border());
                ctx.fill(bx, by, bx + bw, by + bh, GuiTheme.panelBg());
                ctx.fill(bx, by, bx + 3, by + bh, accentInt);
                for (int i = 0; i < lines.size(); i++)
                    TextRenderer.text(lines.get(i), ctx, bx + 9, by + 5 + i * 13, GuiTheme.textPrimary());
            }
        }

        // ── 3. Armor Durability ────────────────────────────────────────────
        if (armorHud.getValue() && mc.player != null) {
            String[] labels = {"Helm", "Chest", "Legs", "Boots"};
            ItemStack[] display = {
                    mc.player.getInventory().getItem(39),
                    mc.player.getInventory().getItem(38),
                    mc.player.getInventory().getItem(37),
                    mc.player.getInventory().getItem(36)
            };
            final int rowH  = 13;
            final int bw    = 115;
            final int headH = 14;
            int bh = headH + 4 * rowH + 6;
            int bx = px(P_ARMOR, 8), by = py(P_ARMOR, screenH - bh - 8);

            ctx.fill(bx + 2, by + 2, bx + bw + 2, by + bh + 2, GuiTheme.rgba(0, 0, 0, 50));
            ctx.fill(bx - 1, by - 1, bx + bw + 1, by + bh + 1, GuiTheme.border());
            ctx.fill(bx, by, bx + bw, by + bh, GuiTheme.panelBg());
            ctx.fill(bx, by, bx + 3, by + bh, accentInt);

            // Header
            ctx.fill(bx + 3, by, bx + bw, by + headH, GuiTheme.rgba(ac.getRed(), ac.getGreen(), ac.getBlue(), 18));
            ctx.fill(bx + 3, by + headH - 1, bx + bw, by + headH, GuiTheme.rgba(ac.getRed(), ac.getGreen(), ac.getBlue(), 60));
            TextRenderer.text("ARMOR", ctx, bx + 9, by + 3, accentInt);

            for (int i = 0; i < 4; i++) {
                ItemStack stack = display[i];
                int rowY = by + headH + 2 + i * rowH;

                if (stack.isEmpty()) {
                    TextRenderer.text(labels[i], ctx, bx + 24, rowY + 5, GuiTheme.textSecondary());
                    TextRenderer.text("--", ctx, bx + bw - 18, rowY + 5, GuiTheme.textSecondary());
                    continue;
                }

                ctx.fakeItem(stack, bx + 5, rowY + 1);

                int maxDmg = stack.getMaxDamage();
                int curDmg = stack.getDamageValue();
                float pct  = maxDmg > 0 ? (float)(maxDmg - curDmg) / maxDmg : 1f;
                Color valC = pct > 0.5f ? new Color(34, 197, 94)
                           : pct > 0.25f ? new Color(249, 115, 22)
                           : new Color(239, 68, 68);

                // Label left, value right — no bar
                TextRenderer.text(labels[i], ctx, bx + 24, rowY + 5, GuiTheme.textSecondary());
                String pctStr = (int)(pct * 100) + "%";
                int pctW = TextRenderer.getWidth(pctStr);
                TextRenderer.text(pctStr, ctx, bx + bw - pctW - 6, rowY + 5,
                        GuiTheme.rgba(valC.getRed(), valC.getGreen(), valC.getBlue(), 230));
            }
        }

        // ── 4. Potion Effects ──────────────────────────────────────────────
        if (effectsHud.getValue() && mc.player != null && !mc.player.getActiveEffects().isEmpty()) {
            List<MobEffectInstance> effects = new ArrayList<>(mc.player.getActiveEffects());
            final int rowH  = 13;
            final int bw    = 145;
            final int headH = 14;
            int bh = headH + effects.size() * rowH + 6;
            int armorOffset = armorHud.getValue()
                    ? (14 + 4 * 13 + 6) + 4   // headH + 4*rowH + padding + gap
                    : 0;
            int bx = px(P_POTIONS, 8), by = py(P_POTIONS, screenH - bh - 8 - armorOffset);

            ctx.fill(bx + 2, by + 2, bx + bw + 2, by + bh + 2, GuiTheme.rgba(0, 0, 0, 50));
            ctx.fill(bx - 1, by - 1, bx + bw + 1, by + bh + 1, GuiTheme.border());
            ctx.fill(bx, by, bx + bw, by + bh, GuiTheme.panelBg());
            ctx.fill(bx, by, bx + 3, by + bh, accentInt);

            // Header
            ctx.fill(bx + 3, by, bx + bw, by + headH, GuiTheme.rgba(ac.getRed(), ac.getGreen(), ac.getBlue(), 18));
            ctx.fill(bx + 3, by + headH - 1, bx + bw, by + headH, GuiTheme.rgba(ac.getRed(), ac.getGreen(), ac.getBlue(), 60));
            TextRenderer.text("EFFECTS", ctx, bx + 9, by + 3, accentInt);

            for (int i = 0; i < effects.size(); i++) {
                MobEffectInstance fx = effects.get(i);
                int rowY = by + headH + 2 + i * rowH;

                int durSec = fx.getDuration() / 20;
                Color timeC = durSec > 60 ? new Color(34, 197, 94)
                            : durSec > 20 ? new Color(249, 115, 22)
                            :               new Color(239, 68, 68);

                // Effect icon from GUI atlas (mob_effect/<name>)
                var effect = fx.getEffect().value();
                var iconId = BuiltInRegistries.MOB_EFFECT.getKey(effect);
                if (iconId != null) {
                    try {
                        Identifier spriteId = Identifier.fromNamespaceAndPath(iconId.getNamespace(), "mob_effect/" + iconId.getPath());
                        ctx.blitSprite(RenderPipelines.GUI_TEXTURED, spriteId, bx + 5, rowY + 1, 10, 10);
                    } catch (Exception ignored) {}
                }

                // Effect name (left, after icon)
                String efName = effect.getDisplayName().getString();
                int lvl = fx.getAmplifier() + 1;
                String label = lvl > 1 ? efName + " " + toRoman(lvl) : efName;
                TextRenderer.text(label, ctx, bx + 19, rowY + 2, GuiTheme.textSecondary());

                // Time remaining (colored, right)
                String time = durSec >= 60
                        ? (durSec / 60) + "m " + (durSec % 60) + "s"
                        : durSec + "s";
                int timeW = TextRenderer.getWidth(time);
                TextRenderer.text(time, ctx, bx + bw - timeW - 6, rowY + 2,
                        GuiTheme.rgba(timeC.getRed(), timeC.getGreen(), timeC.getBlue(), 230));
            }
        }

        // ── 5. Module Arraylist ────────────────────────────────────────────
        if (modules.getValue()) {
            List<Module> enabled = Rocky.INSTANCE.getModuleManager().getModules().stream()
                    .filter(Module::isEnabled)
                    .sorted((a, b) -> Integer.compare(
                            TextRenderer.getWidth(b.getName()),
                            TextRenderer.getWidth(a.getName())))
                    .toList();

            int accentBarW = 3, paddingX = 8, entryH = 18, gap = 2;
            int startY = py(P_MODULES, 8);
            boolean hasStoredX = !Float.isNaN(storedX[P_MODULES]);

            for (Module mod : enabled) {
                String mname = mod.getName().toString();
                int textW    = TextRenderer.getWidth(mname);
                int entryW   = textW + paddingX * 2 + accentBarW;

                int ex, er;
                if (hasStoredX) {
                    ex = (int) storedX[P_MODULES];
                    er = ex + entryW;
                } else {
                    ex = screenW - entryW - 1;
                    er = screenW - 1;
                }
                int ey = startY;

                ctx.fill(ex + 2, ey + 2, er + 2, ey + entryH + 2, GuiTheme.rgba(0, 0, 0, 45));
                ctx.fill(ex - 1, ey - 1, er + 1, ey + entryH + 1, GuiTheme.border());
                ctx.fill(ex, ey, er, ey + entryH, GuiTheme.panelBg());
                ctx.fill(er - accentBarW, ey, er, ey + entryH, accentInt);
                ctx.fillGradient(ex, ey, er - accentBarW, ey + 1,
                        GuiTheme.rgba(ac.getRed(), ac.getGreen(), ac.getBlue(), 0),
                        GuiTheme.rgba(ac.getRed(), ac.getGreen(), ac.getBlue(), 70));
                TextRenderer.text(mname, ctx, ex + paddingX, ey + 5, GuiTheme.textPrimary());
                startY += entryH + gap;
            }
        }

        // ── 6. Toast Notifications ─────────────────────────────────────────
        if (toasts.getValue()) {
            NotificationManager.render(ctx, screenH);
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private void updateBps() {
        if (mc.player == null) { lastPos = null; return; }
        long  now = System.currentTimeMillis();
        Vec3 pos = new Vec3(mc.player.getX(), mc.player.getY(), mc.player.getZ());
        if (lastPos != null && now > lastPosTime) {
            double dist = Math.sqrt(
                    Math.pow(pos.x - lastPos.x, 2) + Math.pow(pos.z - lastPos.z, 2));
            double raw = dist / ((now - lastPosTime) / 1000.0);
            currentBps = currentBps * 0.85 + raw * 0.15;
        }
        lastPos     = pos;
        lastPosTime = now;
    }

    private static String getFacing(float yaw) {
        yaw = ((yaw % 360) + 360) % 360;
        if (yaw < 22.5 || yaw >= 337.5) return "S";
        if (yaw < 67.5)  return "SW";
        if (yaw < 112.5) return "W";
        if (yaw < 157.5) return "NW";
        if (yaw < 202.5) return "N";
        if (yaw < 247.5) return "NE";
        if (yaw < 292.5) return "E";
        return "SE";
    }

    private static String toRoman(int n) {
        if (n <= 0)  return String.valueOf(n);
        if (n == 1)  return "I";
        if (n == 2)  return "II";
        if (n == 3)  return "III";
        if (n == 4)  return "IV";
        if (n == 5)  return "V";
        if (n == 6)  return "VI";
        if (n == 7)  return "VII";
        if (n == 8)  return "VIII";
        if (n == 9)  return "IX";
        if (n == 10) return "X";
        return String.valueOf(n);
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        String[] words = s.split(" ");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (!w.isEmpty()) sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(" ");
        }
        return sb.toString().trim();
    }
}
