package org.velvetatelier;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.StatFs;
import android.util.Log;
import java.io.*;
import java.util.Map;

public final class IncludedModelProvider extends ContentProvider {
    private static final String TAG = "VelvetModel";
    private static final String MODEL = "MeinaHentaiV21";
    private static final String ASSET = "included/meinahentai_v21.safetensors";
    private static final String CHANNEL = "velvet_model_install";
    private static final int NOTIFICATION_ID = 7101;
    private static boolean started = false;

    @Override public boolean onCreate() {
        Context context = getContext();
        if (context == null) return true;
        File ready = new File(new File(new File(context.getFilesDir(), "models"), MODEL), "finished");
        if (ready.isFile()) return true;
        synchronized (IncludedModelProvider.class) {
            if (started) return true;
            started = true;
        }
        Context app = context.getApplicationContext();
        Thread t = new Thread(() -> install(app), "Velvet-FirstRun-Model");
        t.setPriority(Thread.NORM_PRIORITY - 1);
        t.start();
        return true;
    }
    private static void notifyState(Context c, String message, boolean done) {
        try {
            NotificationManager n = (NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);
            n.createNotificationChannel(new NotificationChannel(CHANNEL,"Offline model setup",NotificationManager.IMPORTANCE_LOW));
            Notification.Builder builder = new Notification.Builder(c, CHANNEL)
                .setSmallIcon(c.getApplicationInfo().icon)
                .setContentTitle(done ? "Velvet Atelier model ready" : "Velvet Atelier first-time setup")
                .setContentText(message).setOngoing(!done).setAutoCancel(done);
            n.notify(NOTIFICATION_ID, builder.build());
        } catch(Exception e) { Log.w(TAG,"Notification unavailable",e); }
    }
    private static void install(Context c) {
        File dir = new File(new File(c.getFilesDir(),"models"), MODEL);
        try {
            StatFs stat = new StatFs(c.getFilesDir().getAbsolutePath());
            if (stat.getAvailableBytes() < 6L * 1024 * 1024 * 1024)
                throw new IOException("At least 6 GB free space required for unpacking and conversion.");
            notifyState(c,"Preparing the bundled adult-anime model. Keep the app open.",false);
            if (dir.exists()) deleteTree(dir);
            if (!dir.mkdirs()) throw new IOException("Cannot create model directory");
            File original = new File(dir,"model.safetensors");
            try (InputStream in = c.getAssets().open(ASSET, 2);
                 OutputStream out = new BufferedOutputStream(new FileOutputStream(original), 1024*1024)) {
                byte[] buf = new byte[1024*1024];
                int count;
                while ((count = in.read(buf)) != -1) out.write(buf,0,count);
            }
            notifyState(c,"Converting anime model locally. This can take a long time.",false);
            copyAssetTree(c,"cvtbase",dir);
            File clip = new File(dir,"clip_skip_2.mnn");
            if (!clip.isFile()) throw new IOException("CLIP base architecture missing");
            copyFile(clip,new File(dir,"clip_v2.mnn"));
            String nativeDir=c.getApplicationInfo().nativeLibraryDir;
            File executable=new File(nativeDir,"libstable_diffusion_core.so");
            if (!executable.isFile()) throw new IOException("Native conversion executable missing");
            ProcessBuilder processBuilder = new ProcessBuilder(
                 executable.getAbsolutePath(),"--convert",dir.getAbsolutePath(),"--clip_skip_2");
            processBuilder.directory(new File(nativeDir));
            processBuilder.redirectErrorStream(true);
            Map<String,String> env=processBuilder.environment();
            env.put("LD_LIBRARY_PATH",nativeDir+":/system/lib64:/vendor/lib64:/vendor/lib64/egl");
            env.put("DSP_LIBRARY_PATH",nativeDir);
            Process proc=processBuilder.start();
            String lastLine="";
            try (BufferedReader reader=new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
                String line;
                while ((line=reader.readLine())!=null) {
                    lastLine=line;
                    Log.i(TAG,line);
                }
            }
            int result=proc.waitFor();
            if (result!=0 || !new File(dir,"finished").exists())
                throw new IOException("Conversion failed (exit "+result+"): "+lastLine);
            original.delete();
            new File(dir,"clip_skip_1.mnn").delete();
            new File(dir,"clip_skip_2.mnn").delete();
            notifyState(c,"Model ready. Reopen Velvet Atelier to select MeinaHentaiV21.",true);
        } catch(Throwable e) {
            Log.e(TAG,"Bundled model preparation failed",e);
            notifyState(c,"Model setup failed: "+e.getMessage(),true);
        } finally {
            synchronized(IncludedModelProvider.class) { started=false; }
        }
    }
    private static void copyAssetTree(Context c,String assetPath,File dir) throws IOException {
        String[] children=c.getAssets().list(assetPath);
        if(children==null || children.length==0) {
            try (InputStream in=c.getAssets().open(assetPath);
                 OutputStream out=new BufferedOutputStream(new FileOutputStream(
                     new File(dir,assetPath.substring(assetPath.lastIndexOf('/')+1))))) {
                byte[] b=new byte[65536]; int n;
                while((n=in.read(b))!=-1)out.write(b,0,n);
            }
            return;
        }
        for(String child:children) {
            String sub=assetPath+"/"+child;
            String[] nested=c.getAssets().list(sub);
            if(nested!=null&&nested.length>0) {
                File d=new File(dir,child);
                if(!d.exists()&&!d.mkdirs())throw new IOException("Failed creating "+d);
                copyAssetTree(c,sub,d);
            } else copyAssetTree(c,sub,dir);
        }
    }
    private static void copyFile(File src,File dest) throws IOException {
        try(InputStream in=new FileInputStream(src);OutputStream out=new FileOutputStream(dest)) {
           byte[] b=new byte[65536];int n; while((n=in.read(b))!=-1)out.write(b,0,n);
        }
    }
    private static void deleteTree(File file) throws IOException {
        if(file.isDirectory()){
            File[] kids=file.listFiles(); if(kids!=null)for(File f:kids)deleteTree(f);
        }
        if(!file.delete())throw new IOException("Cannot remove "+file);
    }
    @Override public Cursor query(Uri uri,String[]projection,String selection,String[]selectionArgs,String sortOrder){return null;}
    @Override public String getType(Uri uri){return null;}
    @Override public Uri insert(Uri uri,ContentValues values){return null;}
    @Override public int delete(Uri uri,String selection,String[] selectionArgs){return 0;}
    @Override public int update(Uri uri,ContentValues values,String selection,String[]selectionArgs){return 0;}
}
