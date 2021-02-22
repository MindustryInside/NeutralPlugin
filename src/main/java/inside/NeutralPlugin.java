package inside;

import arc.Events;
import arc.files.Fi;
import arc.math.Mathf;
import arc.struct.*;
import arc.struct.ObjectMap.Entry;
import arc.util.*;
import arc.util.io.Streams;
import com.google.gson.*;
import inside.Config.PluginType;
import inside.entry.*;
import inside.struct.*;
import inside.vote.*;
import mindustry.content.*;
import mindustry.core.NetClient;
import mindustry.game.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.maps.Map;
import mindustry.mod.Plugin;
import mindustry.net.Administration;
import mindustry.net.Administration.PlayerInfo;
import mindustry.world.*;
import mindustry.world.blocks.logic.LogicBlock;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static mindustry.Vars.*;

@SuppressWarnings("unchecked")
public class NeutralPlugin extends Plugin{
    public static final Gson gson = new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_DASHES)
            .disableHtmlEscaping()
            .serializeNulls()
            .setPrettyPrinting()
            .create();

    public static VoteSession[] current = {null};
    public static Config config;
    public static Bundle bundle;

    private final ObjectMap<Team, ObjectSet<String>> surrendered = new ObjectMap<>();
    private final ObjectSet<String> votes = new ObjectSet<>();                //
    private final ObjectSet<String> alertIgnores = new ObjectSet<>();         // TODO(Skat): combine this
    private final ObjectSet<String> activeHistoryPlayers = new ObjectSet<>(); //
    private final ObjectSet<String> spies = new ObjectSet<>();                //
    private final Seq<Tuple3<Player, Player, Long>> send = new Seq<>();
    private final Interval interval = new Interval();

    private CacheSeq<HistoryEntry>[][] history;

    private Seq<IpInfo> forbiddenIps;

    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM-dd-yyyy HH:mm:ss");

    public NeutralPlugin(){

        Fi cfg = dataDirectory.child("config.json");
        if(!cfg.exists()){
            cfg.writeString(gson.toJson(config = new Config()));
            Log.info("Config created...");
        }else{
            config = gson.fromJson(cfg.reader(), Config.class);
        }

        bundle = new Bundle();
    }

    @Override
    public void init(){
        try{
            forbiddenIps = Seq.with(Streams.copyString(Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream("vpn-ipv4.txt"))).split(System.lineSeparator())).map(IpInfo::new);
        }catch(Throwable t){
            throw new ArcRuntimeException(t);
        }

        netServer.admins.addActionFilter(action -> {
            if(action.type == Administration.ActionType.rotate){
                Building building = action.tile.build;
                CacheSeq<HistoryEntry> entries = history[action.tile.x][action.tile.y];
                HistoryEntry entry = new RotateEntry(Misc.colorizedName(action.player), building.block, action.rotation);
                entries.add(entry);
            }
            return true;
        });

        netServer.admins.addChatFilter((target, text) -> {
            String lower = text.toLowerCase();
            if(current[0] != null && (lower.equals("y") || lower.equals("n"))){
                if(current[0].voted().contains(target.uuid()) || current[0].voted().contains(netServer.admins.getInfo(target.uuid()).lastIP)){
                    bundled(target, "commands.already-voted");
                    return null;
                }

                int sign = lower.equals("y") ? 1 : -1;
                current[0].vote(target, sign);
                return null;
            }
            return text;
        });

        Events.run(Trigger.update, () -> {

            send.each(m -> Time.timeSinceMillis(m.t3) > config.messageRedirectTime, send::remove);
        });

        // history

        Events.on(WorldLoadEvent.class, event -> {
            history = new CacheSeq[world.width()][world.height()];

            for(Tile tile : world.tiles){
                history[tile.x][tile.y] = Seqs.newBuilder()
                        .maximumSize(config.historyLimit)
                        .expireAfterWrite(Duration.ofMillis(config.expireDelay))
                        .build();
            }
        });

        Events.on(BlockBuildEndEvent.class, event -> {
            HistoryEntry historyEntry = new BlockEntry(event);

            Seq<Tile> linkedTile = event.tile.getLinkedTiles(new Seq<>());
            for(Tile tile : linkedTile){
                history[tile.x][tile.y].add(historyEntry);
            }
        });

        Events.on(ConfigEvent.class, event -> {
            if(event.tile.block instanceof LogicBlock || event.player == null || event.tile.tileX() > world.width() || event.tile.tileX() > world.height()){
                return;
            }

            CacheSeq<HistoryEntry> entries = history[event.tile.tileX()][event.tile.tileY()];
            boolean connect = true;

            HistoryEntry last = entries.peek();
            if(!entries.isEmpty() && last instanceof ConfigEntry){
                ConfigEntry lastConfigEntry = (ConfigEntry)last;

                connect = !event.tile.getPowerConnections(new Seq<>()).isEmpty() &&
                          !(lastConfigEntry.value instanceof Integer && event.value instanceof Integer &&
                          (int)lastConfigEntry.value == (int)event.value && lastConfigEntry.connect);
            }

            HistoryEntry entry = new ConfigEntry(event, connect);

            Seq<Tile> linkedTile = event.tile.tile.getLinkedTiles(new Seq<>());
            for(Tile tile : linkedTile){
                history[tile.x][tile.y].add(entry);
            }
        });

        Events.on(TapEvent.class, event -> {
            if(activeHistoryPlayers.contains(event.player.uuid())){
                CacheSeq<HistoryEntry> entries = history[event.tile.x][event.tile.y];

                StringBuilder message = new StringBuilder(bundle.format("events.history.title", event.tile.x, event.tile.y));

                entries.cleanUp();
                if(entries.isOverflown()){
                    message.append(bundle.get("events.history.overflow"));
                }

                int i = 0;
                for(HistoryEntry entry : entries){
                    message.append("\n").append(entry.getMessage());
                    if(++i > 6){
                        break;
                    }
                }

                if(entries.isEmpty()){
                    message.append(bundle.get("events.history.empty"));
                }

                event.player.sendMessage(message.toString());
            }
        });

        Events.on(PlayerLeave.class, event -> activeHistoryPlayers.remove(event.player.uuid()));

        //

        Events.on(PlayerJoin.class, event -> forbiddenIps.each(i -> i.matchIp(event.player.con.address), i -> event.player.con.kick(bundle.get("events.vpn-ip"))));

        Events.on(PlayerConnect.class, event -> {
            Player player = event.player;
            if(config.bannedNames.contains(player.name())){
                player.con.kick(bundle.get("events.unofficial-mindustry"), 60000);
            }
        });

        Events.on(DepositEvent.class, event -> {
            Building building = event.tile;
            Player target = event.player;
            if(building.block() == Blocks.thoriumReactor && event.item == Items.thorium && target.team().cores().contains(c -> event.tile.dst(c.x, c.y) < config.alertDistance)){
                Groups.player.each(p -> !alertIgnores.contains(p.uuid()), p -> p.sendMessage(bundle.format("events.withdraw-thorium", Misc.colorizedName(target), building.tileX(), building.tileY())));
            }
        });

        Events.on(BuildSelectEvent.class, event -> {
            if(!event.breaking && event.builder != null && event.builder.buildPlan() != null &&
               event.builder.buildPlan().block == Blocks.thoriumReactor && event.builder.isPlayer() &&
               event.team.cores().contains(c -> event.tile.dst(c.x, c.y) < config.alertDistance)){
                Player target = event.builder.getPlayer();

                if(interval.get(300)){
                    Groups.player.each(p -> !alertIgnores.contains(p.uuid()), p -> p.sendMessage(bundle.format("events.alert", target.name, event.tile.x, event.tile.y)));
                }
            }
        });

        Events.on(PlayerLeave.class, event -> {
            int cur = votes.size;
            int req = (int)Math.ceil(config.voteRatio * Groups.player.size());
            if(votes.contains(event.player.uuid())){
                votes.remove(event.player.uuid());
                Call.sendMessage(bundle.format("commands.rtv.left", Misc.colorizedName(event.player), cur - 1, req));
            }
        });

        Events.on(GameOverEvent.class, __ -> votes.clear());

        if(config.type == PluginType.pvp){
            Events.on(PlayerLeave.class, event -> {
                String uuid = event.player.uuid();
                ObjectSet<String> uuids = surrendered.get(event.player.team(), ObjectSet::new);
                if(uuids.contains(uuid)){
                    uuids.remove(uuid);
                }
            });

            Events.on(GameOverEvent.class, __ -> surrendered.clear());
        }
    }

    @Override
    public void registerServerCommands(CommandHandler handler){

        handler.register("reload-config", "reload configuration", args -> {
            config = gson.fromJson(dataDirectory.child("config.json").readString(), Config.class);
            Log.info("Reloaded");
        });

        handler.register("tell", bundle.get("commands.tell.params"), bundle.get("commands.tell.description"), args -> {
            Player target = Groups.player.find(p -> p.name().equalsIgnoreCase(args[0]) || p.uuid().equalsIgnoreCase(args[0]));
            if(target == null){
                Log.info(bundle.get("commands.tell.player-not-found"));
                return;
            }

            target.sendMessage("[scarlet][[Server]:[] " + args[1]);
            Log.info(bundle.format("commands.tell.log", target.name(), args[1]));
        });

        handler.register("despw", bundle.get("commands.despw.description"), args -> {
            Groups.unit.each(Unit::kill);
            Log.info(bundle.get("commands.despw.log"));
        });

        handler.register("kicks", bundle.get("commands.kicks.description"), args -> {
            Log.info("Kicks: @", netServer.admins.kickedIPs.isEmpty() ? "<none>" : "");
            for(Entry<String, Long> e : netServer.admins.kickedIPs){
                PlayerInfo info = netServer.admins.findByIPs(e.key).first();
                Log.info("  @ / ID: '@' / IP: '@' / END: @",
                         info.lastName, info.id, info.lastIP,
                         formatter.format(Instant.ofEpochMilli(e.value).atZone(ZoneId.systemDefault())));
            }
        });
    }

    @Override
    public void registerClientCommands(CommandHandler handler){

        handler.removeCommand("t");

        handler.<Player>register("t", "<message...>", "Send a message only to your teammates.", (args, player) -> {
            String message = netServer.admins.filterMessage(player, args[0]);
            if(message != null){
                Groups.player.each(p -> p.team() == player.team() || spies.contains(p.uuid()), o -> o.sendMessage(message, player, "[#" + player.team().color.toString() + "]<T>" + NetClient.colorizeName(player.id(), player.name)));
            }
        });

        handler.<Player>register("l", "<range> <message...>", "Send a message in the range.", (args, player) -> {
            if(!Strings.canParseInt(args[0])){
                player.sendMessage("[scarlet]'range' must be a number.");
                return;
            }

            String message = netServer.admins.filterMessage(player, args[1]);
            int range = Strings.parseInt(args[0]) * 10;
            if(message != null){
                Groups.player.each(p -> p.dst(player.x, player.y) < range || spies.contains(p.uuid()), o -> o.sendMessage(message, player, "[#" + player.team().color.toString() + "]<L>" + NetClient.colorizeName(player.id(), player.name)));
            }
        });

        handler.<Player>register("spy", "Admins command for chat listen.", (args, player) -> {
            if(!player.admin){
                player.sendMessage("[scarlet]You must be admin to use this command.");
                return;
            }

            if(spies.contains(player.uuid())){
                spies.remove(player.uuid());
                player.sendMessage("[lightgray]Listening mode [orange]disabled[].");
            }else{
                spies.add(player.uuid());
                player.sendMessage("[lightgray]Listening mode [orange]enabled[].");
            }
        });

        handler.<Player>register("vanish", "Change the command to neutral.", (args, player) -> {
            if(!player.admin){
                player.sendMessage("[scarlet]You must be admin to use this command.");
                return;
            }

            player.clearUnit();
            player.team(player.team() == state.rules.defaultTeam ? Team.derelict : state.rules.defaultTeam);
        });

        handler.<Player>register("m", "<id> <text...>", "Send direct message.", (args, player) -> {
            if(!Strings.canParseInt(args[0])){
                player.sendMessage("[scarlet]Id must be number.");
                return;
            }

            int id = Strings.parseInt(args[0]);
            Player target = Groups.player.find(p -> p.id == id);
            if(target == null){
                player.sendMessage("[scarlet]Player not found.");
                return;
            }

            if(send.contains(t -> t.t2 == target)){
                player.sendMessage("[scarlet]Please wait! Player is busy.");
                return;
            }

            send.add(Tuples.of(player, target, Time.millis()));

            target.sendMessage(Strings.format("[lightgray][[@ --> [orange]You[lightgray]]:[white] @", NetClient.colorizeName(player.id, player.name), args[1]));
            player.sendMessage(Strings.format("[lightgray][[[orange]You[lightgray] --> @]:[white] @", NetClient.colorizeName(target.id, target.name), args[1]));
        });

        handler.<Player>register("r", "<text...>", "Reply direct message.", (args, player) -> {
            Tuple3<Player, Player, Long> message = send.find(m -> m.t2 == player);
            if(message == null){
                player.sendMessage("[scarlet]No one to answer.");
                return;
            }
            Player target = message.t1;

            send.add(Tuples.of(player, target, Time.millis()));

            target.sendMessage(Strings.format("[lightgray][[@ --> [orange]You[lightgray]]:[white] @", NetClient.colorizeName(player.id, player.name), args[0]));
            player.sendMessage(Strings.format("[lightgray][[[orange]You[lightgray] --> @]:[white] @", NetClient.colorizeName(target.id, target.name), args[0]));
        });

        handler.<Player>register("alert", bundle.get("commands.alert.description"), (args, player) -> {
            if(alertIgnores.contains(player.uuid())){
                alertIgnores.remove(player.uuid());
                bundled(player, "commands.alert.on");
            }else{
                alertIgnores.add(player.uuid());
                bundled(player, "commands.alert.off");
            }
        });

        handler.<Player>register("history", bundle.get("commands.history.params"), bundle.get("commands.history.description"), (args, player) -> {
            String uuid = player.uuid();
            if(args.length > 0 && activeHistoryPlayers.contains(uuid)){
                if(!Strings.canParseInt(args[0]) && !Misc.bool(args[0])){
                    bundled(player, "commands.page-not-int");
                    return;
                }

                boolean forward = !Strings.canParseInt(args[0]) ? Misc.bool(args[0]) : args.length > 1 && Misc.bool(args[1]);
                int mouseX = Mathf.clamp(Mathf.round(player.mouseX / 8), 1, world.width());
                int mouseY = Mathf.clamp(Mathf.round(player.mouseY / 8), 1, world.height());
                CacheSeq<HistoryEntry> entries = history[mouseX][mouseY];
                int page = Strings.canParseInt(args[0]) ? Strings.parseInt(args[0]) : 1;
                int pages = Mathf.ceil((float)entries.size / 6);

                page--;

                if((page >= pages || page < 0) && !entries.isEmpty()){
                    bundled(player, "commands.under-page", pages);
                    return;
                }

                StringBuilder result = new StringBuilder();
                result.append(bundle.format("commands.history.page", mouseX, mouseY, page + 1, pages)).append("\n");
                if(entries.isEmpty()){
                    result.append("events.history.empty");
                }

                for(int i = 6 * page; i < Math.min(6 * (page + 1), entries.size); i++){
                    HistoryEntry entry = entries.get(i);

                    result.append(entry.getMessage());
                    if(forward){
                        result.append(bundle.format("events.history.last-access-time", entry.getLastAccessTime(TimeUnit.SECONDS)));
                    }

                    result.append("\n");
                }

                player.sendMessage(result.toString());
            }else if(activeHistoryPlayers.contains(uuid)){
                activeHistoryPlayers.remove(uuid);
                bundled(player, "commands.history.off");
            }else{
                activeHistoryPlayers.add(uuid);
                bundled(player, "commands.history.on");
            }
        });

        if(config.type == PluginType.pvp){
            handler.<Player>register("france", bundle.get("commands.surrender.description"), (args, player) -> {
                String uuid = player.uuid();
                Team team = player.team();
                ObjectSet<String> uuids = surrendered.get(team, ObjectSet::new);
                if(uuids.contains(uuid)){
                    bundled(player, "commands.already-voted");
                    return;
                }

                uuids.add(uuid);
                int cur = uuids.size;
                int req = (int)Math.ceil(config.voteRatio * Groups.player.count(p -> p.team() == team));
                Call.sendMessage(bundle.format("commands.surrender.ok",
                                               Misc.colorizedTeam(team),
                                               Misc.colorizedName(player), cur, req));

                if(cur < req){
                    return;
                }

                surrendered.remove(team);
                Call.sendMessage(bundle.format("commands.surrender.successful", Misc.colorizedTeam(team)));
                Groups.unit.each(u -> u.team == team, u -> Time.run(Mathf.random(360), u::kill));
                for(Tile tile : world.tiles){
                    if(tile.build != null && tile.team() == team){
                        Time.run(Mathf.random(360), tile.build::kill);
                    }
                }
            });
        }

        handler.<Player>register("pl", bundle.get("commands.pl.params"), bundle.get("commands.pl.description"), (args, player) -> {
            if(args.length > 0 && !Strings.canParseInt(args[0])){
                bundled(player, "commands.page-not-int");
                return;
            }

            int page = args.length > 0 ? Strings.parseInt(args[0]) : 1;
            int pages = Mathf.ceil((float)Groups.player.size() / 6);

            if(--page >= pages || page < 0){
                bundled(player, "commands.under-page", pages);
                return;
            }

            StringBuilder result = new StringBuilder();
            result.append(bundle.format("commands.pl.page", page + 1, pages)).append("\n");

            for(int i = 6 * page; i < Math.min(6 * (page + 1), Groups.player.size()); i++){
                Player t = Groups.player.index(i);
                result.append("[lightgray]* ").append(t.name).append(" [lightgray]/ ID: ").append(t.id());

                if(player.admin){
                    result.append(" / raw: ").append(t.name.replaceAll("\\[", "[["));
                }
                result.append("\n");
            }
            player.sendMessage(result.toString());
        });

        handler.<Player>register("rtv", bundle.get("commands.rtv.description"), (args, player) -> {
            if(votes.contains(player.uuid())){
                bundled(player, "commands.already-voted");
                return;
            }

            votes.add(player.uuid());
            int cur = votes.size;
            int req = (int)Math.ceil(config.voteRatio * Groups.player.size());
            Call.sendMessage(bundle.format("commands.rtv.ok", Misc.colorizedName(player), cur, req));

            if(cur < req){
                return;
            }

            Call.sendMessage(bundle.get("commands.rtv.successful"));
            Events.fire(new GameOverEvent(Team.crux));
        });

        handler.<Player>register("go", bundle.get("commands.admin.go.description"), (args, player) -> {
            if(!player.admin){
                bundled(player, "commands.permission-denied");
            }else{
                Events.fire(new GameOverEvent(Team.crux));
            }
        });

        handler.<Player>register("core", bundle.get("commands.admin.core.params"), bundle.get("commands.admin.core.description"), (args, player) -> {
            if(!player.admin){
                bundled(player, "commands.permission-denied");
                return;
            }

            Block core = switch(args[0].toLowerCase()){
                case "medium" -> Blocks.coreFoundation;
                case "big" -> Blocks.coreNucleus;
                default -> Blocks.coreShard;
            };

            Call.constructFinish(player.tileOn(), core, player.unit(), (byte)0, player.team(), false);

            bundled(player, player.tileOn().block() == core ? "commands.admin.core.success" : "commands.admin.core.failed");
        });

        handler.<Player>register("hub", bundle.get("commands.hub.description"), (args, player) -> {
            Tuple2<String, Integer> ip = config.getHubIp();
            Call.connect(player.con, ip.t1, ip.t2);
        });

        handler.<Player>register("team", bundle.get("commands.admin.team.params"), bundle.get("commands.admin.teamp.description"), (args, player) -> {
            if(!player.admin){
                bundled(player, "commands.permission-denied");
                return;
            }

            Team team = Structs.find(Team.all, t -> t.name.equalsIgnoreCase(args[0]));
            if(team == null){
                bundled(player, "commands.admin.team.teams");
                return;
            }

            Player target = args.length > 1 ? Groups.player.find(p -> Strings.stripColors(p.name).equalsIgnoreCase(args[1])) : player;
            if(target == null){
                bundled(player, "commands.player-not-found");
                return;
            }

            bundled(target, "commands.admin.team.success", team.name);
            target.team(team);
        });

        handler.<Player>register("s", bundle.get("commands.admin.vanish.description"), (args, player) -> {
            if(!player.admin){
                bundled(player, "commands.permission-denied");
            }else{
                player.clearUnit();
                player.team(player.team() == Team.derelict ? Team.sharded : Team.derelict);
            }
        });

        handler.<Player>register("maps", bundle.get("commands.maps.params"), bundle.get("commands.maps.description"), (args, player) -> {
            if(args.length > 0 && !Strings.canParseInt(args[0])){
                bundled(player, "commands.page-not-int");
                return;
            }

            Seq<Map> mapList = maps.all();
            int page = args.length > 0 ? Strings.parseInt(args[0]) : 1;
            int pages = Mathf.ceil(mapList.size / 6f);

            if(--page >= pages || page < 0){
                bundled(player, "commands.under-page", pages);
                return;
            }

            StringBuilder result = new StringBuilder();
            result.append(bundle.format("commands.maps.page", page + 1, pages)).append("\n");
            for(int i = 6 * page; i < Math.min(6 * (page + 1), mapList.size); i++){
                result.append("[lightgray] ").append(i + 1).append("[orange] ").append(mapList.get(i).name()).append("[white] ").append("\n");
            }

            player.sendMessage(result.toString());
        });

        handler.<Player>register("saves", bundle.get("commands.saves.params"), bundle.get("commands.saves.description"), (args, player) -> {
            if(args.length > 0 && !Strings.canParseInt(args[0])){
                bundled(player, "commands.page-not-int");
                return;
            }

            Seq<Fi> saves = Seq.with(saveDirectory.list()).filter(f -> Objects.equals(f.extension(), saveExtension));
            int page = args.length > 0 ? Strings.parseInt(args[0]) : 1;
            int pages = Mathf.ceil(saves.size / 6.0F);

            if(--page >= pages || page < 0){
                bundled(player, "commands.under-page", pages);
                return;
            }

            StringBuilder result = new StringBuilder();
            result.append(bundle.format("commands.saves.page", page + 1, pages)).append("\n");
            for(int i = 6 * page; i < Math.min(6 * (page + 1), saves.size); i++){
                result.append("[lightgray] ").append(i + 1).append("[orange] ").append(saves.get(i).nameWithoutExtension()).append("[white] ").append("\n");
            }

            player.sendMessage(result.toString());
        });

        handler.<Player>register("nominate", bundle.get("commands.nominate.params"), bundle.get("commands.nominate.description"), (args, player) -> {
            VoteMode mode;
            try{
                mode = VoteMode.valueOf(args[0].toLowerCase());
            }catch(Throwable t){
                bundled(player, "commands.nominate.incorrect-mode");
                return;
            }

            if(current[0] != null){
                bundled(player, "commands.nominate.already-started");
                return;
            }

            switch(mode){
                case map -> {
                    if(args.length == 1){
                        bundled(player, "commands.nominate.required-second-arg");
                        return;
                    }

                    Map map = Misc.findMap(args[1]);
                    if(map == null){
                        bundled(player, "commands.nominate.map.not-found");
                        return;
                    }

                    VoteSession session = new VoteMapSession(current, map);
                    current[0] = session;
                    session.vote(player, 1);
                }
                case save -> {
                    if(args.length == 1){
                        bundled(player, "commands.nominate.required-second-arg");
                        return;
                    }

                    VoteSession session = new VoteSaveSession(current, args[1]);
                    current[0] = session;
                    session.vote(player, 1);
                }
                case load -> {
                    if(args.length == 1){
                        bundled(player, "commands.nominate.required-second-arg");
                        return;
                    }

                    Fi save = Misc.findSave(args[1]);
                    if(save == null){
                        player.sendMessage("commands.nominate.load.not-found");
                        return;
                    }

                    VoteSession session = new VoteLoadSession(current, save);
                    current[0] = session;
                    session.vote(player, 1);
                }
            }
        });
    }

    public static void bundled(Player player, String key, Object... values){
        player.sendMessage(bundle.format(key, values));
    }
}
