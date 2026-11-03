package com.ntnh.ntnhcore;

import com.hbm.api.energymk2.IEnergyConnectorBlock;
import com.hbm.api.energymk2.IEnergyReceiverMK2;
import com.hbm.interfaces.ILookOverlay;
import com.hbm.lib.ForgeDirection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import java.util.List;

/** The supplied X5806_NTNH HE -> RF converter, adapted to the native HBM graph and Forge Energy.
 * The two buffers, integer conversion, six output faces and LOW HE priority follow the old tile.
 * RF and FE use the same units. This tile is never an HE provider or an FE receiver. */
final class LegacyEnergyConverter {
    static final long HE_CAPACITY=5_000_000L;
    static final int FE_CAPACITY=1_000_000;
    static final ForgeConfigSpec CONFIG;
    static final ForgeConfigSpec.LongValue HE_USED,RF_CREATED;
    static final ForgeConfigSpec.DoubleValue INPUT_DECAY;
    static final DeferredRegister<BlockEntityType<?>> TILES=DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES,"ntnhcore");
    static RegistryObject<Block> BLOCK;
    static RegistryObject<Item> ITEM;
    static RegistryObject<BlockEntityType<Converter>> TYPE;
    static {
        var b=new ForgeConfigSpec.Builder();
        b.comment("Ported from the original NTNH hbmMachines.json / HEToRFConverter.",
            "Defaults: 5 HE -> 1 RF/FE; no HE decay. Existing native HBM FE bridges are independent.",
            "Changes require restarting the game/server.").push("HEToRFConverter");
        HE_USED=b.defineInRange("HE_Used",5L,1L,HE_CAPACITY);
        RF_CREATED=b.defineInRange("RF_Created",1L,1L,(long)FE_CAPACITY);
        INPUT_DECAY=b.defineInRange("inputDecay",0.0D,0.0D,1.0D);
        b.pop();CONFIG=b.build();
    }
    static void register(DeferredRegister<Block> blocks,DeferredRegister<Item> items,IEventBus bus){
        BLOCK=blocks.register("machine_converter_he_rf",ConverterBlock::new);
        ITEM=items.register("machine_converter_he_rf",()->new BlockItem(BLOCK.get(),new Item.Properties()){
            @Override public void appendHoverText(ItemStack stack,Level level,List<Component> lines,TooltipFlag flag){
                lines.add(Component.translatable("ntnhcore.converter.ratio",HE_USED.get(),RF_CREATED.get()));
                lines.add(Component.translatable("ntnhcore.converter.buffers",HE_CAPACITY,FE_CAPACITY));
                lines.add(Component.translatable("ntnhcore.converter.direction"));
            }
        });
        TYPE=TILES.register("machine_converter_he_rf",()->BlockEntityType.Builder.of(Converter::new,BLOCK.get()).build(null));
        TILES.register(bus);
    }
    private static final class ConverterBlock extends Block implements EntityBlock,IEnergyConnectorBlock,ILookOverlay {
        ConverterBlock(){super(BlockBehaviour.Properties.of().strength(5.0F,10.0F).sound(SoundType.METAL).requiresCorrectToolForDrops());}
        @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new Converter(pos,state);}
        @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type){
            return level.isClientSide||type!=TYPE.get()?null:(l,p,s,t)->((Converter)t).tick();
        }
        @Override public boolean canConnect(BlockGetter level,BlockPos pos,ForgeDirection dir){return dir!=ForgeDirection.UNKNOWN;}
        @Override public ILookOverlay.Data getLookOverlay(Level level,BlockPos pos,Player player){
            if(!(level.getBlockEntity(pos) instanceof Converter tile))return null;
            return new ILookOverlay.Data(getName().getString(),0xffff00,0x404000,List.of(
                "§a-> §r"+tile.getPower()+" / "+HE_CAPACITY+" HE",
                "§c<- §r"+tile.energy.getEnergyStored()+" / "+FE_CAPACITY+" RF / FE"));
        }
    }
    static final class Converter extends BlockEntity implements IEnergyReceiverMK2 {
        private long power;
        private int fe;
        private long sentPower=-1;
        private int sentFe=-1;
        final IEnergyStorage energy=new IEnergyStorage(){
            @Override public int receiveEnergy(int amount,boolean simulate){return 0;}
            @Override public int extractEnergy(int amount,boolean simulate){
                int take=Math.min(Math.max(0,amount),fe);
                if(!simulate&&take>0){fe-=take;setChanged();}return take;
            }
            @Override public int getEnergyStored(){return fe;}
            @Override public int getMaxEnergyStored(){return FE_CAPACITY;}
            @Override public boolean canExtract(){return true;}
            @Override public boolean canReceive(){return false;}
        };
        private LazyOptional<IEnergyStorage> energyCap=LazyOptional.of(()->energy);
        Converter(BlockPos pos,BlockState state){super(TYPE.get(),pos,state);}
        @Override public long getPower(){return power;}
        @Override public long getMaxPower(){return HE_CAPACITY;}
        @Override public void setPower(long value){long next=Math.max(0,Math.min(HE_CAPACITY,value));if(next!=power){power=next;setChanged();}}
        @Override public long transferPower(long amount,boolean simulate){
            if(amount<=0)return amount;
            long take=Math.min(amount,HE_CAPACITY-power);if(!simulate)setPower(power+take);return amount-take;
        }
        @Override public ConnectionPriority getPriority(){return ConnectionPriority.LOW;}
        @Override public boolean isLoaded(){return level!=null&&!isRemoved()&&level.hasChunkAt(worldPosition);}
        @Override public <T> LazyOptional<T> getCapability(Capability<T> capability,Direction side){
            return capability==ForgeCapabilities.ENERGY?energyCap.cast():super.getCapability(capability,side);
        }
        @Override public void invalidateCaps(){super.invalidateCaps();energyCap.invalidate();}
        @Override public void reviveCaps(){super.reviveCaps();energyCap=LazyOptional.of(()->energy);}
        void tick(){
            if(level==null||level.isClientSide)return;
            long previousPower=power;int previousFe=fe;
            long created=Math.min(FE_CAPACITY-fe,power/HE_USED.get()*RF_CREATED.get());
            power-=created*HE_USED.get()/RF_CREATED.get();fe+=(int)created;
            if(power>0)power=(long)(power*(1.0D-INPUT_DECAY.get()));
            // The legacy order is DOWN, UP, NORTH, SOUTH, WEST, EAST. Only debit what a consumer accepted.
            for(Direction side:Direction.values()){
                if(fe==0)break;BlockPos neighbor=worldPosition.relative(side);
                if(!level.hasChunkAt(neighbor))continue;
                BlockEntity target=level.getBlockEntity(neighbor);if(target==null||target.isRemoved())continue;
                var output=target.getCapability(ForgeCapabilities.ENERGY,side.getOpposite());
                if(output.isPresent()){
                    var receiver=output.orElseThrow(IllegalStateException::new);
                    if(receiver.canReceive())fe-=Math.max(0,Math.min(fe,receiver.receiveEnergy(fe,false)));
                }
            }
            if(previousPower!=power||previousFe!=fe)setChanged();
            if(level.getGameTime()%15==0&&(sentPower!=power||sentFe!=fe)){
                sentPower=power;sentFe=fe;level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),Block.UPDATE_CLIENTS);
            }
        }
        @Override protected void saveAdditional(CompoundTag tag){super.saveAdditional(tag);tag.putLong("power",power);tag.putInt("Energy",fe);}
        @Override public void load(CompoundTag tag){super.load(tag);power=Math.max(0,Math.min(HE_CAPACITY,tag.getLong("power")));fe=Math.max(0,Math.min(FE_CAPACITY,tag.getInt("Energy")));}
        @Override public CompoundTag getUpdateTag(){return saveWithoutMetadata();}
        @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
    }
}
