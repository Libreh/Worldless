package me.libreh.worldreset.api;

import me.libreh.worldreset.mixin.world.MinecraftServerAccessor;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private static final Logger LOGGER = LoggerFactory.getLogger(LobbyWorld.class);
    private static final String[] LOBBY_DIRECTORIES = {"entities/", "region/"};

    public final Identifier id;
    private final String modId;
    private final String lobbyWorldResource;
    private final String lobbyPathPrefix;
    private final String datapackPrefix;

    public LobbyWorld(String modId) {
        this.modId = modId;
        this.id = Identifier.fromNamespaceAndPath(modId, "lobby");
        this.lobbyWorldResource = "/" + modId + "/lobby_world.zip";
        this.lobbyPathPrefix = "dimensions/" + modId + "/lobby/";
        this.datapackPrefix = "datapacks/" + modId + "/";
    }

    public void prepareLobbyFiles(MinecraftServer server) {
        long startTime = System.currentTimeMillis();
        LOGGER.debug("Preparing lobby for {}...", modId);

        try {
            Path storageDir = getLevelSaveDir(server, id);

            if (storageDir == null) {
                LOGGER.error("Unable to find world storage dir for lobby");
                return;
            }

            Files.createDirectories(storageDir);
            extractLobbyFiles(storageDir);
        } catch (Exception e) {
            LOGGER.error("Error preparing lobby files", e);
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            LOGGER.debug("Lobby preparation completed in {}ms", duration);
        }
    }

    private void extractLobbyFiles(Path storageDir) throws IOException {
        var lobbyZipStream = getClass().getResourceAsStream(lobbyWorldResource);
        if (lobbyZipStream == null) {
            throw new IOException("Could not find " + lobbyWorldResource + " in resources");
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
        if (fullName.startsWith(lobbyPathPrefix)) {
            return fullName.substring(lobbyPathPrefix.length());
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
                LOGGER.error("Attempted to create file outside world location: {}", outPath);
                return;
            }

            Files.createDirectories(outPath.getParent());
            LOGGER.debug("Copying file -> {}", outPath);
            Files.copy(zipStream, outPath, StandardCopyOption.REPLACE_EXISTING);

        } catch (IOException e) {
            LOGGER.error("Error copying lobby file {}", entryName, e);
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

                if (entryName.startsWith(datapackPrefix)) {
                    String outputName = entryName.substring(datapackPrefix.length());
                    LOGGER.debug("Copying {} -> {}", outputName, toPath.getFileName());
                    zipOutput.putNextEntry(new ZipEntry(outputName));
                    IOUtils.copy(zipStream, zipOutput);
                    zipOutput.closeEntry();
                    copiedFiles++;
                }

                zipStream.closeEntry();
            }
        }

        LOGGER.debug("Copied {} files to datapack", copiedFiles);
    }

    private ZipInputStream openLobbyZip() throws IOException {
        InputStream lobbyZipStream = getClass().getResourceAsStream(lobbyWorldResource);
        if (lobbyZipStream == null) {
            throw new IOException("Could not find " + lobbyWorldResource + " in resources");
        }
        return new ZipInputStream(new BufferedInputStream(lobbyZipStream));
    }
}
