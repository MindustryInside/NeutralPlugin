package inside.entry;

import arc.util.Time;
import mindustry.world.Block;

import java.util.concurrent.TimeUnit;

import static pandorum.PandorumPlugin.bundle;

public class RotateEntry implements HistoryEntry{
    protected static final String[] sides;

    static{
        sides = bundle.get("events.history.rotate.all").split(", ");
    }

    public final String name;
    public final Block block;
    public final int rotation;
    public long lastAccessTime = Time.millis();

    public RotateEntry(String name, Block block, int rotation){
        this.name = name;
        this.block = block;
        this.rotation = rotation;
    }

    @Override
    public String getMessage(){
        return bundle.format("events.history.rotate", name, block.name, sides[rotation]);
    }

    @Override
    public long getLastAccessTime(TimeUnit unit){
        return unit.convert(Time.timeSinceMillis(lastAccessTime), TimeUnit.MILLISECONDS);
    }
}
