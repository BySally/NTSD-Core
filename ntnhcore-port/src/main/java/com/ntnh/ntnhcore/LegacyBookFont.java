package com.ntnh.ntnhcore;

import java.awt.Font;
import funwayguy.betterquesting.client.text.FontAtlas;

/** BQ's standalone AWT font atlas is independent of the Minecraft font resource providers. */
final class LegacyBookFont {
    static void install(){
        try {
            FontAtlas.DEFAULT.changeFont(new PixelFace(InstalledBookGlyphs.create()));
        }catch(Exception error){
            // Keep Better Questing usable if a player's resource pack removes a required provider.
            // Press Start 2P and the disputed legacy font are not bundled or selected as fallbacks.
            org.slf4j.LoggerFactory.getLogger("ntnhcore").error("Cannot create book font from installed resources; retaining Better Questing's font",error);
        }
    }
    /** FontAtlas otherwise synthesizes bold at size+1, distorting the pixel grid. */
    private static final class PixelFace extends Font {
        PixelFace(Font source){super(source);}
        @Override public Font deriveFont(int style,float size){return super.deriveFont(Font.PLAIN,getSize2D());}
    }
}
