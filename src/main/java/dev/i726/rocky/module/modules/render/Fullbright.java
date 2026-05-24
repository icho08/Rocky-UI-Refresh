package dev.i726.rocky.module.modules.render;

import dev.i726.rocky.event.events.TickListener;
import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.module.setting.NumberSetting;
import dev.i726.rocky.utils.EncryptedString;

import java.lang.reflect.Field;

public final class Fullbright extends Module implements TickListener {

    private final NumberSetting gamma = new NumberSetting(
            EncryptedString.of("Gamma"), 1.0, 100.0, 15.0, 1.0)
            .setDescription(EncryptedString.of("Gamma level — 15 makes everything fully bright without potion effect"));

    private double originalGamma = 1.0;

    // Cached reflection field inside OptionInstance to bypass the [0,1] validator
    private Field optionValueField;

    public Fullbright() {
        super(EncryptedString.of("Fullbright"),
                EncryptedString.of("Gamma-based brightness — undetectable unlike Night Vision potion"),
                -1, CategoryManager.ESP);
        addSettings(gamma);
    }

    @Override
    public void onEnable() {
        eventManager.add(TickListener.class, this);
        if (mc.options != null) {
            originalGamma = readGamma();
            cacheField();
        }
        super.onEnable();
    }

    @Override
    public void onDisable() {
        eventManager.remove(TickListener.class, this);
        if (mc.options != null) writeGamma(originalGamma);
        super.onDisable();
    }

    @Override
    public void onTick() {
        if (mc.options == null) return;
        writeGamma(gamma.getValue());
    }

    // ── Reflection helpers ────────────────────────────────────────────────────

    private void cacheField() {
        if (optionValueField != null) return;
        Object opt = mc.options.getGamma();
        // Try field names used by Fabric-mapped OptionInstance in 1.21
        for (String name : new String[]{"value", "currentValue", "field_24658", "field_26082"}) {
            for (Class<?> c = opt.getClass(); c != null; c = c.getSuperclass()) {
                try {
                    Field f = c.getDeclaredField(name);
                    f.setAccessible(true);
                    optionValueField = f;
                    return;
                } catch (NoSuchFieldException ignored) {}
            }
        }
    }

    private double readGamma() {
        try {
            return mc.options.getGamma().getValue();
        } catch (Exception e) {
            return 1.0;
        }
    }

    private void writeGamma(double value) {
        if (mc.options == null) return;
        // Try reflection first to bypass the [0,1] validator clamping
        if (optionValueField != null) {
            try {
                optionValueField.set(mc.options.getGamma(), value);
                return;
            } catch (Exception ignored) {}
        }
        // Fallback: clamp to valid range (still useful at 1.0 max)
        try {
            mc.options.getGamma().setValue(Math.min(1.0, value));
        } catch (Exception ignored) {}
    }
}
