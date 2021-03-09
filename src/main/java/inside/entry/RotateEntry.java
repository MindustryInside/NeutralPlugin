package inside.entry;

import inside.Bundle;
import mindustry.world.Block;

import java.util.Locale;

public class RotateEntry implements HistoryEntry{
    protected static final String[] sides;

    static{
        sides = Bundle.get("events.history.rotate.all").split(", ");
    }

    public final String name;
    public final Block block;
    public final int rotation;

    public RotateEntry(String name, Block block, int rotation){
        this.name = name;
        this.block = block;
        this.rotation = rotation;
    }

    @Override
    public String getMessage(Locale locale){
        return Bundle.format("events.history.rotate", locale, name, block.name, sides[rotation]);
    }
}
