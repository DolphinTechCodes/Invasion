package invasion.init;

import invasion.Invasion;
import invasion.entity.ally.DogEntity;
import invasion.entity.monster.CloakedSkeletonEntity;
import invasion.entity.monster.FenSpiderEntity;
import invasion.entity.monster.ImpEntity;
import invasion.entity.monster.MoulderingCreeperEntity;
import net.minecraft.entity.EntityClassification;
import net.minecraft.entity.EntityType;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

public class ModEntityTypes {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = new DeferredRegister<>(ForgeRegistries.ENTITIES, Invasion.MOD_ID);

    public static final RegistryObject<EntityType<FenSpiderEntity>> FEN_SPIDER = ENTITY_TYPES.register("fen_spider", () -> EntityType.Builder.<FenSpiderEntity>create(FenSpiderEntity::new, EntityClassification.MONSTER).size(1.4F, 0.9F).build(new ResourceLocation(Invasion.MOD_ID, "fen_spider").toString()));
    public static final RegistryObject<EntityType<ImpEntity>> IMP = ENTITY_TYPES.register("imp", () -> EntityType.Builder.<ImpEntity>create(ImpEntity::new, EntityClassification.MONSTER).size(0.5f, 0.5f).build(new ResourceLocation(Invasion.MOD_ID, "imp").toString()));
    public static final RegistryObject<EntityType<MoulderingCreeperEntity>> MOULDERING_CREEPER = ENTITY_TYPES.register("mouldering_creeper", () -> EntityType.Builder.<MoulderingCreeperEntity>create(MoulderingCreeperEntity::new, EntityClassification.MONSTER).size(0.6f, 1.7f).build(new ResourceLocation(Invasion.MOD_ID, "mouldering_creeper").toString()));
    public static final RegistryObject<EntityType<CloakedSkeletonEntity>> CLOAKED_SKELETON = ENTITY_TYPES.register("cloaked_skeleton", () -> EntityType.Builder.<CloakedSkeletonEntity>create(CloakedSkeletonEntity::new, EntityClassification.MONSTER).size(0.6f, 1.99f).build(new ResourceLocation(Invasion.MOD_ID, "cloaked_skeleton").toString()));


    public static final RegistryObject<EntityType<DogEntity>> DOG = ENTITY_TYPES.register("dog", () -> EntityType.Builder.<DogEntity>create(DogEntity::new, EntityClassification.CREATURE).size(0.6f, 0.85f).build(new ResourceLocation(Invasion.MOD_ID, "dog").toString()));

    // public static final RegistryObject<EntityType<BlackArrowEntity>> BLACK_ARROW = ENTITY_TYPES.register("black_arrow", () -> EntityType.Builder.<BlackArrowEntity>create(BlackArrowEntity::new, EntityClassification.MISC).size(0.5f, 0.5f).build(new ResourceLocation(Invasion.MOD_ID, "black_arrow").toString()));
    // public static final RegistryObject<EntityType<BoulderEntity>> BOULDER = ENTITY_TYPES.register("boulder", () -> EntityType.Builder.<BlackArrowEntity>create(BoulderEntity::new, EntityClassification.MISC).size(1.0f,1.0f).build(new ResourceLocation(Invasion.MOD_ID, "boulder").toString()));


}
