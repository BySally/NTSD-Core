package com.ntnh.ntnhcore.mixin;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** BQ 4.0.71 otherwise reads UTF-8 translations using the launcher JVM's charset. */
@Mixin(targets="funwayguy.betterquesting.client.text.BQLocale",remap=false)
public abstract class BQLocaleUtf8Mixin {
    @Redirect(method="loadLocale",at=@At(value="NEW",target="(Ljava/io/InputStream;)Ljava/io/InputStreamReader;"),remap=false)
    private InputStreamReader ntnh$utf8(InputStream input){
        return new InputStreamReader(input,StandardCharsets.UTF_8);
    }
}
