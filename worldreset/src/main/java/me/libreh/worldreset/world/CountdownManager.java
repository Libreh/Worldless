package me.libreh.worldreset.world;

import me.libreh.worldreset.config.ConfigManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.function.BooleanSupplier;

public class CountdownManager {
    private static final int TICKS_PER_SECOND = 20;
    private static final int SOUND_THRESHOLD_SECONDS = 10;

    private final MinecraftServer server;
    private final TriggerTracker triggers;
    private final BooleanSupplier isResetting;

    private boolean active;
    private int durationTicks;
    private int endTick;
    private int lastDisplayedSecond;

    public CountdownManager(MinecraftServer server, TriggerTracker triggers, BooleanSupplier isResetting) {
        this.server = server;
        this.triggers = triggers;
        this.isResetting = isResetting;
    }

    public void startCountdown(long seconds) {
        this.durationTicks = (int) (seconds * TICKS_PER_SECOND);
        triggers.reset();
        continueCountdown();
    }

    public void continueCountdown() {
        this.endTick = server.getTickCount() + durationTicks;
        this.lastDisplayedSecond = -1;
        this.active = true;
    }

    public void stopCountdown() {
        this.active = false;
    }

    public boolean isCountdownActive() {
        return active && !isResetting.getAsBoolean();
    }

    public void tick() {
        int remaining = endTick - server.getTickCount();
        if (remaining <= 0) {
            active = false;
            return;
        }
        // Ceil so the display starts at the full duration and shows 1 during the final second.
        int secondsLeft = (remaining + TICKS_PER_SECOND - 1) / TICKS_PER_SECOND;
        if (secondsLeft != lastDisplayedSecond) {
            lastDisplayedSecond = secondsLeft;
            updateTimerDisplay(secondsLeft);
            playCountdownSounds(secondsLeft);
        }
    }

    public void broadcastRestart() {
        if (!ConfigManager.config().restartMessage) {
            return;
        }
        Style style = Style.EMPTY.withBold(true).withColor(ChatFormatting.GREEN);
        broadcastTimer(Component.literal("Resetting...").setStyle(style), false);
    }

    private void updateTimerDisplay(int secondsLeft) {
        int minutes = secondsLeft / 60;
        int seconds = secondsLeft % 60;
        String timeString = String.format("%02d:%02d", minutes, seconds);
        ChatFormatting formatting = minutes > 0 ? ChatFormatting.GRAY : (seconds > SOUND_THRESHOLD_SECONDS ? ChatFormatting.RED : ChatFormatting.DARK_RED);
        broadcastTimer(Component.literal(timeString).withStyle(formatting), true);
    }

    private void broadcastTimer(Component timerComponent, boolean overlay) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.sendSystemMessage(timerComponent, overlay);
        }
    }

    private void playCountdownSounds(int secondsLeft) {
        if (!ConfigManager.config().countdownSounds || secondsLeft > SOUND_THRESHOLD_SECONDS) {
            return;
        }
        final float basePitch = 2.0f;
        final float pitchDecrement = 0.2f;
        float pitch = basePitch - (secondsLeft * pitchDecrement);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.connection.send(new ClientboundSoundPacket(SoundEvents.NOTE_BLOCK_PLING, SoundSource.RECORDS, player.getX(), player.getY(), player.getZ(), 1.0f, pitch, player.level().getRandom().nextLong()));
        }
    }
}
