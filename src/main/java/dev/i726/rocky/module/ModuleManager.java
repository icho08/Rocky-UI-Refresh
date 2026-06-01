package dev.i726.rocky.module;

import dev.i726.rocky.Rocky;
import dev.i726.rocky.event.events.ButtonListener;
import dev.i726.rocky.module.modules.blatant.*;
import dev.i726.rocky.module.modules.client.*;
import dev.i726.rocky.module.modules.combat.*;
import dev.i726.rocky.module.modules.misc.*;
import dev.i726.rocky.module.modules.movement.*;
import dev.i726.rocky.module.modules.render.*;
import dev.i726.rocky.module.setting.KeybindSetting;
import dev.i726.rocky.module.setting.Setting;
import dev.i726.rocky.utils.EncryptedString;

import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ModuleManager implements ButtonListener {
        private final List<Module> modules = new ArrayList<>();
        private final Map<Integer, Long> lastToggleTime = new HashMap<>();
        private static final long DEBOUNCE_MS = 500;

        public ModuleManager() {
                addModules();
                addKeybinds();
        }

        public void addModules() {
                //Combat
                add(new AimAssist());
                add(new AnchorMacro());
                add(new AutoCrystal());
                add(new AutoDoubleHand());
                add(new AutoHitCrystal());
                add(new AutoInventoryTotem());
                add(new SilentAim());
                add(new Reach());
                add(new HitSwap());
                add(new Robobot());
                add(new TriggerBot());
                add(new AutoPot());
                add(new AutoPotRefill());
                add(new AutoWTap());
                add(new CrystalOptimizer());
                add(new DoubleAnchor());
                add(new HoverTotem());
                add(new NoMissDelay());
                add(new ShieldDisabler());
                add(new TotemOffhand());
                add(new AutoJumpReset());
                add(new AutoFireball());
                add(new BoxIn());
                add(new Hitboxes());
                add(new Velocity());
                add(new Criticals());
                add(new KillAura());
                add(new Strafe());
                add(new Surround());
                add(new BedBreaker());

                //Misc
                add(new ChestStealer());
                add(new HandDump());
                add(new AutoArmor());
                add(new AutoEat());
                add(new AntiAFK());
                add(new AutoXP());
                add(new NoJumpDelay());
                add(new PacketLogger());
                add(new PingSpoof());
                add(new FakeLag());
                add(new AutoClicker());
                add(new KeyPearl());
                add(new NoBreakDelay());
                add(new Freecam());
                add(new PackSpoof());
                add(new Sprint());
                add(new BridgeAssist());
                add(new GodBridge());
                add(new SmartBridge());
                add(new Scaffold());
                add(new NoFall());
                add(new Clutch());
                add(new Step());
                add(new FastUse());
                add(new AutoTool());
                add(new AutoRespawn());
                add(new DragClick());
                add(new VersionSpoof());
                add(new BypassAssist());
                add(new Timer());
                add(new InvMove());

                //Movement
                add(new LongJump());

                //Render
                add(new HUD());
                add(new NightVision());
                add(new NoBounce());
                add(new PlayerESP());
                add(new StorageEsp());
                add(new OreESP());
                add(new ItemESP());
                add(new Tracers());
                add(new Chams());
                add(new TargetHud());
                add(new ShowHealth());
                add(new ShowArmor());
                add(new HidePlayers());
                add(new Fullbright());
                add(new NameTags());

                //Blatant
                add(new Fly());
                add(new Speed());
                add(new Spider());
                add(new Teleport());
                add(new Jesus());
                add(new HighJump());
                add(new NoSlowdown());
                add(new Phase());

                //Client
                add(new ClickGUI());
                add(new Friends());
                add(new SelfDestruct());
                add(new BlatantModules());
                add(new ThemePicker());
        }

        public List<Module> getEnabledModules() {
                return modules.stream()
                                .filter(Module::isEnabled)
                                .toList();
        }


        public List<Module> getModules() {
                return modules;
        }

        public void addKeybinds() {
                Rocky.INSTANCE.getEventManager().add(ButtonListener.class, this);

                for (Module module : modules)
                        module.addSetting(new KeybindSetting(EncryptedString.of("Keybind"), module.getKey(), true).setDescription(EncryptedString.of("Key to enabled the module")));
        }

        public List<Module> getModulesInCategory(Category category) {
                return modules.stream()
                                .filter(module -> module.getCategory().equals(category))
                                .toList();
        }

        @SuppressWarnings("unchecked")
        public <T extends Module> T getModule(Class<T> moduleClass) {
                return (T) modules.stream()
                                .filter(moduleClass::isInstance)
                                .findFirst()
                                .orElse(null);
        }

        public Module getModuleByName(String name) {
                return modules.stream()
                                .filter(m -> m.getName().toString().equalsIgnoreCase(name))
                                .findFirst()
                                .orElse(null);
        }

        public void add(Module module) {
                modules.add(module);
        }

        private int getActiveKey(Module module) {
                for (Setting<?> s : module.getSettings()) {
                        if (s instanceof KeybindSetting kb && kb.isModuleKey()) {
                                return kb.getKey();
                        }
                }
                return module.getKey();
        }

        @Override
        public void onButtonPress(ButtonEvent event) {
                if (event.action != GLFW.GLFW_PRESS) return;
                if (SelfDestruct.destruct) return;

                long now = System.currentTimeMillis();
                long last = lastToggleTime.getOrDefault(event.button, 0L);
                if (now - last < DEBOUNCE_MS) return;
                lastToggleTime.put(event.button, now);

                modules.forEach(module -> {
                        int key = getActiveKey(module);
                        if (key != -1 && key == event.button) {
                                module.toggle();
                        }
                });
        }
}
