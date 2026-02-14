package me.libreh.worldreset.world;

import me.libreh.worldreset.WorldReset;
import me.libreh.worldreset.mixin.world.MinecraftServerAccessor;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.apache.commons.io.IOUtils;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class LobbyWorld {
    private static final String LOBBY_WORLD_RESOURCE = "/worldreset/lobby_world.zip";
    private static final String LOBBY_PATH_PREFIX = "dimensions/worldreset/lobby/";
    private static final String[] LOBBY_DIRECTORIES = {"entities/", "region/"};

    public void prepareLobbyFiles(MinecraftServer server) {
        long startTime = System.currentTimeMillis();
        WorldReset.LOGGER.debug("Preparing the WorldReset lobby...");

        try {
            Identifier lobbyWorldId = Identifier.fromNamespaceAndPath("worldreset", "lobby");
            Path storageDir = getLevelSaveDir(server, lobbyWorldId);

            if (storageDir == null) {
                WorldReset.LOGGER.error("Unable to find world storage dir for lobby");
                return;
            }

            createStorageDirectory(storageDir);
            extractLobbyFiles(storageDir);
        } catch (Exception e) {
            WorldReset.LOGGER.error("Error preparing lobby files", e);
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            WorldReset.LOGGER.debug("Lobby preparation completed in {}ms", duration);
        }
    }

    private void createStorageDirectory(Path storageDir) throws IOException {
        Files.createDirectories(storageDir);
    }

    private void extractLobbyFiles(Path storageDir) throws IOException {
        var lobbyZipStream = getClass().getResourceAsStream(LOBBY_WORLD_RESOURCE);
        if (lobbyZipStream == null) {
            throw new IOException("Could not find lobby_world.zip in resources");
        }

        try (ZipInputStream zipStream = new ZipInputStream(lobbyZipStream)) {
            ZipEntry entry;
            while ((entry = zipStream.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }

                String entryName = extractEntryName(entry.getName());
                if (isLobbyFile(entryName)) {
                    extractFile(zipStream, storageDir, entryName);
                }

                zipStream.closeEntry();
            }
        }
    }

    private String extractEntryName(String fullName) {
        if (fullName.startsWith(LOBBY_PATH_PREFIX)) {
            return fullName.substring(LOBBY_PATH_PREFIX.length());
        }
        return fullName;
    }

    private boolean isLobbyFile(String entryName) {
        for (String dir : LOBBY_DIRECTORIES) {
            if (entryName.startsWith(dir)) {
                return true;
            }
        }
        return false;
    }

    private void extractFile(ZipInputStream zipStream, Path storageDir, String entryName) {
        try {
            Path outPath = storageDir.resolve(entryName);
            if (!outPath.startsWith(storageDir)) {
                WorldReset.LOGGER.error("Attempted to create file outside world location: {}", outPath);
                return;
            }

            Files.createDirectories(outPath.getParent());
            WorldReset.LOGGER.debug("Copying file -> {}", outPath);
            Files.copy(zipStream, outPath, StandardCopyOption.REPLACE_EXISTING);

        } catch (IOException e) {
            WorldReset.LOGGER.error("Error copying lobby file {}", entryName, e);
        }
    }

    private Path getLevelSaveDir(MinecraftServer server, Identifier worldId) {
        for (ServerLevel world : server.getAllLevels()) {
            if (world.dimension().identifier().equals(worldId)) {
                return ((MinecraftServerAccessor) server).getStorageSource().getDimensionPath(world.dimension());
            }
        }
        return null;
    }

    public void copyDataPack(Path toPath) throws IOException {
        Files.createDirectories(toPath.getParent());
        Files.deleteIfExists(toPath);
        Files.createFile(toPath);

        int copiedFiles = 0;
        try (ZipOutputStream zipOutput = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(toPath)));
             ZipInputStream zipStream = openLobbyZip()) {

            ZipEntry entry;
            while ((entry = zipStream.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }

                String entryName = entry.getName();
                String entryPrefix = "datapacks/worldreset/";

                if (entryName.startsWith(entryPrefix)) {
                    String outputName = entryName.substring(entryPrefix.length());
                    WorldReset.LOGGER.info("Copying {} -> {}", outputName, toPath.getFileName());
                    zipOutput.putNextEntry(new ZipEntry(outputName));
                    IOUtils.copy(zipStream, zipOutput);
                    zipOutput.closeEntry();
                    copiedFiles++;
                }

                zipStream.closeEntry();
            }
        }

        WorldReset.LOGGER.debug("Copied {} files to datapack", copiedFiles);
    }

    private ZipInputStream openLobbyZip() throws IOException {
        InputStream lobbyZipStream = getClass().getResourceAsStream(LOBBY_WORLD_RESOURCE);
        if (lobbyZipStream == null) {
            throw new IOException("Could not find lobby_world.zip in resources");
        }
        return new ZipInputStream(new BufferedInputStream(lobbyZipStream));
    }
}