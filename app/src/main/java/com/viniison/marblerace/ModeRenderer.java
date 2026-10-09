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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;

/** Renders game modes without importing or recreating third-party artwork. */
public final class ModeRenderer {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private float camera=0;
    public void resetCamera(){camera=0;}
    private void disc(Canvas c,int color,float x,float y,float radius){
        paint.reset();paint.setAntiAlias(true);paint.setColor(color);
        c.drawCircle(x,y,radius,paint);
    }
    private void rect(Canvas c,int color,float l,float t,float r,float b,float rad){
        paint.reset();paint.setAntiAlias(true);paint.setColor(color);
        c.drawRoundRect(l,t,r,b,rad,rad,paint);
    }
    private void line(Canvas c,int color,float width,float x1,float y1,float x2,float y2){
        paint.reset();paint.setAntiAlias(true);paint.setColor(color);
        paint.setStrokeWidth(width);paint.setStrokeCap(Paint.Cap.ROUND);
        c.drawLine(x1,y1,x2,y2,paint);
    }
    private void type(Canvas c,String s,float x,float y,float size,int color,boolean centered){
        paint.reset();paint.setAntiAlias(true);paint.setColor(color);
        paint.setTextSize(size);paint.setTypeface(Typeface.create("sans-serif-condensed",Typeface.BOLD));
        if(centered)paint.setTextAlign(Paint.Align.CENTER);
        c.drawText(s,x,y,paint);
    }
    private int fade(int color,int a){
        return (color&0x00FFFFFF)|(Math.max(0,Math.min(255,a))<<24);
    }
    private void cloud(Canvas c,float x,float y,float size){
        disc(c,0xD9FFFFFF,x,y,45*size);
        disc(c,0xD9FFFFFF,x-43*size,y+13*size,34*size);
        disc(c,0xD9FFFFFF,x+43*size,y+13*size,37*size);
    }
    public void render(Canvas original,ModeEngine engine,boolean exporting){
        if(original.getWidth()==0||original.getHeight()==0)return;
        original.save();
        float s=original.getWidth()/1080f;
        original.scale(s,s);
        float height=original.getHeight()/s;
        Canvas c=original;
        int mode=engine.mode;
        int bg=(mode==ModeEngine.CORE)?0xFF171923:mode==ModeEngine.ELIMINATION?0xFFBED9FA:
            mode==ModeEngine.WORMS?0xFF1A2432:mode==ModeEngine.BIKES?0xFFDEEAF3:0xFF62C4FA;
        c.drawColor(bg);
        if(mode==ModeEngine.WORMS)worms(c,height,engine);
        else if(mode==ModeEngine.BIKES)bikes(c,height,engine);
        else arena(c,height,engine);
        hud(c,height,engine);
        original.restore();
    }
    private void arena(Canvas c,float h,ModeEngine game){
        if(game.mode==ModeEngine.RING||game.mode==ModeEngine.ELIMINATION){
            for(int i=0;i<7;i++){
                float x=100+(i*231)%1000;
                float y=h*.18f+(i*227)%Math.max(240,(int)(h*.63f));
                cloud(c,x,y,.8f+(i%3)*.25f);
            }
        }else{
            for(int i=0;i<14;i++){
                int x=70+(i*149)%940;int y=(i*227)%Math.max(500,(int)h);
                disc(c,0x554DFFE5,x,y,(i%3+1)*5);
            }
        }
        float sc=Math.min(1f,Math.max(.55f,(h-385)/835f));
        c.save();
        c.translate(ModeEngine.CX,h*.48f);
        c.scale(sc,sc);
        c.translate(-ModeEngine.CX,-ModeEngine.CY);
        float r=game.radius();
        if(game.mode==ModeEngine.CORE){
            disc(c,0xFF47B8CF,ModeEngine.CX,ModeEngine.CY,111);
            disc(c,0xFFDEFCFF,ModeEngine.CX,ModeEngine.CY,97);
            disc(c,0xFF3979A1,ModeEngine.CX,ModeEngine.CY,85);
            type(c,"CORE",ModeEngine.CX,ModeEngine.CY-6,37,Color.WHITE,true);
            type(c,String.format(Locale.US,"%02d",Math.round(game.coreLife)),ModeEngine.CX,ModeEngine.CY+47,59,0xFFFFE39E,true);
        }
        paint.reset();paint.setAntiAlias(true);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(game.mode==ModeEngine.ELIMINATION?44:51);
        paint.setStrokeCap(Paint.Cap.BUTT);
        paint.setColor(0x33000000);
        c.drawCircle(ModeEngine.CX+7,ModeEngine.CY+13,r,paint);
        paint.setColor(Color.BLACK);
        if(game.mode==ModeEngine.RING){
            float gap=40;
            c.drawArc(ModeEngine.CX-r,ModeEngine.CY-r,ModeEngine.CX+r,ModeEngine.CY+r,
                game.exitAngle()+gap*.5f,360-gap,false,paint);
        }else c.drawCircle(ModeEngine.CX,ModeEngine.CY,r,paint);
        paint.setStyle(Paint.Style.FILL);
        if(game.mode==ModeEngine.ELIMINATION && game.started()){
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(8);
            paint.setColor(0xFFE94B60);
            c.drawCircle(ModeEngine.CX,ModeEngine.CY,r+26,paint);
        }
        for(ModeEngine.Orb b:game.orbs)if(!b.out)trail(c,b,1f);
        for(ModeEngine.Orb b:game.orbs)if(!b.out)ball(c,b,b.x,b.y,41);
        c.restore();
    }
    private void worms(Canvas c,float h,ModeEngine game){
        float cameraDesired=Math.max(0,game.progress()-h*.48f);
        camera+=(cameraDesired-camera)*.13f;
        c.save();c.translate(0,-camera);
        float begin=Math.max(0,camera-100),end=Math.min(ModeEngine.FINISH+300,camera+h+200);
        rect(c,0xFF2D3C49,60,begin,1020,end,20);
        for(int side=0;side<2;side++){
            float x=side==0?95:985;
            line(c,0xFFFAF7D9,13,x,begin,x,end);
        }
        for(float y=(float)Math.floor(begin/195)*195;y<=end;y+=195){
            for(int i=0;i<5;i++)
                rect(c,0xFF435668,130+i*190,y+15,142+i*190,y+95,7);
        }
        for(float y=430;y<ModeEngine.FINISH;y+=850){
            rect(c,0xFF8F66D9,115,y-12,967,y+15,11);
            for(int i=0;i<8;i++)
                disc(c,0xFFFFCD67,177+i*101,y,12);
        }
        for(ModeEngine.Orb b:game.orbs)trail(c,b,2.3f);
        for(ModeEngine.Orb b:game.orbs)ball(c,b,b.x,b.y,33);
        float fy=ModeEngine.FINISH;
        if(fy>=begin && fy<=end){
            rect(c,Color.WHITE,75,fy-8,1005,fy+25,0);
            type(c,"FINISH",540,fy-40,45,Color.WHITE,true);
        }
        c.restore();
    }
    private void bikes(Canvas c,float h,ModeEngine game){
        float camTarget=Math.max(0,game.progress()-500);
        camera+=(camTarget-camera)*.13f;
        float vertical=Math.min(1f,Math.max(.52f,(h-350)/1500));
        c.save();c.translate(0,210);
        c.scale(1,vertical);
        c.translate(-camera,0);
        float begin=camera-150,end=camera+1320;
        for(int i=0;i<game.orbs.size();i++){
            float y=640+i*135;
            rect(c,i%2==0?0xFFF0F7FC:0xFFDCE9F4,begin,y+42,end,y+124,0);
            line(c,0xFF4B6B80,9,begin,y+110,end,y+110);
            for(int k=(int)Math.floor(begin/330);k<(int)(end/330)+3;k++){
                float x=k*330+115+(i%2)*80;
                if(x<0)continue;
                line(c,0xFFA1BBC8,9,x,y+105,x+100,y+105);
                if((k+i)%4==0){
                    Path triangle=new Path();
                    triangle.moveTo(x+108,y+105);
                    triangle.lineTo(x+133,y+64);
                    triangle.lineTo(x+164,y+105);
                    triangle.close();
                    paint.setStyle(Paint.Style.FILL);
                    paint.setColor(0xFFE9816A);c.drawPath(triangle,paint);
                }
            }
            ModeEngine.Orb b=game.orbs.get(i);
            if(b.x>=begin-70&&b.x<=end+70){
                disc(c,0xFF171B24,b.x-29,y+88,28);
                disc(c,0xFFFFFFFF,b.x-29,y+88,17);
                disc(c,0xFF171B24,b.x+38,y+88,28);
                disc(c,0xFFFFFFFF,b.x+38,y+88,17);
                line(c,b.racer.color,12,b.x-29,y+88,b.x+6,y+27);
                line(c,b.racer.color,12,b.x+6,y+27,b.x+38,y+88);
                line(c,b.racer.color,12,b.x+38,y+88,b.x-29,y+88);
                line(c,0xFF3C4F60,7,b.x+6,y+27,b.x+13,y+4);
                ball(c,b,b.x+13,y-30,30);
            }
        }
        line(c,0xFF202C40,24,ModeEngine.FINISH,460,ModeEngine.FINISH,1900);
        c.restore();
    }
    private void trail(Canvas c,ModeEngine.Orb b,float multiplier){
        for(int i=0;i<b.trailN;i++){
            int index=(b.trailIndex-b.trailN+i+b.trailX.length)%b.trailX.length;
            float amount=(i+1f)/b.trailN;
            float size=(6+amount*23)*multiplier;
            disc(c,fade(b.racer.color,(int)(amount*162)),b.trailX[index],b.trailY[index],size);
        }
    }
    private void ball(Canvas c,ModeEngine.Orb b,float x,float y,float rad){
        disc(c,0x6A000000,x+4,y+6,rad+5);
        disc(c,Color.WHITE,x,y,rad+3);
        disc(c,b.racer.color,x,y,rad);
        Bitmap avatar=b.racer.avatar;
        if(avatar!=null&&!avatar.isRecycled()&&avatar.getWidth()>0&&avatar.getHeight()>0){
            BitmapShader shader=new BitmapShader(avatar,Shader.TileMode.CLAMP,Shader.TileMode.CLAMP);
            Matrix m=new Matrix();
            float sc=Math.max(rad*2/avatar.getWidth(),rad*2/avatar.getHeight());
            m.setScale(sc,sc);
            m.postTranslate(x-avatar.getWidth()*sc*.5f,y-avatar.getHeight()*sc*.5f);
            shader.setLocalMatrix(m);
            paint.reset();paint.setAntiAlias(true);paint.setShader(shader);
            c.drawCircle(x,y,rad-2,paint);paint.setShader(null);
        }else{
            String name=b.racer.name;
            String letter=name.isEmpty()?"?":name.substring(0,1).toUpperCase(Locale.ROOT);
            type(c,letter,x,y+rad*.38f,rad*1.08f,0xFF1E2735,true);
        }
    }
    private void hud(Canvas c,float h,ModeEngine engine){
        rect(c,0xBB000000,20,21,1060,148,16);
        type(c,"MARBLE LAB",44,70,45,Color.WHITE,false);
        type(c,ModeEngine.NAMES[engine.mode],48,117,27,0xFFE4EEF7,false);
        String time=String.format(Locale.US,"%02d:%02d",(int)engine.elapsed/60,(int)engine.elapsed%60);
        type(c,time,1012,90,43,Color.WHITE,true);
        if(!engine.started()){
            type(c,String.valueOf(Math.max(1,3-(int)engine.elapsed)),540,h*.53f,118,Color.WHITE,true);
        }
        rect(c,0xCE101D2B,20,h-189,1060,h-20,19);
        ArrayList<ModeEngine.Orb> sorted=new ArrayList<>(engine.orbs);
        if(engine.mode==ModeEngine.CORE)
            Collections.sort(sorted,(a,b)->Float.compare(b.score,a.score));
        else if(engine.mode==ModeEngine.BIKES)
            Collections.sort(sorted,(a,b)->Float.compare(b.x,a.x));
        else if(engine.mode==ModeEngine.WORMS)
            Collections.sort(sorted,(a,b)->Float.compare(b.y,a.y));
        else Collections.sort(sorted,(a,b)->Boolean.compare(a.out,b.out));
        type(c,engine.mode==ModeEngine.CORE?"PONTUAÇÃO":engine.mode==ModeEngine.RING||
            engine.mode==ModeEngine.ELIMINATION?"SOBREVIVENTES":"CLASSIFICAÇÃO",52,h-139,29,Color.WHITE,false);
        for(int i=0;i<Math.min(3,sorted.size());i++){
            ModeEngine.Orb b=sorted.get(i);
            int x=55+i*340;
            disc(c,b.racer.color,x+18,h-74,19);
            String n=b.racer.name;
            if(n.length()>10)n=n.substring(0,9)+"…";
            type(c,(i+1)+". "+n,x+50,h-65,26,Color.WHITE,false);
        }
        ModeEngine.Orb winner=engine.winner();
        if(winner!=null){
            rect(c,0xEB101D2B,130,h*.36f,950,h*.60f,26);
            type(c,"VENCEDOR!",540,h*.435f,73,0xFFFFDF68,true);
            type(c,winner.racer.name,540,h*.515f,52,Color.WHITE,true);
        }
    }
}
