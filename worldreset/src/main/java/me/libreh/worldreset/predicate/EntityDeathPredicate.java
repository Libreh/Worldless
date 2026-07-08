package me.libreh.worldreset.predicate;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import eu.pb4.predicate.api.AbstractPredicate;
import eu.pb4.predicate.api.MinecraftPredicate;
import eu.pb4.predicate.api.PredicateContext;
import eu.pb4.predicate.api.PredicateResult;
import eu.pb4.predicate.api.PredicateRegistry;
import net.minecraft.resources.Identifier;

import java.util.Optional;

public final class EntityDeathPredicate extends AbstractPredicate {
    public static final Identifier ID = Identifier.parse("worldreset:entity_death");

    public static final MapCodec<EntityDeathPredicate> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Identifier.CODEC.fieldOf("entity").forGetter(EntityDeathPredicate::entity),
            PredicateRegistry.CODEC.optionalFieldOf("filter").forGetter(EntityDeathPredicate::filter)
    ).apply(instance, EntityDeathPredicate::new));

    private final Identifier entity;
    private final Optional<MinecraftPredicate> filter;

    public EntityDeathPredicate(Identifier entity, Optional<MinecraftPredicate> filter) {
        super(ID, CODEC);
        this.entity = entity;
        this.filter = filter;
    }

    public Identifier entity() {
        return entity;
    }

    public Optional<MinecraftPredicate> filter() {
        return filter;
    }

    @Override
    public PredicateResult<?> test(PredicateContext context) {
        return filter.isEmpty() ? PredicateResult.ofSuccess() : filter.get().test(context);
    }
}
