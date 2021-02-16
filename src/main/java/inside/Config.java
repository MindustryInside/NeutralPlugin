package inside;

import java.util.Set;

public class Config{

    public int alertDistance = 300;

    /** Vote ratio required. */
    public float voteRatio = 0.6f;

    /** Maximum entries count of entry in history array. */
    public int historyLimit = 16;

    /** History entry TTL. Default 30 minutes. In milliseconds. */
    public long expireDelay = 1800000;

    /** Vote duration. In seconds. */
    public float voteDuration = 75f;

    /** Ip used in <b>hub</b> command. */
    public String hubIp = "localhost";

    /* TODO(Skat): combine with hubIp and parse ip, port */
    /** Port used in <b>hub</b> command. */
    public int hubPort = 6567;

    /** Plugin type. Defines various features. */
    public PluginType type = PluginType.def;

    /** Plugin locale. */
    public String locale = "ru";

    public Set<String> bannedNames = Set.of(
            "IGGGAMES",
            "CODEX",
            "VALVE",
            "tuttop"
    );

    public enum PluginType{

        /** For Attack or Survival servers. */
        def,

        /** For PvP servers. */
        pvp
    }
}
