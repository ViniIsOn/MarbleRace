package com.viniison.marblerace;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.util.LruCache;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import org.json.JSONArray;
import org.json.JSONObject;

final class OnlineImageSearch {
    interface Listener { void onImage(Bitmap bitmap,String title); }
    static class Result {
        String title,thumb,original;
        Result(String a,String b,String c){title=a;thumb=b;original=c;}
    }
    private final Activity activity;
    private final ExecutorService io;
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final LruCache<String,Bitmap> thumbnails=new LruCache<>(32);
    private final ArrayList<Result> results=new ArrayList<>();
    private AlertDialog dialog;
    private GridView grid;
    private TextView status;
    private ProgressBar loading;
    private ResultAdapter adapter;
    private Listener listener;
    private int searchSerial=0;
    private EditText searchField;
    OnlineImageSearch(Activity a,ExecutorService e){activity=a;io=e;}
    private static GradientDrawable bg(int color,int radius){
        GradientDrawable d=new GradientDrawable();
        d.setColor(color);d.setCornerRadius(radius);return d;
    }
    private int dp(float v){return (int)(v*activity.getResources().getDisplayMetrics().density+.5f);}
    private static byte[] request(String url,int limit)throws Exception{
        HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();
        c.setRequestMethod("GET");
        c.setConnectTimeout(11000);c.setReadTimeout(14000);
        c.setRequestProperty("User-Agent","MarbleRaceStudio/1.0 (Android game; Wikimedia Commons image search)");
        c.setRequestProperty("Accept","application/json,image/*,*/*");
        try {
            if(c.getResponseCode()!=200)throw new Exception("HTTP "+c.getResponseCode());
            try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
                byte[] b=new byte[8192];int n;int size=0;
                while((n=in.read(b))!=-1){size+=n;if(size>limit)throw new Exception("Imagem muito grande");out.write(b,0,n);}
                return out.toByteArray();
            }
        }finally{c.disconnect();}
    }
    static Bitmap download(String url)throws Exception{
        byte[] data=request(url,8*1024*1024);
        BitmapFactory.Options bounds=new BitmapFactory.Options();
        bounds.inJustDecodeBounds=true;
        BitmapFactory.decodeByteArray(data,0,data.length,bounds);
        if(bounds.outWidth<=0 || bounds.outHeight<=0)throw new Exception("Formato de imagem não suportado");
        BitmapFactory.Options options=new BitmapFactory.Options();
        options.inSampleSize=1;
        int max=Math.max(bounds.outWidth,bounds.outHeight);
        while(max/options.inSampleSize>650)options.inSampleSize*=2;
        Bitmap bitmap=BitmapFactory.decodeByteArray(data,0,data.length,options);
        if(bitmap==null)throw new Exception("Não foi possível abrir a imagem");
        return bitmap;
    }
    void open(Listener chosen) {
        listener=chosen;
        LinearLayout body=new LinearLayout(activity);body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(16),dp(12),dp(16),dp(8));body.setBackgroundColor(0xFF101A23);
        TextView explanation=new TextView(activity);
        explanation.setText("Google Imagens é a opção principal para personagens. A busca abaixo mostra imagens da Wikipedia/Commons. GIFs ficam estáticos. Confira os direitos de uso.");
        explanation.setTextSize(12);explanation.setTextColor(0xFFB1C3CE);
        body.addView(explanation,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout form=new LinearLayout(activity);
        form.setGravity(Gravity.CENTER_VERTICAL);form.setPadding(0,dp(12),0,dp(9));
        EditText query=new EditText(activity);
        query.setSingleLine(true);query.setInputType(InputType.TYPE_CLASS_TEXT);
        query.setHint("Mario, Sonic, Blu, desenhos...");
        searchField=query;
        query.setHintTextColor(0xFF7C91A0);query.setTextColor(Color.WHITE);
        query.setTextSize(14);query.setBackground(bg(0xFF253541,dp(10)));
        query.setPadding(dp(12),0,dp(12),0);
        form.addView(query,new LinearLayout.LayoutParams(0,dp(46),1));
        Button search=new Button(activity);search.setText("BUSCAR");
        search.setAllCaps(false);search.setTextColor(0xFF112015);search.setTextSize(12);
        search.setBackground(bg(0xFFC6F47A,dp(12)));
        LinearLayout.LayoutParams sl=new LinearLayout.LayoutParams(dp(94),dp(46));sl.leftMargin=dp(8);form.addView(search,sl);
        body.addView(form);
        LinearLayout quick=new LinearLayout(activity);quick.setOrientation(LinearLayout.HORIZONTAL);
        quick.setGravity(Gravity.CENTER_VERTICAL);
        for(String term:new String[]{"Mario","Sonic","Blu"}){
            TextView chip=new TextView(activity);
            chip.setText(term);chip.setTextSize(12);chip.setTextColor(0xFFCCF7A3);
            chip.setGravity(Gravity.CENTER);chip.setBackground(bg(0xFF273942,dp(10)));
            LinearLayout.LayoutParams chipLayout=new LinearLayout.LayoutParams(0,dp(37),1);
            chipLayout.rightMargin=dp(6);quick.addView(chip,chipLayout);
            chip.setOnClickListener(v->{query.setText(term);search(term);});
        }
        TextView web=new TextView(activity);web.setText("GOOGLE ↗");
        web.setTextSize(12);web.setGravity(Gravity.CENTER);web.setTextColor(Color.WHITE);
        web.setBackground(bg(0xFF405575,dp(10)));
        quick.addView(web,new LinearLayout.LayoutParams(0,dp(37),1));
        web.setOnClickListener(v->{
            String q=query.getText().toString().trim();
            new GoogleImagePicker(activity,io).open(q,bitmap->{
                if(dialog!=null&&dialog.isShowing())dialog.dismiss();
                listener.onImage(bitmap,"Google Imagens");
            });
        });
        body.addView(quick,new LinearLayout.LayoutParams(-1,dp(45)));
        loading=new ProgressBar(activity);
        loading.setVisibility(View.GONE);
        body.addView(loading,new LinearLayout.LayoutParams(-1,dp(25)));
        status=new TextView(activity);
        status.setText("Busque um personagem ou escolha um atalho.");
        status.setTextSize(12);status.setTextColor(0xFFB8C6CE);
        body.addView(status,new LinearLayout.LayoutParams(-1,dp(30)));
        grid=new GridView(activity);grid.setNumColumns(3);
        grid.setHorizontalSpacing(dp(7));grid.setVerticalSpacing(dp(7));
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        adapter=new ResultAdapter();grid.setAdapter(adapter);
        body.addView(grid,new LinearLayout.LayoutParams(-1,dp(310)));
        dialog=new AlertDialog.Builder(activity).setTitle("ESCOLHER IMAGEM")
            .setView(body).setNegativeButton("Fechar",null).create();
        dialog.setOnShowListener(v->{
            if(dialog.getWindow()!=null)dialog.getWindow().setBackgroundDrawable(bg(0xFF101A23,dp(22)));
        });
        grid.setOnItemClickListener((parent,view,position,id)->{
            if(position<0||position>=results.size())return;
            Result image=results.get(position);
            loading.setVisibility(View.VISIBLE);status.setText("Importando "+image.title+"...");
            io.execute(()->{
                try{
                    Bitmap pic=download(image.original);
                    ui.post(()->{
                        loading.setVisibility(View.GONE);
                        if(dialog!=null && dialog.isShowing())dialog.dismiss();
                        listener.onImage(pic,image.title);
                    });
                }catch(Exception e){
                    ui.post(()->{loading.setVisibility(View.GONE);status.setText("Falha ao carregar. Tente outra imagem.");});
                }
            });
        });
        search.setOnClickListener(v->search(query.getText().toString().trim()));
        query.setOnEditorActionListener((v,action,event)->{search(query.getText().toString().trim());return true;});
        dialog.show();
    }
    private void search(String q) {
        if(q.isEmpty()){status.setText("Digite um nome para pesquisar.");return;}
        final int serial=++searchSerial;
        status.setText("Procurando imagens relevantes...");loading.setVisibility(View.VISIBLE);
        results.clear();adapter.notifyDataSetChanged();
        io.execute(()->{
            ArrayList<Result> found=SmartImageSearch.find(q);
            ui.post(()->{
                if(serial!=searchSerial||dialog==null||!dialog.isShowing())return;
                loading.setVisibility(View.GONE);
                results.clear();results.addAll(found);adapter.notifyDataSetChanged();
                status.setText(found.isEmpty()?
                    "Nenhuma imagem aqui. Use GOOGLE ↗ e tente segurar uma imagem para importar.":
                    found.size()+" resultados. Confira os direitos antes de publicar.");
            });
        });
    }
    private class ResultAdapter extends BaseAdapter {
        @Override public int getCount(){return results.size();}
        @Override public Object getItem(int n){return results.get(n);}
        @Override public long getItemId(int n){return n;}
        @Override public View getView(int pos,View old,ViewGroup parent){
            LinearLayout item=new LinearLayout(activity);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setBackground(bg(0xFF22313B,dp(9)));
            ImageView preview=new ImageView(activity);preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
            item.addView(preview,new LinearLayout.LayoutParams(-1,dp(85)));
            TextView label=new TextView(activity);
            label.setPadding(dp(5),dp(3),dp(5),dp(3));
            label.setTextColor(Color.WHITE);label.setTextSize(10);label.setMaxLines(2);
            item.addView(label,new LinearLayout.LayoutParams(-1,dp(39)));
            Result result=results.get(pos);
            label.setText(result.title);
            Bitmap cached=thumbnails.get(result.thumb);
            if(cached!=null){preview.setImageBitmap(cached);}
            else{
                preview.setImageDrawable(null);
                preview.setTag(result.thumb);
                io.execute(()->{
                    try{
                        Bitmap img=download(result.thumb);thumbnails.put(result.thumb,img);
                        ui.post(()->{if(result.thumb.equals(preview.getTag()))preview.setImageBitmap(img);});
                    }catch(Exception ignored){}
                });
            }
            return item;
        }
    }
}
