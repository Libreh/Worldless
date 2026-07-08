package me.libreh.worldreset.mixin.world;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Mixin(StructureTemplate.Palette.class)
public class StructureTemplatePaletteMixin {
    @Redirect(
        method = "blocks(Lnet/minecraft/world/level/block/Block;)Ljava/util/List;",
        at = @At(value = "INVOKE", target = "Ljava/util/Map;computeIfAbsent(Ljava/lang/Object;Ljava/util/function/Function;)Ljava/lang/Object;")
    )
    private Object worldreset$synchronizedComputeIfAbsent(
        Map<Object, Object> map, Object key, Function<Object, Object> mappingFunction
    ) {
        synchronized (map) {
            return map.computeIfAbsent(key, mappingFunction);
        }
    }
}
