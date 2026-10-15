package local.pixelmon.backport;

import com.pixelmonmod.pixelmon.api.config.PixelmonConfigProxy;
import com.pixelmonmod.pixelmon.api.events.spawning.LegendarySpawnEvent;
import com.pixelmonmod.pixelmon.api.util.helpers.RandomHelper;
import net.minecraftforge.eventbus.api.*;

public final class CapSpawns {
    /** Native structure references cover village pieces beyond the start chunk. */
    public static boolean village(net.minecraft.world.server.ServerWorld world,net.minecraft.util.math.BlockPos pos){
        if(world==null || pos==null || !world.func_217354_b(pos.func_177958_n()>>4,pos.func_177952_p()>>4))return false;
        return world.func_241112_a_().func_235010_a_(pos,false,net.minecraft.world.gen.feature.structure.Structure.field_236381_q_).func_75069_d();
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public void spawn(LegendarySpawnEvent.DoSpawn event){
        if(event.isCanceled() || event.getLegendary().getDex()!=CapSpecies.DEX)return;
        var location=event.action.spawnLocation.location;var world=location.world;var pos=location.pos;
        // Check the actual final spawn location as well as the datapack condition.
        if(!(world instanceof net.minecraft.world.server.ServerWorld)
            || !new net.minecraft.util.ResourceLocation("minecraft:plains").equals(world.func_226691_t_(pos).getRegistryName())
            || !village((net.minecraft.world.server.ServerWorld)world,pos)
            || !RandomHelper.getRandomChance((float)BurstPolicy.spawnRetention(PixelmonConfigProxy.getSpawning().getLegendarySpawnChance())))event.setCanceled(true);
    }
}
