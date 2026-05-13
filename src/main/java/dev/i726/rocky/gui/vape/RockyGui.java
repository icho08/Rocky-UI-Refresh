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
    private boolean[] keyState = new boolean[512];
    private TextFieldWidget searchField;
    private String searchText = "";

    private static final int SEARCH_W  = 180;
    private static final int SEARCH_H  = 16;
    private static final int HEADER_H  = 32;

    private static final int[] POLL_KEYS = {
        GLFW.GLFW_KEY_ESCAPE, GLFW.GLFW_KEY_RIGHT_SHIFT, GLFW.GLFW_KEY_LEFT_SHIFT,
        GLFW.GLFW_KEY_W, GLFW.GLFW_KEY_A, GLFW.GLFW_KEY_S, GLFW.GLFW_KEY_D,
        GLFW.GLFW_KEY_SPACE, GLFW.GLFW_KEY_LEFT_CONTROL, GLFW.GLFW_KEY_TAB
    };

    public RockyGui() {
        super(Text.literal("Rocky GUI"));
    }

    @Override
    protected void init() {
        panels.clear();
        scrollAmount = 0;

        int fieldX = width / 2 - SEARCH_W / 2;
        int fieldY = (HEADER_H - SEARCH_H) / 2;
        searchField = new TextFieldWidget(textRenderer, fieldX, fieldY, SEARCH_W, SEARCH_H, Text.literal("Search"));
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

        int panelWidth = 140;
        int gapX       = 8;
        int startX     = 16;
        int startY     = HEADER_H + 8;
        int x = startX;
        int y = startY;

        List<Category> allCategories = CategoryManager.getCategories().stream()
            .filter(Category::isSubcategory)
            .collect(Collectors.toList());

        for (Category cat : allCategories) {
            List<Module> mods = getFilteredModules(cat);
            if (!mods.isEmpty()) {
                double[] pos = Rocky.INSTANCE.getProfileManager().getPanelPosition(cat.getName());
                if (pos != null) {
                    panels.add(new Panel(cat, pos[0], pos[1], panelWidth, searchText));
                } else {
                    panels.add(new Panel(cat, x, y, panelWidth, searchText));
                    x += panelWidth + gapX;
                    if (x > width - panelWidth - 16) {
                        x = startX;
                        y += 200;
                    }
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
        for (Panel panel : panels) {
            Rocky.INSTANCE.getProfileManager().setPanelPosition(
                panel.getCategory().getName(), panel.getX(), panel.getY());
        }
        Rocky.INSTANCE.getProfileManager().saveProfile("default");
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {}
    @Override
    public void renderInGameBackground(DrawContext context) {}

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (Rocky.mc == null) Rocky.mc = MinecraftClient.getInstance();
        MinecraftClient mc = MinecraftClient.getInstance();

        handlePollingInput(mc, mouseX, mouseY);

        // ── Top header bar ───────────────────────────────────────────────
        context.fill(0, 0, width, HEADER_H, new Color(6, 6, 6, 240).getRGB());
        // Subtle bottom border on header
        context.fill(0, HEADER_H - 1, width, HEADER_H, VapeTheme.BORDER.getRGB());

        // ROCKY wordmark — left side
        context.drawText(mc.textRenderer, "ROCKY", 12, (HEADER_H - 8) / 2,
                VapeTheme.ACCENT.getRGB(), false);
        // Underline accent
        context.fill(12, (HEADER_H - 8) / 2 + 10, 12 + mc.textRenderer.getWidth("ROCKY"),
                (HEADER_H - 8) / 2 + 11, VapeTheme.ACCENT.getRGB());

        // Search bar background
        int sfx = searchField.getX() - 6;
        int sfy = searchField.getY() - 3;
        int sfw = SEARCH_W + 12;
        int sfh = SEARCH_H + 6;
        RenderUtils.drawRoundedRect(context, sfx, sfy, sfx + sfw, sfy + sfh, 3,
                new Color(20, 20, 20, 220).getRGB());
        RenderUtils.renderRoundedOutline(context, VapeTheme.BORDER,
                sfx, sfy, sfx + sfw, sfy + sfh, 3, 3, 3, 3, 0.5, 10);

        // Active module count — right side
        long activeCount = Rocky.INSTANCE.getModuleManager().getModules().stream()
                .filter(Module::isEnabled).count();
        String activeStr = activeCount + " active";
        int activeX = width - mc.textRenderer.getWidth(activeStr) - 12;
        context.drawText(mc.textRenderer, activeStr, activeX, (HEADER_H - 8) / 2,
                VapeTheme.TEXT_MUTED.getRGB(), false);

        // Draw the MC text field widget (search)
        super.render(context, mouseX, mouseY, delta);

        // ── Panels ────────────────────────────────────────────────────────
        double adjY = mouseY - scrollAmount;
        context.getMatrices().translate(0, (float) scrollAmount);
        for (Panel panel : panels) {
            panel.render(context, mouseX, (int) adjY, delta);
        }
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
            if (key == GLFW.GLFW_KEY_ESCAPE && !KeybindSetting.isAnyBinding) {
                mc.execute(() -> mc.setScreen(null));
            } else {
                onKeyPress(key);
            }
        }
        keyState[key] = down;
    }

    private void onMouseClick(double mx, double my, int button) {
        // Don't intercept clicks inside the search field
        if (mx >= searchField.getX() && mx <= searchField.getX() + SEARCH_W
                && my >= searchField.getY() && my <= searchField.getY() + SEARCH_H) return;

        for (int i = panels.size() - 1; i >= 0; i--) {
            Panel panel = panels.get(i);
            if (isOverPanel(panel, mx, my)) {
                panel.mouseClicked(mx, my, button);
                panels.remove(i);
                panels.add(panel);
                return;
            }
        }
    }

    private void onMouseRelease(double mx, double my, int button) {
        for (Panel panel : panels) {
            panel.mouseReleased(mx, my, button);
        }
    }

    private void onKeyPress(int keyCode) {
        for (Panel panel : panels) {
            for (ModuleButton button : panel.getButtons()) {
                if (button.onKey(keyCode)) return;
            }
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        scrollAmount += verticalAmount * 20;
        if (scrollAmount > 0) scrollAmount = 0;
        return true;
    }

    private boolean isOverPanel(Panel panel, double mx, double my) {
        return mx >= panel.x && mx <= panel.x + panel.width
            && my >= panel.y && my <= panel.y + panel.getTotalHeight();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
