package com.ntnh.ntnhcore.mixin;
import com.ntnh.ntnhcore.LegacyHbmOreCleanup;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets={"com.ntmspace.world.MunChunkGenerator","com.ntmspace.world.MinmusChunkGenerator",
    "com.ntmspace.world.DunaChunkGenerator","com.ntmspace.world.IkeChunkGenerator",
    "com.ntmspace.world.LaytheChunkGenerator","com.ntmspace.world.EveChunkGenerator",
    "com.ntmspace.world.DresChunkGenerator","com.ntmspace.world.MohoChunkGenerator",
    "com.ntmspace.world.TektoChunkGenerator"},remap=false)
public abstract class HbmPlanetOreOrderMixin {
    // Actual 1.20.1 Space JAR's inherited Minecraft method name; no guessed source-version mapping.
    @Inject(method="m_213609_",at=@At("RETURN"),remap=false)
    private void ntnh$afterNativePopulation(WorldGenLevel level,ChunkAccess chunk,StructureManager structures,CallbackInfo ci){
        LegacyHbmOreCleanup.afterPlanet(level,chunk);
    }
}
