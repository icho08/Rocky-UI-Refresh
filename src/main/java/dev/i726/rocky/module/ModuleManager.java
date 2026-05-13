package dev.i726.rocky.module;

import dev.i726.rocky.Rocky;
import dev.i726.rocky.event.events.ButtonListener;
import dev.i726.rocky.module.modules.client.ClickGUI;
import dev.i726.rocky.module.modules.client.Friends;
import dev.i726.rocky.module.modules.client.SelfDestruct;
import dev.i726.rocky.module.modules.combat.*;
import dev.i726.rocky.module.modules.misc.*;
import dev.i726.rocky.module.modules.movement.*;
import dev.i726.rocky.module.modules.render.*;
import dev.i726.rocky.module.setting.KeybindSetting;
import dev.i726.rocky.utils.EncryptedString;

import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public final class ModuleManager implements ButtonListener {
        private final List<Module> modules = new ArrayList<>();

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

                //Misc
                add(new AutoXP());
                add(new NoJumpDelay());
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
                add(new FastUse());
                add(new AutoTool());
                add(new AutoRespawn());
                add(new DragClick());
                add(new VersionSpoof());

                //Render
                add(new HUD());
                add(new NightVision());
                add(new NoBounce());
                add(new PlayerESP());
                add(new StorageEsp());
                add(new Chams());
                add(new TargetHud());
                add(new ShowHealth());
                add(new ShowArmor());

                //Client
                add(new ClickGUI());
                add(new Friends());
                add(new SelfDestruct());
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

        @Override
        public void onButtonPress(ButtonEvent event) {
                
                if(!SelfDestruct.destruct) {
                        modules.forEach(module -> {
                                if(module.getKey() == event.button && event.action == GLFW.GLFW_PRESS) {
                                        System.out.println("[Rocky] Toggling module: " + module.getName() + " (key=" + module.getKey() + ")");
                                        module.toggle();
                                }
                        });
                }
        }
}
