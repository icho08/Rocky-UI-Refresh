package dev.i726.rocky.utils;

import java.io.File;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Scanner;

public final class StandaloneBootstrap {

    public static void main(String[] args) {
        System.out.println("------------------------------------------");
        System.out.println("      Rocky Client - Wipe & Reset v3.4    ");
        System.out.println("------------------------------------------");

        try {
            System.out.println("[+] Clearing mod JAR cache...");
            clearOldJars();

            List<ProcessHandle> matches = findMinecraftProcesses();
            if (matches.isEmpty()) {
                System.err.println("[!] Error: Could not find any running Minecraft instances.");
                return;
            }

            ProcessHandle target;
            if (matches.size() == 1) {
                target = matches.get(0);
                System.out.println("[i] Auto-selecting PID: " + target.pid());
            } else {
                System.out.println("[?] Multiple instances found:");
                for (int i = 0; i < matches.size(); i++) {
                    ProcessHandle ph = matches.get(i);
                    System.out.printf("[%d] PID: %d | %s\n", i + 1, ph.pid(), getProcessLabel(ph));
                }
                System.out.print("\nSelection: ");
                Scanner scanner = new Scanner(System.in);
                int choice = scanner.nextInt();
                target = matches.get(choice - 1);
            }

            System.out.println("[+] Waking up AttachListener in target VM...");
            new ProcessBuilder("kill", "-3", String.valueOf(target.pid())).start().waitFor();
            Thread.sleep(500);

            File jarFile = new File(StandaloneBootstrap.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            File tempJar = new File(System.getProperty("user.home"),
                    ".rocky-final-" + System.currentTimeMillis() + ".jar");
            Files.copy(jarFile.toPath(), tempJar.toPath(), StandardCopyOption.REPLACE_EXISTING);

            System.out.println("[+] Injecting fresh JAR: " + tempJar.getName());
            inject(String.valueOf(target.pid()), tempJar.getAbsolutePath());

            System.out.println("[✔] Successfully injected with fresh settings!");
            System.out.println("------------------------------------------");
        } catch (Exception e) {
            System.err.println("[!] Injection failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ── Agent entry points ────────────────────────────────────────────────────

    /**
     * Called when the JAR is specified as {@code -javaagent:rocky.jar} at JVM
     * startup.  Lunar users can add this to their launch flags.
     */
    public static void premain(String args, Instrumentation inst) {
        agentmain(args, inst);
    }

    /**
     * Called when the JAR is dynamically attached via VirtualMachine.loadAgent().
     * Detects whether we are inside Lunar Client (Mojang-mapped) or a Fabric
     * environment and routes to the appropriate init path.
     */
    public static void agentmain(String args, Instrumentation inst) {
        System.out.println("[Rocky] Agent attached. Detecting environment...");
        try {
            // ── Locate the game's ClassLoader ──────────────────────────────────
            ClassLoader gameLoader = null;
            for (Class<?> c : inst.getAllLoadedClasses()) {
                String name = c.getName();
                if (name.startsWith("net.minecraft.class_") || name.startsWith("net.minecraft.client.")) {
                    ClassLoader loader = c.getClassLoader();
                    if (loader != null && !loader.getClass().getName().contains("BuiltinClassLoader")) {
                        gameLoader = loader;
                        break;
                    }
                }
            }
            if (gameLoader == null) gameLoader = Thread.currentThread().getContextClassLoader();

            // ── Detect Lunar (Mojang-mapped) vs Fabric (intermediary-mapped) ──
            boolean isLunar = detectLunar(gameLoader);
            System.out.println("[Rocky] Mode: " + (isLunar ? "Lunar Client (Mojang-mapped)" : "Fabric"));

            // ── Load the latest Rocky JAR with the game ClassLoader as parent ─
            File homeDir = new File(System.getProperty("user.home"));
            File[] files = homeDir.listFiles((d, n) ->
                    n.startsWith(".rocky-") && n.endsWith(".jar"));
            if (files == null || files.length == 0) {
                System.err.println("[Rocky] No .rocky-*.jar found in home dir.");
                return;
            }
            Arrays.sort(files, Comparator.comparingLong(File::lastModified).reversed());
            URL jarUrl = files[0].toURI().toURL();
            URLClassLoader bridgeLoader = new URLClassLoader(new URL[]{jarUrl}, gameLoader);

            if (isLunar) {
                // ── LUNAR PATH ─────────────────────────────────────────────────
                // LunarCompat / LunarHooks use only reflection — safe to load here.
                Class<?> compat = Class.forName(
                        "dev.i726.rocky.utils.lunar.LunarCompat", true, bridgeLoader);

                // Mark as Lunar so hooks know which API to use
                Method detect = compat.getMethod("detect", ClassLoader.class);
                detect.invoke(null, gameLoader);

                // Start the Lunar engine (Netty hook + polling loop)
                Method init = compat.getMethod("init", Instrumentation.class, ClassLoader.class);
                init.invoke(null, inst, gameLoader);

            } else {
                // ── FABRIC PATH ────────────────────────────────────────────────
                Class<?> targetClass = Class.forName(
                        "dev.i726.rocky.utils.AgentTarget", true, bridgeLoader);
                Method initMethod = targetClass.getMethod("init", String.class, Instrumentation.class);
                initMethod.invoke(null, args, inst);
            }

        } catch (Throwable t) {
            System.err.println("[Rocky] Agent init failed: " + t);
            t.printStackTrace();
        }
    }

    // ── Lunar detection ───────────────────────────────────────────────────────

    /**
     * Returns true if the JVM is running Lunar Client.
     * Checks for Mojang-mapped class {@code net.minecraft.client.Minecraft}
     * while confirming Fabric intermediary classes are absent.
     */
    private static boolean detectLunar(ClassLoader loader) {
        // 1. Mojang class must exist
        try {
            Class.forName("net.minecraft.client.Minecraft", false, loader);
        } catch (ClassNotFoundException e) {
            return false;
        }
        // 2. Fabric intermediary class must NOT exist (would mean Fabric runtime)
        try {
            Class.forName("net.minecraft.class_310", false, loader);
            return false; // Fabric is present
        } catch (ClassNotFoundException ok) { /* expected in Lunar */ }

        // 3. Extra: Fabric loader itself must be absent
        try {
            Class.forName("net.fabricmc.loader.api.FabricLoader", false, loader);
            return false;
        } catch (ClassNotFoundException ok) { /* expected in Lunar */ }

        return true;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static void clearOldJars() {
        File homeDir = new File(System.getProperty("user.home"));
        File[] files = homeDir.listFiles((dir, name) ->
                name.startsWith(".rocky-") && name.endsWith(".jar"));
        if (files != null) {
            for (File f : files) {
                try { f.delete(); } catch (Exception ignored) {}
            }
        }
    }

    private static String getProcessLabel(ProcessHandle ph) {
        String desc = (ph.info().command().orElse("") + " "
                + String.join(" ", ph.info().arguments().orElse(new String[0]))).toLowerCase();
        if (desc.contains("lunar"))    return "Lunar Client";
        if (desc.contains("modrinth")) return "Modrinth App";
        return "Minecraft Instance";
    }

    private static void inject(String pid, String jarPath) throws Exception {
        Class<?> vmClass     = Class.forName("com.sun.tools.attach.VirtualMachine");
        Method attachMethod  = vmClass.getMethod("attach", String.class);
        Method loadAgent     = vmClass.getMethod("loadAgent", String.class);
        Method detachMethod  = vmClass.getMethod("detach");
        Object vm = attachMethod.invoke(null, pid);
        loadAgent.invoke(vm, jarPath);
        detachMethod.invoke(vm);
    }

    private static List<ProcessHandle> findMinecraftProcesses() {
        List<ProcessHandle> matches = new ArrayList<>();
        for (ProcessHandle ph : ProcessHandle.allProcesses().toArray(ProcessHandle[]::new)) {
            String fullLine = (ph.info().command().orElse("") + " "
                    + String.join(" ", ph.info().arguments().orElse(new String[0]))).toLowerCase();
            if ((fullLine.contains("knotclient") || fullLine.contains("minecraft")
                    || fullLine.contains("modrinth") || fullLine.contains("theseus")
                    || fullLine.contains("lunar"))
                    && fullLine.contains("java")) {
                matches.add(ph);
            }
        }
        return matches;
    }
}
