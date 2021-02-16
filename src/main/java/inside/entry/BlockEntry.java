package inside.entry;

import arc.util.*;
import mindustry.game.EventType.BlockBuildEndEvent;
import mindustry.gen.*;
import mindustry.world.Block;
import pandorum.Misc;

import java.util.concurrent.TimeUnit;

import static pandorum.PandorumPlugin.bundle;

public class BlockEntry implements HistoryEntry{
    public final long lastAccessTime = Time.millis();
    @Nullable
    public final String name;
    public final Unit unit;
    public final Block block;
    public final boolean breaking;
    public final int rotation;

    public BlockEntry(BlockBuildEndEvent event){
        this.unit = event.unit;
        this.name = unit.isPlayer() ? Misc.colorizedName(unit.getPlayer()) : unit.controller() instanceof Player ? Misc.colorizedName(unit.getPlayer()) : null;
        this.block = event.tile.build.block;
        this.breaking = event.breaking;

        this.rotation = event.tile.build.rotation;
    }

    @Override
    public String getMessage(){
        if(breaking){
            return name != null ? bundle.format("events.history.block.destroy.player", name) :
            bundle.format("events.history.block.destroy.unit", unit.type);
        }

        String base = name != null ? bundle.format("events.history.block.construct.player", name, block) :
                      bundle.format("events.history.block.construct.unit", unit.type, block);
        if(block.rotate){
            base += bundle.format("events.history.block.construct.rotate", RotateEntry.sides[rotation]);
        }
        return base;
    }

    @Override
    public long getLastAccessTime(TimeUnit unit){
        return unit.convert(Time.timeSinceMillis(lastAccessTime), TimeUnit.MILLISECONDS);
    }
}
