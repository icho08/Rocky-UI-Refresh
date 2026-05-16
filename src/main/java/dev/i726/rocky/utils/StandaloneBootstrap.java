package dev.i726.rocky.utils;

import java.io.File;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Method;
import java.net.StandardProtocolFamily;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Scanner;

public final class StandaloneBootstrap {

    public static void main(String[] args) {
        System.out.println("------------------------------------------");
        System.out.println("       Rocky Client - Injector v3.5       ");
        System.out.println("------------------------------------------");

        try {
            System.out.println("[+] Clearing old temp-JAR cache...");
            clearOldJars();

            // Copy to a STABLE location that persists between injector runs.
            // Users add -javaagent pointing here ONCE; it auto-updates every time
            // they run this tool.
            File jarFile  = new File(StandaloneBootstrap.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            File stableDir = new File(System.getProperty("user.home"), ".rocky");
            stableDir.mkdirs();
            File stableJar = new File(stableDir, "rocky-agent.jar");
            Files.copy(jarFile.toPath(), stableJar.toPath(), StandardCopyOption.REPLACE_EXISTING);
            System.out.println("[+] Agent JAR updated: " + stableJar.getAbsolutePath());

            // Also stage a temp copy for the dynamic-attach path (agentmain needs it)
            File tempJar = new File(System.getProperty("user.home"),
                    ".rocky-final-" + System.currentTimeMillis() + ".jar");
            Files.copy(jarFile.toPath(), tempJar.toPath(), StandardCopyOption.REPLACE_EXISTING);

            // Score and rank all candidate processes; prefer the deepest child
            List<ProcessHandle> matches = findMinecraftProcesses();
            if (matches.isEmpty()) {
                System.err.println("[!] No running Minecraft / Lunar process found.");
                printAgentFallback(stableJar.getAbsolutePath());
                return;
            }

            ProcessHandle target;
            if (matches.size() == 1) {
                target = matches.get(0);
                System.out.println("[i] Auto-selecting PID: " + target.pid()
                        + "  (" + getProcessLabel(target) + ")");
            } else {
                System.out.println("[?] Multiple instances found:");
                for (int i = 0; i < matches.size(); i++) {
                    ProcessHandle ph = matches.get(i);
                    System.out.printf("  [%d] PID %-7d %s\n",
                            i + 1, ph.pid(), getProcessLabel(ph));
                }
                System.out.print("Selection: ");
                Scanner scanner = new Scanner(System.in);
                target = matches.get(scanner.nextInt() - 1);
            }

            System.out.println("[+] Temp JAR staged for attach: " + tempJar.getName());

            // Try dynamic injection; falls back to javaagent instructions if unsupported
            boolean ok = tryInjectWithFallback(target, tempJar.getAbsolutePath());
            if (ok) {
                System.out.println("[✔] Injected successfully!");
                System.out.println("------------------------------------------");
            } else {
                printAgentFallback(stableJar.getAbsolutePath());
            }

        } catch (Exception e) {
            System.err.println("[!] Fatal: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ── Injection with retry + child-process fallback ─────────────────────────

    /**
     * Attempts to attach to {@code target}.  If that fails with
     * AttachNotSupportedException (common on Lunar / Java 21+), scans the
     * process's children for the actual game JVM and retries.  Returns true
     * on success.
     */
    private static boolean tryInjectWithFallback(ProcessHandle primary, String jarPath) {
        // First try: wake up the attach listener and retry up to 3 times
        if (tryInject(primary, jarPath, 3)) return true;

        System.out.println("[~] Primary PID failed — scanning child processes...");
        List<ProcessHandle> children = collectChildren(primary);
        for (ProcessHandle child : children) {
            System.out.printf("[~] Trying child PID %d (%s)...\n",
                    child.pid(), getProcessLabel(child));
            if (tryInject(child, jarPath, 2)) return true;
        }
        return false;
    }

    /**
     * Wakes up the attach listener (SIGQUIT + .attach_pid file trick), then
     * retries VirtualMachine.attach() up to {@code maxAttempts} times.
     */
    private static boolean tryInject(ProcessHandle ph, String jarPath, int maxAttempts) {
        long pid = ph.pid();
        try {
            wakeAttachListener(pid);
        } catch (Exception ignored) {}

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                Thread.sleep(attempt * 800L); // back-off: 0.8 s, 1.6 s, 2.4 s
                injectNow(String.valueOf(pid), jarPath);
                return true;
            } catch (Exception e) {
                String msg = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
                System.out.printf("[~] Attempt %d/%d for PID %d: %s\n",
                        attempt, maxAttempts, pid, msg);
                if (attempt < maxAttempts) {
                    // Re-send SIGQUIT each retry; some JVMs need multiple signals
                    try { wakeAttachListener(pid); } catch (Exception ignored) {}
                }
            }
        }
        return false;
    }

    /**
     * Sends SIGQUIT to the target JVM AND creates the {@code .attach_pid{pid}}
     * sentinel file that HotSpot looks for when deciding whether to start the
     * Attach Listener thread.
     */
    private static void wakeAttachListener(long pid) throws Exception {
        // 1. Sentinel file in /tmp  (Linux hotspot attach mechanism)
        File sentinel = new File("/tmp/.attach_pid" + pid);
        if (!sentinel.exists()) {
            sentinel.createNewFile();
            sentinel.deleteOnExit();
        }
        // 2. Also try the process working directory
        ph(pid).ifPresent(h -> {
            try {
                String cwd = new File("/proc/" + pid + "/cwd").getCanonicalPath();
                File cwdSentinel = new File(cwd, ".attach_pid" + pid);
                if (!cwdSentinel.exists()) {
                    cwdSentinel.createNewFile();
                    cwdSentinel.deleteOnExit();
                }
            } catch (Throwable ignored) {}
        });
        // 3. SIGQUIT → wakes the Attach Listener thread in HotSpot
        new ProcessBuilder("kill", "-s", "QUIT", String.valueOf(pid))
                .inheritIO().start().waitFor();
    }

    /**
     * Injects the agent JAR into the target JVM.
     *
     * Strategy (tried in order):
     *   1. Direct Unix-socket protocol — bypasses the MonitoredHost/hsperfdata
     *      pre-flight check that Lunar suppresses with -XX:-UsePerfData.
     *      The JVM attach listener can still be running even when perfdata is off.
     *   2. com.sun.tools.attach.VirtualMachine — standard path for plain Fabric.
     */
    private static void injectNow(String pid, String jarPath) throws Exception {
        // ── Path 1: direct socket attach (works when -XX:-UsePerfData is set) ──
        try {
            directSocketInject(Long.parseLong(pid), jarPath);
            return;
        } catch (Exception socketEx) {
            // Socket not ready yet or truly disabled — fall through to VirtualMachine
            System.out.printf("[~] Direct socket failed for PID %s: %s\n", pid, socketEx.getMessage());
        }

        // ── Path 2: standard VirtualMachine.attach() (Fabric / non-hardened JVMs) ─
        Class<?> vmClass  = Class.forName("com.sun.tools.attach.VirtualMachine");
        Method   attach   = vmClass.getMethod("attach", String.class);
        Method   loadAgt  = vmClass.getMethod("loadAgent", String.class);
        Method   detach   = vmClass.getMethod("detach");
        Object   vm       = attach.invoke(null, pid);
        try { loadAgt.invoke(vm, jarPath); }
        finally { try { detach.invoke(vm); } catch (Throwable ignored) {} }
    }

    // ── Direct HotSpot attach-socket protocol ─────────────────────────────────

    /**
     * Implements the HotSpot attach wire protocol over a Unix domain socket.
     *
     * The protocol (identical on Linux and macOS):
     *   1.  Create /tmp/.attach_pid{PID} sentinel + send SIGQUIT to start the
     *       attach listener if it isn't running yet.
     *   2.  Wait up to 8 s for /tmp/.java_pid{PID} (the socket) to appear.
     *   3.  Connect and send 5 null-terminated strings:
     *         "1\0"            protocol version
     *         "load\0"         command
     *         "instrument\0"   built-in JVMTI agent (handles -javaagent jars)
     *         "false\0"        isAbsolute (false = instrument is a built-in name)
     *         jarPath + "\0"   options passed to the instrument agent (= JAR path)
     *   4.  Read the ASCII status line; 0 = success.
     */
    private static void directSocketInject(long pid, String jarPath) throws Exception {
        String tmpdir     = System.getProperty("java.io.tmpdir", "/tmp");
        File   socketFile = new File(tmpdir, ".java_pid" + pid);

        // Wake the attach listener (sentinel file + SIGQUIT)
        wakeAttachListener(pid);

        // Wait for the socket to appear (up to 3 s, re-signaling at 1.5 s)
        for (int i = 0; i < 15; i++) {
            if (socketFile.exists()) break;
            Thread.sleep(200);
            if (i == 7) wakeAttachListener(pid); // one re-signal at 1.5 s
        }
        if (!socketFile.exists()) {
            throw new Exception("Attach socket " + socketFile + " did not appear — "
                    + "JVM may have -XX:+DisableAttachMechanism.");
        }

        // Connect and run the load-agent command
        try (SocketChannel ch = SocketChannel.open(StandardProtocolFamily.UNIX)) {
            ch.connect(UnixDomainSocketAddress.of(socketFile.toPath()));

            // Send protocol payload
            writeAttach(ch, "1");           // version
            writeAttach(ch, "load");        // command
            writeAttach(ch, "instrument");  // JVMTI instrument library (built-in)
            writeAttach(ch, "false");       // isAbsolute = false
            writeAttach(ch, jarPath);       // options → interpreted as JAR path

            // Read response: first line = decimal status code
            String response = readAttachResponse(ch);
            String firstLine = response.split("\n", 2)[0].trim();
            int status;
            try {
                status = Integer.parseInt(firstLine);
            } catch (NumberFormatException e) {
                throw new Exception("Unexpected attach response: " + response);
            }
            if (status != 0) {
                String detail = response.contains("\n")
                        ? response.substring(response.indexOf('\n') + 1).trim() : "";
                throw new Exception("Agent load returned status " + status
                        + (detail.isEmpty() ? "" : " — " + detail));
            }
        }
    }

    /** Writes a null-terminated UTF-8 string to the attach socket. */
    private static void writeAttach(WritableByteChannel ch, String s) throws Exception {
        byte[] bytes = (s + "\0").getBytes(StandardCharsets.UTF_8);
        ByteBuffer buf = ByteBuffer.wrap(bytes);
        while (buf.hasRemaining()) ch.write(buf);
    }

    /** Reads the full response from the attach socket as a String. */
    private static String readAttachResponse(SocketChannel ch) throws Exception {
        StringBuilder sb  = new StringBuilder();
        ByteBuffer    buf = ByteBuffer.allocate(256);
        int read;
        while ((read = ch.read(buf)) > 0) {
            buf.flip();
            while (buf.hasRemaining()) sb.append((char) (buf.get() & 0xFF));
            buf.clear();
        }
        return sb.toString();
    }

    private static java.util.Optional<ProcessHandle> ph(long pid) {
        return ProcessHandle.of(pid);
    }

    /**
     * Prints a clear guide for using -javaagent: when dynamic attach fails
     * (typical on Lunar / hardened JVMs).
     */
    private static void printAgentFallback(String jarPath) {
        String jar = jarPath != null ? jarPath : "/path/to/rocky.jar";
        System.err.println();
        System.err.println("------------------------------------------");
        System.err.println("[!] Dynamic attach not supported by this JVM.");
        System.err.println("    This is common with Lunar Client (Java 21+).");
        System.err.println();
        System.err.println("    Use the -javaagent flag instead:");
        System.err.println("    1. Open Lunar Client launcher");
        System.err.println("    2. Go to Settings → JVM Arguments");
        System.err.println("    3. Add: -javaagent:" + jar);
        System.err.println("    4. Launch Minecraft normally — Rocky activates at startup.");
        System.err.println("------------------------------------------");
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

            // ── Shared init path: AgentTarget works for both Fabric and Lunar ──
            // Lunar runs on top of Fabric and uses the same intermediary class
            // names that Rocky's JAR is compiled against.  AgentTarget.init()
            // therefore works unchanged in Lunar.
            Class<?> targetClass = Class.forName(
                    "dev.i726.rocky.utils.AgentTarget", true, bridgeLoader);
            Method initMethod = targetClass.getMethod("init", String.class, Instrumentation.class);
            initMethod.invoke(null, args, inst);

            if (isLunar) {
                // ── LUNAR EXTRA: event bridge replaces missing Mixin hooks ─────
                // Rocky's Mixins (ClientConnectionMixin, ClientPlayerEntityMixin,
                // etc.) are never registered in Lunar because Rocky is loaded as
                // a -javaagent, not as a Fabric mod.  LunarEventBridge hooks the
                // Netty pipeline and fires the same events the Mixins would have.
                Class<?> bridge = Class.forName(
                        "dev.i726.rocky.utils.lunar.LunarEventBridge", true, bridgeLoader);
                Method setup = bridge.getMethod("setup");
                setup.invoke(null);
                System.out.println("[Rocky] Lunar event bridge started.");
            }

        } catch (Throwable t) {
            System.err.println("[Rocky] Agent init failed: " + t);
            t.printStackTrace();
        }
    }

    // ── Lunar detection ───────────────────────────────────────────────────────

    /**
     * Returns true if the JVM is Lunar Client.
     *
     * Contrary to what you might expect, Lunar runs on top of Fabric and uses
     * Fabric intermediary class names (net.minecraft.class_XXX), NOT Mojang
     * names.  The reliable distinguisher is the com.moonsworth.* launcher
     * classes that are always present in Lunar's JVM.
     */
    private static boolean detectLunar(ClassLoader loader) {
        // Primary: Lunar's Genesis launcher is always present
        try {
            Class.forName("com.moonsworth.lunar.genesis.Genesis", false, loader);
            return true;
        } catch (ClassNotFoundException ignored) {}
        // Fallback: older / variant Lunar builds
        try {
            Class.forName("com.moonsworth.lunar.client.LunarClient", false, loader);
            return true;
        } catch (ClassNotFoundException ignored) {}
        try {
            Class.forName("com.moonsworth.lunar.patcher.LunarPatcher", false, loader);
            return true;
        } catch (ClassNotFoundException ignored) {}
        return false;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static void clearOldJars() {
        File homeDir = new File(System.getProperty("user.home"));
        File[] files = homeDir.listFiles((dir, name) ->
                name.startsWith(".rocky-") && name.endsWith(".jar"));
        if (files != null) {
            for (File f : files) { try { f.delete(); } catch (Exception ignored) {} }
        }
    }

    private static String getProcessLabel(ProcessHandle ph) {
        String d = (ph.info().command().orElse("") + " "
                + String.join(" ", ph.info().arguments().orElse(new String[0]))).toLowerCase();
        if (d.contains("lunar"))    return "Lunar Client";
        if (d.contains("modrinth")) return "Modrinth App";
        if (d.contains("forge"))    return "Forge";
        if (d.contains("fabric") || d.contains("knotclient")) return "Fabric";
        return "Minecraft";
    }

    /**
     * Finds candidate JVM processes and sorts them by a relevance score so
     * the actual game JVM (deepest child, most MC-like arguments) appears first.
     *
     * Scoring:
     *   +3  command-line contains "net.minecraft" (main class)
     *   +3  command-line contains "knotclient" / "minecraftclient" (Fabric main)
     *   +3  command-line contains "com.moonsworth" (Lunar main)
     *   +2  command-line contains "-cp" or "-classpath" (real JVM, not wrapper)
     *   +2  process has a parent that is also a candidate (child > parent)
     *   +1  command-line contains "minecraft"
     *   +1  command-line contains "lunar"
     *   -2  command is just "java" with nothing minecraft-specific (likely launcher)
     */
    private static List<ProcessHandle> findMinecraftProcesses() {
        List<ProcessHandle> all = new ArrayList<>();
        for (ProcessHandle ph : ProcessHandle.allProcesses().toArray(ProcessHandle[]::new)) {
            String full = (ph.info().command().orElse("") + " "
                    + String.join(" ", ph.info().arguments().orElse(new String[0]))).toLowerCase();
            boolean isJava = full.contains("java");
            boolean hasMC  = full.contains("minecraft") || full.contains("lunar")
                    || full.contains("knotclient") || full.contains("modrinth")
                    || full.contains("theseus")    || full.contains("moonsworth");
            if (isJava && hasMC) all.add(ph);
        }

        // Build a set of PIDs in our candidate list for child-detection
        java.util.Set<Long> candidatePids = new java.util.HashSet<>();
        all.forEach(p -> candidatePids.add(p.pid()));

        all.sort((a, b) -> Integer.compare(score(b, candidatePids), score(a, candidatePids)));
        return all;
    }

    private static int score(ProcessHandle ph, java.util.Set<Long> siblings) {
        String full = (ph.info().command().orElse("") + " "
                + String.join(" ", ph.info().arguments().orElse(new String[0]))).toLowerCase();
        int s = 0;
        if (full.contains("net.minecraft"))       s += 3;
        if (full.contains("knotclient") || full.contains("minecraftclient")) s += 3;
        if (full.contains("moonsworth") || full.contains("com.lunar"))       s += 3;
        if (full.contains("-cp") || full.contains("-classpath"))             s += 2;
        if (full.contains("minecraft"))                                       s += 1;
        if (full.contains("lunar"))                                           s += 1;
        // Bonus if this process's parent is also a candidate (this is the child JVM)
        ph.parent().ifPresent(parent -> {
            if (siblings.contains(parent.pid())) {
                // can't modify s here directly; handled via lambda workaround below
            }
        });
        // Parent-is-candidate check via re-query
        boolean parentIsCandidate = ph.parent()
                .map(p -> siblings.contains(p.pid()))
                .orElse(false);
        if (parentIsCandidate) s += 2;
        return s;
    }

    /**
     * Recursively collects all child and grandchild processes of {@code root}
     * that look like a JVM, ordered deepest-first.
     */
    private static List<ProcessHandle> collectChildren(ProcessHandle root) {
        List<ProcessHandle> result = new ArrayList<>();
        root.children().forEach(child -> {
            String full = (child.info().command().orElse("") + " "
                    + String.join(" ", child.info().arguments().orElse(new String[0]))).toLowerCase();
            if (full.contains("java")) result.add(child);
            result.addAll(collectChildren(child));
        });
        return result;
    }
}
