package com.viniison.marblerace;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import java.util.List;
import java.util.Locale;

public final class RaceRenderer {
    private final Paint p=new Paint(3);
    private final Path path=new Path();
    private final RectF rect=new RectF();
    private static final int BG=0xFF090E15;
    private static final int TRACK=0xFF202F3B;
    private static final int LIME=0xFFC6F47A;
    private float camera=0;

    public void resetCamera(){camera=0;}
    private void fill(Canvas c,int color,float l,float t,float r,float b,float radius){
        p.reset();p.setAntiAlias(true);p.setColor(color);p.setStyle(Paint.Style.FILL);
        c.drawRoundRect(l,t,r,b,radius,radius,p);
    }
    private void line(Canvas c,int color,float w,float x1,float y1,float x2,float y2){
        p.reset();p.setAntiAlias(true);p.setStrokeWidth(w);p.setColor(color);p.setStrokeCap(Paint.Cap.ROUND);
        c.drawLine(x1,y1,x2,y2,p);
    }
    private void txt(Canvas c,String s,float x,float y,int size,int color,boolean bold){
        p.reset();p.setAntiAlias(true);p.setColor(color);p.setTextSize(size);
        p.setTypeface(Typeface.create(bold?"sans-serif-condensed":"sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));
        c.drawText(s,x,y,p);
    }
    private String clipped(String s,int len){return s.length()>len?s.substring(0,len-1)+"…":s;}
    public void render(Canvas original,RaceEngine engine,boolean export) {
        if(original.getWidth()<=0||original.getHeight()<=0)return;
        float scale=original.getWidth()/1080f;
        float h=original.getHeight()/scale;
        original.save();
        original.scale(scale,scale);
        Canvas c=original;
        c.drawColor(BG);
        p.reset();p.setAntiAlias(true);
        p.setColor(0xFF162330);p.setStrokeWidth(2);
        for(int x=0;x<1080;x+=90)c.drawLine(x,0,x,h,p);
        for(int y=0;y<h;y+=90)c.drawLine(0,y,1080,y,p);
        float desired=Math.max(0,engine.leadY()-h*.45f);
        camera+=(desired-camera)*Math.min(1,export?.09f:.16f);
        if(engine.elapsed<.05f)camera=0;
        // World space scrolls; HUD stays fixed.
        c.save();c.translate(0,-camera);
        float start=Math.max(0,camera-200);
        float end=Math.min(RaceEngine.FINISH_Y+500,camera+h+200);
        path.reset();
        for(float y=start;y<=end+24;y+=24) {
            float x=RaceEngine.centerAt(y)-370;
            if(y==start)path.moveTo(x,y);else path.lineTo(x,y);
        }
        for(float y=end+24;y>=start;y-=24)path.lineTo(RaceEngine.centerAt(y)+370,y);
        path.close();
        p.reset();p.setAntiAlias(true);p.setColor(TRACK);c.drawPath(path,p);
        // Subtle lane dots and boundary lights.
        for(int side=-1;side<=1;side+=2){
            path.reset();
            for(float y=start;y<=end+24;y+=24){
                float x=RaceEngine.centerAt(y)+side*355;
                if(y==start)path.moveTo(x,y);else path.lineTo(x,y);
            }
            p.reset();p.setAntiAlias(true);p.setStyle(Paint.Style.STROKE);
            p.setColor(0xFF92AD91);p.setStrokeWidth(8);c.drawPath(path,p);
        }
        for(float y=(float)Math.floor(start/160)*160;y<=end;y+=160) {
            float x=RaceEngine.centerAt(y);
            fill(c,0xFF43555F,x-5,y+20,x+5,y+76,5);
            line(c,0xFF4B6069,3,x-270,y,x-250,y);
            line(c,0xFF4B6069,3,x+250,y,x+270,y);
        }
        for(RaceEngine.Bumper b:engine.bumpers) {
            if(b.y<start-100 || b.y>end+100)continue;
            p.reset();p.setAntiAlias(true);p.setColor(0x55000000);
            c.drawCircle(b.x+6,b.y+9,b.r+11,p);
            p.setColor(0xFF96DDE2);c.drawCircle(b.x,b.y,b.r+7,p);
            p.setColor(0xFF345D6A);c.drawCircle(b.x,b.y,b.r,p);
            p.setColor(0xFFB6EAF1);c.drawCircle(b.x-8,b.y-8,Math.max(3,b.r*.2f),p);
        }
        // Finish arch and checkered flag.
        float fy=RaceEngine.FINISH_Y;
        if(fy>=start-100 && fy<=end+100) {
            float cx=RaceEngine.centerAt(fy);
            fill(c,LIME,cx-372,fy-12,cx+372,fy+14,4);
            for(int i=0;i<12;i++)if(i%2==0)fill(c,BG,cx-360+i*60,fy-10,cx-300+i*60,fy+12,2);
            txt(c,"CHEGADA",cx-134,fy-40,44,Color.WHITE,true);
        }
        for(RaceEngine.Ball b:engine.balls)if(b.y>=start-100 && b.y<=end+100)drawBall(c,b);
        c.restore();
        // Top glass HUD
        fill(c,0xEE101922,22,22,1058,155,26);
        txt(c,"MARBLE / RACE",52,79,42,Color.WHITE,true);
        txt(c,"STUDIO    •    VERTICAL 9:16",55,116,21,0xFF9BAFB9,false);
        fill(c,0xFF2A423B,806,53,1029,114,30);
        String t=String.format(Locale.US,"%02d:%02d",(int)engine.elapsed/60,(int)engine.elapsed%60);
        txt(c,t,864,95,36,LIME,true);
        fill(c,0xD9101922,24,h-207,1056,h-25,25);
        List<RaceEngine.Ball> rank=engine.ranked();
        txt(c,"CLASSIFICAÇÃO",55,h-160,24,0xFFADC0C6,true);
        int visible=Math.min(3,rank.size());
        for(int i=0;i<visible;i++){
            RaceEngine.Ball b=rank.get(i);
            int x=58+i*335;
            fill(c,0xFF2C3942,x-5,h-137,x+309,h-55,13);
            txt(c,String.format(Locale.US,"%02d",i+1),x+12,h-86,37,LIME,true);
            p.reset();p.setAntiAlias(true);p.setColor(b.racer.color);c.drawCircle(x+90,h-96,24,p);
            txt(c,clipped(b.racer.name,9),x+125,h-85,27,Color.WHITE,true);
        }
        float progress=Math.max(0,Math.min(1,engine.leadY()/RaceEngine.FINISH_Y));
        fill(c,0xFF34434B,40,h-29,1040,h-20,5);
        fill(c,LIME,40,h-29,40+progress*1000,h-20,5);
        if(engine.winner()!=null) {
            fill(c,0xF20B1117,100,h*.37f,980,h*.59f,36);
            txt(c,"1º LUGAR",370,h*.44f,65,LIME,true);
            txt(c,clipped(engine.winner().racer.name,17),290,h*.51f,67,Color.WHITE,true);
        }else if(!engine.running && engine.elapsed==0) {
            fill(c,0xCC101922,155,h*.43f,925,h*.53f,28);
            txt(c,"PRONTOS PARA CORRER?",250,h*.48f,50,Color.WHITE,true);
        }
        original.restore();
    }
    private void drawBall(Canvas c,RaceEngine.Ball b){
        final float r=RaceEngine.BALL_R;
        p.reset();p.setAntiAlias(true);p.setColor(0x77000000);
        c.drawCircle(b.x+5,b.y+8,r+7,p);
        p.setColor(0xFFFFFFFF);c.drawCircle(b.x,b.y,r+5,p);
        p.setColor(b.racer.color);c.drawCircle(b.x,b.y,r,p);
        Bitmap img=b.racer.avatar;
        if(img!=null && !img.isRecycled() && img.getWidth()>0 && img.getHeight()>0){
            BitmapShader sh=new BitmapShader(img,Shader.TileMode.CLAMP,Shader.TileMode.CLAMP);
            Matrix m=new Matrix();
            float sc=Math.max(2*r/img.getWidth(),2*r/img.getHeight());
            m.setScale(sc,sc);
            m.postTranslate(b.x-img.getWidth()*sc*.5f,b.y-img.getHeight()*sc*.5f);
            sh.setLocalMatrix(m);
            p.setShader(sh);c.drawCircle(b.x,b.y,r-3,p);
            p.setShader(null);
        }else{
            String n=b.racer.name.trim();
            String letter=n.isEmpty()?"?":n.substring(0,1).toUpperCase(Locale.ROOT);
            p.setColor(0xFF14212A);p.setTextSize(40);p.setTypeface(Typeface.DEFAULT_BOLD);
            float width=p.measureText(letter);
            c.drawText(letter,b.x-width/2,b.y+14,p);
        }
        p.setColor(0x77FFFFFF);c.drawCircle(b.x-14,b.y-16,9,p);
    }
}
