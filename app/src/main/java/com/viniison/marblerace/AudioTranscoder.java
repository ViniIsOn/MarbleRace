package com.viniison.marblerace;

import android.content.Context;
import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.net.Uri;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Converts user-supplied MP3 (or other system-decodable audio) into temporary
 * AAC/M4A using Android codecs, WITHOUT re-rendering the video.
 * Memory is bounded to 75 seconds of decoded stereo PCM.
 */
final class AudioTranscoder {
    private AudioTranscoder(){}
    private static final int MAX_SECONDS=75;

    static boolean isAac(Context context,Uri source)throws Exception {
        MediaExtractor extractor=new MediaExtractor();
        try{
            setSource(extractor,context,source);
            for(int i=0;i<extractor.getTrackCount();i++){
                String mime=extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME);
                if(mime!=null&&mime.startsWith("audio/"))
                    return MediaFormat.MIMETYPE_AUDIO_AAC.equals(mime);
            }
            throw new Exception("O arquivo não tem uma faixa de áudio.");
        }finally{extractor.release();}
    }
    static void setSource(MediaExtractor extractor,Context ctx,Uri source)throws Exception{
        if("file".equals(source.getScheme())){
            extractor.setDataSource(source.getPath());
        }else{
            extractor.setDataSource(ctx,source,null);
        }
    }
    private static int audioTrack(MediaExtractor extractor)throws Exception {
        for(int i=0;i<extractor.getTrackCount();i++){
            String mime=extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME);
            if(mime!=null&&mime.startsWith("audio/"))return i;
        }
        throw new Exception("Arquivo não contém áudio reconhecível.");
    }
    private static void appendPcm(ByteArrayOutputStream pcm,ByteBuffer src,int size,int encoding,
                                  int limit)throws Exception{
        if(size<=0)return;
        src.order(ByteOrder.LITTLE_ENDIAN);
        if(encoding==AudioFormat.ENCODING_PCM_16BIT){
            int n=Math.min(size,limit-pcm.size());
            if(n>0){byte[] bytes=new byte[n];src.get(bytes);pcm.write(bytes,0,n);}
        }else if(encoding==AudioFormat.ENCODING_PCM_FLOAT){
            int n=Math.min(size/4,(limit-pcm.size())/2);
            byte[] bytes=new byte[n*2];
            for(int i=0;i<n;i++){
                float v=src.getFloat();
                if(!Float.isFinite(v))v=0;
                short sample=(short)Math.round(Math.max(-1f,Math.min(1f,v))*32767f);
                bytes[i*2]=(byte)sample;
                bytes[i*2+1]=(byte)(sample>>>8);
            }
            pcm.write(bytes,0,bytes.length);
        }else throw new Exception("Formato PCM incompatível com o aparelho.");
    }
    static File convert(Context ctx,Uri source,AtomicBoolean cancel)throws Exception {
        MediaExtractor extractor=new MediaExtractor();
        MediaCodec decoder=null;
        ByteArrayOutputStream pcm=new ByteArrayOutputStream(1024*1024);
        int rate=44100,channels=2,limit=MAX_SECONDS*44100*2*2;
        try{
            setSource(extractor,ctx,source);
            int index=audioTrack(extractor);
            MediaFormat format=extractor.getTrackFormat(index);
            String mime=format.getString(MediaFormat.KEY_MIME);
            rate=format.containsKey(MediaFormat.KEY_SAMPLE_RATE)?format.getInteger(MediaFormat.KEY_SAMPLE_RATE):44100;
            channels=format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)?format.getInteger(MediaFormat.KEY_CHANNEL_COUNT):2;
            if(rate<8000||rate>96000||channels<1||channels>2)
                throw new Exception("Esta música usa um formato de canais não suportado.");
            limit=MAX_SECONDS*rate*channels*2;
            decoder=MediaCodec.createDecoderByType(mime);
            decoder.configure(format,null,null,0);
            decoder.start();
            extractor.selectTrack(index);
            boolean sentEnd=false,decodedEnd=false;
            int idle=0,encoding=AudioFormat.ENCODING_PCM_16BIT;
            MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();
            while(!decodedEnd && pcm.size()<limit) {
                if(cancel.get())throw new InterruptedException("Exportação cancelada");
                if(!sentEnd){
                    int in=decoder.dequeueInputBuffer(5000);
                    if(in>=0){
                        ByteBuffer buffer=decoder.getInputBuffer(in);
                        if(buffer==null)throw new Exception("Decoder sem entrada");
                        buffer.clear();
                        int count=extractor.readSampleData(buffer,0);
                        if(count<0){
                            decoder.queueInputBuffer(in,0,0,0,MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            sentEnd=true;
                        }else{
                            decoder.queueInputBuffer(in,0,count,Math.max(0,extractor.getSampleTime()),0);
                            extractor.advance();
                        }
                    }
                }
                int out=decoder.dequeueOutputBuffer(info,10000);
                if(out==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){
                    MediaFormat fmt=decoder.getOutputFormat();
                    if(fmt.containsKey(MediaFormat.KEY_SAMPLE_RATE))rate=fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                    if(fmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT))channels=fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                    if(fmt.containsKey(MediaFormat.KEY_PCM_ENCODING))
                        encoding=fmt.getInteger(MediaFormat.KEY_PCM_ENCODING);
                    if(rate<8000||rate>96000||channels<1||channels>2)
                        throw new Exception("Canal/Hz incompatível na conversão.");
                    limit=MAX_SECONDS*rate*channels*2;
                }else if(out==MediaCodec.INFO_TRY_AGAIN_LATER){
                    if(++idle>500)throw new Exception("O áudio demorou demais para decodificar.");
                }else if(out>=0){
                    idle=0;
                    if(info.size>0){
                        ByteBuffer bytes=decoder.getOutputBuffer(out);
                        if(bytes==null)throw new Exception("Decoder sem dados PCM");
                        bytes.position(info.offset);
                        bytes.limit(info.offset+info.size);
                        appendPcm(pcm,bytes,info.size,encoding,limit);
                    }
                    decodedEnd=(info.flags&MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0;
                    decoder.releaseOutputBuffer(out,false);
                }
            }
        }finally{
            if(decoder!=null){try{decoder.stop();}catch(Exception ignored){}decoder.release();}
            extractor.release();
        }
        byte[] raw=pcm.toByteArray();
        if(raw.length<channels*2*256)throw new Exception("Não foi possível decodificar a música.");
        File dst=File.createTempFile("marble_audio_",".m4a",ctx.getCacheDir());
        boolean done=false;
        MediaCodec encoder=null;
        MediaMuxer muxer=null;
        boolean muxing=false,encoderStarted=false;
        try{
            MediaFormat format=MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC,rate,channels);
            format.setInteger(MediaFormat.KEY_AAC_PROFILE,MediaCodecInfo.CodecProfileLevel.AACObjectLC);
            format.setInteger(MediaFormat.KEY_BIT_RATE,128000);
            format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE,32768);
            encoder=MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC);
            encoder.configure(format,null,null,MediaCodec.CONFIGURE_FLAG_ENCODE);
            muxer=new MediaMuxer(dst.getAbsolutePath(),MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            encoder.start();encoderStarted=true;
            int cursor=0,track=-1,idle=0;
            boolean sentEnd=false,receivedEnd=false;
            MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();
            final int align=channels*2;
            while(!receivedEnd){
                if(cancel.get())throw new InterruptedException("Exportação cancelada");
                if(!sentEnd){
                    int index=encoder.dequeueInputBuffer(5000);
                    if(index>=0){
                        ByteBuffer buffer=encoder.getInputBuffer(index);
                        if(buffer==null)throw new Exception("Encoder sem entrada");
                        buffer.clear();
                        int count=Math.min(buffer.remaining(),raw.length-cursor);
                        count-=count%align;
                        long pts=(long)cursor*1000000L/(rate*align);
                        if(count>0){
                            buffer.put(raw,cursor,count);
                            encoder.queueInputBuffer(index,0,count,pts,0);
                            cursor+=count;
                        }else{
                            encoder.queueInputBuffer(index,0,0,pts,MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            sentEnd=true;
                        }
                    }
                }
                int out=encoder.dequeueOutputBuffer(info,10000);
                if(out==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){
                    if(muxing)throw new Exception("Formato AAC alterado");
                    track=muxer.addTrack(encoder.getOutputFormat());
                    muxer.start();muxing=true;
                }else if(out==MediaCodec.INFO_TRY_AGAIN_LATER){
                    if(++idle>500)throw new Exception("O encoder de áudio não respondeu.");
                }else if(out>=0){
                    idle=0;
                    if(info.size>0&&(info.flags&MediaCodec.BUFFER_FLAG_CODEC_CONFIG)==0){
                        if(!muxing)throw new Exception("Container AAC ainda não iniciado");
                        ByteBuffer buffer=encoder.getOutputBuffer(out);
                        if(buffer==null)throw new Exception("Encoder AAC sem saída");
                        buffer.position(info.offset);buffer.limit(info.offset+info.size);
                        muxer.writeSampleData(track,buffer,info);
                    }
                    receivedEnd=(info.flags&MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0;
                    encoder.releaseOutputBuffer(out,false);
                }
            }
            muxer.stop();muxing=false;
            done=true;
            return dst;
        }finally{
            if(encoder!=null){if(encoderStarted)try{encoder.stop();}catch(Exception ignored){}encoder.release();}
            if(muxer!=null){if(muxing)try{muxer.stop();}catch(Exception ignored){}muxer.release();}
            if(!done)dst.delete();
        }
    }
}
