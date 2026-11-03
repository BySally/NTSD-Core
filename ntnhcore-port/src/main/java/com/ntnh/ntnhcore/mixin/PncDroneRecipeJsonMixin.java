package com.ntnh.ntnhcore.mixin;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraftforge.fml.ModList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/** Repair only the exact malformed upstream PNC 6.0.23 JSON, regardless of a saved pack order.
 * A valid custom recipe, a removed recipe and any other version remain untouched.
 * Corrected JSON retains its original PNC ID, conditions, shape, ingredients and output.
 */
@Mixin(value=RecipeManager.class,remap=false)
public abstract class PncDroneRecipeJsonMixin {
    @ModifyVariable(method="m_5787_(Ljava/util/Map;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V",at=@At("HEAD"),argsOnly=true,ordinal=0,remap=false)
    private Map<ResourceLocation,JsonElement> ntsd$repairBrokenDroneJson(Map<ResourceLocation,JsonElement> input){
        return com.ntnh.ntnhcore.PncDroneRecipePatch.repair(input);
    }
}
