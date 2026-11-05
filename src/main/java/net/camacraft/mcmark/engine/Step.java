package net.camacraft.mcmark.engine;

/** A unit of tick-spread work (world edits, chunk loading). Ticked until it reports completion. */
public interface Step {
    /** Performs up to one tick's worth of work. Returns true when the step is complete. */
    boolean tick();

    static Step once(Runnable action) {
        return () -> {
            action.run();
            return true;
        };
    }
}
