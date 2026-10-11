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
    private static final int BG=0xFF050A13,BLUE=0xFF46C5FF,RED=0xFFFF5673;
    private static final int[] TEAM={BLUE,RED};
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
    private void arena(Canvas c,CoreBattleEngine g,List<ModeEngine.Orb> entrants){
        block(c,0,CoreBattleEngine.TOP-8,1080,1285,0,0xFF0B1321);
        // A central divider gives marbles separate launch lanes.
        for(CoreBattleEngine.Segment wall:g.segments){
            stroke(c,wall.x1,wall.y1,wall.x2,wall.y2,20,0xFF232F3F);
            stroke(c,wall.x1,wall.y1,wall.x2,wall.y2,8,0xFF9BAEBE);
        }
        // Event gates alternate every five seconds and physically block marbles.
        boolean closed=g.gateClosed();
        for(int side=0;side<2;side++){
            float x=side==0?80:700;
            stroke(c,x,910,x+300,910,closed?24:5,
                closed?0xFFFF7D62:0xFF4EBB8E);
            title(c,closed?"PORTÃO FECHADO":"PORTÃO ABERTO",
                x+150,875,26,closed?0xFFFF9B8C:0xFF6FEFC4,true);
        }
        // Boosters point toward the other team's core.
        for(int side=0;side<2;side++){
            float x=side==0?205:585;
            block(c,x,795,290,59,12,0xFF15535A);
            for(int i=0;i<4;i++){
                float cx=x+38+i*65;
                title(c,side==0?"➜":"➜",cx,837,42,TEAM[side],true);
            }
        }
        for(CoreBattleEngine.Peg peg:g.pegs){
            disc(c,peg.x+3,peg.y+3,peg.r+6,0xFF0E1926);
            disc(c,peg.x,peg.y,peg.r,0xFF62E8AF);
            disc(c,peg.x-4,peg.y-4,5,0xFFE3FFF2);
        }
        // Rotating physical saw wheels
        saw(c,340,858,40,g.cranePhase);
        saw(c,740,858,40,-g.cranePhase);
        saw(c,540,1045,35,g.cranePhase);
        float[][] prop={{280,770},{800,770},{540,985}};
        for(int i=0;i<prop.length;i++){
            float ang=g.cranePhase*(i==1?-2.35f:2.35f)+i;
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
            disc(c,x+5,y+8,radius+18,0xAA000000);
            disc(c,x,y,radius+8,0xFF070F19);
            disc(c,x,y,radius,color);
            for(int ring=0;ring<5;ring++){
                p.reset();p.setAntiAlias(true);p.setColor(opacity(0xFF0B1421,80));
                p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(3);
                c.drawCircle(x,y,radius-11-ring*12,p);
            }
            if(g.flash[side]>0){
                disc(c,x,y,radius,opacity(Color.WHITE,255*g.flash[side]/.16f));
            }
            float wear=1-g.hp[side]/CoreBattleEngine.MAX_HP;
            if(wear>.18f){
                for(int k=0;k<(int)(wear*9)+1;k++){
                    float theta=k*2.45f;
                    float x1=x+(float)Math.cos(theta)*15,y1=y+(float)Math.sin(theta)*15;
                    stroke(c,x1,y1,x1+(float)Math.sin(theta)*35,
                        y1+35,2.7f,0xFF111B2A);
                }
            }
            face(c,side<entrants.size()?entrants.get(side).racer.avatar:null,
                x,y-6,24,0xFF060B12);
        }
        for(CoreBattleEngine.Armor a:g.armor){
            if(a.hp<=0)continue;
            int team=TEAM[a.team];
            block(c,a.x,a.y,a.w,a.h,4,0xFF1B2636);
            float ratio=a.hp/a.maxHp;
            block(c,a.x+3,a.y+3,(a.w-6),Math.max(2,(a.h-6)*ratio),3,opacity(team,190));
            block(c,a.x+a.w*.35f,a.y+a.h*.35f,a.w*.3f,a.h*.3f,2,0xFFCFDCE6);
        }
        // Numerous real moving marbles, color-coded by team.
        for(CoreBattleEngine.Marble m:g.marbles){
            if(!m.active)continue;
            int color=TEAM[m.side];
            disc(c,m.x+1.5f,m.y+2.5f,m.r+2,0xAA02040A);
            disc(c,m.x,m.y,m.r,color);
            disc(c,m.x-m.r*.32f,m.y-m.r*.32f,Math.max(2,m.r*.25f),0xEFFFFFFF);
        }
        // Visual particles are bounded to 96 even during huge waves.
        for(CoreBattleEngine.Spark spark:g.sparks){
            disc(c,spark.x,spark.y,2+spark.life*6,
                opacity(spark.color,255*Math.min(1,spark.life)));
        }
        // Bottom void consumes marbles immediately in physics.
        block(c,0,CoreBattleEngine.BOTTOM,1080,60,0,0xFF030406);
        for(int i=0;i<12;i++){
            float x=40+i*92;
            stroke(c,x,CoreBattleEngine.BOTTOM+12,x+39,CoreBattleEngine.BOTTOM+35,7,0xFFFF7F42);
        }
        title(c,"ZONA DE DESCARTE",540,CoreBattleEngine.BOTTOM+57,30,0xFFFFC97E,true);
    }
    private void hud(Canvas c,float h,CoreBattleEngine game,List<ModeEngine.Orb> entrants){
        block(c,12,10,1056,207,22,0xEE111A28);
        title(c,"CORE DESTRUCTION",38,62,48,Color.WHITE,false);
        int secs=(int)Math.ceil(game.remaining());
        title(c,String.format(Locale.US,"%02d:%02d",secs/60,secs%60),976,69,50,0xFFFFE18C,true);
        for(int side=0;side<2;side++){
            int color=TEAM[side];float x=side==0?36:560;
            String name=side<entrants.size()?entrants.get(side).racer.name:"TIME "+(side+1);
            title(c,clip(name,16).toUpperCase(Locale.ROOT),x,119,34,color,false);
            block(c,x,133,474,28,9,0xFF313F50);
            block(c,x,133,Math.max(1,474*game.hp[side]/CoreBattleEngine.MAX_HP),
                28,9,color);
            title(c,"HP "+Math.round(game.hp[side])+" / "+Math.round(CoreBattleEngine.MAX_HP)+
                "   ● "+game.active[side],x,192,26,Color.WHITE,false);
        }
        float footer=h-206;
        block(c,13,footer,1054,192,22,0xEE111A28);
        title(c,"OBJETOS EM CENA: "+game.marbles.size()+" / 600",40,footer+52,
            31,0xFFD6E9F5,false);
        title(c,"PLACAR  "+game.winTotals[0]+"  ×  "+game.winTotals[1],1040,
            footer+52,32,0xFFF5DB8C,true);
        title(c,game.eventTimer>0?game.event:"SERRAS • HÉLICES • BOOSTERS • PORTÕES",
            540,footer+112,36,Color.WHITE,true);
        title(c,"ONDA A CADA 17s • GERADOR 0,2s",540,footer+159,
            27,0xFF9CC0D3,true);
        if(game.finished){
            block(c,85,h*.38f,995,290,26,0xF50C1725);
            title(c,"NÚCLEO VENCEDOR",540,h*.38f+77,59,
                TEAM[Math.max(0,game.winner)],true);
            String who=game.winner<entrants.size()?
                entrants.get(game.winner).racer.name:"EQUIPE";
            title(c,clip(who,17).toUpperCase(Locale.ROOT),540,h*.38f+169,
                72,Color.WHITE,true);
            title(c,"BATALHA ENCERRADA",540,h*.38f+245,
                31,0xFFDBE7F4,true);
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
        float available=h-425;
        float sc=Math.min(1f,available/(CoreBattleEngine.BOTTOM-CoreBattleEngine.TOP+60));
        float offsetY=220+(available-(CoreBattleEngine.BOTTOM-CoreBattleEngine.TOP+60)*sc)*.5f;
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
