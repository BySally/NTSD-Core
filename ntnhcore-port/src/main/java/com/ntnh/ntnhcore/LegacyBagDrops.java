package com.ntnh.ntnhcore;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * NTNH loot semantics from EnhancedLootBags (USRV / GTNewHorizons, GPL-3.0).
 * Player limits count selections, survive respawns and share one overworld save across dimensions.
 * Prepare the entire opening before committing counters or giving items.
 */
final class LegacyBagDrops {
    private record Drop(String id,ItemStack template,int amount,boolean random,int weight,int limit,String group) {}
    private record Pool(int min,int max,List<Drop> drops,int totalWeight) {}
    private static final class Definitions {
        static final Map<String,Pool> POOLS=load();
        private static Map<String,Pool> load(){
            try(var stream=LegacyBagDrops.class.getResourceAsStream("/ntnhcore/loot-bags.json")){
                if(stream==null)throw new IllegalStateException("Missing NTNH loot definitions");
                var json=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();
                if(json.get("format").getAsInt()!=1)throw new IllegalStateException("Unsupported NTNH loot format");
                Map<String,Pool> result=new HashMap<>();
                for(var entry:json.getAsJsonObject("pools").entrySet()){
                    var spec=entry.getValue().getAsJsonObject();List<Drop> drops=new ArrayList<>();int total=0;
                    for(var value:spec.getAsJsonArray("drops")){
                        var d=value.getAsJsonObject();int weight=d.get("weight").getAsInt();
                        var item=BuiltInRegistries.ITEM.getOptional(new ResourceLocation(d.get("item").getAsString())).orElseThrow();
                        var stack=new ItemStack(item);applyState(stack,d.getAsJsonObject("state"));
                        if(stack.isEmpty()||weight<=0)throw new IllegalStateException("Invalid NTNH loot "+d);
                        drops.add(new Drop(d.get("identifier").getAsString(),stack,d.get("amount").getAsInt(),d.get("random_amount").getAsBoolean(),weight,d.get("limit").getAsInt(),d.get("item_group").getAsString()));
                        total=Math.addExact(total,weight);
                    }
                    int min=spec.get("min_items").getAsInt(),max=spec.get("max_items").getAsInt();
                    if(total<=0||min<1||max<min)throw new IllegalStateException("Invalid NTNH loot pool "+entry.getKey());
                    result.put(entry.getKey(),new Pool(min,max,List.copyOf(drops),total));
                }
                return Map.copyOf(result);
            }catch(java.io.IOException error){throw new IllegalStateException(error);}
        }
    }
    static List<ItemStack> open(ServerLevel level,Player player,int tier,int fortune){
        var pool=Definitions.POOLS.get(tier+"_"+fortune);if(pool==null)return List.of();
        var limits=level.getServer().overworld().getDataStorage().computeIfAbsent(Limits::load,Limits::new,"ntnh-loot-limits");
        Map<String,Integer> pending=new HashMap<>();List<ItemStack> result=new ArrayList<>();
        var random=level.random;int remaining=pool.max>pool.min?random.nextInt(pool.max)+1:pool.min;
        while(remaining>0){
            List<ItemStack> selected=new ArrayList<>();
            for(int attempt=0;attempt<10&&selected.isEmpty();attempt++){
                int ticket=random.nextInt(pool.totalWeight);Drop winner=null;
                for(var drop:pool.drops){ticket-=drop.weight;if(ticket<0){winner=drop;break;}}
                if(winner==null)throw new IllegalStateException("Unreachable NTNH loot selection");
                for(var drop:pool.drops){
                    if(winner.group.isEmpty()?drop!=winner:!winner.group.equalsIgnoreCase(drop.group))continue;
                    String key=player.getUUID()+"_"+tier+"_"+drop.id;
                    int received=pending.getOrDefault(key,limits.counts.getOrDefault(key,0));
                    if(drop.limit>0&&received>=drop.limit)continue;
                    if(drop.limit>0)pending.put(key,received+1);
                    int amount=drop.random?random.nextInt(drop.amount)+1:drop.amount;
                    while(amount>0){var stack=drop.template.copy();int count=Math.min(amount,stack.getMaxStackSize());stack.setCount(count);selected.add(stack);amount-=count;}
                }
            }
            if(selected.isEmpty())return List.of(); // No rewards or counters from a failed opening.
            remaining-=selected.size();result.addAll(selected);
        }
        if(!pending.isEmpty()){limits.counts.putAll(pending);limits.setDirty();}
        return result;
    }
    static Map<String,ItemStack> definitionsForAudit(){
        Map<String,ItemStack> result=new HashMap<>();
        Definitions.POOLS.forEach((key,pool)->pool.drops.forEach(drop->result.put(key+"/"+drop.id,drop.template.copy())));
        return result;
    }
    static Map<String,Integer> limitsForAudit(ServerLevel level){
        return Map.copyOf(level.getServer().overworld().getDataStorage().computeIfAbsent(Limits::load,Limits::new,"ntnh-loot-limits").counts);
    }
    private static void applyState(ItemStack stack,JsonObject state){
        try{
            if(state.has("container_fluid")){
                var spec=new JsonObject();spec.addProperty("item",BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
                spec.addProperty("fluid",state.get("container_fluid").getAsString());spec.addProperty("amount",state.get("container_amount").getAsInt());
                var full=HbmQuestRequirement.display(spec);
                if(full.isEmpty()||!full.is(stack.getItem())||!HbmQuestRequirement.matches(spec,full))throw new IllegalStateException("Invalid filled HBM reward "+state);
                stack.setTag(full.getTag()==null?null:full.getTag().copy());
            }
            if(state.has("charge"))stack.getItem().getClass().getMethod("setCharge",ItemStack.class,long.class).invoke(stack.getItem(),stack,state.get("charge").getAsLong());
            if(state.has("legacy_inert_charge")){
                if(!BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals("hbm:battery_spark"))throw new IllegalStateException("Unexpected legacy inert charge "+stack);
                stack.getOrCreateTag().putLong("charge",state.get("legacy_inert_charge").getAsLong());
            }
            if(state.has("lifespan"))stack.getItem().getClass().getMethod("setLifespan",ItemStack.class,long.class).invoke(stack.getItem(),stack,state.get("lifespan").getAsLong());
            if(state.has("rbmk_yield")){
                var rod=Class.forName("com.hbm.items.machine.ItemRBMKRod");
                rod.getMethod("setYield",ItemStack.class,double.class).invoke(null,stack,state.get("rbmk_yield").getAsDouble());
                rod.getMethod("setCoreHeat",ItemStack.class,double.class).invoke(null,stack,state.get("rbmk_core").getAsDouble());
                rod.getMethod("setHullHeat",ItemStack.class,double.class).invoke(null,stack,state.get("rbmk_hull").getAsDouble());
            }
        }catch(ReflectiveOperationException error){throw new IllegalStateException("Cannot initialize HBM reward "+stack+" "+state,error);}
    }
    private static final class Limits extends SavedData {
        final Map<String,Integer> counts=new HashMap<>();
        static Limits load(CompoundTag tag){
            var result=new Limits();var values=tag.getCompound("counts");
            for(var key:values.getAllKeys())result.counts.put(key,Math.max(0,values.getInt(key)));
            return result;
        }
        @Override public CompoundTag save(CompoundTag tag){var values=new CompoundTag();counts.forEach(values::putInt);tag.put("counts",values);return tag;}
    }
}
