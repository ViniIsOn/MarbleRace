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

/** Vertical broadcast canvas for the original two-core battle mode. */
public final class CoreBattleRenderer {
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final int BG=0xFF070A11,GOLD=0xFFFFD343,PURPLE=0xFF9B44F7;
    private static final int[] COLORS={GOLD,PURPLE};
    private void circle(Canvas c,float x,float y,float r,int color){
        p.reset();p.setAntiAlias(true);p.setColor(color);c.drawCircle(x,y,r,p);
    }
    private void line(Canvas c,float x,float y,float tx,float ty,float width,int color){
        p.reset();p.setAntiAlias(true);p.setColor(color);p.setStrokeWidth(width);
        p.setStrokeCap(Paint.Cap.ROUND);c.drawLine(x,y,tx,ty,p);
    }
    private void panel(Canvas c,float x,float y,float x2,float y2,float rad,int color){
        p.reset();p.setAntiAlias(true);p.setColor(color);
        c.drawRoundRect(x,y,x2,y2,rad,rad,p);
    }
    private void text(Canvas c,String s,float x,float y,float size,int color,boolean center){
        p.reset();p.setAntiAlias(true);p.setColor(color);p.setTextSize(size);
        p.setTypeface(Typeface.create("sans-serif-condensed",Typeface.BOLD));
        if(center)p.setTextAlign(Paint.Align.CENTER);
        c.drawText(s,x,y,p);
    }
    private String clip(String name,int max){
        if(name==null||name.isEmpty())return "COMPETIDOR";
        return name.length()>max?name.substring(0,max-1)+"…":name;
    }
    private int alpha(int color,int opacity){
        return (color&0x00FFFFFF)|(Math.min(255,Math.max(0,opacity))<<24);
    }
    private void avatar(Canvas c,Bitmap picture,float x,float y,float radius,int outline){
        circle(c,x,y,radius+5,outline);
        if(picture==null||picture.isRecycled()||picture.getWidth()<1||picture.getHeight()<1){
            circle(c,x,y,radius,0xFF1A2638);
            return;
        }
        BitmapShader shader=new BitmapShader(picture,Shader.TileMode.CLAMP,Shader.TileMode.CLAMP);
        Matrix matrix=new Matrix();
        float sc=Math.max(radius*2/picture.getWidth(),radius*2/picture.getHeight());
        matrix.setScale(sc,sc);
        matrix.postTranslate(x-picture.getWidth()*sc*.5f,y-picture.getHeight()*sc*.5f);
        shader.setLocalMatrix(matrix);
        p.reset();p.setAntiAlias(true);p.setShader(shader);
        c.drawCircle(x,y,radius,p);
        p.setShader(null);
    }
    public void render(Canvas original,CoreBattleEngine game,List<ModeEngine.Orb> entrants){
        if(original.getWidth()<1||original.getHeight()<1)return;
        original.save();
        float scale=original.getWidth()/1080f;
        original.scale(scale,scale);
        float h=original.getHeight()/scale;
        Canvas c=original;
        c.drawColor(BG);
        float centerY=h*.50f;
        float outer=Math.min(200f,Math.max(92f,h*.18f));
        // subtle checkerboard stage and moving neon accents
        for(int row=0;row<(int)h/76+1;row++)
            for(int col=0;col<11;col++)
                if(((row+col)&1)==0)
                    panel(c,col*99,row*76,col*99+99,row*76+76,0,0xFF0D1420);
        for(int i=0;i<19;i++){
            float x=36+i*61;
            circle(c,x,h*.24f+(float)Math.sin(game.elapsed*.7+i*.64f)*19f,
                3+(i%3),alpha(COLORS[i%2],100));
        }
        // Structural platforms, tracks and central crane
        line(c,15,h*.29f,1065,h*.29f,21,0xFF353B48);
        line(c,0,h*.75f,1080,h*.75f,19,0xFF45515E);
        for(int i=0;i<17;i++){
            float x=i*68;
            panel(c,x,h*.76f,x+37,h*.778f,5,0xFF272D39);
        }
        // Animated crane head swings between the competing cores
        float armAngle=(float)Math.sin(game.cranePhase)*.48f;
        float craneX=540+(float)Math.sin(armAngle)*130;
        float craneY=h*.31f+120+(float)Math.cos(game.cranePhase*.7f)*45;
        line(c,540,h*.29f,craneX,craneY,16,0xFFAAB4C3);
        circle(c,540,h*.29f,25,0xFF40495A);
        circle(c,540,h*.29f,16,0xFFCBD3DA);
        line(c,craneX-42,craneY+28,craneX,craneY,13,0xFFBEC8D1);
        line(c,craneX,craneY,craneX+42,craneY+28,13,0xFFBEC8D1);
        // Side cores: two large ringed discs with independent HP.
        for(int side=0;side<2;side++){
            float x=CoreBattleEngine.CORE_X[side];
            int col=COLORS[side];
            circle(c,x+10,centerY+13,outer+23,0x99000000);
            circle(c,x,centerY,outer+14,0xFF151923);
            circle(c,x,centerY,outer,col);
            for(int ring=0;ring<6;ring++){
                p.reset();p.setAntiAlias(true);
                p.setColor(alpha(0xFF17191F,ring%2==0?130:85));
                p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(3.2f);
                c.drawCircle(x,centerY,outer-13-ring*outer*.125f,p);
            }
            // Sector marks echo turbine cores without copying the reference art.
            for(int k=0;k<24;k++){
                double theta=(k/24.0)*Math.PI*2+(side==0?1:-1)*game.elapsed*.035f;
                float a=outer*.54f,b=outer*.94f;
                line(c,x+(float)Math.cos(theta)*a,centerY+(float)Math.sin(theta)*a,
                    x+(float)Math.cos(theta)*b,centerY+(float)Math.sin(theta)*b,2,alpha(0xFF000000,75));
            }
            if(game.shieldTime[side]>0){
                p.reset();p.setAntiAlias(true);p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(12);p.setColor(0xFF75E9FA);
                c.drawCircle(x,centerY,outer+26,p);
                circle(c,x-outer*.43f,centerY-outer*.63f,14,0xFF75E9FA);
            }
            circle(c,x,centerY,outer*.32f,0xFF0A0D14);
            circle(c,x,centerY,outer*.18f,0xFF21E65B);
            // Selected racer portrait is shown without replacing the core art.
            Bitmap picture=side<entrants.size()?entrants.get(side).racer.avatar:null;
            avatar(c,picture,x,centerY-outer*.49f,Math.max(20,outer*.15f),0xFF101820);
            // Small status bar directly under each core.
            float length=outer*1.62f;
            panel(c,x-length*.5f,centerY+outer+33,x+length*.5f,centerY+outer+52,10,0xFF263441);
            panel(c,x-length*.5f,centerY+outer+33,
                x-length*.5f+length*game.hp[side]/CoreBattleEngine.MAX_HP,
                centerY+outer+52,10,col);
            text(c,Math.round(game.hp[side])+" / "+Math.round(CoreBattleEngine.MAX_HP),
                x,centerY+outer+85,27,Color.WHITE,true);
            text(c,clip(side<entrants.size()?entrants.get(side).racer.name:"NÚCLEO "+(side+1),12).toUpperCase(Locale.ROOT),
                x,centerY-outer-52,38,Color.WHITE,true);
        }
        // Fired projectiles move across the center line, with recognizable shapes.
        for(CoreBattleEngine.Projectile shot:game.projectiles){
            float sy=centerY+(shot.y-CoreBattleEngine.CORE_Y)*.72f;
            int col=COLORS[shot.side];
            line(c,shot.x-shot.vx*.065f,sy-shot.vy*.05f,shot.x,sy,7,alpha(col,160));
            switch(shot.type){
                case CoreBattleEngine.BOMB:
                    circle(c,shot.x,sy,20,0xFF17191D);
                    circle(c,shot.x-7,sy-7,7,0xFFFF6B48);
                    text(c,"B",shot.x,sy+9,24,0xFFFFBE60,true);
                    break;
                case CoreBattleEngine.SAW:
                    p.reset();p.setAntiAlias(true);p.setColor(0xFFB9E5ED);
                    int points=16;Path saw=new Path();
                    for(int i=0;i<points;i++){
                        double a=game.elapsed*8+i*Math.PI*2/points;
                        float radius=(i%2==0?24f:17f);
                        float px=shot.x+(float)Math.cos(a)*radius;
                        float py=sy+(float)Math.sin(a)*radius;
                        if(i==0)saw.moveTo(px,py);else saw.lineTo(px,py);
                    }
                    saw.close();c.drawPath(saw,p);
                    circle(c,shot.x,sy,9,0xFF3F596B);
                    break;
                default:
                    circle(c,shot.x,sy,13,0xFFF5F8FF);
                    circle(c,shot.x,sy,9,col);
                    break;
            }
        }
        if(game.laserFlash>0&&game.laserSide>=0){
            int source=game.laserSide,target=1-source;
            float power=game.laserFlash/.32f;
            line(c,CoreBattleEngine.CORE_X[source],centerY-80,
                CoreBattleEngine.CORE_X[target],centerY-80,18*power+5,
                alpha(COLORS[source],(int)(210*power)));
            line(c,CoreBattleEngine.CORE_X[source],centerY-80,
                CoreBattleEngine.CORE_X[target],centerY-80,4,Color.WHITE);
        }
        for(CoreBattleEngine.Impact hit:game.impacts){
            float iy=centerY+(hit.y-CoreBattleEngine.CORE_Y)*.72f;
            p.reset();p.setAntiAlias(true);p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(10*hit.life);p.setColor(alpha(COLORS[hit.side],(int)(235*hit.life)));
            c.drawCircle(hit.x,iy,15+(1-hit.life)*86,p);
        }
        // Broadcaster HUD: visible in both compact preview and exported Shorts.
        panel(c,20,20,1060,175,25,0xF018202D);
        text(c,"MARBLE LAB  •  CORE BATTLE",48,79,43,Color.WHITE,false);
        int remaining=(int)Math.ceil(game.remaining());
        text(c,String.format(Locale.US,"%02d:%02d",remaining/60,remaining%60),
            989,133,57,0xFFFFE68C,true);
        text(c,"DOIS NÚCLEOS  /  ARMAS  /  GUINDASTE",49,131,25,0xFFBED1DD,false);
        // Below combat zone show a concise weapon legend.
        float legendTop=Math.max(h*.79f,h-285);
        panel(c,21,legendTop,1059,Math.min(h-20,legendTop+245),24,0xF0151F2A);
        text(c,"ARMAS",48,legendTop+49,31,Color.WHITE,false);
        String[] names={"LASER","SERRA","ESCUDO","BOMBA","TIRO","GUINDASTE"};
        int[] colors={0xFF28E8F2,0xFF7CEE89,0xFF93C9FF,0xFFFF7E70,0xFFFFD768,0xFFD4A4FF};
        for(int k=0;k<names.length;k++){
            int row=k/3,col=k%3;
            float x=62+col*342,y=legendTop+92+row*57;
            circle(c,x,y,9,colors[k]);
            text(c,names[k],x+23,y+8,25,0xFFCEDDE4,false);
        }
        if(game.eventTimer>0 && !game.finished){
            panel(c,190,Math.max(183,centerY-outer-145),890,
                Math.max(245,centerY-outer-77),20,alpha(0xFF121A24,190));
            text(c,game.event,540,Math.max(226,centerY-outer-95),39,0xFFF5F7FF,true);
        }
        if(game.finished && game.winner>=0){
            int winning=game.winner;
            panel(c,130,centerY-122,950,centerY+132,25,0xF00B121C);
            text(c,"NÚCLEO VENCEDOR",540,centerY-36,57,COLORS[winning],true);
            String label=clip(winning<entrants.size()?entrants.get(winning).racer.name:"NÚCLEO",15);
            text(c,label.toUpperCase(Locale.ROOT),540,centerY+42,66,Color.WHITE,true);
            text(c,"FIM DA DISPUTA",540,centerY+101,29,0xFFB7C6DA,true);
        }
        original.restore();
    }
}
