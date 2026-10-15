package local.pixelmon.backport;

import com.google.gson.*;
import java.util.Map;
import net.minecraft.util.ResourceLocation;
import com.pixelmonmod.pixelmon.api.pokemon.species.Stats;
import com.pixelmonmod.pixelmon.battles.controller.participants.PixelmonWrapper;

/** An earned permanent form, using the legacy Ash model and ability unchanged. */
public final class PermanentAsh {
    public static final String FORM = "ash_permanent";
    private PermanentAsh() {}
    public static boolean isPermanent(PixelmonWrapper pokemon) {
        return pokemon.getSpecies().getDex()==658 && pokemon.getForm().isForm(FORM);
    }
    public static boolean isAsh(Stats stats) {return stats.isForm("ash",FORM);}
    public static void addForm(Map<ResourceLocation,JsonElement> data) {
        for(Map.Entry<ResourceLocation,JsonElement> entry:data.entrySet()) {
            JsonObject species=entry.getValue().getAsJsonObject();
            if(!species.has("dex") || species.get("dex").getAsInt()!=658)continue;
            JsonArray forms=species.getAsJsonArray("forms");JsonObject ash=null,existing=null;
            for(JsonElement form:forms) {
                String name=form.getAsJsonObject().get("name").getAsString();
                if(FORM.equals(name))existing=form.getAsJsonObject();
                if("ash".equals(name))ash=form.getAsJsonObject();
            }
            if(ash==null)throw new IllegalStateException("Legacy Ash Greninja form missing");
            JsonObject result=new JsonParser().parse(species.toString()).getAsJsonObject();
            JsonObject permanent=new JsonParser().parse(ash.toString()).getAsJsonObject();
            permanent.addProperty("name",FORM);
            permanent.addProperty("defaultBaseForm",FORM);
            JsonArray tags=new JsonArray();
            if(permanent.has("tags"))for(JsonElement tag:permanent.getAsJsonArray("tags"))if(!"temp".equals(tag.getAsString()))tags.add(tag);
            permanent.add("tags",tags);
            if(existing!=null){
                if(!permanent.equals(existing))throw new IllegalStateException("Reserved form collision: Greninja "+FORM);
                return;
            }
            result.getAsJsonArray("forms").add(permanent);
            entry.setValue(result);return;
        }
        throw new IllegalStateException("Legacy Greninja species missing");
    }
}
