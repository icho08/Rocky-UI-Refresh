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
            File jarFile = new File(StandaloneBootstrap.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            File stableDir = new File(System.getProperty("user.home"), ".rocky");
            stableDir.mkdirs();
            File stableJar = new File(stableDir, "rocky-agent.jar");
            Files.copy(jarFile.toPath(), stableJar.toPath(), StandardCopyOption.REPLACE_EXISTING);
            System.out.println("[+] Agent JAR updated: " + stableJar.getAbsolutePath());

            // Stage temp copy in /tmp so ANY user's JVM can read it — critical
            // when this injector runs under sudo (user.home=/root/) but the
            // target JVM runs as a normal user who cannot access /root/.
            File tempJar = new File(System.getProperty("java.io.tmpdir"),
                    ".rocky-final-" + System.currentTimeMillis() + ".jar");
            Files.copy(jarFile.toPath(), tempJar.toPath(), StandardCopyOption.REPLACE_EXISTING);
            tempJar.setReadable(true, false); // world-readable

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

            System.out.println("[+] Temp JAR staged for attach: " + tempJar.getAbsolutePath());

            // Clear any previous init signal
            File signal = new File(System.getProperty("java.io.tmpdir"), ".rocky-init-ok");
            signal.delete();

            // Try dynamic injection; falls back to javaagent instructions if unsupported
            boolean ok = tryInjectWithFallback(target, tempJar.getAbsolutePath());
            if (ok) {
                // Poll for the signal file that AgentTarget writes on successful init
                System.out.println("[i] Waiting for Rocky to initialize inside target JVM...");
                boolean confirmed = false;
                for (int i = 0; i < 30; i++) { // up to 15 seconds
                    Thread.sleep(500);
                    if (signal.exists()) {
                        confirmed = true;
                        signal.delete();
                        break;
                    }
                }
                if (confirmed) {
                    System.out.println("[✔] Injected and confirmed running!");
                } else {
                    System.out.println("[✔] Injection dispatched.");
                    System.out.println("[!] Could not confirm Rocky started — check Lunar Client console for errors.");
                }
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
     * Attempts to attach to {@code target}. Injection order:
     * 1. Native GDB injection — works even when -XX:+DisableAttachMechanism is set
     * 2. Direct Unix-socket attach — for standard Fabric JVMs
     * 3. VirtualMachine.attach() — standard JDK path
     * 4. Child-process scan + retry of all above
     */
    private static boolean tryInjectWithFallback(ProcessHandle primary, String jarPath) {
        // ── Attempt 1: Native GDB injection (bypasses DisableAttachMechanism) ──
        if (tryNativeInject(primary.pid(), jarPath))
            return true;

        // ── Attempt 2-3: Java attach paths (Fabric / non-hardened JVMs) ────────
        if (tryInject(primary, jarPath, 3))
            return true;

        System.out.println("[~] Primary PID failed — scanning child processes...");
        List<ProcessHandle> children = collectChildren(primary);
        for (ProcessHandle child : children) {
            System.out.printf("[~] Trying child PID %d (%s)...\n",
                    child.pid(), getProcessLabel(child));
            if (tryNativeInject(child.pid(), jarPath))
                return true;
            if (tryInject(child, jarPath, 2))
                return true;
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
        } catch (Exception ignored) {
        }

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
                    try {
                        wakeAttachListener(pid);
                    } catch (Exception ignored) {
                    }
                }
            }
        }
        return false;
    }

    // ── Native GDB injection (bypasses -XX:+DisableAttachMechanism) ──────────

    /**
     * Compiles a tiny C shim and injects it into the target JVM via GDB + dlopen.
     *
     * Flow:
     * 1. Compile rocky_loader.c → /tmp/rocky_loader_{pid}.so
     * using the current JDK's jni.h / jvmti.h headers.
     * 2. gdb -p PID → call dlopen("/tmp/rocky_loader_{pid}.so", RTLD_NOW)
     * GDB uses ptrace internally; this works even with DisableAttachMechanism.
     * 3. The library's __attribute__((constructor)) fires immediately inside
     * the JVM process:
     * • dlsym(RTLD_DEFAULT, "JNI_GetCreatedJavaVMs") — already in libjvm.so
     * • jvmti->AddToBootstrapClassLoaderSearch(rockyJar)
     * • JNI FindClass + CallStaticVoidMethod → AgentTarget.init("", null)
     * (Instrumentation param is unused in AgentTarget.init body)
     *
     * Requires: gcc + gdb on PATH.
     */
    private static boolean tryNativeInject(long pid, String jarPath) {
        try {
            System.out.println("[+] Trying native GDB injection (bypasses DisableAttachMechanism)...");

            // ── 1. Locate JDK include directory ──────────────────────────────
            String javaHome = resolveJavaHome();
            if (javaHome == null) {
                System.out.println("[~] GDB inject: cannot find JDK include headers.");
                return false;
            }
            String os = System.getProperty("os.name", "").toLowerCase();
            String platformDir = os.contains("mac") ? "darwin" : "linux";
            String incMain = javaHome + "/include";
            String incPlatform = incMain + "/" + platformDir;

            // ── 2. Write C source ─────────────────────────────────────────────
            File cFile = File.createTempFile("rocky_loader_" + pid + "_", ".c");
            cFile.deleteOnExit();
            Files.writeString(cFile.toPath(), buildNativeLoaderSource(jarPath));

            // ── 3. Compile ────────────────────────────────────────────────────
            File soFile = new File(System.getProperty("java.io.tmpdir"),
                    "rocky_loader_" + pid + ".so");
            soFile.deleteOnExit();
            Process gcc = new ProcessBuilder(
                    "gcc", "-shared", "-fPIC",
                    "-o", soFile.getAbsolutePath(),
                    cFile.getAbsolutePath(),
                    "-I" + incMain,
                    "-I" + incPlatform,
                    "-ldl").inheritIO().start();
            int gccExit = gcc.waitFor();
            cFile.delete();
            if (gccExit != 0 || !soFile.exists()) {
                System.out.println("[~] GDB inject: gcc failed (not installed or compile error).");
                return false;
            }
            System.out.println("[+] Loader compiled: " + soFile.getName());

            // ── 4. Inject via GDB ─────────────────────────────────────────────
            // RTLD_NOW = 2 — resolves symbols immediately, constructor fires inline.
            // ProcessBuilder passes -ex values as separate argv entries, so no
            // shell quoting is needed for the path.
            String dlopen = String.format(
                    "call (void*)dlopen(\"%s\", 2)", soFile.getAbsolutePath());
            String output = runGdb(pid, soFile.getAbsolutePath(), false);

            if (output == null) {
                // gdb not on PATH
                System.out.println("[~] GDB inject: gdb not found on PATH.");
                return false;
            }

            if (output.contains("ptrace: Operation not permitted")) {
                System.out.println("[~] ptrace blocked (ptrace_scope=1) — retrying with sudo...");
                output = runGdb(pid, soFile.getAbsolutePath(), true); // sudo gdb
                if (output == null) {
                    System.out.println("[!] GDB inject: sudo gdb failed or not available.");
                    System.out.println("    → Either run this injector with sudo:");
                    System.out.println("        sudo java -jar " + jarPath);
                    System.out.println("    → Or allow ptrace once (resets on reboot):");
                    System.out.println("        echo 0 | sudo tee /proc/sys/kernel/yama/ptrace_scope");
                    return false;
                }
            }

            // GDB exits 0 even on failure — check actual output for real success
            if (output.contains("ptrace:") && !output.contains("0x0000000")) {
                System.out.println("[~] GDB inject: ptrace error — " + firstLine(output, "ptrace:"));
                return false;
            }
            if (output.contains("= (void *) 0x0\n") || output.contains("= (void *) 0x0 \n")) {
                System.out.println("[~] GDB inject: dlopen returned NULL (library failed to load).");
                return false;
            }

            // Give the constructor thread a moment to start Rocky
            Thread.sleep(1500);

            // Check if Rocky actually started via our verification file
            File signal = new File(System.getProperty("java.io.tmpdir"), ".rocky-init-ok");
            if (signal.exists()) {
                System.out.println("[✔] Native GDB injection dispatched and confirmed.");
                return true;
            } else {
                System.out.println("[!] GDB injection dispatched but Rocky did not start.");
                System.out.println("--- GDB Transcript ---");
                System.out.println(output);
                System.out.println("----------------------");
                System.out.println("[i] Check /tmp/rocky_native.log for native-level errors.");
                return false;
            }

        } catch (Exception e) {
            System.out.println("[~] GDB inject error: " + e.getMessage());
            return false;
        }
    }

    /**
     * Runs GDB in batch mode against {@code pid}, executing a script that
     * performs the dlopen call.
     *
     * @param useSudo prefix the command with "sudo -n" (non-interactive sudo)
     */
    private static String runGdb(long pid, String soPath, boolean useSudo) {
        try {
            // Use a robust GDB script that handles the dlopen call safely
            // and waits briefly before detaching to allow the constructor to run.
            String gdbScript = String.format(
                "set confirm off\\n" +
                "set disassembly-flavor intel\\n" +
                "set $dl = (void*(*)(const char*, int)) __libc_dlopen_mode\\n" +
                "if !$dl\\n" +
                "  set $dl = (void*(*)(const char*, int)) dlopen\\n" +
                "end\\n" +
                "if !$dl\\n" +
                "  set $dl = (void*(*)(const char*, int)) __dlopen\\n" +
                "end\\n" +
                "if $dl\\n" +
                "  call $dl(\"%s\", 2)\\n" +
                "  shell sleep 0.5\\n" +
                "end\\n" +
                "detach\\n" +
                "quit\\n", 
                soPath
            );

            File scriptFile = new File(System.getProperty("java.io.tmpdir"), "rocky_gdb_" + pid + ".script");
            java.nio.file.Files.writeString(scriptFile.toPath(), gdbScript);
            scriptFile.setReadable(true, false);

            List<String> cmd = new ArrayList<>();
            if (useSudo) {
                cmd.add("sudo");
                cmd.add("-n");
            }
            cmd.addAll(List.of("gdb", "-p", String.valueOf(pid), "-batch", "-x", scriptFile.getAbsolutePath()));

            Process p = new ProcessBuilder(cmd)
                    .redirectErrorStream(true)
                    .start();
            byte[] raw = p.getInputStream().readAllBytes();
            p.waitFor();
            
            scriptFile.delete();
            return new String(raw, StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            if (e.getMessage() != null && e.getMessage().contains("No such file"))
                return null;
            return "[~] GDB error: " + e.getMessage();
        } catch (Exception e) {
            return "[~] GDB error: " + e.getMessage();
        }
    }

    /**
     * Returns the first complete line in {@code text} that contains {@code marker}.
     */
    private static String firstLine(String text, String marker) {
        for (String line : text.split("\n")) {
            if (line.contains(marker))
                return line.trim();
        }
        return marker;
    }

    /** Finds the JDK home that has include/jni.h. */
    private static String resolveJavaHome() {
        for (String candidate : new String[] {
                System.getProperty("java.home"),
                System.getenv("JAVA_HOME"),
                System.getProperty("java.home") != null
                        ? new File(System.getProperty("java.home")).getParent()
                        : null
        }) {
            if (candidate == null)
                continue;
            if (new File(candidate, "include/jni.h").exists())
                return candidate;
        }
        return null;
    }

    /**
     * Returns the C source for the runtime loader shim.
     * The shim is compiled with -fPIC -shared and loaded via GDB + dlopen.
     */
    // @@JAR_PATH@@ is replaced via String.replace() so no Java format specifiers
    // are needed — avoiding conflicts with C's own % signs inside fprintf calls.
    private static final String NATIVE_LOADER_TEMPLATE = """
            #include <jni.h>
            #include <dlfcn.h>
            #include <stdio.h>
            #include <string.h>

            /*
             * Rocky native loader — injected via GDB + dlopen.
             *
             * Correct ClassLoader hierarchy:
             *   URLClassLoader            <-- Rocky JAR
             *     parent: KnotClassLoader  <-- Minecraft + Fabric API
             *       parent: AppClassLoader
             *
             * The GDB-attached thread's contextClassLoader is NOT KnotClassLoader —
             * it's AppClassLoader or null.  We must scan all live threads to find
             * one whose contextClassLoader is Knot (class name contains "Knot").
             */

            typedef jint (*GetJavaVMs_fn)(JavaVM**, jsize, jsize*);

            /* File-based logging — stderr is invisible when injected into another process */
            static void rlog(const char *msg) {
                FILE *f = fopen("/tmp/rocky_native.log", "a");
                if (f) { fprintf(f, "%s\\n", msg); fflush(f); fclose(f); }
            }
            static void rlogf(const char *fmt, const char *arg) {
                char buf[512];
                snprintf(buf, sizeof(buf), fmt, arg);
                rlog(buf);
            }
            static void rlogi(const char *fmt, int a, int b) {
                char buf[256];
                snprintf(buf, sizeof(buf), fmt, a, b);
                rlog(buf);
            }

            static int check_ex(JNIEnv *env, const char *where) {
                if (!(*env)->ExceptionCheck(env)) return 0;
                rlogf("[Rocky/native] exception @ %s", where);
                (*env)->ExceptionDescribe(env);
                (*env)->ExceptionClear(env);
                return 1;
            }

            /*
             * Scans all live threads and returns the first contextClassLoader whose
             * class name contains "Knot" (i.e. KnotClassLoader).  Returns NULL if
             * none found.
             */
            static jobject find_knot_loader(JNIEnv *env) {
                jclass tCls = (*env)->FindClass(env, "java/lang/Thread");
                if (!tCls) { (*env)->ExceptionClear(env); return NULL; }
                jmethodID getAllM = (*env)->GetStaticMethodID(env, tCls, "getAllStackTraces",
                    "()Ljava/util/Map;");
                if (!getAllM) { (*env)->ExceptionClear(env); return NULL; }
                jobject map = (*env)->CallStaticObjectMethod(env, tCls, getAllM);
                if (!map) { (*env)->ExceptionClear(env); return NULL; }

                jclass mapCls  = (*env)->FindClass(env, "java/util/Map");
                jmethodID ksM  = (*env)->GetMethodID(env, mapCls, "keySet", "()Ljava/util/Set;");
                jobject keySet = (*env)->CallObjectMethod(env, map, ksM);
                jclass setCls  = (*env)->FindClass(env, "java/util/Set");
                jmethodID taM  = (*env)->GetMethodID(env, setCls, "toArray", "()[Ljava/lang/Object;");
                jobjectArray threads = (jobjectArray)(*env)->CallObjectMethod(env, keySet, taM);
                jsize len = (*env)->GetArrayLength(env, threads);

                jmethodID getCtxM = (*env)->GetMethodID(env, tCls, "getContextClassLoader",
                    "()Ljava/lang/ClassLoader;");
                jclass classCls   = (*env)->FindClass(env, "java/lang/Class");
                jmethodID nameM   = (*env)->GetMethodID(env, classCls, "getName",
                    "()Ljava/lang/String;");

                for (jsize i = 0; i < len; i++) {
                    jobject thread = (*env)->GetObjectArrayElement(env, threads, i);
                    jobject loader = (*env)->CallObjectMethod(env, thread, getCtxM);
                    if ((*env)->ExceptionCheck(env)) { (*env)->ExceptionClear(env); continue; }
                    if (!loader) continue;
                    jclass loaderCls = (*env)->GetObjectClass(env, loader);
                    jstring nameJ    = (jstring)(*env)->CallObjectMethod(env, loaderCls, nameM);
                    const char *name = (*env)->GetStringUTFChars(env, nameJ, NULL);
                    /* Check for Knot, Genesis, or other known game loaders */
                    int isKnot = (strstr(name, "Knot") != NULL || strstr(name, "knot") != NULL ||
                                  strstr(name, "Genesis") != NULL || strstr(name, "lunar") != NULL);

                    if (isKnot) {
                        rlogf("[Rocky/native] Found candidate ClassLoader: %s", name);
                        (*env)->ReleaseStringUTFChars(env, nameJ, name);
                        return (*env)->NewGlobalRef(env, loader);
                    }
                    (*env)->ReleaseStringUTFChars(env, nameJ, name);
                }
                return NULL;
            }

            static void do_init(JavaVM *jvm) {
                JNIEnv *env = NULL;
                /* Use AttachCurrentThread instead of Daemon for better stability during injection */
                if ((*jvm)->AttachCurrentThread(jvm, (void**)&env, NULL) != JNI_OK || !env) {
                    return;
                }

                /* 1. Find KnotClassLoader by scanning all live threads */
                jobject knot = find_knot_loader(env);
                if (!knot) {
                    jclass clsCls = (*env)->FindClass(env, "java/lang/ClassLoader");
                    jmethodID sysM = (*env)->GetStaticMethodID(env, clsCls, "getSystemClassLoader",
                        "()Ljava/lang/ClassLoader;");
                    if (!(*env)->ExceptionCheck(env) && sysM)
                        knot = (*env)->CallStaticObjectMethod(env, clsCls, sysM);
                    (*env)->ExceptionClear(env);
                }
                if (!knot) return;

                /* 2. URLClassLoader(new URL[]{ new URL("file:@@JAR_PATH@@") }, knot) */
                jclass urlCls = (*env)->FindClass(env, "java/net/URL");
                if ((*env)->ExceptionCheck(env) || !urlCls) { (*env)->ExceptionClear(env); return; }
                jmethodID urlCtor = (*env)->GetMethodID(env, urlCls, "<init>", "(Ljava/lang/String;)V");
                if ((*env)->ExceptionCheck(env) || !urlCtor) { (*env)->ExceptionClear(env); return; }
                jstring urlStr = (*env)->NewStringUTF(env, "file:@@JAR_PATH@@");
                jobject urlObj = (*env)->NewObject(env, urlCls, urlCtor, urlStr);
                if ((*env)->ExceptionCheck(env) || !urlObj) { (*env)->ExceptionClear(env); return; }
                jobjectArray urlArr = (*env)->NewObjectArray(env, 1, urlCls, urlObj);
                if ((*env)->ExceptionCheck(env) || !urlArr) { (*env)->ExceptionClear(env); return; }

                jclass uclCls = (*env)->FindClass(env, "java/net/URLClassLoader");
                if ((*env)->ExceptionCheck(env) || !uclCls) { (*env)->ExceptionClear(env); return; }
                jmethodID uclCtor = (*env)->GetMethodID(env, uclCls, "<init>",
                    "([Ljava/net/URL;Ljava/lang/ClassLoader;)V");
                if ((*env)->ExceptionCheck(env) || !uclCtor) { (*env)->ExceptionClear(env); return; }
                jobject ucl = (*env)->NewObject(env, uclCls, uclCtor, urlArr, knot);
                if ((*env)->ExceptionCheck(env) || !ucl) { (*env)->ExceptionClear(env); return; }

                /* 3. ucl.loadClass("dev.i726.rocky.utils.AgentTarget") */
                jclass ldrCls = (*env)->FindClass(env, "java/lang/ClassLoader");
                jmethodID loadM = (*env)->GetMethodID(env, ldrCls, "loadClass", "(Ljava/lang/String;)Ljava/lang/Class;");
                if ((*env)->ExceptionCheck(env) || !loadM) { (*env)->ExceptionClear(env); return; }
                jstring cn = (*env)->NewStringUTF(env, "dev.i726.rocky.utils.AgentTarget");
                jclass agentCls = (jclass)(*env)->CallObjectMethod(env, ucl, loadM, cn);
                if ((*env)->ExceptionCheck(env) || !agentCls) { (*env)->ExceptionClear(env); return; }

                /* 4. AgentTarget.init("", null) */
                jmethodID initM = (*env)->GetStaticMethodID(env, agentCls, "init",
                    "(Ljava/lang/String;Ljava/lang/instrument/Instrumentation;)V");
                if ((*env)->ExceptionCheck(env) || !initM) { (*env)->ExceptionClear(env); return; }
                jstring empty = (*env)->NewStringUTF(env, "");
                (*env)->CallStaticVoidMethod(env, agentCls, initM, empty, NULL);
                (*env)->ExceptionClear(env);
            }

            __attribute__((constructor)) static void rocky_ctor(void) {
                GetJavaVMs_fn fn = (GetJavaVMs_fn)dlsym(RTLD_DEFAULT, "JNI_GetCreatedJavaVMs");
                if (!fn) return;
                JavaVM *vms[1]; jsize n = 0;
                if (fn(vms, 1, &n) == 0 && n > 0) {
                    do_init(vms[0]);
                }
            }
            """;

    private static String buildNativeLoaderSource(String jarPath) {
        // Escape for C string literal (backslashes and double-quotes only)
        String escaped = jarPath.replace("\\", "\\\\").replace("\"", "\\\"");
        // Use replace(), not formatted() — avoids conflicts with C's own % in fprintf
        return NATIVE_LOADER_TEMPLATE.replace("@@JAR_PATH@@", escaped);
    }

    /**
     * Sends SIGQUIT to the target JVM AND creates the {@code .attach_pid{pid}}
     * sentinel file that HotSpot looks for when deciding whether to start the
     * Attach Listener thread.
     */
    private static void wakeAttachListener(long pid) throws Exception {
        // 1. Sentinel file in /tmp (Linux hotspot attach mechanism)
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
            } catch (Throwable ignored) {
            }
        });
        // 3. SIGQUIT → wakes the Attach Listener thread in HotSpot
        new ProcessBuilder("kill", "-s", "QUIT", String.valueOf(pid))
                .inheritIO().start().waitFor();
    }

    /**
     * Injects the agent JAR into the target JVM.
     *
     * Strategy (tried in order):
     * 1. Direct Unix-socket protocol — bypasses the MonitoredHost/hsperfdata
     * pre-flight check that Lunar suppresses with -XX:-UsePerfData.
     * The JVM attach listener can still be running even when perfdata is off.
     * 2. com.sun.tools.attach.VirtualMachine — standard path for plain Fabric.
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
        Class<?> vmClass = Class.forName("com.sun.tools.attach.VirtualMachine");
        Method attach = vmClass.getMethod("attach", String.class);
        Method loadAgt = vmClass.getMethod("loadAgent", String.class);
        Method detach = vmClass.getMethod("detach");
        Object vm = attach.invoke(null, pid);
        try {
            loadAgt.invoke(vm, jarPath);
        } finally {
            try {
                detach.invoke(vm);
            } catch (Throwable ignored) {
            }
        }
    }

    // ── Direct HotSpot attach-socket protocol ─────────────────────────────────

    /**
     * Implements the HotSpot attach wire protocol over a Unix domain socket.
     *
     * The protocol (identical on Linux and macOS):
     * 1. Create /tmp/.attach_pid{PID} sentinel + send SIGQUIT to start the
     * attach listener if it isn't running yet.
     * 2. Wait up to 8 s for /tmp/.java_pid{PID} (the socket) to appear.
     * 3. Connect and send 5 null-terminated strings:
     * "1\0" protocol version
     * "load\0" command
     * "instrument\0" built-in JVMTI agent (handles -javaagent jars)
     * "false\0" isAbsolute (false = instrument is a built-in name)
     * jarPath + "\0" options passed to the instrument agent (= JAR path)
     * 4. Read the ASCII status line; 0 = success.
     */
    private static void directSocketInject(long pid, String jarPath) throws Exception {
        String tmpdir = System.getProperty("java.io.tmpdir", "/tmp");
        File socketFile = new File(tmpdir, ".java_pid" + pid);

        // Wake the attach listener (sentinel file + SIGQUIT)
        wakeAttachListener(pid);

        // Wait for the socket to appear (up to 3 s, re-signaling at 1.5 s)
        for (int i = 0; i < 15; i++) {
            if (socketFile.exists())
                break;
            Thread.sleep(200);
            if (i == 7)
                wakeAttachListener(pid); // one re-signal at 1.5 s
        }
        if (!socketFile.exists()) {
            throw new Exception("Attach socket " + socketFile + " did not appear — "
                    + "JVM may have -XX:+DisableAttachMechanism.");
        }

        // Connect and run the load-agent command
        try (SocketChannel ch = SocketChannel.open(StandardProtocolFamily.UNIX)) {
            ch.connect(UnixDomainSocketAddress.of(socketFile.toPath()));

            // Send protocol payload
            writeAttach(ch, "1"); // version
            writeAttach(ch, "load"); // command
            writeAttach(ch, "instrument"); // JVMTI instrument library (built-in)
            writeAttach(ch, "false"); // isAbsolute = false
            writeAttach(ch, jarPath); // options → interpreted as JAR path

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
                        ? response.substring(response.indexOf('\n') + 1).trim()
                        : "";
                throw new Exception("Agent load returned status " + status
                        + (detail.isEmpty() ? "" : " — " + detail));
            }
        }
    }

    /** Writes a null-terminated UTF-8 string to the attach socket. */
    private static void writeAttach(WritableByteChannel ch, String s) throws Exception {
        byte[] bytes = (s + "\0").getBytes(StandardCharsets.UTF_8);
        ByteBuffer buf = ByteBuffer.wrap(bytes);
        while (buf.hasRemaining())
            ch.write(buf);
    }

    /** Reads the full response from the attach socket as a String. */
    private static String readAttachResponse(SocketChannel ch) throws Exception {
        StringBuilder sb = new StringBuilder();
        ByteBuffer buf = ByteBuffer.allocate(256);
        int read;
        while ((read = ch.read(buf)) > 0) {
            buf.flip();
            while (buf.hasRemaining())
                sb.append((char) (buf.get() & 0xFF));
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
     * startup. At premain time, Minecraft/Knot classes are NOT loaded yet,
     * so we defer the real init to a background thread that polls for them.
     */
    public static void premain(String args, Instrumentation inst) {
        System.out.println("[Rocky] premain called — deferring until game classes load...");
        Thread deferThread = new Thread(() -> {
            try {
                // Poll until KnotClassLoader appears on a thread (up to 120 s)
                ClassLoader gameLoader = null;
                for (int i = 0; i < 240 && gameLoader == null; i++) {
                    Thread.sleep(500);
                    gameLoader = findKnotClassLoader();
                }
                if (gameLoader == null) {
                    // Last resort: scan loaded classes via Instrumentation
                    gameLoader = findGameLoaderFromInst(inst);
                }
                if (gameLoader == null) {
                    System.err.println("[Rocky] premain: KnotClassLoader never appeared. Aborting.");
                    return;
                }
                System.out.println("[Rocky] premain: KnotClassLoader found, initializing...");
                doAgentInit(args, inst, gameLoader);
            } catch (Throwable t) {
                System.err.println("[Rocky] premain deferred init failed: " + t);
                t.printStackTrace();
            }
        }, "Rocky-PremainDefer");
        deferThread.setDaemon(true);
        deferThread.start();
    }

    /**
     * Called when the JAR is dynamically attached via VirtualMachine.loadAgent().
     * The game is already running, so KnotClassLoader should be available.
     */
    public static void agentmain(String args, Instrumentation inst) {
        System.out.println("[Rocky] Agent attached. Detecting environment...");
        try {
            // ── Locate the game's ClassLoader ──────────────────────────────────
            ClassLoader gameLoader = findGameLoaderFromInst(inst);
            if (gameLoader == null)
                gameLoader = findKnotClassLoader();
            if (gameLoader == null)
                gameLoader = Thread.currentThread().getContextClassLoader();
            doAgentInit(args, inst, gameLoader);
        } catch (Throwable t) {
            System.err.println("[Rocky] Agent init failed: " + t);
            t.printStackTrace();
        }
    }

    /**
     * Scans all live threads for one whose contextClassLoader is KnotClassLoader.
     */
    private static ClassLoader findKnotClassLoader() {
        for (Thread t : Thread.getAllStackTraces().keySet()) {
            try {
                ClassLoader cl = t.getContextClassLoader();
                if (cl != null && cl.getClass().getName().toLowerCase().contains("knot")) {
                    return cl;
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    /**
     * Scans Instrumentation's loaded classes for a Minecraft class and returns
     * its ClassLoader.
     */
    private static ClassLoader findGameLoaderFromInst(Instrumentation inst) {
        for (Class<?> c : inst.getAllLoadedClasses()) {
            String name = c.getName();
            if (name.startsWith("net.minecraft.class_") || name.startsWith("net.minecraft.client.")) {
                ClassLoader loader = c.getClassLoader();
                if (loader != null && !loader.getClass().getName().contains("BuiltinClassLoader")) {
                    return loader;
                }
            }
        }
        return null;
    }

    /**
     * Common init path for both premain (deferred) and agentmain.
     */
    private static void doAgentInit(String args, Instrumentation inst, ClassLoader gameLoader) throws Exception {
        // ── Detect Lunar vs Fabric ──
        boolean isLunar = detectLunar(gameLoader);
        System.out.println("[Rocky] Mode: " + (isLunar ? "Lunar Client" : "Fabric"));
        System.out.println("[Rocky] Game ClassLoader: " + gameLoader.getClass().getName());

        // ── Find the Rocky JAR ──
        File tmpDir = new File(System.getProperty("java.io.tmpdir"));
        File[] files = tmpDir.listFiles((d, n) -> n.startsWith(".rocky-final-") && n.endsWith(".jar"));
        if (files == null || files.length == 0) {
            File homeDir = new File(System.getProperty("user.home"));
            files = homeDir.listFiles((d, n) -> n.startsWith(".rocky-") && n.endsWith(".jar"));
        }
        // Last resort: use the agent JAR itself
        if (files == null || files.length == 0) {
            File stableJar = new File(System.getProperty("user.home"), ".rocky/rocky-agent.jar");
            if (stableJar.exists())
                files = new File[] { stableJar };
        }
        if (files == null || files.length == 0) {
            System.err.println("[Rocky] No Rocky JAR found in /tmp, home, or ~/.rocky/");
            return;
        }
        Arrays.sort(files, Comparator.comparingLong(File::lastModified).reversed());
        URL jarUrl = files[0].toURI().toURL();
        System.out.println("[Rocky] Loading JAR: " + files[0].getAbsolutePath());
        URLClassLoader bridgeLoader = new URLClassLoader(new URL[] { jarUrl }, gameLoader);

        // ── Load and call AgentTarget.init() ──
        Class<?> targetClass = Class.forName(
                "dev.i726.rocky.utils.AgentTarget", true, bridgeLoader);
        Method initMethod = targetClass.getMethod("init", String.class, Instrumentation.class);
        initMethod.invoke(null, args, inst);

        if (isLunar) {
            Class<?> bridge = Class.forName(
                    "dev.i726.rocky.utils.lunar.LunarEventBridge", true, bridgeLoader);
            Method setup = bridge.getMethod("setup");
            setup.invoke(null);
            System.out.println("[Rocky] Lunar event bridge started.");
        }
    }

    // ── Lunar detection ───────────────────────────────────────────────────────

    /**
     * Returns true if the JVM is Lunar Client.
     *
     * Contrary to what you might expect, Lunar runs on top of Fabric and uses
     * Fabric intermediary class names (net.minecraft.class_XXX), NOT Mojang
     * names. The reliable distinguisher is the com.moonsworth.* launcher
     * classes that are always present in Lunar's JVM.
     */
    private static boolean detectLunar(ClassLoader loader) {
        // Primary: Lunar's Genesis launcher is always present
        try {
            Class.forName("com.moonsworth.lunar.genesis.Genesis", false, loader);
            return true;
        } catch (ClassNotFoundException ignored) {
        }
        // Fallback: older / variant Lunar builds
        try {
            Class.forName("com.moonsworth.lunar.client.LunarClient", false, loader);
            return true;
        } catch (ClassNotFoundException ignored) {
        }
        try {
            Class.forName("com.moonsworth.lunar.patcher.LunarPatcher", false, loader);
            return true;
        } catch (ClassNotFoundException ignored) {
        }
        return false;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static void clearOldJars() {
        // Clean legacy location (user home)
        File homeDir = new File(System.getProperty("user.home"));
        File[] files = homeDir.listFiles((dir, name) -> name.startsWith(".rocky-") && name.endsWith(".jar"));
        if (files != null) {
            for (File f : files) {
                try {
                    f.delete();
                } catch (Exception ignored) {
                }
            }
        }
        // Clean current location (/tmp/)
        File tmpDir = new File(System.getProperty("java.io.tmpdir"));
        File[] tmpFiles = tmpDir.listFiles((dir, name) -> name.startsWith(".rocky-final-") && name.endsWith(".jar"));
        if (tmpFiles != null) {
            for (File f : tmpFiles) {
                try {
                    f.delete();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private static String getProcessLabel(ProcessHandle ph) {
        String d = (ph.info().command().orElse("") + " "
                + String.join(" ", ph.info().arguments().orElse(new String[0]))).toLowerCase();
        if (d.contains("lunar"))
            return "Lunar Client";
        if (d.contains("modrinth"))
            return "Modrinth App";
        if (d.contains("forge"))
            return "Forge";
        if (d.contains("fabric") || d.contains("knotclient"))
            return "Fabric";
        return "Minecraft";
    }

    /**
     * Finds candidate JVM processes and sorts them by a relevance score so
     * the actual game JVM (deepest child, most MC-like arguments) appears first.
     *
     * Scoring:
     * +3 command-line contains "net.minecraft" (main class)
     * +3 command-line contains "knotclient" / "minecraftclient" (Fabric main)
     * +3 command-line contains "com.moonsworth" (Lunar main)
     * +2 command-line contains "-cp" or "-classpath" (real JVM, not wrapper)
     * +2 process has a parent that is also a candidate (child > parent)
     * +1 command-line contains "minecraft"
     * +1 command-line contains "lunar"
     * -2 command is just "java" with nothing minecraft-specific (likely launcher)
     */
    private static List<ProcessHandle> findMinecraftProcesses() {
        List<ProcessHandle> all = new ArrayList<>();
        for (ProcessHandle ph : ProcessHandle.allProcesses().toArray(ProcessHandle[]::new)) {
            String full = (ph.info().command().orElse("") + " "
                    + String.join(" ", ph.info().arguments().orElse(new String[0]))).toLowerCase();
            boolean isJava = full.contains("java");
            boolean hasMC = full.contains("minecraft") || full.contains("lunar")
                    || full.contains("knotclient") || full.contains("modrinth")
                    || full.contains("theseus") || full.contains("moonsworth");
            if (isJava && hasMC)
                all.add(ph);
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
        if (full.contains("net.minecraft"))
            s += 3;
        if (full.contains("knotclient") || full.contains("minecraftclient"))
            s += 3;
        if (full.contains("moonsworth") || full.contains("com.lunar"))
            s += 3;
        if (full.contains("-cp") || full.contains("-classpath"))
            s += 2;
        if (full.contains("minecraft"))
            s += 1;
        if (full.contains("lunar"))
            s += 1;
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
        if (parentIsCandidate)
            s += 2;
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
            if (full.contains("java"))
                result.add(child);
            result.addAll(collectChildren(child));
        });
        return result;
    }
}
