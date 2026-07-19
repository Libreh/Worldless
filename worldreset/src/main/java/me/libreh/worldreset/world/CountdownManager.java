package me.libreh.worldreset.world;

import me.libreh.worldreset.WorldReset;
import me.libreh.worldreset.config.Config;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.function.Supplier;

public class CountdownManager {
    private static final int TICKS_PER_SECOND = 20;
    private static final int COUNTDOWN_SOUND_THRESHOLD = 10;
    private static final int TICKS_OFFSET = 39;

    private final MinecraftServer server;
    private final TriggerTracker triggers;
    private final Supplier<Config> config;
    private boolean isCountdownActive;
    private int ticks;
    private int remainingTicks;
    private int initialTicks;

    public CountdownManager(MinecraftServer server, TriggerTracker triggers, Supplier<Config> config) {
        this.server = server;
        this.triggers = triggers;
        this.config = config;
    }

    public void startCountdown(long seconds) {
        this.initialTicks = (int) (seconds * TICKS_PER_SECOND) + TICKS_OFFSET;
        triggers.reset();
        continueCountdown();
    }

    public void continueCountdown() {
        this.remainingTicks = initialTicks;
        this.isCountdownActive = true;
    }

    public void stopCountdown() {
        this.isCountdownActive = false;
    }

    public boolean isCountdownActive() {
        return isCountdownActive && WorldReset.worlds(server).state() == WorldState.LOADED;
    }

    public void tick() {
        if (++ticks < TICKS_PER_SECOND - 1) return;
        ticks = 0;
        remainingTicks -= TICKS_PER_SECOND;
        if (remainingTicks <= 0) {
            isCountdownActive = false;
            return;
        }
        updateTimerDisplay();
    }

    public void broadcastRestart() {
        if (!config.get().restartMessage) {
            return;
        }
        Style style = Style.EMPTY.withBold(true).withColor(ChatFormatting.GREEN);
        broadcastTimer(Component.literal("Resetting...").setStyle(style), false);
    }

    private void updateTimerDisplay() {
        int totalSeconds = remainingTicks / TICKS_PER_SECOND;
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
        if (!config.get().countdownSounds || minutes != 0 || seconds > COUNTDOWN_SOUND_THRESHOLD) {
            return;
        }
        final float basePitch = 2.0f;
        final float pitchDecrement = 0.2f;
        float pitch = basePitch - (seconds * pitchDecrement);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.connection.send(new ClientboundSoundPacket(SoundEvents.NOTE_BLOCK_PLING, SoundSource.RECORDS, player.getX(), player.getY(), player.getZ(), 1.0f, pitch, player.level().getRandom().nextLong()));
        }
    }
}