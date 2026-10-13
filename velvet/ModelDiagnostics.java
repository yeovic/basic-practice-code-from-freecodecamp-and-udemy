package org.velvetatelier;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Build;
import android.os.StatFs;
import java.io.File;
import java.util.Locale;

/** Human-readable, offline diagnostics. No network calls or telemetry. */
public final class ModelDiagnostics {
    private ModelDiagnostics(){}
    private static String bytes(long n){
        if(n<0)return "unknown";
        return String.format(Locale.US,"%.2f GB",n/1000000000.0);
    }
    public static String report(Context context){
        StringBuilder out=new StringBuilder(680);
        out.append("Velvet Atelier — local device report\n");
        out.append("Device: ").append(Build.MANUFACTURER).append(" ")
            .append(Build.MODEL).append("\n");
        out.append("Android: ").append(Build.VERSION.RELEASE).append("\n");
        out.append("Architecture: ").append(Build.SUPPORTED_ABIS.length>0?
            Build.SUPPORTED_ABIS[0]:"unknown").append("\n");
        ActivityManager am=(ActivityManager)context.getSystemService(Context.ACTIVITY_SERVICE);
        if(am!=null){
            ActivityManager.MemoryInfo mem=new ActivityManager.MemoryInfo();
            am.getMemoryInfo(mem);
            out.append("Device RAM: ").append(bytes(mem.totalMem)).append("\n");
            out.append("RAM currently free: ").append(bytes(mem.availMem)).append("\n");
            out.append("Android low-memory warning: ").append(mem.lowMemory).append("\n");
            out.append("Low-RAM device class: ").append(am.isLowRamDevice()).append("\n");
        }
        out.append("Java heap ceiling: ").append(bytes(Runtime.getRuntime().maxMemory())).append("\n");
        int storageMode=ModelStorage.getMode(context);
        out.append("Model storage mode: ").append(storageMode==ModelStorage.MODE_SPEED?
            "Internal speed":storageMode==ModelStorage.MODE_SPACE?"Removable space":"Automatic")
            .append("\n");
        File models=ModelStorage.modelsDir(context);
        File installed=new File(models,"MeinaHentaiV21");
        out.append("Active model directory: ").append(installed.getAbsolutePath()).append("\n");
        try{
            StatFs fs=new StatFs(models.getAbsolutePath());
            out.append("Model volume free: ").append(bytes(fs.getAvailableBytes())).append("\n");
            out.append("Model volume total: ").append(bytes(fs.getTotalBytes())).append("\n");
        }catch(Exception e){out.append("Storage diagnostics unavailable: ").append(e.getMessage()).append("\n");}
        out.append("Model READY (full asset check): ")
            .append(IncludedModelProvider.modelReady(installed)).append("\n");
        if(!IncludedModelProvider.modelReady(installed))
            out.append("Missing runtime assets: ")
                .append(ModelArtifactVerifier.missing(installed)).append("\n");
        File savedCheckpoint=new File(installed,"model.safetensors");
        out.append("Saved safetensors checkpoint: ")
            .append(savedCheckpoint.isFile()?bytes(savedCheckpoint.length()):"none")
            .append("\n");
        out.append("First-run installation busy: ")
            .append(IncludedModelProvider.isBusy()).append("\n");
        out.append("Model setup:\n").append(IncludedModelProvider.currentStatus(context)).append("\n");
        out.append("No benchmark has run: render performance must be measured on-device.\n");
        return out.toString();
    }
}
