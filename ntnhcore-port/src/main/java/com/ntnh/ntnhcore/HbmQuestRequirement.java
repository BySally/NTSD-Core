package com.ntnh.ntnhcore;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.world.item.ItemStack;
import java.util.concurrent.ConcurrentHashMap;

/** Inspect HBM's real fluid/charge state through its public item API, never cosmetic names. */
final class HbmQuestRequirement {
    private static final ConcurrentHashMap<String,Object> fluids=new ConcurrentHashMap<>();
    private static Object fluid(JsonObject req){
        return fluids.computeIfAbsent(req.toString(),key->{
            try{
                var spec=new JsonArray();spec.add("fluid_container");spec.add(req.get("item").getAsString());spec.add(1);spec.add(req.get("fluid").getAsString());spec.add(req.get("amount").getAsInt());
                return Class.forName("com.hbm.inventory.recipes.CustomMachineRecipes").getMethod("readAStack",JsonArray.class).invoke(null,spec);
            }catch(ReflectiveOperationException error){throw new IllegalStateException("Cannot resolve HBM quest fluid "+req,error);}
        });
    }
    static boolean matches(JsonObject req,ItemStack stack){
        try{
            if(req.has("cell_fluid"))return (boolean)Class.forName("com.hbm.items.special.ItemCell")
                .getMethod("isFullCell",ItemStack.class,Class.forName("com.hbm.inventory.fluid.FluidType")).invoke(null,stack,cellFluid(req));
            if(req.has("fluid"))return (boolean)Class.forName("com.hbm.inventory.RecipesCommon$AStack").getMethod("matchesRecipe",ItemStack.class,boolean.class).invoke(fluid(req),stack,true);
            if(req.has("charge"))return ((Number)stack.getItem().getClass().getMethod("getCharge",ItemStack.class).invoke(stack.getItem(),stack)).longValue()==req.get("charge").getAsLong();
            return true;
        }catch(ReflectiveOperationException error){throw new IllegalStateException("Cannot inspect HBM quest requirement "+req,error);}
    }
    static ItemStack display(JsonObject req){
        try{
            if(req.has("cell_fluid"))return (ItemStack)Class.forName("com.hbm.items.special.ItemCell")
                .getMethod("getFullCell",Class.forName("com.hbm.inventory.fluid.FluidType")).invoke(null,cellFluid(req));
            return (ItemStack)Class.forName("com.hbm.inventory.RecipesCommon$AStack").getMethod("getDisplayStack").invoke(fluid(req));
        }
        catch(ReflectiveOperationException error){throw new IllegalStateException(error);}
    }
    private static Object cellFluid(JsonObject req)throws ReflectiveOperationException{
        return Class.forName("com.hbm.inventory.fluid.Fluids").getMethod("fromName",String.class).invoke(null,req.get("cell_fluid").getAsString());
    }
}
