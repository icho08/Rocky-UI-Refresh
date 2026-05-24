package dev.i726.rocky.module.modules.client;

import dev.i726.rocky.Rocky;
import dev.i726.rocky.event.events.AttackListener;
import dev.i726.rocky.event.events.ButtonListener;
import dev.i726.rocky.event.events.HudListener;
import dev.i726.rocky.gui.GuiTheme;
import dev.i726.rocky.managers.FriendManager;
import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.module.setting.BooleanSetting;
import dev.i726.rocky.module.setting.KeybindSetting;
import dev.i726.rocky.utils.EncryptedString;
import dev.i726.rocky.utils.WorldUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.EntityHitResult;
import org.lwjgl.glfw.GLFW;

import java.awt.Color;
import java.util.List;

public final class Friends extends Module implements ButtonListener, AttackListener, HudListener {

    private final KeybindSetting addFriendKey = new KeybindSetting(
            EncryptedString.of("Friend Key"), GLFW.GLFW_MOUSE_BUTTON_MIDDLE, false)
            .setDescription(EncryptedString.of("Key to add/remove the player you're looking at"));

    public final BooleanSetting antiAttack = new BooleanSetting(
            EncryptedString.of("Anti-Attack"), false)
            .setDescription(EncryptedString.of("Prevents attacking friends"));

    public final BooleanSetting disableAimAssist = new BooleanSetting(
            EncryptedString.of("Anti-Aim"), false)
            .setDescription(EncryptedString.of("Disables aim assist when targeting a friend"));

    public final BooleanSetting friendStatus = new BooleanSetting(
            EncryptedString.of("Friend Status"), true)
            .setDescription(EncryptedString.of("Shows a notification when aiming at a friend"));

    public final BooleanSetting showList = new BooleanSetting(
            EncryptedString.of("Show List"), false)
            .setDescription(EncryptedString.of("Shows your friend list on screen"));

    private FriendManager manager;

    // Smooth fade animation for the notification card
    private float notifAnim = 0f;
    private String lastFriendName = "";

    public Friends() {
        super(EncryptedString.of("Friends"),
                EncryptedString.of("Manage friend list"), -1, CategoryManager.GUI);
        addSettings(addFriendKey, antiAttack, disableAimAssist, friendStatus, showList);
        setKey(-1);
    }

    @Override
    public void onEnable() {
        manager = Rocky.INSTANCE.getFriendManager();
        eventManager.add(ButtonListener.class, this);
        eventManager.add(AttackListener.class, this);
        eventManager.add(HudListener.class, this);
        super.onEnable();
    }

    @Override
    public void onDisable() {
        eventManager.remove(ButtonListener.class, this);
        eventManager.remove(AttackListener.class, this);
        eventManager.remove(HudListener.class, this);
        super.onDisable();
    }

    @Override
    public void onButtonPress(ButtonEvent event) {
        if (mc.player == null || mc.currentScreen != null) return;
        if (event.button != addFriendKey.getKey() || event.action != GLFW.GLFW_PRESS) return;

        if (mc.crosshairTarget instanceof EntityHitResult hitResult
                && hitResult.getEntity() instanceof PlayerEntity player) {
            if (manager.isFriend(player)) {
                manager.removeFriend(player);
            } else {
                manager.addFriend(player);
            }
        }
    }

    @Override
    public void onAttack(AttackEvent event) {
        if (antiAttack.getValue() && manager.isAimingOverFriend()) {
            event.cancel();
        }
    }

    @Override
    public void onRenderHud(HudEvent event) {
        DrawContext ctx = event.context;
        int screenW = mc.getWindow().getScaledWidth();
        int screenH = mc.getWindow().getScaledHeight();

        // ── Friend status notification ────────────────────────────────────────
        if (friendStatus.getValue()) {
            boolean aimingAtFriend = false;
            String friendName = "";
            if (WorldUtils.getHitResult(100) instanceof EntityHitResult hit
                    && hit.getEntity() instanceof PlayerEntity player
                    && manager.isFriend(player)) {
                aimingAtFriend = true;
                friendName = player.getName().getString();
                lastFriendName = friendName;
            }

            float target = aimingAtFriend ? 1f : 0f;
            notifAnim += (target - notifAnim) * 0.18f;

            if (notifAnim > 0.01f) {
                Color ac = GuiTheme.accent();
                int acInt = GuiTheme.accentInt();

                String label = "\u2665 Friend: " + (aimingAtFriend ? lastFriendName : lastFriendName);
                int textW = mc.textRenderer.getWidth(label);
                int cardW = textW + 20;
                int cardH = 20;
                int cx = screenW / 2 - cardW / 2;
                int cy = screenH / 2 + 20;

                int alpha = (int)(notifAnim * 220);
                int bgColor = GuiTheme.rgba(10, 9, 16, alpha);
                int borderColor = GuiTheme.rgba(ac.getRed(), ac.getGreen(), ac.getBlue(), (int)(notifAnim * 130));
                int accentBarColor = GuiTheme.rgba(ac.getRed(), ac.getGreen(), ac.getBlue(), (int)(notifAnim * 255));

                // Shadow
                ctx.fill(cx + 2, cy + 2, cx + cardW + 2, cy + cardH + 2, GuiTheme.rgba(0, 0, 0, (int)(notifAnim * 50)));
                // Border
                ctx.fill(cx - 1, cy - 1, cx + cardW + 1, cy + cardH + 1, borderColor);
                // Background
                ctx.fill(cx, cy, cx + cardW, cy + cardH, bgColor);
                // Left accent bar
                ctx.fill(cx, cy, cx + 3, cy + cardH, accentBarColor);
                // Top accent fade
                ctx.fillGradient(cx + 3, cy, cx + cardW, cy + 1,
                        GuiTheme.rgba(ac.getRed(), ac.getGreen(), ac.getBlue(), (int)(notifAnim * 70)),
                        GuiTheme.rgba(ac.getRed(), ac.getGreen(), ac.getBlue(), 0));

                int textColor = GuiTheme.rgba(228, 224, 255, (int)(notifAnim * 255));
                ctx.drawText(mc.textRenderer, label, cx + 10, cy + 6, textColor, false);
            }
        }

        // ── Friend list overlay ───────────────────────────────────────────────
        if (showList.getValue()) {
            List<String> friendList = manager.getFriends();
            if (friendList.isEmpty()) return;

            Color ac = GuiTheme.accent();
            int acInt = GuiTheme.accentInt();
            int listW = 120;
            int headerH = 18;
            int rowH = 16;
            int totalH = headerH + 1 + friendList.size() * rowH + 4;
            int lx = 8;
            int ly = screenH - totalH - 8;

            // Shadow
            ctx.fill(lx + 2, ly + 2, lx + listW + 2, ly + totalH + 2, GuiTheme.rgba(0, 0, 0, 55));
            // Border
            ctx.fill(lx - 1, ly - 1, lx + listW + 1, ly + totalH + 1, GuiTheme.border());
            // Body
            ctx.fill(lx, ly, lx + listW, ly + totalH, GuiTheme.panelBg());
            // Header bg
            ctx.fill(lx, ly, lx + listW, ly + headerH, GuiTheme.headerBg());
            ctx.fillGradient(lx, ly, lx + listW, ly + headerH,
                    GuiTheme.rgba(ac.getRed(), ac.getGreen(), ac.getBlue(), 40),
                    GuiTheme.rgba(ac.getRed(), ac.getGreen(), ac.getBlue(), 0));
            ctx.fill(lx, ly, lx + 3, ly + headerH, acInt);
            ctx.drawText(mc.textRenderer, "FRIENDS", lx + 8, ly + 5, GuiTheme.textPrimary(), false);

            // Separator
            ctx.fill(lx + 3, ly + headerH, lx + listW - 3, ly + headerH + 1, GuiTheme.separator());

            // Friend entries
            int ry = ly + headerH + 3;
            for (String name : friendList) {
                // Check if this friend is in the world currently
                boolean online = mc.world != null && mc.world.getPlayers().stream()
                        .anyMatch(p -> p.getName().getString().equals(name));
                int nameColor = online ? acInt : GuiTheme.textSecondary();
                String prefix = online ? "\u25CF " : "\u25CB ";
                ctx.drawText(mc.textRenderer, prefix + name, lx + 8, ry, nameColor, false);
                ry += rowH;
            }
        }
    }
}
