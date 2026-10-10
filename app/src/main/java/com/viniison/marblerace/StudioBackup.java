package com.viniison.marblerace;

import android.content.Context;
import android.net.Uri;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/** Portable ZIP containing the original racer images AND imported music bytes.
 * No external music downloads. Files must already be accessible to the user.
 * Streams media instead of putting hundreds of songs into a huge JSON/base64 blob.
 */
final class StudioBackup {
    private StudioBackup(){}
    private static final int META_LIMIT=18*1024*1024;
    private static final long PER_TRACK_LIMIT=64L*1024*1024;
    private static final long ARCHIVE_LIMIT=1600L*1024*1024;
    static final class Loaded {
        final RacerBackup.Loaded creator;
        final ArrayList<MusicLibrary.Track> music;
        final int selected;
        final boolean shuffle,hasMusic;
        Loaded(RacerBackup.Loaded creator,ArrayList<MusicLibrary.Track> tracks,
               int selected,boolean shuffle,boolean includesMusic){
            this.creator=creator;this.music=tracks;this.selected=selected;
            this.shuffle=shuffle;this.hasMusic=includesMusic;
        }
    }
    static void write(Context ctx,Uri destination,List<RaceEngine.Racer> racers,
                      int mode,int track,MusicLibrary library)throws Exception{
        ArrayList<MusicLibrary.Track> songs=library.snapshot();
        if(songs.size()>300)throw new Exception("Músicas demais para o backup");
        JSONObject manifest=new JSONObject();
        manifest.put("format","MarbleLab-studio-zip");
        manifest.put("version",2);
        manifest.put("creator",RacerBackup.encode(racers,mode,track));
        manifest.put("selected",library.selectedIndex());
        manifest.put("shuffle",library.isShuffle());
        JSONArray audio=new JSONArray();
        for(int i=0;i<songs.size();i++){
            JSONObject item=new JSONObject();
            item.put("title",songs.get(i).title);
            item.put("entry","audio/"+i+".bin");
            audio.put(item);
        }
        manifest.put("audio",audio);
        byte[] json=manifest.toString().getBytes(StandardCharsets.UTF_8);
        if(json.length>META_LIMIT)throw new Exception("Informações do backup muito grandes");
        try(OutputStream stream=ctx.getContentResolver().openOutputStream(destination,"wt")){
            if(stream==null)throw new Exception("Não foi possível abrir o arquivo de backup");
            try(ZipOutputStream zip=new ZipOutputStream(stream)){
                zip.setLevel(0); // compressed music does not benefit from additional compression
                zip.putNextEntry(new ZipEntry("manifest.json"));
                zip.write(json);
                zip.closeEntry();
                byte[] buffer=new byte[32768];
                long total=0;
                for(int i=0;i<songs.size();i++){
                    MusicLibrary.Track song=songs.get(i);
                    zip.putNextEntry(new ZipEntry("audio/"+i+".bin"));
                    long one=0;
                    try(InputStream input=ctx.getContentResolver()
                        .openInputStream(Uri.parse(song.uri))){
                        if(input==null)throw new Exception("Música indisponível: "+song.title);
                        int n;
                        while((n=input.read(buffer))!=-1){
                            one+=n;total+=n;
                            if(one>PER_TRACK_LIMIT||total>ARCHIVE_LIMIT)
                                throw new Exception("Backup excedeu o limite de tamanho; reduza a lista musical.");
                            zip.write(buffer,0,n);
                        }
                    }catch(Exception e){
                        throw new Exception("Não foi possível incluir '"+song.title+"': "+e.getMessage(),e);
                    }
                    if(one==0)throw new Exception("Arquivo de música vazio: "+song.title);
                    zip.closeEntry();
                }
            }
        }
    }
    private static byte[] readMeta(ZipInputStream zip)throws Exception{
        ByteArrayOutputStream output=new ByteArrayOutputStream();
        byte[] buf=new byte[8192];int n;
        while((n=zip.read(buf))!=-1){
            if(output.size()+n>META_LIMIT)throw new Exception("Manifesto do backup muito grande");
            output.write(buf,0,n);
        }
        return output.toByteArray();
    }
    static Loaded read(Context ctx,Uri uri)throws Exception {
        try(InputStream raw=ctx.getContentResolver().openInputStream(uri)){
            if(raw==null)throw new Exception("Não foi possível ler o backup");
            BufferedInputStream buffered=new BufferedInputStream(raw);
            buffered.mark(8);
            int a=buffered.read(),b=buffered.read(),c=buffered.read(),d=buffered.read();
            buffered.reset();
            if(a!=0x50||b!=0x4b||c!=0x03||d!=0x04){
                // Import old v1 JSON backups without losing the existing music library.
                return new Loaded(RacerBackup.read(ctx,uri),new ArrayList<>(),-1,false,false);
            }
            ArrayList<File> written=new ArrayList<>();
            try(ZipInputStream zip=new ZipInputStream(buffered)){
                ZipEntry first=zip.getNextEntry();
                if(first==null||!"manifest.json".equals(first.getName()))
                    throw new Exception("Backup ZIP sem manifesto válido");
                JSONObject manifest=new JSONObject(new String(readMeta(zip),StandardCharsets.UTF_8));
                if(!"MarbleLab-studio-zip".equals(manifest.optString("format"))||
                    manifest.optInt("version")!=2)
                    throw new Exception("Formato de backup não reconhecido");
                JSONObject creator=manifest.getJSONObject("creator");
                JSONArray songs=manifest.getJSONArray("audio");
                if(songs.length()>300)throw new Exception("Há mais de 300 músicas no backup");
                File dir=new File(ctx.getFilesDir(),"restored_music");
                if(!dir.exists()&&!dir.mkdirs())throw new Exception("Sem espaço para restaurar músicas");
                ArrayList<MusicLibrary.Track> imported=new ArrayList<>();
                byte[] buffer=new byte[32768];
                long total=0;
                try{
                    for(int i=0;i<songs.length();i++){
                        JSONObject details=songs.getJSONObject(i);
                        String expected="audio/"+i+".bin";
                        if(!expected.equals(details.optString("entry")))
                            throw new Exception("Ordem ou nome da faixa inválido");
                        ZipEntry entry=zip.getNextEntry();
                        if(entry==null||!expected.equals(entry.getName())||entry.isDirectory())
                            throw new Exception("Música ausente no arquivo: "+i);
                        String title=details.optString("title","Faixa "+(i+1));
                        if(title.length()>150)title=title.substring(0,150);
                        String ext=".m4a";
                        String lower=title.toLowerCase(java.util.Locale.ROOT);
                        if(lower.endsWith(".mp3"))ext=".mp3";
                        else if(lower.endsWith(".wav"))ext=".wav";
                        else if(lower.endsWith(".ogg"))ext=".ogg";
                        else if(lower.endsWith(".flac"))ext=".flac";
                        File destination=File.createTempFile("track_"+i+"_",ext,dir);
                        written.add(destination);
                        long size=0;
                        try(FileOutputStream out=new FileOutputStream(destination)){
                            int n;
                            while((n=zip.read(buffer))!=-1){
                                size+=n;total+=n;
                                if(size>PER_TRACK_LIMIT||total>ARCHIVE_LIMIT)
                                    throw new Exception("Backup musical excedeu o tamanho permitido.");
                                out.write(buffer,0,n);
                            }
                        }
                        if(size==0)throw new Exception("Música vazia: "+title);
                        imported.add(new MusicLibrary.Track(title,Uri.fromFile(destination).toString()));
                    }
                    if(zip.getNextEntry()!=null)
                        throw new Exception("Backup contém arquivos adicionais não esperados");
                    // Validate avatar data after all music files are safely extracted.
                    byte[] json=creator.toString().getBytes(StandardCharsets.UTF_8);
                    RacerBackup.Loaded profile=RacerBackup.decode(ctx,json);
                    return new Loaded(profile,imported,manifest.optInt("selected",-1),
                        manifest.optBoolean("shuffle",false),true);
                }catch(Exception failed){
                    for(File file:written)file.delete();
                    throw failed;
                }
            }
        }
    }
}
