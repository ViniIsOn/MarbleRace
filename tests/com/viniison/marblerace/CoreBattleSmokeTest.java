package com.viniison.marblerace;

/** Headless check: predictable replay, two competing cores, 60-90 second games. */
public final class CoreBattleSmokeTest {
    static class Result {
        final int winner;final float elapsed,hp0,hp1;
        final int hits0,hits1;
        Result(CoreBattleEngine g){
            winner=g.winner;elapsed=g.elapsed;
            hp0=g.hp[0];hp1=g.hp[1];
            hits0=g.hits[0];hits1=g.hits[1];
        }
    }
    private static Result play(long seed){
        CoreBattleEngine g=new CoreBattleEngine(seed);
        int frames=0;
        while(!g.finished && frames++<3500){
            g.advance(1f/30f);
            if(g.projectiles.size()>80)throw new AssertionError("Projectile leak");
            if(g.hp[0]<0||g.hp[1]<0)throw new AssertionError("Negative health");
            if(!Float.isFinite(g.hp[0])||!Float.isFinite(g.hp[1]))
                throw new AssertionError("Invalid HP");
        }
        if(!g.finished)throw new AssertionError("Core battle did not end");
        if(g.winner<0||g.winner>1)throw new AssertionError("No winner");
        if(g.elapsed<CoreBattleEngine.MIN_DURATION||g.elapsed>CoreBattleEngine.MAX_DURATION+.034)
            throw new AssertionError("Wrong game duration "+g.elapsed);
        if(g.hits[0]+g.hits[1]==0)throw new AssertionError("No visible attacks");
        return new Result(g);
    }
    public static void main(String[] args){
        int[] winCounts={0,0};
        for(int k=0;k<48;k++){
            long seed=12345678L+k*1301981L;
            Result a=play(seed),b=play(seed);
            winCounts[a.winner]++;
            if(a.winner!=b.winner ||
                Float.compare(a.elapsed,b.elapsed)!=0 ||
                Float.compare(a.hp0,b.hp0)!=0 ||
                Float.compare(a.hp1,b.hp1)!=0 ||
                a.hits0!=b.hits0||a.hits1!=b.hits1)
                throw new AssertionError("Non-deterministic export at seed "+seed);
        }
        if(winCounts[0]==0||winCounts[1]==0)
            throw new AssertionError("One core always wins");
        System.out.println("PASS: 48 Core Destruction battles lasted 60-90s, both sides can win and all 48 seeds replay exactly");
    }
}
