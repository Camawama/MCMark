package net.camacraft.mcmark.engine;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Per-world MCMark state. Tracks which chunk-generation regions have been consumed so terrain
 * generation is only ever measured on truly fresh, spec-defined chunks. Only region 0 (the
 * standard region) yields a standard-eligible chunk-gen result — one per world, which is why
 * fresh benchmark worlds are the recommended workflow.
 */
public class McmarkSavedData extends SavedData {
    private static final String NAME = "mcmark";
    private int nextChunkgenIndex = 0;

    public static McmarkSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(McmarkSavedData::load, McmarkSavedData::new, NAME);
    }

    public static McmarkSavedData load(CompoundTag tag) {
        McmarkSavedData data = new McmarkSavedData();
        data.nextChunkgenIndex = tag.getInt("nextChunkgenIndex");
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("nextChunkgenIndex", nextChunkgenIndex);
        return tag;
    }

    /** Returns the region index for this run and marks it consumed. */
    public int consumeChunkgenRegion() {
        int index = nextChunkgenIndex++;
        setDirty();
        return index;
    }
}
