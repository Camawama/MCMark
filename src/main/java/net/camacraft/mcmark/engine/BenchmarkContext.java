package net.camacraft.mcmark.engine;

import net.camacraft.mcmark.ClientHook;
import net.camacraft.mcmark.spec.BenchmarkSpec;
import net.camacraft.mcmark.spec.StandardFlag;
import net.camacraft.mcmark.spec.Suite;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/** Shared state handed to phases: the world, deterministic RNG, suite scaling, and run flags. */
public final class BenchmarkContext {
    public final MinecraftServer server;
    public final ServerLevel level;
    public final Suite suite;
    public final Set<StandardFlag> flags;
    public final ClientHook client;

    @Nullable
    private final UUID playerId;
    /** Re-seeded deterministically by the engine before each phase begins. */
    public Random random = new Random(BenchmarkSpec.WORLD_SEED);

    private int chunkgenCenterCx = BenchmarkSpec.CHUNKGEN_CENTER_CX;
    private int chunkgenCenterCz = BenchmarkSpec.CHUNKGEN_CENTER_CZ;

    public BenchmarkContext(MinecraftServer server, ServerLevel level, Suite suite,
                            Set<StandardFlag> flags, ClientHook client, @Nullable UUID playerId) {
        this.server = server;
        this.level = level;
        this.suite = suite;
        this.flags = flags;
        this.client = client;
        this.playerId = playerId;
    }

    @Nullable
    public ServerPlayer player() {
        return playerId == null ? null : server.getPlayerList().getPlayer(playerId);
    }

    public void reseed(int phaseIndex) {
        random = new Random(BenchmarkSpec.WORLD_SEED * 31 + phaseIndex);
    }

    public int ticks(int standardTicks) {
        return suite.scaleTicks(standardTicks);
    }

    public int count(int standardCount) {
        return suite.scaleCount(standardCount);
    }

    public void addFlag(StandardFlag flag) {
        flags.add(flag);
    }

    /** Runs a command silently with full permissions (used for gamerule management). */
    public void runCommand(String command) {
        server.getCommands().performPrefixedCommand(
                server.createCommandSourceStack().withSuppressedOutput(), command);
    }

    public void setChunkgenCenter(int cx, int cz) {
        this.chunkgenCenterCx = cx;
        this.chunkgenCenterCz = cz;
    }

    public int chunkgenCenterCx() {
        return chunkgenCenterCx;
    }

    public int chunkgenCenterCz() {
        return chunkgenCenterCz;
    }
}
