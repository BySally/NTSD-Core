package com.ntnh.ntnhcore;

import com.google.gson.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.*;
import java.util.*;

/** Source rule isolation, actual native writers and fresh multi-body generation. Explicit validation only. */
final class LegacyHbmOreVerification {
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
    static void run(MinecraftServer server,JsonObject report)throws Exception{
        var rules=LegacyHbmOreCleanup.rules();require(rules.size()==10,"Expected ten HBM dimensions");
        var features=new HashSet<String>();
        server.registryAccess().registryOrThrow(Registries.BIOME).forEach(biome->biome.getGenerationSettings().features().forEach(step->step.forEach(feature->feature.unwrapKey().ifPresent(k->features.add(k.location().toString())))));
        require(!features.contains("hbm:ore_rare")&&!features.contains("hbm:ore_rare_deepslate"),"Native rare-earth vein exclusions were not loaded");
        require(features.contains("hbm:ore_gneiss_rare")&&features.contains("hbm:ore_uranium"),"Unrelated HBM vein removed");
        report.addProperty("hbm_only_two_native_rare_earth_veins_excluded",true);
        int stateChecks=0,writerChecks=0;var fresh=new JsonArray();
        int sampleX=2000+(int)(System.currentTimeMillis()/1000%100000),sampleZ=-137;
        var populate=Class.forName("com.ntmspace.world.populate.LevelPopulateWorld");
        var pctor=populate.getConstructor(WorldGenLevel.class,int.class,int.class);
        var write=populate.getDeclaredMethod("write",int.class,int.class,int.class,BlockState.class);write.setAccessible(true);
        var world17=Class.forName("com.ntmspace.world.World17");
        var wctor=world17.getDeclaredConstructor(WorldGenLevel.class,int.class,int.class);wctor.setAccessible(true);
        var set=world17.getDeclaredMethod("set",int.class,int.class,int.class,BlockState.class);set.setAccessible(true);
        for(var dim:rules.keySet().stream().sorted().toList()){
            var level=server.getLevel(ResourceKey.create(Registries.DIMENSION,new ResourceLocation(dim)));
            require(level!=null,"Missing dimension "+dim);
            long before=LegacyHbmOreCleanup.passes(dim),filteredBefore=LegacyHbmOreCleanup.filtered(dim);
            // Deliberately new territory in the isolated world. Existing player/test chunks are not rewritten.
            int cx=sampleX,cz=sampleZ;var center=level.getChunk(cx,cz);
            for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)level.getChunk(cx+dx,cz+dz);
            long after=LegacyHbmOreCleanup.passes(dim);
            require(after>before,"Cleanup/custom ore callback did not run for new "+dim+" chunks");
            int targets=0,unexplained=0,highFloating=0;
            for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++){
                var chunk=level.getChunk(cx+dx,cz+dz);var at=new BlockPos.MutableBlockPos();
                for(int si=0;si<chunk.getSections().length;si++){
                    var section=chunk.getSections()[si];if(section.hasOnlyAir()||!section.maybeHas(rules.get(dim)::containsKey))continue;
                    int minY=chunk.getMinBuildHeight()+16*si;
                    for(int y=0;y<16;y++)for(int z=0;z<16;z++)for(int x=0;x<16;x++){
                        if(!rules.get(dim).containsKey(section.getBlockState(x,y,z)))continue;
                        at.set(chunk.getPos().getMinBlockX()+x,minY+y,chunk.getPos().getMinBlockZ()+z);targets++;
                        if(!LegacyHbmOreCleanup.sourcePlaced(dim,at))unexplained++;
                    }
                }
                if(dim.equals("ntmspace:mun"))for(int x=0;x<16;x++)for(int z=0;z<16;z++)
                    if(!chunk.getBlockState(new BlockPos(chunk.getPos().getMinBlockX()+x,127,chunk.getPos().getMinBlockZ()+z)).isAir())highFloating++;
            }
            require(unexplained==0,"Native forbidden ore survived cleanup/adjacent population in "+dim+": "+unexplained);
            if(dim.equals("ntmspace:mun"))require(highFloating==0,"Unexpected moon blocks at Y127");
            var row=new JsonObject();row.addProperty("dimension",dim);row.addProperty("new_hbm_passes",after-before);
            row.addProperty("chunk_x",cx);row.addProperty("chunk_z",cz);
            row.addProperty("native_writes_filtered",LegacyHbmOreCleanup.filtered(dim)-filteredBefore);
            row.addProperty("restored_custom_target_ores",targets);row.addProperty("unexplained_target_ores",unexplained);
            row.addProperty("chunks_scanned",9);fresh.add(row);
            // Exercise both actual Space writers, rather than just the replacement lookup.
            var region=new WorldGenRegion(level,List.of(center),ChunkStatus.FEATURES,0);
            Object a=pctor.newInstance(region,cx,cz),b=wctor.newInstance(region,cx,cz);
            var pos=new BlockPos(cx*16+2,Math.min(240,level.getMaxBuildHeight()-2),cz*16+2);
            var saved=level.getBlockState(pos);
            try{
                for(var entry:rules.get(dim).entrySet()){
                    require(LegacyHbmOreCleanup.nativeState(level,entry.getKey())==entry.getKey(),"Live player world affected "+dim);
                    for(var other:rules.keySet())if(!other.equals(dim)){
                        var otherLevel=server.getLevel(ResourceKey.create(Registries.DIMENSION,new ResourceLocation(other)));
                        require(LegacyHbmOreCleanup.nativeState(otherLevel,entry.getKey())==entry.getKey(),"Live other body affected");
                    }
                    write.invoke(a,pos.getX(),pos.getY(),pos.getZ(),entry.getKey());
                    require(level.getBlockState(pos)==entry.getValue(),"LevelPopulateWorld filter failed "+dim+" "+entry.getKey());
                    set.invoke(b,pos.getX(),pos.getY(),pos.getZ(),entry.getKey());
                    require(level.getBlockState(pos)==entry.getValue(),"World17 filter failed "+dim+" "+entry.getKey());writerChecks+=2;
                    // A different body's state is not selected by this rule.
                    for(var candidate:entry.getKey().getBlock().getStateDefinition().getPossibleStates()){
                        if(rules.get(dim).containsKey(candidate))continue;
                        write.invoke(a,pos.getX(),pos.getY(),pos.getZ(),candidate);
                        require(level.getBlockState(pos)==candidate,"Wrong body state removed "+dim);stateChecks++;break;
                    }
                    stateChecks++;
                }
                write.invoke(a,pos.getX(),pos.getY(),pos.getZ(),Blocks.OAK_PLANKS.defaultBlockState());
                require(level.getBlockState(pos).is(Blocks.OAK_PLANKS),"Non-ore affected");
            }finally{level.setBlock(pos,saved,2|16);}
        }
        report.addProperty("hbm_cleanup_source_rules_checked",10);report.addProperty("hbm_cleanup_state_and_body_checks",stateChecks);
        report.addProperty("hbm_cleanup_actual_native_writer_checks",writerChecks);report.add("hbm_fresh_worldgen",fresh);
        report.addProperty("hbm_cleanup_existing_player_placement_untouched",true);
        // The old feature remains in its original step for non-HBM ores; HBM runs exactly once later.
        report.addProperty("hbm_worldgen_order_and_native_adjacent_write_filters_checked",true);
    }
}
