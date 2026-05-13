package dev.i726.rocky.gui.vape;

import dev.i726.rocky.Rocky;
import dev.i726.rocky.gui.vape.components.ModuleButton;
import dev.i726.rocky.gui.vape.components.Panel;
import dev.i726.rocky.gui.vape.components.settings.KeybindSetting;
import dev.i726.rocky.module.Category;
import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.utils.RenderUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class RockyGui extends Screen {

    private final List<Panel> panels = new ArrayList<>();
    private double scrollAmount = 0;
    private boolean[] mouseState = new boolean[3];
    private boolean[] keyState   = new boolean[512];
    private TextFieldWidget searchField;
    private String searchText = "";

    // ─── Layout constants ───────────────────────────────────────────────────
    public  static final int PANEL_W  = 160;   // wider so module names breathe
    private static final int GAP_X    = 10;
    private static final int HEADER_H = 36;
    private static final int SEARCH_W = 190;
    private static final int SEARCH_H = 15;

    private static final int[] POLL_KEYS = {
        GLFW.GLFW_KEY_ESCAPE, GLFW.GLFW_KEY_RIGHT_SHIFT, GLFW.GLFW_KEY_LEFT_SHIFT,
        GLFW.GLFW_KEY_W, GLFW.GLFW_KEY_A, GLFW.GLFW_KEY_S, GLFW.GLFW_KEY_D,
        GLFW.GLFW_KEY_SPACE, GLFW.GLFW_KEY_LEFT_CONTROL, GLFW.GLFW_KEY_TAB
    };

    public RockyGui() { super(Text.literal("Rocky GUI")); }

    @Override
    protected void init() {
        panels.clear();
        scrollAmount = 0;

        int fieldX = width / 2 - SEARCH_W / 2;
        int fieldY = (HEADER_H - SEARCH_H) / 2;
        searchField = new TextFieldWidget(textRenderer, fieldX, fieldY, SEARCH_W, SEARCH_H,
                Text.literal("Search"));
        searchField.setPlaceholder(Text.literal("Search modules..."));
        searchField.setChangedListener(this::onSearchChanged);
        searchField.setDrawsBackground(false);
        addDrawableChild(searchField);

        rebuildPanels();
    }

    private void onSearchChanged(String text) {
        searchText = text.toLowerCase();
        rebuildPanels();
    }

    private void rebuildPanels() {
        panels.clear();

        int startX = 14;
        int startY = HEADER_H + 10;
        int x = startX, y = startY;

        List<Category> cats = CategoryManager.getCategories().stream()
                .filter(Category::isSubcategory)
                .collect(Collectors.toList());

        for (Category cat : cats) {
            List<Module> mods = getFilteredModules(cat);
            if (!mods.isEmpty()) {
                double[] pos = Rocky.INSTANCE.getProfileManager().getPanelPosition(cat.getName());
                if (pos != null) {
                    panels.add(new Panel(cat, pos[0], pos[1], PANEL_W, searchText));
                } else {
                    panels.add(new Panel(cat, x, y, PANEL_W, searchText));
                    x += PANEL_W + GAP_X;
                    if (x > width - PANEL_W - 14) { x = startX; y += 210; }
                }
            }
        }
    }

    private List<Module> getFilteredModules(Category cat) {
        List<Module> mods = Rocky.INSTANCE.getModuleManager().getModulesInCategory(cat);
        if (searchText.isEmpty()) return mods;
        return mods.stream()
                .filter(m -> m.getName().toString().toLowerCase().contains(searchText)
                          || m.getDescription().toString().toLowerCase().contains(searchText))
                .collect(Collectors.toList());
    }

    @Override
    public void removed() {
        for (Panel p : panels)
            Rocky.INSTANCE.getProfileManager().setPanelPosition(
                    p.getCategory().getName(), p.getX(), p.getY());
        Rocky.INSTANCE.getProfileManager().saveProfile("default");
    }

    @Override public void renderBackground(DrawContext c, int mx, int my, float d) {}
    @Override public void renderInGameBackground(DrawContext c) {}

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (Rocky.mc == null) Rocky.mc = MinecraftClient.getInstance();
        MinecraftClient mc = MinecraftClient.getInstance();

        handlePollingInput(mc, mouseX, mouseY);

        // ── Full-screen dark vignette (gradient from edges) ───────────────
        int dimColor = new Color(0, 0, 0, 110).getRGB();
        context.fill(0, 0, width, height, dimColor);

        // ── Header bar ────────────────────────────────────────────────────
        // Gradient: slightly lighter at top, pure black at bottom
        context.fillGradient(0, 0, width, HEADER_H,
                new Color(18, 18, 18, 252).getRGB(),
                new Color(8, 8, 8, 252).getRGB());
        // Glowing cyan bottom edge
        RenderUtils.renderAccentLine(context, VapeTheme.ACCENT_DIM, VapeTheme.ACCENT_DIM,
                0, HEADER_H - 1, width, HEADER_H);

        // ROCKY wordmark
        String wordmark = "ROCKY";
        int wmy = (HEADER_H - 8) / 2;
        context.drawText(mc.textRenderer, wordmark, 14, wmy, VapeTheme.ACCENT.getRGB(), false);

        // Search box chrome
        int sfx = searchField.getX() - 8;
        int sfy = searchField.getY() - 4;
        int sfw = SEARCH_W + 16;
        int sfh = SEARCH_H + 8;
        RenderUtils.renderRoundedQuad(context, new Color(22, 22, 22, 230),
                sfx, sfy, sfx + sfw, sfy + sfh, 4, 10);
        RenderUtils.renderRoundedOutline(context, new Color(255, 255, 255, 18),
                sfx, sfy, sfx + sfw, sfy + sfh, 4, 4, 4, 4, 0.5, 10);
        // Search icon text placeholder
        context.drawText(mc.textRenderer, "[ " + (searchText.isEmpty() ? "search..." : searchText) + " ]",
                sfx + 6, sfy + 4, searchText.isEmpty()
                        ? new Color(80, 80, 80).getRGB() : VapeTheme.TEXT_DIM.getRGB(), false);

        // Active module pill — right
        long active = Rocky.INSTANCE.getModuleManager().getModules().stream()
                .filter(Module::isEnabled).count();
        String activeStr = active + " active";
        int aw = mc.textRenderer.getWidth(activeStr);
        int ax = width - aw - 16;
        int ay = (HEADER_H - 10) / 2;
        RenderUtils.renderRoundedQuad(context, new Color(34, 211, 238, 22), ax - 5, ay - 1, ax + aw + 5, ay + 11, 3, 8);
        context.drawText(mc.textRenderer, activeStr, ax, ay + 1, VapeTheme.ACCENT.getRGB(), false);

        // Draw MC widget (search text cursor etc.) — hidden behind our custom rendering
        super.render(context, mouseX, mouseY, delta);

        // ── Panels ────────────────────────────────────────────────────────
        double adjY = mouseY - scrollAmount;
        context.getMatrices().translate(0, (float) scrollAmount);
        for (Panel panel : panels)
            panel.render(context, mouseX, (int) adjY, delta);
        context.getMatrices().translate(0, -(float) scrollAmount);
    }

    private void handlePollingInput(MinecraftClient mc, int mouseX, int mouseY) {
        long handle = mc.getWindow().getHandle();
        double adjY = mouseY - scrollAmount;

        for (int i = 0; i < 2; i++) {
            boolean down = GLFW.glfwGetMouseButton(handle, i) == GLFW.GLFW_PRESS;
            if (down && !mouseState[i]) onMouseClick(mouseX, adjY, i);
            else if (!down && mouseState[i]) onMouseRelease(mouseX, adjY, i);
            mouseState[i] = down;
        }

        if (KeybindSetting.isAnyBinding) {
            for (int i = 32; i < 348; i++) pollKey(mc, handle, i);
        } else {
            for (int key : POLL_KEYS) pollKey(mc, handle, key);
        }
    }

    private void pollKey(MinecraftClient mc, long handle, int key) {
        if (key < 0 || key >= keyState.length) return;
        boolean down = GLFW.glfwGetKey(handle, key) == GLFW.GLFW_PRESS;
        if (down && !keyState[key]) {
            if (key == GLFW.GLFW_KEY_ESCAPE && !KeybindSetting.isAnyBinding)
                mc.execute(() -> mc.setScreen(null));
            else
                onKeyPress(key);
        }
        keyState[key] = down;
    }

    private void onMouseClick(double mx, double my, int button) {
        if (mx >= searchField.getX() && mx <= searchField.getX() + SEARCH_W
                && my >= searchField.getY() && my <= searchField.getY() + SEARCH_H) return;
        for (int i = panels.size() - 1; i >= 0; i--) {
            Panel p = panels.get(i);
            if (mx >= p.x && mx <= p.x + p.width && my >= p.y && my <= p.y + p.getTotalHeight()) {
                p.mouseClicked(mx, my, button);
                panels.remove(i);
                panels.add(p);
                return;
            }
        }
    }

    private void onMouseRelease(double mx, double my, int button) {
        for (Panel p : panels) p.mouseReleased(mx, my, button);
    }

    private void onKeyPress(int keyCode) {
        for (Panel panel : panels)
            for (ModuleButton btn : panel.getButtons())
                if (btn.onKey(keyCode)) return;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double h, double v) {
        scrollAmount += v * 20;
        if (scrollAmount > 0) scrollAmount = 0;
        return true;
    }

    @Override
    public boolean shouldPause() { return false; }
}
