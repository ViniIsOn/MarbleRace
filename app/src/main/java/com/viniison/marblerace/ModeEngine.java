package com.viniison.marblerace;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Original creator mini-games built on a shared deterministic fixed-step simulation.
 * Modes: circular escape, shrinking arena, core destruction, worm sprint and cycle race.
 */
public final class ModeEngine {
    public static final String[] NAMES={
        "CORRIDA DO ARO","ELIMINAÇÃO","DESTRUIR O NÚCLEO","CORRIDA DE MINHOCAS","BICICLETAS"
    };
    public static final String[] DESCRIPTIONS={
        "Contagem no aro, depois pista com obstáculos e chegada",
        "A arena encolhe até sobrar um vencedor",
        "Acertar o núcleo central dá pontos",
        "Minhocas coloridas disputam uma pista",
        "Bicicletas enfrentam rampas e obstáculos"
    };
    public static final int RING=0,ELIMINATION=1,CORE=2,WORMS=3,BIKES=4;
    public static final float CX=540f, CY=905f, FINISH=6250f;
    public static class Orb {
        public final RaceEngine.Racer racer;
        public float x,y,vx,vy,score,jumpTime;
        public int hits=0;
        public boolean out=false;
        public int place=0, trailN=0,trailIndex=0;
        public final float[] trailX=new float[28],trailY=new float[28];
        Orb(RaceEngine.Racer r){racer=r;}
        void mark(){
            trailX[trailIndex]=x;trailY[trailIndex]=y;
            trailIndex=(trailIndex+1)%trailX.length;
            if(trailN<trailX.length)trailN++;
        }
    }
    public final ArrayList<Orb> orbs=new ArrayList<>();
    public final int mode;
    public static final float INTRO_SECONDS=3f, TRANSITION_SECONDS=.65f;
    public final RaceEngine ringRace;
    public boolean running=false;
    public int finished=0;
    public float elapsed=0,coreLife=28;
    private final Random random;
    public ModeEngine(List<RaceEngine.Racer> racers,int mode){
        this.mode=Math.max(0,Math.min(NAMES.length-1,mode));
        for(RaceEngine.Racer racer:racers)orbs.add(new Orb(racer));
        ringRace=this.mode==RING?new RaceEngine(racers,0):null;
        random=new Random(889+this.mode*31L);
        reset();
    }
    public float radius(){return mode==ELIMINATION?Math.max(105,375-Math.max(0,elapsed-3)*7):379;}
    public float exitAngle(){return 90+38*(float)Math.sin(elapsed*.55f);}
    public boolean started(){return elapsed>=3;}
    public void reset(){
        random.setSeed(889+mode*31L);
        elapsed=0;running=false;finished=0;coreLife=28;
        if(ringRace!=null)ringRace.reset();
        for(int i=0;i<orbs.size();i++){
            Orb b=orbs.get(i);
            b.place=0;b.out=false;b.score=0;b.hits=0;b.jumpTime=0;b.trailN=0;b.trailIndex=0;
            float angle=(float)(i*2*Math.PI/Math.max(1,orbs.size()));
            if(mode==RING){
                // Racers gather at the bottom of the closed starting ring.
                int row=i/4,position=i%4;
                int count=Math.min(4,orbs.size()-row*4);
                b.x=CX+(position-(count-1)/2f)*105;
                b.y=CY+236-row*104;
                b.vx=0;b.vy=0;
            }else if(mode==WORMS){
                b.x=180+(i%4)*225;b.y=180+(i/4)*96;
                b.vx=0;b.vy=210+i*16;
            }else if(mode==BIKES){
                b.x=50+(i%4)*10;b.y=660+i*125;
                b.vx=210+i*12;b.vy=0;
            }else{
                b.x=CX+(float)Math.cos(angle)*165;
                b.y=CY+(float)Math.sin(angle)*165;
                b.vx=(random.nextFloat()-.5f)*420;
                b.vy=(random.nextFloat()-.5f)*400;
            }
            b.mark();
        }
    }
    public void toggle(){running=!running;}
    private static float clamp(float v,float min,float max){return Math.max(min,Math.min(max,v));}
    private static float normalizeAngle(float a){
        a%=360f;
        if(a<0)a+=360f;
        return a;
    }
    private void eliminate(Orb ball){
        if(ball.out)return;
        ball.out=true;
        finished++;
        ball.place=Math.max(1,orbs.size()-finished+1);
    }
    private void win(Orb ball){
        if(ball.place!=0)return;
        ball.place=1;
        ball.out=true;
        finished++;
        running=false;
    }
    public void advance(float dt){
        if(!running||orbs.size()<2||((mode==RING||mode==ELIMINATION||mode==CORE)&&winner()!=null))return;
        dt=clamp(dt,0,.04f);
        elapsed+=dt;
        if(mode==RING){
            // A closed circle introduces the competitors; it is NOT an elimination arena.
            // The 3-second countdown and 0.65-second handoff precede the obstacle course.
            if(elapsed>=INTRO_SECONDS+TRANSITION_SECONDS && ringRace!=null){
                if(ringRace.finished<ringRace.balls.size()){
                    ringRace.running=true;
                    ringRace.advance(dt);
                }
                finished=ringRace.finished;
                if(finished==ringRace.balls.size() && finished>0)running=false;
            }
            return;
        }
        if(!started())return;
        float step=elapsed-3;
        if(mode==WORMS){
            int i=0;
            for(Orb ball:orbs){
                if(ball.place!=0){i++;continue;}
                float pace=235+(i%4)*14+28*(float)Math.sin(step*.9f+i);
                float prev=ball.y;
                ball.y+=Math.max(95,pace)*dt;
                float curve=(float)Math.sin(ball.y*.0024f+i*.7f)*180;
                ball.x=clamp(540+curve+(i%4-1.5f)*105,100,980);
                if((int)(prev/700)!=(int)(ball.y/700))
                    ball.y+=17+i*2;
                ball.mark();
                if(ball.y>=FINISH){
                    ball.y=FINISH;
                    ball.place=++finished;
                    if(finished==orbs.size())running=false;
                }
                i++;
            }
            return;
        }
        if(mode==BIKES){
            int i=0;
            for(Orb ball:orbs){
                if(ball.place!=0){i++;continue;}
                float pace=215+(i%4)*14+17*(float)Math.cos(step*1.7f+i);
                float before=ball.x;
                ball.x+=Math.max(125,pace-((ball.hits>0)?ball.hits*11:0))*dt;
                // Each marked ramp is a game event, not just visual decoration.
                int prior=(int)Math.floor((before-(115+(i%2)*80+133))/330f);
                int next=(int)Math.floor((ball.x-(115+(i%2)*80+133))/330f);
                if(next>prior && next>=0 && (next+i)%4==0){
                    ball.jumpTime=.56f;
                    if((next+i)%5==0){ball.hits++;ball.x=Math.max(before,ball.x-65);}
                }
                ball.jumpTime=Math.max(0,ball.jumpTime-dt);
                ball.y=640+i*135-70*(float)Math.sin(ball.jumpTime/.56f*Math.PI)
                    +28*(float)Math.sin(ball.x*.0053f+i);
                ball.mark();
                if(ball.x>=FINISH){
                    ball.x=FINISH;ball.place=++finished;
                    if(finished==orbs.size())running=false;
                }
                i++;
            }
            return;
        }
        float radius=radius();
        for(int i=0;i<orbs.size();i++){
            Orb b=orbs.get(i);
            if(b.out)continue;
            // Gravity and low drag: more weighty, less random jitter.
            b.vy+=350*dt;
            b.vx*=.999f;b.vy*=.999f;
            if(mode==CORE){
                float dx=CX-b.x,dy=CY-b.y;
                float dist=(float)Math.sqrt(dx*dx+dy*dy)+.001f;
                b.vx+=dx/dist*115*dt;
                b.vy+=dy/dist*115*dt;
            }
            b.x+=b.vx*dt;b.y+=b.vy*dt;
            float dx=b.x-CX,dy=b.y-CY;
            float dist=(float)Math.sqrt(dx*dx+dy*dy);
            float boundary=radius-41f;
            if(mode==CORE && dist<117){
                float nx=dx/Math.max(dist,1),ny=dy/Math.max(dist,1);
                b.x=CX+nx*118;b.y=CY+ny*118;
                float towards=b.vx*nx+b.vy*ny;
                b.vx-=1.7f*towards*nx;b.vy-=1.7f*towards*ny;
                b.vx+=nx*180;b.vy+=ny*180;
                b.score++;
                coreLife=Math.max(0,coreLife-1);
            }
            if(dist>=boundary){
                float nx=dx/Math.max(1,dist),ny=dy/Math.max(1,dist);
                float angle=normalizeAngle((float)Math.toDegrees(Math.atan2(dy,dx)));
                float gap=Math.abs(angle-exitAngle());
                gap=Math.min(gap,360-gap);
                if(mode==RING && gap<19){
                    eliminate(b);
                }else if(mode==ELIMINATION && step>32){
                    eliminate(b);
                }else{
                    b.x=CX+nx*boundary;b.y=CY+ny*boundary;
                    float dot=b.vx*nx+b.vy*ny;
                    if(dot>0){b.vx-=1.85f*dot*nx;b.vy-=1.85f*dot*ny;}
                    b.vx+=-ny*(i%2==0?38:-38);
                }
            }
            b.vx=clamp(b.vx,-610,610);
            b.vy=clamp(b.vy,-650,650);
            b.mark();
        }
        // Pairwise collision response.
        for(int i=0;i<orbs.size();i++)for(int j=i+1;j<orbs.size();j++){
            Orb a=orbs.get(i),b=orbs.get(j);
            if(a.out||b.out)continue;
            float dx=a.x-b.x,dy=a.y-b.y;
            float dist=(float)Math.sqrt(dx*dx+dy*dy);
            if(dist<82 && dist>1){
                float nx=dx/dist,ny=dy/dist;
                float push=(82-dist)*.51f;
                a.x+=nx*push;a.y+=ny*push;
                b.x-=nx*push;b.y-=ny*push;
                float rv=(a.vx-b.vx)*nx+(a.vy-b.vy)*ny;
                if(rv<0){
                    float imp=-rv*.90f;
                    a.vx+=imp*nx;a.vy+=imp*ny;
                    b.vx-=imp*nx;b.vy-=imp*ny;
                }
            }
        }
        if(mode==CORE&&coreLife<=0){
            Orb best=null;
            for(Orb b:orbs)if(best==null||b.score>best.score)best=b;
            if(best!=null)win(best);
        }
        if((mode==RING||mode==ELIMINATION)&&elapsed>7){
            int remain=0;Orb candidate=null;
            for(Orb b:orbs)if(!b.out){remain++;candidate=b;}
            if(remain==1 && candidate!=null)win(candidate);
            if(remain==0)running=false;
        }
        if(elapsed>37){
            Orb best=null;
            for(Orb b:orbs)if(!b.out && (best==null||b.score>best.score))best=b;
            if(best==null&&!orbs.isEmpty())best=orbs.get(0);
            if(best!=null)win(best);
        }
    }
    public Orb winner(){
        if(mode==RING){
            if(ringRace==null || ringRace.finished<ringRace.balls.size() || ringRace.balls.isEmpty())
                return null;
            RaceEngine.Ball first=ringRace.winner();
            if(first==null)return null;
            for(Orb orb:orbs)if(orb.racer==first.racer)return orb;
            return null;
        }
        for(Orb b:orbs)if(b.place==1)return b;
        return null;
    }
    public float progress(){
        if(mode==RING && ringRace!=null)return ringRace.leadY();
        float p=0;
        for(Orb b:orbs)p=Math.max(p,mode==BIKES?b.x:b.y);
        return p;
    }
}
