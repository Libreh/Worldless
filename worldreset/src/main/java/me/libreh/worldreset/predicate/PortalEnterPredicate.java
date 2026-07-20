package me.libreh.worldreset.predicate;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import eu.pb4.predicate.api.AbstractPredicate;
import eu.pb4.predicate.api.MinecraftPredicate;
import eu.pb4.predicate.api.PredicateContext;
import eu.pb4.predicate.api.PredicateResult;
import eu.pb4.predicate.api.PredicateRegistry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class PortalEnterPredicate extends AbstractPredicate implements Trigger {
    public static final Identifier ID = Identifier.parse("worldreset:portal_enter");

    public static final MapCodec<PortalEnterPredicate> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Identifier.CODEC.fieldOf("block").forGetter(PortalEnterPredicate::block),
            Codec.BOOL.optionalFieldOf("require_all_players", false).forGetter(PortalEnterPredicate::requireAllPlayers),
            Identifier.CODEC.optionalFieldOf("origin_dimension").forGetter(PortalEnterPredicate::originDimension),
            PredicateRegistry.CODEC.optionalFieldOf("filter").forGetter(PortalEnterPredicate::filter)
    ).apply(instance, PortalEnterPredicate::new));

    private final Identifier block;
    private final boolean requireAllPlayers;
    private final Optional<Identifier> originDimension;
    private final Optional<MinecraftPredicate> filter;

    public PortalEnterPredicate(Identifier block, boolean requireAllPlayers, Optional<Identifier> originDimension, Optional<MinecraftPredicate> filter) {
        super(ID, CODEC);
        this.block = block;
        this.requireAllPlayers = requireAllPlayers;
        this.originDimension = originDimension;
        this.filter = filter;
    }

    public Identifier block() {
        return block;
    }

    public boolean requireAllPlayers() {
        return requireAllPlayers;
    }

    public Optional<Identifier> originDimension() {
        return originDimension;
    }

    public Optional<MinecraftPredicate> filter() {
        return filter;
    }

    public boolean matchesDimension(ResourceKey<Level> actual) {
        return originDimension.isEmpty() || originDimension.get().equals(actual.identifier());
    }

    @Override
    public PredicateResult<?> test(PredicateContext context) {
        return filter.isEmpty() ? PredicateResult.ofSuccess() : filter.get().test(context);
    }

    @Override
    public TriggerState newState() {
        return new PortalState();
    }

    @Override
    public String describe() {
        String desc = "portal " + block;
        if (originDimension.isPresent()) desc += " from " + originDimension.get();
        desc += requireAllPlayers ? " (all players)" : " (any player)";
        return desc;
    }

    private final class PortalState implements TriggerState {
        private final Set<UUID> entered = new HashSet<>();

        @Override
        public boolean notePortal(ServerPlayer player, Identifier blockId, ResourceKey<Level> originDim) {
            if (block.equals(blockId) && matchesDimension(originDim) && test(PredicateContext.of(player)).success()) {
                entered.add(player.getUUID());
                return true;
            }
            return false;
        }

        @Override
        public boolean isSatisfied(int playerCount) {
            return requireAllPlayers
                ? (playerCount > 0 && entered.size() >= playerCount)
                : !entered.isEmpty();
        }
    }
}
