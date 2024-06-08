package de.yamayaki.cesium.mixin.core.players;

import de.yamayaki.cesium.api.accessor.DatabaseSetter;
import de.yamayaki.cesium.api.accessor.DatabaseSource;
import de.yamayaki.cesium.api.database.DatabaseSpec;
import de.yamayaki.cesium.api.database.IDBInstance;
import de.yamayaki.cesium.common.lmdb.LMDBInstance;
import de.yamayaki.cesium.common.spec.PlayerDatabaseSpecs;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.stats.ServerStatsCounter;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.PlayerDataStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.io.File;
import java.nio.file.Path;
import java.util.UUID;

@Mixin(PlayerList.class)
public class MixinPlayerList implements DatabaseSource {
    @Shadow
    @Final
    private PlayerDataStorage playerIo;

    @Unique
    private IDBInstance database;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void initCesiumPlayers(MinecraftServer minecraftServer, LayeredRegistryAccess<?> layeredRegistryAccess, PlayerDataStorage playerDataStorage, int i, CallbackInfo ci) {
        final Path path = minecraftServer.getWorldPath(LevelResource.PLAYER_ADVANCEMENTS_DIR).getParent();

        this.database = new LMDBInstance(path, "players", new DatabaseSpec[]{
                PlayerDatabaseSpecs.PLAYER_DATA,
                PlayerDatabaseSpecs.ADVANCEMENTS,
                PlayerDatabaseSpecs.STATISTICS
        });

        ((DatabaseSetter) this.playerIo)
                .cesium$setStorage(this.database);
    }

    @Inject(method = "getPlayerAdvancements", at = @At(value = "INVOKE", target = "Ljava/util/Map;put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", shift = At.Shift.BEFORE), locals = LocalCapture.CAPTURE_FAILHARD)
    private void setAdvancementsStorage(ServerPlayer serverPlayer, CallbackInfoReturnable<PlayerAdvancements> cir, UUID uUID, PlayerAdvancements playerAdvancements, Path path) {
        ((DatabaseSetter) playerAdvancements).cesium$setStorage(this.database);
    }

    @Inject(method = "getPlayerStats", at = @At(value = "INVOKE", target = "Ljava/util/Map;put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", shift = At.Shift.BEFORE), locals = LocalCapture.CAPTURE_FAILHARD)
    private void setStatsStorage(Player player, CallbackInfoReturnable<ServerStatsCounter> cir, UUID uUID, ServerStatsCounter serverStatsCounter, File file, File file2) {
        ((DatabaseSetter) serverStatsCounter).cesium$setStorage(this.database);
    }

    @Override
    public IDBInstance cesium$getStorage() {
        return this.database;
    }
}
