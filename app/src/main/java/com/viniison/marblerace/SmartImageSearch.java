package com.viniison.marblerace;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

/** Three-step search: relevant encyclopedia thumbnails, Commons and smart aliases.
 *  Results are not automatically licensed for reuse: users must review source rights.
 */
final class SmartImageSearch {
    private SmartImageSearch(){}
    private static String enc(String q)throws Exception{return URLEncoder.encode(q,"UTF-8");}
    private static JSONObject fetch(String uri)throws Exception {
        HttpURLConnection c=(HttpURLConnection)new URL(uri).openConnection();
        c.setConnectTimeout(9000);c.setReadTimeout(10000);
        c.setRequestProperty("User-Agent","MarbleRaceStudio/1.1 Android image picker");
        c.setRequestProperty("Accept","application/json");
        try{
            if(c.getResponseCode()!=200)throw new Exception("Erro HTTP "+c.getResponseCode());
            try(InputStream stream=c.getInputStream();ByteArrayOutputStream data=new ByteArrayOutputStream()){
                byte[] buf=new byte[8192];int n;
                while((n=stream.read(buf))!=-1) {
                    if(data.size()+n>1500000)throw new Exception("Muitos dados na pesquisa");
                    data.write(buf,0,n);
                }
                return new JSONObject(new String(data.toByteArray(),StandardCharsets.UTF_8));
            }
        }finally{c.disconnect();}
    }
    private static String[] aliases(String term) {
        String q=term.trim().toLowerCase(Locale.ROOT);
        if(q.equals("mario")||q.contains("super mario"))
            return new String[]{"Mario (character)","Mario Nintendo character"};
        if(q.equals("sonic")||q.contains("sonic the hedgehog"))
            return new String[]{"Sonic the Hedgehog","Sonic the Hedgehog Sega character"};
        if(q.equals("blu")||q.contains("arara blu")||q.contains("blu rio"))
            return new String[]{"Rio (2011 film)","Blu Rio 2011 animated macaw character"};
        if(q.equals("luigi"))return new String[]{"Luigi","Luigi Nintendo character"};
        if(q.equals("yoshi"))return new String[]{"Yoshi","Yoshi Nintendo character"};
        if(q.equals("tails"))return new String[]{"Tails (Sonic the Hedgehog)","Tails Sega character"};
        return new String[]{term,term+" character"};
    }
    static ArrayList<OnlineImageSearch.Result> find(String query) {
        LinkedHashMap<String,OnlineImageSearch.Result> found=new LinkedHashMap<>();
        String[] alt=aliases(query);
        // Direct Wikipedia article: an explicit character article is normally more relevant
        // than all-files queries. Show source labels to help users check permissions.
        try {
            String endpoint="https://en.wikipedia.org/w/api.php?action=query&redirects=1"
                +"&titles="+enc(alt[0])
                +"&prop=pageimages&piprop=thumbnail&pithumbsize=650&format=json&formatversion=2";
            addWiki(fetch(endpoint),found,"WIKIPEDIA");
        }catch(Exception ignored){}
        for(String lang:new String[]{"en","pt"}){
            try{
                String queryText=lang.equals("en")?alt[1]:query;
                String endpoint="https://"+lang+".wikipedia.org/w/api.php?action=query"
                    +"&generator=search&gsrnamespace=0&gsrlimit=14&gsrsearch="+enc(queryText)
                    +"&prop=pageimages&piprop=thumbnail&pithumbsize=650&format=json&formatversion=2";
                addWiki(fetch(endpoint),found,"WIKIPEDIA");
            }catch(Exception ignored){}
        }
        try{
            String search=(alt[1].equals(query+" character")?query:alt[1]);
            String endpoint="https://commons.wikimedia.org/w/api.php?action=query"
                +"&generator=search&gsrnamespace=6&gsrlimit=35&gsrsearch="+enc(search)
                +"&prop=imageinfo&iiprop=url%7Cmime&iiurlwidth=650&format=json&formatversion=2";
            addCommons(fetch(endpoint),found);
        }catch(Exception ignored){}
        if(found.size()<6){
            try{
                String endpoint="https://commons.wikimedia.org/w/api.php?action=query"
                    +"&generator=search&gsrnamespace=6&gsrlimit=35&gsrsearch="+enc(query)
                    +"&prop=imageinfo&iiprop=url%7Cmime&iiurlwidth=650&format=json&formatversion=2";
                addCommons(fetch(endpoint),found);
            }catch(Exception ignored){}
        }
        ArrayList<OnlineImageSearch.Result> list=new ArrayList<>(found.values());
        if(list.size()>60)return new ArrayList<>(list.subList(0,60));
        return list;
    }
    private static void addWiki(JSONObject root,LinkedHashMap<String,OnlineImageSearch.Result> results,String source){
        JSONObject query=root.optJSONObject("query");
        if(query==null)return;
        JSONArray pages=query.optJSONArray("pages");
        if(pages==null)return;
        for(int i=0;i<pages.length();i++){
            JSONObject page=pages.optJSONObject(i);if(page==null)continue;
            JSONObject thumb=page.optJSONObject("thumbnail");if(thumb==null)continue;
            String uri=thumb.optString("source");
            String title=page.optString("title");
            if(uri.startsWith("https://")&&!results.containsKey(uri))
                results.put(uri,new OnlineImageSearch.Result(source+" • "+title,uri,uri));
        }
    }
    private static void addCommons(JSONObject root,LinkedHashMap<String,OnlineImageSearch.Result> results){
        JSONObject query=root.optJSONObject("query");if(query==null)return;
        JSONArray pages=query.optJSONArray("pages");if(pages==null)return;
        for(int i=0;i<pages.length();i++){
            JSONObject page=pages.optJSONObject(i);if(page==null)continue;
            JSONArray infos=page.optJSONArray("imageinfo");
            if(infos==null||infos.length()==0)continue;
            JSONObject image=infos.optJSONObject(0);if(image==null)continue;
            String mime=image.optString("mime");
            if(!(mime.equals("image/png")||mime.equals("image/jpeg")||mime.equals("image/gif")||mime.equals("image/webp")))continue;
            String thumb=image.optString("thumburl",image.optString("url"));
            String original=image.optString("url");
            if(!thumb.startsWith("https://")||!original.startsWith("https://"))continue;
            String name=page.optString("title").replaceFirst("^File:","");
            if(!results.containsKey(thumb))
                results.put(thumb,new OnlineImageSearch.Result("COMMONS • "+name,thumb,thumb));
        }
    }
}
