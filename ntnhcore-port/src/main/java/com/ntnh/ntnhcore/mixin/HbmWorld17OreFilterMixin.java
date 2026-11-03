package com.ntnh.ntnhcore.mixin;
import com.ntnh.ntnhcore.LegacyHbmOreCleanup;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(targets="com.ntmspace.world.World17",remap=false)
public abstract class HbmWorld17OreFilterMixin {
    @Shadow @Final private WorldGenLevel level;
    @ModifyVariable(method="set",at=@At("HEAD"),argsOnly=true,remap=false)
    private BlockState ntnh$sourceOreRule(BlockState state){return LegacyHbmOreCleanup.nativeState(level,state);}
}
