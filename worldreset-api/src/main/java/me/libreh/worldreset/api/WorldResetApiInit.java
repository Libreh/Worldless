package me.libreh.worldreset.api;

import net.fabricmc.api.ModInitializer;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.TicketType;

// The api registers its own ticket type so consumers can use WorldPreloader without the
// worldreset mod installed. Loader dedupes jar-in-jar copies by mod id, so this runs once.
public final class WorldResetApiInit implements ModInitializer {
    @Override
    public void onInitialize() {
        WorldPreloader.ASYNC_CHUNK_TICKET = Registry.register(
            BuiltInRegistries.TICKET_TYPE,
            "worldreset:async_chunk",
            new TicketType(0L, TicketType.FLAG_LOADING)
        );
    }
}
