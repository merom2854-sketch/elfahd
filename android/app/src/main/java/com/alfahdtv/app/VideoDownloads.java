package com.alfahdtv.app;

import android.app.DownloadManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.webkit.URLUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class VideoDownloads {
    private static final String PREFS="video_downloads";
    private static final String IDS="ids";
    private static final String HIDDEN="hidden";

    static final class Record {
        final long id;
        final String name,url,mime,userAgent,cookie;
        final long createdAt;
        final boolean cancelled;

        Record(long id,String name,String url,String mime,String userAgent,String cookie,long createdAt,boolean cancelled){
            this.id=id;this.name=name;this.url=url;this.mime=mime;this.userAgent=userAgent;this.cookie=cookie;this.createdAt=createdAt;this.cancelled=cancelled;
        }
    }

    static final class Snapshot {
        final Record record;
        final boolean exists;
        final int status,reason;
        final long downloaded,total;

        Snapshot(Record record,boolean exists,int status,int reason,long downloaded,long total){
            this.record=record;this.exists=exists;this.status=status;this.reason=reason;this.downloaded=downloaded;this.total=total;
        }
    }

    private VideoDownloads(){}

    static long enqueue(Context context,String url,String title,String userAgent,String cookie,String mime){
        url=stableUrl(url);
        String contentType=(mime==null||mime.isEmpty()||"application/octet-stream".equalsIgnoreCase(mime))?"video/mp4":mime;
        String name=fileName(context,title,url,contentType);
        DownloadManager.Request request=new DownloadManager.Request(Uri.parse(url));
        request.setTitle(name);
        request.setDescription("تحميل من الفهد TV");
        request.setMimeType(contentType);
        request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI|DownloadManager.Request.NETWORK_MOBILE);
        request.setAllowedOverMetered(true);
        request.setAllowedOverRoaming(false);
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        request.setVisibleInDownloadsUi(true);
        if(userAgent!=null&&!userAgent.isEmpty())request.addRequestHeader("User-Agent",userAgent);
        if(cookie!=null&&!cookie.isEmpty())request.addRequestHeader("Cookie",cookie);
        request.addRequestHeader("Accept-Encoding","identity");
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS,name);
        DownloadManager manager=(DownloadManager)context.getSystemService(Context.DOWNLOAD_SERVICE);
        long id=manager.enqueue(request);
        save(context,new Record(id,name,url,contentType,userAgent==null?"":userAgent,cookie==null?"":cookie,System.currentTimeMillis(),false));
        return id;
    }

    static List<Record> records(Context context){
        importExisting(context);
        SharedPreferences prefs=context.getSharedPreferences(PREFS,0);
        List<Record> result=new ArrayList<>();
        for(String raw:prefs.getStringSet(IDS,Collections.emptySet())){
            try{
                long id=Long.parseLong(raw);String key="item_"+id+"_";
                result.add(new Record(id,prefs.getString(key+"name","ملف فيديو"),prefs.getString(key+"url",""),prefs.getString(key+"mime","video/mp4"),prefs.getString(key+"ua",""),prefs.getString(key+"cookie",""),prefs.getLong(key+"created",0),prefs.getBoolean(key+"cancelled",false)));
            }catch(Exception ignored){}
        }
        result.sort((a,b)->Long.compare(b.createdAt,a.createdAt));
        return result;
    }

    static Snapshot snapshot(Context context,Record record){
        DownloadManager manager=(DownloadManager)context.getSystemService(Context.DOWNLOAD_SERVICE);
        try(Cursor cursor=manager.query(new DownloadManager.Query().setFilterById(record.id))){
            if(cursor!=null&&cursor.moveToFirst()){
                int status=cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
                int reason=cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON));
                long done=cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
                long total=cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
                return new Snapshot(record,true,status,reason,done,total);
            }
        }catch(Exception ignored){}
        return new Snapshot(record,false,record.cancelled?DownloadManager.STATUS_FAILED:0,0,0,-1);
    }

    static Uri downloadedUri(Context context,long id){return ((DownloadManager)context.getSystemService(Context.DOWNLOAD_SERVICE)).getUriForDownloadedFile(id);}

    static void cancel(Context context,Record record){
        ((DownloadManager)context.getSystemService(Context.DOWNLOAD_SERVICE)).remove(record.id);
        context.getSharedPreferences(PREFS,0).edit().putBoolean("item_"+record.id+"_cancelled",true).apply();
    }

    static long retry(Context context,Record record){
        forget(context,record.id);
        return enqueue(context,record.url,stripExtension(record.name),record.userAgent,record.cookie,record.mime);
    }

    static void forget(Context context,long id){
        SharedPreferences prefs=context.getSharedPreferences(PREFS,0);
        Set<String> ids=new HashSet<>(prefs.getStringSet(IDS,Collections.emptySet()));ids.remove(String.valueOf(id));
        Set<String> hidden=new HashSet<>(prefs.getStringSet(HIDDEN,Collections.emptySet()));hidden.add(String.valueOf(id));
        String key="item_"+id+"_";
        prefs.edit().putStringSet(IDS,ids).putStringSet(HIDDEN,hidden).remove(key+"name").remove(key+"url").remove(key+"mime").remove(key+"ua").remove(key+"cookie").remove(key+"created").remove(key+"cancelled").apply();
    }

    private static void importExisting(Context context){
        SharedPreferences prefs=context.getSharedPreferences(PREFS,0);
        Set<String> known=new HashSet<>(prefs.getStringSet(IDS,Collections.emptySet()));
        Set<String> hidden=prefs.getStringSet(HIDDEN,Collections.emptySet());
        DownloadManager manager=(DownloadManager)context.getSystemService(Context.DOWNLOAD_SERVICE);
        try(Cursor cursor=manager.query(new DownloadManager.Query())){
            while(cursor!=null&&cursor.moveToNext()){
                long id=cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_ID));String raw=String.valueOf(id);
                if(known.contains(raw)||hidden.contains(raw))continue;
                String title=cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TITLE));
                String url=cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_URI));
                save(context,new Record(id,title==null?"ملف فيديو":title,url==null?"":url,"video/mp4","","",id,false));known.add(raw);
            }
        }catch(Exception ignored){}
    }

    private static void save(Context context,Record record){
        SharedPreferences prefs=context.getSharedPreferences(PREFS,0);
        Set<String> ids=new HashSet<>(prefs.getStringSet(IDS,Collections.emptySet()));ids.add(String.valueOf(record.id));
        String key="item_"+record.id+"_";
        prefs.edit().putStringSet(IDS,ids).putString(key+"name",record.name).putString(key+"url",record.url).putString(key+"mime",record.mime).putString(key+"ua",record.userAgent).putString(key+"cookie",record.cookie).putLong(key+"created",record.createdAt).putBoolean(key+"cancelled",record.cancelled).apply();
    }

    private static String fileName(Context context,String title,String url,String mime){
        String source=url;
        try{String nested=Uri.parse(url).getQueryParameter("url");if(nested!=null&&!nested.isEmpty())source=nested;}catch(Exception ignored){}
        String extension=".mp4";
        try{String path=Uri.parse(source).getLastPathSegment();if(path!=null&&path.matches("(?i).+\\.(mp4|mkv|webm|avi|mov)$"))extension=path.substring(path.lastIndexOf('.')).toLowerCase(Locale.ROOT);}catch(Exception ignored){}
        String clean=title==null?"":title.trim().replaceAll("[\\\\/:*?\"<>|]","_").replaceAll("[. ]+$","");
        if(clean.isEmpty())clean=URLUtil.guessFileName(source,"",mime).replaceAll("(?i)\\.(mp4|mkv|webm|avi|mov|bin)$","");
        if(clean.isEmpty()||clean.equalsIgnoreCase("downloadfile"))clean="Al-Fahd-TV";
        if(clean.length()>90)clean=clean.substring(0,90).trim();
        String candidate=clean+extension;
        File folder=Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        if(new File(folder,candidate).exists())candidate=clean+"-"+(System.currentTimeMillis()/1000)+extension;
        return candidate;
    }

    private static String stableUrl(String url){
        try{
            Uri outer=Uri.parse(url);String host=outer.getHost();
            if(host!=null&&host.equalsIgnoreCase("akwam-stream-fetcher.meroo3292.workers.dev")){
                String nested=outer.getQueryParameter("url");Uri direct=nested==null?null:Uri.parse(nested);String directHost=direct==null?null:direct.getHost();
                if(direct!=null&&"https".equalsIgnoreCase(direct.getScheme())&&directHost!=null&&(directHost.equalsIgnoreCase("downet.net")||directHost.toLowerCase(Locale.ROOT).endsWith(".downet.net")))return direct.toString();
            }
        }catch(Exception ignored){}
        return url;
    }

    private static String stripExtension(String name){return name==null?"":name.replaceFirst("(?i)\\.(mp4|mkv|webm|avi|mov)$","");}
}
