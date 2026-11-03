package com.ntnh.ntnhcore;

import java.awt.Font;
import funwayguy.betterquesting.client.text.FontAtlas;

/** BQ's standalone AWT font atlas is independent of the Minecraft font resource providers. */
final class LegacyBookFont {
    static void install(){
        try(var stream=LegacyBookFont.class.getResourceAsStream("/assets/ntnhcore/font/PressStart2P-Regular.ttf")){
            if(stream==null)throw new IllegalStateException("Missing licensed NTSD book font");
            FontAtlas.DEFAULT.changeFont(new PixelFace(Font.createFont(Font.TRUETYPE_FONT,stream).deriveFont(8f)));
        }catch(java.io.IOException|java.awt.FontFormatException error){throw new IllegalStateException("Cannot load licensed NTSD book font",error);}
    }
    /** FontAtlas otherwise synthesizes bold at size+1, distorting the pixel grid. */
    private static final class PixelFace extends Font {
        PixelFace(Font source){super(source);}
        @Override public Font deriveFont(int style,float size){return super.deriveFont(Font.PLAIN,getSize2D());}
    }
}
