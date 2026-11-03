package com.ntnh.ntnhcore;

import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import funwayguy.betterquesting.questing.player.ParticipantInfo;
import funwayguy.betterquesting.questing.player.profile.ProfileManager;
import funwayguy.bq_standard.bootstrap.forge.events.InvoProxy;
import funwayguy.betterquesting.questing.quests.QuestDatabase;
import funwayguy.bq_standard.questing.tasks.TaskRetrieval;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Exercise the installed Chisel API and native recipe/quest matchers. */
final class MarkingsVerification {
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
    private static CraftingContainer grid(){return new TransientCraftingContainer(new AbstractContainerMenu(null,0){
        @Override public boolean stillValid(net.minecraft.world.entity.player.Player p){return true;}
        @Override public ItemStack quickMoveStack(net.minecraft.world.entity.player.Player p,int slot){return ItemStack.EMPTY;}
    },3,3);}
    static void run(MinecraftServer server,JsonObject report)throws Exception{
        var helper=Class.forName("com.leclowndu93150.chisel.carving.CarvingHelper");
        int checked=0,restored=0;var groups=new HashMap<String,Set<Block>>();
        try(var input=MarkingsVerification.class.getResourceAsStream("/ntnhcore/legacy-markings.json")){
            for(var element:JsonParser.parseReader(new InputStreamReader(input,StandardCharsets.UTF_8)).getAsJsonArray()){
                var row=element.getAsJsonObject();var id=new ResourceLocation(row.get("id").getAsString());String kind=row.get("kind").getAsString();
                var block=BuiltInRegistries.BLOCK.getOptional(id).orElseThrow();var item=BuiltInRegistries.ITEM.getOptional(id).orElseThrow();
                var state=block.defaultBlockState();var stack=new ItemStack(item);require(item instanceof BlockItem&&((BlockItem)item).getBlock()==block,"Marking block/item mismatch "+id);
                require(block.getClass().getSimpleName().equals(kind.equals("floor")?"MarkingFloor":"MarkingWall"),"Wrong marking orientation implementation "+id);
                require(state.hasProperty(BlockStateProperties.HORIZONTAL_FACING),"Marking cannot be rotated "+id);
                for(var direction:net.minecraft.core.Direction.Plane.HORIZONTAL){
                    var facing=state.setValue(BlockStateProperties.HORIZONTAL_FACING,direction);
                    require(!facing.getShape(server.overworld(),BlockPos.ZERO).isEmpty(),"Missing marking selection shape "+id);
                    require(facing.getCollisionShape(server.overworld(),BlockPos.ZERO).isEmpty(),"Painted marking obstructs walking "+id);
                }
                var drops=Block.getDrops(state,server.overworld(),BlockPos.ZERO,null,null,ItemStack.EMPTY);
                require(drops.size()==1&&drops.get(0).is(item)&&drops.get(0).getCount()==1,"Marking drops a different variant "+id);
                require((boolean)helper.getMethod("canChisel",BlockState.class).invoke(null,state),"Chisel rejects placed marking "+id);
                require((boolean)helper.getMethod("canChisel",ItemStack.class).invoke(null,stack),"Chisel rejects inventory marking "+id);
                var blockGroup=(TagKey<?>)helper.getMethod("getCarvingGroup",BlockState.class).invoke(null,state);
                var itemGroup=(TagKey<?>)helper.getMethod("getCarvingGroupForItem",ItemStack.class).invoke(null,stack);
                var expected=new ResourceLocation("chisel","carving/ntnh_markings_"+kind);
                require(blockGroup.location().equals(expected)&&itemGroup.location().equals(expected),"Floor/wall Chisel groups mixed "+id);
                var next=(Block)helper.getMethod("getNextVariant",BlockState.class).invoke(null,state);
                var previous=(Block)helper.getMethod("getPreviousVariant",BlockState.class).invoke(null,next.defaultBlockState());
                require(previous==block&&next!=block,"Chisel variant cycle loses a marking "+id);
                groups.computeIfAbsent(kind,k->new HashSet<>()).add(block);checked++;if(row.get("restored").getAsBoolean())restored++;
            }
        }
        for(var entry:groups.entrySet()){
            var tag=net.minecraft.tags.BlockTags.create(new ResourceLocation("chisel","carving/ntnh_markings_"+entry.getKey()));
            var variants=(List<?>)helper.getMethod("getBlocksInGroup",TagKey.class).invoke(null,tag);
            require(new HashSet<>(variants).equals(entry.getValue()),"Native Chisel group omits legacy markings");
            Block start=entry.getValue().iterator().next(),cursor=start;var seen=new HashSet<Block>();
            do{require(seen.add(cursor),"Chisel cycle repeats before reaching its start");cursor=(Block)helper.getMethod("getNextVariant",BlockState.class).invoke(null,cursor.defaultBlockState());}while(cursor!=start&&seen.size()<=170);
            require(seen.equals(entry.getValue()),"Some old markings cannot be reached with Chisel");
        }
        for(String kind:List.of("floor","wall"))for(var plank:List.of(Items.OAK_PLANKS,Items.SPRUCE_PLANKS,Items.BIRCH_PLANKS,Items.JUNGLE_PLANKS,Items.ACACIA_PLANKS,Items.DARK_OAK_PLANKS)){
            var grid=grid();grid.setItem(0,new ItemStack(Items.PAPER));grid.setItem(kind.equals("floor")?3:1,new ItemStack(plank));
            var recipe=server.getRecipeManager().getRecipeFor(RecipeType.CRAFTING,grid,server.overworld()).orElseThrow();
            var output=recipe.assemble(grid,server.registryAccess());
            require(output.is(BuiltInRegistries.ITEM.get(new ResourceLocation("ags_modernmarkings",kind+"_marking_exit")))&&output.getCount()==64,"Old marking recipe/cost differs");
            grid.setItem(kind.equals("floor")?3:1,new ItemStack(Items.CRIMSON_PLANKS));
            require(!recipe.matches(grid,server.overworld()),"Marking recipe accepts a plank not accepted by the original");
        }
        for(int taskID:new int[]{257,513}){
            var player=new ServerPlayer(server,server.overworld(),new GameProfile(UUID.randomUUID(),"MarkingsCheck"));ProfileManager.get().makeOrUpdate(player.getUUID(),"MarkingsCheck");
            var participant=new ParticipantInfo(player.getUUID());var task=(TaskRetrieval)QuestDatabase.INSTANCE.getValue(93185).getTasks().getValue(taskID);
            task.onInventoryChange(participant,taskID,93185,new InvoProxy(player.getInventory()));task.detect(participant,taskID,93185);
            require(!participant.PLAYER.progress.getTaskData(93185,taskID).getBoolean("isComplete"),"Empty inventory grants markings goal");
            var item=BuiltInRegistries.ITEM.get(new ResourceLocation("ags_modernmarkings",(taskID==257?"floor":"wall")+"_marking_exit"));player.getInventory().add(new ItemStack(item));
            task.onInventoryChange(participant,taskID,93185,new InvoProxy(player.getInventory()));task.detect(participant,taskID,93185);
            require(participant.PLAYER.progress.getTaskData(93185,taskID).getBoolean("isComplete")&&player.getInventory().countItem(item)==1,"Marking not detected or consumed");
        }
        require(checked==170&&restored==92,"Original marking registry coverage differs");
        report.addProperty("legacy_marking_variants_native_chisel_and_drops_checked",checked);report.addProperty("restored_missing_marking_variants_checked",restored);
        report.addProperty("markings_floor_and_wall_full_chisel_cycles_checked",true);report.addProperty("markings_original_recipes_and_nonconsuming_quest_detection_checked",true);
    }
}
