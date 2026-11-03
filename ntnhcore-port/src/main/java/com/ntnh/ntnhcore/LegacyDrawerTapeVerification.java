package com.ntnh.ntnhcore;

import com.google.gson.JsonObject;
import com.jaquadro.minecraft.storagedrawers.api.storage.IDrawerAttributesModifiable;
import com.jaquadro.minecraft.storagedrawers.block.BlockDrawers;
import com.jaquadro.minecraft.storagedrawers.block.FaceSlotBlock.InteractContext;
import com.jaquadro.minecraft.storagedrawers.block.tile.BlockEntityDrawers;
import funwayguy.betterquesting.questing.player.ParticipantInfo;
import funwayguy.betterquesting.questing.player.profile.ProfileManager;
import funwayguy.betterquesting.questing.quests.QuestDatabase;
import funwayguy.bq_standard.questing.tasks.TaskRetrieval;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import java.util.*;

/** Real native break/placement, ownership and automation, in the isolated validation world only. */
final class LegacyDrawerTapeVerification {
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
    private static BlockEntityDrawers tile(ServerLevel level,BlockPos pos){return (BlockEntityDrawers)level.getBlockEntity(pos);}
    private static void cleanDrops(ServerLevel level,BlockPos pos){for(var e:level.getEntitiesOfClass(ItemEntity.class,new AABB(pos).inflate(3)))e.discard();}
    private static List<ItemStack> drops(ServerLevel level,BlockPos pos){return level.getEntitiesOfClass(ItemEntity.class,new AABB(pos).inflate(3)).stream().map(e->e.getItem().copy()).toList();}
    private static UseOnContext context(ServerLevel level,BlockPos pos,ServerPlayer actor,ItemStack held){
        actor.setItemInHand(InteractionHand.MAIN_HAND,held);return new UseOnContext(actor,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(pos),Direction.NORTH,pos,false));
    }
    private static IDrawerAttributesModifiable attrs(BlockEntityDrawers tile){return (IDrawerAttributesModifiable)tile.getDrawerAttributes();}
    private static String inventory(BlockEntityDrawers tile){
        var a=new net.minecraft.nbt.ListTag();for(int i=0;i<tile.getGroup().getDrawerCount();i++){
            var drawer=tile.getGroup().getDrawer(i);var prototype=drawer.getStoredItemPrototype();
            // Prototype stack size is not the inventory quantity. Empty native prototypes may
            // be AIR×1 before serialisation and ItemStack.EMPTY afterwards; those are equivalent.
            var tag=new CompoundTag();if(!prototype.isEmpty()){var copy=prototype.copy();copy.setCount(1);copy.save(tag);}
            tag.putInt("FullCount",drawer.getStoredItemCount());a.add(tag);
        }return a.toString();
    }
    static void run(MinecraftServer server,JsonObject report)throws Exception {
        require(Boolean.getBoolean("ntnhcore.validation"),"Drawer checks are isolated-runtime only");
        var level=server.overworld();var pos=new BlockPos(384,300,384);var place=pos.east(2);
        require(level.getBlockState(pos).isAir()&&level.getBlockState(place).isAir(),"Drawer validation area is not empty");
        var actor=new ServerPlayer(server,level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"TapeCheck"));
        actor.setPos(pos.getX()+.5,pos.getY(),pos.getZ()-2.5);var tape=LegacyDrawerTape.TAPE.get();
        int definitions=0,roundtrips=0;
        for(var b:BuiltInRegistries.BLOCK)if(b instanceof BlockDrawers){require(b.defaultBlockState().hasProperty(LegacyDrawerTape.SEALED)&&!LegacyDrawerTape.sealed(b.defaultBlockState()),"A native drawer defaults to sealed: "+BuiltInRegistries.BLOCK.getKey(b));definitions++;}
        String[] variants={"oak_full_drawers_1","oak_full_drawers_2","oak_full_drawers_4","oak_half_drawers_1","oak_half_drawers_2","oak_half_drawers_4","framed_full_drawers_1","compacting_drawers_3","compacting_half_drawers_2"};
        try{
            for(var name:variants)for(var direction:Direction.Plane.HORIZONTAL){
                var block=BuiltInRegistries.BLOCK.get(new ResourceLocation("storagedrawers:"+name));
                var state=block.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING,direction);level.setBlock(pos,state,3);var source=tile(level,pos);
                source.customName().setName(net.minecraft.network.chat.Component.literal("NTNH tape "+direction));
                var upgrade=new ItemStack(BuiltInRegistries.ITEM.get(new ResourceLocation("storagedrawers:gold_storage_upgrade")));
                require(source.upgrades().addUpgrade(upgrade),"Native upgrade insertion failed");
                if(name.startsWith("framed")){source.material().setSide(new ItemStack(Blocks.BRICKS));source.material().setFront(new ItemStack(Blocks.OAK_PLANKS));source.material().setTrim(new ItemStack(Blocks.GOLD_BLOCK));}
                var stored=new ItemStack(Items.IRON_INGOT);stored.getOrCreateTag().putString("ntnh_test","contents retain NBT");
                source.getGroup().getDrawer(0).setStoredItem(stored);source.getGroup().getDrawer(0).setStoredItemCount(137);
                var expected=inventory(source);var expectedMaterial=source.material().write(new CompoundTag());
                var roll=new ItemStack(tape);require(tape.onItemUseFirst(roll,context(level,pos,actor,roll)).consumesAction(),"Tape first-use did not intercept native drawer insertion");
                require(LegacyDrawerTape.sealed(level,pos)&&source.getDrawerAttributes().isSealed()&&roll.getDamageValue()==1,"Native seal/roll wear missing");
                require(!tape.useOn(context(level,pos,actor,roll)).consumesAction()&&roll.getDamageValue()==1,"Repeated seal spends tape");
                require(source.takeItemsFromSlot(0,1).isEmpty()&&source.putItemsIntoSlot(0,stored.copy(),1)==0,"Packed direct transfer remained active");
                var handler=source.getCapability(ForgeCapabilities.ITEM_HANDLER,Direction.UP).orElseThrow(()->new IllegalStateException("Missing native item handler"));
                require(handler.getSlots()==0&&handler.extractItem(1,1,false).isEmpty(),"Packed automation extracts contents");
                require(handler.insertItem(0,stored.copy(),false).getCount()==stored.getCount(),"Packed automation accepts items");
                require(handler.insertItem(1,stored.copy(),false).getCount()==stored.getCount(),"Packed direct-slot automation accepts items");
                require(!source.saveWithoutMetadata().getBoolean("Sus"),"Temporary tape seal leaked into the persistent native suspension flag");
                var ctx=new InteractContext(level.getBlockState(pos),level,pos,actor,new BlockHitResult(Vec3.atCenterOf(pos),direction,pos,false),0);
                var drawers=(BlockDrawers)block;
                require(!drawers.interactPullDrawer(ctx)&&!drawers.interactReturnDrawer(ctx,new ItemStack(Items.DIRT)),"Packed detached-drawer path remained active");
                var savedState=NbtUtils.writeBlockState(level.getBlockState(pos));
                require(LegacyDrawerTape.sealed(NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(),savedState)),"Seal lost in state NBT roundtrip");
                cleanDrops(level,pos);require(level.destroyBlock(pos,true),"Native packed break failed");
                var packedDrops=drops(level,pos);require(packedDrops.size()==1&&packedDrops.get(0).is(block.asItem())&&packedDrops.get(0).getTagElement("tile")!=null,"Packed break duplicated/spilled contents or lost NBT");
                var packed=packedDrops.get(0);cleanDrops(level,pos);
                level.setBlock(place.below(),Blocks.STONE.defaultBlockState(),3);
                // Exercise BlockItem.place and native setPlacedBy/readPortable, not a manual NBT copy.
                actor.setItemInHand(InteractionHand.MAIN_HAND,packed);
                var use=new UseOnContext(actor,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(place.below()).add(0,.5,0),Direction.UP,place.below(),false));
                require(((BlockItem)block.asItem()).place(new BlockPlaceContext(use)).consumesAction(),"Native packed BlockItem placement failed");
                var restored=tile(level,place);
                require(!LegacyDrawerTape.sealed(level,place)&&!restored.getDrawerAttributes().isSealed(),"Placement failed to remove tape");
                require(inventory(restored).equals(expected),"Stored prototypes/counts/NBT changed in transit: "+name+" before="+expected+" after="+inventory(restored));
                require(restored.upgrades().getUpgrade(0).is(upgrade.getItem())&&restored.customName().getDisplayName().getString().equals("NTNH tape "+direction)&&restored.material().write(new CompoundTag()).equals(expectedMaterial),"Upgrade/name/frame material lost in transit");
                int before=restored.getGroup().getDrawer(0).getStoredItemCount();
                require(!restored.getDrawerAttributes().isSuspended()&&!restored.takeItemsFromSlot(0,1).isEmpty()&&restored.getGroup().getDrawer(0).getStoredItemCount()==before-1,"Placed drawer remained suspended or manual extraction duplicated contents");
                var restoredHandler=restored.getCapability(ForgeCapabilities.ITEM_HANDLER,Direction.UP).orElseThrow(()->new IllegalStateException("Missing placed native item handler"));
                require(!restoredHandler.extractItem(1,1,false).isEmpty()&&restored.getGroup().getDrawer(0).getStoredItemCount()==before-2,"Automation did not resume after placement or duplicated contents");
                level.setBlock(place,Blocks.AIR.defaultBlockState(),3);level.setBlock(place.below(),Blocks.AIR.defaultBlockState(),3);cleanDrops(level,pos);roundtrips++;
            }
            var block=BuiltInRegistries.BLOCK.get(new ResourceLocation("storagedrawers:oak_full_drawers_1"));level.setBlock(pos,block.defaultBlockState(),3);var source=tile(level,pos);
            source.upgrades().addUpgrade(new ItemStack(BuiltInRegistries.ITEM.get(new ResourceLocation("storagedrawers:gold_storage_upgrade"))));
            source.getGroup().getDrawer(0).setStoredItem(new ItemStack(Items.COBBLESTONE));source.getGroup().getDrawer(0).setStoredItemCount(4097);
            require(source.getGroup().getDrawer(0).getStoredItemCount()==4097,"Large-stock fixture did not fit in the upgraded native drawer");
            cleanDrops(level,pos);level.destroyBlock(pos,true);var loose=drops(level,pos);
            int looseCount=loose.stream().filter(s->s.is(Items.COBBLESTONE)).mapToInt(ItemStack::getCount).sum();
            require(looseCount==4097,"Unsealed break lost contents: expected 4097, got "+looseCount);
            require(loose.stream().filter(s->s.is(block.asItem())).count()==1&&loose.stream().filter(s->s.is(block.asItem())).noneMatch(s->s.getTagElement("tile")!=null),"Unsealed break also drops a portable inventory");
            require(loose.stream().filter(s->BuiltInRegistries.ITEM.getKey(s.getItem()).toString().equals("storagedrawers:gold_storage_upgrade")).mapToInt(ItemStack::getCount).sum()==1,"Unsealed upgrade duplicated or lost");cleanDrops(level,pos);
            level.setBlock(pos,block.defaultBlockState(),3);source=tile(level,pos);source.setOwner(UUID.randomUUID());var roll=new ItemStack(tape);
            require(!tape.useOn(context(level,pos,actor,roll)).consumesAction()&&roll.getDamageValue()==0&&!LegacyDrawerTape.sealed(level,pos),"Foreign-owned drawer can be sealed");source.setOwner(null);
            for(int i=0;i<9;i++){
                require(tape.useOn(context(level,pos,actor,roll)).consumesAction(),"Tape failed before legacy roll was exhausted");
                require((i<8&&!roll.isEmpty()&&roll.getDamageValue()==i+1)||(i==8&&roll.isEmpty()),"Wrong legacy tape lifespan");
                actor.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);actor.setShiftKeyDown(true);
                var event=new PlayerInteractEvent.RightClickBlock(actor,InteractionHand.MAIN_HAND,pos,new BlockHitResult(Vec3.atCenterOf(pos),Direction.NORTH,pos,false));
                LegacyDrawerTape.rightClick(event);require(event.isCanceled()&&!LegacyDrawerTape.sealed(level,pos),"Shift-empty-hand unseal failed");actor.setShiftKeyDown(false);
            }
            ProfileManager.get().makeOrUpdate(actor.getUUID(),"TapeCheck");var participant=new ParticipantInfo(actor.getUUID());var quest=QuestDatabase.INSTANCE.getValue(16385);var task=(TaskRetrieval)quest.getTasks().getValue(257);
            var worn=new ItemStack(tape);worn.setDamageValue(4);actor.getInventory().add(worn);task.onInventoryChange(participant,257,16385,new funwayguy.bq_standard.bootstrap.forge.events.InvoProxy(actor.getInventory()));task.detect(participant,257,16385);
            require(participant.PLAYER.progress.getTaskData(16385,257).getBoolean("isComplete")&&actor.getInventory().countItem(tape)==1,"Worn tape fails quest or is consumed");
        }finally{
            level.setBlock(pos,Blocks.AIR.defaultBlockState(),3);level.setBlock(place,Blocks.AIR.defaultBlockState(),3);level.setBlock(place.below(),Blocks.AIR.defaultBlockState(),3);cleanDrops(level,pos);
        }
        report.addProperty("legacy_drawer_tape_native_default_states_checked",definitions);
        report.addProperty("legacy_drawer_tape_real_break_place_inventory_roundtrips",roundtrips);
        report.addProperty("legacy_drawer_tape_automation_ownership_unseal_and_wear_checked",true);
        report.addProperty("legacy_drawer_tape_unsealed_contents_conserved",4097);
        report.addProperty("legacy_drawer_tape_worn_quest_nonconsume_checked",true);
    }
}
