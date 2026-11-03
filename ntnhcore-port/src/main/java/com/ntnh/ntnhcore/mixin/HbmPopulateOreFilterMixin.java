package com.ntnh.ntnhcore.mixin;
import com.ntnh.ntnhcore.LegacyHbmOreCleanup;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(targets="com.ntmspace.world.populate.LevelPopulateWorld",remap=false)
public abstract class HbmPopulateOreFilterMixin {
    @Shadow @Final private WorldGenLevel level;
    @ModifyVariable(method="write",at=@At("HEAD"),argsOnly=true,remap=false)
    private BlockState ntnh$sourceOreRule(BlockState state){return LegacyHbmOreCleanup.nativeState(level,state);}
}
