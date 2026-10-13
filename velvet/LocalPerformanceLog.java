package org.velvetatelier;

import android.content.Context;
import android.os.Build;
import android.os.Debug;
import android.os.PowerManager;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Offline, bounded performance journal for comparing cold vs warm SD1.5,
 * multiple passes, OpenCL, and different repair modes on real hardware.
 * No prompt, images, account IDs, or network access ever logged.
 */
public final class LocalPerformanceLog {
    private static final String FILE="velvet-local-perf.csv";
    private static final int MAX_ROWS=201;
    private LocalPerformanceLog(){}
    public static synchronized void record(Context c,String operation,
        int steps,int width,int height,long wallMs,long nativeMs,boolean gpu){
        if(c==null)return;
        String name=(operation==null?"unknown":operation).replaceAll("[^A-Za-z0-9_-]","_");
        if(name.length()>40)name=name.substring(0,40);
        int thermal=-1;
        try{
            if(Build.VERSION.SDK_INT>=29){
                PowerManager pm=(PowerManager)c.getSystemService(Context.POWER_SERVICE);
                if(pm!=null)thermal=pm.getCurrentThermalStatus();
            }
        }catch(Throwable ignored){}
        long pssKiB=-1;
        try{pssKiB=Debug.getPss();}catch(Throwable ignored){}
        String line=System.currentTimeMillis()+","+name+","+steps+","+width+","
            +height+","+wallMs+","+nativeMs+","+(gpu?1:0)+","
            +pssKiB+","+thermal+"\n";
        try{
            File data=new File(c.getFilesDir(),FILE);
            if(data.length()>96*1024)compact(data);
            try(FileOutputStream out=new FileOutputStream(data,true)){
                out.write(line.getBytes(StandardCharsets.UTF_8));
            }
        }catch(Exception ignored){}
    }
    private static void compact(File data)throws IOException{
        List<String> lines=Files.readAllLines(data.toPath(),StandardCharsets.UTF_8);
        int start=Math.max(0,lines.size()-MAX_ROWS);
        List<String> trimmed=new ArrayList<>(lines.subList(start,lines.size()));
        Files.write(data.toPath(),trimmed,StandardCharsets.UTF_8);
    }
    public static synchronized String report(Context c){
        File file=new File(c.getFilesDir(),FILE);
        String heading="Timestamp(ms),operation,steps,width,height,elapsed_ms,"
            +"native_inference_ms,opencl,pss_kib,thermal_status\n";
        if(!file.isFile())return heading+"No local render measurements yet.";
        try{
            List<String> lines=Files.readAllLines(file.toPath(),StandardCharsets.UTF_8);
            int start=Math.max(0,lines.size()-70);
            StringBuilder result=new StringBuilder(heading);
            for(int i=start;i<lines.size();i++)result.append(lines.get(i)).append('\n');
            return result.toString();
        }catch(Exception e){return heading+"Unable to read local log: "+e.getMessage();}
    }
}
