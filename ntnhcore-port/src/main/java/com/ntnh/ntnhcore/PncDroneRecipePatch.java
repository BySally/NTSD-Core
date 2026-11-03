package com.ntnh.ntnhcore;

import com.google.gson.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModList;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/** Lookup stays in Core's module, not RecipeManager's module after Mixin merging. */
public final class PncDroneRecipePatch {
    private PncDroneRecipePatch(){}
    public static Map<ResourceLocation,JsonElement> repair(Map<ResourceLocation,JsonElement> input){
        if(!ModList.get().isLoaded("pneumaticcraft")||!ModList.get().isLoaded("computercraft"))return input;
        String version=ModList.get().getModContainerById("pneumaticcraft").orElseThrow().getModInfo().getVersion().toString();
        if(!(version.equals("6.0.23")||version.startsWith("6.0.23+")||version.startsWith("6.0.23-")))return input;
        ResourceLocation id=new ResourceLocation("pneumaticcraft","drone_interface");
        JsonElement current=input.get(id);if(current==null)return input;
        try(var stream=PncDroneRecipePatch.class.getResourceAsStream("/data/pneumaticcraft/recipes/drone_interface.json")){
            if(stream==null)throw new IllegalStateException("Missing PNC recipe correction resource");
            JsonObject fixed=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject broken=fixed.deepCopy();
            broken.getAsJsonArray("recipes").get(0).getAsJsonObject().getAsJsonObject("recipe").getAsJsonObject("key").add("M",new JsonArray());
            if(!current.equals(broken))return input;
            Map<ResourceLocation,JsonElement> corrected=new HashMap<>(input);corrected.put(id,fixed);
            org.slf4j.LoggerFactory.getLogger("ntnhcore").info("NTSD repaired exact malformed PNC 6.0.23 drone_interface JSON before recipe parsing (saved datapack order independent)");
            return corrected;
        }catch(java.io.IOException error){throw new IllegalStateException("Cannot read PNC recipe correction",error);}
    }
}
