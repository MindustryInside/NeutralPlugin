package inside;

import arc.util.*;
import inside.struct.*;
import mindustry.Vars;

import java.util.*;

public class Config{

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
    public Locale locale = Locale.forLanguageTag("ru");

    public Set<String> bannedNames = Set.of(
            "IGGGAMES",
            "CODEX",
            "VALVE",
            "tuttop"
    );

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
