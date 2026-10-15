package local.pixelmon.backport;
import java.util.*;
import com.pixelmonmod.pixelmon.entities.pixelmon.AbstractHoldsItemsEntity;
import com.pixelmonmod.pixelmon.api.registries.PixelmonItems;
import com.pixelmonmod.pixelmon.api.pokemon.item.pokeball.PokeBallRegistry;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
/** Add guaranteed rewards to the existing native death-loot query, never grant twice. */
public final class CapDrops {
    private CapDrops(){}
    public static List<ItemStack> ensure(AbstractHoldsItemsEntity entity,List<ItemStack> original){
        if(entity==null || entity.field_70170_p.field_72995_K || entity.getPokemon().getSpecies().getDex()!=CapSpecies.DEX
            || entity.hasOwner() || entity.getTrainer()!=null || entity.getPokemon().getOwnerPlayerUUID()!=null
            || entity.getPokemon().getStorage()!=null || entity.getPokemon().isEgg() || entity.getPokemon().hasNoDrops())return original;
        List<ItemStack> drops=new ArrayList<>();
        if(original!=null)for(ItemStack stack:original)if(!reward(stack))drops.add(stack);
        drops.add(PokeBallRegistry.MASTER_BALL.getValueUnsafe().getBallItem(1));
        drops.add(new ItemStack(PixelmonItems.pikashunium_z,1));return drops;
    }
    private static boolean reward(ItemStack stack){
        if(stack==null || stack.func_190926_b())return false;
        ResourceLocation id=stack.func_77973_b().getRegistryName();
        return stack.func_77973_b()==PixelmonItems.pikashunium_z || new ResourceLocation("pixelmon:master_ball").equals(id)
            || new ResourceLocation("pixelmon:poke_ball").equals(id) && stack.func_77942_o() && "master_ball".equals(stack.func_77978_p().func_74779_i("PokeBallID"));
    }
}
