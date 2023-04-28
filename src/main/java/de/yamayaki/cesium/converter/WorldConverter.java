package de.yamayaki.cesium.converter;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import de.yamayaki.cesium.converter.formats.anvil.AnvilChunkStorage;
import de.yamayaki.cesium.converter.formats.anvil.AnvilPlayerStorage;
import de.yamayaki.cesium.converter.formats.cesium.CesiumChunkStorage;
import de.yamayaki.cesium.converter.formats.cesium.CesiumPlayerStorage;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.WorldStem;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.storage.LevelStorageSource;

import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

public class WorldConverter {
    private final Thread thread;

    private final Format newFormat;

    protected final Path playerDataPath;
    protected final List<ResourceKey<Level>> dimensions;
    protected final LevelStorageSource.LevelStorageAccess levelAccess;

    protected volatile boolean running = true;
    protected volatile boolean finished;
    protected volatile String status;

    protected volatile int dimension;

    protected volatile int progressTotal;
    protected volatile int progressCurrent;

    public WorldConverter(Format format, Minecraft minecraft, LevelStorageSource.LevelStorageAccess levelAccess) {
        this.newFormat = format;

        this.playerDataPath = levelAccess.getDimensionPath(Level.OVERWORLD);
        this.levelAccess = levelAccess;

        try (WorldStem worldStem = minecraft.createWorldOpenFlows().loadWorldStem(levelAccess, false)) {
            RegistryAccess.Frozen frozen = worldStem.registries().compositeAccess();
            levelAccess.saveDataTag(frozen, worldStem.worldData());

            Registry<LevelStem> registry = frozen.registryOrThrow(Registries.LEVEL_STEM);
            this.dimensions = registry.registryKeySet().stream().map(Registries::levelStemToLevel).toList();
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }

        this.status = "cesium.converter.loading";
        this.thread = new ThreadFactoryBuilder().setDaemon(true).build().newThread(this::work);
        this.thread.setUncaughtExceptionHandler((thread, throwable) -> {
            this.status = "cesium.converter.failed";
            this.finished = true;
        });
        this.thread.start();
    }

    public void cancel() {
        this.running = false;

        try {
            this.thread.join();
        } catch (InterruptedException ignored) {
        }
    }

    public void work() {
        this.status = "Converting player data";
        this.importPlayerData();

        this.status = "Converting chunk data";
        for (ResourceKey<Level> levelResourceKey : this.dimensions) {
            this.dimension++;
            this.importLevel(levelResourceKey);
        }

        this.finished = true;
    }

    private void importPlayerData() {
        final IPlayerStorage originalStorage = this.getPlayerStorage(this.playerDataPath, true);
        final IPlayerStorage newStorage = this.getPlayerStorage(this.playerDataPath, false);

        final List<UUID> playerList = originalStorage.getAllPlayers();
        final Iterator<UUID> iterator = playerList.iterator();

        this.progressTotal = playerList.size();
        this.progressCurrent = 0;

        while (this.running && iterator.hasNext()) {
            this.progressCurrent++;
            UUID player = iterator.next();

            newStorage.setPlayerNBT(player, originalStorage.getPlayerNBT(player));
            newStorage.setPlayerAdvancements(player, originalStorage.getPlayerAdvancements(player));
            newStorage.setPlayerStatistics(player, originalStorage.getPlayerStatistics(player));
        }

        originalStorage.close();
        newStorage.close();
    }

    private void importLevel(final ResourceKey<Level> level) {
        final Path dimensionPath = this.levelAccess.getDimensionPath(level);

        final IChunkStorage originalStorage = this.getChunkStorage(dimensionPath, true);
        final IChunkStorage newStorage = this.getChunkStorage(dimensionPath, false);

        final List<ChunkPos> chunkList = originalStorage.getAllChunks();
        final Iterator<ChunkPos> iterator = chunkList.iterator();

        this.progressTotal = chunkList.size();
        this.progressCurrent = 0;

        while (this.running && iterator.hasNext()) {
            this.progressCurrent++;
            ChunkPos chunkPos = iterator.next();

            newStorage.setChunkData(chunkPos, originalStorage.getChunkData(chunkPos));
            newStorage.setPOIData(chunkPos, originalStorage.getPOIData(chunkPos));
            newStorage.setEntityData(chunkPos, originalStorage.getEntityData(chunkPos));

            if (this.progressCurrent % 10240 == 0) {
                this.status = "Flushing data ...";
                newStorage.flush();
                this.status = "Converting chunk data";
            }
        }

        originalStorage.close();
        newStorage.close();
    }

    public boolean isFinished() {
        return this.finished;
    }

    public String getStatus() {
        return this.status;
    }

    public int getDimensions() {
        return this.dimensions.size();
    }

    public int getCurrentDim() {
        return this.dimension;
    }

    public int getProgressTotal() {
        return this.progressTotal;
    }

    public int getProgressCurrent() {
        return this.progressCurrent;
    }

    public double getPercentage() {
        return this.getProgressCurrent() / (double) Math.max(this.getProgressTotal(), 1);
    }

    public IChunkStorage getChunkStorage(final Path path, final boolean old) {
        if (!old) {
            return this.newFormat == Format.TO_ANVIL ? new AnvilChunkStorage(path) : new CesiumChunkStorage(path);
        } else {
            return this.newFormat == Format.TO_ANVIL ? new CesiumChunkStorage(path) : new AnvilChunkStorage(path);
        }
    }

    public IPlayerStorage getPlayerStorage(final Path path, final boolean old) {
        if (!old) {
            return this.newFormat == Format.TO_ANVIL ? new AnvilPlayerStorage(path) : new CesiumPlayerStorage(path);
        } else {
            return this.newFormat == Format.TO_ANVIL ? new CesiumPlayerStorage(path) : new AnvilPlayerStorage(path);
        }
    }

    public String getDimName(int key) {
        final int index = key - 1;
        if (index < 0) {
            return "";
        }

        return this.dimensions.get(index).location().toString();
    }

    public enum Format {
        TO_ANVIL,
        TO_CESIUM
    }
}
