package com.ntnh.ntnhcore;

import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** Pack-specific replacements for the legacy reward items required by NTNH quests. */
public final class PackContent {
    private static final DeferredRegister<Item> ITEMS=DeferredRegister.create(ForgeRegistries.ITEMS,"ntnhcore");
    private static final DeferredRegister<Block> BLOCKS=DeferredRegister.create(ForgeRegistries.BLOCKS,"ntnhcore");
    static final net.minecraftforge.registries.RegistryObject<Item> QUEST_BOOK=ITEMS.register("quest_book",()->new Item(new Item.Properties().stacksTo(1)){
        @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){
            if(level.isClientSide)QuestBookClient.open();
            return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand),level.isClientSide);
        }
    });

    public static void bootstrap(IEventBus bus) {
        LegacyBackpacks.register(bus);
        LegacyFlintTools.register(ITEMS);
        LegacyChisels.register(ITEMS);
        LegacyDrawerTape.register(ITEMS);
        LegacyCertusOres.register(BLOCKS,ITEMS);
        LegacyMarkings.register(BLOCKS,ITEMS);
        LegacyEnergyConverter.register(BLOCKS,ITEMS,bus);
        LegacyRadialEngine.register(BLOCKS,ITEMS,bus);
        LegacyRfEnergyConverter.register(BLOCKS,ITEMS,bus);
        var carpet=BLOCKS.register("microsoft_carpet",()->new net.minecraft.world.level.block.CarpetBlock(BlockBehaviour.Properties.copy(Blocks.WHITE_CARPET).strength(0.1F)));
        ITEMS.register("microsoft_carpet",()->new BlockItem(carpet.get(),new Item.Properties()));
        for (int tier=0;tier<=6;tier++) {
            int selected=tier;
            ITEMS.register("lootbag_"+tier,()->new LootBag(selected));
        }
        ITEMS.register("end_portal",()->new BlockItem(Blocks.END_PORTAL,new Item.Properties()));
        try (var input=PackContent.class.getResourceAsStream("/ntnhcore/trophies.json")) {
            if (input==null) throw new IllegalStateException("Missing trophy definitions");
            for (var element:JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonArray()) {
                String name=element.getAsString();
                var block=BLOCKS.register("trophy_"+name,()->new TrophyBlock());
                ITEMS.register("trophy_"+name,()->new BlockItem(block.get(),new Item.Properties()));
            }
        } catch (java.io.IOException error) {throw new IllegalStateException(error);}
        BLOCKS.register(bus);ITEMS.register(bus);
    }

    private static final class TrophyBlock extends Block {
        private static final VoxelShape SHAPE=Shapes.or(Block.box(3,0,3,13,3,13),Block.box(6,3,6,10,10,10),Block.box(3,10,3,13,15,13));
        TrophyBlock(){super(BlockBehaviour.Properties.copy(Blocks.GOLD_BLOCK).strength(1).noOcclusion());}
        @Override public VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return SHAPE;}
    }

    private static final class LootBag extends Item {
        private final int tier;
        LootBag(int tier){super(new Item.Properties().stacksTo(16));this.tier=tier;}
        @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){
            ItemStack stack=player.getItemInHand(hand);
            if (level instanceof ServerLevel server) {
                int fortune=Math.min(3,EnchantmentHelper.getItemEnchantmentLevel(Enchantments.BLOCK_FORTUNE,stack));
                ResourceLocation id=new ResourceLocation("ntnhcore","bags/"+tier+"_"+fortune);
                var drops=LegacyBagDrops.open(server,player,tier,fortune);
                if (drops.isEmpty()) {
                    player.displayClientMessage(Component.translatable("ntnhcore.message.loot_unavailable",id.toString()),true);
                    return InteractionResultHolder.fail(stack);
                }
                for (var drop:drops) if (!player.getInventory().add(drop)) player.drop(drop,false);
                if (!player.getAbilities().instabuild) stack.shrink(1);
            }
            return InteractionResultHolder.sidedSuccess(stack,level.isClientSide);
        }
    }
}
