package me.libreh.worldless.world;

import me.libreh.worldless.Worldless;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.World;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class LobbyWorldService {
    private static final String LOBBY_WORLD_ZIP_PATH = "/worldless/lobby_world.zip";
//    private final Path worldDir;

    public LobbyWorldService() {
//        this.worldDir = FabricLoader.getInstance().getGameDir().resolve("world");
    }

//    public void unzipLobbyWorld() {
//        try (InputStream is = World.class.getResourceAsStream(LOBBY_WORLD_ZIP_PATH)) {
//            try (ZipInputStream zis = new ZipInputStream(is)) {
//                ZipEntry entry;
//                while ((entry = zis.getNextEntry()) != null) {
//                    Path targetPath = worldDir.resolve(entry.getName()).normalize();
//                    if (!targetPath.startsWith(worldDir)) {
//                        throw new IOException("Invalid zip entry: " + entry.getName());
//                    }
//                    if (entry.isDirectory()) {
//                        Files.createDirectories(targetPath);
//                    } else {
//                        Files.createDirectories(targetPath.getParent());
//                        Files.copy(zis, targetPath, StandardCopyOption.REPLACE_EXISTING);
//                    }
//                }
//            }
//        } catch (IOException e) {
//            Worldless.LOGGER.error("Failed to unzip lobby world", e);
//        }
//    }

    public void unzipLobbyWorld() {
        try (ZipInputStream is = new ZipInputStream(World.class.getResourceAsStream(LOBBY_WORLD_ZIP_PATH))) {
            byte[] buffer = new byte[1024];
            ZipEntry entry;

            while ((entry = is.getNextEntry()) != null) {
                File targetFile = FabricLoader.getInstance()
                        .getGameDir()
                        .resolve("world")
                        .resolve(entry.getName())
                        .toFile();

                if (entry.isDirectory()) {
                    targetFile.mkdirs();
                } else {
                    ensureParentDirectoryExists(targetFile);
                    writeZipEntryToFile(is, buffer, targetFile);
                }
            }
        } catch (IOException e) {
            Worldless.LOGGER.error("Failed to unzip lobby world", e);
        }
    }

    private void ensureParentDirectoryExists(File file) {
        File parent = file.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
    }

    private void writeZipEntryToFile(ZipInputStream zis, byte[] buffer, File targetFile) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(targetFile)) {
            int length;
            while ((length = zis.read(buffer)) > 0) {
                fos.write(buffer, 0, length);
            }
        }
    }
} 