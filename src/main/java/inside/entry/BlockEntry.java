package inside.entry;

import arc.util.Nullable;
import inside.*;
import mindustry.game.EventType.BlockBuildEndEvent;
import mindustry.gen.*;
import mindustry.world.Block;

import java.util.Locale;

public class BlockEntry implements HistoryEntry{
    @Nullable
    public final String name;
    public final Unit unit;
    public final Block block;
    public final boolean breaking;
    public final int rotation;

    public BlockEntry(BlockBuildEndEvent event){
        this.unit = event.unit;
        // TODO: optimise this line
        this.name = unit.isPlayer() ? Misc.colorizedName(unit.getPlayer()) : unit.controller() instanceof Player ? Misc.colorizedName(unit.getPlayer()) : null;
        this.block = event.tile.build.block;
        this.breaking = event.breaking;

        this.rotation = event.tile.build.rotation;
    }

    @Override
    public String getMessage(Locale locale){
        if(breaking){
            return name != null ? Bundle.format("events.history.block.destroy.player", locale, name) :
                   Bundle.format("events.history.block.destroy.unit", locale, unit.type);
        }

        String base = name != null ? Bundle.format("events.history.block.construct.player", locale, name, block) :
                      Bundle.format("events.history.block.construct.unit", locale, unit.type, block);
        if(block.rotate){
            base += Bundle.format("events.history.block.construct.rotate", locale, RotateEntry.sides[rotation]);
        }
        return base;
    }
}
