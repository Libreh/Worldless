package me.libreh.worldless.command.worldless;

import com.mojang.brigadier.context.CommandContext;
import me.libreh.worldless.command.BaseCommand;
import me.libreh.worldless.config.ConfigManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class ReloadCommand extends BaseCommand {
    private static final Text SUCCESS = Text.literal("Reloaded config!");
    private static final Text FAILURE = Text.literal("Error reloading config!")
            .formatted(Formatting.RED);

    public ReloadCommand() {
        super("reload");
    }

    @Override
    public int run(CommandContext<ServerCommandSource> context) {
        boolean ok = ConfigManager.getInstance().loadConfig();
        context.getSource().sendFeedback(() -> ok ? SUCCESS : FAILURE, false);
        return ok ? 1 : 0;
    }
}