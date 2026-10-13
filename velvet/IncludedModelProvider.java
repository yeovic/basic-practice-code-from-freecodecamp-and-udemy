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
import java.util.Locale;

public final class IncludedModelProvider extends ContentProvider {
    private static final String TAG = "VelvetModel";
    private static final String MODEL = "MeinaHentaiV21";
    private static final String ASSET_FOLDER = "included";
    private static final String MODEL_SHA256 = "deb40a068adf41aa2466dc8e76b516216af1c1afe721dfd23fc2c1108b4d152f";
    private static final String CHANNEL = "velvet_model_install";
    private static final long MODEL_BYTES = 2132651194L;
    private static final long FIRST_SETUP_BYTES = 6L * 1024 * 1024 * 1024;
    private static final long RETRY_CONVERSION_BYTES = 3L * 1024 * 1024 * 1024;
    private static final int NOTIFICATION_ID = 7101;
    private static volatile boolean started = false;
    public static boolean isBusy(){return started;}

    static boolean modelReady(File dir){
        return ModelArtifactVerifier.ready(dir);
    }
    private static volatile String status = "Checking included model";
    private static volatile String lastError = "";
    private static volatile int progressPercent = 0;

    public static String currentStatus(Context c) {
        File dir=new File(ModelStorage.modelsDir(c),MODEL);
        if(modelReady(dir))return "READY — MeinaHentai v2.1 installed in "+dir.getAbsolutePath();
        String state=status;
        if(state.startsWith("READY")) {
            state="NOT READY in the selected storage. Tap Retry / Continue Model Setup.";
        }
        String diagnostic=readLastError(c);
        if (!diagnostic.isEmpty()) state+="\nLast error: "+diagnostic;
        return state+"\nTarget folder: "+dir.getAbsolutePath();
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
        if(modelReady(dir)) {
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
            // The model might have a stale finished marker left from an
            // incomplete installation. Do not suppress a repair attempt.
            if(new File(dir,"finished").exists()&&!modelReady(dir)){
                if(!new File(dir,"finished").delete())
                    throw new IOException("Cannot remove stale finished marker");
            }
            // Preserve the downloaded checkpoint between failures. Repeatedly
            // unpacking 2.13 GB on conversion errors is slow and wears storage.
            if (!dir.isDirectory() && !dir.mkdirs())
                throw new IOException("Cannot create model directory: "+dir);

            File original = new File(dir, "model.safetensors");
            File staging = new File(dir, "model.safetensors.part");
            if (staging.exists() && !staging.delete())
                throw new IOException("Cannot remove incomplete model extraction");

            boolean haveVerifiedCheckpoint = false;
            if(original.isFile() && original.length() == MODEL_BYTES) {
                notifyState(c, "Found prior checkpoint. Verifying integrity before reuse.", false);
                haveVerifiedCheckpoint = MODEL_SHA256.equals(sha256File(original));
                if(!haveVerifiedCheckpoint) {
                    notifyState(c, "Old checkpoint checksum mismatch; re-extracting.", false);
                    if(!original.delete())
                        throw new IOException("Cannot remove corrupted checkpoint");
                }
            } else if (original.exists() && !original.delete()) {
                throw new IOException("Cannot remove truncated checkpoint");
            }
            StatFs stat = new StatFs(dir.getAbsolutePath());
            long required = haveVerifiedCheckpoint ? RETRY_CONVERSION_BYTES : FIRST_SETUP_BYTES;
            if(stat.getAvailableBytes() < required)
                throw new IOException("Insufficient space on selected storage: "
                    +(required/(1024*1024*1024))+" GB minimum needed for "
                    +(haveVerifiedCheckpoint?"model conversion":"initial extraction and conversion"));

            if(!haveVerifiedCheckpoint) {
                notifyState(c,"Extracting 2.13 GB included checkpoint. Keep app open.",false);
                String[] parts=c.getAssets().list(ASSET_FOLDER);
                if(parts==null)throw new IOException("Checkpoint chunks absent from APK");
                Arrays.sort(parts);
                MessageDigest digest=MessageDigest.getInstance("SHA-256");
                int found=0;
                int total=0;
                for(String p:parts)if(p.startsWith("part-")&&p.endsWith(".bin"))total++;
                try(OutputStream out=new BufferedOutputStream(new FileOutputStream(staging),1024*1024)) {
                    byte[] buf=new byte[256*1024];
                    for(String part:parts) {
                        if(!part.startsWith("part-")||!part.endsWith(".bin"))continue;
                        found++;
                        notifyState(c,"Unpacking checkpoint part "+found+" / "+total,false);
                        try(InputStream in=c.getAssets().open(ASSET_FOLDER+"/"+part,2)) {
                            int count;
                            while((count=in.read(buf))!=-1) {
                                out.write(buf,0,count);
                                digest.update(buf,0,count);
                            }
                        }
                    }
                }
                if(found!=total || total<2)
                    throw new IOException("Missing checkpoint chunks: "+found+"/"+total);
                if(staging.length()!=MODEL_BYTES)
                    throw new IOException("Checkpoint length mismatch: "+staging.length());
                String actual=hex(digest.digest());
                if(!MODEL_SHA256.equals(actual))
                    throw new IOException("Checkpoint checksum mismatch during extraction");
                if(!staging.renameTo(original))
                    throw new IOException("Cannot finalize verified checkpoint");
            } else {
                notifyState(c,"Verified saved checkpoint; skipping 2.13 GB extraction.",false);
            }

            notifyState(c,"Converting model for offline Android inference. Keep app open.",false);
            copyAssetTree(c,"cvtbase",dir);
            File clip = new File(dir,"clip_skip_2.mnn");
            if(!clip.isFile())throw new IOException("CLIP skip-2 base not included");
            copyFile(clip,new File(dir,"clip_v2.mnn"));
            String nativeDir=c.getApplicationInfo().nativeLibraryDir;
            File executable=new File(nativeDir,"libstable_diffusion_core.so");
            if(!executable.isFile())
                throw new IOException("Android ARM64 native converter not found");
            ProcessBuilder builder=new ProcessBuilder(
                executable.getAbsolutePath(),"--convert",dir.getAbsolutePath(),"--clip_skip_2");
            builder.directory(new File(nativeDir));
            builder.redirectErrorStream(true);
            Map<String,String> env=builder.environment();
            env.put("LD_LIBRARY_PATH",nativeDir+":/system/lib64:/vendor/lib64:/vendor/lib64/egl");
            env.put("DSP_LIBRARY_PATH",nativeDir);
            Process proc=builder.start();
            String lastLine="";
            try(BufferedReader reader=new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
                String line;
                while((line=reader.readLine())!=null) {
                    lastLine=line;
                    Log.i(TAG,line);
                    String lower=line.toLowerCase(Locale.ROOT);
                    if(line.length()>4 && line.length()<140 &&
                       (lower.contains("convert")||lower.contains("%")||
                        lower.contains("done")||lower.contains("error")))
                        status="Converting model: "+line;
                }
            }
            int code=proc.waitFor();
            if(code!=0 || !modelReady(dir))
                throw new IOException("Model conversion incomplete or failed (exit "+code
                   +"): "+lastLine+"; check native MNN weights");
            if(!original.delete())
                Log.w(TAG,"Unable to reclaim 2.13 GB checkpoint after conversion");
            new File(dir,"clip_skip_1.mnn").delete();
            new File(dir,"clip_skip_2.mnn").delete();
            org.json.JSONObject defaults=new org.json.JSONObject();
            defaults.put("default_prompt","masterpiece, best quality, adult woman, anime art, detailed lighting");
            defaults.put("default_negative_prompt","worst quality, low quality, poorly drawn hands, extra fingers, bad anatomy, child, underage");
            defaults.put("default_steps",20);
            defaults.put("default_cfg",7);
            defaults.put("default_scheduler","euler_a");
            try(FileWriter config=new FileWriter(new File(dir,"config.json"))) {
                config.write(defaults.toString());
            }
            notifyState(c,"READY — MeinaHentaiV21 installed. Open Generator now.",true);
        } catch(Throwable e) {
            Log.e(TAG,"Bundled model preparation failed",e);
            lastError=String.valueOf(e.getMessage());
            try(FileWriter w=new FileWriter(new File(c.getFilesDir(),"velvet-model-error.txt"))) {
                w.write(lastError.replace('\n',' ').replace('\r',' ')
                    .substring(0,Math.min(500,lastError.length())));
            }catch(Exception ignored){}
            notifyState(c,"FAILED — model setup. Tap Retry in Velvet Atelier.",true);
        } finally {
            synchronized(IncludedModelProvider.class){started=false;}
        }
    }

    private static String sha256File(File file) throws Exception {
        MessageDigest digest=MessageDigest.getInstance("SHA-256");
        try(InputStream in=new BufferedInputStream(new FileInputStream(file),1024*1024)){
            byte[] chunk=new byte[256*1024];
            int n;
            while((n=in.read(chunk))!=-1)digest.update(chunk,0,n);
        }
        return hex(digest.digest());
    }
    private static String hex(byte[] digest) {
        StringBuilder output=new StringBuilder(digest.length*2);
        for(byte b:digest){
            output.append(Character.forDigit((b>>>4)&0xf,16));
            output.append(Character.forDigit(b&0xf,16));
        }
        return output.toString();
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
