package com.viniison.marblerace;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.net.URLEncoder;
import java.util.concurrent.ExecutorService;

/** Google Images web UI without scraping or claiming an official Google search API.
 *  Long-press can import an HTTP(S) image; Chrome is the reliable fallback.
 */
final class GoogleImagePicker {
    interface Listener{void onImage(Bitmap result);}
    private final Activity activity;
    private final ExecutorService executor;
    private final Handler ui=new Handler(Looper.getMainLooper());
    GoogleImagePicker(Activity a,ExecutorService e){activity=a;executor=e;}
    private int dp(float px){return (int)(activity.getResources().getDisplayMetrics().density*px+.5f);}
    static String searchUrl(String term)throws Exception {
        String q=term==null?"":term.trim();
        // Respect exactly what the user typed. Never append "rosto", "PNG"
        // or franchise names that bias or hide the requested result.
        if(q.isEmpty())return "https://www.google.com/imghp?hl=pt-BR";
        return "https://www.google.com/search?tbm=isch&safe=active&q="+URLEncoder.encode(q,"UTF-8");
    }
    void open(String term,Listener listener){
        final String url;
        try{url=searchUrl(term);}catch(Exception e){return;}
        LinearLayout body=new LinearLayout(activity);body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(10),dp(10),dp(10),dp(8));
        TextView info=new TextView(activity);
        info.setText("Segure uma imagem para tentar importar. Se o Google bloquear a tela, abra no navegador, salve e importe pela galeria.");
        info.setTextSize(12);info.setTextColor(0xFFDDE8F0);
        body.addView(info,new LinearLayout.LayoutParams(-1,-2));
        WebView web=new WebView(activity);
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setDomStorageEnabled(true);
        web.getSettings().setLoadsImagesAutomatically(true);
        web.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest req){
                Uri u=req.getUrl();String host=u.getHost();
                if(host!=null&&(host.equals("google.com")||host.endsWith(".google.com")||host.endsWith(".google.com.br")))return false;
                try{activity.startActivity(new Intent(Intent.ACTION_VIEW,u));}catch(Exception ignored){}
                return true;
            }
        });
        LinearLayout.LayoutParams wlp=new LinearLayout.LayoutParams(-1,dp(430));
        wlp.topMargin=dp(8);body.addView(web,wlp);
        TextView browser=new TextView(activity);
        browser.setText("ABRIR NO NAVEGADOR ↗");
        browser.setGravity(Gravity.CENTER);browser.setTextSize(14);
        browser.setTextColor(0xFF13211A);
        android.graphics.drawable.GradientDrawable bg=new android.graphics.drawable.GradientDrawable();
        bg.setColor(0xFFC6F47A);bg.setCornerRadius(dp(12));
        browser.setBackground(bg);
        LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,dp(44));bp.topMargin=dp(8);
        body.addView(browser,bp);
        AlertDialog dialog=new AlertDialog.Builder(activity).setTitle("GOOGLE IMAGENS")
            .setView(body).setNegativeButton("Fechar",null).create();
        browser.setOnClickListener(v->{
            try{activity.startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}
            catch(Exception e){info.setText("Não consegui abrir o navegador.");}
        });
        web.setOnLongClickListener(v->{
            WebView.HitTestResult hit=web.getHitTestResult();
            String image=hit.getExtra();
            int type=hit.getType();
            if(image==null||(type!=WebView.HitTestResult.IMAGE_TYPE&&
                type!=WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE))return false;
            new AlertDialog.Builder(activity).setTitle("IMPORTAR IMAGEM?")
                .setMessage("Pode ser uma miniatura. Você poderá ampliar e recortar o rosto na próxima tela. Confira os direitos antes de publicar.")
                .setNegativeButton("Cancelar",null)
                .setPositiveButton("Importar",(d,w)->{
                    info.setText("Carregando imagem...");
                    executor.execute(()->{
                        try{
                            Bitmap b;
                            if(image.startsWith("data:image/")){
                                int comma=image.indexOf(',');
                                if(comma<0||image.length()>10000000)throw new Exception("Dados inválidos");
                                byte[] bytes=Base64.decode(image.substring(comma+1),Base64.DEFAULT);
                                b=BitmapFactory.decodeByteArray(bytes,0,bytes.length);
                            }else if(image.startsWith("https://"))b=OnlineImageSearch.download(image);
                            else throw new Exception("URL incompatível");
                            if(b==null)throw new Exception("Imagem inválida");
                            ui.post(()->{dialog.dismiss();listener.onImage(b);});
                        }catch(Exception error){
                            ui.post(()->info.setText("Miniatura bloqueada. Tente outra ou abra no navegador."));
                        }
                    });
                }).show();
            return true;
        });
        dialog.setOnDismissListener(v->{web.stopLoading();web.destroy();});
        dialog.show();
        web.loadUrl(url);
    }
}
