package com.viniison.marblerace;

import java.util.ArrayList;
import java.util.List;

/** Runs collision regression checks without Android runtime, JUnit or network. */
public final class RaceEngineSmokeTest {
    private static List<RaceEngine.Racer> racers(int count){
        ArrayList<RaceEngine.Racer> list=new ArrayList<>();
        for(int n=0;n<count;n++)
            list.add(new RaceEngine.Racer("Marble "+n,"",null,
                RaceEngine.PALETTE[n%RaceEngine.PALETTE.length]));
        return list;
    }
    private static int[] simulate(RaceEngine race){
        race.running=true;
        float prevElapsed=0;
        for(int frame=0;frame<70*30 && race.finished<race.balls.size();frame++){
            race.advance(1f/30f);
            if(race.elapsed<prevElapsed)throw new AssertionError("Clock moved backward");
            prevElapsed=race.elapsed;
            for(RaceEngine.Ball ball:race.balls) {
                if(!Float.isFinite(ball.x)||!Float.isFinite(ball.y))
                    throw new AssertionError("Nonfinite ball position");
                if(ball.place==0 && ball.stallTime>2.23f)
                    throw new AssertionError("Stall not recovered");
                float center=race.centerAt(ball.y);
                if(ball.place==0&&Math.abs(ball.x-center)>340)
                    throw new AssertionError("Ball escaped corridor");
            }
        }
        if(race.finished!=race.balls.size())
            throw new AssertionError("Race did not finish: track="+race.track+
                " seed="+race.seed+" count="+race.balls.size()+" done="+race.finished+
                " clock="+race.elapsed);
        int[] positions=new int[race.balls.size()];
        boolean[] places=new boolean[race.balls.size()+1];
        for(int i=0;i<race.balls.size();i++){
            int place=race.balls.get(i).place;
            if(place<1||place>race.balls.size()||places[place])
                throw new AssertionError("Duplicate or invalid finish position");
            places[place]=true;
            positions[i]=place;
        }
        return positions;
    }
    public static void main(String[] args){
        int tested=0;
        for(int track=0;track<RaceEngine.TRACK_NAMES.length;track++){
            for(int count:new int[]{2,4,8}){
                List<RaceEngine.Racer> entries=racers(count);
                for(int n=0;n<8;n++){
                    long seed=99460073L+track*8191+n*71+count*943;
                    RaceEngine first=new RaceEngine(entries,track,seed);
                    int[] a=simulate(first);
                    RaceEngine replay=new RaceEngine(entries,track,seed);
                    int[] b=simulate(replay);
                    for(int i=0;i<count;i++)
                        if(a[i]!=b[i])throw new AssertionError("Replay winner mismatch");
                    tested++;
                }
            }
        }
        System.out.println("PASS: "+tested+" tracks/seeds/party sizes finished and replayed accurately");
    }
}
