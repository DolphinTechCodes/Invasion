package invasion.entity.monster;

import invasion.init.ModEntityTypes;
import invasion.nexus.Nexus;
import net.minecraft.block.BlockState;
import net.minecraft.entity.CreatureAttribute;
import net.minecraft.entity.EntityType;
import net.minecraft.util.DamageSource;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import javax.annotation.Nullable;

public class FenSpiderEntity extends InvadingEntity {

    public FenSpiderEntity(EntityType<? extends FenSpiderEntity> type, World world) {
        super(type, world, null);
    }

    public FenSpiderEntity(World world, @Nullable Nexus nexus) {
        super(ModEntityTypes.FEN_SPIDER.get(), world, nexus);
    }

    @Override
    public double getMountedYOffset() {
        //TODO find value
        return 1.1;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.ENTITY_SPIDER_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSourceIn) {
        return SoundEvents.ENTITY_SPIDER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ENTITY_SPIDER_DEATH;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState blockIn) {
        this.playSound(SoundEvents.ENTITY_SPIDER_STEP, 0.15F, 1.0F);
    }

    @Override
    public CreatureAttribute getCreatureAttribute() {
        return CreatureAttribute.ARTHROPOD;
    }
}
