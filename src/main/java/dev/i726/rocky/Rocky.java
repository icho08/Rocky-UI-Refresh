package dev.i726.rocky;

import dev.i726.rocky.event.EventManager;
import dev.i726.rocky.gui.GuiTheme;
import dev.i726.rocky.managers.CombatManager;
import dev.i726.rocky.managers.FriendManager;
import dev.i726.rocky.module.ModuleManager;
import dev.i726.rocky.managers.ProfileManager;
import dev.i726.rocky.utils.rotation.RotatorManager;
import java.io.File;
import java.io.IOException;
import java.net.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

@SuppressWarnings("all")
public final class Rocky {
        public RotatorManager rotatorManager;
        public ProfileManager profileManager;
        public ModuleManager moduleManager;
        public EventManager eventManager;
        public FriendManager friendManager;
        public CombatManager combatManager;
        public static Minecraft mc;
        public String version = " v1.0";
        public static boolean BETA;
        public static Rocky INSTANCE;
        public boolean guiInitialized;
        public Screen previousScreen = null;
        public long lastModified;
        public File rockyJar;

	public Rocky() throws InterruptedException, IOException {
		INSTANCE = this;
		mc = Minecraft.getInstance();
		GuiTheme.loadTheme();
		this.eventManager = new EventManager();
		this.moduleManager = new ModuleManager();
		this.combatManager = new CombatManager();
		this.rotatorManager = new RotatorManager();
		this.profileManager = new ProfileManager();
		this.friendManager = new FriendManager();

		this.getProfileManager().loadProfile("default");
		this.setLastModified();
		this.guiInitialized = false;
	}

        public ProfileManager getProfileManager() {
                return profileManager;
        }

        public ModuleManager getModuleManager() {
                return moduleManager;
        }

        public FriendManager getFriendManager() {
                return friendManager;
        }

        public EventManager getEventManager() {
                return eventManager;
        }

        public CombatManager getCombatManager() {
                return combatManager;
        }

        public void resetModifiedDate() {
                this.rockyJar.setLastModified(lastModified);
        }

        public String getVersion() {
                return version;
        }

        public void setLastModified() {
                try {
                        this.rockyJar = new File(Rocky.class.getProtectionDomain().getCodeSource().getLocation().toURI());
                        this.lastModified = rockyJar.lastModified();
                } catch (URISyntaxException ignored) {}
        }
}
