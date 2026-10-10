package com.viniison.marblerace;

import android.graphics.Bitmap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

/** Simulação determinística: a mesma pista produz a mesma corrida no preview e no vídeo. */
public final class RaceEngine {
    public static final float WORLD_W=1080f;
    public static final float FINISH_Y=11500f;
    public static final float BALL_R=40f;
    public static final int TRAIL_SAMPLES=20;
    public static final String[] TRACK_NAMES={"SKY DROP","CANDY LAB","NEON REACTOR","VOLCANO RUSH"};
    public static final String[] TRACK_DETAILS={
        "Pistas aéreas • ventos e molas", "Doces, curvas e rebatedores",
        "Impulso elétrico • rotores", "Calor e obstáculos móveis"
    };
    public static final int[] PALETTE={
        0xFFF75F79,0xFF4DC9FF,0xFFFFB43E,0xFFB48AFF,
        0xFF5DE0AB,0xFFFFFF72,0xFFFF88C9,0xFFEEEEF7
    };
    public static class Racer {
        public String name,imagePath;
        public Bitmap avatar;
        public int color;
        public Racer(String name,String path,Bitmap avatar,int color){
            this.name=name;imagePath=path;this.avatar=avatar;this.color=color;
        }
    }
    public static class Ball {
        public final Racer racer;
        public float x,y,vx,vy,speedBonus,eventTime;
        public float progressMarker,stallTime;
        public int recoveryCount;
        public String eventLabel="";
        public int place=0,trailCount=0,trailCursor=0;
        public float finishTime=-1;
        public final float[] trailX=new float[TRAIL_SAMPLES],trailY=new float[TRAIL_SAMPLES];
        Ball(Racer r){racer=r;}
        void mark(){
            trailX[trailCursor]=x;trailY[trailCursor]=y;
            trailCursor=(trailCursor+1)%TRAIL_SAMPLES;
            if(trailCount<TRAIL_SAMPLES)trailCount++;
        }
    }
    public static class Bumper {
        public final float x,y,r,phase;
        public final int type; // 0 bumper, 1 rotor, 2 mover, 3 spring, 4 glue, 5 kicker, 6 slingshot
        Bumper(float x,float y,float r,int type,float phase){
            this.x=x;this.y=y;this.r=r;this.type=type;this.phase=phase;
        }
    }
    /** Surprise lanes: 0 boost, 1 slowdown, 2 turbo, 3 side kick. */
    public static class Zone {
        public final float y,offset,width;
        public final int kind;
        Zone(float y,float offset,float width,int kind){
            this.y=y;this.offset=offset;this.width=width;this.kind=kind;
        }
    }
    public final ArrayList<Zone> zones=new ArrayList<>();
    public final ArrayList<Racer> racers=new ArrayList<>();
    public final ArrayList<Ball> balls=new ArrayList<>();
    public final ArrayList<Bumper> bumpers=new ArrayList<>();
    public final int track;
    public final long seed;
    private static final AtomicLong ROUND_COUNTER=new AtomicLong(1);
    /** A new unpredictable-but-reproducible race number is assigned per match. */
    public static long newRoundSeed(){
        return System.nanoTime() ^ (System.currentTimeMillis()<<17)
            ^ (ROUND_COUNTER.getAndIncrement()*0x9E3779B97F4A7C15L);
    }
    public boolean running=false;
    public float elapsed=0;
    public int finished=0;
    public RaceEngine(List<Racer> racers){this(racers,0,newRoundSeed());}
    public RaceEngine(List<Racer> entries,int track){this(entries,track,newRoundSeed());}
    public RaceEngine(List<Racer> entries,int track,long seed){
        this.track=Math.max(0,Math.min(TRACK_NAMES.length-1,track));
        this.seed=seed;
        racers.addAll(entries);
        Random random=new Random(seed ^ (10942L+this.track*619L));
        // Spread obstacles into separated groups, preserving two passable lanes.
        // No overlapping obstacle disks and no movers pinning balls to a wall.
        // Different seeds still produce visibly different surprise layouts.
        for(int i=0;i<53;i++){
            float y=450+i*201f+random.nextInt(26);
            int type=random.nextInt(7);
            float lane=(random.nextBoolean()?-1:1)*(95+random.nextInt(105));
            float x=centerAt(y)+lane;
            float radius=type==1?45:type==3?37:29+random.nextInt(15);
            bumpers.add(new Bumper(x,y,radius,type,random.nextFloat()*6.28f));
            if(i%3==1){
                float extraY=y+96;
                float extraX=centerAt(extraY)-lane*.93f;
                int extraType=random.nextInt(7);
                bumpers.add(new Bumper(extraX,extraY,29+random.nextInt(11),
                    extraType,random.nextFloat()*6.28f));
            }
        }
        // Half-lane effects create real lead changes, not cosmetic decoration.
        for(int i=0;i<18;i++){
            float y=850+i*570f+random.nextInt(140);
            float offset=random.nextBoolean()?-178:178;
            zones.add(new Zone(y,offset,300,random.nextInt(4)));
        }
        reset();
    }
    public float centerAt(float y){
        switch(track){
            case 1: return 540f+155f*(float)Math.sin(y*.00145f)+29f*(float)Math.sin(y*.006f);
            case 2: return 540f+80f*(float)Math.sin(y*.00245f)+70f*(float)Math.sin(y*.0041f);
            case 3: return 540f+130f*(float)Math.sin(y*.00197f)+42f*(float)Math.cos(y*.0054f);
            default:return 540f+112f*(float)Math.sin(y*.0017f)+44f*(float)Math.sin(y*.0055f);
        }
    }
    public float obstacleX(Bumper bumper){
        return bumper.x+((bumper.type==2||bumper.type==5)?
            (float)Math.sin(elapsed*(bumper.type==5?3.3f:2.4f)+bumper.phase)*70f:0f);
    }
    public void reset(){
        // Reset only replays the same seed. Create a new engine for a NEW race.
        Random starter=new Random(seed ^ 0xD1B54A32D192ED03L);
        balls.clear();
        for(int i=0;i<racers.size();i++){
            Ball b=new Ball(racers.get(i));
            b.x=centerAt(120)+(-1.5f+i%4)*137f+(starter.nextFloat()-.5f)*35f;
            b.y=110+(i/4)*105;
            b.vx=(starter.nextFloat()-.5f)*330f;
            b.vy=145+starter.nextFloat()*145f;
            b.eventTime=0;b.eventLabel="";
            b.progressMarker=b.y;b.stallTime=0;b.recoveryCount=0;
            b.speedBonus=(starter.nextFloat()-.5f)*85f;
            b.mark();
            balls.add(b);
        }
        elapsed=0;finished=0;running=false;
    }
    public void toggle(){running=!running;}
    public void advance(float dt){
        if(!running||balls.isEmpty()||finished>=balls.size())return;
        dt=Math.min(.04f,Math.max(0,dt));
        elapsed+=dt;
        for(int k=0;k<balls.size();k++){
            Ball b=balls.get(k);
            if(b.place>0)continue;
            b.eventTime=Math.max(0,b.eventTime-dt);
            float acceleration=track==3?230f:210f;
            b.vy=Math.min((track==2?485f:455f)+b.speedBonus,b.vy+acceleration*dt);
            b.vx+=Math.sin(elapsed*1.8f+k*2.4f+track)*72*dt;
            b.vx*=.995f;
            float prevY=b.y;
            b.x+=b.vx*dt;b.y+=b.vy*dt;
            // Energy strips every ~900 world units add visible bursts of speed.
            int before=(int)Math.floor(Math.max(0,prevY-650)/940f);
            int after=(int)Math.floor(Math.max(0,b.y-650)/940f);
            if(after>before && b.y>650){b.vy=Math.min(640,b.vy+135);b.vx+=((k&1)==0?1:-1)*75;}
            float center=centerAt(b.y);
            float min=center-338+BALL_R,max=center+338-BALL_R;
            if(b.x<min){b.x=min;b.vx=Math.abs(b.vx)*.8f+22;}
            if(b.x>max){b.x=max;b.vx=-Math.abs(b.vx)*.8f-22;}
            for(Bumper p:bumpers){
                float dy=b.y-p.y;
                if(Math.abs(dy)>120)continue;
                float ox=obstacleX(p);
                float dx=b.x-ox,rad=BALL_R+p.r+(p.type==1?13:3);
                float d2=dx*dx+dy*dy;
                if(d2>=rad*rad)continue;
                // Head-on contact used to push marbles upward every frame.
                // Choose a deterministic sideways escape instead of trapping
                // them on top of a bumper or between two neighboring disks.
                float direction=dx< -1?-1:dx>1?1:((k+(int)(p.phase*9))%2==0?-1:1);
                float d=(float)Math.sqrt(Math.max(.0001f,d2));
                float nx=dx/d,ny=dy/d;
                float penetration=rad-d;
                float lateral=direction*Math.max(9f,Math.min(28f,penetration*.72f));
                b.x+=lateral;
                if(d>2f){
                    // Small position correction, never a full vertical rewind.
                    b.x+=nx*Math.min(12f,penetration*.25f);
                    b.y+=ny*Math.min(4f,penetration*.12f);
                }
                b.vx=direction*Math.max(Math.abs(b.vx),235f+(p.type==5?65:0));
                b.vy=Math.max(185,b.vy*.91f);
                // Trigger special effects once per contact, not on each frame
                // of continued contact with the same obstacle.
                if(p.type==3){
                    b.vy+=155;b.eventLabel="MOLA!";b.eventTime=.9f;
                }else if(p.type==4){
                    b.vy=Math.max(135,b.vy*.82f);
                    b.eventLabel="LENTO!";b.eventTime=.9f;
                }else if(p.type==5){
                    b.vx+=direction*65;b.eventLabel="REBATIDA!";b.eventTime=.9f;
                }else if(p.type==6){
                    b.vy+=175;b.eventLabel="TURBO!";b.eventTime=.9f;
                }
            }
            for(Zone zone:zones) {
                if(prevY<zone.y && b.y>=zone.y &&
                    Math.abs(b.x-(centerAt(zone.y)+zone.offset))<zone.width*.5f+BALL_R) {
                    if(zone.kind==0){
                        b.vy+=320;b.eventLabel="BOOST!";b.eventTime=1;
                    }else if(zone.kind==1){
                        b.vy=Math.max(110,b.vy*.45f);
                        b.eventLabel="ARMADILHA!";b.eventTime=1;
                    }else if(zone.kind==2){
                        b.vy+=440;b.eventLabel="ULTRA TURBO!";b.eventTime=1;
                    }else{
                        b.vx+=(zone.offset>0?-1:1)*355f;
                        b.vy+=140;b.eventLabel="REVIRAVOLTA!";b.eventTime=1;
                    }
                }
            }
            b.vx=Math.max(-485,Math.min(485,b.vx));
            b.vy=Math.max(150,b.vy);
            // Detect stalled progress using world distance instead of speed.
            // As a last resort, roll forward one obstacle diameter, not to the
            // finish line. This guarantees no endless stuck ball.
            if(b.y>=b.progressMarker+52){
                b.progressMarker=b.y;
                b.stallTime=0;
            }else{
                b.stallTime+=dt;
                if(b.stallTime>2.15f){
                    float side=((k+b.recoveryCount)&1)==0?1f:-1f;
                    b.y+=112f;
                    b.x+=side*50f;
                    b.vx=side*270f;
                    b.vy=Math.max(b.vy,315f);
                    b.progressMarker=b.y;
                    b.stallTime=0f;
                    b.recoveryCount++;
                    b.eventLabel="DESVIO!";
                    b.eventTime=1.0f;
                }
            }
            // Collision with walls after bumper impact too.
            center=centerAt(b.y);
            b.x=Math.max(center-338+BALL_R,Math.min(center+338-BALL_R,b.x));
            if(b.y>=FINISH_Y){
                b.y=FINISH_Y;b.place=++finished;b.finishTime=elapsed;
            }
            b.mark();
        }
        for(int i=0;i<balls.size();i++)for(int j=i+1;j<balls.size();j++){
            Ball a=balls.get(i),b=balls.get(j);
            if(a.place>0||b.place>0)continue;
            float dx=a.x-b.x,dy=a.y-b.y,d2=dx*dx+dy*dy;
            if(d2<(BALL_R*2)*(BALL_R*2)&&d2>1){
                float d=(float)Math.sqrt(d2),nx=dx/d,ny=dy/d;
                float push=(BALL_R*2-d)*.5f;
                a.x+=nx*push;b.x-=nx*push;
                a.y+=ny*push;b.y-=ny*push;
                float va=a.vx;a.vx=b.vx*.83f+nx*20;b.vx=va*.83f-nx*20;
            }
        }
        if(finished==balls.size())running=false;
    }
    public float leadY(){
        // Keep the camera on competitors who are still racing, instead of
        // locking to the finish line after the first ball gets there.
        float activeLead=0;
        boolean active=false;
        for(Ball b:balls){
            if(b.place==0){
                active=true;
                activeLead=Math.max(activeLead,b.y);
            }
        }
        return active?activeLead:(finished>0?FINISH_Y:0);
    }
    public Ball winner(){for(Ball b:balls)if(b.place==1)return b;return null;}
    public List<Ball> ranked(){
        ArrayList<Ball> list=new ArrayList<>(balls);
        Collections.sort(list,(a,b)->{
            if(a.place!=0&&b.place!=0)return Integer.compare(a.place,b.place);
            if(a.place!=0)return -1;
            if(b.place!=0)return 1;
            return Float.compare(b.y,a.y);
        });
        return list;
    }
}
