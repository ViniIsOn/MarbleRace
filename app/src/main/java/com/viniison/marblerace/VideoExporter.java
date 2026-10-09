package com.viniison.marblerace;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.media.Image;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.provider.MediaStore;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

final class VideoExporter {
    private static final int WIDTH=720,HEIGHT=1280,FPS=30;
    interface Progress {
        void update(int percent);
        default void stage(String label) {}
        default void musicWarning(String message) {}
    }
    private static class Output {
        final MediaMuxer muxer;
        int track=-1;
        boolean started=false,ended=false;
        Output(MediaMuxer m){muxer=m;}
    }
    private static int clamp(int v){return Math.max(0,Math.min(255,v));}
    // Write YUV_420_888 using the actual plane row and pixel strides.
    private static void writeImage(Image image,int[] pixels) {
        Image.Plane[] planes=image.getPlanes();
        if(planes.length<3)throw new IllegalStateException("Codificador YUV não suportado");
        ByteBuffer yy=planes[0].getBuffer(),uu=planes[1].getBuffer(),vv=planes[2].getBuffer();
        int ys=planes[0].getRowStride(),yp=planes[0].getPixelStride();
        int us=planes[1].getRowStride(),up=planes[1].getPixelStride();
        int vs=planes[2].getRowStride(),vp=planes[2].getPixelStride();
        for(int y=0;y<HEIGHT;y++) {
            int src=y*WIDTH;
            int row=y*ys;
            int cu=y/2*us,cv=y/2*vs;
            for(int x=0;x<WIDTH;x++){
                int argb=pixels[src+x];
                int r=(argb>>16)&255,g=(argb>>8)&255,b=argb&255;
                yy.put(row+x*yp,(byte)((77*r+150*g+29*b)>>8));
                if((y&1)==0 && (x&1)==0){
                    int u=clamp(((-43*r-85*g+128*b)>>8)+128);
                    int v=clamp(((128*r-107*g-21*b)>>8)+128);
                    uu.put(cu+(x/2)*up,(byte)u);
                    vv.put(cv+(x/2)*vp,(byte)v);
                }
            }
        }
    }
    private static void drain(MediaCodec codec,Output output,MediaCodec.BufferInfo info,boolean awaitEos) throws Exception{
        int idle=0;
        while(true) {
            int index=codec.dequeueOutputBuffer(info,awaitEos?10000:0);
            if(index==MediaCodec.INFO_TRY_AGAIN_LATER){
                if(!awaitEos)return;
                if(++idle>1800)throw new Exception("O codificador parou de responder");
            }else if(index==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                if(output.started)throw new Exception("Formato do vídeo mudou inesperadamente");
                output.track=output.muxer.addTrack(codec.getOutputFormat());
                output.muxer.start();output.started=true;
            }else if(index>=0) {
                if(info.size>0 && (info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG)==0){
                    if(!output.started)throw new Exception("Faixa MP4 não iniciada");
                    ByteBuffer buffer=codec.getOutputBuffer(index);
                    if(buffer==null)throw new Exception("Buffer de vídeo ausente");
                    buffer.position(info.offset);buffer.limit(info.offset+info.size);
                    output.muxer.writeSampleData(output.track,buffer,info);
                }
                boolean eos=(info.flags&MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0;
                codec.releaseOutputBuffer(index,false);
                if(eos){output.ended=true;return;}
            }
        }
    }
    static Uri export(Context ctx,List<RaceEngine.Racer> racers,int track,int mode,long seed,
                      String musicUri,Progress progress,AtomicBoolean cancel)throws Exception{
        final boolean hasMusic=musicUri!=null&&!musicUri.isEmpty();
        Progress videoProgress=hasMusic?new Progress(){
            @Override public void update(int pct){progress.update(Math.min(92,pct*92/100));}
            @Override public void stage(String label){progress.stage(label);}
            @Override public void musicWarning(String warning){progress.musicWarning(warning);}
        }:progress;
        Uri video;
        try {
            video=FastVideoExporter.export(ctx,racers,track,mode,seed,videoProgress,cancel);
        }catch(InterruptedException cancelled){
            throw cancelled;
        }catch(Exception gpuError){
            if(cancel.get())throw new InterruptedException("Exportação cancelada");
            progress.stage("Modo compatibilidade • sem GPU");
            videoProgress.update(0);
            video=exportSoftware(ctx,racers,track,mode,seed,videoProgress,cancel);
        }
        if(!hasMusic)return video;
        if(cancel.get()){
            ctx.getContentResolver().delete(video,null,null);
            throw new InterruptedException("Exportação cancelada");
        }
        progress.stage("Adicionando trilha sonora ao MP4");
        progress.update(93);
        try{
            Uri soundVideo=MusicMuxer.addMusic(ctx,video,Uri.parse(musicUri),cancel);
            progress.update(100);
            return soundVideo;
        }catch(InterruptedException cancelled){
            ctx.getContentResolver().delete(video,null,null);
            throw cancelled;
        }catch(Exception e){
            progress.musicWarning(e.getMessage());
            progress.update(100);
            // The silent MP4 remains available. Never report that it has audio.
            return video;
        }
    }
    private static Uri exportSoftware(Context ctx,List<RaceEngine.Racer> racers,int track,int mode,long seed,Progress progress,AtomicBoolean cancel)throws Exception {
        if(Build.VERSION.SDK_INT<29)throw new Exception("A exportação requer Android 10 ou mais recente.");
        ContentResolver resolver=ctx.getContentResolver();
        ContentValues val=new ContentValues();
        val.put(MediaStore.Video.Media.DISPLAY_NAME,"MarbleRace_"+System.currentTimeMillis()+".mp4");
        val.put(MediaStore.Video.Media.MIME_TYPE,"video/mp4");
        val.put(MediaStore.Video.Media.RELATIVE_PATH,Environment.DIRECTORY_MOVIES+"/MarbleRace");
        val.put(MediaStore.Video.Media.IS_PENDING,1);
        Uri uri=resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,val);
        if(uri==null)throw new Exception("Não foi possível criar o MP4.");
        ParcelFileDescriptor fd=null;
        MediaCodec codec=null;
        MediaMuxer muxer=null;
        Output output=null;
        Bitmap bitmap=null;
        boolean success=false;
        try {
            fd=resolver.openFileDescriptor(uri,"rw");
            if(fd==null)throw new Exception("Falha ao criar arquivo de vídeo");
            muxer=new MediaMuxer(fd.getFileDescriptor(),MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            output=new Output(muxer);
            MediaFormat format=MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC,WIDTH,HEIGHT);
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT,MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);
            format.setInteger(MediaFormat.KEY_BIT_RATE,3200000);
            format.setInteger(MediaFormat.KEY_FRAME_RATE,FPS);
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL,2);
            codec=MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
            codec.configure(format,null,null,MediaCodec.CONFIGURE_FLAG_ENCODE);
            codec.start();
            RaceEngine race=mode==0?new RaceEngine(racers,track,seed):null;
            ModeEngine mini=mode!=0?new ModeEngine(racers,mode-1,seed):null;
            RaceRenderer renderer=new RaceRenderer();
            ModeRenderer gameRenderer=new ModeRenderer();
            if(race!=null)race.running=true;
            if(mini!=null)mini.running=true;
            bitmap=Bitmap.createBitmap(WIDTH,HEIGHT,Bitmap.Config.ARGB_8888);
            Canvas canvas=new Canvas(bitmap);
            int[] pixels=new int[WIDTH*HEIGHT];
            MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();
            int lastFrame=0;
            int podiumFrames=0;
            for(int frame=0;frame<45*FPS;frame++) {
                if(cancel.get())throw new InterruptedException("Exportação cancelada");
                if(race!=null){race.advance(1f/FPS);renderer.render(canvas,race,true);}
                else{mini.advance(1f/FPS);gameRenderer.render(canvas,mini,true);}
                bitmap.getPixels(pixels,0,WIDTH,0,0,WIDTH,HEIGHT);
                int input=-1,attempts=0;
                while(input<0) {
                    input=codec.dequeueInputBuffer(10000);
                    if(input<0){
                        drain(codec,output,info,false);
                        if(++attempts>800)throw new Exception("Codificador ocupado por tempo excessivo");
                    }
                }
                Image inputImage=codec.getInputImage(input);
                if(inputImage==null)throw new Exception("O aparelho não suporta a exportação de quadros YUV");
                try{writeImage(inputImage,pixels);}finally{inputImage.close();}
                codec.queueInputBuffer(input,0,WIDTH*HEIGHT*3/2,frame*1000000L/FPS,0);
                drain(codec,output,info,false);
                lastFrame=frame+1;
                if(frame%15==0)progress.update(Math.min(99,(int)((frame+1)*100f/(45*FPS))));
                boolean classicFinished=race!=null && race.finished==race.balls.size() && race.elapsed>=10;
                boolean modeFinished=mini!=null && mini.winner()!=null && mini.elapsed>=5
                    && (mini.mode<=ModeEngine.CORE||mini.finished==mini.orbs.size());
                // Leave the finish/podium visible for 1.5 seconds in exported Shorts.
                if(classicFinished||modeFinished) {
                    podiumFrames++;
                    if(podiumFrames>=45)break;
                }else podiumFrames=0;
            }
            int eosBuffer=-1,tries=0;
            while(eosBuffer<0){
                eosBuffer=codec.dequeueInputBuffer(10000);
                if(eosBuffer<0){drain(codec,output,info,false);if(++tries>500)throw new Exception("Erro ao terminar o vídeo");}
            }
            codec.queueInputBuffer(eosBuffer,0,0,lastFrame*1000000L/FPS,MediaCodec.BUFFER_FLAG_END_OF_STREAM);
            drain(codec,output,info,true);
            if(!output.started||!output.ended)throw new Exception("O arquivo MP4 não foi finalizado");
            success=true;
            progress.update(100);
        }finally {
            if(bitmap!=null)bitmap.recycle();
            if(codec!=null){try{codec.stop();}catch(Exception ignored){}try{codec.release();}catch(Exception ignored){}}
            if(muxer!=null){if(output!=null && output.started)try{muxer.stop();}catch(Exception ignored){}try{muxer.release();}catch(Exception ignored){}}
            if(fd!=null)try{fd.close();}catch(Exception ignored){}
            if(success){
                ContentValues done=new ContentValues();done.put(MediaStore.Video.Media.IS_PENDING,0);
                resolver.update(uri,done,null,null);
            }else resolver.delete(uri,null,null);
        }
        return uri;
    }
}
