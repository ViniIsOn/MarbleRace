package com.viniison.marblerace;

/** Real-physics regression: caps, collision stability, HP, damage, waves and deterministic export. */
public final class CoreBattleSmokeTest {
    static class Result {
        final int winner,damage0,damage1,spawn0,spawn1,boost0,boost1,peak;
        final int approach0,approach1;
        final float approachX0,approachX1;
        final float elapsed,hp0,hp1;
        Result(CoreBattleEngine game,int peak){
            winner=game.winner;elapsed=game.elapsed;hp0=game.hp[0];hp1=game.hp[1];
            damage0=game.hits[0];damage1=game.hits[1];
            spawn0=game.spawned[0];spawn1=game.spawned[1];boost0=game.boosts[0];boost1=game.boosts[1];this.peak=peak;
            approach0=game.approaches[0];approach1=game.approaches[1];
            approachX0=approach0==0?0:game.approachSumX[0]/approach0;
            approachX1=approach1==0?0:game.approachSumX[1]/approach1;
        }
    }
    private static Result simulate(long seed){
        CoreBattleEngine g=new CoreBattleEngine(seed);
        int frames=0,peak=0;
        while(!g.finished && frames++<CoreBattleEngine.MAX_DURATION*30+60){
            g.advance(1f/30f);
            if(g.marbles.size()>CoreBattleEngine.MAX_PER_TEAM*2+70)
                throw new AssertionError("Too many marbles allocated");
            for(int side=0;side<2;side++){
                if(g.active[side]<0||g.active[side]>CoreBattleEngine.MAX_PER_TEAM)
                    throw new AssertionError("Spawner pool limit broken");
                if(g.hp[side]<0||g.hp[side]>CoreBattleEngine.MAX_HP)
                    throw new AssertionError("Core HP out of bounds");
            }
            if(g.elapsed>=16){
                if(g.spawned[0]<50||g.spawned[1]<50)
                    throw new AssertionError("No wave spawner");
            }
            for(CoreBattleEngine.Marble ball:g.marbles){
                if(!Float.isFinite(ball.x)||!Float.isFinite(ball.y)||
                   !Float.isFinite(ball.vx)||!Float.isFinite(ball.vy))
                    throw new AssertionError("Physics diverged");
                if(ball.active){
                    float speed=(float)Math.hypot(ball.vx,ball.vy);
                    if(speed>1090f)throw new AssertionError("Unbounded marble speed: "+speed);
                }
            }
            peak=Math.max(peak,g.marbles.size());
        }
        if(!g.finished||g.winner<0||g.winner>1)
            throw new AssertionError("Match did not finish");
        if(g.elapsed<CoreBattleEngine.MIN_DURATION||
           g.elapsed>CoreBattleEngine.MAX_DURATION+.034f)
            throw new AssertionError("Game outside 60-95 seconds");
        return new Result(g,peak);
    }
    public static void main(String[] args){
        int[] victories={0,0};
        int totalHits=0,maxPeak=0;
        for(int k=0;k<32;k++){
            long seed=12345678L+k*1301981L;
            Result a=simulate(seed);
            Result b=simulate(seed);
            if(k<6)System.out.println("CORE DIAGNOSTIC seed="+seed+" hp="+a.hp0+"/"+a.hp1+" hits="+a.damage0+"/"+a.damage1+" spawn="+a.spawn0+"/"+a.spawn1+" boosts="+a.boost0+"/"+a.boost1+" approach="+a.approach0+"/"+a.approach1+" meanX="+a.approachX0+"/"+a.approachX1+" winner="+a.winner);
            victories[a.winner]++;
            totalHits+=a.damage0+a.damage1;
            maxPeak=Math.max(maxPeak,a.peak);
            if(a.winner!=b.winner ||Float.compare(a.elapsed,b.elapsed)!=0 ||
                Float.compare(a.hp0,b.hp0)!=0 ||Float.compare(a.hp1,b.hp1)!=0 ||
                a.spawn0!=b.spawn0||a.spawn1!=b.spawn1||
                a.damage0!=b.damage0||a.damage1!=b.damage1)
                throw new AssertionError("Export outcome differed for seed "+seed);
        }
        if(totalHits<16)throw new AssertionError("No real hits on enemy cores: "+totalHits);
        if(victories[0]==0||victories[1]==0)
            throw new AssertionError("Unbalanced winners: "+victories[0]+"/"+victories[1]);
        System.out.println("PASS: 32 physics battles + exact replays; real hits="+totalHits+
            ", max marbles="+maxPeak+", winners="+victories[0]+"/"+victories[1]);
    }
}
