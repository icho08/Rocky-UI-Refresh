package dev.i726.rocky.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.util.Map;

@Pseudo
@Mixin(targets = "com.terraformersmc.modmenu.ModMenu", remap = false)
public class ModMenuHideMixin {

    @Inject(method = "onInitializeClient", at = @At("RETURN"), remap = false)
    private void hideRockyFromModMenu(CallbackInfo ci) {
        try {
            Class<?> modMenuClass = Class.forName("com.terraformersmc.modmenu.ModMenu");
            for (Field field : modMenuClass.getDeclaredFields()) {
                if (Map.class.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    Object value = field.get(null);
                    if (value instanceof Map<?, ?> map) {
                        map.remove("rocky");
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }
}
