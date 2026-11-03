package com.ntnh.ntnhcore;

import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.net.URI;

/** Empty defaults: never send users to an invented or original NTNH support address. */
final class SecondDawnLinks {
    static String get(String kind){
        var path=net.minecraftforge.fml.loading.FMLPaths.CONFIGDIR.get().resolve("ntsd-links.json");
        if(!Files.isRegularFile(path))return "";
        try(var reader=Files.newBufferedReader(path,StandardCharsets.UTF_8)){
            var root=JsonParser.parseReader(reader).getAsJsonObject();if(!root.has(kind))return "";
            String value=root.get(kind).getAsString().trim();if(value.isEmpty())return "";
            URI uri=URI.create(value);String host=uri.getHost();
            if(!"https".equalsIgnoreCase(uri.getScheme())||host==null||uri.getUserInfo()!=null)return "";
            boolean valid=kind.equals("github_issues")?host.equalsIgnoreCase("github.com")&&uri.getPath().contains("/issues"):
                host.equalsIgnoreCase("discord.gg")||host.equalsIgnoreCase("discord.com")&&uri.getPath().startsWith("/invite/");
            return valid?value:"";
        }catch(Exception error){return "";}
    }
}
