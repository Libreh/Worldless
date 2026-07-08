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

import java.util.Optional;

public final class AdvancementPredicate extends AbstractPredicate {
    public static final Identifier ID = Identifier.parse("worldreset:advancement");

    public static final MapCodec<AdvancementPredicate> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Identifier.CODEC.fieldOf("advancement").forGetter(AdvancementPredicate::advancement),
            Codec.BOOL.optionalFieldOf("require_all_players", false).forGetter(AdvancementPredicate::requireAllPlayers),
            PredicateRegistry.CODEC.optionalFieldOf("filter").forGetter(AdvancementPredicate::filter)
    ).apply(instance, AdvancementPredicate::new));

    private final Identifier advancement;
    private final boolean requireAllPlayers;
    private final Optional<MinecraftPredicate> filter;

    public AdvancementPredicate(Identifier advancement, boolean requireAllPlayers, Optional<MinecraftPredicate> filter) {
        super(ID, CODEC);
        this.advancement = advancement;
        this.requireAllPlayers = requireAllPlayers;
        this.filter = filter;
    }

    public Identifier advancement() {
        return advancement;
    }

    public boolean requireAllPlayers() {
        return requireAllPlayers;
    }

    public Optional<MinecraftPredicate> filter() {
        return filter;
    }

    @Override
    public PredicateResult<?> test(PredicateContext context) {
        return filter.isEmpty() ? PredicateResult.ofSuccess() : filter.get().test(context);
    }
}
