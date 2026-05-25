package dev.i726.rocky.module.modules.misc;

import dev.i726.rocky.event.events.PacketReceiveListener;
import dev.i726.rocky.event.events.PacketSendListener;
import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.module.setting.BooleanSetting;
import dev.i726.rocky.module.setting.NumberSetting;
import dev.i726.rocky.module.setting.StringSetting;
import dev.i726.rocky.utils.EncryptedString;
import net.minecraft.util.Util;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Logs incoming (S2C) and/or outgoing (C2S) packets to
 * .minecraft/rocky/packet_log.txt for debugging and analysis.
 *
 * Writing is off-thread so it never stalls the game loop.
 */
public final class PacketLogger extends Module implements PacketSendListener, PacketReceiveListener {

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private final BooleanSetting logOutgoing = new BooleanSetting(
            EncryptedString.of("Log Outgoing"), true)
            .setDescription(EncryptedString.of("Log C2S packets sent to the server"));

    private final BooleanSetting logIncoming = new BooleanSetting(
            EncryptedString.of("Log Incoming"), false)
            .setDescription(EncryptedString.of("Log S2C packets received from the server"));

    private final StringSetting filter = new StringSetting(
            EncryptedString.of("Filter"), "")
            .setDescription(EncryptedString.of("Only log packets whose class name contains this text (empty = all)"));

    private final NumberSetting maxLines = new NumberSetting(
            EncryptedString.of("Max Lines"), 100, 100000, 10000, 500)
            .setDescription(EncryptedString.of("Clear the log file when it exceeds this many lines"));

    private final BooleanSetting clearOnEnable = new BooleanSetting(
            EncryptedString.of("Clear On Enable"), true)
            .setDescription(EncryptedString.of("Wipe the log file each time the module is enabled"));

    private ExecutorService writer;
    private Path logFile;
    private volatile int lineCount = 0;

    public PacketLogger() {
        super(EncryptedString.of("Packet Logger"),
                EncryptedString.of("Logs packets to .minecraft/rocky/packet_log.txt"),
                -1, CategoryManager.NETWORK);
        addSettings(logOutgoing, logIncoming, filter, maxLines, clearOnEnable);
    }

    @Override
    public void onEnable() {
        try {
            Path dir = Util.getGameDir().toPath().resolve("rocky");
            Files.createDirectories(dir);
            logFile = dir.resolve("packet_log.txt");

            if (clearOnEnable.getValue()) {
                Files.writeString(logFile, "", StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                lineCount = 0;
            } else {
                lineCount = countLines(logFile);
            }
        } catch (IOException e) {
            logFile = null;
        }

        writer = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "PacketLogger-Writer");
            t.setDaemon(true);
            return t;
        });

        eventManager.add(PacketSendListener.class, this);
        eventManager.add(PacketReceiveListener.class, this);
        super.onEnable();
    }

    @Override
    public void onDisable() {
        eventManager.remove(PacketSendListener.class, this);
        eventManager.remove(PacketReceiveListener.class, this);
        if (writer != null) {
            writer.shutdown();
            writer = null;
        }
        super.onDisable();
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (!logOutgoing.getValue()) return;
        log("OUT", event.packet.getClass().getSimpleName());
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (!logIncoming.getValue()) return;
        log("IN ", event.packet.getClass().getSimpleName());
    }

    private void log(String direction, String packetName) {
        String f = filter.getValue().trim();
        if (!f.isEmpty() && !packetName.toLowerCase().contains(f.toLowerCase())) return;

        String line = "[" + LocalDateTime.now().format(FMT) + "] " + direction + " " + packetName;

        if (writer == null || writer.isShutdown() || logFile == null) return;
        writer.execute(() -> {
            try {
                if (lineCount >= (int) maxLines.getValue()) {
                    Files.writeString(logFile, "", StandardOpenOption.TRUNCATE_EXISTING);
                    lineCount = 0;
                }
                Files.writeString(logFile, line + System.lineSeparator(),
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                lineCount++;
            } catch (IOException ignored) {}
        });
    }

    private static int countLines(Path path) {
        if (!Files.exists(path)) return 0;
        try (var lines = Files.lines(path)) {
            return (int) lines.count();
        } catch (IOException e) {
            return 0;
        }
    }
}
