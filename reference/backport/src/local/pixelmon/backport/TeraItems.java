package local.pixelmon.backport;
import com.pixelmonmod.pixelmon.api.pokemon.*;
import com.pixelmonmod.pixelmon.entities.pixelmon.PixelmonEntity;
import com.pixelmonmod.pixelmon.battles.BattleRegistry;
import com.pixelmonmod.pixelmon.battles.controller.participants.*;
import com.pixelmonmod.pixelmon.items.PixelmonItem;
import net.minecraft.item.*;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.*;
import net.minecraft.util.*;
import net.minecraft.util.text.*;
import net.minecraft.world.World;
import net.minecraft.client.util.ITooltipFlag;
import com.pixelmonmod.pixelmon.api.events.HealerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import java.util.*;

public final class TeraItems {
    private TeraItems(){}
    public static final String[] NAMES={"normal","fire","water","grass","electric","ice","fighting","poison","ground","flying","psychic","bug","rock","ghost","dragon","dark","steel","fairy","stellar"};
    public static boolean hasOrb(PlayerEntity p){try(PokeInventory inventory=new PokeInventory(p)){for(ItemStack s:inventory.stacks)if(!s.func_190926_b() && s.func_77973_b() instanceof Orb && charged(s))return true;return false;}}
    public static boolean charged(ItemStack s){return !s.func_77942_o() || !s.func_77978_p().func_74767_n("BackportTeraSpent");}
    public static void consumeOrb(PixelmonWrapper p){if(p.bc.simulateMode || !(p.getParticipant() instanceof PlayerParticipant))return;
        PlayerEntity owner=((PlayerParticipant)p.getParticipant()).player;
        try(PokeInventory inventory=new PokeInventory(owner)){for(ItemStack s:inventory.stacks)if(!s.func_190926_b() && s.func_77973_b() instanceof Orb && charged(s)){s.func_196082_o().func_74757_a("BackportTeraSpent",true);break;}}
    }
    public static boolean change(ServerPlayerEntity owner,Pokemon p,int type){
        if(type<0 || type>=Tera.TYPES.length || p.isEgg() || !owner.func_110124_au().equals(p.getOwnerPlayerUUID()) || BattleRegistry.getBattle(owner)!=null || !hasAnyOrb(owner))return false;
        try(PokeInventory inventory=new PokeInventory(owner)){
        Item item=Backport.REGISTERED.get(NAMES[type]+"_tera_shard").get();int count=0;
        for(ItemStack stack:inventory.stacks)if(stack.func_77973_b()==item)count+=stack.func_190916_E();
        if(count<50 && !owner.field_71075_bZ.field_75098_d)return false;
        if(Tera.type(p)==Tera.TYPES[type] || !Tera.changeType(p,Tera.TYPES[type]))return false;
        if(!owner.field_71075_bZ.field_75098_d){int left=50;for(ItemStack stack:inventory.stacks)if(stack.func_77973_b()==item){int take=Math.min(left,stack.func_190916_E());stack.func_190918_g(take);left-=take;if(left==0)break;}}
        return true;}
    }
    public static boolean hasAnyOrb(PlayerEntity p){try(PokeInventory inventory=new PokeInventory(p)){for(ItemStack s:inventory.stacks)if(!s.func_190926_b() && s.func_77973_b() instanceof Orb)return true;return false;}}
    public static void recharge(PlayerEntity player){
        if(!(player instanceof ServerPlayerEntity) || player.field_70170_p.field_72995_K)return;
        ServerPlayerEntity owner=(ServerPlayerEntity)player;
        var server=owner.func_184102_h();if(server==null)return;
        // Optional healing packets may invoke their native handler off-thread.
        if(!server.func_213162_bc()){server.execute(()->recharge(owner));return;}
        boolean changed=false;try(PokeInventory inventory=new PokeInventory(owner)){for(ItemStack s:inventory.stacks)
            if(!s.func_190926_b() && s.func_77973_b() instanceof Orb && !charged(s)){s.func_196082_o().func_74757_a("BackportTeraSpent",false);changed=true;}}
        if(changed){owner.field_71071_by.func_70296_d();owner.func_146105_b(new TranslationTextComponent("pixelmon.backport.tera.recharged"),true);}
    }
    public static class Orb extends PixelmonItem {
        public Orb(){super(new Item.Properties().func_200917_a(1).func_200916_a(ItemGroup.field_78026_f));}
        @Override public boolean func_77636_d(ItemStack stack){return charged(stack);}
        @Override public ActionResult<ItemStack> func_77659_a(World world,PlayerEntity player,Hand hand){
            if(world.field_72995_K)TeraNetwork.CHANNEL.sendToServer(new TeraNetwork.Request(-1,new UUID(0,0),2,-1));
            return ActionResult.func_226249_b_(player.func_184586_b(hand));
        }
        @Override public void func_77624_a(ItemStack s,World world,List<ITextComponent> lines,ITooltipFlag flag){
            for(String key:new String[]{charged(s)?"pixelmon.backport.tera.orb.charged":"pixelmon.backport.tera.orb.spent","pixelmon.backport.tera.orb.help","pixelmon.backport.tera.orb.edit"})
                lines.add(new TranslationTextComponent(key).func_240699_a_(TextFormatting.GRAY));
        }
    }
    public static class Shard extends PixelmonItem {
        private final int type;
        public Shard(int type){super(new Item.Properties().func_200916_a(ItemGroup.field_78026_f));this.type=type;}
        @Override public ActionResultType func_111207_a(ItemStack stack,PlayerEntity owner,LivingEntity entity,Hand hand){
            if(!(owner instanceof ServerPlayerEntity) || !(entity instanceof PixelmonEntity))return ActionResultType.PASS;
            return change((ServerPlayerEntity)owner,((PixelmonEntity)entity).getPokemon(),type)?ActionResultType.SUCCESS:ActionResultType.PASS;
        }
        @Override public void func_77624_a(ItemStack s,World world,List<ITextComponent> lines,ITooltipFlag flag){lines.add(new TranslationTextComponent("pixelmon.backport.tera.shard.help").func_240699_a_(TextFormatting.GRAY));}
    }
    public static class Charging {
        @SubscribeEvent public void healer(HealerEvent.Post event){
            recharge(event.player);
        }
    }
}
