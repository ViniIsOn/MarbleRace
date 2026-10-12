package com.viniison.marblerace;

import java.util.ArrayList;
import java.util.Random;

/**
 * Deterministic 2D equipment selector: physical bouncing selector marbles choose
 * the recipient of an item picked from a MOVING conveyor by the central scythe.
 * The picker never chooses a winner or changes HP; CoreBattleEngine handles each
 * delivered item as a real battlefield action.
 */
public final class CoreEquipmentSystem {
    public static final int PENETRATION_LASER=0, SAW=1, SHIELD=2,
        BURNING_LASER=3, BOMB=4, SHOT=5;
    public static final String[] NAMES={"PenetrationLaser","Saw","Shield","BurningLaser","Bomb","Shot"};
    public static final int[] COLORS={0xFF345CF9,0xFF32DB54,0xFF22CECC,
        0xFFEE3D43,0xFFCF36E9,0xFFFF972C};
    public static final int IDLE=0, DESCENDING=1, LIFTING=2, DELIVERING=3, COOLDOWN=4;
    public static final int ITEM_COUNT=9;
    public static final float BELT_Y=1465f;
    public static final class Item {
        public int type;
        public float x;
        Item(int type,float x){this.type=type;this.x=x;}
    }
    public static final class SelectorBall {
        public final int side;
        public float x,y,vx,vy;
        SelectorBall(int side,float x,float y,float vx,float vy){
            this.side=side;this.x=x;this.y=y;this.vx=vx;this.vy=vy;
        }
    }
    public static final class Delivery {
        public final int side,type;
        Delivery(int side,int type){this.side=side;this.type=type;}
    }
    public final ArrayList<Item> conveyor=new ArrayList<>();
    public final SelectorBall[] selector=new SelectorBall[2];
    public final int[] deliveriesBySide={0,0};
    public final int[] deliveriesByType=new int[NAMES.length];
    public float time,scytheY,scytheX,beltOffset,phaseTime,nextPick;
    public int phase,selectedSide,selectedType,lastSide=-1,lastType=-1,deliveries=0;
    private final long seed;
    private final Random random;
    private float idleFor;
    private int nextItemType;

    public CoreEquipmentSystem(long seed){
        this.seed=seed;
        random=new Random(seed^0x5E1EC7A5L);
        reset();
    }
    public void reset(){
        random.setSeed(seed^0x5E1EC7A5L);
        time=0;phaseTime=0;idleFor=0;beltOffset=0;
        nextPick=3.5f;
        phase=IDLE;selectedSide=-1;selectedType=-1;
        lastSide=-1;lastType=-1;deliveries=0;nextItemType=0;
        deliveriesBySide[0]=deliveriesBySide[1]=0;
        for(int i=0;i<deliveriesByType.length;i++)deliveriesByType[i]=0;
        conveyor.clear();
        for(int i=0;i<ITEM_COUNT;i++)
            conveyor.add(new Item(i%NAMES.length,55f+i*111f));
        selector[0]=new SelectorBall(0,473f,257f,68,32);
        selector[1]=new SelectorBall(1,607f,259f,-68,32);
        scytheX=540;scytheY=360;
    }
    private void runSelector(SelectorBall ball,float dt){
        // Small, genuinely bouncing chooser marbles inside the central chamber.
        // The top-right and top-left balls share exactly the same rules.
        ball.vy+=260*dt;
        ball.x+=ball.vx*dt;
        ball.y+=ball.vy*dt;
        if(ball.x<457){ball.x=457;ball.vx=Math.abs(ball.vx);}
        if(ball.x>623){ball.x=623;ball.vx=-Math.abs(ball.vx);}
        if(ball.y<250){ball.y=250;ball.vy=Math.abs(ball.vy);}
        if(ball.y>407){
            ball.y=407;ball.vy=-Math.abs(ball.vy)*.83f;
            ball.vx+=(random.nextFloat()-.5f)*47;
            if(ball.vx>175)ball.vx=175;
            if(ball.vx<-175)ball.vx=-175;
        }
    }
    private void trigger(int side){
        selectedSide=side;
        selectedType=-1;
        phase=DESCENDING;phaseTime=0;idleFor=0;
    }
    private void pickup(){
        Item nearest=null;
        float dist=Float.MAX_VALUE;
        for(Item item:conveyor){
            float d=Math.abs(item.x-540);
            if(d<dist){dist=d;nearest=item;}
        }
        if(nearest==null)selectedType=SHOT;
        else{
            selectedType=nearest.type;
            // Picking removes that actual belt object, and a new colored
            // capsule appears at the start of the conveyor afterwards.
            conveyor.remove(nearest);
            conveyor.add(new Item(nextItemType++%NAMES.length,44f));
        }
    }
    public Delivery update(float dt){
        dt=Math.max(0,Math.min(.045f,dt));
        time+=dt;phaseTime+=dt;
        // Capsules travel along an ACTUAL item-selection conveyor.
        for(Item item:conveyor){
            item.x+=68f*dt;
            if(item.x>1065)item.x=42;
        }
        beltOffset=(beltOffset+125*dt)%48f;
        for(SelectorBall b:selector)runSelector(b,dt);
        Delivery delivered=null;
        if(phase==IDLE){
            scytheX=540;scytheY=360;idleFor+=dt;
            if(time>=nextPick){
                // Whichever physical selector marble crosses the marked pickup
                // contact first determines the team receiving this equipment.
                int touching=-1;float best=Float.MAX_VALUE;
                for(SelectorBall b:selector){
                    float distance=Math.abs(b.x-540)+Math.abs(b.y-390)*.6f;
                    if(distance<best){touching=b.side;best=distance;}
                }
                if(best<40 || idleFor>5.8f)trigger(touching);
            }
        }else if(phase==DESCENDING){
            scytheY=360+Math.min(1,phaseTime/.78f)*1030;
            if(phaseTime>=.78f){pickup();phase=LIFTING;phaseTime=0;}
        }else if(phase==LIFTING){
            scytheY=1390-Math.min(1,phaseTime/.72f)*1010;
            if(phaseTime>=.72f){phase=DELIVERING;phaseTime=0;}
        }else if(phase==DELIVERING){
            float progress=Math.min(1,phaseTime/.58f);
            scytheX=540+(selectedSide==0?-260:260)*progress;
            scytheY=380+progress*470;
            if(phaseTime>=.58f){
                lastSide=selectedSide;lastType=selectedType;
                deliveriesBySide[selectedSide]++;
                deliveriesByType[selectedType]++;
                deliveries++;
                delivered=new Delivery(selectedSide,selectedType);
                phase=COOLDOWN;phaseTime=0;
                nextPick=time+3f;
            }
        }else if(phase==COOLDOWN){
            if(phaseTime>=.9f){phase=IDLE;phaseTime=0;idleFor=0;}
        }
        return delivered;
    }
}
