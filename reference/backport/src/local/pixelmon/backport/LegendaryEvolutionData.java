package local.pixelmon.backport;
import com.google.gson.*;
/** Correct the native scroll target spelling against the actual registered form. */
public final class LegendaryEvolutionData {
    private LegendaryEvolutionData(){}
    public static void normalize(JsonObject species){
        if(species.get("dex").getAsInt()!=891)return;
        for(JsonElement value:species.getAsJsonArray("forms")){
            JsonObject form=value.getAsJsonObject();if(!form.has("evolutions"))continue;
            for(JsonElement evolution:form.getAsJsonArray("evolutions")){
                JsonObject row=evolution.getAsJsonObject();if(row.has("to") && row.get("to").getAsString().equals("Urshifu form:single_strike"))row.addProperty("to","Urshifu form:singlestrike");
            }
        }
    }
}
