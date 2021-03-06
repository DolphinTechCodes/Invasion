package invasion.nexus;

import invasion.Invasion;
import invasion.init.ModBlocks;
import invasion.init.ModItems;
import invasion.init.ModSounds;
import invasion.tileentity.NexusTileEntity;
import mcp.MethodsReturnNonnullByDefault;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.util.DamageSource;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.Region;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toCollection;

//TODO refactor this class into individual classes for each invasion mode, but this is a story for another time

/**
 * The Nexus class is responsible for spawning enemies and managing hp and stuff. However, some logic is located in 'NexusTileEntity'
 */
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public class Nexus extends WorldSavedData {

    //@CapabilityInject(Nexus.class)
    //public static Capability<Nexus> INVASION_CAPABILITY = null;
    public static final int MAX_RADIUS = 128, MIN_RADIUS = 32;
    private static final String DATA_NAME = "nexus";
    private static Nexus instance;

    private final int MAX_HP = 100;

    //private static final long BIND_EXPIRE_TIME = 300000L;
    //RM private final WaveSpawner waveSpawner;
    //RM private final WaveBuilder waveBuilder = new WaveBuilder();
    //RM private final AttackerAI attackerAI;
    private final World world;
    //RM private List<EntityIMLiving> mobList = new ArrayList<>();
    // The following fields are null when no invasion is happening (and may be set when starting an invasion or reading from NBT)
    private NexusTileEntity nexusTE = null;
    private BlockPos pos = null;
    private AxisAlignedBB boundingBoxToRadius = null;
    private Region sourroundingArea = null;
    private Wave_ wave;

    // The following fields are stored in NBT
    private int hp;
    private int level;
    private List<UUID> boundPlayers = new ArrayList<>();
    private NexusMode mode;
    private boolean happening;
    private int waveNumber;
    private int spawnRadius = 52;
    private int kills; //Maybe move to nexus TE
    private int mobsLeftInWave;


    private int powerLevel;
    private int lastPowerLevel;
    private int powerLevelTimer;
    // Here
    private long waveSeed;
    private int lastMobsLeftInWave; // Here
    private int mobsToKillInWave;   // Here
    private int nextAttackTime;
    private int daysToAttack;       //Here
    private long lastWorldTime;
    private int zapTimer;
    private int errorState;
    private int tickCount;
    private int immuneTicks = 0;    //DarthXenon: Cooldown between attacks

    private int cleanupTimer;
    //private long spawnerElapsedRestore;
    private long timer;
    private long waveDelayTimer = -1L;
    private long waveDelay;
    private boolean continuousAttack;
    private boolean mobsSorted;
    private boolean resumedFromNBT;


    public Nexus(World world) {
        super(DATA_NAME);
        this.world = world;
        //RM waveSpawner = new WaveSpawner(this, spawnRadius);
        //boundingBoxToRadius = new AxisAlignedBB(pos.getX(), pos.getY(), pos.getZ(), pos.getX(), pos.getY(), pos.getZ());
        /*boundingBoxToRadius = new AxisAlignedBB(
                pos.getX() - (spawnRadius + 10), pos.getY() - (spawnRadius + 40), pos.getZ() - (spawnRadius + 10),
                pos.getX() + (spawnRadius + 10), pos.getY() + (spawnRadius + 40), pos.getZ() + (spawnRadius + 10));*/
        //RM attackerAI = new AttackerAI(this);

    }

    /**
     * Retrieves a Nexus instance from a world. if an instance is already loaded, it will be returned.
     *
     * @param worldIn The world from which the Nexus is retrieved. It must not be remote.
     * @return A Nexus instance. It either is loaded from worldIn directly or cached from previous calls.
     */
    public static Nexus get(World worldIn) {
        if (worldIn.isRemote) throw new IllegalStateException("Tried to retrieve WorldSavedData remotely");
        if (instance == null) {
            instance = ((ServerWorld) worldIn).getSavedData().getOrCreate(() -> new Nexus(worldIn), DATA_NAME);
        }
        return instance;
    }

    /*
    public static Nexus in(World world) {
        return world.getCapability(INVASION_CAPABILITY).orElseThrow(() -> new IllegalStateException("Tried to retrieve capability from client side"));
    }

     */

    /**
     * Called every tick and handles spawning and various checks
     *
     * @param event The event dataAngle
     */
    @SubscribeEvent
    public void update(TickEvent.WorldTickEvent event) {
        if (world.isRemote) return;

        world.getProfiler().startSection("nexus");

        if (immuneTicks > 0) immuneTicks--;
        if (hp <= 0) {
            hp = 0;
            theEnd();

        } else {
            updateStatus();
            updateAI();

            if ((mode == NexusMode.WAVE_INVASION) || (mode == NexusMode.CONTINUOUS_INVASION) || (mode == NexusMode.MODE_3)) {
                if (resumedFromNBT) {
                    onResume();
                    resumedFromNBT = false;
                }
                try {
                    tickCount++;
                    if (tickCount == 60) {
                        tickCount -= 60;
                        bindPlayers();
                        updateMobList();
                    }

                    if ((mode == NexusMode.WAVE_INVASION) || (mode == NexusMode.MODE_3))
                        doInvasion(50);
                    else if (mode == NexusMode.CONTINUOUS_INVASION)
                        doContinuous(50);
                } catch (WaveSpawnerException e) {
                    Invasion.logger.fatal("Error while spawning: {}", e.getMessage());
                    e.printStackTrace();
                    stop();
                }
            }

            if (cleanupTimer++ > 40) {
                cleanupTimer = 0;
                if (world.getBlockState(pos).getBlock() != ModBlocks.NEXUS.get()) {
                    stop();
                    Invasion.logger.warn("Stranded nexus entity trying to delete itself...");
                }
            }
        }
        world.getProfiler().endSection();
    }

    private void onResume() {
        Invasion.logger.info("Resuming invasion");
        boundingBoxToRadius = new AxisAlignedBB(pos.getX() - (spawnRadius + 10), 0.0D,
                pos.getZ() - (spawnRadius + 10),
                pos.getX() + (spawnRadius + 10), 127.0D,
                pos.getZ() + (spawnRadius + 10));
        if ((mode == NexusMode.CONTINUOUS_INVASION) && (continuousAttack)) {
            if (resumeSpawnerContinuous()) {
                //RM mobsLeftInWave = (lastMobsLeftInWave += acquireEntities());
                Invasion.logger.debug("mobsLeftInWave: {}", mobsLeftInWave);
                Invasion.logger.debug("mobsToKillInWave: {}", mobsToKillInWave);
            }
        } else {
            resumeSpawnerInvasion();
            //RM acquireEntities();
        }
        //RM  attackerAI.onResume();
    }

    /**
     * Force-stop any ongoing invasion
     */
    public void emergencyStop() {
        Invasion.logger.info("Nexus manually stopped by command");
        stop();
        //RM killAllMobs();
    }

    /**
     * Prints debug messages to player chats
     */
    public void debugStatus() {
        forEachBoundPlayer(p -> p.sendMessage(new StringTextComponent("Current Time: " + world.getGameTime())));
        forEachBoundPlayer(p -> p.sendMessage(new StringTextComponent("Time to next: " + nextAttackTime)));
        forEachBoundPlayer(p -> p.sendMessage(new StringTextComponent("Days to attack: " + daysToAttack)));
        forEachBoundPlayer(p -> p.sendMessage(new StringTextComponent("Mobs left: " + mobsLeftInWave)));
        forEachBoundPlayer(p -> p.sendMessage(new StringTextComponent("Mode: " + mode)));
    }

    /**
     * Used to start a wave invasion via commands
     *
     * @param startWave  the wave number to start at
     * @param tileEntity the NexusTileEntity to bind to
     */
    public void debugStartInvasion(int startWave, NexusTileEntity tileEntity) {
        startInvasion(startWave, tileEntity);
        happening = true;
    }

    /**
     * Used to start a continuous invasion via commands
     *
     * @param tileEntity the NexusTileEntity to bind to
     */
    public void debugStartContinuous(NexusTileEntity tileEntity) {
        startContinuousPlay(tileEntity);
        happening = true;
    }

    /**
     * Called to signalize the death of an enemy
     */
    public void registerMobDied() {
        kills += 1;
        mobsLeftInWave -= 1;
        if (mobsLeftInWave <= 0) {
            if (lastMobsLeftInWave > 0) {
                forEachBoundPlayer(p -> p.sendMessage(new TranslationTextComponent("message.nexus.stable")));
                forEachBoundPlayer(p -> p.sendMessage(new TranslationTextComponent("message.nexus.energy_blast")));
                lastMobsLeftInWave = mobsLeftInWave;
            }
            return;
        }
        while (mobsLeftInWave + mobsToKillInWave * 0.1F <= lastMobsLeftInWave) {
            forEachBoundPlayer(p -> p.sendMessage(new TranslationTextComponent("message.nexus.stabilized_to", (100 - 100 * mobsLeftInWave / mobsToKillInWave))));
            lastMobsLeftInWave = ((int) (lastMobsLeftInWave - mobsToKillInWave * 0.1F));
        }
    }

    public NexusMode getMode() {
        return mode;
    }

    public void setMode(NexusMode mode) {
        this.mode = mode;
    }

    public int getSpawnRadius() {
        return spawnRadius;
    }

    /**
     * Sets the spawn radius. Only works if the Nexus is active.
     *
     * @param radius the new radius (must be greater than 8)
     */
    public void setSpawnRadius(int radius) {
        //RM  if ((!waveSpawner.isActive()) && (radius > 8)) {
        //RM      spawnRadius = radius;
        //RM     waveSpawner.setRadius(radius);
        //RM    boundingBoxToRadius = new AxisAlignedBB(pos.getX() - (spawnRadius + 10), 0.0D, pos.getZ() - (spawnRadius + 10),
        //RM             pos.getX() + (spawnRadius + 10), 127.0D, pos.getZ() + (spawnRadius + 10));
        //RM }

    }

    public int getPowerLevel() {
        return powerLevel;
    }

    public int getX() {
        return pos.getX();
    }

    public int getY() {
        return pos.getY();
    }

    public int getZ() {
        return pos.getZ();
    }

    //RM public List<EntityIMLiving> getMobList() {
    //RM    return mobList;
    //RM }

    public int getNexusPowerLevel() {
        return powerLevel;
    }

    public void setNexusPowerLevel(int i) {
        powerLevel = i;
    }

    public int getWaveNumber() {
        return waveNumber;
    }

    public int getKills() {
        return kills;
    }

    /*
    @Override
    public void readFromNBT(NBTTagCompound nbttagcompound) {
        ModLogger.logDebug("Restoring Invasion from NBT");
        super.readFromNBT(nbttagcompound);
        // added 0 to gettaglist, because it asked an int
        NBTTagList nbttaglist = nbttagcompound.getTagList("Items", 0);
        nexusItemStacks = new ItemStack[getSizeInventory()];
        for (int i = 0; i < nbttaglist.tagCount(); i++) {
            NBTTagCompound nbttagcompound1 = nbttaglist
                    .getCompoundTagAt(i);
            byte byte0 = nbttagcompound1.getByte("Slot");
            if ((byte0 >= 0) && (byte0 < nexusItemStacks.length)) {
                nexusItemStacks[byte0] = new ItemStack(nbttagcompound1);
            }

        }

        // added 0 to gettaglist, because it asked an int
        nbttaglist = nbttagcompound.getTagList("boundPlayers", 0);
        for (int i = 0; i < nbttaglist.tagCount(); i++) {
            boundPlayers.add(nbttaglist.getCompoundTagAt(i).getString("name"));
            ModLogger.logDebug("Added bound player: " + nbttaglist.getCompoundTagAt(i).getString("name"));
        }

        activationTimer = nbttagcompound.getShort("activationTimer");
        mode = nbttagcompound.getInteger("mode");
        currentWave = nbttagcompound.getShort("currentWave");
        spawnRadius = nbttagcompound.getShort("spawnRadius");
        nexusLevel = nbttagcompound.getShort("nexusLevel");
        hp = nbttagcompound.getShort("hp");
        nexusKills = nbttagcompound.getInteger("nexusKills");
        generation = nbttagcompound.getShort("generation");
        powerLevel = nbttagcompound.getInteger("powerLevel");
        lastPowerLevel = nbttagcompound.getInteger("lastPowerLevel");
        nextAttackTime = nbttagcompound.getInteger("nextAttackTime");
        daysToAttack = nbttagcompound.getInteger("daysToAttack");
        continuousAttack = nbttagcompound.getBoolean("continuousAttack");
        happening = nbttagcompound.getBoolean("activated");

        NexusBlock.setBlockView(happening, getWorld(), getPos());


        ModLogger.logDebug("activationTimer = " + activationTimer);
        ModLogger.logDebug("mode = " + mode);
        ModLogger.logDebug("currentWave = " + currentWave);
        ModLogger.logDebug("spawnRadius = " + spawnRadius);
        ModLogger.logDebug("nexusLevel = " + nexusLevel);
        ModLogger.logDebug("hp = " + hp);
        ModLogger.logDebug("nexusKills = " + nexusKills);
        ModLogger.logDebug("powerLevel = " + powerLevel);
        ModLogger.logDebug("lastPowerLevel = " + lastPowerLevel);
        ModLogger.logDebug("nextAttackTime = " + nextAttackTime);

        waveSpawner.setRadius(spawnRadius);
        if ((mode == 1) || (mode == 3) || ((mode == 2) && (continuousAttack))) {
            ModLogger.logDebug("Nexus is active; flagging for restore");
            resumedFromNBT = true;
            spawnerElapsedRestore = nbttagcompound.getLong("spawnerElapsed");
            ModLogger.logDebug("spawnerElapsed = " + spawnerElapsedRestore);
        }

        attackerAI.readFromNBT(nbttagcompound);
    }



    @Override
    public CompoundNBT writeToNBT(CompoundNBT nbttagcompound) {
        super.writeToNBT(nbttagcompound);
        nbttagcompound.putShort("activationTimer", (short) activationTimer);
        nbttagcompound.putShort("currentWave", (short) currentWave);
        nbttagcompound.putShort("spawnRadius", (short) spawnRadius);
        nbttagcompound.putLong("spawnerElapsed", waveSpawner.getElapsedTime());
        nbttagcompound.putInt("mode", mode);
        nbttagcompound.putInt("powerLevel", powerLevel);
        nbttagcompound.putInt("lastPowerLevel", lastPowerLevel);
        nbttagcompound.putInt("nextAttackTime", nextAttackTime);
        nbttagcompound.putInt("daysToAttack", daysToAttack);
        nbttagcompound.putBoolean("continuousAttack", continuousAttack);
        nbttagcompound.putBoolean("activated", happening);

        NBTTagList nbttaglist = new NBTTagList();
        for (int i = 0; i < nexusItemStacks.length; i++) {
            if (nexusItemStacks[i] != null) {
                NBTTagCompound nbttagcompound1 = new NBTTagCompound();
                nbttagcompound1.setByte("Slot", (byte) i);
                nexusItemStacks[i].writeToNBT(nbttagcompound1);
                nbttaglist.appendTag(nbttagcompound1);
            }
        }
        nbttagcompound.setTag("Items", nbttaglist);

        NBTTagList nbttaglist2 = new NBTTagList();
        for (String playerName : boundPlayers.toArray(new String[boundPlayers.size()])) {
            NBTTagCompound nbttagcompound1 = new NBTTagCompound();
            nbttagcompound1.setString("name", playerName);
            nbttaglist2.appendTag(nbttagcompound1);
        }
        nbttagcompound.setTag("boundPlayers", nbttaglist2);

        attackerAI.writeToNBT(nbttagcompound);
        return nbttagcompound;
    }

     */

    /**
     * Reads data from NBT
     *
     * @param nbt the data as NBT
     */
    @Override
    public void read(@Nonnull CompoundNBT nbt) {
        Invasion.logger.debug("Restoring nexus from NBT...");
        //super.read(nbt);

        happening = nbt.getBoolean("happening");
        if (happening) {
            pos = new BlockPos(nbt.getInt("x"), nbt.getInt("x"), nbt.getInt("x"));
            nexusTE = (NexusTileEntity) world.getTileEntity(pos);
            Invasion.logger.debug("An invasion is happening and restored Tile entity from NBT: {}, {}, {}", pos.getX(), pos.getY(), pos.getZ());
        }
        hp = nbt.getInt("hp");
        level = nbt.getInt("level");
        powerLevel = nbt.getInt("powerLevel");
        waveNumber = nbt.getInt("currentWave");
        waveSeed = nbt.getLong("waveSeed");
        spawnRadius = nbt.getInt("spawnRadius");
        //RM waveSpawner.setRadius(spawnRadius);
        boundingBoxToRadius = new AxisAlignedBB(
                pos.getX() - (spawnRadius + 10),
                pos.getY() - (spawnRadius + 40),
                pos.getZ() - (spawnRadius + 10),
                pos.getX() + (spawnRadius + 10),
                pos.getY() + (spawnRadius + 40),
                pos.getZ() + (spawnRadius + 10));

        boundPlayers = nbt.getList("boundPlayers", Constants.NBT.TAG_COMPOUND).stream().map((compound) -> new UUID(
                ((CompoundNBT) compound).getLong("UUIDMost"),
                ((CompoundNBT) compound).getLong("UUIDLeast")
        )).collect(Collectors.toList());

        Invasion.logger.debug("Restored invasion from NBT with with currentWave: {}, spawnRadius: {}, powerLevel: {}, boundPlayers: {} entries",
                waveNumber, waveNumber, powerLevel, boundPlayers.size());

        //RM attackerAI.read(nbt);
    }

    /**
     * Writes the data of a Nexus to NBT
     *
     * @param nbt the original NBT Compound
     * @return the written NBT Compound
     */
    @Override
    @Nonnull
    public CompoundNBT write(@Nonnull CompoundNBT nbt) {
        //TODO maybe return a new NBT tag
        Invasion.logger.debug("Writing invasion to NBT...");

        nbt.putBoolean("happening", happening);
        if (happening) {
            nbt.putInt("x", pos.getX());
            nbt.putInt("y", pos.getY());
            nbt.putInt("z", pos.getZ());
        }
        nbt.putInt("hp", hp);
        nbt.putInt("level", level);
        nbt.putInt("powerLevel", powerLevel);
        nbt.putInt("currentWave", waveNumber);
        nbt.putLong("waveSeed", waveSeed);
        nbt.putInt("spawnRadius", spawnRadius);

        ListNBT uuids = new ListNBT();
        boundPlayers.forEach((uuid) -> {
            CompoundNBT compound = new CompoundNBT();
            compound.putLong("UUIDMost", uuid.getMostSignificantBits());
            compound.putLong("UUIDLeast", uuid.getLeastSignificantBits());
            uuids.add(compound);
        });
        nbt.put("boundPlayers", uuids);


        return nbt;
    }

    //RM
    /*
    public void askForRespawn(EntityIMLiving entity) {
        Invasion.logger.warn("Stuck entity asking for respawn: {} ({}, {}, {}) ", entity.toString(), entity.getPosX(), entity.getPosY(), entity.getPosZ());
        waveSpawner.askForRespawn(entity);
    }



    public AttackerAI getAttackerAI() {
        return attackerAI;
    }
    */

    //public void setWave(int wave) {
    //    waveNumber = wave;
    //}

    public void startInvasion(int startWave, NexusTileEntity tileEntity) {

        if (happening) {
            Invasion.logger.warn("Tried to start invasion while another is happening.");
            return;
        }

        nexusTE = tileEntity;
        pos = tileEntity.getPos();

        boundingBoxToRadius = new AxisAlignedBB(
                pos.getX() - (spawnRadius + 10),
                pos.getY() - (spawnRadius + 40),
                pos.getZ() - (spawnRadius + 10),
                pos.getX() + (spawnRadius + 10),
                pos.getY() + (spawnRadius + 40),
                pos.getZ() + (spawnRadius + 10));

        if ((mode == NexusMode.OFF) || (mode == NexusMode.CONTINUOUS_INVASION)) {
            //RM
            if (true/* waveSpawner.isReady()*/) {
                try {
                    waveNumber = startWave;
                    //RM waveSpawner.beginNextWave(currentWave);
                    if (mode == NexusMode.OFF) {
                        setMode(NexusMode.WAVE_INVASION);
                    } else {
                        setMode(NexusMode.MODE_3);
                    }
                    bindPlayers();
                    waveDelayTimer = -1L;
                    timer = System.currentTimeMillis();
                    forEachBoundPlayer(p -> p.sendMessage(new TranslationTextComponent("message.wave.first")));
                    //TODO play sound for everyone
                    world.playSound(null, pos, ModSounds.RUMBLE.get(), SoundCategory.AMBIENT, 1.0F, 1.0F);
                    //RM
                } catch (/*WaveSpawnerException*/ Exception e) {
                    stop();
                    Invasion.logger.fatal(e);
                }
            } else {
                Invasion.logger.error("Wave spawner not in ready state");
            }
        } else {
            Invasion.logger.warn("Tried to activate nexus while already active");
        }
        happening = true;
        Invasion.logger.info("Starting invasion at wave {}.", startWave);
    }

    private void startContinuousPlay(NexusTileEntity tileEntity) {

        if (happening) {
            Invasion.logger.warn("Tried to start invasion while another is happening.");
            return;
        }

        nexusTE = tileEntity;
        pos = tileEntity.getPos();

        boundingBoxToRadius = new AxisAlignedBB(
                pos.getX() - (spawnRadius + 10),
                0.0D,
                pos.getZ() - (spawnRadius + 10),
                pos.getX() + (spawnRadius + 10),
                127.0D,
                pos.getZ() + (spawnRadius + 10));
        //RM
        if ((mode == NexusMode.MODE_4) && (true /*waveSpawner.isReady()*/) && (happening)) {
            setMode(NexusMode.CONTINUOUS_INVASION);
            lastPowerLevel = powerLevel;
            lastWorldTime = world.getGameTime();
            nextAttackTime = ((int) (lastWorldTime / 24000L * 24000L) + 14000);
            if ((lastWorldTime % 24000L > 12000L)
                    && (lastWorldTime % 24000L < 16000L)) {
                forEachBoundPlayer(p -> p.sendMessage(new TranslationTextComponent("message.invasion.dusk")));
            } else {
                forEachBoundPlayer(p -> p.sendMessage(new TranslationTextComponent("message.nexus.activated")));
            }
        } else {
            forEachBoundPlayer(p -> p.sendMessage(new TranslationTextComponent("message.nexus.activation_failed")));
        }

        happening = true;
        Invasion.logger.info("Starting continuous invasion.");
    }

    private void doInvasion(int elapsed) throws WaveSpawnerException {
        //RM
        if (true/*waveSpawner.isActive()*/) {
            //RM
            if (true/*waveSpawner.isWaveComplete()*/ && wave.isOver()) {
                if (waveDelayTimer == -1L) {
                    forEachBoundPlayer(p -> p.sendMessage(new TranslationTextComponent("message.wave.complete", waveNumber)));
                    //playSoundForBoundPlayers("invmod:chime1");
                    //TODO play to everyone
                    world.playSound(null, pos, ModSounds.CHIME.get(), SoundCategory.AMBIENT, 1.0F, 1.0F);
                    waveDelayTimer = 0L;
                    //RM waveDelay = waveSpawner.getWaveRestTime();
                } else {
                    waveDelayTimer += elapsed;
                    if (waveDelayTimer > waveDelay) {
                        waveNumber += 1;
                        forEachBoundPlayer(p -> p.sendMessage(new TranslationTextComponent("message.wave.begin", waveNumber)));
                        //RM waveSpawner.beginNextWave(currentWave);
                        waveDelayTimer = -1L;
                        world.playSound(null, pos, ModSounds.RUMBLE.get(), SoundCategory.AMBIENT, 1.0F, 1.0F);
                        level = Math.max(level, waveNumber);

                        waveSeed = world.getRandom().nextLong();
                        wave = WaveGenerator.generate(waveNumber, new Random(waveSeed));
                    }
                }
            } else {
                wave.doSpawns(elapsed, this);
                //RM waveSpawner.spawn(elapsed);
            }
        }
    }


    private void playSoundForBoundPlayers(SoundEvent sound) {
        /* TODO if (getBoundPlayers() != null) {
            for (int i = 0; i < getBoundPlayers().size(); i++) {
                try {
                    EntityPlayerMP player = FMLCommonHandler.instance().getMinecraftServerInstance().getPlayerList().getPlayerByUsername(getBoundPlayers().get(i));
                    if (player != null) player.playSound(sound, 1f, 1f);
                } catch (Exception name) {
                    System.out.println("Problem while trying to play sound at player.");
                }
            }
        }*/
    }

    private void doContinuous(int elapsed) {
        powerLevelTimer += elapsed;
        if (powerLevelTimer > 2200) {
            powerLevelTimer -= 2200;
            nexusTE.generateFlux(5 + (int) (5 * powerLevel / 1550.0F));
            if (nexusTE.isInInputSlot(ModItems.DAMPING_AGENT.get())) {
                powerLevel += 1;
            }
        }

        if (nexusTE.isInInputSlot(ModItems.STRONG_DAMPING_AGENT.get())) {
            if ((powerLevel >= 0) && (!continuousAttack)) {
                powerLevel -= 1;
                if (powerLevel < 0) {
                    stop();
                }
            }
        }

        if (!continuousAttack) {
            long currentTime = world.getGameTime();
            if ((world.getDayTime() < 12000L) && (currentTime % 24000L >= 12000L) && (currentTime + 12000L > nextAttackTime)) {
                forEachBoundPlayer(p -> p.sendMessage(new TranslationTextComponent("message.invasion.dusk")));
            }
            if (lastWorldTime > currentTime) {
                nextAttackTime = ((int) (nextAttackTime - (lastWorldTime - currentTime)));
            }
            lastWorldTime = currentTime;

            if (lastWorldTime >= nextAttackTime) {
                float difficulty = 1.0F + (float) powerLevel / 4500;
                float tierLevel = 1.0F + (float) powerLevel / 4500;
                int timeSeconds = 240;
                try {
                    //RM Wave wave = waveBuilder.generateWave(difficulty,
                    //RM       tierLevel, timeSeconds);
                    //RM  mobsLeftInWave = (lastMobsLeftInWave = mobsToKillInWave = (int) (wave
                    //RM          .getTotalMobAmount() * 0.8F));
                    //RM waveSpawner.beginNextWave(wave);
                    continuousAttack = true;
                    int days = 2; //RM world.rand.nextInt(1
                    //RM  + Config.MAX_DAYS_BETWEEN_ATTACKS_CONTINIOUS_MODE
                    //RM - Config.MIN_DAYS_BETWEEN_ATTACKS_CONTINIOUS_MODE);
                    nextAttackTime = ((int) (currentTime / 24000L * 24000L) + 14000 + days * 24000);
                    hp = MAX_HP;
                    zapTimer = 0;
                    waveDelayTimer = -1L;
                    forEachBoundPlayer(p -> {
                        p.sendMessage(new TranslationTextComponent("message.nexus_destabilized"));
                        p.playSound(ModSounds.RUMBLE.get(), 1.9f, 1.0f);
                    });
                    //RM
                } catch (Exception /* WaveSpawnerException*/ e) {
                    Invasion.logger.error("Spawn error: {}", e.getMessage());
                    stop();
                }

            }

        } else if (hp <= 0) {
        } else if (hp <= 0) {
            continuousAttack = false;
            continuousNexusHurt();
            //RM
        } else if (true /*waveSpawner.isWaveComplete()*/) {

            if (waveDelayTimer == -1L) {
                waveDelayTimer = 0L;
                //RM   waveDelay = waveSpawner.getWaveRestTime();
            } else {

                waveDelayTimer += elapsed;
                if ((waveDelayTimer > waveDelay)
                        && (zapTimer < -200)) {
                    waveDelayTimer = -1L;
                    continuousAttack = false;
                    //RM    waveSpawner.stop();
                    hp = MAX_HP;
                    lastPowerLevel = powerLevel;
                }
            }

            zapTimer -= 1;
            if (mobsLeftInWave <= 0) {
                //RM  if ((zapTimer <= 0) && (zapEnemy(1))) {
                //RM    zapEnemy(0);
                //RM    zapTimer = 23;
                //RM}
            }
        } else {
            //RM  try {
            //RM    waveSpawner.spawn(elapsed);
            //RM } catch (WaveSpawnerException e) {
            //RM     Invasion.logger.error("Spawn error: {}", e.getMessage());
            //RM     stop();
            //RM }
        }
    }

    private void updateStatus() {
/*
    } else if((mode ==0)||(mode ==4))

    {
        if (nexusItemStacks[0] != null) {
            if ((nexusItemStacks[0].getItem() == BlocksAndItems.itemNexusCatalyst)
                    || (nexusItemStacks[0].getItem() == BlocksAndItems.itemStrongCatalyst)) {
                activationTimer += 1;
                mode = 0;
            } else if (nexusItemStacks[0].getItem() == BlocksAndItems.itemStableNexusCatalyst) {
                activationTimer += 1;
                mode = 4;
            }
        } else {
            activationTimer = 0;
        }
    } else if(mode ==2)

    {
        if (nexusItemStacks[0] != null) {
            if ((nexusItemStacks[0].getItem() == BlocksAndItems.itemNexusCatalyst)
                    || (nexusItemStacks[0].getItem() == BlocksAndItems.itemStrongCatalyst)) {
                activationTimer += 1;
            }
        } else
            activationTimer = 0;
    }

 */

    }

    private void stop() {
        if (mode == NexusMode.MODE_3) {
            setMode(NexusMode.CONTINUOUS_INVASION);
            int days = 2; //RM world.rand.nextInt(1
            //RM  + Config.MAX_DAYS_BETWEEN_ATTACKS_CONTINIOUS_MODE
            //RM  - Config.MIN_DAYS_BETWEEN_ATTACKS_CONTINIOUS_MODE);
            nextAttackTime = ((int) (world.getGameTime() / 24000L * 24000L) + 14000 + days * 24000);
        } else {
            setMode(NexusMode.OFF);
        }

        //RM waveSpawner.stop();
        waveNumber = 0;
        errorState = 0;
        happening = false;
        nexusTE.reset();
        forEachBoundPlayer(p -> p.sendMessage(new TranslationTextComponent("message.invasion.end")));
        boundPlayers.clear();

        Invasion.logger.info("Main ended.");

    }

    private void bindPlayers() {
        List<PlayerEntity> players = world.getEntitiesWithinAABB(PlayerEntity.class, boundingBoxToRadius);

        boundPlayers = players.stream().map(Entity::getUniqueID).collect(toCollection(ArrayList::new));

        players.forEach((player) -> {
            ITextComponent name = player.getName();
            Invasion.logger.debug("binding {} to nexus", name.toString());
            forEachBoundPlayer(p -> p.sendMessage(new TranslationTextComponent("message.invasion.bind_player")));
            player.sendMessage(new TranslationTextComponent("message.invasion.bind_you").applyTextStyle(TextFormatting.DARK_RED));
        });

    }

    private void updateMobList() {
        //RM   mobList = world.getEntitiesWithinAABB(EntityIMLiving.class, boundingBoxToRadius);
        mobsSorted = false;
    }

    //RM
    /*
    private int acquireEntities() {
        AxisAlignedBB bb = boundingBoxToRadius
                .expand(10.0D, 128.0D, 10.0D);

        List<InvadingEntity> entities = world.getEntitiesWithinAABB(
                InvadingEntity.class, bb);
        for (InvadingEntity entity : entities) {
            entity.acquiredByNexus(this);
        }
        Invasion.logger.info("Acquired {} entities after state restore", entities.size());
        return entities.size();
    }

     */

    public void theEnd() {
        if (!world.isRemote) {
            forEachBoundPlayer(p -> {
                p.sendMessage(new TranslationTextComponent("message.nexus.destroyed"));
                p.playSound(SoundEvents.ENTITY_GENERIC_EXPLODE, 1.0f, 1.0f);
                p.attackEntityFrom(DamageSource.MAGIC, Float.MAX_VALUE);
            });
            stop();
            //RM killAllMobs();
        }
    }

    private void continuousNexusHurt() {
        forEachBoundPlayer(p -> {
            p.sendMessage(new TranslationTextComponent("message.nexus.damaged"));
            p.playSound(SoundEvents.ENTITY_GENERIC_EXPLODE, 1.0f, 1.0f);
        });
        //RM killAllMobs();
        //RM waveSpawner.stop();
        powerLevel = ((int) ((powerLevel - (powerLevel - lastPowerLevel)) * 0.7F));
        lastPowerLevel = powerLevel;
        if (powerLevel < 0) {
            powerLevel = 0;
            stop();
        }
    }

    //RM
    /*
    private void killAllMobs() {
        // monsters
        List<EntityIMLiving> mobs = world.getEntitiesWithinAABB(EntityIMLiving.class, boundingBoxToRadius);
        for (EntityIMLiving mob : mobs)
            mob.attackEntityFrom(DamageSource.MAGIC, Float.MAX_VALUE);

        // TODO wolves
       /* List<EntityIMWolf> wolves = world.getEntitiesWithinAABB(EntityIMWolf.class, boundingBoxToRadius);
        for (EntityIMWolf wolf : wolves)
            wolf.attackEntityFrom(DamageSource.MAGIC, Float.MAX_VALUE);*/
    //}

    //RM
    /*
    private boolean zapEnemy(int sfx) {
        if (mobList.size() > 0) {
            if (!mobsSorted) {
                mobList.sort(new ComparatorEntityDistance(pos.getX(), pos.getY(), pos.getZ()));
            }
            EntityIMLiving mob = mobList.remove(mobList.size() - 1);
            mob.attackEntityFrom(DamageSource.MAGIC, 500.0F);
            BoltEntity bolt = new BoltEntity(world,
                    pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D,
                    mob.getPosX(), mob.getPosY(), mob.getPosZ(), 15, sfx);
            world.addEntity(bolt);
            return true;
        }

        return false;
    }

     */

    public void attackNexus(int damage) {
        if (immuneTicks != 0) return;
        immuneTicks = 10 + world.rand.nextInt(30);
        int lastHp = hp;
        hp -= damage;
        if (hp <= 0) {
            hp = 0;
            if (mode == NexusMode.WAVE_INVASION) theEnd();
        }
        while (this.hp + 5 <= lastHp) {
            forEachBoundPlayer(p -> p.sendMessage(new TranslationTextComponent("message.nexus_at_hp", lastHp - 5)));
        }
    }

    private boolean resumeSpawnerContinuous() {
        //RMtry {
        //RM  float difficulty = 1.0F + (float) powerLevel / 4500;
        ////RMRM  float tierLevel = 1.0F + (float) powerLevel / 4500;
        //RM   int timeSeconds = 240;
        //RM Wave wave = waveBuilder.generateWave(difficulty, tierLevel, timeSeconds);
        //RM   mobsToKillInWave = ((int) (wave.getTotalMobAmount() * 0.8F));
        //RM  Invasion.logger.info("Original mobs to kill: {}", mobsToKillInWave);
        //RM  mobsLeftInWave = (lastMobsLeftInWave = mobsToKillInWave
        //RM          - waveSpawner.resumeFromState(wave, 0, spawnRadius));
        //RM   return true;
        //RM }//RM catch (WaveSpawnerException e) {
        //RM    Invasion.logger.fatal("Error resuming spawner: {}", e.getMessage());
        //RM    waveSpawner.stop();
        return false;
        //RM }

    }

    private void resumeSpawnerInvasion() {
        //RM  try {
        //RM       waveSpawner.resumeFromState(currentWave, 0, spawnRadius);
        //RM  } catch (WaveSpawnerException e) {
        //RM      Invasion.logger.error("Error resuming spawner: {}", e.getMessage());
        //RM      waveSpawner.stop();
        //RM  }
    }

    public void forEachBoundPlayer(Consumer<PlayerEntity> action) {
        boundPlayers.stream().map(world::getPlayerByUuid).filter(Objects::nonNull).forEach(action);
    }

    private void updateAI() {
        //RM attackerAI.update();
    }

    public List<UUID> getBoundPlayers() {
        return boundPlayers;
    }

    public boolean isActive() {
        return happening;
    }

    private void setActive(boolean flag) {
// TODO: Fix this
//		if (world != null) {
//			int meta = world.getBlockMetadata(pos.getX(), pos.getY(),
//					pos.getZ());
//			if (flag) {
//				world.setBlockMetadataWithNotify(pos.getX(),
//						pos.getY(), pos.getZ(), (meta & 0x4) == 0 ? meta + 4
//								: meta, 3);
//			} else {
//				world.setBlockMetadataWithNotify(pos.getX(),
//						pos.getY(), pos.getZ(), (meta & 0x4) == 4 ? meta - 4
//								: meta, 3);
//			}
//		}
    }

    public Region getSourroundingArea() {
        return sourroundingArea;
    }

    public NexusTileEntity getNexusTE() {
        return nexusTE;
    }

    public BlockPos getPos() {
        return pos;
    }

    public World getWorld() {
        return world;
    }

    public int getLevel() {
        return level;
    }

    public int getHp() {
        return hp;
    }
}