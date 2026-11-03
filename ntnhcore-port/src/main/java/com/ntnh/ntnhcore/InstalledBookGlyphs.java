package com.ntnh.ntnhcore;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import javax.imageio.ImageIO;
import java.awt.Font;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.ZipInputStream;

/** Uses the player's installed 1.20.1 font resources; no Mojang glyph file is redistributed. */
final class InstalledBookGlyphs {
    private final ResourceManager resources;
    private final Map<Integer,RuntimePixelFont.Face> faces=new TreeMap<>();
    private final Set<String> visited=new HashSet<>();
    private InstalledBookGlyphs(ResourceManager resources){this.resources=resources;}
    static Font create() throws Exception {
        var source=new InstalledBookGlyphs(Minecraft.getInstance().getResourceManager());
        source.load(new ResourceLocation("minecraft","include/default"),0);
        source.load(new ResourceLocation("minecraft","include/unifont"),0);
        source.faces.put(32,new RuntimePixelFont.Face(java.util.List.of(),800));
        for(int cp:new int[]{'A','g','0',0x410,0x430,0x451})
            if(!source.faces.containsKey(cp))throw new IllegalStateException("Installed font missing U+"+Integer.toHexString(cp));
        org.slf4j.LoggerFactory.getLogger("ntnhcore").info("NTSD book font uses {} installed glyphs; no bundled pixel font",source.faces.size());
        return RuntimePixelFont.create(source.faces);
    }
    private static boolean wanted(int cp){return cp>=32 && (cp<0x530 || cp>=0x2000&&cp<0x2800);}
    private void load(ResourceLocation id,int depth) throws Exception {
        if(depth>8)throw new IllegalStateException("Cyclic font reference");
        if(!visited.add(id.toString()))return;
        var file=new ResourceLocation(id.getNamespace(),"font/"+id.getPath()+".json");
        var resource=resources.getResource(file);if(resource.isEmpty())return;
        JsonObject json;
        try(var reader=resource.get().openAsReader()){json=JsonParser.parseReader(reader).getAsJsonObject();}
        for(var value:json.getAsJsonArray("providers")) {
            var p=value.getAsJsonObject();String type=p.get("type").getAsString().replace("minecraft:","");
            switch(type){
                case "reference" -> load(new ResourceLocation(p.get("id").getAsString()),depth+1);
                case "bitmap" -> bitmap(p);
                case "unihex" -> unihex(p);
                default -> { /* Spaces are installed explicitly; unrelated providers stay in Minecraft. */ }
            }
        }
    }
    private void bitmap(JsonObject p) throws Exception {
        var id=new ResourceLocation(p.get("file").getAsString());
        var file=new ResourceLocation(id.getNamespace(),"textures/"+id.getPath());
        java.awt.image.BufferedImage image;
        try(var stream=resources.getResource(file).orElseThrow().open()){image=ImageIO.read(stream);}
        if(image==null)throw new IllegalStateException("Unreadable installed bitmap "+file);
        var rows=p.getAsJsonArray("chars");int columns=rows.get(0).getAsString().codePointCount(0,rows.get(0).getAsString().length());
        int cw=image.getWidth()/columns,ch=image.getHeight()/rows.size();
        int height=p.has("height")?p.get("height").getAsInt():8,ascent=p.get("ascent").getAsInt();
        if(cw<1||ch<1||cw>128||ch>128)throw new IllegalStateException("Unsupported installed font cell");
        for(int row=0;row<rows.size();row++){
            int[] cps=rows.get(row).getAsString().codePoints().toArray();
            for(int col=0;col<cps.length;col++){
                int cp=cps[col];if(!wanted(cp)||faces.containsKey(cp)||cp==32)continue;
                int left=cw,right=-1,top=ch,bottom=-1;
                for(int y=0;y<ch;y++)for(int x=0;x<cw;x++)if((image.getRGB(col*cw+x,row*ch+y)>>>24)>=128){
                    left=Math.min(left,x);right=Math.max(right,x);top=Math.min(top,y);bottom=Math.max(bottom,y);
                }
                if(right<left)continue;
                // Preserve 8px bitmap grids. Taller accented cells fit in the legacy 8px book cell.
                double scale=height/(double)ch*2;
                int yStart=(int)Math.round((7-ascent)*2+top*scale);
                int yEnd=(int)Math.round((7-ascent)*2+(bottom+1)*scale);
                boolean fit=yStart<0||yEnd>16;
                double yScale=fit?Math.min(scale,12.0/(bottom-top+1)):scale;
                int offset=fit?2-(int)Math.round(top*yScale):(7-ascent)*2;
                if(cp=='`')offset+=2;
                if(cp==0xb0||cp==0xb2||cp==0xb3)offset+=2;
                if(cp==0x2264||cp==0x2265){yScale=12.0/(bottom-top+1);offset=2-(int)Math.round(top*yScale);}
                int xPadding=cp==0x2116||cp==0x2122||cp>=0x2190&&cp<=0x2193||cp==0x221e||cp==0x2264||cp==0x2265||cp==0x26a0?2:0;
                var pixels=new ArrayList<RuntimePixelFont.Pixel>();
                for(int y=top;y<=bottom;y++)for(int x=left;x<=right;x++){
                    if((image.getRGB(col*cw+x,row*ch+y)>>>24)<128)continue;
                    int x0=(int)Math.round((x-left)*scale),x1=(int)Math.round((x-left+1)*scale);
                    int y0=offset+(int)Math.round(y*yScale),y1=offset+(int)Math.round((y+1)*yScale);
                    for(int yy=Math.max(0,y0);yy<Math.min(16,y1);yy++)for(int xx=x0;xx<x1;xx++)pixels.add(new RuntimePixelFont.Pixel(xx+xPadding,yy));
                }
                int advance=(int)Math.round((right-left+1)*scale+2)*100;
                if(cp==0x221e)advance+=200;
                faces.put(cp,new RuntimePixelFont.Face(pixels,advance));
            }
        }
    }
    private void unihex(JsonObject p) throws Exception {
        var id=new ResourceLocation(p.get("hex_file").getAsString());
        try(var stream=resources.getResource(id).orElseThrow().open();var zip=new ZipInputStream(stream)){
            java.util.zip.ZipEntry entry;
            while((entry=zip.getNextEntry())!=null){
                if(entry.isDirectory()||!entry.getName().endsWith(".hex")){zip.closeEntry();continue;}
                var reader=new BufferedReader(new InputStreamReader(zip,StandardCharsets.US_ASCII));
                String line;
                while((line=reader.readLine())!=null){
                    int colon=line.indexOf(':');if(colon<4||colon>6)continue;
                    int cp=Integer.parseInt(line.substring(0,colon),16);
                    if(!wanted(cp)||faces.containsKey(cp))continue;
                    String hex=line.substring(colon+1).trim();int width=hex.length()/4;
                    if(width<8||width>32||hex.length()!=width*4)continue;
                    long[] values=new long[16];int left=width,right=-1;
                    for(int y=0;y<16;y++){
                        values[y]=Long.parseLong(hex.substring(y*width/4,(y+1)*width/4),16);
                        for(int x=0;x<width;x++)if((values[y]&(1L<<(width-x-1)))!=0){left=Math.min(left,x);right=Math.max(right,x);}
                    }
                    if(right<left)continue;
                    int top=16,bottom=-1;
                    for(int y=0;y<16;y++)if(values[y]!=0){top=Math.min(top,y);bottom=y;}
                    double scale=Math.min(1,12.0/(bottom-top+1));
                    int yOffset=top<2?2:Math.min(top,14-(int)Math.ceil((bottom-top+1)*scale));
                    var pixels=new ArrayList<RuntimePixelFont.Pixel>();
                    for(int y=top;y<=bottom;y++)for(int x=left;x<=right;x++)if((values[y]&(1L<<(width-x-1)))!=0){
                        int yy=yOffset+(int)Math.round((y-top)*scale);
                        pixels.add(new RuntimePixelFont.Pixel(x-left+2,yy));
                    }
                    // One local pixel of left bearing avoids AWT's half-grid edge clipping.
                    faces.put(cp,new RuntimePixelFont.Face(pixels,(right-left+5)*100));
                }
                zip.closeEntry();
            }
        }
    }
}
