package com.viniison.marblerace;

import android.graphics.Bitmap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** Simulação determinística: a mesma pista produz a mesma corrida no preview e no vídeo. */
public final class RaceEngine {
    public static final float WORLD_W=1080f;
    public static final float FINISH_Y=6200f;
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
        public float x,y,vx,vy;
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
        public final int type; // 0 round bumper; 1 rotor; 2 oscillating bumper
        Bumper(float x,float y,float r,int type,float phase){
            this.x=x;this.y=y;this.r=r;this.type=type;this.phase=phase;
        }
    }
    public final ArrayList<Racer> racers=new ArrayList<>();
    public final ArrayList<Ball> balls=new ArrayList<>();
    public final ArrayList<Bumper> bumpers=new ArrayList<>();
    public final int track;
    public boolean running=false;
    public float elapsed=0;
    public int finished=0;
    public RaceEngine(List<Racer> racers){this(racers,0);}
    public RaceEngine(List<Racer> entries,int track){
        this.track=Math.max(0,Math.min(TRACK_NAMES.length-1,track));
        racers.addAll(entries);
        Random random=new Random(10942+this.track*619L);
        for(int i=0;i<20;i++){
            float y=480+i*278f;
            float cx=centerAt(y);
            int type=(i%5==2)?1:(i%4==1?2:0);
            float x=cx+(random.nextFloat()-.5f)*430;
            float r=type==1?39:30+i%4*7;
            bumpers.add(new Bumper(x,y,r,type,i*.91f));
            if((i+track)%3==0)
                bumpers.add(new Bumper(centerAt(y+125)+(random.nextFloat()-.5f)*390,y+125,27,0,i));
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
        return bumper.x+(bumper.type==2?(float)Math.sin(elapsed*2.8f+bumper.phase)*115f:0f);
    }
    public void reset(){
        balls.clear();
        for(int i=0;i<racers.size();i++){
            Ball b=new Ball(racers.get(i));
            b.x=centerAt(120)+(-1.5f+i%4)*137f;
            b.y=110+(i/4)*105;
            b.vx=(i%2==0?1:-1)*(55+i*7);
            b.vy=125+i*12;
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
            float acceleration=track==3?230f:210f;
            b.vy=Math.min(track==2?485f:455f,b.vy+acceleration*dt);
            b.vx+=Math.sin(elapsed*1.8f+k*2.4f+track)*72*dt;
            b.vx*=.998f;
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
                if(Math.abs(dy)>115)continue;
                float ox=obstacleX(p);
                float dx=b.x-ox,rad=BALL_R+p.r+(p.type==1?15:3);
                float d2=dx*dx+dy*dy;
                if(d2<rad*rad && d2>.01f){
                    float d=(float)Math.sqrt(d2),nx=dx/d,ny=dy/d;
                    b.x=ox+nx*rad;b.y=p.y+ny*rad;
                    b.vx+=nx*(p.type==1?320:245);
                    b.vy=Math.max(130,b.vy+ny*(p.type==1?90:55)-30);
                }
            }
            b.vx=Math.max(-430,Math.min(430,b.vx));
            b.vy=Math.max(115,b.vy);
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
        float m=0;
        for(Ball b:balls)m=Math.max(m,b.y);
        return m;
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
