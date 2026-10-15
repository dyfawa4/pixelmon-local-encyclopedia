package local.pixelmon.backport;

import java.util.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import com.google.gson.*;
import com.pixelmonmod.pixelmon.api.pokemon.*;
import com.pixelmonmod.pixelmon.api.pokemon.species.*;
import com.pixelmonmod.pixelmon.api.registries.PixelmonSpecies;
import com.pixelmonmod.pixelmon.api.events.raids.RaidDropsEvent;
import com.pixelmonmod.pixelmon.battles.controller.BattleController;
import com.pixelmonmod.pixelmon.battles.controller.participants.*;
import com.pixelmonmod.pixelmon.battles.raids.*;
import com.pixelmonmod.pixelmon.entities.DenEntity;
import net.minecraft.entity.*;
import net.minecraft.item.*;
import net.minecraft.util.*;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.registries.ForgeRegistries;

/** Independent encounter and reward pools. Never writes DropItemRegistry.raidDrops. */
public final class TeraRaids {
    public static final JsonObject DATA;
    static {try(InputStream in=TeraRaids.class.getResourceAsStream("/backport/tera_raids.json")){
        DATA=new JsonParser().parse(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonObject();
    }catch(Exception e){throw new ExceptionInInitializerError(e);}}
    public static final int[] LEVELS={0,12,25,35,45,75,90};
    public static final int[] HP={0,3,5,8,12,20,25};
    public static final Map<RaidData,TeraRaidParticipant> RAIDS=Collections.synchronizedMap(new WeakHashMap<>());
    private TeraRaids(){}
    public static TeraRaidParticipant get(BattleController battle){if(battle==null)return null;for(BattleParticipant p:battle.participants)if(p instanceof TeraRaidParticipant)return (TeraRaidParticipant)p;return null;}
    public static boolean charged(PixelmonWrapper p){TeraRaidParticipant raid=get(p.bc);return raid==null || p.getParticipant()==raid || raid.charge(p.getParticipant())>=3;}
    public static Pokemon encounter(JsonObject entry){
        String[] tokens=entry.get("spec").getAsString().split(" ");
        Species s=PixelmonSpecies.fromNameOrDex(tokens[0]).orElseThrow(()->new IllegalArgumentException("Missing Tera species "+tokens[0]));
        if(!RaidSpawnExclusions.allowed(s.getDex()))throw new IllegalArgumentException("Species excluded from Tera dens: "+tokens[0]);
        Pokemon p=PokemonFactory.create(s);
        for(int i=1;i<tokens.length;i++){
            String[] part=tokens[i].split(":",2);
            if(part[0].equals("form")){String form=part[1];if(s.getDex()==550 && form.equals("white_striped"))form="white";if(!s.hasForm(form))throw new IllegalArgumentException("Missing Tera form "+entry);p.setForm(form);}
            else if(part[0].equals("palette")){p.setPalette(part[1]);if(!p.getPalette().getName().equals(part[1]))throw new IllegalArgumentException("Missing Tera palette "+entry);}
            else throw new IllegalArgumentException("Unhandled Tera spec "+entry);
        }
        return p;
    }
    public static JsonObject pick(int stars,Random random,java.util.function.Predicate<String> habitat){
        List<JsonObject> entries=new ArrayList<>();double sum=0;
        for(JsonElement row:DATA.getAsJsonArray("encounters")){JsonObject r=row.getAsJsonObject();if(RaidSpawnExclusions.allowedSpec(r.get("spec").getAsString()) && stars>=r.get("min").getAsInt() && stars<=r.get("max").getAsInt() && habitat.test(r.get("spec").getAsString())){entries.add(r);sum+=r.get("weight").getAsDouble();}}
        if(entries.isEmpty())return null;
        double roll=random.nextDouble()*sum;for(JsonObject r:entries){roll-=r.get("weight").getAsDouble();if(roll<0)return r;}return entries.get(entries.size()-1);
    }
    public static RaidSettings settings(RaidData raid,Pokemon p){int stars=Math.max(1,Math.min(6,raid.getStars()));
        p.setLevelNum(LEVELS[stars]);p.rerollMoveset();
        return RaidSettings.builder().setAutoGenerate(false).setStars(stars).setLevel(LEVELS[stars]).setTrueLevel(LEVELS[stars])
            .setPerfectIVs(Math.max(1,stars-1)).setGigantamax(false).setDynamaxLevel(0,0).setRewardCount(0)
            .setShields(0,0).setCanCatch(true).setShockwaveChance(0).setTurns(Integer.MAX_VALUE).setLives(Integer.MAX_VALUE)
            .setMoveset(new ArrayList<>(p.getMoveset())).build();
    }
    public static Item rewardItem(String id){Item item=ForgeRegistries.ITEMS.getValue(new ResourceLocation(id));if(item==null || item==Items.field_190931_a)throw new IllegalStateException("Unregistered Tera reward "+id);return item;}
    public static ArrayList<ItemStack> rewards(int stars,int type,Random random){
        if(stars<1 || stars>6 || type<0 || type>=19)throw new IllegalArgumentException("Tera reward context");
        ArrayList<ItemStack> result=new ArrayList<>();
        result.add(new ItemStack(Backport.REGISTERED.get(TeraItems.NAMES[type]+"_tera_shard").get(),2+stars*2));
        List<JsonObject> choices=new ArrayList<>();double sum=0;for(JsonElement row:DATA.getAsJsonArray("rewards")){JsonObject r=row.getAsJsonObject();if(stars>=r.get("min").getAsInt() && stars<=r.get("max").getAsInt()){choices.add(r);sum+=r.get("weight").getAsDouble();}}
        int rolls=stars<4?3+random.nextInt(3):stars<6?4+random.nextInt(2):5;
        for(int i=0;i<rolls;i++){double roll=random.nextDouble()*sum;for(JsonObject r:choices){roll-=r.get("weight").getAsDouble();if(roll<0){result.add(new ItemStack(rewardItem(r.get("item").getAsString())));break;}}}
        result.add(new ItemStack(Backport.REGISTERED.get("tera_blast_disc_single_use").get()));
        // Stellar remains renewable even when no naturally rolled boss has that type.
        if(stars>=5 && type!=18)result.add(new ItemStack(Backport.REGISTERED.get("stellar_tera_shard").get(),stars-3));
        return result;
    }
    public static boolean convert(long seed,String dimension,int x,int y,int z){long hash=seed^dimension.hashCode()^((long)x*341873128712L)^((long)y*42317861L)^((long)z*132897987541L);hash^=hash>>>33;hash*=0xff51afd7ed558ccdL;hash^=hash>>>33;return (hash&1)==0;}
    public static class Events {
        @SubscribeEvent(priority=EventPriority.LOWEST) public void join(EntityJoinWorldEvent e){
            if(e.isCanceled() || e.getWorld().field_72995_K || !(e.getEntity() instanceof DenEntity) || e.getEntity() instanceof TeraDenEntity)return;
            DenEntity den=(DenEntity)e.getEntity();if(den.isDecoration() || den.getPersistentData().func_74764_b("BackportDenKind"))return;
            ServerWorld world=(ServerWorld)e.getWorld();boolean tera=convert(world.func_72905_C(),world.func_234923_W_().func_240901_a_().toString(),den.func_233580_cy_().func_177958_n(),den.func_233580_cy_().func_177956_o(),den.func_233580_cy_().func_177952_p());
            den.getPersistentData().func_74768_a("BackportDenKind",tera?1:0);
            if(!tera || den.getData().map(RaidData::hasPlayers).orElse(false))return;
            e.setCanceled(true);
            world.func_73046_m().execute(()->{TeraDenEntity replacement=new TeraDenEntity(world);replacement.func_226286_f_(den.func_226277_ct_(),den.func_226278_cu_(),den.func_226281_cx_());replacement.func_184221_a(den.func_110124_au());replacement.roll((int)(world.func_72820_D()/24000));world.func_217376_c(replacement);});
        }
        @SubscribeEvent(priority=EventPriority.LOWEST) public void drops(RaidDropsEvent event){
            TeraRaidParticipant raid=RAIDS.get(event.getRaid());if(raid==null)return;
            event.setDrops(rewards(raid.stars,raid.teraType,event.getPlayer().playerEntity.func_70681_au()));
            Pokemon caught=event.getPossibleCatch();caught.setForm(raid.originalForm);caught.setPalette(raid.palette);
            caught.getPersistentData().func_74768_a("TeraType",raid.teraType+1);caught.getPersistentData().func_74757_a("BackportTeraRaidCatch",true);
            caught.setGigantamaxFactor(false);caught.setDynamaxLevel(0);event.setCanCatch(true);
        }
    }
}
