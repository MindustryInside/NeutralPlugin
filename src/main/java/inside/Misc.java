package inside;

import arc.files.Fi;
import arc.util.Strings;
import mindustry.core.NetClient;
import mindustry.game.Team;
import mindustry.gen.Player;
import mindustry.maps.Map;

import java.util.Objects;

import static mindustry.Vars.*;

public abstract class Misc{

    private Misc(){}

    public static String colorizedTeam(Team team){
        Objects.requireNonNull(team, "team");
        return Strings.format("[#@]@", team.color, team);
    }

    /**
     * Better method for getting colorized player nickname.
     * Unlike {@link NetClient#colorizeName(int, String)} doesn't check player existing
     *
     * @param player - target player
     * @return colorized player nickname
     * @throws NullPointerException - if player is null
     */
    public static String colorizedName(Player player){
        Objects.requireNonNull(player, "player");
        return "[#" + player.color().toString().toUpperCase() + "]" + player.name;
    }

    public static Map findMap(String text){
        for(int i = 0; i < maps.all().size; i++){
            Map map = maps.all().get(i);
            if(Strings.canParseInt(text) && i == Strings.parseInt(text) - 1 || map.name().equals(text)){
                return map;
            }
        }
        return null;
    }

    public static Fi findSave(String text){
        for(int i = 0; i < saveDirectory.list().length; i++){
            Fi save = saveDirectory.list()[i];
            if(Strings.canParseInt(text) && i == Strings.parseInt(text) - 1 || save.nameWithoutExtension().equals(text)){
                return save;
            }
        }
        return null;
    }
}
