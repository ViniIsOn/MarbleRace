package com.viniison.marblerace;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Base64;
import android.util.AtomicFile;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Portable creator backup: names, colors, selected game and real avatar bytes.
 *  Stored as a user-chosen JSON document; survives app removal if saved outside it.
 */
final class RacerBackup {
    static final int SAVE_REQUEST=701,RESTORE_REQUEST=702;
    private static final int MAX_BYTES=18*1024*1024;
    private RacerBackup(){}
    static final class Loaded {
        final ArrayList<RaceEngine.Racer> racers;
        final int mode,track;
        Loaded(ArrayList<RaceEngine.Racer> racers,int mode,int track){
            this.racers=racers;this.mode=mode;this.track=track;
        }
    }
    static JSONObject encode(List<RaceEngine.Racer> racers,int mode,int track)throws Exception{
        JSONObject root=new JSONObject();
        root.put("format","MarbleLab-creator-backup");
        root.put("version",1);
        root.put("mode",mode);root.put("track",track);
        JSONArray list=new JSONArray();
        for(RaceEngine.Racer r:racers){
            JSONObject item=new JSONObject();
            item.put("name",r.name);
            item.put("color",r.color);
            Bitmap bitmap=r.avatar;
            if(bitmap==null && r.imagePath!=null&&!r.imagePath.isEmpty())
                bitmap=BitmapFactory.decodeFile(r.imagePath);
            if(bitmap!=null&&!bitmap.isRecycled()){
                ByteArrayOutputStream buffer=new ByteArrayOutputStream();
                int w=bitmap.getWidth(),h=bitmap.getHeight();
                float scale=Math.min(1f,384f/Math.max(w,h));
                Bitmap small=scale<1?
                    Bitmap.createScaledBitmap(bitmap,Math.max(1,Math.round(w*scale)),
                        Math.max(1,Math.round(h*scale)),true):bitmap;
                small.compress(Bitmap.CompressFormat.PNG,100,buffer);
                item.put("avatarPng",Base64.encodeToString(buffer.toByteArray(),Base64.NO_WRAP));
                if(small!=bitmap)small.recycle();
            }
            list.put(item);
        }
        root.put("racers",list);
        return root;
    }
    /** Automatic private recovery snapshot, saved after editing a character.
     *  Survives updates. User-exported backup is required before uninstalling.
     */
    static void autoSave(Context ctx,List<RaceEngine.Racer> racers,int mode,int track)throws Exception{
        AtomicFile store=new AtomicFile(new File(ctx.getFilesDir(),"creator_autosave.json"));
        byte[] bytes=encode(racers,mode,track).toString().getBytes(StandardCharsets.UTF_8);
        if(bytes.length>MAX_BYTES)throw new Exception("Snapshot grande demais");
        FileOutputStream out=null;
        try{
            out=store.startWrite();
            out.write(bytes);
            store.finishWrite(out);
        }catch(Exception problem){
            if(out!=null)store.failWrite(out);
            throw problem;
        }
    }
    static Loaded autoRestore(Context ctx)throws Exception{
        AtomicFile store=new AtomicFile(new File(ctx.getFilesDir(),"creator_autosave.json"));
        if(!store.getBaseFile().exists())return null;
        try(InputStream input=store.openRead()){
            return decode(ctx,readLimited(input));
        }
    }
    static void write(Context ctx,Uri file,List<RaceEngine.Racer> racers,int mode,int track)throws Exception{
        byte[] bytes=encode(racers,mode,track).toString().getBytes(StandardCharsets.UTF_8);
        if(bytes.length>MAX_BYTES)throw new Exception("Backup muito grande");
        try(OutputStream output=ctx.getContentResolver().openOutputStream(file,"wt")){
            if(output==null)throw new Exception("Sem permissão para salvar documento");
            output.write(bytes);output.flush();
        }
    }
    static Loaded read(Context ctx,Uri uri)throws Exception{
        byte[] raw;
        try(InputStream input=ctx.getContentResolver().openInputStream(uri)){
            raw=readLimited(input);
        }
        return decode(ctx,raw);
    }
    private static byte[] readLimited(InputStream in)throws Exception{
        if(in==null)throw new Exception("Arquivo não encontrado");
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        byte[] buffer=new byte[8192];int n;
        while((n=in.read(buffer))!=-1){
            if(out.size()+n>MAX_BYTES)throw new Exception("Backup excede 18 MB");
            out.write(buffer,0,n);
        }
        return out.toByteArray();
    }
    static Loaded decode(Context ctx,byte[] bytes)throws Exception{
        JSONObject root=new JSONObject(new String(bytes,StandardCharsets.UTF_8));
        if(!"MarbleLab-creator-backup".equals(root.optString("format")))
            throw new Exception("Esse arquivo não é um backup MarbleLab");
        if(root.optInt("version")!=1)throw new Exception("Formato de backup incompatível");
        JSONArray array=root.optJSONArray("racers");
        if(array==null||array.length()<2||array.length()>8)
            throw new Exception("É necessário ter entre 2 e 8 corredores");
        ArrayList<RaceEngine.Racer> racers=new ArrayList<>();
        File dir=new File(ctx.getFilesDir(),"avatars");
        if(!dir.exists()&&!dir.mkdirs())throw new Exception("Não consegui salvar as imagens");
        for(int i=0;i<array.length();i++){
            JSONObject item=array.getJSONObject(i);
            String name=item.optString("name","CORREDOR "+(i+1)).trim();
            if(name.isEmpty())name="CORREDOR "+(i+1);
            if(name.length()>24)name=name.substring(0,24);
            String filePath="";
            Bitmap bitmap=null;
            String data=item.optString("avatarPng","");
            if(!data.isEmpty()){
                if(data.length()>2500000)throw new Exception("Imagem do corredor muito grande");
                byte[] png=Base64.decode(data,Base64.DEFAULT);
                BitmapFactory.Options bounds=new BitmapFactory.Options();
                bounds.inJustDecodeBounds=true;
                BitmapFactory.decodeByteArray(png,0,png.length,bounds);
                if(bounds.outWidth<1||bounds.outHeight<1||bounds.outWidth>4096||bounds.outHeight>4096)
                    throw new Exception("Imagem inválida no backup");
                BitmapFactory.Options opts=new BitmapFactory.Options();
                opts.inSampleSize=1;
                while(Math.max(bounds.outWidth,bounds.outHeight)/opts.inSampleSize>512)
                    opts.inSampleSize*=2;
                bitmap=BitmapFactory.decodeByteArray(png,0,png.length,opts);
                if(bitmap==null)throw new Exception("Não consegui restaurar a imagem");
                File image=new File(dir,"restored_"+System.nanoTime()+"_"+i+".png");
                try(FileOutputStream out=new FileOutputStream(image)){
                    if(!bitmap.compress(Bitmap.CompressFormat.PNG,100,out))
                        throw new Exception("Não consegui restaurar a imagem PNG");
                }
                filePath=image.getAbsolutePath();
            }
            racers.add(new RaceEngine.Racer(name,filePath,bitmap,
                item.optInt("color",RaceEngine.PALETTE[i])));
        }
        int mode=Math.max(0,Math.min(ModeEngine.NAMES.length,root.optInt("mode",1)));
        int track=Math.max(0,Math.min(RaceEngine.TRACK_NAMES.length-1,root.optInt("track",0)));
        return new Loaded(racers,mode,track);
    }
}
