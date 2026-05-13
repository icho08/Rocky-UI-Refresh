package dev.i726.rocky.gui;

import dev.i726.rocky.Rocky;
import dev.i726.rocky.gui.components.CategoryPanel;
import dev.i726.rocky.managers.ProfileManager;
import dev.i726.rocky.module.Category;
import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ClickGuiScreen extends Screen {

    private static final List<CategoryPanel> panels = new ArrayList<>();
    private static boolean initialized = false;

    private static final int BAR_Y  = 5;
    private static final int BAR_H  = 28;
    private static final int SB_W   = 170;
    private static final int SB_PAD = 8;

    private String  searchQuery  = "";
    private boolean searchFocused = false;

    public ClickGuiScreen() {
        super(Text.literal("Rocky"));
    }

    @Override
    protected void init() {
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
        List<Category> topLevel = Arrays.asList(
                CategoryManager.COMBAT,
                CategoryManager.PLAYER,
                CategoryManager.VISUAL,
                CategoryManager.MISC
        );
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
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, this.width, this.height, GuiTheme.rgba(5, 4, 10, 175));

        renderTopBar(ctx, mouseX, mouseY);

        for (CategoryPanel panel : panels)
            panel.render(ctx, mouseX, mouseY, delta);
    }

    private void renderTopBar(DrawContext ctx, int mouseX, int mouseY) {
        MinecraftClient mc = MinecraftClient.getInstance();
        Color ac = GuiTheme.accent();
        int acInt = GuiTheme.accentInt();

        // ── Background ──────────────────────────────────────────────────────
        ctx.fill(0, BAR_Y + BAR_H, this.width, BAR_Y + BAR_H + 2, GuiTheme.rgba(0, 0, 0, 50));
        ctx.fill(0, BAR_Y, this.width, BAR_Y + BAR_H, GuiTheme.headerBg());
        // bottom separator line
        ctx.fill(0, BAR_Y + BAR_H - 1, this.width, BAR_Y + BAR_H, GuiTheme.border());
        // left accent bar (3 px)
        ctx.fill(0, BAR_Y, 3, BAR_Y + BAR_H, acInt);
        // top accent fade
        ctx.fillGradient(3, BAR_Y, this.width, BAR_Y + 1,
                GuiTheme.rgba(ac.getRed(), ac.getGreen(), ac.getBlue(), 90),
                GuiTheme.rgba(ac.getRed(), ac.getGreen(), ac.getBlue(), 0));

        // ── Logo: accent box "R" + "OCKY CLIENT" text ───────────────────────
        int logoBoxX = 10;
        int logoBoxY = BAR_Y + 6;
        int logoBoxS = 16;

        // Accent box
        ctx.fill(logoBoxX, logoBoxY, logoBoxX + logoBoxS, logoBoxY + logoBoxS, acInt);
        // Subtle inner highlight on top edge
        ctx.fill(logoBoxX, logoBoxY, logoBoxX + logoBoxS, logoBoxY + 1,
                GuiTheme.rgba(255, 255, 255, 30));
        // "R" letter centred in the box
        ctx.drawText(mc.textRenderer, "R",
                logoBoxX + 4, logoBoxY + 4, GuiTheme.rgba(255, 255, 255, 255), false);

        int tx = logoBoxX + logoBoxS + 6;
        int ty = BAR_Y + 10;
        // "OCKY" in accent colour
        ctx.drawText(mc.textRenderer, "OCKY", tx, ty, acInt, false);
        int ockyW = mc.textRenderer.getWidth("OCKY");
        // "CLIENT" in softer white
        ctx.drawText(mc.textRenderer, " CLIENT", tx + ockyW, ty, GuiTheme.textPrimary(), false);
        // Version tiny text
        int clientW = mc.textRenderer.getWidth(" CLIENT");
        String ver = Rocky.INSTANCE.getVersion().trim();
        ctx.drawText(mc.textRenderer, "  " + ver,
                tx + ockyW + clientW, ty,
                GuiTheme.rgba(90, 86, 128, 255), false);

        // ── Search bar ──────────────────────────────────────────────────────
        int sbX = this.width - SB_W - SB_PAD;
        int sbY = BAR_Y + 5;
        int sbH = BAR_H - 10;

        boolean hoveringSearch = mouseX >= sbX && mouseX < sbX + SB_W
                              && mouseY >= sbY && mouseY < sbY + sbH;
        int borderColor = searchFocused
                ? GuiTheme.accentDim()
                : hoveringSearch
                        ? GuiTheme.rgba(60, 56, 90, 200)
                        : GuiTheme.border();

        // outer border
        ctx.fill(sbX - 1, sbY - 1, sbX + SB_W + 1, sbY + sbH + 1, borderColor);
        // background
        ctx.fill(sbX, sbY, sbX + SB_W, sbY + sbH, GuiTheme.rgba(6, 5, 12, 220));
        // left tint when focused
        if (searchFocused) {
            ctx.fill(sbX, sbY, sbX + 2, sbY + sbH, acInt);
        }

        // search icon ⌕ (unicode RING OPERATOR, commonly used as search symbol)
        int iconColor = searchFocused ? acInt : GuiTheme.textSecondary();
        ctx.drawText(mc.textRenderer, "\u2315", sbX + 4, sbY + 4, iconColor, false);

        // text / placeholder
        int textX = sbX + 16;
        int textY = sbY + 4;
        if (searchQuery.isEmpty() && !searchFocused) {
            ctx.drawText(mc.textRenderer, "Search modules...", textX, textY,
                    GuiTheme.textSecondary(), false);
        } else {
            // clip text inside the bar
            ctx.enableScissor(textX, sbY, sbX + SB_W - 4, sbY + sbH);
            ctx.drawText(mc.textRenderer, searchQuery, textX, textY,
                    GuiTheme.textPrimary(), false);
            ctx.disableScissor();

            // blinking cursor
            if (searchFocused && (System.currentTimeMillis() / 530) % 2 == 0) {
                int cursorX = textX + mc.textRenderer.getWidth(searchQuery);
                ctx.fill(cursorX, sbY + 3, cursorX + 1, sbY + sbH - 3, GuiTheme.textPrimary());
            }
        }
    }

    private int searchBarX()  { return this.width - SB_W - SB_PAD; }
    private int searchBarY()  { return BAR_Y + 5; }
    private int searchBarH()  { return BAR_H - 10; }

    private boolean isOverSearchBar(double mx, double my) {
        int sbX = searchBarX(), sbY = searchBarY(), sbH = searchBarH();
        return mx >= sbX && mx < sbX + SB_W && my >= sbY && my < sbY + sbH;
    }

    private void updateFilter() {
        for (CategoryPanel panel : panels)
            panel.setFilter(searchQuery);
    }

    @Override
    public boolean mouseClicked(Click click, boolean canDoubleClick) {
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
    public boolean mouseDragged(Click click, double deltaX, double deltaY) {
        double mouseX = click.x(), mouseY = click.y();
        int button = click.button();
        for (CategoryPanel panel : panels)
            if (panel.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) return true;
        return super.mouseDragged(click, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(Click click) {
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
    public boolean keyPressed(KeyInput input) {
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
    public boolean charTyped(CharInput input) {
        if (!input.isValidChar()) return super.charTyped(input);
        char chr = (char) input.codepoint();
        int modifiers = input.modifiers();

        if (searchFocused) {
            searchQuery += chr;
            updateFilter();
            return true;
        }

        for (CategoryPanel panel : panels)
            if (panel.charTyped(chr, modifiers)) return true;
        return super.charTyped(input);
    }

    @Override
    public void removed() {
        ProfileManager pm = Rocky.INSTANCE.getProfileManager();
        for (CategoryPanel panel : panels) {
            pm.setPanelPosition(panel.getName(), panel.getX(), panel.getY());
        }
        pm.saveProfile("default");
        super.removed();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    public static void resetPanels() {
        initialized = false;
        panels.clear();
    }
}
