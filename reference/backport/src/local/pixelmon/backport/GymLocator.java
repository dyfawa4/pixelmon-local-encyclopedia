package local.pixelmon.backport;

import java.util.*;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.pixelmonmod.pixelmon.world.structure.PixelmonStructures;
import com.pixelmonmod.pixelmon.world.structure.type.GymConfig;
import com.pixelmonmod.pixelmon.world.structure.type.GymStructure;
import net.minecraft.command.*;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.util.text.*;
import net.minecraft.util.text.event.ClickEvent;
import net.minecraft.world.IStructureReader;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.*;
import net.minecraft.world.gen.feature.structure.*;
import net.minecraft.world.gen.settings.StructureSeparationSettings;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.*;

/** Search aliases index native gym starts; they are never added to biome generation. */
public final class GymLocator {
    public static final DeferredRegister<Structure<?>> ALIASES=DeferredRegister.create(ForgeRegistries.STRUCTURE_FEATURES,Content.NS);
    static {for(String type:GymThemePaths.TYPES)ALIASES.register(type+"_gym",()->new TypedGym(type));TeraDenLocator.registerAliases(ALIASES);}
    /** Forge registry IDs alone do not populate the legacy structure NBT name map. */
    public static void registerNames(){
        int repaired=0;
        for(var entry:ALIASES.getEntries()){
            Structure<?> value=entry.get();String key=entry.getId().toString();
            Structure<?> previous=Structure.field_236365_a_.get(key);
            if(previous!=null && previous!=value)throw new IllegalStateException("Conflicting structure save name "+key);
            if(value.func_143025_a()==null)repaired++;
            Structure.field_236365_a_.put(key,value);
        }
        org.apache.logging.log4j.LogManager.getLogger("pixelmon_backport").info("Registered {} structure save names; repaired {} missing legacy names",ALIASES.getEntries().size(),repaired);
    }
    private final Map<CommandSource,Search> jobs=new LinkedHashMap<>();
    public static final class TypedGym extends GymStructure {
        public final String type;
        TypedGym(String type){super(GymConfig.CODEC);this.type=type;}
        @Override public boolean isSuitable(Biome.Category category,String name){return false;}
    }
    public static Structure<?> nativeGym(){return PixelmonStructures.GYMS.get();}
    public static StructureSeparationSettings settings(ServerWorld world){return world.func_72863_F().func_201711_g().func_235957_b_().func_236197_a_(nativeGym());}
    public static boolean available(ServerWorld world){
        return world.func_73046_m().func_240793_aU_().func_230418_z_().func_236222_c_()
            && settings(world)!=null && world.func_72863_F().func_201711_g().func_202090_b().func_205004_a(nativeGym());
    }
    public static boolean matches(StructureStart<?> start,String type){
        if(start==null || !start.func_75069_d())return false;
        List<String> paths=new ArrayList<>();for(StructurePiece piece:start.func_186161_c())paths.add(piece.toString());
        return type.equals(GymThemePaths.classify(paths));
    }
    public static boolean available(ServerWorld world,String type){
        if(!available(world))return false;
        for(Biome biome:world.func_72863_F().func_201711_g().func_202090_b().func_235203_c_())
            for(var supplier:biome.func_242440_e().func_242487_a()){
                var feature=supplier.get();
                if(feature.field_236268_b_==nativeGym() && feature.field_236269_c_ instanceof GymConfig){
                    String pool=((GymConfig)feature.field_236269_c_).func_242810_c().get().func_214947_b().toString();
                    Set<String> configured=GymThemePaths.poolTypes(pool);
                    // Custom datapacks may use an unknown pool; inspect real starts in that case.
                    if(configured.isEmpty() || configured.contains(type))return true;
                }
            }
        return false;
    }
    /** Also used by the optional compass adapter; query the existing gym index. */
    public static StructureStart<?> filteredStart(StructureManager manager,SectionPos pos,Structure<?> requested,IStructureReader chunk){
        StructureStart<?> start=manager.func_235013_a_(pos,requested instanceof TypedGym?nativeGym():requested,chunk);
        return requested instanceof TypedGym && !matches(start,((TypedGym)requested).type)?null:start;
    }
    @SubscribeEvent public void commands(RegisterCommandsEvent event){
        var command=Commands.func_197057_a("locategym");
        for(int i=0;i<GymThemePaths.TYPES.length;i++){
            final String type=GymThemePaths.TYPES[i];
            for(String spelling:new String[]{type,GymThemePaths.CHINESE[i]})command.then(Commands.func_197057_a(spelling)
                .executes(ctx->begin(ctx,type,8192)).then(Commands.func_197056_a("radius",IntegerArgumentType.integer(512,32768))
                .executes(ctx->begin(ctx,type,IntegerArgumentType.getInteger(ctx,"radius")))));
        }
        command.then(Commands.func_197057_a("cancel").executes(ctx->{jobs.entrySet().removeIf(e->sameSource(e.getKey(),ctx.getSource()));return 1;}));
        event.getDispatcher().register(command);
    }
    private static boolean sameSource(CommandSource a,CommandSource b){return a.func_197022_f()!=null?a.func_197022_f()==b.func_197022_f():a.func_197037_c().equals(b.func_197037_c());}
    private int begin(CommandContext<CommandSource> ctx,String type,int radius){
        CommandSource source=ctx.getSource();ServerWorld world=source.func_197023_e();
        if(!available(world,type)){source.func_197021_a(new TranslationTextComponent("backport.gym.locate.unavailable"));return 0;}
        jobs.entrySet().removeIf(e->sameSource(e.getKey(),source));
        jobs.put(source,new Search(world,new BlockPos(source.func_197036_d()),type,radius));
        source.func_197030_a(new TranslationTextComponent("backport.gym.locate.searching",GymThemePaths.chinese(type),radius),false);return 1;
    }
    @SubscribeEvent public void unload(WorldEvent.Unload event){jobs.entrySet().removeIf(e->e.getValue().world==event.getWorld());}
    @SubscribeEvent public void tick(TickEvent.ServerTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;
        long deadline=System.nanoTime()+8_000_000L;
        for(Iterator<Map.Entry<CommandSource,Search>> it=jobs.entrySet().iterator();it.hasNext();){
            Map.Entry<CommandSource,Search> entry=it.next();CommandSource source=entry.getKey();Search search=entry.getValue();
            if(source.func_197022_f()!=null && (source.func_197022_f().field_70128_L || source.func_197022_f().field_70170_p!=search.world)){it.remove();continue;}
            // At most one structure-start chunk per job per tick; never materialize city blocks.
            BlockPos result=search.step();
            if(result!=null){
                String tp="/tp @s "+result.func_177958_n()+" ~ "+result.func_177952_p();
                IFormattableTextComponent message=new TranslationTextComponent("backport.gym.locate.found",GymThemePaths.chinese(search.type),result.func_177958_n(),result.func_177952_p())
                    .func_240699_a_(TextFormatting.GREEN).func_240700_a_(style->style.func_240715_a_(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND,tp)));
                source.func_197030_a(message,false);it.remove();
            }else if(search.finished()){source.func_197021_a(new TranslationTextComponent("backport.gym.locate.missing",GymThemePaths.chinese(search.type),search.radius));it.remove();}
            if(System.nanoTime()>=deadline)break;
        }
    }
    private static final class Search {
        final ServerWorld world;final String type;final int radius;final List<ChunkPos> candidates=new ArrayList<>();final BlockPos origin;int cursor;
        Search(ServerWorld world,BlockPos origin,String type,int radius){
            this.world=world;this.origin=origin;this.type=type;this.radius=radius;
            StructureSeparationSettings settings=settings(world);int spacing=settings.func_236668_a_();
            int cx=Math.floorDiv(origin.func_177958_n()>>4,spacing),cz=Math.floorDiv(origin.func_177952_p()>>4,spacing),range=radius/(spacing*16)+2;
            SharedSeedRandom random=new SharedSeedRandom();
            for(int x=cx-range;x<=cx+range;x++)for(int z=cz-range;z<=cz+range;z++){
                ChunkPos pos=nativeGym().func_236392_a_(settings,world.func_72905_C(),random,x*spacing,z*spacing);
                if(distance(pos)<=(long)(radius+16)*(radius+16))candidates.add(pos);
            }
            candidates.sort(Comparator.comparingLong(this::distance));
        }
        long distance(ChunkPos p){long dx=(long)p.field_77276_a*16+8-origin.func_177958_n(),dz=(long)p.field_77275_b*16+8-origin.func_177952_p();return dx*dx+dz*dz;}
        boolean finished(){return cursor>=candidates.size();}
        BlockPos step(){
            if(finished())return null;ChunkPos pos=candidates.get(cursor++);
            IChunk chunk=world.func_217348_a(pos.field_77276_a,pos.field_77275_b,ChunkStatus.field_222606_b);
            StructureStart<?> start=world.func_241112_a_().func_235013_a_(SectionPos.func_218156_a(pos,0),nativeGym(),(IStructureReader)chunk);
            if(!matches(start,type))return null;
            BlockPos found=start.func_204294_a();long dx=(long)found.func_177958_n()-origin.func_177958_n(),dz=(long)found.func_177952_p()-origin.func_177952_p();
            return dx*dx+dz*dz<=(long)radius*radius?found:null;
        }
    }
}
