-dontwarn **
-dontnote **
-dontoptimize
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Keep main class
-keep class dev.i726.rocky.utils.StandaloneBootstrap {
    public static void main(java.lang.String[]);
    public static void agentmain(java.lang.String, java.lang.instrument.Instrumentation);
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
