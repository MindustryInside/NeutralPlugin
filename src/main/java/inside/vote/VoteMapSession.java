package inside.vote;

import arc.util.*;
import arc.util.Timer.Task;
import mindustry.game.Gamemode;
import mindustry.gen.*;
import mindustry.maps.*;
import mindustry.net.WorldReloader;

import static mindustry.Vars.*;
import static inside.NeutralPlugin.*;

public class VoteMapSession extends VoteSession{
    private final Map target;

    public VoteMapSession(VoteSession[] map, Map target){
        super(map);

        this.target = target;
    }

    @Override
    public void vote(Player player, int d){
        votes += d;
        voted.addAll(player.uuid(), netServer.admins.getInfo(player.uuid()).lastIP);
        bundled("commands.nominate.map.vote", player.name, target.name(), votes, votesRequired());
        checkPass();
    }

    @Override
    protected Task start(){
        return Timer.schedule(() -> {
            if(!checkPass()){
                bundled("commands.nominate.map.failed", target.name());
                voted.clear();
                map[0] = null;
                task.cancel();
            }
        }, config.voteDuration);
    }

    @Override
    protected boolean checkPass(){
        if(votes >= votesRequired()){
            bundled("commands.nominate.map.passed", target.name());
            map[0] = null;
            task.cancel();

            Runnable r = () -> {
                WorldReloader reloader = new WorldReloader();

                reloader.begin();

                world.loadMap(target, target.applyRules(Gamemode.survival));

                state.rules = state.map.applyRules(Gamemode.survival);
                logic.play();

                reloader.end();
            };

            Timer.schedule(new Task(){
                @Override
                public void run(){
                    try{
                        r.run();
                    }catch(MapException e){
                        Log.err(e);
                        net.closeServer();
                    }
                }
            }, 10);
            return true;
        }
        return false;
    }
}
