package dev.i726.rocky.module.modules.misc;

import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.module.setting.ModeSetting;
import dev.i726.rocky.utils.EncryptedString;

public final class VersionSpoof extends Module {
    public enum Version {
        v1_21_11(776, "1.21.11"),
        v1_21_10(775, "1.21.10"),
        v1_21_9(774, "1.21.9"),
        v1_21_8(773, "1.21.8"),
        v1_21_7(772, "1.21.7"),
        v1_21_6(771, "1.21.6"),
        v1_21_5(770, "1.21.5"),
        v1_21_4(769, "1.21.4"),
        v1_21_2(768, "1.21.2/3"),
        v1_21(767, "1.21/1.21.1"),
        v1_20_6(766, "1.20.5/6"),
        v1_20_4(765, "1.20.3/4"),
        v1_20_2(764, "1.20.2"),
        v1_20(763, "1.20/1.20.1"),
        v1_19_4(762, "1.19.4"),
        v1_19_3(761, "1.19.3"),
        v1_19_2(760, "1.19.1/2"),
        v1_19(759, "1.19"),
        v1_18_2(758, "1.18.2"),
        v1_18(757, "1.18/1.18.1"),
        v1_17_1(756, "1.17.1"),
        v1_17(755, "1.17"),
        v1_16_5(754, "1.16.5"),
        v1_16_3(753, "1.16.3/4"),
        v1_16_2(751, "1.16.2"),
        v1_16_1(736, "1.16.1"),
        v1_16(735, "1.16"),
        v1_15_2(578, "1.15.2"),
        v1_14_4(498, "1.14.4"),
        v1_13_2(404, "1.13.2"),
        v1_12_2(340, "1.12.2"),
        v1_8_9(47, "1.8.x");

        public final int protocol;
        public final String name;

        Version(int protocol, String name) {
            this.protocol = protocol;
            this.name = name;
        }
        
        @Override
        public String toString() {
            return name;
        }
    }

    private final ModeSetting<Version> versionMode = new ModeSetting<>(
            EncryptedString.of("Version"), Version.v1_21, Version.class)
            .setDescription(EncryptedString.of("The Minecraft version you want servers to think you are using."));

    public VersionSpoof() {
        super(EncryptedString.of("Version Spoof"),
                EncryptedString.of("Changes protocol version"),
                -1,
                CategoryManager.NETWORK);
        addSettings(versionMode);
    }

    public int getSpoofedProtocol() {
        return versionMode.getMode().protocol;
    }
}
