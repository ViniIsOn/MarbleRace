package com.viniison.marblerace;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Original two-core auto-battle. Pure Java, reproducible from a race seed.
 *  Match lasts 60-90 game seconds, with a clean winner at the end.
 */
public final class CoreBattleEngine {
    public static final int SHOT=0,BOMB=1,SAW=2,LASER=3,CRANE=4;
    public static final float MIN_DURATION=60f,MAX_DURATION=90f;
    public static final float MAX_HP=320f;
    public static final float[] CORE_X={268f,812f};
    public static final float CORE_Y=905f;
    public static final class Projectile {
        public float x,y,vx,vy,age;
        public int side,type;
        public boolean active=true;
        Projectile(int side,int type,float x,float y,float vx,float vy){
            this.side=side;this.type=type;this.x=x;this.y=y;this.vx=vx;this.vy=vy;
        }
    }
    public static final class Impact {
        public final float x,y;
        public final int type,side;
        public float life=1;
        Impact(float x,float y,int type,int side){
            this.x=x;this.y=y;this.type=type;this.side=side;
        }
    }
    public final ArrayList<Projectile> projectiles=new ArrayList<>();
    public final ArrayList<Impact> impacts=new ArrayList<>();
    public final float[] hp={MAX_HP,MAX_HP};
    public final int[] hits={0,0};
    public final float[] shieldTime={0,0};
    public final float[] nextAttack={1.4f,1.8f};
    private final Random random;
    private final long seed;
    public float elapsed=0,cranePhase=0,laserFlash=0;
    public int laserSide=-1,winner=-1;
    public boolean finished=false;
    public String event="PREPARE O ATAQUE";
    public float eventTimer=0;

    public CoreBattleEngine(long seed){this.seed=seed;random=new Random(seed^0x4FC0DE12L);reset();}
    public void reset(){
        random.setSeed(seed^0x4FC0DE12L);
        projectiles.clear();impacts.clear();
        for(int s=0;s<2;s++){
            hp[s]=MAX_HP;hits[s]=0;shieldTime[s]=0;
            nextAttack[s]=1.8f+s*.25f+random.nextFloat()*.35f;
        }
        elapsed=0;cranePhase=0;laserFlash=0;laserSide=-1;
        winner=-1;finished=false;
        event="BATALHA DOS NÚCLEOS";eventTimer=2.8f;
    }
    private float rnd(float min,float max){return min+random.nextFloat()*(max-min);}
    private static float clamp(float n,float lo,float hi){return Math.max(lo,Math.min(hi,n));}
    private void damage(int target,float amount,int from,int weapon){
        if(hp[target]<=0)return;
        float multiplier=shieldTime[target]>0?(weapon==LASER?.53f:.22f):1f;
        hp[target]=Math.max(0,hp[target]-amount*multiplier);
        hits[from]++;
        impacts.add(new Impact(CORE_X[target]+rnd(-40,40),CORE_Y+rnd(-45,45),weapon,from));
        if(weapon==BOMB){event="EXPLOSÃO!";eventTimer=.9f;}
        else if(weapon==LASER){event="LASER PERFURANTE";eventTimer=1.1f;}
        else if(weapon==CRANE){event="GOLPE DO GUINDASTE";eventTimer=1.2f;}
        else if(weapon==SAW){event="SERRA GIRATÓRIA";eventTimer=.8f;}
    }
    private void launch(int side,int type){
        int other=1-side;
        if(type==LASER){
            laserSide=side;laserFlash=.32f;
            damage(other,rnd(11,18),side,LASER);
            return;
        }
        if(type==CRANE){
            // A swinging crane occasionally deflects an attack into a core.
            int target=(random.nextFloat()<.77f)?other:side;
            damage(target,rnd(10,19),other==target?side:other,CRANE);
            return;
        }
        float direction=side==0?1f:-1f;
        float px=CORE_X[side]+direction*145;
        float py=CORE_Y-110;
        float speed=type==BOMB?390:type==SAW?465:515;
        float vy=type==BOMB?-105:type==SAW?-15:rnd(-36,26);
        projectiles.add(new Projectile(side,type,px,py,direction*speed,vy));
    }
    private void schedule(int side){
        float roll=random.nextFloat();
        // Mix projectiles, timed shields, laser beams and swinging crane.
        if(roll<.11f){
            shieldTime[side]=2.6f;
            event="ESCUDO ATIVADO";eventTimer=.85f;
        }else if(roll<.48f)launch(side,SHOT);
        else if(roll<.66f)launch(side,BOMB);
        else if(roll<.80f)launch(side,SAW);
        else if(roll<.93f)launch(side,LASER);
        else launch(side,CRANE);
        nextAttack[side]=elapsed+rnd(.72f,1.19f);
    }
    public void advance(float dt){
        if(finished)return;
        dt=clamp(dt,0,.04f);
        elapsed+=dt;
        cranePhase+=dt*1.44f;
        eventTimer=Math.max(0,eventTimer-dt);
        laserFlash=Math.max(0,laserFlash-dt);
        for(int s=0;s<2;s++){
            shieldTime[s]=Math.max(0,shieldTime[s]-dt);
            if(elapsed>=nextAttack[s] && hp[s]>0) schedule(s);
        }
        for(Projectile p:projectiles){
            if(!p.active)continue;
            p.age+=dt;
            if(p.type==BOMB)p.vy+=200f*dt;
            else if(p.type==SAW)p.vy+=(float)Math.sin(p.age*7f)*120f*dt;
            else p.vy+=70f*dt;
            p.x+=p.vx*dt;
            p.y+=p.vy*dt;
            int target=1-p.side;
            float dx=p.x-CORE_X[target],dy=p.y-CORE_Y;
            float reach=p.type==BOMB?155:p.type==SAW?158:151;
            if(dx*dx+dy*dy<reach*reach){
                float power=p.type==BOMB?rnd(7,12):p.type==SAW?rnd(4,8):rnd(3.5f,6.5f);
                damage(target,power,p.side,p.type);
                p.active=false;
            }
            if(p.age>3.4f || p.x<0||p.x>1080 || p.y>1400||p.y<0)p.active=false;
        }
        projectiles.removeIf(p->!p.active);
        for(Impact impact:impacts)impact.life-=dt*2.5f;
        impacts.removeIf(p->p.life<=0);
        // No indefinite matches and no abrupt 10-second bouts.
        if(elapsed>=MIN_DURATION && (hp[0]<=0||hp[1]<=0) || elapsed>=MAX_DURATION){
            finished=true;
            if(hp[0]!=hp[1])winner=hp[0]>hp[1]?0:1;
            else if(hits[0]!=hits[1])winner=hits[0]>hits[1]?0:1;
            else winner=(int)(Math.abs(seed)%2L);
            event="FIM DA BATALHA";eventTimer=2.5f;
        }
    }
    public float remaining(){return Math.max(0,MAX_DURATION-elapsed);}
}
