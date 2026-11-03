package com.ntnh.ntnhcore;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.leclowndu93150.chisel.ChiselConfig;
import com.leclowndu93150.chisel.item.ItemChisel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/** Missing legacy tool tiers. Native Chisel implements the menus, modes, world carving and packets.
 * The old NTNH config supplies durability, extra attack damage and repair ingredients. */
final class LegacyChisels {
    static RegistryObject<Item> OBSIDIAN,NETHER_STAR;
    static void register(DeferredRegister<Item> items){
        OBSIDIAN=items.register("obsidian_chisel",()->new RestoredChisel(2500,4,Items.OBSIDIAN));
        NETHER_STAR=items.register("nether_star_chisel",()->new RestoredChisel(32767,6,Items.NETHER_STAR));
    }
    private static final class RestoredChisel extends ItemChisel {
        private final int durability;
        private final Item repair;
        private final Multimap<Attribute,AttributeModifier> attributes;
        RestoredChisel(int durability,int attack,Item repair){
            // Both old tiers support world carving and the regular chisel modes.
            super(ChiselType.DIAMOND,new Item.Properties().stacksTo(1));
            this.durability=durability;this.repair=repair;
            attributes=ImmutableMultimap.of(Attributes.ATTACK_DAMAGE,
                new AttributeModifier(BASE_ATTACK_DAMAGE_UUID,"Chisel Damage",attack,AttributeModifier.Operation.ADDITION));
        }
        @Override public int getMaxDamage(ItemStack stack){return ChiselConfig.allowChiselDamage?durability:0;}
        @Override public boolean isValidRepairItem(ItemStack tool,ItemStack material){return material.is(repair);}
        @Override public Multimap<Attribute,AttributeModifier> getDefaultAttributeModifiers(EquipmentSlot slot){
            return slot==EquipmentSlot.MAINHAND?attributes:ImmutableMultimap.of();
        }
        @Override public boolean hurtEnemy(ItemStack stack,LivingEntity target,LivingEntity attacker){
            stack.hurtAndBreak(1,attacker,e->e.broadcastBreakEvent(EquipmentSlot.MAINHAND));return false;
        }
    }
}
