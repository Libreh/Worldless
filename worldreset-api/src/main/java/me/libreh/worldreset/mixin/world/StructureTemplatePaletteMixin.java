package me.libreh.worldreset.mixin.world;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Map;
import java.util.function.Function;

@Mixin(StructureTemplate.Palette.class)
public class StructureTemplatePaletteMixin {
    @WrapOperation(
        method = "blocks(Lnet/minecraft/world/level/block/Block;)Ljava/util/List;",
        at = @At(value = "INVOKE", target = "Ljava/util/Map;computeIfAbsent(Ljava/lang/Object;Ljava/util/function/Function;)Ljava/lang/Object;")
    )
    private Object worldreset$synchronizedComputeIfAbsent(
        Map<Object, Object> map, Object key, Function<Object, Object> mappingFunction, Operation<Object> original
    ) {
        synchronized (map) {
            return original.call(map, key, mappingFunction);
        }
    }
}
