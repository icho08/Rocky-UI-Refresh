package dev.i726.rocky.module.modules.render;

import dev.i726.rocky.module.Category;
import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.utils.EncryptedString;

public final class NoBounce extends Module {
	public NoBounce() {
		super(EncryptedString.of("No View Bobbing"),
                EncryptedString.of("Removes view bobbing"),
				-1,
				CategoryManager.ESP);
	}
}
