package com.ntnh.ntnhcore;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** NTNH ore distributions adapted to 1.20.1. Cloud noise and Bezier branches are approximated.
 * Every anchor is seeded by its own chunk. Writes stay inside the chunk being decorated,
 * so large deposits do not request neighbour generation or depend on decoration order. */
public final class PackOreFeature extends Feature<NoneFeatureConfiguration> {
    private static final DeferredRegister<Feature<?>> FEATURES=DeferredRegister.create(ForgeRegistries.FEATURES,"ntnhcore");
    private static volatile List<Distribution> distributions;
    public static void bootstrap(IEventBus bus){
        FEATURES.register("legacy_ores",()->new PackOreFeature(false));
        FEATURES.register("legacy_hbm_ores",()->new PackOreFeature(true));FEATURES.register(bus);
    }
    private final boolean hbm;
    PackOreFeature(boolean hbm){super(NoneFeatureConfiguration.CODEC);this.hbm=hbm;}
    private record Distribution(String name,String dimension,BlockState ore,List<String> biomes,List<String> hosts,
        boolean veins,double frequency,double radius,double radiusRange,double thickness,double thicknessRange,
        double height,double heightRange,double density,double length){}

    private static List<String> strings(JsonArray values){
        var out=new ArrayList<String>();for(var value:values)out.add(value.getAsString());return List.copyOf(out);
    }
    private static double number(JsonObject row,String name){return row.get(name).getAsDouble();}
    private static List<Distribution> definitions(){
        if(distributions!=null)return distributions;
        synchronized(PackOreFeature.class){
            if(distributions!=null)return distributions;
            var out=new ArrayList<Distribution>();
            try(var input=PackOreFeature.class.getResourceAsStream("/ntnhcore/ore-distributions.json")){
                if(input==null)throw new IllegalStateException("Missing NTNH ore definitions");
                for(var value:JsonParser.parseReader(new InputStreamReader(input,StandardCharsets.UTF_8)).getAsJsonArray()){
                    JsonObject row=value.getAsJsonObject();
                    var id=new ResourceLocation(row.get("ore").getAsString());
                    var block=BuiltInRegistries.BLOCK.getOptional(id).orElseThrow(()->new IllegalStateException("Unknown NTNH ore: "+id));
                    BlockState ore=block.defaultBlockState();
                    if(row.has("properties"))for(var prop:row.getAsJsonObject("properties").entrySet()){
                        var property=block.getStateDefinition().getProperty(prop.getKey());
                        if(property==null)throw new IllegalStateException("Unknown ore property: "+prop.getKey());
                        ore=property(ore,property,prop.getValue().getAsString());
                    }
                    out.add(new Distribution(row.get("name").getAsString(),row.get("dimension").getAsString(),ore,
                        strings(row.getAsJsonArray("biomes")),strings(row.getAsJsonArray("hosts")),row.get("veins").getAsBoolean(),
                        number(row,"frequency"),number(row,"radius"),number(row,"radius_range"),number(row,"thickness"),
                        number(row,"thickness_range"),number(row,"height"),number(row,"height_range"),number(row,"density"),number(row,"length")));
                }
            }catch(java.io.IOException error){throw new IllegalStateException(error);}
            distributions=List.copyOf(out);return distributions;
        }
    }
    private static double sample(Random random,double mean,double range){return mean+(random.nextDouble()*2-1)*range;}
    private static <T extends Comparable<T>> BlockState property(BlockState state,net.minecraft.world.level.block.state.properties.Property<T> property,String value){
        return state.setValue(property,property.getValue(value).orElseThrow());
    }

    @Override public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context){
        WorldGenLevel level=context.level();
        int minX=Math.floorDiv(context.origin().getX(),16)*16,minZ=Math.floorDiv(context.origin().getZ(),16)*16;
        if(hbm)placeHbm(level,level.getChunk(minX>>4,minZ>>4));
        else generate(level,minX,minZ,false);
        return true;
    }
    static void placeHbm(WorldGenLevel level,net.minecraft.world.level.chunk.ChunkAccess chunk){
        LegacyHbmOreCleanup.clean(level,chunk);
        generate(level,chunk.getPos().getMinBlockX(),chunk.getPos().getMinBlockZ(),true);
        LegacyHbmOreCleanup.pass(level);
    }
    private static void generate(WorldGenLevel level,int minX,int minZ,boolean hbm){
        String dimension=level.getLevel().dimension().location().toString();
        long worldSeed=level.getSeed();
        for(var d:definitions()){
            if(!d.dimension.equals(dimension))continue;
            String ns=BuiltInRegistries.BLOCK.getKey(d.ore.getBlock()).getNamespace();
            if((ns.equals("hbm")||ns.equals("ntmspace"))!=hbm)continue;
            int reach=(int)Math.ceil(Math.max(d.radius+d.radiusRange,d.veins?d.length:0)/16)+1;
            // Source settings are bounded during conversion; never turn malformed data into unbounded work.
            reach=Math.min(18,Math.max(1,reach));
            for(int ax=(minX>>4)-reach;ax<=(minX>>4)+reach;ax++)for(int az=(minZ>>4)-reach;az<=(minZ>>4)+reach;az++){
                var random=new Random(worldSeed^((long)ax*341873128712L)^((long)az*132897987541L)^d.name.hashCode());
                int attempts=(int)d.frequency+(random.nextDouble()<d.frequency%1?1:0);
                for(int attempt=0;attempt<attempts;attempt++){
                    double x=ax*16+random.nextInt(16),z=az*16+random.nextInt(16),y=sample(random,d.height,d.heightRange);
                    if(!d.biomes.isEmpty()){
                        // Check only the current chunk. Cross-chunk biome lookups could trigger cascading generation.
                        var biome=level.getBiome(new BlockPos(minX+8,(int)y,minZ+8)).unwrapKey();
                        if(biome.isEmpty()||!d.biomes.contains(biome.get().location().toString()))continue;
                    }
                    double radius=Math.max(1,sample(random,d.radius,d.radiusRange));
                    double thickness=Math.max(1,sample(random,d.thickness,d.thicknessRange));
                    if(!d.veins){ellipsoid(level,d,minX,minZ,x,y,z,radius,thickness,worldSeed);continue;}
                    // Three gently bending branches retain the legacy long-vein scale without cross-chunk writes.
                    for(int branch=0;branch<3;branch++){
                        double angle=random.nextDouble()*Math.PI*2,px=x,pz=z,py=y;
                        int segments=Math.max(1,(int)(d.length/3));
                        for(int step=0;step<segments;step++){
                            angle+=(random.nextDouble()-.5)*.18;
                            px+=Math.cos(angle)*3;pz+=Math.sin(angle)*3;py+=(random.nextDouble()-.5)*.6;
                            ellipsoid(level,d,minX,minZ,px,py,pz,2.4,1.8,worldSeed);
                        }
                    }
                }
            }
        }
    }
    private static void ellipsoid(WorldGenLevel level,Distribution d,int minX,int minZ,double cx,double cy,double cz,double radius,double thickness,long seed){
        if(cx+radius<minX||cx-radius>minX+15||cz+radius<minZ||cz-radius>minZ+15)return;
        int x0=Math.max(minX,(int)Math.floor(cx-radius)),x1=Math.min(minX+15,(int)Math.ceil(cx+radius));
        int z0=Math.max(minZ,(int)Math.floor(cz-radius)),z1=Math.min(minZ+15,(int)Math.ceil(cz+radius));
        int y0=Math.max(level.getMinBuildHeight()+1,(int)Math.floor(cy-thickness));
        int y1=Math.min(level.getMaxBuildHeight()-1,(int)Math.ceil(cy+thickness));
        var pos=new BlockPos.MutableBlockPos();
        for(int x=x0;x<=x1;x++)for(int z=z0;z<=z1;z++){
            double horizontal=((x+.5-cx)*(x+.5-cx)+(z+.5-cz)*(z+.5-cz))/(radius*radius);
            if(horizontal>1)continue;
            for(int y=y0;y<=y1;y++){
                double height=(y+.5-cy)/thickness;if(horizontal+height*height>1)continue;
                long hash=seed^((long)x*73428767L)^((long)y*912931L)^((long)z*438289L)^d.name.hashCode();
                hash=(hash^(hash>>>33))*0xff51afd7ed558ccdL;
                if((hash&0xFFFFFF)/(double)0x1000000>=d.density)continue;
                pos.set(x,y,z);var existing=level.getBlockState(pos);
                String id=BuiltInRegistries.BLOCK.getKey(existing.getBlock()).toString();
                boolean host=d.hosts.contains(id)||(d.hosts.contains("#stone")&&(existing.is(BlockTags.STONE_ORE_REPLACEABLES)||existing.is(BlockTags.DEEPSLATE_ORE_REPLACEABLES)));
                if(host&&level.setBlock(pos,d.ore,2|16))LegacyHbmOreCleanup.placed(level,pos,d.ore);
            }
        }
    }
}
