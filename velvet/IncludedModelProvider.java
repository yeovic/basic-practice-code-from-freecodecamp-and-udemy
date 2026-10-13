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
import java.util.Arrays;
import java.security.MessageDigest;

public final class IncludedModelProvider extends ContentProvider {
    private static final String TAG = "VelvetModel";
    private static final String MODEL = "MeinaHentaiV21";
    private static final String ASSET_FOLDER = "included";
    private static final String MODEL_SHA256 = "deb40a068adf41aa2466dc8e76b516216af1c1afe721dfd23fc2c1108b4d152f";
    private static final String CHANNEL = "velvet_model_install";
    private static final int NOTIFICATION_ID = 7101;
    private static boolean started = false;

    private static volatile String status = "Checking included model";
    private static volatile String lastError = "";
    private static volatile int progressPercent = 0;

    public static String currentStatus(Context c) {
        File dir=new File(ModelStorage.modelsDir(c),MODEL);
        File finished=new File(dir,"finished");
        if(finished.isFile())return "READY — MeinaHentai v2.1 installed in "+dir.getAbsolutePath();
        String state=status;
        String diagnostic=readLastError(c);
        if (!diagnostic.isEmpty()) state+="\\nLast error: "+diagnostic;
        return state+"\\nTarget folder: "+dir.getAbsolutePath();
    }
    private static String readLastError(Context c) {
        File f=new File(c.getFilesDir(),"velvet-model-error.txt");
        if(!f.isFile())return "";
        try(BufferedReader br=new BufferedReader(new FileReader(f))) {
            String line=br.readLine();
            return line==null?"":line;
        }catch(IOException e){return "";}
    }
    public static synchronized void startSetup(Context c) {
        Context app=c.getApplicationContext();
        File dir=new File(ModelStorage.modelsDir(app),MODEL);
        if(new File(dir,"finished").isFile()) {
            status="READY — MeinaHentai v2.1";return;
        }
        if(started)return;
        started=true;
        status="Preparing included model. Keep this screen open.";
        File error=new File(app.getFilesDir(),"velvet-model-error.txt");
        if(error.exists())error.delete();
        Thread t=new Thread(()->install(app),"Velvet-Model-Setup");
        t.setPriority(Thread.NORM_PRIORITY-1);
        t.start();
    }
    @Override public boolean onCreate() {
        Context c=getContext();
        if(c!=null)startSetup(c);
        return true;
    }
    private static void notifyState(Context c, String message, boolean done) {
        status=message;
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
        File dir = new File(ModelStorage.modelsDir(c), MODEL);
        try {
            StatFs stat = new StatFs(ModelStorage.modelsDir(c).getAbsolutePath());
            if (stat.getAvailableBytes() < 6L * 1024 * 1024 * 1024)
                throw new IOException("At least 6 GB free on selected storage (SD card or internal) for conversion.");
            notifyState(c,"Reading 2.13 GB bundled model into local storage",false);
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                android.widget.Toast.makeText(c,"Installing included anime model. Reopen after setup is complete.",android.widget.Toast.LENGTH_LONG).show());
            if (dir.exists()) deleteTree(dir);
            if (!dir.mkdirs()) throw new IOException("Cannot create model directory");
            File original = new File(dir,"model.safetensors");
            String[] parts=c.getAssets().list(ASSET_FOLDER);
            if(parts==null) throw new IOException("Model chunks are missing from APK");
            Arrays.sort(parts);
            MessageDigest digest=MessageDigest.getInstance("SHA-256");
            int found=0;
            try (OutputStream out = new BufferedOutputStream(new FileOutputStream(original), 1024*1024)) {
                byte[] buf = new byte[1024*1024];
                for(String part:parts) {
                    if(!part.startsWith("part-") || !part.endsWith(".bin")) continue;
                    found++;
                    notifyState(c,"Unpacking bundled checkpoint part "+found+" of "+parts.length,false);
                    try(InputStream in=c.getAssets().open(ASSET_FOLDER+"/"+part,2)) {
                        int count;
                        while((count=in.read(buf))!=-1) {
                            out.write(buf,0,count);
                            digest.update(buf,0,count);
                        }
                    }
                }
            }
            if(found<2) throw new IOException("Bundled weights are incomplete: "+found+" pieces");
            byte[] sha=digest.digest();
            StringBuilder got=new StringBuilder();
            for(byte b:sha) got.append(String.format("%02x",b & 255));
            if(!MODEL_SHA256.equals(got.toString())) throw new IOException("Bundled model SHA256 verification failed");
            notifyState(c,"Converting model for ARM64 Android. Keep app open.",false);
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
                    if(line.length()>4 && line.length()<140 &&
                       (line.toLowerCase().contains("convert") || line.toLowerCase().contains("%") ||
                        line.toLowerCase().contains("done") || line.toLowerCase().contains("error"))) {
                        status="Converting model: "+line;
                    }
                }
            }
            int result=proc.waitFor();
            if (result!=0 || !new File(dir,"finished").exists())
                throw new IOException("Conversion failed (exit "+result+"): "+lastLine);
            original.delete();
            new File(dir,"clip_skip_1.mnn").delete();
            new File(dir,"clip_skip_2.mnn").delete();
            try (java.io.FileWriter config = new java.io.FileWriter(new File(dir,"config.json"))) {
                config.write("{\"default_prompt\":\"masterpiece, best quality, adult woman, anime art, detailed lighting\",\"default_negative_prompt\":\"worst quality, low quality, poorly drawn hands, extra fingers, bad anatomy, child, underage\",\"default_steps\":20,\"default_cfg\":7,\"default_scheduler\":\"euler_a\"}");
            }
            notifyState(c,"READY — MeinaHentaiV21 installed. Open Generator now.",true);
        } catch(Throwable e) {
            Log.e(TAG,"Bundled model preparation failed",e);
            lastError=String.valueOf(e.getMessage());
            try(FileWriter w=new FileWriter(new File(c.getFilesDir(),"velvet-model-error.txt"))) {
                w.write(lastError.replace('\n',' ').replace('\r',' ').substring(0,Math.min(500,lastError.length())));
            }catch(Exception ignored){}
            notifyState(c,"FAILED — model setup. Tap Retry in Velvet Atelier.",true);
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
