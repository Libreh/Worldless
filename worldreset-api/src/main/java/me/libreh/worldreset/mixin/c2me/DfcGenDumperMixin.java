package me.libreh.worldreset.mixin.c2me;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.nio.file.Path;

// C2ME's density function compiler runs once per world creation (per RandomState), and on every run
// GenDumper synchronously writes the generated .class to ./cache/c2me-dfc and builds + writes a
// GraphViz .dot dump of every root density function. That is debug tooling nobody reads in
// production, and it adds disk IO and graph-building to each "worlds created". Skip both; the actual
// compiled class is still defined in memory and used normally.
@Mixin(targets = "com.ishland.c2me.opts.dfc.common.gen.GenDumper", remap = false)
public class DfcGenDumperMixin {
    @Inject(method = "dumpClass", at = @At("HEAD"), cancellable = true, require = 0)
    private static void worldreset$skipClassDump(CallbackInfoReturnable<Path> cir) {
        cir.setReturnValue(null);
    }

    @Inject(method = "dumpDot", at = @At("HEAD"), cancellable = true, require = 0)
    private static void worldreset$skipDotDump(CallbackInfo ci) {
        ci.cancel();
    }
}
