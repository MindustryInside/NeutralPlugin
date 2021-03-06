package inside.vote;

import arc.struct.ObjectSet;
import arc.util.Timer.Task;
import mindustry.gen.*;

import static inside.NeutralPlugin.config;

public abstract class VoteSession{
    protected ObjectSet<String> voted = new ObjectSet<>();
    protected VoteSession[] map;
    protected Task task;
    protected int votes;

    public VoteSession(VoteSession[] map){
        this.map = map;
        this.task = start();
    }

    public ObjectSet<String> voted(){
        return voted;
    }

    public abstract void vote(Player player, int d);

    protected abstract Task start();

    protected abstract boolean checkPass();

    protected int votesRequired(){
        return (int)Math.ceil(config.voteRatio * Groups.player.size());
    }
}
