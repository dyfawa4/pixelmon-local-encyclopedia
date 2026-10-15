package local.pixelmon.backport;

import com.google.gson.*;
import java.util.Map;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.world.gen.feature.structure.Structure;

/** Native mansion encounters follow the structure, including modded biomes. */
public final class MagearnaSpawns {
    private static final ResourceLocation RULE = new ResourceLocation("pixelmon", "legendaries/magearna.set");
    private MagearnaSpawns() {}

    public static void merge(Map<ResourceLocation, JsonElement> data) {
        JsonElement original = data.get(RULE);
        if (original == null || !original.isJsonObject()) return;
        JsonObject set = new JsonParser().parse(original.toString()).getAsJsonObject();
        if (!set.has("spawnInfos") || !set.get("spawnInfos").isJsonArray()) return;
        boolean changed = false;
        for (JsonElement value : set.getAsJsonArray("spawnInfos")) {
            if (!value.isJsonObject()) continue;
            JsonObject info = value.getAsJsonObject();
            if (!info.has("spec") || !info.get("spec").isJsonPrimitive()
                    || !info.get("spec").getAsString().matches("(?i)species:Magearna(?:\\s.*)?")
                    || !info.has("condition") || !info.get("condition").isJsonObject()) continue;
            JsonObject condition = info.getAsJsonObject("condition");
            condition.remove("stringBiomes");
            JsonArray structures = new JsonArray();
            structures.add("mansion");
            condition.add("structures", structures);
            changed = true;
        }
        if (changed) data.put(RULE, set);
    }

    public static boolean mansion(ServerWorld world, BlockPos pos) {
        if (world == null || pos == null
                || !world.func_217354_b(pos.func_177958_n() >> 4, pos.func_177952_p() >> 4)) return false;
        // Resolve the real cross-chunk references and require a mansion piece.
        // No biome list or start-chunk-only cache determines eligibility.
        return world.func_241112_a_().func_235010_a_(pos, true, Structure.field_236368_d_).func_75069_d();
    }
}
