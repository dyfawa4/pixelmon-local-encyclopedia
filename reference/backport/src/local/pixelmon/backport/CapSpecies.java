package local.pixelmon.backport;

import com.google.gson.*;
import java.util.*;
import net.minecraft.util.ResourceLocation;

/** A native species, not a held-item costume. Stable save/spec identifier. */
public final class CapSpecies {
    public static final int DEX=1026;
    public static final String NAME="CapPikachu";
    public static final JsonObject DATA=Content.read("backport/cap_pikachu.json");
    private CapSpecies(){}
    private static JsonElement copy(JsonElement e){return new JsonParser().parse(e.toString());}
    public static void add(Map<ResourceLocation,JsonElement> data){
        JsonObject pikachu=null;
        for(JsonElement value:data.values()){
            JsonObject species=value.getAsJsonObject();int dex=species.get("dex").getAsInt();
            if(dex==DEX){
                if(!NAME.equals(species.get("name").getAsString()))throw new IllegalStateException("CapPikachu species id conflict #"+DEX);
                return;
            }
            if(dex==25)pikachu=species;
        }
        if(pikachu==null)throw new IllegalStateException("Missing native Pikachu learnset");
        data.put(new ResourceLocation("pixelmon","1026_cappikachu"),create(pikachu));
    }
    public static JsonObject create(JsonObject pikachu){
        JsonObject species=copy(pikachu).getAsJsonObject(),base=copy(pikachu.getAsJsonArray("forms").get(0)).getAsJsonObject();
        species.addProperty("name",NAME);species.addProperty("dex",DEX);
        base.addProperty("name","");base.addProperty("defaultBaseForm","");
        base.add("battleStats",copy(DATA.get("battleStats")));base.add("abilities",copy(DATA.get("abilities")));
        base.addProperty("catchRate",3);base.add("tags",strings("legendary"));
        base.addProperty("malePercentage",100);base.add("possibleGenders",strings("MALE"));
        base.add("eggGroups",strings("UNDISCOVERED"));base.add("preEvolutions",new JsonArray());base.add("evolutions",new JsonArray());
        base.add("megaItems",new JsonArray());base.add("megas",new JsonArray());
        JsonObject gmax=new JsonObject();gmax.addProperty("canHaveFactor",false);gmax.addProperty("canGigantamax",false);gmax.addProperty("form","");gmax.addProperty("move","");base.add("gigantamax",gmax);
        base.add("genderProperties",copy(Content.read("backport/ash_cap.json").get("genderProperties")));
        JsonObject spawn=base.getAsJsonObject("spawn");spawn.addProperty("spawnLevel",70);spawn.addProperty("spawnLevelRange",0);spawn.addProperty("baseExp",300);
        JsonObject moves=base.getAsJsonObject("moves");
        // Preserve the entire actual native learnset, including compatible pack additions.
        for(String pool:new String[]{"tutorMoves","tmMoves9"}){
            JsonArray rows=moves.has(pool)?moves.getAsJsonArray(pool):new JsonArray();moves.add(pool,rows);
            for(JsonElement extra:DATA.getAsJsonArray("extraMoves"))if(!rows.contains(extra))rows.add(copy(extra));
        }
        JsonObject level=null;for(JsonElement row:moves.getAsJsonArray("levelUpMoves"))if(row.getAsJsonObject().get("level").getAsInt()==0){level=row.getAsJsonObject();break;}
        if(level==null){level=new JsonObject();level.addProperty("level",0);level.add("attacks",new JsonArray());moves.getAsJsonArray("levelUpMoves").add(level);}
        JsonArray learned=level.getAsJsonArray("attacks");if(!learned.contains(new JsonPrimitive("Ten Million Volts")))learned.add("Ten Million Volts");
        JsonArray forms=new JsonArray();forms.add(base);species.add("forms",forms);
        return species;
    }
    private static JsonArray strings(String... values){JsonArray a=new JsonArray();for(String value:values)a.add(value);return a;}
}
