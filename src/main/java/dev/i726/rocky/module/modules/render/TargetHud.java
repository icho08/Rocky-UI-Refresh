package dev.i726.rocky.module.modules.render;

import dev.i726.rocky.event.events.HudListener;
import dev.i726.rocky.event.events.PacketSendListener;
import dev.i726.rocky.gui.GuiTheme;
import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.module.setting.BooleanSetting;
import dev.i726.rocky.module.setting.NumberSetting;
import dev.i726.rocky.utils.EncryptedString;
import dev.i726.rocky.utils.RenderUtils;
import dev.i726.rocky.utils.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;

import java.awt.Color;

public final class TargetHud extends Module implements HudListener, PacketSendListener {

    private final NumberSetting xCoord    = new NumberSetting(EncryptedString.of("X"), 0, 1920, 10, 1);
    private final NumberSetting yCoord    = new NumberSetting(EncryptedString.of("Y"), 0, 1080, 10, 1);
    private final BooleanSetting hudTimeout = new BooleanSetting(EncryptedString.of("Timeout"), true)
            .setDescription(EncryptedString.of("Hides after 10 seconds of not attacking"));

    private long lastAttackTime = 0;
    public static float animation = 1f;
    private static final long TIMEOUT_MS = 10_000L;

    private static final int CARD_W  = 190;
    private static final int HEADER_H = 20;

    public TargetHud() {
        super(EncryptedString.of("Target HUD"),
                EncryptedString.of("Shows target information"),
                -1,
                CategoryManager.ESP);
        addSettings(xCoord, yCoord, hudTimeout);
    }

    @Override
    public void onEnable() {
        eventManager.add(HudListener.class, this);
        eventManager.add(PacketSendListener.class, this);
        super.onEnable();
    }

    @Override
    public void onDisable() {
        eventManager.remove(HudListener.class, this);
        eventManager.remove(PacketSendListener.class, this);
        super.onDisable();
    }

    @Override
    public void onRenderHud(HudEvent event) {
        DrawContext ctx = event.context;
        if (mc.player == null) return;

        boolean hasTarget = mc.player.getAttacking() instanceof PlayerEntity p && p.isAlive();
        boolean withinTimeout = !hudTimeout.getValue()
                || (System.currentTimeMillis() - lastAttackTime <= TIMEOUT_MS);

        float animTarget = (hasTarget && withinTimeout) ? 0f : 1f;
        animation = RenderUtils.fast(animation, animTarget, 15f);

        if (!hasTarget || !withinTimeout) return;

        PlayerEntity target = (PlayerEntity) mc.player.getAttacking();
        PlayerListEntry entry = mc.getNetworkHandler() != null
                ? mc.getNetworkHandler().getPlayerListEntry(target.getUuid()) : null;

        float health       = Math.min(target.getHealth() + target.getAbsorptionAmount(), 20f);
        float healthPct    = health / 20f;
        int   armorVal     = target.getArmor();
        float armorPct     = armorVal / 20f;
        int   ping         = entry != null ? entry.getLatency() : -1;
        String name        = target.getName().getString();
        boolean isBot      = entry == null;

        int x = xCoord.getValueInt();
        int y = yCoord.getValueInt();

        // ── Layout math ──────────────────────────────────────────────────────
        int cardH = HEADER_H          // name header
                + 1                   // separator
                + 10                  // HP label padding
                + 8                   // HP bar
                + 8                   // Armor label padding
                + 8                   // Armor bar
                + (ping >= 0 ? 18 : 0)// Ping row
                + 10;                 // bottom padding

        Color ac   = GuiTheme.accent();
        int acInt  = GuiTheme.accentInt();

        // ── Card ─────────────────────────────────────────────────────────────
        // Shadow
        ctx.fill(x + 2, y + 2, x + CARD_W + 2, y + cardH + 2, GuiTheme.rgba(0, 0, 0, 60));
        // Border
        ctx.fill(x - 1, y - 1, x + CARD_W + 1, y + cardH + 1, GuiTheme.border());
        // Body
        ctx.fill(x, y, x + CARD_W, y + cardH, GuiTheme.panelBg());

        // ── Header ───────────────────────────────────────────────────────────
        ctx.fill(x, y, x + CARD_W, y + HEADER_H, GuiTheme.headerBg());
        // Accent gradient (left → transparent, matching panels)
        ctx.fillGradient(x, y, x + CARD_W, y + HEADER_H,
                GuiTheme.rgba(ac.getRed(), ac.getGreen(), ac.getBlue(), 45),
                GuiTheme.rgba(ac.getRed(), ac.getGreen(), ac.getBlue(), 0));
        // Left accent bar
        ctx.fill(x, y, x + 3, y + HEADER_H, acInt);

        // Name
        int nameColor = isBot ? GuiTheme.rgba(239, 68, 68, 255) : GuiTheme.textPrimary();
        String displayName = isBot ? name + " [BOT]" : name;
        ctx.drawText(mc.textRenderer, displayName, x + 9, y + 6, nameColor, false);

        // ── Header separator ─────────────────────────────────────────────────
        int sepY = y + HEADER_H;
        ctx.fill(x + 4, sepY, x + CARD_W - 4, sepY + 1, GuiTheme.separator());

        // ── HP row ───────────────────────────────────────────────────────────
        int rowY = sepY + 8;
        int barX = x + 9;
        int barW = CARD_W - 18;

        // Label
        ctx.drawText(mc.textRenderer, "HP", barX, rowY, GuiTheme.textSecondary(), false);
        // Value (right-aligned, colored by health %)
        String hpStr   = String.valueOf(Math.round(health));
        int    hpColor = healthBarColor(healthPct);
        int    hpStrW  = mc.textRenderer.getWidth(hpStr);
        ctx.drawText(mc.textRenderer, hpStr, x + CARD_W - 9 - hpStrW, rowY, hpColor, false);

        // Health bar
        int barY = rowY + 10;
        ctx.fill(barX, barY, barX + barW, barY + 5, GuiTheme.sliderTrack());
        int fillW = Math.max(0, (int)(barW * healthPct));
        ctx.fill(barX, barY, barX + fillW, barY + 5, hpColor);
        // Subtle highlight on top of filled bar
        if (fillW > 0) {
            ctx.fill(barX, barY, barX + fillW, barY + 1,
                    GuiTheme.rgba(255, 255, 255, 25));
        }

        // ── Armor row ────────────────────────────────────────────────────────
        rowY = barY + 10;
        ctx.drawText(mc.textRenderer, "Armor", barX, rowY, GuiTheme.textSecondary(), false);
        String armorStr  = String.valueOf(armorVal);
        int    armorStrW = mc.textRenderer.getWidth(armorStr);
        ctx.drawText(mc.textRenderer, armorStr, x + CARD_W - 9 - armorStrW, rowY, acInt, false);

        barY = rowY + 10;
        ctx.fill(barX, barY, barX + barW, barY + 5, GuiTheme.sliderTrack());
        int armorFill = (int)(barW * armorPct);
        if (armorFill > 0) {
            ctx.fill(barX, barY, barX + armorFill, barY + 5, acInt);
            ctx.fill(barX, barY, barX + armorFill, barY + 1,
                    GuiTheme.rgba(255, 255, 255, 25));
        }

        // ── Ping row ─────────────────────────────────────────────────────────
        if (ping >= 0) {
            rowY = barY + 10;
            ctx.fill(x + 4, rowY - 3, x + CARD_W - 4, rowY - 2, GuiTheme.separator());
            rowY += 3;
            ctx.drawText(mc.textRenderer, "Ping", barX, rowY, GuiTheme.textSecondary(), false);
            String pingStr  = ping + "ms";
            int    pingColor = ping < 80 ? GuiTheme.rgba(34, 197, 94, 255)
                    : ping < 150 ? GuiTheme.rgba(234, 179, 8, 255)
                    : GuiTheme.rgba(239, 68, 68, 255);
            int pingStrW = mc.textRenderer.getWidth(pingStr);
            ctx.drawText(mc.textRenderer, pingStr, x + CARD_W - 9 - pingStrW, rowY, pingColor, false);
        }
    }

    private int healthBarColor(float pct) {
        if (pct > 0.6f) return GuiTheme.rgba(34, 197, 94, 255);   // green
        if (pct > 0.3f) return GuiTheme.rgba(234, 179, 8, 255);   // yellow
        return GuiTheme.rgba(239, 68, 68, 255);                     // red
    }

    @Override
    public void onPacketSend(PacketSendListener.PacketSendEvent event) {
        if (event.packet instanceof PlayerInteractEntityC2SPacket packet) {
            packet.handle(new PlayerInteractEntityC2SPacket.Handler() {
                @Override public void interact(Hand hand) {}
                @Override public void interactAt(Hand hand, Vec3d pos) {}
                @Override
                public void attack() {
                    if (mc.targetedEntity instanceof PlayerEntity) {
                        lastAttackTime = System.currentTimeMillis();
                    }
                }
            });
        }
    }
}
