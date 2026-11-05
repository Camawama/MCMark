package net.camacraft.mcmark.detect;

/** Broad categories of performance-related mods MCMark can recognize. */
public enum PerfModCategory {
    RENDERING("Rendering optimizer"),
    SERVER_TICK("Game-logic / tick optimizer"),
    MEMORY("Memory optimizer"),
    CHUNK_GENERATION("Chunk generation / loading optimizer"),
    LIGHTING("Lighting engine optimizer"),
    NETWORK("Network optimizer"),
    LOAD_TIME("Load-time optimizer"),
    PROFILER("Profiler / diagnostics"),
    MISC("Miscellaneous performance mod");

    public final String display;

    PerfModCategory(String display) {
        this.display = display;
    }
}
