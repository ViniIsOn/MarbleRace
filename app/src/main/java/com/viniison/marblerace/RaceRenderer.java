package com.viniison.marblerace;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.graphics.Typeface;
import java.util.List;
import java.util.Locale;

/** High-contrast clean race graphics with distinct track identities. */
public final class RaceRenderer {
    private final Paint p=new Paint(3);
    private final Path path=new Path();
    private float camera=0;
    private static final int[] SKY={0xFF81D8F9,0xFFFFE7A2,0xFF131E3A,0xFFEB754B};
    private static final int[] GROUND={0xFFB9F1F5,0xFFFFD3E8,0xFF1A254B,0xFFF5B75C};
    private static final int[] ROAD={0xFFEFFDFD,0xFFFFF2BD,0xFF27334E,0xFF4D1F27};
    private static final int[] EDGES={0xFF358CBD,0xFFEC6F9F,0xFF58FFF2,0xFFFFBA5E};
    private static final int[] PINS={0xFFFE6781,0xFF8B63DD,0xFF73FBEE,0xFFF9D779};
    private static final int[] DARK={0xFF15364A,0xFF52334D,0xFF102039,0xFF522B25};

    public void resetCamera(){camera=0;}
    private void fill(Canvas c,int color,float x1,float y1,float x2,float y2,float rad){
        p.reset();p.setAntiAlias(true);p.setColor(color);
        c.drawRoundRect(x1,y1,x2,y2,rad,rad,p);
    }
    private void circle(Canvas c,int color,float x,float y,float rad){
        p.reset();p.setAntiAlias(true);p.setColor(color);c.drawCircle(x,y,rad,p);
    }
    private void stroke(Canvas c,int color,float width,float x1,float y1,float x2,float y2){
        p.reset();p.setAntiAlias(true);p.setColor(color);p.setStrokeWidth(width);
        p.setStrokeCap(Paint.Cap.ROUND);c.drawLine(x1,y1,x2,y2,p);
    }
    private void label(Canvas c,String content,float x,float y,float size,int color,boolean bold){
        p.reset();p.setAntiAlias(true);p.setColor(color);p.setTextSize(size);
        p.setTypeface(Typeface.create(bold?"sans-serif-condensed":"sans-serif",
            bold?Typeface.BOLD:Typeface.NORMAL));
        c.drawText(content,x,y,p);
    }
    private String clip(String s,int n){return s.length()>n?s.substring(0,n-1)+"…":s;}
    private int alpha(int c,int a){return (c&0x00FFFFFF)|((Math.max(0,Math.min(255,a)))<<24);}
    public void render(Canvas original,RaceEngine engine,boolean export){
        render(original,engine,export,false);
    }
    /** Bouncy-style race: black rails, blue sky and no survival mechanics. */
    public void render(Canvas original,RaceEngine engine,boolean export,boolean ringIntroCourse){
        if(original.getWidth()<1||original.getHeight()<1)return;
        original.save();
        float factor=original.getWidth()/1080f;
        original.scale(factor,factor);
        Canvas c=original;
        float h=original.getHeight()/factor;
        int t=engine.track;
        p.reset();p.setAntiAlias(true);
        p.setShader(new LinearGradient(0,0,0,h,ringIntroCourse?0xFF61C7FB:SKY[t],ringIntroCourse?0xFFB7E9FF:GROUND[t],Shader.TileMode.CLAMP));
        c.drawRect(0,0,1080,h,p);p.setShader(null);
        float target=Math.max(0,engine.leadY()-h*.47f);
        camera+=(target-camera)*(export?.13f:.17f);
        if(engine.elapsed<.04f)camera=0;
        c.save();c.translate(0,-camera);
        float top=Math.max(0,camera-300),bottom=Math.min(RaceEngine.FINISH_Y+450,camera+h+300);
        decorate(c,t,top,bottom,engine.elapsed);
        // Thick outer rail and thin inner line, fewer interface effects.
        trackShape(engine,top,bottom);
        p.reset();p.setAntiAlias(true);p.setColor(alpha(DARK[t],90));
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(ringIntroCourse?72:45);
        c.drawPath(path,p);
        p.setStyle(Paint.Style.FILL);p.setColor(ringIntroCourse?0xFF80D4FA:ROAD[t]);c.drawPath(path,p);
        trackLines(c,engine,t,top,bottom,ringIntroCourse);
        for(float y=650;y<RaceEngine.FINISH_Y;y+=940){
            if(y<top-80||y>bottom+80)continue;
            float cx=engine.centerAt(y);
            fill(c,alpha(ringIntroCourse?0xFF101820:EDGES[t],ringIntroCourse?225:170),cx-320,y-26,cx+320,y+34,11);
            for(int i=0;i<7;i++) {
                float x=cx-288+i*92;
                stroke(c,alpha(Color.WHITE,200),11,x,y-5,x+23,y+14);
            }
        }
        for(RaceEngine.Bumper b:engine.bumpers){
            if(b.y<top-110||b.y>bottom+110)continue;
            float x=engine.obstacleX(b);
            circle(c,alpha(DARK[t],90),x+7,b.y+10,b.r+15);
            circle(c,ringIntroCourse?0xFF080B10:EDGES[t],x,b.y,b.r+7);
            circle(c,ringIntroCourse?(b.type==1?0xFF16141B:0xFF333D47):(b.type==1?DARK[t]:PINS[t]),x,b.y,b.r);
            if(b.type==1){
                // Rotating hazard with visible spokes.
                for(int a=0;a<4;a++){
                    double ang=engine.elapsed*3+b.phase+a*Math.PI/2;
                    float px=x+(float)Math.cos(ang)*b.r*.74f;
                    float py=b.y+(float)Math.sin(ang)*b.r*.74f;
                    stroke(c,ringIntroCourse?0xFFFFC947:PINS[t],9,x,b.y,px,py);
                }
                circle(c,Color.WHITE,x,b.y,9);
            }else{
                circle(c,alpha(Color.WHITE,195),x-b.r*.28f,b.y-b.r*.28f,Math.max(5,b.r*.18f));
            }
        }
        float fy=RaceEngine.FINISH_Y;
        if(fy>=top-90&&fy<=bottom+90){
            float cx=engine.centerAt(fy);
            fill(c,ringIntroCourse?Color.BLACK:DARK[t],cx-356,fy-22,cx+356,fy+26,3);
            for(int i=0;i<16;i++)if((i&1)==0)
                fill(c,Color.WHITE,cx-350+i*44,fy-19,cx-306+i*44,fy+23,1);
            label(c,"FINISH",cx-110,fy-64,52,DARK[t],true);
        }
        for(RaceEngine.Ball b:engine.balls)drawTrail(c,b,top,bottom);
        for(RaceEngine.Ball b:engine.balls)if(b.y>=top-70&&b.y<=bottom+70)drawBall(c,b);
        c.restore();
        if(ringIntroCourse)drawRingRaceHud(c,engine,h);
        else drawHud(c,engine,h);
        original.restore();
    }
    private void trackShape(RaceEngine engine,float top,float bottom){
        path.reset();
        for(float y=top;y<=bottom+20;y+=20){
            float x=engine.centerAt(y)-370;
            if(y==top)path.moveTo(x,y);else path.lineTo(x,y);
        }
        for(float y=bottom+20;y>=top;y-=20){
            path.lineTo(engine.centerAt(y)+370,y);
        }
        path.close();
    }
    private void decorate(Canvas c,int t,float top,float bottom,float time){
        for(float y=(float)Math.floor(top/340)*340;y<bottom+340;y+=340){
            float offset=(float)Math.sin(y*.018f)*44;
            float x1=140+offset,x2=930-offset;
            if(t==0){
                cloud(c,x1,y+84,.95f);cloud(c,x2,y+250,.73f);
            }else if(t==1){
                circle(c,0x99FFFFFF,x1,y+90,47);
                circle(c,0xBFFF91BE,x1+10,y+76,21);
                circle(c,0xBFFFFFFF,x2,y+222,35);
                stroke(c,0xAAFE7FA7,10,x2-21,y+198,x2+19,y+240);
            }else if(t==2){
                circle(c,0x554CFFEF,x1,y+160,8);
                circle(c,0xA752C9FF,x2,y+72,13);
                stroke(c,0x555CDAF9,3,x1-25,y+85,x1+20,y+38);
                stroke(c,0x555CDAF9,3,x2-20,y+168,x2+20,y+208);
            }else{
                circle(c,0x99FFD273,x1,y+120,18);
                circle(c,0x88FF733E,x2,y+190,33);
                stroke(c,0x88FFE48C,7,x1-18,y+220,x1+42,y+242);
            }
        }
    }
    private void cloud(Canvas c,float x,float y,float sc){
        circle(c,0xBBFFFFFF,x,y,47*sc);
        circle(c,0xBBFFFFFF,x-45*sc,y+8*sc,33*sc);
        circle(c,0xBBFFFFFF,x+43*sc,y+9*sc,37*sc);
    }
    private void trackLines(Canvas c,RaceEngine engine,int t,float top,float bottom,boolean ringIntroCourse){
        for(int side=-1;side<=1;side+=2){
            path.reset();
            for(float y=top;y<=bottom+30;y+=20){
                float x=engine.centerAt(y)+side*349;
                if(y==top)path.moveTo(x,y);else path.lineTo(x,y);
            }
            p.reset();p.setAntiAlias(true);p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(ringIntroCourse?19:11);p.setColor(ringIntroCourse?Color.BLACK:EDGES[t]);c.drawPath(path,p);
        }
        for(float y=(float)Math.floor(top/180)*180;y<=bottom;y+=180){
            float cx=engine.centerAt(y);
            fill(c,alpha(ringIntroCourse?Color.WHITE:EDGES[t],ringIntroCourse?130:75),cx-5,y+18,cx+5,y+90,5);
            circle(c,alpha(ringIntroCourse?Color.BLACK:EDGES[t],170),cx-330,y+58,9);
            circle(c,alpha(ringIntroCourse?Color.BLACK:EDGES[t],170),cx+330,y+58,9);
        }
    }
    private void drawTrail(Canvas c,RaceEngine.Ball b,float top,float bottom){
        if(b.trailCount<2)return;
        for(int i=0;i<b.trailCount;i++){
            int index=(b.trailCursor-b.trailCount+i+RaceEngine.TRAIL_SAMPLES)%RaceEngine.TRAIL_SAMPLES;
            float x=b.trailX[index],y=b.trailY[index];
            if(y<top-50||y>bottom+50)continue;
            float ratio=(i+1)/(float)b.trailCount;
            int a=(int)(ratio*150f);
            circle(c,alpha(b.racer.color,a),x,y,7+ratio*26);
            if(i%3==0)circle(c,alpha(Color.WHITE,(int)(ratio*75f)),x-5,y-5,2+ratio*4);
        }
    }
    private void drawBall(Canvas c,RaceEngine.Ball b){
        float r=RaceEngine.BALL_R;
        circle(c,0x50000000,b.x+4,b.y+7,r+7);
        circle(c,Color.WHITE,b.x,b.y,r+4);
        circle(c,b.racer.color,b.x,b.y,r);
        Bitmap avatar=b.racer.avatar;
        if(avatar!=null&&!avatar.isRecycled()&&avatar.getWidth()>0&&avatar.getHeight()>0){
            BitmapShader shader=new BitmapShader(avatar,Shader.TileMode.CLAMP,Shader.TileMode.CLAMP);
            Matrix transform=new Matrix();
            float scale=Math.max(2*r/avatar.getWidth(),2*r/avatar.getHeight());
            transform.setScale(scale,scale);
            transform.postTranslate(b.x-avatar.getWidth()*scale*.5f,b.y-avatar.getHeight()*scale*.5f);
            shader.setLocalMatrix(transform);
            p.reset();p.setAntiAlias(true);p.setShader(shader);
            c.drawCircle(b.x,b.y,r-3,p);p.setShader(null);
        }else{
            String n=b.racer.name.trim();
            String first=n.isEmpty()?"?":n.substring(0,1).toUpperCase(Locale.ROOT);
            p.reset();p.setAntiAlias(true);p.setColor(DARK[0]);
            p.setTextSize(39);p.setTypeface(Typeface.DEFAULT_BOLD);
            c.drawText(first,b.x-p.measureText(first)/2,b.y+14,p);
        }
        circle(c,0x99FFFFFF,b.x-16,b.y-17,8);
    }
    /** Broadcast scoreboard kept outside the obstacle area for easy Shorts cropping. */
    private void drawRingRaceHud(Canvas c,RaceEngine engine,float h){
        fill(c,0xDA000000,26,24,1054,142,18);
        label(c,"MARBLE LAB  /  OBSTACLE RACE",53,78,39,Color.WHITE,true);
        label(c,"RING START  →  FINISH LINE",56,119,25,0xFFC7EFFF,true);
        String stamp=String.format(Locale.US,"%02d:%02d",(int)engine.elapsed/60,(int)engine.elapsed%60);
        fill(c,0xCCFFFFFF,842,51,1030,112,13);
        label(c,stamp,889,94,33,Color.BLACK,true);
        List<RaceEngine.Ball> leaders=engine.ranked();
        float base=h-188;
        fill(c,0xE9000000,24,base,1056,h-25,22);
        label(c,"CLASSIFICAÇÃO",51,base+40,27,Color.WHITE,true);
        for(int i=0;i<Math.min(3,leaders.size());i++){
            RaceEngine.Ball b=leaders.get(i);
            int x=62+i*333;
            circle(c,b.racer.color,x+22,base+94,22);
            label(c,(i+1)+". "+clip(b.racer.name,10),x+56,base+105,26,Color.WHITE,true);
        }
        fill(c,0xFF42525B,45,h-41,1035,h-31,6);
        float progress=Math.max(0,Math.min(1,engine.leadY()/RaceEngine.FINISH_Y));
        fill(c,0xFFFFCA42,45,h-41,45+990*progress,h-31,6);
        if(engine.winner()!=null&&engine.finished==engine.balls.size()){
            fill(c,0xEB05080B,132,h*.36f,948,h*.58f,24);
            label(c,"CHEGADA!",330,h*.45f,66,0xFFFFCA42,true);
            label(c,clip(engine.winner().racer.name,15),298,h*.51f,57,Color.WHITE,true);
        }
    }
    private void drawHud(Canvas c,RaceEngine engine,float h){
        int t=engine.track;
        int dark=DARK[t];
        // Minimal broadcast-style overlays, readable over every theme.
        fill(c,0xF7FFFFFF,22,20,1058,142,19);
        fill(c,EDGES[t],22,20,37,142,5);
        label(c,"MARBLE RACE",56,72,40,0xFF1D2634,true);
        label(c,"TRACK "+String.format(Locale.US,"%02d",t+1)+"  /  "+RaceEngine.TRACK_NAMES[t],58,112,25,0xFF51606A,true);
        String stamp=String.format(Locale.US,"%02d:%02d",(int)engine.elapsed/60,(int)engine.elapsed%60);
        fill(c,0xFF1D2634,838,49,1024,117,15);
        label(c,stamp,882,99,36,Color.WHITE,true);
        float base=h-194;
        fill(c,0xF9FFFFFF,22,base,1058,h-26,18);
        label(c,"LEADERBOARD",48,base+39,21,0xFF566676,true);
        List<RaceEngine.Ball> top=engine.ranked();
        for(int i=0;i<Math.min(3,top.size());i++){
            RaceEngine.Ball b=top.get(i);
            int x=51+i*340;
            fill(c,i==0?0xFFE5EEF2:0xFFEDF1F4,x,base+52,x+321,base+130,12);
            fill(c,b.racer.color,x,base+52,x+8,base+130,3);
            label(c,""+(i+1),x+20,base+105,42,0xFF1E2D3B,true);
            circle(c,b.racer.color,x+99,base+93,22);
            label(c,clip(b.racer.name,9),x+132,base+102,25,0xFF1F3040,true);
        }
        fill(c,0xFFCDD8DB,45,h-39,1035,h-30,4);
        float progress=Math.max(0,Math.min(1,engine.leadY()/RaceEngine.FINISH_Y));
        fill(c,EDGES[t],45,h-39,45+990*progress,h-30,4);
        RaceEngine.Ball winner=engine.winner();
        if(winner!=null && engine.finished==engine.balls.size()){
            fill(c,0xF8FFFFFF,135,h*.37f,945,h*.57f,23);
            label(c,"WINNER",403,h*.44f,58,dark,true);
            label(c,clip(winner.racer.name,15),295,h*.50f,59,dark,true);
        }else if(!engine.running&&engine.elapsed==0){
            fill(c,0xEFFFFFFF,133,h*.42f,947,h*.53f,24);
            label(c,"READY TO RACE",295,h*.485f,53,dark,true);
        }
    }
}
