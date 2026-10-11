package com.viniison.marblerace;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;

/**
 * Deterministic fixed-step two-dimensional physics arena.
 * Physics is real collision/velocity integration, not animated fake hits.
 * No Android dependencies: run collision/spawner tests on desktop CI.
 */
public final class CoreBattleEngine {
    public static final float MIN_DURATION=60f,MAX_DURATION=95f;
    public static final float MAX_HP=1000f;
    public static final float CORE_Y=1190f, CORE_RADIUS=76f;
    public static final float[] CORE_X={257f,823f};
    public static final int MAX_PER_TEAM=300, MAX_PARTICLES=96;
    public static final float MIN_X=42,MAX_X=1038,TOP=175,BOTTOM=1450;
    private static final float BALL_MIN_R=11f, BALL_MAX_R=17f;
    private static final float CELL=52f, MAX_SPEED=1075f;
    private static final int COLS=22,ROWS=31;
    private static final float GRAVITY=440f,RESTITUTION=.82f;
    private final Random rng;
    private final long seed;
    @SuppressWarnings("unchecked")
    private final ArrayList<Marble>[] cells=(ArrayList<Marble>[])new ArrayList[COLS*ROWS];
    public static final class Marble {
        public float x,y,vx,vy,r,mass,age;
        public final int side;
        public boolean active=true,boosted=false;
        public final int id;
        Marble(int id,int side,float x,float y,float vx,float vy,float r,float mass){
            this.id=id;this.side=side;this.x=x;this.y=y;
            this.vx=vx;this.vy=vy;this.r=r;this.mass=mass;
        }
    }
    public static final class Armor {
        public final int team;
        public final float x,y,w,h,maxHp;
        public float hp;
        Armor(int team,float x,float y,float w,float h,float health){
            this.team=team;this.x=x;this.y=y;this.w=w;this.h=h;
            maxHp=health;hp=health;
        }
    }
    public static final class Peg {
        public final float x,y,r;
        Peg(float x,float y,float r){this.x=x;this.y=y;this.r=r;}
    }
    public static final class Segment {
        public final float x1,y1,x2,y2;
        Segment(float x1,float y1,float x2,float y2){
            this.x1=x1;this.y1=y1;this.x2=x2;this.y2=y2;
        }
    }
    public static final class Spark {
        public float x,y,vx,vy,life;
        public final int color;
        Spark(float x,float y,float vx,float vy,float life,int color){
            this.x=x;this.y=y;this.vx=vx;this.vy=vy;this.life=life;this.color=color;
        }
    }
    public final ArrayList<Marble> marbles=new ArrayList<>();
    public final ArrayList<Armor> armor=new ArrayList<>();
    public final ArrayList<Peg> pegs=new ArrayList<>();
    public final ArrayList<Segment> segments=new ArrayList<>();
    public final ArrayList<Spark> sparks=new ArrayList<>();
    public final float[] hp={MAX_HP,MAX_HP};
    public final float[] flash={0,0};
    public final int[] hits={0,0},active={0,0},spawned={0,0},boosts={0,0},winTotals={0,0};
    public float elapsed=0,cranePhase=0;
    public int winner=-1;
    public boolean finished=false;
    public String event="PREPARE-SE",lastEvent="PREPARE-SE";
    public float eventTimer=0;
    private final float[] nextSpawn={0,0};
    private int nextWave=15, idSequence=0, frames=0;
    private boolean burstAtFinish=false;

    public CoreBattleEngine(long seed){
        this.seed=seed;rng=new Random(seed ^ 0x53A1C4B17L);
        for(int i=0;i<cells.length;i++)cells[i]=new ArrayList<>();
        reset();
    }
    public void reset(){
        rng.setSeed(seed ^ 0x53A1C4B17L);
        marbles.clear();armor.clear();pegs.clear();segments.clear();sparks.clear();
        Arrays.fill(hp,MAX_HP);Arrays.fill(flash,0);
        Arrays.fill(hits,0);Arrays.fill(active,0);Arrays.fill(spawned,0);Arrays.fill(boosts,0);
        nextSpawn[0]=.55f;nextSpawn[1]=.65f;nextWave=15;
        elapsed=0;cranePhase=0;winner=-1;finished=false;event="PREPARE-SE";
        lastEvent=event;eventTimer=2.8f;idSequence=0;frames=0;burstAtFinish=false;
        // Outer border + central separator; funnels guide opposite trajectories.
        segments.add(new Segment(MIN_X,TOP,MIN_X,BOTTOM));
        segments.add(new Segment(MAX_X,TOP,MAX_X,BOTTOM));
        segments.add(new Segment(MIN_X,TOP,MAX_X,TOP));
        segments.add(new Segment(540,TOP,540,650));
        segments.add(new Segment(64,560,327,760));
        segments.add(new Segment(503,560,362,790));
        segments.add(new Segment(1016,560,753,760));
        segments.add(new Segment(577,560,718,790));
        segments.add(new Segment(66,1050,162,1114));
        segments.add(new Segment(1014,1050,918,1114));
        for(int side=0;side<2;side++){
            float cx=CORE_X[side];
            armor.add(new Armor(side,cx-105,CORE_Y-88,34,72,47));
            armor.add(new Armor(side,cx+71,CORE_Y-88,34,72,47));
            armor.add(new Armor(side,cx-42,CORE_Y-145,84,25,63));
            armor.add(new Armor(side,cx-42,CORE_Y+90,84,25,63));
        }
        for(int side=0;side<2;side++){
            float x=side==0?185:895;
            for(int i=0;i<8;i++){
                float px=x+(i%3-1)*65f;
                float py=600+(i/3)*108;
                pegs.add(new Peg(px,py,13+i%2*4));
            }
        }
    }
    private float rnd(float a,float b){return a+rng.nextFloat()*(b-a);}
    private static float clamp(float n,float lo,float hi){return Math.max(lo,Math.min(hi,n));}
    private static float dist2(float x,float y){return x*x+y*y;}
    public float remaining(){return Math.max(0,MAX_DURATION-elapsed);}
    public boolean gateClosed(){return ((int)(elapsed/5f)%2)==0;}
    private void setEvent(String msg){
        event=msg;lastEvent=msg;eventTimer=1.15f;
    }
    private void particle(float x,float y,int color,int count){
        for(int i=0;i<count && sparks.size()<MAX_PARTICLES;i++){
            float a=rnd(0,6.28318f),speed=rnd(60,350);
            sparks.add(new Spark(x,y,(float)Math.cos(a)*speed,
                (float)Math.sin(a)*speed,rnd(.35f,1f),color));
        }
    }
    private void addMarble(int team,float x,float y){
        if(active[team]>=MAX_PER_TEAM)return;
        float r=rnd(BALL_MIN_R,BALL_MAX_R);
        // Wide enough spacing prevents pathological overlap at spawners.
        float dir=team==0?1f:-1f;
        marbles.add(new Marble(++idSequence,team,x,y,dir*rnd(35,90),
            rnd(35,95),r,r*r/175f));
        active[team]++;spawned[team]++;
    }
    private void spawnOne(int side){
        float base=side==0?258:822;
        addMarble(side,base+rnd(-85,85),220+rnd(-20,10));
    }
    private void wave(int side,int count){
        if(active[side]>=MAX_PER_TEAM)return;
        float base=side==0?270:810;
        int limit=Math.min(count,MAX_PER_TEAM-active[side]);
        for(int i=0;i<limit;i++){
            int col=i%10,row=i/10;
            addMarble(side,base+(col-4.5f)*32f,
                365-row*29f);
        }
    }
    private void cap(Marble m){
        float v2=dist2(m.vx,m.vy),limit=MAX_SPEED*MAX_SPEED;
        if(v2>limit){
            float mult=MAX_SPEED/(float)Math.sqrt(v2);
            m.vx*=mult;m.vy*=mult;
        }
    }
    private void circleCollision(Marble m,float x,float y,float radius,float power){
        float dx=m.x-x,dy=m.y-y,r=m.r+radius,d2=dist2(dx,dy);
        if(d2>=r*r)return;
        float d=(float)Math.sqrt(Math.max(.001f,d2));
        float nx=dx/d,ny=dy/d;
        if(d<1){nx=(m.id%2==0?1:-1);ny=0;}
        m.x=x+nx*(r+1);m.y=y+ny*(r+1);
        float dot=m.vx*nx+m.vy*ny;
        if(dot<0){
            m.vx-=1.85f*dot*nx;m.vy-=1.85f*dot*ny;
        }
        m.vx+=nx*power;m.vy+=ny*power;
    }
    private void segmentCollision(Marble m,float x1,float y1,float x2,float y2,
                                  float margin,float kick){
        float dx=x2-x1,dy=y2-y1;
        float length=dx*dx+dy*dy;
        if(length<1)return;
        float ratio=clamp(((m.x-x1)*dx+(m.y-y1)*dy)/length,0f,1f);
        float px=x1+ratio*dx,py=y1+ratio*dy;
        float vx=m.x-px,vy=m.y-py;
        float radius=m.r+margin,dd=dist2(vx,vy);
        if(dd>=radius*radius)return;
        float d=(float)Math.sqrt(Math.max(.01f,dd));
        float nx=vx/d,ny=vy/d;
        if(d<1){nx=-dy/(float)Math.sqrt(length);ny=dx/(float)Math.sqrt(length);}
        m.x+=nx*(radius-d+.8f);m.y+=ny*(radius-d+.8f);
        float toward=m.vx*nx+m.vy*ny;
        if(toward<0){
            m.vx-=(1+RESTITUTION)*toward*nx;
            m.vy-=(1+RESTITUTION)*toward*ny;
        }
        m.vx+=nx*kick;m.vy+=ny*kick;
    }
    private void machinery(Marble m){
        // Two spinning saws + one center pinball saw.
        float[][] saws={{340,858,40},{740,858,40},{540,1045,35}};
        for(float[] saw:saws){
            float dx=m.x-saw[0],dy=m.y-saw[1];
            if(dist2(dx,dy)<(m.r+saw[2]+2)*(m.r+saw[2]+2)){
                circleCollision(m,saw[0],saw[1],saw[2],235f);
                setEvent("SERRA GIRATÓRIA");
            }
        }
        // Two rotating rigid paddles with tangential velocity transfer.
        float[][] prop={{280,770},{800,770},{540,985}};
        for(int i=0;i<prop.length;i++){
            float ang=cranePhase*(i==1?-2.35f:2.35f)+i;
            float vx=(float)Math.cos(ang)*105f,vy=(float)Math.sin(ang)*105f;
            float x=prop[i][0],y=prop[i][1];
            float ds=dist2(m.x-x,m.y-y);
            if(ds<165*165){
                segmentCollision(m,x-vx,y-vy,x+vx,y+vy,13,90);
                // Paddle tangential force rotates the collision response.
                if(ds<145*145){
                    m.vx-=(m.y-y)*.20f;
                    m.vy+=(m.x-x)*.20f;
                }
            }
        }
        // Timed gates release accumulated marbles every five seconds.
        if(gateClosed()){
            segmentCollision(m,80,910,380,910,10,5);
            segmentCollision(m,700,910,1000,910,10,5);
        }
        // Booster strips along the central crossing lanes push marbles at enemy.
        if(m.y>805 && m.y<860 && !m.boosted){
            // Team-specific impulse regardless of x: the funnels may deflect
            // a marble across the divider before it reaches the booster level.
            float target=CORE_X[1-m.side];
            // Physical aim depends on the current coordinate, never a forced
            // direction. A marble already across the divider must NOT be
            // launched away from the target core.
            m.vx=clamp((target-m.x)/.78f,-890,890)+rnd(-65,65);
            m.vy=clamp(m.vy,220,480);
            m.boosted=true;boosts[m.side]++;
            setEvent(m.side==0?"BOOST AZUL →":"← BOOST VERMELHO");
        }
        for(Peg peg:pegs){
            if(Math.abs(m.y-peg.y)<55 && Math.abs(m.x-peg.x)<55)
                circleCollision(m,peg.x,peg.y,peg.r,36);
        }
    }
    private void damageArmor(Marble m,Armor a){
        if(a.hp<=0)return;
        float nearestX=clamp(m.x,a.x,a.x+a.w);
        float nearestY=clamp(m.y,a.y,a.y+a.h);
        float dx=m.x-nearestX,dy=m.y-nearestY;
        if(dist2(dx,dy)>m.r*m.r)return;
        float speed=(float)Math.sqrt(dist2(m.vx,m.vy));
        if(m.side!=a.team){
            a.hp=Math.max(0,a.hp-(2.8f+speed*m.mass*.004f));
            if(a.hp<=0){
                setEvent("ARMADURA QUEBRADA!");
                particle(a.x+a.w*.5f,a.y+a.h*.5f,m.side==0?0xFF7DDCFF:0xFFFF647F,8);
            }
        }
        // Bounce from actual rectangle face.
        float left=Math.abs(m.x-a.x),right=Math.abs(m.x-(a.x+a.w));
        float top=Math.abs(m.y-a.y),bottom=Math.abs(m.y-(a.y+a.h));
        float min=Math.min(Math.min(left,right),Math.min(top,bottom));
        if(min==left){m.x=a.x-m.r-1;m.vx=-Math.abs(m.vx)*.8f;}
        else if(min==right){m.x=a.x+a.w+m.r+1;m.vx=Math.abs(m.vx)*.8f;}
        else if(min==top){m.y=a.y-m.r-1;m.vy=-Math.abs(m.vy)*.75f;}
        else{m.y=a.y+a.h+m.r+1;m.vy=Math.abs(m.vy)*.75f;}
    }
    private void damageCore(Marble m,int target){
        if(m.side==target||hp[target]<=0)return;
        float cx=CORE_X[target],dy=m.y-CORE_Y,dx=m.x-cx;
        float rad=CORE_RADIUS+m.r;
        if(dist2(dx,dy)>rad*rad)return;
        float speed=(float)Math.sqrt(dist2(m.vx,m.vy));
        // Incoming marbles must earn damage physically, not via a random timer.
        float power=clamp((3.5f+speed*.023f)*m.mass*.95f,3,28);
        boolean protectedCore=false;
        for(Armor a:armor)
            if(a.team==target&&a.hp>0){protectedCore=true;break;}
        if(protectedCore)power*=.22f;
        hp[target]=Math.max(0,hp[target]-power);
        flash[target]=.16f;
        hits[m.side]++;
        particle(m.x,m.y,m.side==0?0xFF65CFFF:0xFFFF5B7D,4);
        m.active=false;active[m.side]--;
        if(hp[target]==0){
            setEvent("NÚCLEO DESTRUÍDO!");
            particle(cx,CORE_Y,target==0?0xFFFFD050:0xFFA950F5,65);
        }
    }
    private void simulateMarble(Marble m,float dt){
        m.age+=dt;
        m.vy+=GRAVITY*dt;m.vx*=.999f;
        cap(m);
        m.x+=m.vx*dt;m.y+=m.vy*dt;
        if(m.y>BOTTOM+36||m.age>19){
            // Void: remove immediately so object counts never grow forever.
            m.active=false;active[m.side]--;return;
        }
        for(Segment segment:segments){
            if(m.y>=Math.min(segment.y1,segment.y2)-m.r-20 &&
               m.y<=Math.max(segment.y1,segment.y2)+m.r+20)
                segmentCollision(m,segment.x1,segment.y1,segment.x2,segment.y2,7,0);
        }
        machinery(m);
        for(Armor a:armor)if(a.hp>0 && Math.abs(m.x-a.x)<a.w+38 && Math.abs(m.y-a.y)<a.h+38)
            damageArmor(m,a);
        damageCore(m,1-m.side);
        if(m.active)cap(m);
    }
    private int index(Marble m){
        int x=(int)clamp(m.x/CELL,0,COLS-1);
        int y=(int)clamp(m.y/CELL,0,ROWS-1);
        return y*COLS+x;
    }
    private void collideBalls(){
        for(ArrayList<Marble> cell:cells)cell.clear();
        for(Marble m:marbles)if(m.active)cells[index(m)].add(m);
        for(Marble m:marbles){
            if(!m.active)continue;
            int cx=(int)clamp(m.x/CELL,0,COLS-1);
            int cy=(int)clamp(m.y/CELL,0,ROWS-1);
            for(int y=Math.max(0,cy-1);y<=Math.min(ROWS-1,cy+1);y++)
                for(int x=Math.max(0,cx-1);x<=Math.min(COLS-1,cx+1);x++){
                    for(Marble n:cells[y*COLS+x]){
                        if(n.id<=m.id||!n.active)continue;
                        float dx=m.x-n.x,dy=m.y-n.y,range=m.r+n.r,d2=dist2(dx,dy);
                        if(d2>=range*range)continue;
                        float distance=(float)Math.sqrt(Math.max(.01f,d2));
                        float nx=distance<1?(m.id%2==0?1:-1):dx/distance;
                        float ny=distance<1?0:dy/distance;
                        float penetration=range-distance;
                        float ratio=n.mass/(m.mass+n.mass);
                        m.x+=nx*penetration*ratio;
                        m.y+=ny*penetration*ratio;
                        n.x-=nx*penetration*(1-ratio);
                        n.y-=ny*penetration*(1-ratio);
                        float relative=(m.vx-n.vx)*nx+(m.vy-n.vy)*ny;
                        if(relative<0){
                            float impulse=-(1+RESTITUTION)*relative/(1/m.mass+1/n.mass);
                            m.vx+=impulse*nx/m.mass;m.vy+=impulse*ny/m.mass;
                            n.vx-=impulse*nx/n.mass;n.vy-=impulse*ny/n.mass;
                        }
                        cap(m);cap(n);
                    }
                }
        }
    }
    private void endMatch(){
        if(finished)return;
        finished=true;
        if(hp[0]!=hp[1])winner=hp[0]>hp[1]?0:1;
        else if(hits[0]!=hits[1])winner=hits[0]>hits[1]?0:1;
        else winner=rng.nextBoolean()?0:1;
        setEvent("VITÓRIA DO NÚCLEO");
        if(!burstAtFinish){
            particle(CORE_X[1-winner],CORE_Y,winner==0?0xFF80DCFF:0xFFFF638C,42);
            burstAtFinish=true;
        }
    }
    public void advance(float dt){
        if(finished)return;
        dt=clamp(dt,0,.04f);
        elapsed+=dt;frames++;cranePhase+=dt*2.45f;
        eventTimer=Math.max(0,eventTimer-dt);
        for(int i=0;i<2;i++)flash[i]=Math.max(0,flash[i]-dt);
        for(int side=0;side<2;side++){
            while(elapsed>=nextSpawn[side]){
                if(active[side]<MAX_PER_TEAM)spawnOne(side);
                nextSpawn[side]+=.2f;
            }
        }
        if(elapsed>=nextWave){
            for(int side=0;side<2;side++)wave(side,50);
            nextWave+=17;
            setEvent("ONDA DE 50 BOLINHAS!");
        }
        for(Marble m:marbles)if(m.active)simulateMarble(m,dt);
        collideBalls();
        // Garbage collection at most every 4 simulation frames.
        if(frames%4==0)marbles.removeIf(m->!m.active);
        for(Spark p:sparks){p.x+=p.vx*dt;p.y+=p.vy*dt;p.vy+=195*dt;p.life-=dt;}
        sparks.removeIf(p->p.life<=0);
        if(elapsed>=MAX_DURATION ||
            (elapsed>=MIN_DURATION && (hp[0]<=0||hp[1]<=0)))endMatch();
    }
}
