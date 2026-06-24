-dontwarn **
-dontnote **
-dontoptimize
-ignorewarnings
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Keep main class and all agent entry points
-keep class dev.i726.rocky.utils.StandaloneBootstrap {
    public static void main(java.lang.String[]);
    public static void agentmain(java.lang.String, java.lang.instrument.Instrumentation);
    public static void premain(java.lang.String, java.lang.instrument.Instrumentation);
}

# Keep Lunar compat layer — called via reflection, must not be renamed
-keep class dev.i726.rocky.utils.lunar.LunarCompat {
    public static ** detect(java.lang.ClassLoader);
    public static ** init(java.lang.instrument.Instrumentation, java.lang.ClassLoader);
    public static ** setNoFall(boolean);
    public static ** setSprint(boolean);
    public static ** setVelocity(boolean, float, float);
}
-keep class dev.i726.rocky.utils.lunar.LunarHooks { *; }
-keep class dev.i726.rocky.utils.lunar.LunarEventBridge {
    public static ** setup();
    public static ** shutdown();
}

# Keep all classes in rocky package
-keep class dev.i726.rocky.** { *; }

# Keep Fabric mod initializer
-keep class * implements net.fabricmc.api.ModInitializer {
    public void onInitialize();
}

# Keep mixins
-keep @org.spongepowered.asm.mixin.Mixin class * { *; }

# Keep Minecraft classes
-keep class net.minecraft.** { *; }
-keep class net.fabricmc.** { *; }

# Keep Lombok generated methods
-keepclassmembers class * {
    @lombok.** *;
}
