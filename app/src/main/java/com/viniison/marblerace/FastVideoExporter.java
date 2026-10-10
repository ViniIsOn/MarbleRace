package com.viniison.marblerace;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.net.Uri;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLExt;
import android.opengl.EGLSurface;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.os.Build;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.view.Surface;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Fast export path: Canvas -> native Bitmap texture upload -> OpenGL ES ->
 * MediaCodec input Surface. Android's video hardware performs RGBA -> YUV
 * instead of the old pixel-by-pixel Java conversion.
 */
final class FastVideoExporter {
    private static final int VIDEO_W=720, VIDEO_H=1280, RENDER_W=540, RENDER_H=960, FPS=30;
    private FastVideoExporter(){}
    private static final class GlEncoder implements AutoCloseable {
        private EGLDisplay display=EGL14.EGL_NO_DISPLAY;
        private EGLContext context=EGL14.EGL_NO_CONTEXT;
        private EGLSurface window=EGL14.EGL_NO_SURFACE;
        private int program,texture,position,texCoord,uniform;
        private final FloatBuffer vertices;
        GlEncoder(Surface surface) {
            float[] quad={
                -1f,-1f, 0f,1f,
                 1f,-1f, 1f,1f,
                -1f, 1f, 0f,0f,
                 1f, 1f, 1f,0f
            };
            vertices=ByteBuffer.allocateDirect(quad.length*4)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
            vertices.put(quad).position(0);
            display=EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
            int[] version=new int[2];
            if(display==EGL14.EGL_NO_DISPLAY||!EGL14.eglInitialize(display,version,0,version,1))
                throw new IllegalStateException("EGL indisponível");
            int[] attrs={
                EGL14.EGL_RED_SIZE,8,EGL14.EGL_GREEN_SIZE,8,
                EGL14.EGL_BLUE_SIZE,8,EGL14.EGL_ALPHA_SIZE,8,
                EGL14.EGL_RENDERABLE_TYPE,EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_SURFACE_TYPE,EGL14.EGL_WINDOW_BIT,
                EGL14.EGL_NONE
            };
            EGLConfig[] configs=new EGLConfig[1];int[] number=new int[1];
            if(!EGL14.eglChooseConfig(display,attrs,0,configs,0,1,number,0)||number[0]<1)
                throw new IllegalStateException("Configuração EGL não encontrada");
            context=EGL14.eglCreateContext(display,configs[0],EGL14.EGL_NO_CONTEXT,
                new int[]{EGL14.EGL_CONTEXT_CLIENT_VERSION,2,EGL14.EGL_NONE},0);
            if(context==EGL14.EGL_NO_CONTEXT)throw new IllegalStateException("Contexto GLES2 indisponível");
            window=EGL14.eglCreateWindowSurface(display,configs[0],surface,
                new int[]{EGL14.EGL_NONE},0);
            if(window==EGL14.EGL_NO_SURFACE||!EGL14.eglMakeCurrent(display,window,window,context))
                throw new IllegalStateException("Não foi possível conectar GPU ao encoder");
            int vertex=shader(GLES20.GL_VERTEX_SHADER,
                "attribute vec2 aPosition; attribute vec2 aTexture;"+
                "varying vec2 vTexture; void main(){gl_Position=vec4(aPosition,0.0,1.0);vTexture=aTexture;}");
            int fragment=shader(GLES20.GL_FRAGMENT_SHADER,
                "precision mediump float; varying vec2 vTexture; uniform sampler2D uImage;"+
                "void main(){gl_FragColor=texture2D(uImage,vTexture);}");
            program=GLES20.glCreateProgram();
            GLES20.glAttachShader(program,vertex);
            GLES20.glAttachShader(program,fragment);
            GLES20.glLinkProgram(program);
            int[] linked=new int[1];
            GLES20.glGetProgramiv(program,GLES20.GL_LINK_STATUS,linked,0);
            GLES20.glDeleteShader(vertex);GLES20.glDeleteShader(fragment);
            if(linked[0]!=GLES20.GL_TRUE)throw new IllegalStateException("Falha no shader de vídeo");
            position=GLES20.glGetAttribLocation(program,"aPosition");
            texCoord=GLES20.glGetAttribLocation(program,"aTexture");
            uniform=GLES20.glGetUniformLocation(program,"uImage");
            int[] tex=new int[1];GLES20.glGenTextures(1,tex,0);
            texture=tex[0];if(texture==0)throw new IllegalStateException("Textura GLES indisponível");
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,texture);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glViewport(0,0,VIDEO_W,VIDEO_H);
        }
        private int shader(int kind,String source) {
            int obj=GLES20.glCreateShader(kind);
            GLES20.glShaderSource(obj,source);GLES20.glCompileShader(obj);
            int[] ok=new int[1];GLES20.glGetShaderiv(obj,GLES20.GL_COMPILE_STATUS,ok,0);
            if(ok[0]!=GLES20.GL_TRUE)throw new IllegalStateException("Shader GLES inválido");
            return obj;
        }
        void draw(Bitmap bmp,int frame) {
            GLES20.glUseProgram(program);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,texture);
            if(frame==0) GLUtils.texImage2D(GLES20.GL_TEXTURE_2D,0,bmp,0);
            else GLUtils.texSubImage2D(GLES20.GL_TEXTURE_2D,0,0,0,bmp);
            GLES20.glUniform1i(uniform,0);
            GLES20.glClearColor(0f,0f,0f,1f);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
            vertices.position(0);
            GLES20.glEnableVertexAttribArray(position);
            GLES20.glVertexAttribPointer(position,2,GLES20.GL_FLOAT,false,16,vertices);
            vertices.position(2);
            GLES20.glEnableVertexAttribArray(texCoord);
            GLES20.glVertexAttribPointer(texCoord,2,GLES20.GL_FLOAT,false,16,vertices);
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4);
            GLES20.glDisableVertexAttribArray(position);GLES20.glDisableVertexAttribArray(texCoord);
            int err=GLES20.glGetError();
            if(err!=GLES20.GL_NO_ERROR)throw new IllegalStateException("Falha OpenGL "+err);
            EGLExt.eglPresentationTimeANDROID(display,window,frame*1000000000L/FPS);
            if(!EGL14.eglSwapBuffers(display,window))
                throw new IllegalStateException("Não foi possível enviar quadro à GPU");
        }
        @Override public void close(){
            if(display!=EGL14.EGL_NO_DISPLAY){
                if(context!=EGL14.EGL_NO_CONTEXT && window!=EGL14.EGL_NO_SURFACE){
                    GLES20.glDeleteTextures(1,new int[]{texture},0);
                    GLES20.glDeleteProgram(program);
                }
                EGL14.eglMakeCurrent(display,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_CONTEXT);
                if(window!=EGL14.EGL_NO_SURFACE)EGL14.eglDestroySurface(display,window);
                if(context!=EGL14.EGL_NO_CONTEXT)EGL14.eglDestroyContext(display,context);
                EGL14.eglTerminate(display);
                display=EGL14.EGL_NO_DISPLAY;
            }
        }
    }
    private static class Muxing {
        boolean started=false,eos=false;
        int track=-1;
    }
    private static void drain(MediaCodec codec,MediaMuxer muxer,Muxing state,
                              MediaCodec.BufferInfo info,boolean waitEnd)throws Exception{
        int empty=0;
        while(true) {
            int index=codec.dequeueOutputBuffer(info,waitEnd?10000:0);
            if(index==MediaCodec.INFO_TRY_AGAIN_LATER){
                if(!waitEnd)break;
                if(++empty>700)throw new Exception("O codificador de vídeo não terminou");
            }else if(index==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){
                if(state.started)throw new Exception("Formato do encoder mudou");
                state.track=muxer.addTrack(codec.getOutputFormat());muxer.start();state.started=true;
            }else if(index>=0){
                if(info.size>0&&(info.flags&MediaCodec.BUFFER_FLAG_CODEC_CONFIG)==0){
                    if(!state.started)throw new Exception("MP4 não iniciado");
                    ByteBuffer frame=codec.getOutputBuffer(index);
                    if(frame==null)throw new Exception("Buffer H.264 indisponível");
                    frame.position(info.offset);
                    frame.limit(info.offset+info.size);
                    muxer.writeSampleData(state.track,frame,info);
                }
                if((info.flags&MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0)state.eos=true;
                codec.releaseOutputBuffer(index,false);
                if(state.eos)break;
            }
        }
    }

    private static MediaCodec selectHardwareEncoder(MediaFormat format)throws Exception{
        Exception last=null;
        for(MediaCodecInfo info:new MediaCodecList(MediaCodecList.REGULAR_CODECS).getCodecInfos()){
            if(!info.isEncoder()||info.isSoftwareOnly())continue;
            boolean avc=false;
            for(String mime:info.getSupportedTypes())if(MediaFormat.MIMETYPE_VIDEO_AVC.equalsIgnoreCase(mime))avc=true;
            if(!avc)continue;
            try{
                boolean supportsSurface=false;
                for(int c:info.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_AVC).colorFormats)
                    if(c==MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)supportsSurface=true;
                if(!supportsSurface)continue;
                MediaCodec encoder=MediaCodec.createByCodecName(info.getName());
                try{encoder.configure(format,null,null,MediaCodec.CONFIGURE_FLAG_ENCODE);return encoder;}
                catch(Exception e){encoder.release();throw e;}
            }catch(Exception e){last=e;}
        }
        throw new IllegalStateException("Não há encoder H.264 acelerado com entrada Surface",last);
    }
    static Uri export(Context ctx,List<RaceEngine.Racer> racers,int track,int mode,long seed,
                      VideoExporter.Progress progress,AtomicBoolean cancel)throws Exception{
        if(Build.VERSION.SDK_INT<29)throw new IllegalStateException("Exportação acelerada requer Android 10+");
        progress.stage("Aceleração por GPU e codificador H.264");
        MediaFormat format=MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC,VIDEO_W,VIDEO_H);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT,MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        format.setInteger(MediaFormat.KEY_BIT_RATE,3000000);
        format.setInteger(MediaFormat.KEY_FRAME_RATE,FPS);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL,2);
        MediaCodec codec=null;
        Surface input=null;
        GlEncoder gl=null;
        ParcelFileDescriptor descriptor=null;
        MediaMuxer muxer=null;
        Bitmap bitmap=null;
        Uri uri=null;
        Muxing state=new Muxing();
        boolean started=false,success=false;
        try{
            codec=selectHardwareEncoder(format);
            input=codec.createInputSurface();
            gl=new GlEncoder(input);
            codec.start();
            started=true;
            ContentResolver resolver=ctx.getContentResolver();
            ContentValues values=new ContentValues();
            values.put(MediaStore.Video.Media.DISPLAY_NAME,"MarbleLab_FAST_"+System.currentTimeMillis()+".mp4");
            values.put(MediaStore.Video.Media.MIME_TYPE,"video/mp4");
            values.put(MediaStore.Video.Media.RELATIVE_PATH,Environment.DIRECTORY_MOVIES+"/MarbleRace");
            values.put(MediaStore.Video.Media.IS_PENDING,1);
            uri=resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,values);
            if(uri==null)throw new IllegalStateException("Não consegui criar o arquivo de vídeo");
            descriptor=resolver.openFileDescriptor(uri,"rw");
            if(descriptor==null)throw new IllegalStateException("Sem acesso ao arquivo MP4");
            muxer=new MediaMuxer(descriptor.getFileDescriptor(),MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            RaceEngine race=mode==0?new RaceEngine(racers,track,seed):null;
            ModeEngine mini=mode==0?null:new ModeEngine(racers,mode-1,seed);
            RaceRenderer renderer=new RaceRenderer();
            ModeRenderer modeRenderer=new ModeRenderer();
            if(race!=null)race.running=true;
            if(mini!=null)mini.running=true;
            bitmap=Bitmap.createBitmap(RENDER_W,RENDER_H,Bitmap.Config.ARGB_8888);
            Canvas canvas=new Canvas(bitmap);
            MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();
            int podium=0,frameCount=0;
            final int maxFrames=(mode==0||mode==1?70:45)*FPS;
            for(int frame=0;frame<maxFrames;frame++){
                if(cancel.get())throw new InterruptedException("Exportação cancelada");
                if(race!=null){race.advance(1f/FPS);renderer.render(canvas,race,true);}
                else{mini.advance(1f/FPS);modeRenderer.render(canvas,mini,true);}
                drain(codec,muxer,state,info,false);
                gl.draw(bitmap,frame);
                drain(codec,muxer,state,info,false);
                frameCount=frame+1;
                if(frame%12==0)progress.update(Math.min(99,(int)(100L*frameCount/maxFrames)));
                boolean classicDone=race!=null&&race.finished==race.balls.size()&&race.elapsed>=10;
                boolean miniDone=mini!=null&&mini.winner()!=null&&mini.elapsed>=5
                        &&(mini.mode<=ModeEngine.CORE||mini.finished==mini.orbs.size());
                if(classicDone||miniDone){
                    if(++podium>=45)break;
                }else podium=0;
            }
            codec.signalEndOfInputStream();
            drain(codec,muxer,state,info,true);
            if(!state.started||!state.eos)throw new Exception("Não finalizei o MP4");
            progress.update(100);
            success=true;
            return uri;
        }finally{
            if(bitmap!=null)bitmap.recycle();
            if(gl!=null)try{gl.close();}catch(Exception ignored){}
            if(input!=null)try{input.release();}catch(Exception ignored){}
            if(codec!=null){
                if(started)try{codec.stop();}catch(Exception ignored){}
                try{codec.release();}catch(Exception ignored){}
            }
            if(muxer!=null){
                if(state.started)try{muxer.stop();}catch(Exception ignored){}
                try{muxer.release();}catch(Exception ignored){}
            }
            if(descriptor!=null)try{descriptor.close();}catch(Exception ignored){}
            if(uri!=null){
                ContentResolver resolver=ctx.getContentResolver();
                if(success){
                    ContentValues done=new ContentValues();done.put(MediaStore.Video.Media.IS_PENDING,0);
                    resolver.update(uri,done,null,null);
                }else resolver.delete(uri,null,null);
            }
        }
    }
}
