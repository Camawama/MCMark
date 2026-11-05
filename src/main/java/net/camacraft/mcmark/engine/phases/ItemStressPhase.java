package net.camacraft.mcmark.engine.phases;

import net.camacraft.mcmark.engine.ArenaBuilder;
import net.camacraft.mcmark.engine.BenchmarkContext;
import net.camacraft.mcmark.engine.BenchmarkPhase;
import net.camacraft.mcmark.engine.PhaseResult;
import net.camacraft.mcmark.spec.BenchmarkSpec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Item-entity stress. The initial population is 70% non-stackable items (they never merge, so
 * the entity count — and physics/collision load — stays constant) plus 30% stackables; waves of
 * stackables dropped on a fixed schedule during measurement keep the merge logic continuously
 * exercised.
 */
public class ItemStressPhase extends BenchmarkPhase {
    private int dropped;
    private int initialCount;

    public ItemStressPhase() {
        super("item_stress", "Item Stress");
    }

    @Override
    public void begin(BenchmarkContext ctx) {
        dropped = 0;
        initialCount = ctx.count(BenchmarkSpec.ITEM_INITIAL);
    }

    @Override
    protected boolean setupTick(BenchmarkContext ctx, int tick) {
        for (int i = 0; i < BenchmarkSpec.ITEM_SPAWNS_PER_TICK && dropped < initialCount; i++, dropped++) {
            ItemStack stack = dropped % 10 < 7
                    ? new ItemStack(Items.WOODEN_SWORD)
                    : new ItemStack(Items.COBBLESTONE);
            ArenaBuilder.dropItem(ctx.level, stack, ctx.random);
        }
        return dropped >= initialCount;
    }

    @Override
    protected int settleTicks(BenchmarkContext ctx) {
        return ctx.ticks(BenchmarkSpec.ITEM_SETTLE_TICKS);
    }

    @Override
    protected int measureTicks(BenchmarkContext ctx) {
        return ctx.ticks(BenchmarkSpec.ITEM_MEASURE_TICKS);
    }

    @Override
    protected void measureTick(BenchmarkContext ctx, int tick) {
        if (tick > 0 && tick % BenchmarkSpec.ITEM_WAVE_INTERVAL_TICKS == 0) {
            int wave = ctx.count(BenchmarkSpec.ITEM_WAVE_SIZE);
            for (int i = 0; i < wave; i++) {
                ArenaBuilder.dropItem(ctx.level, new ItemStack(Items.COBBLESTONE), ctx.random);
            }
        }
    }

    @Override
    protected boolean cleanupTick(BenchmarkContext ctx, int tick) {
        ArenaBuilder.discardItems(ctx.level);
        return true;
    }

    @Override
    public PhaseResult result(BenchmarkContext ctx) {
        PhaseResult r = msptResult();
        r.stats.put("initial_items", (double) initialCount);
        return r;
    }

    @Override
    public void abort(BenchmarkContext ctx) {
        ArenaBuilder.discardItems(ctx.level);
    }
}
