package me.libreh.worldreset.world;

import me.libreh.worldreset.config.ConfigManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

public class CountdownManager {
    private static final int TICKS_PER_SECOND = 20;
    private static final int COUNTDOWN_SOUND_THRESHOLD = 10;
    private static final int SECONDS_OFFSET = 39;

    private final MinecraftServer server;
    private boolean isCountdownActive;
    private int ticks;
    private int worldTimer;
    private int resetTimer;

    public CountdownManager(MinecraftServer server) {
        this.server = server;
    }

    public void startCountdown(long seconds) {
        this.resetTimer = (int) (seconds * TICKS_PER_SECOND) + SECONDS_OFFSET;
        continueCountdown();
    }

    public void continueCountdown() {
        this.worldTimer = resetTimer;
        this.isCountdownActive = true;
    }

    public void stopCountdown() {
        this.isCountdownActive = false;
    }

    public boolean isCountdownActive() {
        return isCountdownActive;
    }

    public void tick() {
        if (++ticks < TICKS_PER_SECOND - 1) return;
        ticks = 0;
        worldTimer -= TICKS_PER_SECOND;
        if (worldTimer <= 0) {
            isCountdownActive = false;
            return;
        }
        updateTimerDisplay();
    }

    public void broadcastRestart() {
        if (!ConfigManager.config().restartMessage) {
            return;
        }
        Style style = Style.EMPTY.withBold(true).withColor(ChatFormatting.GREEN);
        broadcastTimer(Component.literal("Resetting...").setStyle(style), false);
    }

    private void updateTimerDisplay() {
        int totalSeconds = worldTimer / TICKS_PER_SECOND;
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        String timeString = String.format("%02d:%02d", minutes, seconds);
        ChatFormatting formatting = minutes > 0 ? ChatFormatting.GRAY : (seconds > COUNTDOWN_SOUND_THRESHOLD ? ChatFormatting.RED : ChatFormatting.DARK_RED);
        Component timerComponent = Component.literal(timeString).withStyle(formatting);
        broadcastTimer(timerComponent, true);
        playCountdownSounds(minutes, seconds);
    }

    private void broadcastTimer(Component timerComponent, boolean overlay) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.sendSystemMessage(timerComponent, overlay);
        }
    }

    private void playCountdownSounds(int minutes, int seconds) {
        if (!ConfigManager.config().countdownSounds || minutes != 0 || seconds > COUNTDOWN_SOUND_THRESHOLD) {
            return;
        }
        final float basePitch = 2.0f;
        final float pitchDecrement = 0.2f;
        float pitch = basePitch - (seconds * pitchDecrement);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.playNotifySound(SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.RECORDS, 1.0f, pitch);
        }
    }
}