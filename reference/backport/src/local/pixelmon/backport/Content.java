package local.pixelmon.backport;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.util.ResourceLocation;
import com.pixelmonmod.pixelmon.enums.technicalmoves.Gen9TechnicalMachines;

/** Merge before native deserialization so save files, trades and NPCs see real
 * native Species/Stats/Attack objects rather than a parallel Pokémon registry. */
public final class Content {
    public static final String NS = "pixelmon_backport";
    public static final JsonObject DATA = read("backport/content.json");
    public static final JsonObject MODERN_MOVES=read("backport/modern-moves.json");
    public static final JsonObject TERA_LEARNING=read("backport/tera_learning.json");
    public static final JsonObject RIDING=read("backport/riding.json");
    private Content() {}
    public static JsonObject read(String path) {
        try (InputStream stream = Content.class.getClassLoader().getResourceAsStream(path)) {
            if (stream == null) throw new IllegalStateException("Missing content: " + path);
            return new JsonParser().parse(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException e) { throw new IllegalStateException(path, e); }
    }
    public static void apply(String context, String type, Map<ResourceLocation, JsonElement> data) {
        if (!"pixelmon".equals(context)) return;
        if("spawning".equals(type))MagearnaSpawns.merge(data);
        if("npcs".equals(type))NpcLocalization.merge(data);
        if(DATA.has("referenceExtras"))for(Map.Entry<ResourceLocation,JsonElement> entry:data.entrySet()){
            String key=type+"/"+entry.getKey().func_110623_a();JsonObject extras=DATA.getAsJsonObject("referenceExtras");
            if(!extras.has(key))continue;
            JsonElement extra=extras.get(key),merged=copy(entry.getValue());
            if(type.equals("daycare")){
                // Only localized requirement messages differ in the reference;
                // preserve existing mechanics and costs.
                JsonArray ours=merged.getAsJsonObject().getAsJsonArray("requirements"),source=extra.getAsJsonObject().getAsJsonArray("requirements");
                for(int i=0;i<Math.min(ours.size(),source.size());i++)if(source.get(i).getAsJsonObject().has("error_message"))ours.get(i).getAsJsonObject().add("error_message",copy(source.get(i).getAsJsonObject().get("error_message")));
            }else if(merged.isJsonArray()){
                JsonArray rows=merged.getAsJsonArray();
                for(JsonElement value:extra.getAsJsonArray()){
                    JsonObject incoming=value.getAsJsonObject(),existing=null;
                    for(JsonElement row:rows)if(row.getAsJsonObject().get("pokemon").equals(incoming.get("pokemon")))existing=row.getAsJsonObject();
                    if(existing==null)rows.add(copy(incoming));else appendUnique(existing,"items",incoming.getAsJsonArray("items"));
                }
            }else mergeAdditional(merged,extra);
            entry.setValue(merged);
        }
        if ("config".equals(type)) {
            for(Map.Entry<ResourceLocation,JsonElement> entry:data.entrySet())if(entry.getKey().func_110623_a().equals("starters") && entry.getValue().isJsonObject()){
                JsonObject config=new JsonParser().parse(entry.getValue().toString()).getAsJsonObject();JsonArray starters=config.getAsJsonArray("starters");
                Set<String> existing=new HashSet<>();for(JsonElement starter:starters)existing.add(starter.getAsJsonObject().get("spec").getAsString());
                for(JsonElement starter:DATA.getAsJsonArray("starterAdditions"))if(!existing.contains(starter.getAsJsonObject().get("spec").getAsString()))starters.add(copy(starter));
                entry.setValue(config);
            }
        } else if ("species".equals(type)) {
            for (JsonElement patchElement : DATA.getAsJsonArray("patches")) {
                JsonObject patch = patchElement.getAsJsonObject();
                JsonObject incoming = patch.getAsJsonObject("species");
                int dex = incoming.get("dex").getAsInt();
                Map.Entry<ResourceLocation, JsonElement> existing = data.entrySet().stream()
                    .filter(e -> e.getValue().isJsonObject() && e.getValue().getAsJsonObject().has("dex")
                        && e.getValue().getAsJsonObject().get("dex").getAsInt() == dex).findFirst().orElse(null);
                if (existing == null) {
                    if (!"new".equals(patch.get("mode").getAsString()))
                        throw new IllegalStateException("Missing expected legacy species #" + dex);
                    data.put(new ResourceLocation("pixelmon", String.format(Locale.ROOT, "%03d_%s", dex,
                        incoming.get("name").getAsString().toLowerCase(Locale.ROOT))), copy(incoming));
                    continue;
                }
                JsonObject original = existing.getValue().getAsJsonObject();
                if (!original.get("name").getAsString().equals(incoming.get("name").getAsString()))
                    throw new IllegalStateException("Species id conflict #" + dex);
                // A different addon/data pack may already implement a new dex.
                if ("new".equals(patch.get("mode").getAsString())) continue;
                data.put(existing.getKey(), mergeSpecies(original, incoming));
            }
            PermanentAsh.addForm(data);
            CapPikachu.addForm(data);
            CapSpecies.add(data);
            for(JsonElement value:data.values())LegendaryEvolutionData.normalize(value.getAsJsonObject());
            for(JsonElement value:data.values()){
                JsonObject species=value.getAsJsonObject();String dex=species.get("dex").getAsString();
                if("149".equals(dex))for(String alias:new String[]{"dragoninite","dragonitite"})if(net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(new ResourceLocation("pixelmon:"+alias)) instanceof com.pixelmonmod.pixelmon.items.heldItems.MegaStoneItem){
                    JsonObject base=species.getAsJsonArray("forms").get(0).getAsJsonObject();
                    JsonObject canonical=new JsonObject();JsonArray items=new JsonArray(),forms=new JsonArray();items.add("pixelmon:"+alias);forms.add("mega");canonical.add("megaItems",items);canonical.add("megas",forms);mergeMegaPairs(base,canonical);
                }
                if(!RIDING.has(dex))continue;
                for(JsonElement form:species.getAsJsonArray("forms")){
                    JsonObject f=form.getAsJsonObject(),settings=RIDING.getAsJsonObject(dex).getAsJsonObject(f.get("name").getAsString());
                    if(settings!=null)f.add("movement",copy(settings));
                }
            }
            for(JsonElement value:data.values()){
                JsonObject species=value.getAsJsonObject();String dex=species.get("dex").getAsString();if(!TERA_LEARNING.has(dex))continue;
                JsonArray source=TERA_LEARNING.getAsJsonArray(dex);
                for(JsonElement valueForm:species.getAsJsonArray("forms")){
                    JsonObject form=valueForm.getAsJsonObject();String name=form.get("name").getAsString();
                    boolean base=name.isEmpty() || name.equals("base");if(!source.contains(new JsonPrimitive(name)) && !(base && source.contains(new JsonPrimitive("base"))))continue;
                    // A missing move object inherits the base form in the native loader.
                    // Materialize that inheritance before adding a move; otherwise regional
                    // variants would lose their entire native learnset and retain only Tera Blast.
                    JsonObject moves;
                    if(form.has("moves"))moves=form.getAsJsonObject("moves");
                    else{
                        JsonObject baseForm=species.getAsJsonArray("forms").get(0).getAsJsonObject();
                        String baseName=form.has("defaultBaseForm")?form.get("defaultBaseForm").getAsString():"";
                        if(!baseName.isEmpty())for(JsonElement candidate:species.getAsJsonArray("forms"))
                            if(baseName.equals(candidate.getAsJsonObject().get("name").getAsString()))baseForm=candidate.getAsJsonObject();
                        moves=baseForm.has("moves")?copy(baseForm.get("moves")).getAsJsonObject():new JsonObject();
                        form.add("moves",moves);
                    }
                    JsonArray machines=moves.has("tmMoves9")?moves.getAsJsonArray("tmMoves9"):new JsonArray();moves.add("tmMoves9",machines);if(!machines.contains(new JsonPrimitive("Tera Blast")))machines.add("Tera Blast");
                    JsonArray levels=moves.has("levelUpMoves")?moves.getAsJsonArray("levelUpMoves"):new JsonArray();moves.add("levelUpMoves",levels);
                    boolean learned=false;for(JsonElement level:levels)if(level.getAsJsonObject().getAsJsonArray("attacks").contains(new JsonPrimitive("Tera Blast")))learned=true;
                    if(!learned){JsonObject target=null;for(JsonElement level:levels)if(level.getAsJsonObject().get("level").getAsInt()==50)target=level.getAsJsonObject();if(target==null){target=new JsonObject();target.addProperty("level",50);target.add("attacks",new JsonArray());levels.add(target);}target.getAsJsonArray("attacks").add("Tera Blast");}
                }
            }
        } else if ("moves".equals(type)) {
            Set<String> names = new HashSet<>();
            for (JsonElement value : data.values()) names.add(value.getAsJsonObject().get("attackName").getAsString());
            List<JsonElement> additions=new ArrayList<>();for(JsonElement move:DATA.getAsJsonArray("moves"))additions.add(move);
            for(JsonElement move:CapSpecies.DATA.getAsJsonArray("moves"))additions.add(move);
            for (JsonElement move : additions) {
                String name = move.getAsJsonObject().get("attackName").getAsString();
                if (!names.contains(name)) data.put(new ResourceLocation("pixelmon", "backport_" + name.toLowerCase(Locale.ROOT).replace(' ', '_')), copy(move));
            }
            for(JsonElement value:data.values()){
                JsonObject move=value.getAsJsonObject();
                String moveName=move.get("attackName").getAsString();
                // The native Thunderbolt crystal row previously admitted every Pikachu.
                // Only the independent signature may respond to Pikashunium Z now.
                if(!"Ten Million Volts".equals(moveName) && move.has("z")){
                    JsonArray allowed=new JsonArray();for(JsonElement row:move.getAsJsonArray("z"))
                        if(!"pikashunium_z".equals(row.getAsJsonObject().get("crystal").getAsString()))allowed.add(row);
                    move.add("z",allowed);
                }
                // These damaging signatures gained entry hazards in Gen 9.
                // Ancient aliases retain the Legends splinter mechanics.
                if(MODERN_MOVES.has(moveName) && move.getAsJsonArray("effects").toString().contains("Splinters"))
                    move.add("effects",copy(MODERN_MOVES.getAsJsonObject(moveName).get("effects")));
                if("Tera Blast".equals(move.get("attackName").getAsString())){
                    JsonArray effects=move.getAsJsonArray("effects");
                    if(!effects.toString().contains("BackportTeraBlast")){
                        JsonObject effect=new JsonObject();effect.addProperty("effectTypeID","BackportTeraBlast");effect.addProperty("persists",false);effect.add("modifiers",new JsonArray());effects.add(effect);
                    }
                }
            }
        } else if ("abilities".equals(type)) {
            for (String name : new String[]{"TeraShift", "TeraShell", "PoisonPuppeteer", "TeraformZero", "EmbodyAspect"}) {
                boolean exists = data.values().stream().anyMatch(v -> v.toString().contains(name));
                if (!exists) {
                    JsonObject ability = new JsonObject();
                    ability.addProperty("className", "local.pixelmon.backport.BackportAbilities$" + name);
                    data.put(new ResourceLocation("pixelmon", "backport_" + name.toLowerCase(Locale.ROOT)), ability);
                }
            }
        }
    }
    public static JsonObject mergeSpecies(JsonObject original, JsonObject incoming) {
        JsonObject result = copy(original).getAsJsonObject();
        JsonArray forms = result.getAsJsonArray("forms");
        for (JsonElement element : incoming.getAsJsonArray("forms")) {
            JsonObject source = element.getAsJsonObject();
            String name = source.get("name").getAsString();
            JsonObject target = null;
            for (JsonElement existing : forms) if (name.equals(existing.getAsJsonObject().get("name").getAsString())) target = existing.getAsJsonObject();
            if (target == null) { forms.add(copy(source)); continue; }
            boolean placeholder = target.toString().contains("\"none\"]");
            mergeMegaPairs(target,source);
            for (Map.Entry<String, JsonElement> field : source.entrySet()) {
                String key = field.getKey();
                if ("name".equals(key)) continue;
                if ("megaItems".equals(key) || "megas".equals(key)) continue;
                if ("evolutions".equals(key)) {
                    appendUnique(target, key, field.getValue().getAsJsonArray());
                } else if ("moves".equals(key)) {
                    JsonObject moves = target.has(key) ? target.getAsJsonObject(key) : new JsonObject();
                    for (Map.Entry<String, JsonElement> move : field.getValue().getAsJsonObject().entrySet()) {
                        if(!"levelUpMoves".equals(move.getKey())){appendUnique(moves,move.getKey(),move.getValue().getAsJsonArray());continue;}
                        JsonArray levels=moves.has("levelUpMoves")?moves.getAsJsonArray("levelUpMoves"):new JsonArray();moves.add("levelUpMoves",levels);
                        for(JsonElement level:move.getValue().getAsJsonArray()){
                            JsonObject incomingLevel=level.getAsJsonObject(),present=null;
                            for(JsonElement existingLevel:levels)if(existingLevel.getAsJsonObject().get("level").equals(incomingLevel.get("level")))present=existingLevel.getAsJsonObject();
                            if(present==null)levels.add(copy(level));else appendUnique(present,"attacks",incomingLevel.getAsJsonArray("attacks"));
                        }
                    }
                    normalizeTM9(moves);
                    target.add(key, moves);
                } else if (placeholder || !target.has(key)) target.add(key, copy(field.getValue()));
            }
            if (placeholder && target.has("tags")) {
                JsonArray tags = new JsonArray();
                for (JsonElement tag : target.getAsJsonArray("tags")) if (!"undexable".equals(tag.getAsString())) tags.add(tag);
                target.add("tags", tags);
            }
        }
        return result;
    }
    private static void mergeMegaPairs(JsonObject target,JsonObject source){
        if(!source.has("megaItems") || !source.has("megas"))return;
        JsonArray items=target.has("megaItems")?target.getAsJsonArray("megaItems"):new JsonArray();
        JsonArray megas=target.has("megas")?target.getAsJsonArray("megas"):new JsonArray();
        JsonArray added=source.getAsJsonArray("megaItems"),forms=source.getAsJsonArray("megas");
        if(added.size()!=forms.size() || items.size()!=megas.size())throw new IllegalArgumentException("Unpaired Mega item/form data: "+target.get("name"));
        for(int i=0;i<added.size();i++)if(!items.contains(added.get(i))){items.add(copy(added.get(i)));megas.add(copy(forms.get(i)));}
        target.add("megaItems",items);target.add("megas",megas);
    }
    private static void normalizeTM9(JsonObject moves){
        if(!moves.has("tmMoves9"))return;
        JsonArray supported=new JsonArray();
        for(JsonElement entry:moves.getAsJsonArray("tmMoves9")){
            String name=entry.getAsString();Gen9TechnicalMachines nativeMove=null;
            for(Gen9TechnicalMachines move:Gen9TechnicalMachines.values())if(move.getAttackName().equalsIgnoreCase(name)){nativeMove=move;break;}
            if(nativeMove!=null){JsonPrimitive canonical=new JsonPrimitive(nativeMove.getAttackName());if(!supported.contains(canonical))supported.add(canonical);}
            else if(BackportTechnicalMoves.byName(name)!=null){JsonArray extra=new JsonArray();extra.add(name);appendUnique(moves,"tmMoves",extra);}
            else supported.add(entry);
        }
        moves.add("tmMoves9",supported);
    }
    private static void mergeAdditional(JsonElement target,JsonElement incoming){
        if(target.isJsonObject() && incoming.isJsonObject())for(Map.Entry<String,JsonElement> e:incoming.getAsJsonObject().entrySet()){
            JsonObject obj=target.getAsJsonObject();if(!obj.has(e.getKey()))obj.add(e.getKey(),copy(e.getValue()));else mergeAdditional(obj.get(e.getKey()),e.getValue());
        }
        else if(target.isJsonArray() && incoming.isJsonArray())for(JsonElement e:incoming.getAsJsonArray())if(!target.getAsJsonArray().contains(e))target.getAsJsonArray().add(copy(e));
        // Never replace a pre-existing cost, rarity or other scalar setting.
    }
    private static void appendUnique(JsonObject obj, String key, JsonArray incoming) {
        JsonArray result = obj.has(key) ? obj.getAsJsonArray(key) : new JsonArray();
        for (JsonElement item : incoming) {
            boolean found = false;
            for (JsonElement old : result) if (old.equals(item)) { found = true; break; }
            if (!found) result.add(copy(item));
        }
        obj.add(key, result);
    }
    private static JsonElement copy(JsonElement value){return new JsonParser().parse(value.toString());}
}
