package inside;

import arc.struct.ObjectSet;
import arc.util.Strings;
import inside.struct.*;
import mindustry.Vars;

import java.util.*;

public class Config{

    public static final ObjectSet<Locale> supportedLocales = ObjectSet.with(new Locale("ru", "RU"), new Locale("en"));

    /** A distance of the nuke alert. Default 30 blocks. */
    public int alertDistance = 300;

    /** Vote ratio required. */
    public float voteRatio = 0.6f;

    /** Maximum entries count of entry in history array. */
    public int historyLimit = 16;

    /** History entry TTL. Default 30 minutes. In milliseconds. */
    public long expireDelay = 1800000;

    /** Response timeout. In milliseconds. Default 10 seconds. */
    public long messageRedirectTime = 10000;

    /** Vote duration. In seconds. */
    public float voteDuration = 75f;

    /** Ip and port used in <b>hub</b> command. */
    public String hubIp = "localhost:6567";

    /** Plugin type. Defines various features. */
    public PluginType type = PluginType.def;

    /** Plugin locale. */
    public Locale locale = new Locale("en");

    /** Illegal names. If player has one of this names he be a kicked from server. */
    public Set<String> bannedNames = Set.of(
            "IGGGAMES",
            "CODEX",
            "VALVE",
            "tuttop"
    );

    /** Parsed pair with port and ip for <b>hub</b> command. */
    public Tuple2<String, Integer> getHubIp(){
        String ip = hubIp;
        int port = Vars.port;
        String[] parts = ip.split(":");
        if(ip.contains(":") && Strings.canParsePositiveInt(parts[1])){
            ip = parts[0];
            port = Strings.parseInt(parts[1]);
        }
        return Tuples.of(ip, port);
    }

    public enum PluginType{

        /** For Attack or Survival servers. */
        def,

        /** For PvP servers. */
        pvp
    }
}
