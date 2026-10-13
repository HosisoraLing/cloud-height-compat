// SPDX-License-Identifier: AGPL-3.0-only
package dev.cloudheight.mixin;

import com.google.common.collect.ImmutableList;
import dev.cloudheight.BslPatch;
import java.util.List;
import net.irisshaders.iris.shaderpack.include.AbsolutePackPath;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.irisshaders.iris.shaderpack.include.IncludeProcessor", remap = false)
public abstract class IrisIncludeMixin {
    @Inject(method = "getIncludedFile", at = @At("RETURN"), cancellable = true, require = 1)
    private void shcc$patchBsl(AbsolutePackPath path, CallbackInfoReturnable<ImmutableList<String>> cir) {
        ImmutableList<String> original = cir.getReturnValue();
        List<String> patched = BslPatch.patch(path.getPathString(), original);
        if (patched != original) cir.setReturnValue(ImmutableList.copyOf(patched));
    }
}
