package com.ntnh.ntnhcore;

import com.google.gson.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/** Ten original NTNH COG substitutions, strictly limited to generation of new HBM terrain. */
public final class LegacyHbmOreCleanup {
    private static volatile Map<String,Map<BlockState,BlockState>> replacements;
    private static final boolean VALIDATION=Boolean.getBoolean("ntnhcore.validation");
    private static final Map<String,LongAdder> nativeFiltered=new ConcurrentHashMap<>(),passes=new ConcurrentHashMap<>();
    private static final Map<String,Set<Long>> restored=new ConcurrentHashMap<>();
    static Map<String,Map<BlockState,BlockState>> rules(){
        if(replacements!=null)return replacements;
        synchronized(LegacyHbmOreCleanup.class){
            if(replacements!=null)return replacements;
            var out=new HashMap<String,Map<BlockState,BlockState>>();
            try(var in=LegacyHbmOreCleanup.class.getResourceAsStream("/ntnhcore/hbm-ore-cleanup.json")){
                if(in==null)throw new IllegalStateException("Missing HBM cleanup rules");
                for(var value:JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonArray()){
                    var row=value.getAsJsonObject();var dim=row.get("dimension").getAsString();
                    var target=BuiltInRegistries.BLOCK.getOptional(new ResourceLocation(row.get("replacement").getAsString())).orElseThrow().defaultBlockState();
                    var map=out.computeIfAbsent(dim,k->new HashMap<>());
                    for(var spec:row.getAsJsonArray("matches")){
                        var match=spec.getAsJsonObject();var block=BuiltInRegistries.BLOCK.getOptional(new ResourceLocation(match.get("block").getAsString())).orElseThrow();
                        for(var state:block.getStateDefinition().getPossibleStates()){
                            boolean accept=true;
                            for(var p:match.getAsJsonObject("properties").entrySet()){
                                var property=block.getStateDefinition().getProperty(p.getKey());
                                if(property==null)throw new IllegalStateException("Unknown cleanup property "+p.getKey());
                                if(!propertyName(state,property).equals(p.getValue().getAsString()))accept=false;
                            }
                            if(accept)map.put(state,target);
                        }
                    }
                }
            }catch(java.io.IOException e){throw new IllegalStateException(e);}
            var immutable=new HashMap<String,Map<BlockState,BlockState>>();out.forEach((k,v)->immutable.put(k,Map.copyOf(v)));
            replacements=Map.copyOf(immutable);return replacements;
        }
    }
    private static <T extends Comparable<T>> String propertyName(BlockState state,net.minecraft.world.level.block.state.properties.Property<T> property){return property.getName(state.getValue(property));}
    static Map<BlockState,BlockState> rules(String dim){return rules().getOrDefault(dim,Map.of());}
    /** Native Space writes may spill into an already decorated neighbour. Filter them when they occur. */
    public static BlockState nativeState(WorldGenLevel level,BlockState state){
        if(!(level instanceof WorldGenRegion))return state; // No change to live saplings, placement, machines or saved worlds.
        String dim=level.getLevel().dimension().location().toString();var next=rules(dim).get(state);
        if(next==null)return state;
        if(VALIDATION)nativeFiltered.computeIfAbsent(dim,k->new LongAdder()).increment();
        return next;
    }
    static int clean(WorldGenLevel level,ChunkAccess chunk){
        var map=rules(level.getLevel().dimension().location().toString());if(map.isEmpty())return 0;
        var at=new BlockPos.MutableBlockPos();int count=0;
        for(int section=0;section<chunk.getSections().length;section++){
            var data=chunk.getSections()[section];if(data.hasOnlyAir()||!data.maybeHas(map::containsKey))continue;
            int minY=chunk.getMinBuildHeight()+section*16;
            for(int y=0;y<16;y++)for(int z=0;z<16;z++)for(int x=0;x<16;x++){
                var target=map.get(data.getBlockState(x,y,z));if(target==null)continue;
                at.set(chunk.getPos().getMinBlockX()+x,minY+y,chunk.getPos().getMinBlockZ()+z);
                level.setBlock(at,target,2|16);count++;
            }
        }
        return count;
    }
    /** Called once after this planet's native populate, never on loading an existing chunk. */
    public static void afterPlanet(WorldGenLevel level,ChunkAccess chunk){
        if(!(level instanceof WorldGenRegion))return;
        String dim=level.getLevel().dimension().location().toString();if(!rules().containsKey(dim)||dim.equals("minecraft:overworld"))return;
        PackOreFeature.placeHbm(level,chunk);
    }
    static void pass(WorldGenLevel level){if(VALIDATION)passes.computeIfAbsent(level.getLevel().dimension().location().toString(),k->new LongAdder()).increment();}
    static void placed(WorldGenLevel level,BlockPos pos,BlockState state){
        if(!VALIDATION)return;String dim=level.getLevel().dimension().location().toString();
        if(rules(dim).containsKey(state))restored.computeIfAbsent(dim,k->ConcurrentHashMap.newKeySet()).add(pos.asLong());
    }
    static boolean sourcePlaced(String dim,BlockPos pos){return restored.getOrDefault(dim,Set.of()).contains(pos.asLong());}
    static long passes(String dim){return passes.containsKey(dim)?passes.get(dim).sum():0;}
    static long filtered(String dim){return nativeFiltered.containsKey(dim)?nativeFiltered.get(dim).sum():0;}
}
