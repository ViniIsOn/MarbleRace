package com.viniison.marblerace;

import android.graphics.Bitmap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

public final class RaceEngine {
    public static final float WORLD_W = 1080f;
    public static final float FINISH_Y = 6200f;
    public static final float BALL_R = 40f;
    public static final int[] PALETTE = {
        0xFFC6F47A, 0xFF86E1F9, 0xFFFFA684, 0xFFFFD67D,
        0xFFD6B0FF, 0xFF89B7FF, 0xFFFF95CC, 0xFFF4F2DC
    };
    public static class Racer {
        public String name;
        public String imagePath;
        public Bitmap avatar;
        public int color;
        public Racer(String n, String path, Bitmap bitmap, int c) {
            name=n; imagePath=path; avatar=bitmap; color=c;
        }
    }
    public static class Ball {
        public Racer racer;
        public float x,y,vx,vy;
        public int place=0;
        public float finishTime=-1;
        Ball(Racer r) {racer=r;}
    }
    public static class Bumper {
        public final float x,y,r;
        Bumper(float xx,float yy,float rr){x=xx;y=yy;r=rr;}
    }
    public final ArrayList<Racer> racers = new ArrayList<>();
    public final ArrayList<Ball> balls = new ArrayList<>();
    public final ArrayList<Bumper> bumpers = new ArrayList<>();
    public boolean running=false;
    public float elapsed=0;
    public int finished=0;

    public RaceEngine(List<Racer> entries) {
        racers.addAll(entries);
        Random r = new Random(3748);
        for(int i=0;i<17;i++){
            float y=550+i*325f;
            float x=centerAt(y) + (r.nextFloat()-.5f)*430;
            bumpers.add(new Bumper(x,y,34+i%3*8));
            if(i%3==1) bumpers.add(new Bumper(centerAt(y+120)+(r.nextFloat()-.5f)*410,y+120,27));
        }
        reset();
    }
    public static float centerAt(float y) {
        return 540f + 112f*(float)Math.sin(y*.0017) + 44f*(float)Math.sin(y*.0055);
    }
    public void reset() {
        balls.clear();
        for(int i=0;i<racers.size();i++) {
            Ball b=new Ball(racers.get(i));
            b.x=centerAt(120)+(-1.5f+i%4)*137f;
            b.y=110+(i/4)*105;
            b.vx=(i%2==0?1:-1)*(55+i*7);
            b.vy=100+i*9;
            balls.add(b);
        }
        elapsed=0;
        finished=0;
        running=false;
    }
    public void toggle(){running=!running;}
    public void advance(float dt) {
        if(!running || finished>=balls.size() || balls.isEmpty()) return;
        dt=Math.min(.04f,Math.max(0,dt));
        elapsed+=dt;
        for(int k=0;k<balls.size();k++) {
            Ball b=balls.get(k);
            if(b.place>0)continue;
            b.vy=Math.min(455f,b.vy+210f*dt);
            b.vx+=Math.sin(elapsed*1.8+k*2.4)*65*dt;
            b.vx*=.998f;
            b.x+=b.vx*dt;
            b.y+=b.vy*dt;
            float c=centerAt(b.y);
            float min=c-338+BALL_R, max=c+338-BALL_R;
            if(b.x<min){b.x=min;b.vx=Math.abs(b.vx)*.78f+20;}
            if(b.x>max){b.x=max;b.vx=-Math.abs(b.vx)*.78f-20;}
            for(Bumper p:bumpers) {
                float dy=b.y-p.y;
                if(Math.abs(dy)>100)continue;
                float dx=b.x-p.x, rad=BALL_R+p.r+3f;
                float d2=dx*dx+dy*dy;
                if(d2<rad*rad && d2>.01f) {
                    float d=(float)Math.sqrt(d2), nx=dx/d,ny=dy/d;
                    b.x=p.x+nx*rad;b.y=p.y+ny*rad;
                    b.vx+=nx*245;
                    b.vy=Math.max(115,b.vy+ny*55-43);
                }
            }
            if(b.vx>360)b.vx=360;
            if(b.vx< -360)b.vx=-360;
            b.vy=Math.max(105,b.vy);
            if(b.y>=FINISH_Y){
                b.y=FINISH_Y;
                b.place=++finished;
                b.finishTime=elapsed;
            }
        }
        for(int i=0;i<balls.size();i++)for(int j=i+1;j<balls.size();j++){
            Ball a=balls.get(i),b=balls.get(j);
            if(a.place>0||b.place>0)continue;
            float dx=a.x-b.x,dy=a.y-b.y,d2=dx*dx+dy*dy;
            if(d2<(BALL_R*2)*(BALL_R*2)&&d2>1) {
                float d=(float)Math.sqrt(d2),nx=dx/d,ny=dy/d;
                float push=(BALL_R*2-d)*.5f;
                a.x+=nx*push;b.x-=nx*push;
                a.y+=ny*push;b.y-=ny*push;
                float t=a.vx;a.vx=b.vx*.83f;a.vx+=nx*20;
                b.vx=t*.83f-nx*20;
            }
        }
        if(finished>=balls.size() && !balls.isEmpty())running=false;
    }
    public float leadY(){
        float m=0;
        for(Ball b:balls)m=Math.max(m,b.y);
        return m;
    }
    public Ball winner(){
        for(Ball b:balls)if(b.place==1)return b;
        return null;
    }
    public List<Ball> ranked(){
        ArrayList<Ball> out=new ArrayList<>(balls);
        Collections.sort(out,(a,b)->{
            if(a.place!=0 && b.place!=0)return Integer.compare(a.place,b.place);
            if(a.place!=0)return -1;
            if(b.place!=0)return 1;
            return Float.compare(b.y,a.y);
        });
        return out;
    }
}
