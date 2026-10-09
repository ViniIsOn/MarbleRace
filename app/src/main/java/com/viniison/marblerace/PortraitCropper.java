package com.viniison.marblerace;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Interactive head/portrait crop with finger drag and pinch zoom.
 *  Works offline for images imported from the gallery or online picker.
 */
final class PortraitCropper {
    interface Result {void onCropped(Bitmap portrait);}
    private final Activity activity;
    private final Bitmap source;
    private final Result result;
    PortraitCropper(Activity activity,Bitmap image,Result callback){
        this.activity=activity;this.source=image;this.result=callback;
    }
    private int dp(float v){return (int)(v*activity.getResources().getDisplayMetrics().density+.5f);}
    void show(){
        LinearLayout body=new LinearLayout(activity);body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(14),0,dp(14),dp(8));
        TextView tip=new TextView(activity);
        tip.setText("Arraste para centralizar o rosto. Use dois dedos para aumentar ou diminuir.");
        tip.setTextColor(0xFFD0DFE7);tip.setTextSize(13);
        tip.setPadding(dp(8),dp(10),dp(8),dp(12));
        body.addView(tip);
        CropView view=new CropView();
        body.addView(view,new LinearLayout.LayoutParams(-1,dp(400)));
        AlertDialog dialog=new AlertDialog.Builder(activity)
            .setTitle("RECORTAR O PERSONAGEM")
            .setView(body)
            .setNegativeButton("Cancelar",null)
            .setPositiveButton("USAR RECORTE",null).create();
        dialog.setOnShowListener(v->{
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(0xFF76BE1B);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(clicked->{
                try{
                    Bitmap crop=view.crop();
                    result.onCropped(crop);
                    dialog.dismiss();
                }catch(Exception error){
                    android.widget.Toast.makeText(activity,"Não foi possível recortar.",android.widget.Toast.LENGTH_SHORT).show();
                }
            });
        });
        dialog.show();
    }
    private final class CropView extends View {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
        private final Path hole=new Path();
        private float zoom=1,offsetX=0,offsetY=0,lastX,lastY,pinch=0;
        CropView(){super(activity);setLayerType(View.LAYER_TYPE_SOFTWARE,null);}
        private float radius(){return Math.min(getWidth()*.405f,getHeight()*.365f);}
        private float baseScale(float r){
            return Math.max(2*r/source.getWidth(),2*r/source.getHeight());
        }
        private void clamp(){
            float size=radius()*2,scaledX=source.getWidth()*baseScale(radius())*zoom;
            float scaledY=source.getHeight()*baseScale(radius())*zoom;
            offsetX=Math.max(-(scaledX-size)/2,Math.min((scaledX-size)/2,offsetX));
            offsetY=Math.max(-(scaledY-size)/2,Math.min((scaledY-size)/2,offsetY));
        }
        @Override protected void onDraw(Canvas c){
            super.onDraw(c);
            c.drawColor(0xFF172430);
            float r=radius(),cx=getWidth()/2f,cy=getHeight()/2f;
            for(int y=0;y<getHeight();y+=dp(24))for(int x=0;x<getWidth();x+=dp(24)){
                paint.setColor(((x/dp(24)+y/dp(24))%2==0)?0xFF263743:0xFF293E4B);
                c.drawRect(x,y,x+dp(24),y+dp(24),paint);
            }
            clamp();
            c.save();
            hole.reset();hole.addCircle(cx,cy,r,Path.Direction.CW);
            c.clipPath(hole);
            c.translate(cx+offsetX,cy+offsetY);
            float scale=baseScale(r)*zoom;
            c.scale(scale,scale);
            paint.setColor(Color.WHITE);
            c.drawBitmap(source,-source.getWidth()/2f,-source.getHeight()/2f,paint);
            c.restore();
            paint.setStyle(Paint.Style.STROKE);paint.setColor(0xFFF8FAFF);paint.setStrokeWidth(dp(3));
            c.drawCircle(cx,cy,r,paint);paint.setStyle(Paint.Style.FILL);
        }
        Bitmap crop(){
            int size=512;
            Bitmap bitmap=Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888);
            Canvas c=new Canvas(bitmap);
            c.drawColor(Color.TRANSPARENT);
            float r=radius();
            float scale=baseScale(r)*zoom*size/(r*2);
            c.translate(size/2f+offsetX*size/(r*2),size/2f+offsetY*size/(r*2));
            c.scale(scale,scale);
            paint.setColor(Color.WHITE);
            c.drawBitmap(source,-source.getWidth()/2f,-source.getHeight()/2f,paint);
            return bitmap;
        }
        private float separation(MotionEvent event){
            if(event.getPointerCount()<2)return 0;
            float dx=event.getX(0)-event.getX(1);
            float dy=event.getY(0)-event.getY(1);
            return (float)Math.sqrt(dx*dx+dy*dy);
        }
        @Override public boolean onTouchEvent(MotionEvent event){
            int action=event.getActionMasked();
            if(action==MotionEvent.ACTION_DOWN){
                lastX=event.getX();lastY=event.getY();pinch=0;return true;
            }
            if(action==MotionEvent.ACTION_POINTER_DOWN&&event.getPointerCount()>=2){
                pinch=separation(event);return true;
            }
            if(action==MotionEvent.ACTION_MOVE){
                if(event.getPointerCount()>=2){
                    float now=separation(event);
                    if(pinch>1)zoom=Math.max(1,Math.min(7,zoom*now/pinch));
                    pinch=now;
                }else{
                    offsetX+=event.getX()-lastX;
                    offsetY+=event.getY()-lastY;
                    lastX=event.getX();lastY=event.getY();
                }
                clamp();invalidate();return true;
            }
            if(action==MotionEvent.ACTION_POINTER_UP){
                pinch=0;
                lastX=event.getX(0);lastY=event.getY(0);
                return true;
            }
            return true;
        }
    }
}
