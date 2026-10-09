package com.viniison.marblerace;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.net.Uri;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Fast remux, without decoding/re-encoding video or audio.
 * AAC in M4A/MP4/ADTS files is compatible with Android's MP4 MediaMuxer.
 * MP3 and other codecs need transcoding and are intentionally rejected instead
 * of producing corrupt or silently muted MP4s.
 */
final class MusicMuxer {
    private MusicMuxer(){}
    private static final int BUFFER=2*1024*1024;
    private static int findTrack(MediaExtractor extractor,String prefix){
        for(int i=0;i<extractor.getTrackCount();i++){
            String mime=extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME);
            if(mime!=null && mime.startsWith(prefix))return i;
        }
        return -1;
    }
    static Uri addMusic(Context ctx,Uri video,Uri audio,AtomicBoolean cancel)throws Exception{
        MediaExtractor videoExt=new MediaExtractor(),audioExt=new MediaExtractor();
        MediaMuxer muxer=null;
        ParcelFileDescriptor descriptor=null;
        Uri output=null;
        boolean started=false,success=false;
        try{
            videoExt.setDataSource(ctx,video,null);
            audioExt.setDataSource(ctx,audio,null);
            int v=findTrack(videoExt,"video/");
            int a=findTrack(audioExt,"audio/");
            if(v<0 || a<0)throw new IllegalArgumentException("Vídeo ou faixa de música inválidos");
            MediaFormat vf=videoExt.getTrackFormat(v),af=audioExt.getTrackFormat(a);
            String mime=af.getString(MediaFormat.KEY_MIME);
            if(!MediaFormat.MIMETYPE_AUDIO_AAC.equals(mime))
                throw new IllegalArgumentException("A música foi importada, mas para incluir no MP4 use M4A/AAC. MP3 toca no app; adicione MP3 ao Short no editor.");
            long durationUs=vf.containsKey(MediaFormat.KEY_DURATION)?
                vf.getLong(MediaFormat.KEY_DURATION):0L;
            if(durationUs<1000){
                videoExt.selectTrack(v);
                long last=0;
                while(videoExt.getSampleTrackIndex()>=0){
                    if(cancel.get())throw new InterruptedException("Exportação cancelada");
                    last=Math.max(last,videoExt.getSampleTime());
                    if(!videoExt.advance())break;
                }
                durationUs=last+33334;
                videoExt.unselectTrack(v);
                videoExt.seekTo(0,MediaExtractor.SEEK_TO_CLOSEST_SYNC);
            }
            ContentResolver resolver=ctx.getContentResolver();
            ContentValues val=new ContentValues();
            val.put(MediaStore.Video.Media.DISPLAY_NAME,"MarbleLab_Music_"+System.currentTimeMillis()+".mp4");
            val.put(MediaStore.Video.Media.MIME_TYPE,"video/mp4");
            val.put(MediaStore.Video.Media.RELATIVE_PATH,Environment.DIRECTORY_MOVIES+"/MarbleRace");
            val.put(MediaStore.Video.Media.IS_PENDING,1);
            output=resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,val);
            if(output==null)throw new IllegalStateException("Sem espaço para criar vídeo com música");
            descriptor=resolver.openFileDescriptor(output,"rw");
            if(descriptor==null)throw new IllegalStateException("Arquivo de vídeo com música inacessível");
            muxer=new MediaMuxer(descriptor.getFileDescriptor(),MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            int videoTrack=muxer.addTrack(vf);
            int audioTrack=muxer.addTrack(af);
            muxer.start();started=true;
            ByteBuffer data=ByteBuffer.allocateDirect(BUFFER);
            MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();
            videoExt.selectTrack(v);
            while(true){
                if(cancel.get())throw new InterruptedException("Exportação cancelada");
                data.clear();
                int size=videoExt.readSampleData(data,0);
                if(size<0)break;
                info.offset=0;info.size=size;info.presentationTimeUs=videoExt.getSampleTime();
                info.flags=videoExt.getSampleFlags();
                muxer.writeSampleData(videoTrack,data,info);
                if(!videoExt.advance())break;
            }
            audioExt.selectTrack(a);
            long offset=0,prevTs=-1;
            int loops=0;
            while(offset<durationUs && loops<100){
                if(cancel.get())throw new InterruptedException("Exportação cancelada");
                data.clear();
                int size=audioExt.readSampleData(data,0);
                if(size<0){
                    if(prevTs<0)break;
                    offset+=prevTs+23000;
                    prevTs=-1;
                    loops++;
                    audioExt.seekTo(0,MediaExtractor.SEEK_TO_CLOSEST_SYNC);
                    continue;
                }
                long sampleTime=Math.max(0,audioExt.getSampleTime());
                if(offset+sampleTime>=durationUs)break;
                info.offset=0;info.size=size;info.presentationTimeUs=offset+sampleTime;
                info.flags=audioExt.getSampleFlags();
                muxer.writeSampleData(audioTrack,data,info);
                prevTs=sampleTime;
                if(!audioExt.advance()){
                    offset+=sampleTime+23000;
                    prevTs=-1;
                    loops++;
                    audioExt.seekTo(0,MediaExtractor.SEEK_TO_CLOSEST_SYNC);
                }
            }
            success=true;
            return output;
        }finally{
            if(muxer!=null){
                if(started)try{muxer.stop();}catch(Exception ignored){}
                try{muxer.release();}catch(Exception ignored){}
            }
            if(descriptor!=null)try{descriptor.close();}catch(Exception ignored){}
            videoExt.release();audioExt.release();
            if(output!=null){
                if(success){
                    ContentValues done=new ContentValues();done.put(MediaStore.Video.Media.IS_PENDING,0);
                    ctx.getContentResolver().update(output,done,null,null);
                    // Remove temporary silent MP4 only after music video was created.
                    ctx.getContentResolver().delete(video,null,null);
                }else ctx.getContentResolver().delete(output,null,null);
            }
        }
    }
}
