package me.libreh.worldreset.predicate;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import eu.pb4.predicate.api.AbstractPredicate;
import eu.pb4.predicate.api.MinecraftPredicate;
import eu.pb4.predicate.api.PredicateContext;
import eu.pb4.predicate.api.PredicateResult;
import eu.pb4.predicate.api.PredicateRegistry;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;

import java.util.Optional;

public final class EntityDeathPredicate extends AbstractPredicate implements Trigger {
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

    @Override
    public TriggerState newState() {
        return new DeathState();
    }

    @Override
    public String describe() {
        return "death " + entity;
    }

    private final class DeathState implements TriggerState {
        private boolean triggered;

        @Override
        public boolean noteDeath(Identifier entityId, Entity deadEntity) {
            if (triggered) return false;
            if (entity.equals(entityId) && test(PredicateContext.of(deadEntity)).success()) {
                triggered = true;
                return true;
            }
            return false;
        }

        @Override
        public boolean isSatisfied(int playerCount) {
            return triggered;
        }
    }
}
