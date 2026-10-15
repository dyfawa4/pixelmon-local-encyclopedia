package local.pixelmon.backport;
import com.pixelmonmod.pixelmon.Pixelmon;
import com.pixelmonmod.pixelmon.api.pokemon.*;
import com.pixelmonmod.pixelmon.api.events.pokemon.ItemFormChangeEvent;
import com.pixelmonmod.pixelmon.items.*;
import com.pixelmonmod.pixelmon.items.heldItems.MegaStoneItem;
import com.pixelmonmod.pixelmon.enums.heldItems.EnumHeldItems;
import com.pixelmonmod.pixelmon.entities.pixelmon.PixelmonEntity;
import com.pixelmonmod.pixelmon.entities.pixelmon.helpers.EvolutionQuery;
import com.pixelmonmod.pixelmon.battles.attacks.*;
import com.pixelmonmod.pixelmon.battles.attacks.specialAttacks.StatsEffect;
import com.pixelmonmod.pixelmon.battles.status.EntryHazard;
import com.pixelmonmod.pixelmon.battles.controller.participants.PixelmonWrapper;
import net.minecraft.item.*;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.*;
import net.minecraft.util.*;
import java.util.*;

public final class ReferenceItems {
    private ReferenceItems(){}
    public static Item create(String name,String kind,String form){
        if(kind.equals("mega"))return Backport.isDragonStone(name)?new Backport.LegacyDragoninite(form):new MegaStoneItem(form);
        switch(name){
            case "tera_orb":return new TeraItems.Orb();
            case "clear_amulet":return new Amulet();
            case "covert_cloak":return new Cloak();
            case "fairy_feather":return new FairyFeather();
            case "utility_umbrella":return new Umbrella();
            case "eternatus_meteorite":return new Meteorite();
            case "pink_nectar":return new Nectar("pau");
            case "purple_nectar":return new Nectar("sensu");
            case "red_nectar":return new Nectar("baile");
            case "yellow_nectar":return new Nectar("pompom");
            default:return new PixelmonItem(new Item.Properties().func_200916_a(ItemGroup.field_78026_f));
        }
    }
    public static class Amulet extends HeldItem {public Amulet(){super(EnumHeldItems.other,new Item.Properties());}}
    public static class Cloak extends HeldItem {public Cloak(){super(EnumHeldItems.other,new Item.Properties());}}
    public static class Umbrella extends HeldItem {
        public Umbrella(){super(EnumHeldItems.other,new Item.Properties());}
        @Override public void applySwitchInEffect(PixelmonWrapper p){p.getBattleAbility().onWeatherChange(p,UmbrellaHooks.backport$filter(p,p.bc.globalStatusController.getWeather()));}
    }
    public static class Meteorite extends HeldItem {public Meteorite(){super(EnumHeldItems.other,new Item.Properties());}}
    public static class FairyFeather extends HeldItem {
        public FairyFeather(){super(EnumHeldItems.other,new Item.Properties());}
        @Override public double preProcessDamagingAttackUser(PixelmonWrapper user,PixelmonWrapper target,Attack attack,double damage){return attack.getType()==Element.FAIRY?damage*1.2:damage;}
    }
    public static boolean blocksSecondary(EffectBase e,PixelmonWrapper user,PixelmonWrapper target){
        if(user==target || !(target.getUsableHeldItem() instanceof Cloak) || !e.isChance() || e instanceof EntryHazard)return false;
        return !(e instanceof StatsEffect && ((StatsEffect)e).isUser);
    }
    public static class Nectar extends PixelmonItem {
        public final String form;
        public Nectar(String form){super(new Item.Properties().func_200916_a(ItemGroup.field_78026_f));this.form=form;}
        @Override public ActionResultType func_111207_a(ItemStack stack,PlayerEntity player,LivingEntity entity,Hand hand){
            if(!(player instanceof ServerPlayerEntity) || !(entity instanceof PixelmonEntity) || hand!=Hand.MAIN_HAND)return ActionResultType.PASS;
            PixelmonEntity mon=(PixelmonEntity)entity;Pokemon p=mon.getPokemon();
            if(p.getSpecies().getDex()!=741 || !player.func_110124_au().equals(p.getOwnerPlayerUUID()) || p.getForm().isForm(form) || com.pixelmonmod.pixelmon.battles.BattleRegistry.getBattle(player)!=null)return ActionResultType.PASS;
            if(Pixelmon.EVENT_BUS.post(new ItemFormChangeEvent((ServerPlayerEntity)player,stack,mon,null)))return ActionResultType.FAIL;
            new EvolutionQuery(p,p.getSpecies().getForm(form));
            if(!player.field_71075_bZ.field_75098_d)stack.func_190918_g(1);
            return ActionResultType.SUCCESS;
        }
    }
}
