package dev.i726.rocky.module.modules.misc;

import dev.i726.rocky.module.Category;
import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.utils.EncryptedString;

public final class NoBreakDelay extends Module {
	public NoBreakDelay() {
		super(EncryptedString.of("Fast Break"),
                EncryptedString.of("Removes block break delay"),
				-1,
				CategoryManager.AUTOMATION);
	}
}
