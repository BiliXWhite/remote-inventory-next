package dev.blinkwhite.remoteinventory.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.core.BlockPos;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

@Environment(EnvType.CLIENT)
public class ContainerReturnTracker
{
    public static final ContainerReturnTracker INSTANCE = new ContainerReturnTracker();

    public record ReturnEntry(String dimension, BlockPos pos, String itemId, int pass) {}

    private final Deque<ReturnEntry> queue = new ArrayDeque<>();

    private ContainerReturnTracker() {}

    public void track(String dimension, BlockPos pos, String itemId)
    {
        this.queue.addLast(new ReturnEntry(dimension, pos, itemId, 0));
    }

    public boolean isEmpty()
    {
        return this.queue.isEmpty();
    }

    public int size()
    {
        return this.queue.size();
    }

    public void remove(ReturnEntry target)
    {
        this.queue.remove(target);
    }

    public List<ReturnEntry> peekAll()
    {
        return new ArrayList<>(this.queue);
    }

    public void clear()
    {
        this.queue.clear();
    }
}