package com.ntnh.ntnhcore;

import java.awt.Font;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Original font-container encoder. Glyph inputs are read locally at runtime, never bundled. */
final class RuntimePixelFont {
    record Pixel(int x, int y) {}
    record Face(List<Pixel> pixels, int advance) {}
    private record Outline(byte[] bytes, int points, int contours) {}
    private interface Write { void write(DataOutputStream out) throws Exception; }
    private static byte[] data(Write action) throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var out = new DataOutputStream(bytes)) { action.write(out); }
        return bytes.toByteArray();
    }
    static Font create(Map<Integer, Face> input) throws Exception {
        return Font.createFont(Font.TRUETYPE_FONT, new ByteArrayInputStream(encode(input))).deriveFont(8f);
    }
    static byte[] encode(Map<Integer, Face> input) throws Exception {
        var sorted = new TreeMap<Integer,Face>(input);
        sorted.putIfAbsent(32, new Face(List.of(), 800));
        sorted.keySet().removeIf(cp -> cp < 32 || cp >= 65535 || cp >= 0xd800 && cp <= 0xdfff);
        if (sorted.size() > 6000) throw new IllegalArgumentException("Too many book glyphs");
        var faces = new ArrayList<Face>(); faces.add(new Face(List.of(), 800));
        var cmap = new TreeMap<Integer,Integer>();
        for (var entry : sorted.entrySet()) { cmap.put(entry.getKey(), faces.size()); faces.add(entry.getValue()); }
        var outlines = new ArrayList<Outline>();
        for (var face : faces) outlines.add(outline(face));
        int maxPoints = outlines.stream().mapToInt(Outline::points).max().orElse(0);
        int maxContours = outlines.stream().mapToInt(Outline::contours).max().orElse(0);
        int[] offsets = new int[faces.size()+1];
        byte[] glyf = data(out -> {
            int offset=0;
            for(int i=0;i<outlines.size();i++) {
                offsets[i]=offset; byte[] b=outlines.get(i).bytes();out.write(b);offset+=b.length;
                while((offset&3)!=0){out.writeByte(0);offset++;}
            }
            offsets[outlines.size()]=offset;
        });
        var tables = new TreeMap<String,byte[]>();
        tables.put("glyf",glyf);
        tables.put("loca",data(out->{for(int n:offsets)out.writeInt(n);}));
        tables.put("hmtx",data(out->{for(var f:faces){out.writeShort(f.advance());out.writeShort(f.pixels().stream().mapToInt(Pixel::x).min().orElse(0)*100);}}));
        tables.put("head",data(out->{
            out.writeInt(0x00010000);out.writeInt(0x00010000);out.writeInt(0);out.writeInt(0x5f0f3cf5);
            out.writeShort(3);out.writeShort(1600);out.writeLong(0xe0000000L);out.writeLong(0xe0000000L);
            out.writeShort(0);out.writeShort(-400);out.writeShort(3200);out.writeShort(2000);
            out.writeShort(0);out.writeShort(8);out.writeShort(2);out.writeShort(1);out.writeShort(0);
        }));
        tables.put("hhea",data(out->{
            out.writeInt(0x00010000);out.writeShort(1600);out.writeShort(-200);out.writeShort(200);
            out.writeShort(faces.stream().mapToInt(Face::advance).max().orElse(1600));
            out.writeShort(0);out.writeShort(0);out.writeShort(3200);out.writeShort(1);out.writeShort(0);
            for(int i=0;i<5;i++)out.writeShort(0);out.writeShort(0);out.writeShort(faces.size());
        }));
        tables.put("maxp",data(out->{
            out.writeInt(0x00010000);out.writeShort(faces.size());out.writeShort(maxPoints);out.writeShort(maxContours);
            out.writeShort(0);out.writeShort(0);out.writeShort(2);for(int i=0;i<8;i++)out.writeShort(0);
        }));
        tables.put("OS/2",data(out->{
            out.writeShort(0);out.writeShort(1200);out.writeShort(400);out.writeShort(5);out.writeShort(0);
            for(int i=0;i<11;i++)out.writeShort(0);out.write(new byte[10]);
            for(int i=0;i<4;i++)out.writeInt(0);out.writeBytes("NTSD");
            out.writeShort(64);out.writeShort(cmap.firstKey());out.writeShort(cmap.lastKey());
            out.writeShort(1600);out.writeShort(-200);out.writeShort(200);out.writeShort(1600);out.writeShort(200);
        }));
        tables.put("post",data(out->{out.writeInt(0x00030000);for(int i=0;i<7;i++)out.writeInt(0);}));
        tables.put("cmap",data(out->{
            int segments=cmap.size()+1, power=Integer.highestOneBit(segments);
            out.writeShort(0);out.writeShort(1);out.writeShort(3);out.writeShort(1);out.writeInt(12);
            out.writeShort(4);out.writeShort(16+segments*8);out.writeShort(0);out.writeShort(segments*2);
            out.writeShort(power*2);out.writeShort(Integer.numberOfTrailingZeros(power));out.writeShort(segments*2-power*2);
            for(int cp:cmap.keySet())out.writeShort(cp);out.writeShort(65535);out.writeShort(0);
            for(int cp:cmap.keySet())out.writeShort(cp);out.writeShort(65535);
            for(var e:cmap.entrySet())out.writeShort(e.getValue()-e.getKey());out.writeShort(1);
            for(int i=0;i<segments;i++)out.writeShort(0);
        }));
        var names = new LinkedHashMap<Integer,String>();
        names.put(1,"NTSD Runtime Book");names.put(2,"Regular");names.put(3,"NTSD runtime resource adapter 1");
        names.put(4,"NTSD Runtime Book");names.put(5,"Version 1.0");names.put(6,"NTSDRuntimeBook");
        names.put(13,"Generated in memory from locally installed game resources; not included in the mod archive.");
        tables.put("name",data(out->{
            int start=6+names.size()*12,offset=0;out.writeShort(0);out.writeShort(names.size());out.writeShort(start);
            for(var e:names.entrySet()) {
                byte[] b=e.getValue().getBytes(StandardCharsets.UTF_16BE);
                out.writeShort(3);out.writeShort(1);out.writeShort(0x0409);out.writeShort(e.getKey());
                out.writeShort(b.length);out.writeShort(offset);offset+=b.length;
            }
            for(String s:names.values())out.write(s.getBytes(StandardCharsets.UTF_16BE));
        }));
        int count=tables.size(),start=12+count*16,power=Integer.highestOneBit(count);
        var positions=new TreeMap<String,Integer>();int offset=start;
        for(var e:tables.entrySet()){positions.put(e.getKey(),offset);offset+=(e.getValue().length+3)&~3;}
        byte[] result=data(out->{
            out.writeInt(0x00010000);out.writeShort(count);out.writeShort(power*16);
            out.writeShort(Integer.numberOfTrailingZeros(power));out.writeShort(count*16-power*16);
            for(var e:tables.entrySet()){
                out.writeBytes(e.getKey());out.writeInt((int)checksum(e.getValue()));
                out.writeInt(positions.get(e.getKey()));out.writeInt(e.getValue().length);
            }
            for(var e:tables.entrySet()){
                byte[] b=e.getValue();out.write(b);for(int i=b.length;i%4!=0;i++)out.writeByte(0);
            }
        });
        ByteBuffer.wrap(result).order(ByteOrder.BIG_ENDIAN).putInt(positions.get("head")+8,(int)(0xb1b0afbaL-checksum(result)));
        return result;
    }
    private static long checksum(byte[] data){
        long sum=0;
        for(int i=0;i<data.length;i+=4){long n=0;for(int j=0;j<4;j++)n=n<<8|(i+j<data.length?data[i+j]&255:0);sum=(sum+n)&0xffffffffL;}
        return sum;
    }
    private static Outline outline(Face face) throws Exception {
        int contours=face.pixels().size(), points=contours*4;
        if(points>16000)throw new IllegalArgumentException("Oversized glyph");
        if(contours==0)return new Outline(new byte[0],0,0);
        int minX=face.pixels().stream().mapToInt(Pixel::x).min().orElse(0)*100;
        int maxX=face.pixels().stream().mapToInt(Pixel::x).max().orElse(0)*100+100;
        int minY=face.pixels().stream().mapToInt(p->13-p.y()).min().orElse(0)*100;
        int maxY=face.pixels().stream().mapToInt(p->13-p.y()).max().orElse(0)*100+100;
        int[] xs=new int[points],ys=new int[points];int at=0;
        for(var p:face.pixels()){
            int x=p.x()*100,y=(13-p.y())*100;
            for(int[] xy:new int[][]{{x,y},{x+100,y},{x+100,y+100},{x,y+100}}){xs[at]=xy[0];ys[at++]=xy[1];}
        }
        byte[] bytes=data(out->{
            out.writeShort(contours);out.writeShort(minX);out.writeShort(minY);out.writeShort(maxX);out.writeShort(maxY);
            for(int i=0;i<contours;i++)out.writeShort(i*4+3);out.writeShort(0);
            for(int i=0;i<points;i++)out.writeByte(1);
            int last=0;for(int x:xs){out.writeShort(x-last);last=x;}last=0;
            for(int y:ys){out.writeShort(y-last);last=y;}
        });
        return new Outline(bytes,points,contours);
    }
}
