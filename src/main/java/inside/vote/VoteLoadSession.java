package inside.vote;

import arc.files.Fi;
import arc.util.*;
import arc.util.Timer.Task;
import mindustry.game.Gamemode;
import mindustry.gen.*;
import mindustry.io.SaveIO;
import mindustry.maps.MapException;
import mindustry.net.WorldReloader;

import static mindustry.Vars.*;
import static inside.NeutralPlugin.*;

public class VoteLoadSession extends VoteSession{
    private final Fi target;

    public VoteLoadSession(VoteSession[] map, Fi target){
        super(map);

        this.target = target;
    }

    @Override
    public void vote(Player player, int d){
        votes += d;
        voted.addAll(player.uuid(), netServer.admins.getInfo(player.uuid()).lastIP);
        bundled("commands.nominate.load.vote", player.name, target.nameWithoutExtension(), votes, votesRequired());
        checkPass();
    }

    @Override
    protected Task start(){
        return Timer.schedule(() -> {
            if(!checkPass()){
                bundled("commands.nominate.load.failed", target.nameWithoutExtension());
                voted.clear();
                map[0] = null;
                task.cancel();
            }
        }, config.voteDuration);
    }

    @Override
    protected boolean checkPass(){
        if(votes >= votesRequired()){
            bundled("commands.nominate.load.passed", target.nameWithoutExtension());
            map[0] = null;
            task.cancel();

            Runnable r = () -> {
                WorldReloader reloader = new WorldReloader();

                reloader.begin();
                SaveIO.load(target);

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
