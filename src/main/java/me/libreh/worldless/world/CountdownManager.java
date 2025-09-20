package me.libreh.worldless.world;

import me.libreh.worldless.config.ConfigManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

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
        if (!ConfigManager.getInstance().getConfig().restartMessage) {
            return;
        }
        Style style = Style.EMPTY.withBold(true).withFormatting(Formatting.GREEN);
        broadcastTimer(Text.literal("Restarting...").setStyle(style), false);
    }

    private void updateTimerDisplay() {
        int totalSeconds = worldTimer / TICKS_PER_SECOND;
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        String timeString = String.format("%02d:%02d", minutes, seconds);
        Formatting formatting = minutes > 0 ? Formatting.GRAY : (seconds > COUNTDOWN_SOUND_THRESHOLD ? Formatting.RED : Formatting.DARK_RED);
        Text timerText = Text.literal(timeString).formatted(formatting);
        broadcastTimer(timerText, true);
        playCountdownSounds(minutes, seconds);
    }

    private void broadcastTimer(Text timerText, boolean overlay) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            player.sendMessage(timerText, overlay);
        }
    }

    private void playCountdownSounds(int minutes, int seconds) {
        if (!ConfigManager.getInstance().getConfig().countdownSounds || minutes != 0 || seconds > COUNTDOWN_SOUND_THRESHOLD) {
            return;
        }
        final float basePitch = 2.0f;
        final float pitchDecrement = 0.2f;
        float pitch = basePitch - (seconds * pitchDecrement);
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            player.playSoundToPlayer(SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), SoundCategory.RECORDS, 1.0f, pitch);
        }
    }
}