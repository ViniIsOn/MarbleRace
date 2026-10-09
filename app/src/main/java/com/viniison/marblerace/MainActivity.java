package com.viniison.marblerace;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivity extends Activity {
    private static final int CHOOSE_IMAGE=120;
    private static final int BG=0xFF090E15;
    private static final int SURFACE=0xFF14212D;
    private static final int LIME=0xFFC6F47A;
    private final ArrayList<RaceEngine.Racer> racers=new ArrayList<>();
    private final ExecutorService io=Executors.newFixedThreadPool(4);
    private RaceEngine race;
    private RaceView preview;
    private LinearLayout racerRow;
    private TextView startButton,speedButton,trackButton;
    private int selectedTrack=0;
    private RaceEngine.Racer pendingPicker;
    private float speed=1;
    private int speedIndex=0;
    private final float[] speeds={1f,1.5f,2f};
    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        loadRacers();
        selectedTrack=getPreferences(0).getInt("track_v2",0);
        if(selectedTrack<0||selectedTrack>=RaceEngine.TRACK_NAMES.length)selectedTrack=0;
        race=new RaceEngine(racers,selectedTrack);
        makeScreen();
    }
    private int dp(float d){return (int)(getResources().getDisplayMetrics().density*d+.5f);}
    private GradientDrawable shape(int color,int radius){
        GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;
    }
    private GradientDrawable outline(int color,int border){
        GradientDrawable d=shape(color,16);d.setStroke(dp(1),border);return d;
    }
    private TextView text(String message,int size,int color,boolean bold){
        TextView v=new TextView(this);
        v.setText(message);v.setTextSize(size);v.setTextColor(color);
        v.setGravity(Gravity.CENTER_VERTICAL);
        v.setTypeface(Typeface.create(bold?"sans-serif-medium":"sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));
        return v;
    }
    private TextView button(String label,boolean primary) {
        TextView b=text(label,13,primary?0xFF112016:Color.WHITE,true);
        b.setGravity(Gravity.CENTER);
        b.setBackground(shape(primary?LIME:0xFF263542,14));
        b.setClickable(true);b.setFocusable(true);
        return b;
    }
    private LinearLayout column(){
        LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);return v;
    }
    private LinearLayout row(){
        LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.HORIZONTAL);v.setGravity(Gravity.CENTER_VERTICAL);return v;
    }
    private void makeScreen() {
        LinearLayout root=column();root.setBackgroundColor(BG);
        root.setPadding(dp(12),dp(8),dp(12),dp(10));
        setContentView(root);

        LinearLayout brand=row();
        TextView mark=text("●",37,LIME,true);
        brand.addView(mark,new LinearLayout.LayoutParams(dp(42),dp(56)));
        LinearLayout brandText=column();
        TextView h=text("MARBLE RACE",22,Color.WHITE,true);
        h.setLetterSpacing(.055f);
        brandText.addView(h,new LinearLayout.LayoutParams(-2,dp(31)));
        TextView sub=text("RACE EDITOR  /  ORIGINAL TRACKS",10,0xFF97B1BF,true);
        sub.setLetterSpacing(.13f);
        brandText.addView(sub);
        brand.addView(brandText,new LinearLayout.LayoutParams(0,-2,1));
        TextView noAds=text("SEM ADS",11,LIME,true);
        noAds.setGravity(Gravity.CENTER);
        noAds.setBackground(shape(0xFF223A32,12));
        brand.addView(noAds,new LinearLayout.LayoutParams(dp(72),dp(31)));
        root.addView(brand,new LinearLayout.LayoutParams(-1,dp(62)));

        FrameLayout frame=new FrameLayout(this);
        frame.setBackground(outline(SURFACE,0xFF334552));
        frame.setClipToOutline(true);
        LinearLayout.LayoutParams frameParams=new LinearLayout.LayoutParams(-1,0,1);
        frameParams.topMargin=dp(7);frameParams.bottomMargin=dp(11);
        root.addView(frame,frameParams);
        preview=new RaceView();frame.addView(preview,new FrameLayout.LayoutParams(-1,-1));

        LinearLayout trackRow=row();
        LinearLayout trackCaption=column();
        TextView trackHeading=text("SELECIONAR PISTA",12,0xFFDBE9EE,true);
        trackCaption.addView(trackHeading,new LinearLayout.LayoutParams(-1,dp(21)));
        trackCaption.addView(text("Quatro cenários originais",10,0xFF8FA7B5,false));
        trackRow.addView(trackCaption,new LinearLayout.LayoutParams(0,dp(42),1));
        trackButton=button(RaceEngine.TRACK_NAMES[selectedTrack]+"  ▾",false);
        trackButton.setTextColor(LIME);
        trackRow.addView(trackButton,new LinearLayout.LayoutParams(dp(167),dp(43)));
        trackButton.setOnClickListener(v->selectTrack());
        LinearLayout.LayoutParams trackParams=new LinearLayout.LayoutParams(-1,dp(49));
        trackParams.bottomMargin=dp(4);
        root.addView(trackRow,trackParams);

        LinearLayout controls=row();
        startButton=button("▶  INICIAR",true);
        LinearLayout.LayoutParams startp=new LinearLayout.LayoutParams(0,dp(49),2.1f);
        controls.addView(startButton,startp);
        startButton.setOnClickListener(v->{
            if(race.finished>=race.balls.size() && !race.balls.isEmpty()) {
                race.reset();preview.renderer.resetCamera();
            }
            race.toggle();
            startButton.setText(race.running?"Ⅱ  PAUSAR":"▶  CONTINUAR");
        });
        TextView reset=button("↺  RESET",false);
        LinearLayout.LayoutParams resetp=new LinearLayout.LayoutParams(0,dp(49),1.35f);
        resetp.leftMargin=dp(6);
        controls.addView(reset,resetp);
        reset.setOnClickListener(v->{
            race.reset();preview.renderer.resetCamera();startButton.setText("▶  INICIAR");preview.invalidate();
        });
        speedButton=button("1×",false);
        LinearLayout.LayoutParams speedp=new LinearLayout.LayoutParams(0,dp(49),.85f);
        speedp.leftMargin=dp(6);controls.addView(speedButton,speedp);
        speedButton.setOnClickListener(v->{
            speedIndex=(speedIndex+1)%speeds.length;
            speed=speeds[speedIndex];
            speedButton.setText(speed==1?"1×":speed==1.5?"1.5×":"2×");
        });
        root.addView(controls,new LinearLayout.LayoutParams(-1,dp(49)));

        LinearLayout toolbar=row();
        LinearLayout titles=column();
        TextView title=text("CORREDORES",15,Color.WHITE,true);
        title.setLetterSpacing(.07f);
        titles.addView(title,new LinearLayout.LayoutParams(-1,dp(25)));
        titles.addView(text("Toque para editar nome ou imagem",11,0xFF91A8B3,false));
        toolbar.addView(titles,new LinearLayout.LayoutParams(0,dp(49),1));
        TextView add=button("+ ADICIONAR",false);
        add.setTextColor(LIME);
        toolbar.addView(add,new LinearLayout.LayoutParams(dp(119),dp(39)));
        add.setOnClickListener(v->addRacer());
        LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(-1,dp(52));tp.topMargin=dp(9);
        root.addView(toolbar,tp);
        
        HorizontalScrollView scroller=new HorizontalScrollView(this);
        scroller.setHorizontalScrollBarEnabled(false);
        racerRow=row();racerRow.setPadding(0,dp(4),0,dp(6));
        scroller.addView(racerRow);
        root.addView(scroller,new LinearLayout.LayoutParams(-1,dp(107)));
        redrawRacers();

        TextView export=button("●  EXPORTAR SHORT  •  MP4  9:16",true);
        LinearLayout.LayoutParams exp=new LinearLayout.LayoutParams(-1,dp(48));
        exp.topMargin=dp(7);
        root.addView(export,exp);
        export.setOnClickListener(v->exportVideo());

        TextView foot=text("720 × 1280  /  30 FPS  /  SEM ÁUDIO  /  ATÉ 45 S",10,0xFF859BA7,true);
        foot.setLetterSpacing(.065f);foot.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams fp=new LinearLayout.LayoutParams(-1,dp(25));root.addView(foot,fp);
    }
    private class RaceView extends View {
        final RaceRenderer renderer=new RaceRenderer();
        long last=System.nanoTime();
        RaceView(){super(MainActivity.this);setLayerType(View.LAYER_TYPE_HARDWARE,null);}
        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);
            long now=System.nanoTime();
            float dt=Math.min(.035f,(now-last)/1000000000f);
            last=now;
            if(race!=null)race.advance(dt*speed);
            if(race!=null)renderer.render(canvas,race,false);
            if(race!=null && !race.running && race.finished==race.balls.size() && race.finished>0 && startButton!=null)
                startButton.setText("▶  NOVA CORRIDA");
            postInvalidateOnAnimation();
        }
    }
    private void redrawRacers(){
        if(racerRow==null)return;
        racerRow.removeAllViews();
        for(int i=0;i<racers.size();i++){
            final RaceEngine.Racer racer=racers.get(i);
            LinearLayout card=column();card.setGravity(Gravity.CENTER);
            card.setBackground(outline(0xFF192733,0xFF304452));
            LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(dp(91),dp(93));
            if(i>0)cp.leftMargin=dp(7);
            racerRow.addView(card,cp);
            FrameLayout circle=new FrameLayout(this);
            ImageView photo=new ImageView(this);
            photo.setScaleType(ImageView.ScaleType.CENTER_CROP);
            photo.setBackground(shape(racer.color,99));
            photo.setClipToOutline(true);
            if(racer.avatar!=null)photo.setImageBitmap(racer.avatar);
            else{
                TextView letter=text(racer.name.isEmpty()?"?":racer.name.substring(0,1).toUpperCase(Locale.ROOT),22,0xFF10201A,true);
                letter.setGravity(Gravity.CENTER);
                circle.addView(letter,new FrameLayout.LayoutParams(-1,-1));
            }
            circle.addView(photo,0,new FrameLayout.LayoutParams(-1,-1));
            card.addView(circle,new LinearLayout.LayoutParams(dp(47),dp(47)));
            TextView name=text(racer.name,11,Color.WHITE,true);
            name.setGravity(Gravity.CENTER);name.setSingleLine();name.setMaxWidth(dp(82));name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            card.addView(name,new LinearLayout.LayoutParams(-1,dp(24)));
            TextView edit=text("EDITAR",9,LIME,true);edit.setGravity(Gravity.CENTER);
            card.addView(edit);
            card.setOnClickListener(v->editRacer(racer));
        }
    }
    private void refresh(){
        race=new RaceEngine(racers,selectedTrack);
        if(preview!=null){preview.renderer.resetCamera();preview.invalidate();}
        if(startButton!=null)startButton.setText("▶  INICIAR");
        redrawRacers();saveRacers();
    }
    private void selectTrack(){
        String[] names=new String[RaceEngine.TRACK_NAMES.length];
        for(int i=0;i<names.length;i++)
            names[i]=RaceEngine.TRACK_NAMES[i]+"  —  "+RaceEngine.TRACK_DETAILS[i];
        new AlertDialog.Builder(this)
            .setTitle("ESCOLHER PISTA")
            .setSingleChoiceItems(names,selectedTrack,(dialog,which)->{
                selectedTrack=which;
                getPreferences(0).edit().putInt("track_v2",selectedTrack).apply();
                trackButton.setText(RaceEngine.TRACK_NAMES[selectedTrack]+"  ▾");
                refresh();
                dialog.dismiss();
            })
            .setNegativeButton("Cancelar",null).show();
    }

    private void addRacer(){
        if(racers.size()>=8){toast("Limite de 8 corredores.");return;}
        int i=racers.size();
        RaceEngine.Racer racer=new RaceEngine.Racer("CORREDOR "+(i+1),"",null,RaceEngine.PALETTE[i]);
        racers.add(racer);refresh();editRacer(racer);
    }
    private void editRacer(RaceEngine.Racer racer) {
        String[] choices={"✎  Renomear","▣  Escolher imagem da galeria",
            "⌕  Buscar PNG / GIF online","◯  Remover imagem","×  Excluir corredor"};
        new AlertDialog.Builder(this)
            .setTitle("EDITAR • "+racer.name)
            .setItems(choices,(dlg,n)->{
                switch(n){
                    case 0:rename(racer);break;
                    case 1:chooseGallery(racer);break;
                    case 2:new OnlineImageSearch(this,io).open((bitmap,title)->{
                        saveAvatarAsync(racer,bitmap);
                    });break;
                    case 3:racer.avatar=null;racer.imagePath="";refresh();break;
                    case 4:
                        if(racers.size()<=2){toast("A corrida precisa de pelo menos 2 corredores.");return;}
                        new AlertDialog.Builder(this).setMessage("Remover "+racer.name+"?")
                        .setNegativeButton("Cancelar",null)
                        .setPositiveButton("Remover",(d,w)->{racers.remove(racer);refresh();}).show();
                        break;
                }
            }).setNegativeButton("Voltar",null).show();
    }
    private void rename(RaceEngine.Racer racer) {
        EditText edit=new EditText(this);edit.setSingleLine(true);
        edit.setText(racer.name);edit.setSelectAllOnFocus(true);
        LinearLayout container=column();container.setPadding(dp(24),0,dp(24),0);
        container.addView(edit);
        new AlertDialog.Builder(this).setTitle("Nome do corredor")
            .setView(container).setNegativeButton("Cancelar",null)
            .setPositiveButton("Salvar",(d,w)->{
                String n=edit.getText().toString().trim();
                if(n.isEmpty()){toast("Digite um nome.");return;}
                racer.name=n.length()>24?n.substring(0,24):n;refresh();
            }).show();
    }
    private void chooseGallery(RaceEngine.Racer racer){
        pendingPicker=racer;
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        startActivityForResult(intent,CHOOSE_IMAGE);
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request!=CHOOSE_IMAGE||result!=RESULT_OK||data==null||data.getData()==null||pendingPicker==null)return;
        RaceEngine.Racer target=pendingPicker;
        Uri uri=data.getData();
        io.execute(()->{
            try{
                BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;
                try(InputStream in=getContentResolver().openInputStream(uri)){BitmapFactory.decodeStream(in,null,bounds);}
                BitmapFactory.Options opts=new BitmapFactory.Options();opts.inSampleSize=1;
                while(Math.max(bounds.outWidth,bounds.outHeight)/opts.inSampleSize>650)opts.inSampleSize*=2;
                Bitmap bitmap;
                try(InputStream in=getContentResolver().openInputStream(uri)){
                    bitmap=BitmapFactory.decodeStream(in,null,opts);
                }
                if(bitmap==null)throw new Exception("Imagem não compatível");
                saveAvatar(target,bitmap);
                runOnUiThread(()->{refresh();toast("Imagem aplicada!");});
            }catch(Exception e){runOnUiThread(()->toast("Não foi possível ler essa imagem."));}
        });
    }
    private void saveAvatarAsync(RaceEngine.Racer racer,Bitmap bitmap){
        toast("Adicionando imagem...");
        io.execute(()->{
            try{
                saveAvatar(racer,bitmap);
                runOnUiThread(()->{refresh();toast("Imagem adicionada!");});
            }catch(Exception e){runOnUiThread(()->toast("Falha ao salvar a imagem."));}
        });
    }
    private void saveAvatar(RaceEngine.Racer racer,Bitmap bitmap)throws Exception{
        int max=Math.max(bitmap.getWidth(),bitmap.getHeight());
        float factor=Math.min(1,384f/Math.max(1,max));
        int w=Math.max(1,Math.round(bitmap.getWidth()*factor));
        int h=Math.max(1,Math.round(bitmap.getHeight()*factor));
        Bitmap scaled=Bitmap.createScaledBitmap(bitmap,w,h,true);
        File dir=new File(getFilesDir(),"avatars");
        if(!dir.exists()&&!dir.mkdirs())throw new Exception("Sem espaço para avatares");
        File out=new File(dir,"avatar_"+System.nanoTime()+".png");
        try(FileOutputStream stream=new FileOutputStream(out)){
            if(!scaled.compress(Bitmap.CompressFormat.PNG,100,stream))throw new Exception("Falha ao salvar PNG");
        }
        // Background processing finishes before the UI and race reference are rebuilt.
        racer.imagePath=out.getAbsolutePath();
        racer.avatar=scaled;
    }
    private void loadRacers() {
        try{
            String stored=getPreferences(0).getString("racers_v1","");
            if(!stored.isEmpty()){
                JSONArray a=new JSONArray(stored);
                for(int i=0;i<a.length() && i<8;i++){
                    JSONObject o=a.getJSONObject(i);
                    String path=o.optString("path","");
                    Bitmap b=null;
                    if(!path.isEmpty())b=BitmapFactory.decodeFile(path);
                    racers.add(new RaceEngine.Racer(o.optString("name","CORREDOR "+(i+1)),
                        path,b,o.optInt("color",RaceEngine.PALETTE[i])));
                }
            }
        }catch(Exception ignored){}
        if(racers.size()<2){
            racers.clear();
            String[] defaults={"TURBO","NEON","COMETA","FLASH","PIXEL","RAIO"};
            for(int i=0;i<defaults.length;i++)
                racers.add(new RaceEngine.Racer(defaults[i],"",null,RaceEngine.PALETTE[i]));
        }
    }
    private void saveRacers(){
        try{
            JSONArray array=new JSONArray();
            for(RaceEngine.Racer r:racers){
                JSONObject o=new JSONObject();
                o.put("name",r.name);o.put("path",r.imagePath);o.put("color",r.color);
                array.put(o);
            }
            getPreferences(0).edit().putString("racers_v1",array.toString()).apply();
        }catch(Exception ignored){}
    }
    private void exportVideo(){
        if(racers.size()<2){toast("Adicione pelo menos dois corredores.");return;}
        AtomicBoolean cancel=new AtomicBoolean(false);
        LinearLayout pane=column();pane.setPadding(dp(24),dp(10),dp(24),dp(12));
        TextView message=text("Preparando vídeo vertical...",14,Color.WHITE,false);
        pane.addView(message,new LinearLayout.LayoutParams(-1,dp(42)));
        ProgressBar bar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);pane.addView(bar,new LinearLayout.LayoutParams(-1,dp(12)));
        TextView advice=text("O vídeo é processado em tempo real. Mantenha o app aberto.",12,0xFF94ACB8,false);
        advice.setPadding(0,dp(16),0,0);
        pane.addView(advice);
        AlertDialog progressDialog=new AlertDialog.Builder(this).setTitle("EXPORTANDO SHORT")
            .setView(pane).setNegativeButton("Cancelar",(d,w)->cancel.set(true))
            .setCancelable(false).create();
        progressDialog.show();
        // Snapshot: racing changes after export do not modify the export.
        ArrayList<RaceEngine.Racer> snapshot=new ArrayList<>(racers);
        io.execute(()->{
            try{
                Uri video=VideoExporter.export(this,snapshot,selectedTrack,pct->
                    runOnUiThread(()->{if(!cancel.get()){
                        bar.setProgress(pct);message.setText("Renderizando... "+pct+"%");
                    }}),cancel);
                runOnUiThread(()->{
                    progressDialog.dismiss();
                    toast("Short salvo em Filmes/MarbleRace!");
                    new AlertDialog.Builder(this).setTitle("VÍDEO PRONTO!")
                        .setMessage("O MP4 foi salvo em Filmes/MarbleRace, em 720×1280. Adicione uma música no editor antes de publicar.")
                        .setNegativeButton("Fechar",null)
                        .setPositiveButton("Abrir vídeo",(d,w)->{
                            Intent i=new Intent(Intent.ACTION_VIEW);
                            i.setDataAndType(video,"video/mp4");
                            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            try{startActivity(i);}catch(Exception e){toast("Abra o vídeo pela galeria.");}
                        }).show();
                });
            }catch(Exception error){
                runOnUiThread(()->{
                    progressDialog.dismiss();
                    if(cancel.get()){toast("Exportação cancelada.");return;}
                    new AlertDialog.Builder(this).setTitle("Não foi possível exportar")
                        .setMessage(error.getMessage()+"\n\nAlternativa: use o gravador de tela do Android durante a corrida.")
                        .setPositiveButton("Entendi",null).show();
                });
            }
        });
    }
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}
    @Override protected void onPause(){
        super.onPause();
        if(race!=null && race.running){race.running=false;if(startButton!=null)startButton.setText("▶  CONTINUAR");}
    }
    @Override protected void onDestroy(){io.shutdownNow();super.onDestroy();}
}
