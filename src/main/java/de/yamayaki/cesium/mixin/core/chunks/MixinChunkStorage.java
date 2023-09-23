package de.yamayaki.cesium.mixin.core.chunks;

import de.yamayaki.cesium.accessor.DatabaseSetter;
import de.yamayaki.cesium.accessor.SpecificationSetter;
import de.yamayaki.cesium.accessor.DatabaseActions;
import de.yamayaki.cesium.common.db.LMDBInstance;
import de.yamayaki.cesium.common.db.spec.impl.WorldDatabaseSpecs;
import net.minecraft.world.level.chunk.storage.ChunkStorage;
import net.minecraft.world.level.chunk.storage.IOWorker;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(ChunkStorage.class)
public class MixinChunkStorage implements DatabaseSetter, DatabaseActions {
    @Mutable
    @Shadow
    @Final
    private IOWorker worker;

    @Unique
    private LMDBInstance database;

    @Override
    public void cesium$flush() {
        this.database.flushChanges();
    }

    @Override
    public void cesium$close() {
        this.database.close();
    }

    @Override
    public void cesium$setStorage(LMDBInstance lmdbInstance) {
        this.database = lmdbInstance;

        ((DatabaseSetter) this.worker).cesium$setStorage(this.database);
        ((SpecificationSetter) this.worker).cesium$setSpec(WorldDatabaseSpecs.CHUNK_DATA);
    }
}
