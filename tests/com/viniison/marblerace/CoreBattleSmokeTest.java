package com.viniison.marblerace;

/** Real-physics regression: caps, collision stability, HP, damage, waves and deterministic export. */
public final class CoreBattleSmokeTest {
    static class Result {
        final int physicalWinner,winner,damage0,damage1,shield0,shield1,spawn0,spawn1,boost0,boost1,peak;
        final int approach0,approach1;
        final float approachX0,approachX1;
        final float elapsed,hp0,hp1;
        Result(CoreBattleEngine game,int peak){
            winner=game.winner;physicalWinner=game.physicalWinner;elapsed=game.elapsed;hp0=game.hp[0];hp1=game.hp[1];
            damage0=game.hits[0];damage1=game.hits[1];shield0=game.shieldHits[0];shield1=game.shieldHits[1];
            spawn0=game.spawned[0];spawn1=game.spawned[1];boost0=game.boosts[0];boost1=game.boosts[1];this.peak=peak;
            approach0=game.approaches[0];approach1=game.approaches[1];
            approachX0=approach0==0?0:game.approachSumX[0]/approach0;
            approachX1=approach1==0?0:game.approachSumX[1]/approach1;
        }
    }
    private static Result simulate(long seed){
        CoreBattleEngine g=new CoreBattleEngine(seed);
        int frames=0,peak=0;
        while(!g.finished && frames++<(CoreBattleEngine.MAX_DURATION+4)*30){
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
           g.elapsed>CoreBattleEngine.MAX_DURATION+2.51f)
            throw new AssertionError("Game outside 0-122.5 seconds");
        return new Result(g,peak);
    }
    private static void neutralCenterTest(){
        // The center must NOT hide a rotating saw or a one-directional paddle.
        CoreBattleEngine base=new CoreBattleEngine(123L);
        boolean centered=false;
        for(CoreBattleEngine.Peg peg:base.pegs){
            if(Math.abs(peg.x-540f)<.1f && Math.abs(peg.y-1015f)<.1f &&
               peg.r>=20 && peg.r<=30) centered=true;
        }
        if(!centered)
            throw new AssertionError("Expected neutral stationary center peg");
        // Same-speed marbles arriving from opposite directions must rebound
        // with mirror-equivalent horizontal velocities from the center peg.
        CoreBattleEngine left=new CoreBattleEngine(108L);
        CoreBattleEngine right=new CoreBattleEngine(108L);
        CoreBattleEngine.Marble fromLeft=new CoreBattleEngine.Marble(
            6001,0,505f,1015f,150f,0f,15f,1f);
        CoreBattleEngine.Marble fromRight=new CoreBattleEngine.Marble(
            6002,1,575f,1015f,-150f,0f,15f,1f);
        left.marbles.add(fromLeft); left.active[0]++;
        right.marbles.add(fromRight);right.active[1]++;
        for(int i=0;i<2;i++){
            left.advance(1f/60f);
            right.advance(1f/60f);
        }
        if(!fromLeft.active || !fromRight.active)
            throw new AssertionError("Center obstacle consumed marbles");
        if(fromLeft.vx>=0 || fromRight.vx<=0 ||
           Math.abs(fromLeft.vx+fromRight.vx)>2f)
            throw new AssertionError("Center bumper is biased: vx="+
                fromLeft.vx+"/"+fromRight.vx);
        System.out.println("PASS: center bumper gives equal and opposite rebounds");
    }

    private static void physicsRegressions(){
        // Historical bug: projectiles vanished mid-arena at age 19 seconds.
        CoreBattleEngine g=new CoreBattleEngine(77);
        for(int i=0;i<40;i++)g.advance(1f/30f);
        if(g.marbles.isEmpty())throw new AssertionError("No spawned projectile");
        CoreBattleEngine.Marble first=g.marbles.get(0);
        first.x=120;first.y=420;first.vx=0;first.vy=0;first.age=28f;
        int before=g.active[first.side];
        g.advance(1f/30f);
        if(!first.active || g.active[first.side]<before)
            throw new AssertionError("In-arena marble vanished only because of its age");

        // Cross-team balls must exchange impulses rather than passing through.
        CoreBattleEngine cross=new CoreBattleEngine(91);
        CoreBattleEngine.Marble a=new CoreBattleEngine.Marble(
            9101,0,405,425,110,0,15,1);
        CoreBattleEngine.Marble b=new CoreBattleEngine.Marble(
            9102,1,429,425,-110,0,15,1);
        cross.marbles.add(a);cross.marbles.add(b);
        cross.active[0]++;cross.active[1]++;
        cross.advance(1f/30f);
        if(a.vx>=0||b.vx<=0)
            throw new AssertionError("Opposing marbles did not collide physically");

        // No scripted difference in core defenses or spawn rate.
        CoreBattleEngine equal=new CoreBattleEngine(13);
        if(equal.armorTier[0]!=equal.armorTier[1])
            throw new AssertionError("Unfair invisible armor tiers");
        equal.hp[0]=150;
        for(int i=0;i<150;i++)equal.advance(1f/30f);
        if(equal.spawned[0]!=equal.spawned[1])
            throw new AssertionError("Losing team received an invisible spawn boost");
        // Armor must intercept incoming marbles and clearly flash on a hit.
        CoreBattleEngine armored=new CoreBattleEngine(94);
        CoreBattleEngine.Marble projectile=new CoreBattleEngine.Marble(
            20000,1,CoreBattleEngine.CORE_X[0]+2,CoreBattleEngine.CORE_Y,
            0,0,14,1.2f);
        armored.marbles.add(projectile);armored.active[1]++;
        armored.advance(1f/30f);
        if(armored.hp[0]!=CoreBattleEngine.MAX_HP ||
           armored.shieldHits[0]==0 || !projectile.active)
            throw new AssertionError("Shield failed to intercept projectile");
        // Destruction screen must animate instead of appearing instantaneously.
        CoreBattleEngine outro=new CoreBattleEngine(95);
        outro.hp[0]=0;
        outro.advance(1f/30f);
        if(!outro.ending || outro.finished)
            throw new AssertionError("No destruction transition");
        for(int i=0;i<85;i++)outro.advance(1f/30f);
        if(!outro.finished)
            throw new AssertionError("Destruction transition never completed");
        System.out.println("PASS: no phantom despawns; real shield collisions; delayed destruction; symmetric defenses/spawn");
    }

    public static void main(String[] args){
        neutralCenterTest();
        physicsRegressions();
        int[] victories={0,0}, physicalVictories={0,0};
        int totalHits=0,maxPeak=0,shieldImpacts=0;
        for(int k=0;k<32;k++){
            long seed=12345678L+k*1301981L;
            Result a=simulate(seed);
            Result b=simulate(seed);
            if(k<6)System.out.println("CORE DIAGNOSTIC seed="+seed+" hp="+a.hp0+"/"+a.hp1+" hits="+a.damage0+"/"+a.damage1+" spawn="+a.spawn0+"/"+a.spawn1+" boosts="+a.boost0+"/"+a.boost1+" approach="+a.approach0+"/"+a.approach1+" meanX="+a.approachX0+"/"+a.approachX1+" winner="+a.winner+" physicalWinner="+a.physicalWinner);
            victories[a.winner]++;
            physicalVictories[a.physicalWinner]++;
            totalHits+=a.damage0+a.damage1;
            shieldImpacts+=a.shield0+a.shield1;
            maxPeak=Math.max(maxPeak,a.peak);
            if(a.winner!=b.winner || a.physicalWinner!=b.physicalWinner ||Float.compare(a.elapsed,b.elapsed)!=0 ||
                Float.compare(a.hp0,b.hp0)!=0 ||Float.compare(a.hp1,b.hp1)!=0 ||
                a.spawn0!=b.spawn0||a.spawn1!=b.spawn1||
                a.damage0!=b.damage0||a.damage1!=b.damage1||
                a.shield0!=b.shield0||a.shield1!=b.shield1)
                throw new AssertionError("Export outcome differed for seed "+seed);
        }
        if(totalHits+shieldImpacts<16)throw new AssertionError("No real hits on enemy cores or armor: "+totalHits+" shield="+shieldImpacts);
        if(victories[0]==0||victories[1]==0||physicalVictories[0]==0||physicalVictories[1]==0)
            throw new AssertionError("Unbalanced winners: entrants="+victories[0]+"/"+victories[1]+" physical="+physicalVictories[0]+"/"+physicalVictories[1]);
        System.out.println("PASS: 32 physics battles + exact replays; real hits="+totalHits+
            ", armor impacts="+shieldImpacts+", max marbles="+maxPeak+", winners="+victories[0]+"/"+victories[1]+" physical="+physicalVictories[0]+"/"+physicalVictories[1]);
    }
}
