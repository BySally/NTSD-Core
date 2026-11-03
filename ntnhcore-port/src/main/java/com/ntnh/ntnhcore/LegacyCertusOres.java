package com.ntnh.ntnhcore;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.DeferredRegister;

/** The two removed AE2 1.7 ore blocks, using current AE2 crystals as their drops.
 * Reference: the supplied rv3-beta-1019 GTNH OreQuartz/OreQuartzCharged classes.
 * Loot tables preserve uniform 1..2 base drops, ore Fortune, and Silk Touch.
 * They do not replace the current AE2 budding-quartz mechanics. */
final class LegacyCertusOres {
    static void register(DeferredRegister<Block> blocks,DeferredRegister<Item> items){
        for(boolean charged:new boolean[]{false,true}){
            String name=charged?"charged_certus_quartz_ore":"certus_quartz_ore";
            var block=blocks.register(name,()->new CertusOre(charged));
            items.register(name,()->new BlockItem(block.get(),new Item.Properties()));
        }
    }
    private static final class CertusOre extends DropExperienceBlock {
        private final boolean charged;
        CertusOre(boolean charged){
            // AEBaseBlock in the supplied legacy jar uses pickaxe level 0.
            super(BlockBehaviour.Properties.copy(Blocks.COAL_ORE).strength(3.0F,5.0F),UniformInt.of(2,5));
            this.charged=charged;
        }
        @Override public void animateTick(BlockState state,Level level,BlockPos pos,RandomSource random){
            if(charged&&random.nextInt(4)==0)level.addParticle(ParticleTypes.ELECTRIC_SPARK,
                pos.getX()+random.nextDouble(),pos.getY()+random.nextDouble(),pos.getZ()+random.nextDouble(),0,0,0);
        }
    }
}
