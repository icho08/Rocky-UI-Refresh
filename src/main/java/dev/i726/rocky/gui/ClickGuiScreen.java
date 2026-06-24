package dev.i726.rocky.gui;

import dev.i726.rocky.Rocky;
import dev.i726.rocky.gui.components.CategoryPanel;
import dev.i726.rocky.managers.ProfileManager;
import dev.i726.rocky.module.Category;
import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.module.modules.client.BlatantModules;
import dev.i726.rocky.module.modules.client.FpsModules;
import org.lwjgl.glfw.GLFW;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public class ClickGuiScreen extends Screen {

    private static final List<CategoryPanel> panels = new ArrayList<>();
    private static boolean initialized = false;
    private static ClickGuiScreen currentInstance = null;

    // Tooltip queued by ModuleRow during render — drawn on top of everything
    private static String pendingTooltip    = null;
    private static int    pendingTooltipX   = 0;
    private static int    pendingTooltipY   = 0;

    private static final int BAR_Y  = 5;
    private static final int BAR_H  = 28;
    private static final int SB_W   = 170;
    private static final int SB_PAD = 8;

    private String  searchQuery  = "";
    private boolean searchFocused = false;

    public ClickGuiScreen() {
        super(Component.literal("Rocky"));
    }

    /** Called by ModuleRow during render to schedule a tooltip for the end of the frame. */
    public static void queueTooltip(String text, int mouseX, int mouseY) {
        pendingTooltip  = text;
        pendingTooltipX = mouseX;
        pendingTooltipY = mouseY;
    }

    /** Re-build the panel list (e.g. after BlatantModules is toggled). */
    public static void refreshPanels() {
        initialized = false;
        panels.clear();
        if (currentInstance != null) {
            currentInstance.setupPanels();
            initialized = true;
        }
    }

    /** Adds the Blatant panel without destroying any other panels. */
    public static void addBlatantPanel() {
        initialized = false;
        if (currentInstance == null) return;
        boolean exists = panels.stream()
                .anyMatch(p -> p.getName().equals(CategoryManager.BLATANT.getName()));
        if (exists) return;
        List<Module> mods = currentInstance.getModulesForCategory(CategoryManager.BLATANT);
        if (mods.isEmpty()) return;
        float px = 8 + panels.size() * (CategoryPanel.WIDTH + 5);
        float py = 42;
        ProfileManager pm = Rocky.INSTANCE.getProfileManager();
        double[] saved = pm.getPanelPosition(CategoryManager.BLATANT.getName());
        if (saved != null) { px = (float) saved[0]; py = (float) saved[1]; }
        panels.add(new CategoryPanel(CategoryManager.BLATANT.getName(), mods, px, py));
    }

    /** Removes the Blatant panel without touching the rest. */
    public static void removeBlatantPanel() {
        initialized = false;
        panels.removeIf(p -> p.getName().equals(CategoryManager.BLATANT.getName()));
    }

    /** Adds the FPS panel without destroying any other panels. */
    public static void addFpsPanel() {
        initialized = false;
        if (currentInstance == null) return;
        boolean exists = panels.stream()
                .anyMatch(p -> p.getName().equals(CategoryManager.FPS.getName()));
        if (exists) return;
        List<Module> mods = currentInstance.getModulesForCategory(CategoryManager.FPS);
        if (mods.isEmpty()) return;
        float px = 8 + panels.size() * (CategoryPanel.WIDTH + 5);
        float py = 42;
        ProfileManager pm = Rocky.INSTANCE.getProfileManager();
        double[] saved = pm.getPanelPosition(CategoryManager.FPS.getName());
        if (saved != null) { px = (float) saved[0]; py = (float) saved[1]; }
        panels.add(new CategoryPanel(CategoryManager.FPS.getName(), mods, px, py));
    }

    /** Removes the FPS panel without touching the rest. */
    public static void removeFpsPanel() {
        initialized = false;
        panels.removeIf(p -> p.getName().equals(CategoryManager.FPS.getName()));
    }

    @Override
    protected void init() {
        currentInstance = this;
        if (!initialized) {
            setupPanels();
            initialized = true;
        } else {
            for (CategoryPanel panel : panels)
                panel.clampToScreen(this.width, this.height);
        }
    }

    private void setupPanels() {
        panels.clear();

        boolean showBlatant = Rocky.INSTANCE.moduleManager.getModule(BlatantModules.class) != null
                && Rocky.INSTANCE.moduleManager.getModule(BlatantModules.class).isEnabled();
        boolean showFps = Rocky.INSTANCE.moduleManager.getModule(FpsModules.class) != null
                && Rocky.INSTANCE.moduleManager.getModule(FpsModules.class).isEnabled();

        List<Category> topLevel = new ArrayList<>(Arrays.asList(
                CategoryManager.COMBAT,
                CategoryManager.PLAYER,
                CategoryManager.VISUAL,
                CategoryManager.MISC
        ));
        if (showFps) topLevel.add(CategoryManager.FPS);
        if (showBlatant) topLevel.add(CategoryManager.BLATANT);

        ProfileManager pm = Rocky.INSTANCE.getProfileManager();
        int startX = Math.max(8, this.width / 2 - 295);
        int startY = 42;
        for (int i = 0; i < topLevel.size(); i++) {
            Category cat = topLevel.get(i);
            List<Module> catModules = getModulesForCategory(cat);
            if (!catModules.isEmpty()) {
                float px = startX + i * (CategoryPanel.WIDTH + 5);
                float py = startY;
                double[] saved = pm.getPanelPosition(cat.getName());
                if (saved != null) {
                    px = (float) saved[0];
                    py = (float) saved[1];
                }
                panels.add(new CategoryPanel(cat.getName(), catModules, px, py));
            }
        }
    }

    private List<Module> getModulesForCategory(Category cat) {
        List<Module> result = new ArrayList<>();
        for (Module m : Rocky.INSTANCE.moduleManager.getModules()) {
            Category mCat = m.getCategory();
            if (mCat == null) continue;
            if (mCat.equals(cat) || (mCat.getParent() != null && mCat.getParent().equals(cat)))
                result.add(m);
        }
        return result;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        pendingTooltip = null;

        ctx.fill(0, 0, this.width, this.height, GuiTheme.rgba(5, 4, 10, 175));

        renderTopBar(ctx, mouseX, mouseY);

        for (CategoryPanel panel : panels)
            panel.render(ctx, mouseX, mouseY, delta);

        // Draw tooltip on top of all panels
        if (pendingTooltip != null) {
            renderTooltip(ctx, pendingTooltip, mouseX, mouseY);
            pendingTooltip = null;
        }
    }

    private void renderTooltip(GuiGraphicsExtractor ctx, String text, int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getInstance();
        int maxWidth = 160;

        // Word-wrap
        List<String> lines = wrapText(mc, text, maxWidth - 12);

        int lineH   = mc.font.lineHeight + 2;
        int boxW    = maxWidth;
        int boxH    = 6 + lines.size() * lineH + 2;

        // Position: right of cursor, clamped to screen
        int tx = mouseX + 10;
        int ty = mouseY - 4;
        if (tx + boxW > this.width  - 4) tx = mouseX - boxW - 4;
        if (ty + boxH > this.height - 4) ty = this.height - boxH - 4;
        if (ty < 4) ty = 4;

        Color ac = GuiTheme.accent();

        // Shadow
        ctx.fill(tx + 2, ty + 2, tx + boxW + 2, ty + boxH + 2, GuiTheme.rgba(0, 0, 0, 60));
        // Border
        ctx.fill(tx - 1, ty - 1, tx + boxW + 1, ty + boxH + 1,
                GuiTheme.rgba(ac.getRed(), ac.getGreen(), ac.getBlue(), 100));
        // Background
        ctx.fill(tx, ty, tx + boxW, ty + boxH, GuiTheme.rgba(10, 9, 18, 240));
        // Left accent stripe
        ctx.fill(tx, ty, tx + 2, ty + boxH, GuiTheme.accentInt());
        // Subtle top gradient tint
        ctx.fillGradient(tx, ty, tx + boxW, ty + Math.min(boxH, 12),
                GuiTheme.rgba(ac.getRed(), ac.getGreen(), ac.getBlue(), 25),
                GuiTheme.rgba(ac.getRed(), ac.getGreen(), ac.getBlue(), 0));

        // Text lines
        int textX = tx + 8;
        int textY = ty + 4;
        for (String line : lines) {
            ctx.text(mc.font, line, textX, textY, GuiTheme.rgba(190, 186, 220, 255), false);
            textY += lineH;
        }
    }

    private List<String> wrapText(Minecraft mc, String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        String[] words = text.split(" ");
        StringBuilder current = new StringBuilder();
        for (String word : words) {
            String test = current.isEmpty() ? word : current + " " + word;
            if (mc.font.width(test) <= maxWidth) {
                current = new StringBuilder(test);
            } else {
                if (!current.isEmpty()) lines.add(current.toString());
                current = new StringBuilder(word);
            }
        }
        if (!current.isEmpty()) lines.add(current.toString());
        return lines.isEmpty() ? List.of(text) : lines;
    }

    private void renderTopBar(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getInstance();
        Color ac = GuiTheme.accent();
        int acInt = GuiTheme.accentInt();

        // ── Floating "Rocky Client" title — no background ───────────────────
        int ty = BAR_Y + 10;
        ctx.text(mc.font, "Rocky", 12, ty, acInt, true);
        int rockyW = mc.font.width("Rocky");
        ctx.text(mc.font, " Client", 12 + rockyW, ty, GuiTheme.textPrimary(), true);
        int clientW = mc.font.width(" Client");
        String ver = Rocky.INSTANCE.getVersion().trim();
        ctx.text(mc.font, "  " + ver,
                12 + rockyW + clientW, ty,
                GuiTheme.rgba(90, 86, 128, 255), true);

        // ── Search bar ──────────────────────────────────────────────────────
        int sbX = this.width - SB_W - SB_PAD;
        int sbY = BAR_Y + 4;
        int sbH = BAR_H - 8;

        boolean hoveringSearch = mouseX >= sbX && mouseX < sbX + SB_W
                              && mouseY >= sbY && mouseY < sbY + sbH;

        // Border: always accent-tinted, brighter when focused/hovered
        int borderAlpha = searchFocused ? 200 : hoveringSearch ? 110 : 60;
        ctx.fill(sbX - 1, sbY - 1, sbX + SB_W + 1, sbY + sbH + 1,
                GuiTheme.rgba(ac.getRed(), ac.getGreen(), ac.getBlue(), borderAlpha));
        // Background
        ctx.fill(sbX, sbY, sbX + SB_W, sbY + sbH, GuiTheme.rgba(8, 7, 14, 215));
        // Left accent sliver when focused
        if (searchFocused) {
            ctx.fill(sbX, sbY, sbX + 2, sbY + sbH, acInt);
        }

        // Search icon
        int iconColor = searchFocused ? acInt : GuiTheme.textSecondary();
        ctx.text(mc.font, "\u2315", sbX + 4, sbY + 4, iconColor, false);

        // Text / placeholder
        int textX = sbX + 16;
        int textY = sbY + 4;
        if (searchQuery.isEmpty() && !searchFocused) {
            ctx.text(mc.font, "Search modules...", textX, textY,
                    GuiTheme.textSecondary(), false);
        } else {
            ctx.enableScissor(textX, sbY, sbX + SB_W - 4, sbY + sbH);
            ctx.text(mc.font, searchQuery, textX, textY,
                    GuiTheme.textPrimary(), false);
            ctx.disableScissor();
            // Blinking cursor
            if (searchFocused && (System.currentTimeMillis() / 530) % 2 == 0) {
                int cursorX = textX + mc.font.width(searchQuery);
                ctx.fill(cursorX, sbY + 3, cursorX + 1, sbY + sbH - 3, GuiTheme.textPrimary());
            }
        }
    }

    private int searchBarX()  { return this.width - SB_W - SB_PAD; }
    private int searchBarY()  { return BAR_Y + 4; }
    private int searchBarH()  { return BAR_H - 8; }

    private boolean isOverSearchBar(double mx, double my) {
        int sbX = searchBarX(), sbY = searchBarY(), sbH = searchBarH();
        return mx >= sbX && mx < sbX + SB_W && my >= sbY && my < sbY + sbH;
    }

    private void updateFilter() {
        for (CategoryPanel panel : panels)
            panel.setFilter(searchQuery);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean canDoubleClick) {
        double mouseX = click.x(), mouseY = click.y();
        int button = click.button();

        if (button == 0) {
            if (isOverSearchBar(mouseX, mouseY)) {
                searchFocused = true;
                return true;
            } else {
                searchFocused = false;
            }
        }

        for (int i = panels.size() - 1; i >= 0; i--)
            if (panels.get(i).mouseClicked(mouseX, mouseY, button)) return true;
        return super.mouseClicked(click, canDoubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent click, double deltaX, double deltaY) {
        double mouseX = click.x(), mouseY = click.y();
        int button = click.button();
        for (CategoryPanel panel : panels)
            if (panel.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) return true;
        return super.mouseDragged(click, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent click) {
        double mouseX = click.x(), mouseY = click.y();
        int button = click.button();
        for (CategoryPanel panel : panels) panel.mouseReleased(mouseX, mouseY, button);
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        for (CategoryPanel panel : panels)
            if (panel.mouseScrolled(mouseX, mouseY, verticalAmount)) return true;
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        int keyCode = input.key(), scanCode = input.scancode(), modifiers = input.modifiers();

        if (searchFocused) {
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (!searchQuery.isEmpty()) {
                    searchQuery = searchQuery.substring(0, searchQuery.length() - 1);
                    updateFilter();
                }
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                if (!searchQuery.isEmpty()) {
                    searchQuery = "";
                    updateFilter();
                } else {
                    searchFocused = false;
                }
                return true;
            }
            return true;
        }

        for (CategoryPanel panel : panels)
            if (panel.keyPressed(keyCode, scanCode, modifiers)) return true;
        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharacterEvent input) {
        if (!input.isAllowedChatCharacter()) return super.charTyped(input);
        char chr = (char) input.codepoint();
        if (searchFocused) {
            searchQuery += chr;
            updateFilter();
            return true;
        }

        for (CategoryPanel panel : panels)
            if (panel.charTyped(chr, 0)) return true;
        return super.charTyped(input);
    }

    @Override
    public void removed() {
        currentInstance = null;
        try {
            ProfileManager pm = Rocky.INSTANCE.getProfileManager();
            for (CategoryPanel panel : panels) {
                pm.setPanelPosition(panel.getName(), panel.getX(), panel.getY());
            }
            pm.saveProfile("default");
        } catch (Exception e) {
            System.err.println("[Rocky] Failed to save profile: " + e.getMessage());
            e.printStackTrace();
        }
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    public static void resetPanels() {
        initialized = false;
        panels.clear();
    }
}
