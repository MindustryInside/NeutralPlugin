package inside;

import arc.files.Fi;
import arc.struct.Seq;
import arc.util.Strings;
import mindustry.core.NetClient;
import mindustry.game.Team;
import mindustry.gen.Player;
import mindustry.maps.Map;

import java.util.*;

import static inside.NeutralPlugin.*;
import static mindustry.Vars.*;

public abstract class Misc{

    private Misc(){}

    private static final Seq<String> bools = Seq.with(bundle.get("misc.bools").split(", "));

    public static boolean bool(String text){
        Objects.requireNonNull(text, "text");
        return bools.contains(text.toLowerCase());
    }

    public static String colorizedTeam(Team team){
        Objects.requireNonNull(team, "team");
        return Strings.format("[#@]@", team.color, team);
    }

    public static String colorizedName(Player player){
        Objects.requireNonNull(player, "player");
        return NetClient.colorizeName(player.id, player.name);
    }

    public static Map findMap(String text){
        for(int i = 0; i < maps.all().size; i++){
            Map map = maps.all().get(i);
            if((Strings.canParseInt(text) && i == Strings.parseInt(text) - 1) || map.name().equals(text)){
                return map;
            }
        }
        return null;
    }

    public static Fi findSave(String text){
        for(int i = 0; i < saveDirectory.list().length; i++){
            Fi save = saveDirectory.list()[i];
            if((Strings.canParseInt(text) && i == Strings.parseInt(text) - 1) || Objects.equals(save.nameWithoutExtension(), text)){
                return save;
            }
        }
        return null;
    }
}
