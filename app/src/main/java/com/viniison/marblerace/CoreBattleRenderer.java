package com.viniison.marblerace;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.graphics.Typeface;
import java.util.List;
import java.util.Locale;

/** 9:16-friendly arena; every hazard displayed here also exists in the physics. */
public final class CoreBattleRenderer {
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final int BG=0xFF030406,YELLOW=0xFFFFD921,PURPLE=0xFF9C39FA;
    private static final int[] TEAM={YELLOW,PURPLE};
    private final Path polygon=new Path();
    private void disc(Canvas c,float x,float y,float r,int color){
        p.reset();p.setAntiAlias(true);p.setColor(color);c.drawCircle(x,y,r,p);
    }
    private void stroke(Canvas c,float x,float y,float xx,float yy,float width,int color){
        p.reset();p.setAntiAlias(true);p.setColor(color);p.setStrokeWidth(width);
        p.setStrokeCap(Paint.Cap.ROUND);c.drawLine(x,y,xx,yy,p);
    }
    private void block(Canvas c,float x,float y,float w,float h,float radius,int color){
        p.reset();p.setAntiAlias(true);p.setColor(color);
        c.drawRoundRect(x,y,x+w,y+h,radius,radius,p);
    }
    private void title(Canvas c,String s,float x,float y,float size,int color,boolean center){
        p.reset();p.setAntiAlias(true);p.setColor(color);
        p.setTextSize(size);p.setTypeface(Typeface.create("sans-serif-condensed",Typeface.BOLD));
        if(center)p.setTextAlign(Paint.Align.CENTER);
        c.drawText(s,x,y,p);
    }
    private static String clip(String s,int len){
        if(s==null||s.trim().isEmpty())return "TIME";
        return s.length()>len?s.substring(0,len-1)+"…":s;
    }
    private static int opacity(int rgb,float opacity){
        return (rgb&0x00FFFFFF)|((int)Math.max(0,Math.min(255,opacity))<<24);
    }
    private void face(Canvas c,Bitmap picture,float x,float y,float radius,int ring){
        disc(c,x,y,radius+5,ring);
        if(picture==null||picture.isRecycled()||picture.getWidth()<1||picture.getHeight()<1){
            disc(c,x,y,radius,0xFF182432);return;
        }
        BitmapShader shader=new BitmapShader(picture,Shader.TileMode.CLAMP,Shader.TileMode.CLAMP);
        Matrix m=new Matrix();
        float scale=Math.max(radius*2/picture.getWidth(),radius*2/picture.getHeight());
        m.setScale(scale,scale);
        m.postTranslate(x-picture.getWidth()*scale*.5f,y-picture.getHeight()*scale*.5f);
        shader.setLocalMatrix(m);
        p.reset();p.setAntiAlias(true);p.setShader(shader);
        c.drawCircle(x,y,radius,p);p.setShader(null);
    }
    private void saw(Canvas c,float x,float y,float radius,float angle){
        polygon.reset();
        for(int i=0;i<24;i++){
            double a=angle+i*Math.PI/12;
            float r=i%2==0?radius+10:radius-2;
            float px=x+(float)Math.cos(a)*r,py=y+(float)Math.sin(a)*r;
            if(i==0)polygon.moveTo(px,py);else polygon.lineTo(px,py);
        }
        polygon.close();
        p.reset();p.setAntiAlias(true);p.setColor(0xFFB1C8DE);c.drawPath(polygon,p);
        disc(c,x,y,22,0xFF34435A);
        disc(c,x,y,8,0xFFDAEDF8);
    }
    private void crane(Canvas c,CoreBattleEngine g,int side){
        float center=CoreBattleEngine.CORE_X[side], x=g.craneX(side);
        float y=CoreBattleEngine.TOP+53f;
        // The trolley is tied to the actual wave-release coordinate in physics.
        stroke(c,center-190,y-26,center+190,y-26,14,0xFF272C36);
        stroke(c,center-190,y-27,center+190,y-27,3,0xFF7D8795);
        stroke(c,x,y-15,x,y+96,5,0xFF707882);
        block(c,x-28,y-32,56,30,5,0xFF313743);
        block(c,x-19,y-27,38,13,4,TEAM[side]);
        stroke(c,x-25,y+89,x,y+114,8,0xFFA7ABB3);
        stroke(c,x+25,y+89,x,y+114,8,0xFFA7ABB3);
        disc(c,x,y+89,9,0xFFE3E6EB);
        title(c,"CRANE "+(side+1),center,y-57,23,0xFFAAB4C2,true);
    }
    private void curvedPipe(Canvas c,boolean right,int color){
        Path rail=new Path();
        if(!right){
            rail.moveTo(68,486);
            rail.cubicTo(18,720,54,992,CoreBattleEngine.CORE_X[0],CoreBattleEngine.CORE_Y);
        }else{
            rail.moveTo(1012,486);
            rail.cubicTo(1062,720,1026,992,CoreBattleEngine.CORE_X[1],CoreBattleEngine.CORE_Y);
        }
        p.reset();p.setAntiAlias(true);p.setStrokeCap(Paint.Cap.ROUND);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(33);
        p.setColor(0xFF08090D);c.drawPath(rail,p);
        p.setStrokeWidth(23);p.setColor(0xFF4A4B50);c.drawPath(rail,p);
        p.setStrokeWidth(6);p.setColor(0xFF898E93);c.drawPath(rail,p);
        p.setStyle(Paint.Style.FILL);
        disc(c,right?1012:68,486,13,color);
    }
    private void machineryDecor(Canvas c,CoreBattleEngine game){
        // Saw housings and green mechanical accent rails occupy the side lanes;
        // their inner movable saws have corresponding collision bodies.
        for(int side=0;side<2;side++){
            float x=side==0?90:990;
            saw(c,x,520,94,game.cranePhase*(side==0?1:-1));
            p.reset();p.setAntiAlias(true);p.setStrokeWidth(11);
            p.setStyle(Paint.Style.STROKE);p.setColor(0xFF88F41B);
            Path green=new Path();
            if(side==0){
                green.moveTo(180,345);green.cubicTo(185,425,129,476,96,493);
            }else{
                green.moveTo(900,345);green.cubicTo(895,425,951,476,984,493);
            }
            c.drawPath(green,p);
            p.setStyle(Paint.Style.FILL);
        }
    }
    private void selectorAndScythe(Canvas c,CoreBattleEngine g){
        CoreEquipmentSystem machine=g.equipment;
        block(c,417,220,246,245,15,0xFF0F0A0A);
        block(c,433,241,214,211,10,0xFF19120F);
        for(int i=0;i<3;i++)
            disc(c,473+i*65,289+i%2*43,16,0xFF533019);
        block(c,431,426,218,23,1,0xFFCB0C6C);
        for(CoreEquipmentSystem.SelectorBall b:machine.selector){
            int col=b.side==0?YELLOW:PURPLE;
            disc(c,b.x,b.y,14,col);
            disc(c,b.x-4,b.y-5,4,0xDFFFFFFF);
        }
        // Two pads report the team selected by an actual top marble contact.
        for(int i=0;i<2;i++)disc(c,478+i*124,221,9,i==0?YELLOW:PURPLE);
        stroke(c,540,190,540,307,8,0xFF8C8C8F);
        disc(c,540,197,24,0xFF2E3035);
        disc(c,540,197,15,0xFFAAB2B8);
        // The scythe is driven by the equipment state machine, not decoration.
        float sx=machine.scytheX,sy=machine.scytheY;
        stroke(c,540,197,sx,sy,5,0xFFB6BAC0);
        disc(c,sx,sy,15,0xFFECEEF0);
        Path blade=new Path();
        blade.moveTo(sx-8,sy+6);
        blade.quadTo(sx+48,sy+63,sx+130,sy-4);
        blade.quadTo(sx+66,sy+36,sx+3,sy+26);
        blade.close();
        p.reset();p.setAntiAlias(true);p.setColor(0xFFBFC5CC);c.drawPath(blade,p);
        if(machine.selectedType>=0 && machine.phase>=CoreEquipmentSystem.LIFTING
            &&machine.phase<=CoreEquipmentSystem.DELIVERING){
            int col=CoreEquipmentSystem.COLORS[machine.selectedType];
            disc(c,sx+10,sy+25,17,col);
            disc(c,sx+6,sy+20,6,0xFFFAF7FF);
        }
    }
    private void weaponLegend(Canvas c,CoreBattleEngine game){
        float x=427,y=900,w=226,h=337;
        block(c,x-5,y-12,w+10,h+26,8,0xDD08090D);
        title(c,"EQUIPAMENTOS",x+w*.5f,y+22,25,0xFFE8ECEF,true);
        for(int i=0;i<CoreEquipmentSystem.NAMES.length;i++){
            float cy=y+64+i*45;
            boolean selected=game.equipment.lastType==i && game.equipment.phase!=CoreEquipmentSystem.IDLE;
            if(selected)block(c,x+4,cy-26,w-8,39,4,0xFF29333D);
            disc(c,x+20,cy-10,12,CoreEquipmentSystem.COLORS[i]);
            title(c,CoreEquipmentSystem.NAMES[i],x+42,cy,21,0xFFE6E8ED,false);
        }
    }
    private void movingConveyor(Canvas c,CoreBattleEngine game){
        CoreEquipmentSystem eq=game.equipment;
        float y=CoreEquipmentSystem.BELT_Y;
        block(c,28,y-16,1024,80,3,0xFF090A0C);
        block(c,32,y+13,1016,17,3,0xFF585960);
        for(int i=0;i<24;i++){
            float x=44+i*46;
            disc(c,x,y+43,18,0xFF2F3439);
            disc(c,x,y+43,8,0xFF777C81);
        }
        for(CoreEquipmentSystem.Item item:eq.conveyor){
            float x=item.x;
            disc(c,x,y-3,17,0xFF080A0F);
            disc(c,x,y-3,13,CoreEquipmentSystem.COLORS[item.type]);
            disc(c,x-4,y-8,5,0xFFB9B5C3);
        }
        // The TRASH boxes are explicitly scenery. They do not delete pickups,
        // projectiles or marbles: recycling happens only outside the arena.
        for(int i=0;i<5;i++){
            float x=68+i*235;
            block(c,x,y-47,82,35,3,0xFF777C81);
            title(c,"TRASH",x+41,y-25,20,0xFF070707,true);
        }
    }
    private void weaponEffects(Canvas c,CoreBattleEngine game){
        for(int side=0;side<2;side++){
            if(game.shieldSeconds[side]>0){
                p.reset();p.setAntiAlias(true);p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(13);p.setColor(0xFF3BF1D2);
                c.drawCircle(CoreBattleEngine.CORE_X[side],
                    CoreBattleEngine.CORE_Y,CoreBattleEngine.CORE_RADIUS+56,p);
                p.setStyle(Paint.Style.FILL);
            }
        }
        if(game.beamSeconds>0 && game.beamSide>=0){
            float start=CoreBattleEngine.CORE_X[game.beamSide];
            float finish=CoreBattleEngine.CORE_X[1-game.beamSide];
            int col=game.beamType==CoreEquipmentSystem.BURNING_LASER?
                0xFFEA3A42:0xFF315BFA;
            stroke(c,start,CoreBattleEngine.CORE_Y-50,
                finish,CoreBattleEngine.CORE_Y-50,17,col);
            stroke(c,start,CoreBattleEngine.CORE_Y-50,
                finish,CoreBattleEngine.CORE_Y-50,5,0xFFEAF8FF);
        }
        for(CoreBattleEngine.WeaponProjectile shot:game.weaponProjectiles){
            int col=CoreEquipmentSystem.COLORS[shot.type];
            float radius=shot.type==CoreEquipmentSystem.BOMB?24:
                shot.type==CoreEquipmentSystem.SAW?27:13;
            if(shot.type==CoreEquipmentSystem.SAW)
                saw(c,shot.x,shot.y,radius,shot.age*11);
            else{
                stroke(c,shot.x-shot.vx*.07f,shot.y-shot.vy*.07f,
                    shot.x,shot.y,11,opacity(col,115));
                disc(c,shot.x,shot.y,radius,col);
            }
        }
    }
    private void arena(Canvas c,CoreBattleEngine g,List<ModeEngine.Orb> entrants){
        block(c,0,CoreBattleEngine.TOP-115,1080,
            CoreBattleEngine.BOTTOM-CoreBattleEngine.TOP+240,0,0xFF070708);
        for(int i=0;i<14;i++){
            float yy=CoreBattleEngine.TOP+i*95;
            stroke(c,0,yy,1080,yy,1,0xFF141518);
        }
        machineryDecor(c,g);
        crane(c,g,0);crane(c,g,1);
        // A central divider gives marbles separate launch lanes.
        for(CoreBattleEngine.Segment wall:g.segments){
            stroke(c,wall.x1,wall.y1,wall.x2,wall.y2,18,0xFF111217);
            stroke(c,wall.x1,wall.y1,wall.x2,wall.y2,6,0xFF7B7F84);
        }
        // Event gates alternate every five seconds and physically block marbles.
        boolean closed=g.gateClosed();
        for(int side=0;side<2;side++){
            float x=side==0?80:700;
            stroke(c,x,910,x+300,910,closed?24:5,
                closed?0xFFE86235:0xFF3DBA75);
            title(c,closed?"PORTÃO FECHADO":"PORTÃO ABERTO",
                x+150,875,26,closed?0xFFFFAF81:0xFF94DAAE,true);
        }
        // Boosters point toward the other team's core.
        // Full-width booster field. It physically redirects each team's marble,
        // including marbles swept into the opponent lane by the funnels.
        block(c,61,795,958,62,5,0xFF202522);
        for(int side=0;side<2;side++){
            float start=side==0?80:580;
            for(int i=0;i<5;i++)
                title(c,side==0?"➜":"←",start+i*88,837,43,TEAM[side],true);
        }
        for(CoreBattleEngine.Peg peg:g.pegs){
            disc(c,peg.x+3,peg.y+3,peg.r+6,0xFF111112);
            disc(c,peg.x,peg.y,peg.r,0xFF9BA4AA);
            disc(c,peg.x-4,peg.y-4,5,0xFFFFFFFF);
        }
        // Rotating physical saw wheels
        saw(c,340,858,40,g.cranePhase);
        saw(c,740,858,40,-g.cranePhase);
        // The static center bumper is already drawn by the peg loop.
        // Only side paddles rotate, exactly as in CoreBattleEngine.
        float[][] prop={{280,770},{800,770}};
        for(int i=0;i<prop.length;i++){
            float ang=(i==1?-1f:1f)*g.cranePhase*2.35f;
            float vx=(float)Math.cos(ang)*105f,vy=(float)Math.sin(ang)*105f;
            stroke(c,prop[i][0]-vx,prop[i][1]-vy,prop[i][0]+vx,prop[i][1]+vy,25,0xFF1D2533);
            stroke(c,prop[i][0]-vx,prop[i][1]-vy,prop[i][0]+vx,prop[i][1]+vy,13,0xFFB1C6D3);
            disc(c,prop[i][0],prop[i][1],21,0xFF28384E);
            disc(c,prop[i][0],prop[i][1],8,0xFFB8DFF4);
        }
        // Two actual cores: armor protects them until marbles break the blocks.
        for(int side=0;side<2;side++){
            float x=CoreBattleEngine.CORE_X[side],y=CoreBattleEngine.CORE_Y;
            int color=TEAM[side];
            float radius=CoreBattleEngine.CORE_RADIUS;
            // Two giant machine targets instead of tiny HUD-like icons.
            float large=radius+22; // bright circle is the actual 145-unit hit target
            disc(c,x+7,y+9,large+9,0xFF010102);
            disc(c,x,y,large,side==0?0xFF75650B:0xFF481C76);
            disc(c,x,y,large-15,side==0?0xFFBDA111:0xFF7026BA);
            for(int ring=0;ring<10;ring++){
                p.reset();p.setAntiAlias(true);
                p.setColor(opacity(side==0?0xFFEDD344:0xFFAF69E9,92));
                p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2.5f);
                c.drawCircle(x,y,large-18-ring*12,p);
            }
            disc(c,x,y,radius+5,0xFF050608);
            disc(c,x,y,radius-2,side==0?0xFFD6B619:0xFF8430D2);
            if(g.flash[side]>0)
                disc(c,x,y,radius-2,opacity(Color.WHITE,
                    255*g.flash[side]/.16f));
            if(g.absorbedFlash[side]>0){
                p.reset();p.setAntiAlias(true);
                p.setColor(opacity(Color.WHITE,g.absorbedFlash[side]/.18f*230f));
                p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(12);
                c.drawCircle(g.lastImpactX[side],g.lastImpactY[side],
                    24+(1-g.absorbedFlash[side]/.18f)*44,p);
            }
            if(g.ending && g.destroyedAt[side]>=0){
                float progress=1-g.endingTime/2.4f;
                p.reset();p.setAntiAlias(true);
                p.setColor(opacity(color,(1-progress)*250));
                p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(22*(1-progress)+3);
                c.drawCircle(x,y,radius+progress*260,p);
                for(int debris=0;debris<12;debris++){
                    float angle=(float)(debris*Math.PI/6);
                    float range=radius+progress*(110+debris%4*40);
                    disc(c,x+(float)Math.cos(angle)*range,
                        y+(float)Math.sin(angle)*range,
                        Math.max(3,19*(1-progress)),color);
                }
            }
            disc(c,x,y,25,0xFF03FE26);
            disc(c,x-6,y-7,7,0xFFB9FFBA);
            float wear=1-g.hp[side]/CoreBattleEngine.MAX_HP;
            if(wear>.18f){
                for(int k=0;k<(int)(wear*9)+1;k++){
                    float theta=k*2.45f;
                    float x1=x+(float)Math.cos(theta)*15,y1=y+(float)Math.sin(theta)*15;
                    stroke(c,x1,y1,x1+(float)Math.sin(theta)*35,
                        y1+35,2.7f,0xFF111B2A);
                }
            }
            face(c,g.entrantAtCore[side]<entrants.size()?entrants.get(g.entrantAtCore[side]).racer.avatar:null,
                x,y+58,29,0xFF030304);
        }
        curvedPipe(c,false,YELLOW);
        curvedPipe(c,true,PURPLE);
        for(CoreBattleEngine.Armor a:g.armor){
            if(a.hp<=0)continue;
            int team=TEAM[a.team];
            block(c,a.x,a.y,a.w,a.h,3,0xFF08090B);
            float ratio=a.hp/a.maxHp;
            block(c,a.x+3,a.y+3,(a.w-6),Math.max(2,(a.h-6)*ratio),3,opacity(team,190));
            block(c,a.x+a.w*.35f,a.y+a.h*.35f,a.w*.3f,a.h*.3f,2,0xFFE4E6E8);
        }
        // Numerous real moving marbles, color-coded by team.
        for(CoreBattleEngine.Marble m:g.marbles){
            if(!m.active)continue;
            int color=TEAM[m.side];
            stroke(c,m.x-m.vx*.033f,m.y-m.vy*.033f,m.x,m.y,
                Math.max(3,m.r*.9f),opacity(color,56));
            stroke(c,m.x-m.vx*.015f,m.y-m.vy*.015f,m.x,m.y,
                Math.max(3,m.r*.72f),opacity(color,125));
            disc(c,m.x+1.5f,m.y+2.5f,m.r+2,0xAA02040A);
            disc(c,m.x,m.y,m.r,color);
            disc(c,m.x-m.r*.32f,m.y-m.r*.32f,Math.max(2,m.r*.25f),0xEFFFFFFF);
        }
        // Visual particles are bounded to 96 even during huge waves.
        for(CoreBattleEngine.Spark spark:g.sparks){
            disc(c,spark.x,spark.y,2+spark.life*6,
                opacity(spark.color,255*Math.min(1,spark.life)));
        }
        weaponLegend(c,g);
        movingConveyor(c,g);
        weaponEffects(c,g);
        selectorAndScythe(c,g);
    }
    private void hud(Canvas c,float h,CoreBattleEngine game,List<ModeEngine.Orb> entrants){
        // Fixed safe header: title, names, health and timer occupy separate rows.
        // The previous labels were drawn over each other on a 9:16 phone.
        block(c,0,0,1080,205,0,0xFF050609);
        title(c,"MARBLE LAB   •   CORE DESTRUCTION",540,39,33,Color.WHITE,true);
        for(int side=0;side<2;side++){
            int color=TEAM[side],idx=game.entrantAtCore[side];
            float cx=side==0?260:820;
            float barX=side==0?24:593;
            String name=idx<entrants.size()?entrants.get(idx).racer.name:"TIME "+(side+1);
            title(c,clip(name,11).toUpperCase(Locale.ROOT),cx,81,32,color,true);
            block(c,barX,98,463,24,4,0xFF25272C);
            block(c,barX,98,Math.max(0,463*game.hp[side]/CoreBattleEngine.MAX_HP),
                24,4,color);
            title(c,Math.round(game.hp[side])+" HP    •    "+game.active[side]+" BOLINHAS",
                cx,156,24,0xFFE6E6E6,true);
        }
        int secs=(int)Math.ceil(game.remaining());
        title(c,String.format(Locale.US,"%02d:%02d",secs/60,secs%60),
            540,188,44,0xFFFFF1B4,true);
        float footer=h-110f;
        block(c,0,footer,1080,110,0,0xFA07080A);
        if(game.eventTimer>0f)
            title(c,game.event,540,footer+38,34,0xFFEDEDED,true);
        else title(c,"FÍSICA 2D    •    ARMADURAS    •    GUINDASTES",
            540,footer+38,26,0xFFABB2BA,true);
        title(c,"PLACAR   "+game.winTotals[game.entrantAtCore[0]]+
            " : "+game.winTotals[game.entrantAtCore[1]]+
            "     •     ONDAS A CADA 14s",540,footer+78,24,0xFFD0D0D0,true);
        if(game.finished){
            block(c,85,h*.42f,910,232,16,0xF50D0D11);
            title(c,"NÚCLEO VENCEDOR",540,h*.42f+67,47,
                TEAM[Math.max(0,game.physicalWinner)],true);
            String who=game.winner>=0&&game.winner<entrants.size()?
                entrants.get(game.winner).racer.name:"EQUIPE";
            title(c,clip(who,16).toUpperCase(Locale.ROOT),540,h*.42f+151,
                66,Color.WHITE,true);
            title(c,"BATALHA ENCERRADA",540,h*.42f+202,
                26,0xFFCCCCCC,true);
        }
    }
    public void render(Canvas original,CoreBattleEngine engine,List<ModeEngine.Orb> entrants){
        if(original.getWidth()<1||original.getHeight()<1)return;
        original.save();
        float global=original.getWidth()/1080f;
        original.scale(global,global);
        float h=original.getHeight()/global;
        Canvas c=original;
        c.drawColor(BG);
        float available=Math.max(100,h-335f);
        float worldHeight=CoreBattleEngine.BOTTOM-CoreBattleEngine.TOP+85f;
        float sc=Math.min(1f,available/worldHeight);
        float offsetY=214f+(available-worldHeight*sc)*.5f;
        c.save();
        c.translate(540,offsetY);
        c.scale(sc,sc);
        c.translate(-540,-CoreBattleEngine.TOP);
        arena(c,engine,entrants);
        c.restore();
        hud(c,h,engine,entrants);
        original.restore();
    }
}
