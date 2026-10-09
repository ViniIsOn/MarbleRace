package com.viniison.marblerace;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.ClipData;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.Random;
import org.json.JSONArray;
import org.json.JSONObject;

/** User-supplied media library. No songs or commercial audio packaged in the APK. */
final class MusicLibrary {
    static final int PICK_AUDIO=202;
    private static final String PREFS="marble_music_v1";
    static final class Track {
        final String title,uri;
        Track(String title,String uri){this.title=title;this.uri=uri;}
    }
    private final Activity activity;
    private final SharedPreferences prefs;
    private final ArrayList<Track> tracks=new ArrayList<>();
    private final Random random=new Random();
    private MediaPlayer player;
    private int selected=-1;
    private boolean shuffle=false;
    private boolean requested=false;
    private int generation=0;
    MusicLibrary(Activity context){
        activity=context;prefs=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
        load();
    }
    private void load(){
        selected=prefs.getInt("selected",-1);
        shuffle=prefs.getBoolean("shuffle",false);
        try{
            JSONArray array=new JSONArray(prefs.getString("songs","[]"));
            for(int i=0;i<array.length();i++){
                JSONObject o=array.getJSONObject(i);
                String uri=o.optString("uri"),title=o.optString("title");
                if(uri.startsWith("content://")&&!title.isEmpty())
                    tracks.add(new Track(title,uri));
            }
        }catch(Exception ignored){}
        if(selected>=tracks.size())selected=tracks.isEmpty()?-1:0;
    }
    private void save(){
        JSONArray arr=new JSONArray();
        for(Track t:tracks){
            JSONObject o=new JSONObject();
            try{o.put("title",t.title);o.put("uri",t.uri);}catch(Exception ignored){}
            arr.put(o);
        }
        prefs.edit().putString("songs",arr.toString()).putInt("selected",selected)
            .putBoolean("shuffle",shuffle).apply();
    }
    void launchPicker(){
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("audio/*");
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        activity.startActivityForResult(i,PICK_AUDIO);
    }
    private String name(Uri uri){
        String title=null;
        try(Cursor c=activity.getContentResolver().query(uri,
            new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){
            if(c!=null && c.moveToFirst())title=c.getString(0);
        }catch(Exception ignored){}
        return title==null||title.trim().isEmpty()?"Música "+(tracks.size()+1):title;
    }
    private void addOne(Uri uri,int flags){
        if(uri==null)return;
        try{
            activity.getContentResolver().takePersistableUriPermission(uri,
                flags&Intent.FLAG_GRANT_READ_URI_PERMISSION);
        }catch(Exception ignored){}
        for(Track t:tracks)if(t.uri.equals(uri.toString()))return;
        if(tracks.size()>=150){
            Toast.makeText(activity,"Limite de 150 músicas na biblioteca.",Toast.LENGTH_LONG).show();
            return;
        }
        tracks.add(new Track(name(uri),uri.toString()));
    }
    void onPickerResult(Intent data){
        if(data==null)return;
        int before=tracks.size();
        ClipData clip=data.getClipData();
        if(clip!=null){
            for(int i=0;i<clip.getItemCount();i++)
                addOne(clip.getItemAt(i).getUri(),data.getFlags());
        }else addOne(data.getData(),data.getFlags());
        if(selected<0&&!tracks.isEmpty())selected=0;
        save();
        Toast.makeText(activity,(tracks.size()-before)+" música(s) adicionada(s).",Toast.LENGTH_SHORT).show();
        showDialog();
    }
    boolean hasTrack(){return selected>=0&&selected<tracks.size();}
    Track current(){
        if(!hasTrack())return null;
        return tracks.get(selected);
    }
    String selectedUri(){
        Track t=current();return t==null?null:t.uri;
    }
    String label(){
        Track t=current();
        if(t==null)return "SEM MÚSICA";
        String title=t.title;
        return title.length()>19?title.substring(0,18)+"…":title;
    }
    void showDialog(){
        ArrayList<String> options=new ArrayList<>();
        options.add("＋ Adicionar músicas do celular (seleção múltipla)");
        options.add(shuffle?"☑ Aleatório: ligado":"☐ Aleatório: desligado");
        options.add("■ Parar música");
        options.add("◯ Sem música no vídeo");
        for(int i=0;i<tracks.size();i++){
            String song=tracks.get(i).title;
            options.add((i==selected?"● ":"○ ")+song);
        }
        options.add("✕ Esvaziar biblioteca (não apaga arquivos)");
        String[] items=options.toArray(new String[0]);
        new AlertDialog.Builder(activity)
            .setTitle("BIBLIOTECA DE MÚSICAS")
            .setMessage("Escolha arquivos locais que você tem permissão de usar. Reprodução no app: MP3/M4A. Para áudio no MP4: preferencialmente M4A/AAC. Nenhuma música vem instalada.")
            .setItems(items,(dialog,which)->{
                if(which==0){launchPicker();return;}
                if(which==1){
                    shuffle=!shuffle;save();showDialog();return;
                }
                if(which==2){stop();showDialog();return;}
                if(which==3){stop();selected=-1;save();showDialog();return;}
                int idx=which-4;
                if(idx>=0&&idx<tracks.size()){
                    selected=idx;save();play();showDialog();return;
                }
                if(which==tracks.size()+4){
                    new AlertDialog.Builder(activity)
                        .setTitle("Limpar lista?")
                        .setMessage("Os arquivos de áudio continuarão no celular.")
                        .setNegativeButton("Cancelar",null)
                        .setPositiveButton("Limpar",(d,w)->{
                            stop();tracks.clear();selected=-1;save();
                        }).show();
                }
            }).setNegativeButton("Fechar",null).show();
    }
    void play(){
        if(!hasTrack())return;
        if(player!=null && requested && player.isPlaying())return;
        stop();
        if(shuffle && tracks.size()>1){selected=random.nextInt(tracks.size());save();}
        final String uri=tracks.get(selected).uri;
        requested=true;final int serial=++generation;
        MediaPlayer next=new MediaPlayer();
        player=next;
        try{
            next.setDataSource(activity,Uri.parse(uri));
            next.setAudioAttributes(new android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC).build());
            next.setLooping(true);
            next.setVolume(.65f,.65f);
            next.setOnPreparedListener(mp->{
                if(serial==generation && requested)mp.start();
            });
            next.setOnErrorListener((mp,what,extra)->{
                if(serial==generation)Toast.makeText(activity,
                    "Não foi possível tocar esse arquivo.",Toast.LENGTH_SHORT).show();
                return true;
            });
            next.prepareAsync();
        }catch(Exception e){stop();Toast.makeText(activity,"Arquivo de áudio inacessível.",Toast.LENGTH_SHORT).show();}
    }
    void stop(){
        requested=false;generation++;
        if(player!=null){
            try{player.reset();}catch(Exception ignored){}
            try{player.release();}catch(Exception ignored){}
            player=null;
        }
    }
    void close(){stop();}
}
