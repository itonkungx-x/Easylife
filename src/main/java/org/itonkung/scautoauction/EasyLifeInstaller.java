package org.itonkung.scautoauction;

import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.TimeUnit;

/**
 * Standalone helper JAR, spawned during Minecraft JVM shutdown.
 * No Minecraft/Fabric dependencies. Waits for old JAR locks to be released
 * (especially on Windows) and replaces the original JAR in-place.
 */
public final class EasyLifeInstaller {
    private EasyLifeInstaller() {}

    public static void main(String[] args) {
        if (args.length != 3) return;
        try {
            long parentPid = Long.parseLong(args[0]);
            Path staged = Path.of(args[1]).toAbsolutePath().normalize();
            Path target = Path.of(args[2]).toAbsolutePath().normalize();
            Path folder = target.getParent();

            if (parentPid < 1 || !staged.getFileName().toString().equals("pending.jar")
                || !target.getFileName().toString().endsWith(".jar")
                || !folder.getFileName().toString().equalsIgnoreCase("mods")
                || !Files.isRegularFile(staged)
                || !Files.isRegularFile(target)) return;

            var parent = ProcessHandle.of(parentPid);
            if (parent.isPresent()) {
                try {
                    parent.get().onExit().get(100, TimeUnit.SECONDS);
                } catch (java.util.concurrent.TimeoutException timeout) {
                    System.err.println("Minecraft has not exited; preserving pending update.");
                    return;
                }
            }
            Thread.sleep(1200L);
            Path backup = target.resolveSibling(target.getFileName().toString() + ".backup");
            Files.copy(target, backup, StandardCopyOption.REPLACE_EXISTING);
            try {
                try {
                    Files.move(staged, target, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException ignored) {
                    Files.move(staged, target, StandardCopyOption.REPLACE_EXISTING);
                }
                System.out.println("Easylife in Siamcraft update installed: " + target);
                Files.deleteIfExists(backup);
            } catch (Exception failure) {
                if (!Files.exists(target)) Files.copy(backup, target, StandardCopyOption.REPLACE_EXISTING);
                throw failure;
            }
        } catch (Exception error) {
            error.printStackTrace();
        }
    }
}
