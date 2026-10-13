// SPDX-License-Identifier: AGPL-3.0-only
package dev.cloudheight.mixin;

import dev.cloudheight.CloudHeightCompat;
import java.util.function.DoubleSupplier;
import net.irisshaders.iris.gl.uniform.UniformHolder;
import net.irisshaders.iris.gl.uniform.UniformUpdateFrequency;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.irisshaders.iris.uniforms.IrisExclusiveUniforms$WorldInfoUniforms", remap = false)
public abstract class IrisWorldInfoMixin {
    @Redirect(method = "addWorldInfoUniforms", at = @At(value = "INVOKE", target =
            "Lnet/irisshaders/iris/gl/uniform/UniformHolder;uniform1f(Lnet/irisshaders/iris/gl/uniform/UniformUpdateFrequency;Ljava/lang/String;Ljava/util/function/DoubleSupplier;)Lnet/irisshaders/iris/gl/uniform/UniformHolder;"), require = 1)
    private static UniformHolder shcc$cloudHeight(UniformHolder holder, UniformUpdateFrequency frequency,
                                                 String name, DoubleSupplier value) {
        return holder.uniform1f(frequency, name, name.equals("cloudHeight")
                ? () -> CloudHeightCompat.adjust(value.getAsDouble()) : value);
    }

    @Inject(method = "addWorldInfoUniforms", at = @At("TAIL"), require = 1)
    private static void shcc$registerBslUniform(UniformHolder uniforms, CallbackInfo ci) {
        uniforms.uniform3f(UniformUpdateFrequency.PER_FRAME, "shccCloudAdjustment", CloudHeightCompat::parameters);
    }
}
