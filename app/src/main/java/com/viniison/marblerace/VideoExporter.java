package com.viniison.marblerace;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
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
import android.graphics.Canvas;
import android.view.Surface;
import java.io.File;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

final class VideoExporter {
    interface Progress { void update(int percent); }
    static Uri export(Context ctx,List<RaceEngine.Racer> racers,Progress progress,AtomicBoolean cancel)throws Exception {
        if(Build.VERSION.SDK_INT<29)throw new Exception("A exportação requer Android 10 ou mais recente.");
        ContentResolver resolver=ctx.getContentResolver();
        ContentValues val=new ContentValues();
        val.put(MediaStore.Video.Media.DISPLAY_NAME,"MarbleRace_"+System.currentTimeMillis()+".mp4");
        val.put(MediaStore.Video.Media.MIME_TYPE,"video/mp4");
        val.put(MediaStore.Video.Media.RELATIVE_PATH,Environment.DIRECTORY_MOVIES+"/MarbleRace");
        val.put(MediaStore.Video.Media.IS_PENDING,1);
        Uri uri=resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,val);
        if(uri==null)throw new Exception("Não foi possível criar vídeo na galeria.");
        ParcelFileDescriptor fd=null;
        MediaCodec codec=null;
        MediaMuxer muxer=null;
        Surface input=null;
        boolean started=false,success=false;
        try {
            fd=resolver.openFileDescriptor(uri,"rw");
            if(fd==null)throw new Exception("Não foi possível criar arquivo de saída.");
            muxer=new MediaMuxer(fd.getFileDescriptor(),MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            MediaFormat fmt=MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC,720,1280);
            fmt.setInteger(MediaFormat.KEY_COLOR_FORMAT,MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
            fmt.setInteger(MediaFormat.KEY_BIT_RATE,3500000);
            fmt.setInteger(MediaFormat.KEY_FRAME_RATE,30);
            fmt.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL,2);
            codec=MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
            codec.configure(fmt,null,null,MediaCodec.CONFIGURE_FLAG_ENCODE);
            input=codec.createInputSurface();
            codec.start();
            RaceEngine race=new RaceEngine(racers);
            RaceRenderer renderer=new RaceRenderer();
            race.running=true;
            MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();
            int track=-1;
            long start=SystemClock.elapsedRealtime();
            int maxFrames=45*30;
            int lastReport=-1;
            for(int frame=0;frame<maxFrames;frame++){
                if(cancel.get())throw new InterruptedException("Exportação cancelada");
                race.advance(1f/30);
                Canvas canvas=null;
                try {
                    // Hardware Canvas draws directly into the H.264 encoder's input Surface.
                    canvas=input.lockHardwareCanvas();
                    renderer.render(canvas,race,true);
                }catch(Exception e){
                    throw new Exception("Este aparelho não aceita exportação direta. Use o gravador de tela do Android.",e);
                }finally{
                    if(canvas!=null)input.unlockCanvasAndPost(canvas);
                }
                boolean more=true;
                while(more){
                    int out=codec.dequeueOutputBuffer(info,0);
                    if(out==MediaCodec.INFO_TRY_AGAIN_LATER){more=false;}
                    else if(out==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){
                        if(started)throw new Exception("Formato do codificador alterado duas vezes");
                        track=muxer.addTrack(codec.getOutputFormat());muxer.start();started=true;
                    }else if(out>=0){
                        if(info.size>0){
                            if(!started)throw new Exception("Vídeo sem faixa válida");
                            ByteBuffer buf=codec.getOutputBuffer(out);
                            if(buf==null)throw new Exception("Buffer do vídeo ausente");
                            buf.position(info.offset);buf.limit(info.offset+info.size);
                            muxer.writeSampleData(track,buf,info);
                        }
                        codec.releaseOutputBuffer(out,false);
                    }
                }
                int percent=Math.min(99,(int)((frame+1)*100f/maxFrames));
                if(percent!=lastReport && frame%15==0){lastReport=percent;progress.update(percent);}
                // Frames use real presentation timestamps in surface mode.
                long next=start+(frame+1)*1000L/30;
                long wait=next-SystemClock.elapsedRealtime();
                if(wait>0)SystemClock.sleep(wait);
                if(race.finished==race.balls.size() && race.elapsed>10)break;
            }
            codec.signalEndOfInputStream();
            boolean eos=false;
            long until=SystemClock.elapsedRealtime()+18000;
            while(!eos && SystemClock.elapsedRealtime()<until){
                int out=codec.dequeueOutputBuffer(info,10000);
                if(out==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){
                    if(!started){track=muxer.addTrack(codec.getOutputFormat());muxer.start();started=true;}
                }else if(out>=0){
                    if(info.size>0){
                        if(!started)throw new Exception("Codificador sem saída");
                        ByteBuffer buf=codec.getOutputBuffer(out);
                        if(buf==null)throw new Exception("Codificador retornou buffer vazio");
                        buf.position(info.offset);buf.limit(info.offset+info.size);
                        muxer.writeSampleData(track,buf,info);
                    }
                    eos=(info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0;
                    codec.releaseOutputBuffer(out,false);
                }
            }
            if(!started||!eos)throw new Exception("A gravação não terminou corretamente.");
            progress.update(100);
            success=true;
        }finally{
            if(codec!=null){try{codec.stop();}catch(Exception ignored){}try{codec.release();}catch(Exception ignored){}}
            if(input!=null)try{input.release();}catch(Exception ignored){}
            if(muxer!=null){if(started)try{muxer.stop();}catch(Exception ignored){}try{muxer.release();}catch(Exception ignored){}}
            if(fd!=null)try{fd.close();}catch(Exception ignored){}
            if(success){ContentValues done=new ContentValues();done.put(MediaStore.Video.Media.IS_PENDING,0);resolver.update(uri,done,null,null);}
            else resolver.delete(uri,null,null);
        }
        return uri;
    }
}
