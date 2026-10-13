package org.velvetatelier;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.graphics.Color;
import android.graphics.Typeface;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * LocalDream SD1.5 automated MULTI-PASS prompt conditioner.
 * Each chunk is encoded by native CLIP separately (no native >77 context).
 * The result becomes the following img2img init image, with low denoise.
 * NOTE: not equivalent to single-pass 154-/231-token CLIP concatenation.
 */
public final class LongPromptRunnerActivity extends Activity {
    public static final String EXTRA_POSITIVE="velvet_positive";
    public static final String EXTRA_NEGATIVE="velvet_negative";
    public static final String EXTRA_STEPS="velvet_steps";
    public static final String EXTRA_CFG="velvet_cfg";
    public static final String EXTRA_SEED="velvet_seed";
    public static final String EXTRA_SCHEDULER="velvet_scheduler";
    public static final String EXTRA_SIZE="velvet_size";
    private static final int SAVE=3011;
    private static final String HOST="http://127.0.0.1:8081";
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final AtomicBoolean cancel=new AtomicBoolean(false);
    private volatile boolean busy=false,dead=false;
    private volatile HttpURLConnection currentConnection;
    private String prompt,negative,scheduler;
    private int width=512,height=512,steps=24;
    private float cfg=6.5f;
    private long seed=-1;
    private ImageView preview;
    private TextView status;
    private Button start,stop,save;
    private Bitmap finished;

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        Intent input=getIntent();
        prompt=input.getStringExtra(EXTRA_POSITIVE);
        negative=input.getStringExtra(EXTRA_NEGATIVE);
        scheduler=input.getStringExtra(EXTRA_SCHEDULER);
        if(negative==null)negative="bad anatomy, deformed face";
        if(scheduler==null)scheduler="euler_a";
        steps=Math.max(10,Math.min(50,input.getIntExtra(EXTRA_STEPS,24)));
        cfg=input.getFloatExtra(EXTRA_CFG,6.5f);
        seed=input.getLongExtra(EXTRA_SEED,-1);
        int requested=input.getIntExtra(EXTRA_SIZE,512);
        width=height=requested==384?384:512;
        LinearLayout body=new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(20,20,20,26);
        body.setBackgroundColor(0xff1b1622);
        ScrollView scroll=new ScrollView(this);
        scroll.addView(body);
        text(body,"LONG PROMPT STUDIO",24,Color.WHITE,true);
        text(body,"Runs multiple CLIP-length prompts sequentially through the same OFFLINE image model. Later passes refine the previous output via low-denoise img2img. Not the same as a larger native attention window.",14,0xffd7c8e5,false);
        status=text(body,"Ready. Select Start when Meina model setup is complete.",14,0xffd7c8e5,false);
        preview=new ImageView(this);
        preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        preview.setBackgroundColor(0xff2f2838);
        body.addView(preview,new LinearLayout.LayoutParams(-1,550));
        start=button(body,"Start local multi-pass generation",this::begin);
        stop=button(body,"Cancel",()->{cancel.set(true);HttpURLConnection c=currentConnection;if(c!=null)c.disconnect();});
        save=button(body,"Save final PNG",this::export);
        stop.setEnabled(false);save.setEnabled(false);
        text(body,"The model must have completed first-run conversion. Performance and image fidelity will vary with each pass; additional passes can change composition. This activity never uploads images to the internet.",13,0xffbca5ce,false);
        setContentView(scroll);
    }
    private static TextView text(LinearLayout v,String value,int sp,int color,boolean bold){
        TextView t=new TextView(v.getContext());
        t.setText(value);t.setTextSize(sp);t.setTextColor(color);
        if(bold)t.setTypeface(null,Typeface.BOLD);
        t.setPadding(0,8,0,14);v.addView(t);
        return t;
    }
    private Button button(LinearLayout v,String title,Runnable callback){
        Button b=new Button(this);b.setText(title);b.setAllCaps(false);
        b.setOnClickListener(x->callback.run());v.addView(b);
        return b;
    }
    private void ui(String message){
        handler.post(()->{if(!dead)status.setText(message);});
    }
    private void begin(){
        if(busy)return;
        if(prompt==null||prompt.trim().isEmpty()){ui("No prompt received");return;}
        if(!IncludedModelProvider.modelReady(
            new java.io.File(ModelStorage.modelsDir(this),"MeinaHentaiV21"))){
            ui("The included Meina model is not READY. Return to the home screen and finish setup.");
            return;
        }
        busy=true;cancel.set(false);start.setEnabled(false);stop.setEnabled(true);save.setEnabled(false);
        worker.execute(()->{
            try{
                startBackend();
                if(!waitForBackend())throw new IOException(
                    "Local inference backend did not become ready. Check model conversion and available RAM.");
                List<String> prompts=exactPasses(prompt);
                List<String> negatives=exactPasses(negative);
                int count=Math.max(prompts.size(),negatives.size());
                if(count>12)throw new IOException("Too many passes ("+count+"). Reduce prompt length.");
                ui("Backend ready. "+count+" pass(es); native BPE validation complete.");
                Bitmap current=null;
                for(int i=0;i<count;i++){
                    if(cancel.get())throw new IOException("Generation cancelled");
                    String positive=prompts.get(Math.min(i,prompts.size()-1));
                    String negativePass=negatives.get(Math.min(i,negatives.size()-1));
                    int passIndex=i;
                    ui("Pass "+(i+1)+"/"+count+" · generating...");
                    Bitmap next=generate(positive,negativePass,
                        i==0?steps:Math.min(18,steps),
                        i==0?seed:-1,current,passIndex,count);
                    if(current!=null && current!=next)current.recycle();
                    current=next;
                    // Only display the final result: showing a temporary
                    // bitmap while recycling it on the next pass can cause
                    // "Canvas: trying to use a recycled bitmap" on Android.
                    ui("Completed pass "+(passIndex+1)+"/"+count
                        +"; retaining just the current working image.");
                }
                final Bitmap output=current;
                handler.post(()->{
                    if(dead){output.recycle();return;}
                    if(finished!=null && finished!=output)finished.recycle();
                    finished=output;
                    preview.setImageBitmap(output);
                    save.setEnabled(true);
                    status.setText("All "+count+" passes completed locally. Save the final PNG.");
                    finishBusy();
                });
            }catch(Exception ex){
                ui("Long prompt failed: "+ex.getMessage());
                handler.post(this::finishBusy);
            }finally{
                currentConnection=null;
                stopBackend();
            }
        });
    }
    private void finishBusy(){
        busy=false;if(dead)return;
        start.setEnabled(true);stop.setEnabled(false);
    }
    private void startBackend(){
        Intent service=new Intent().setClassName(this,
            "io.github.xororz.localdream.service.BackendService");
        service.putExtra("modelId","MeinaHentaiV21");
        service.putExtra("backendType","sd15cpu");
        service.putExtra("width",width);
        service.putExtra("height",height);
        if(Build.VERSION.SDK_INT>=26)startForegroundService(service);
        else startService(service);
    }
    private void stopBackend(){
        try{
            Intent service=new Intent().setClassName(this,
                "io.github.xororz.localdream.service.BackendService");
            service.setAction("io.github.xororz.localdream.STOP_GENERATION");
            startService(service);
        }catch(Exception ignored){}
    }
    private boolean waitForBackend(){
        for(int i=0;i<90;i++){
            if(cancel.get())return false;
            try{
                HttpURLConnection c=open("/health","GET");
                int status=c.getResponseCode();c.disconnect();
                if(status==200)return true;
            }catch(Exception ignored){}
            try{Thread.sleep(2000);}catch(InterruptedException ex){Thread.currentThread().interrupt();return false;}
        }
        return false;
    }
    private HttpURLConnection open(String path,String method)throws IOException{
        HttpURLConnection c=(HttpURLConnection)new URL(HOST+path).openConnection();
        c.setRequestMethod(method);
        c.setConnectTimeout(3000);
        c.setReadTimeout(method.equals("GET")?4000:900000);
        if(method.equals("POST")){
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type","application/json");
        }
        currentConnection=c;
        return c;
    }
    private JSONObject postJson(String path,JSONObject payload)throws Exception{
        HttpURLConnection c=open(path,"POST");
        try{
            byte[] data=payload.toString().getBytes("UTF-8");
            try(OutputStream out=c.getOutputStream()){out.write(data);}
            int http=c.getResponseCode();
            if(http!=200)throw new IOException(path+" status "+http);
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();
            try(InputStream in=c.getInputStream()){
                byte[] buf=new byte[2048];int n;
                while((n=in.read(buf))!=-1){
                    if(bytes.size()+n>2_000_000)throw new IOException("Tokenizer response too long");
                    bytes.write(buf,0,n);
                }
            }
            return new JSONObject(bytes.toString("UTF-8"));
        }finally{c.disconnect();}
    }
    private int nativeTokenCount(String text)throws Exception{
        JSONObject in=new JSONObject();in.put("prompt",text);
        return postJson("/tokenize",in).getInt("count");
    }
    private List<String> exactPasses(String input)throws Exception{
        List<String> result=new ArrayList<>();
        // Rough tag-first segmentation, then exact SD1.5 BPE token
        // verification through the real on-phone /tokenize endpoint.
        LongPromptPlanner.Plan draft=LongPromptPlanner.plan(input);
        for(String part:draft.passes)splitVerified(part,result,0);
        if(result.size()>12)throw new IOException("More than 12 token-safe passes");
        return result;
    }
    private void splitVerified(String text,List<String> parts,int depth)throws Exception{
        if(cancel.get())throw new IOException("Cancelled");
        int tokenCount=nativeTokenCount(text);
        if(tokenCount<=77){parts.add(text);return;}
        if(depth>=12)throw new IOException("A prompt fragment cannot fit native CLIP");
        int cut=text.lastIndexOf(',',text.length()/2);
        if(cut<10)cut=text.lastIndexOf(' ',text.length()/2);
        if(cut<2)throw new IOException("Cannot split token-heavy phrase: "+text.substring(0,Math.min(text.length(),55)));
        splitVerified(text.substring(0,cut).trim(),parts,depth+1);
        splitVerified(text.substring(cut+1).trim(),parts,depth+1);
    }
    private Bitmap generate(String positive,String negativePrompt,int stepCount,
                            long stepSeed,Bitmap source,int index,int total)throws Exception{
        JSONObject req=new JSONObject();
        req.put("prompt",positive);
        req.put("negative_prompt",negativePrompt);
        req.put("steps",stepCount);
        req.put("cfg",cfg);
        req.put("width",width);req.put("height",height);
        req.put("scheduler",scheduler);
        req.put("use_opencl",false);
        req.put("show_diffusion_process",false);
        req.put("preview_format","jpeg");
        req.put("output_format","raw");
        req.put("denoise_strength",source==null?0.6f:0.28f);
        if(stepSeed>=0)req.put("seed",stepSeed);
        if(source!=null){
            ByteArrayOutputStream png=new ByteArrayOutputStream();
            if(!source.compress(Bitmap.CompressFormat.PNG,100,png))
                throw new IOException("Cannot encode intermediate PNG");
            req.put("image",Base64.encodeToString(png.toByteArray(),Base64.NO_WRAP));
        }
        HttpURLConnection c=open("/generate","POST");
        try{
            try(OutputStream out=c.getOutputStream()){
                out.write(req.toString().getBytes("UTF-8"));
            }
            if(c.getResponseCode()!=200)
                throw new IOException("Generation endpoint returned "+c.getResponseCode());
            try(BufferedReader reader=new BufferedReader(new InputStreamReader(c.getInputStream(),"UTF-8"))){
                String line;
                while((line=reader.readLine())!=null){
                    if(cancel.get())throw new IOException("Cancelled");
                    if(!line.startsWith("data: "))continue;
                    String payload=line.substring(6).trim();
                    if("[DONE]".equals(payload))break;
                    JSONObject event=new JSONObject(payload);
                    String type=event.optString("type","");
                    if("progress".equals(type)){
                        int step=event.optInt("step",0);
                        ui("Pass "+(index+1)+"/"+total+": "+step+"/"+stepCount+" steps");
                    }else if("error".equals(type)){
                        throw new IOException(event.optString("message","Native generation error"));
                    }else if("complete".equals(type)){
                        byte[] bytes=Base64.decode(event.getString("image"),Base64.DEFAULT);
                        if("raw".equals(event.optString("format","raw"))){
                            int w=event.optInt("width",width),h=event.optInt("height",height);
                            if(w<1||h<1||w*h>1024*1024||bytes.length!=w*h*3)
                                throw new IOException("Invalid local image response");
                            int[] pixels=new int[w*h];int j=0;
                            for(int p=0;p<pixels.length;p++){
                                pixels[p]=0xff000000|((bytes[j++]&255)<<16)
                                    |((bytes[j++]&255)<<8)|(bytes[j++]&255);
                            }
                            Bitmap bitmap=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
                            bitmap.setPixels(pixels,0,w,0,0,w,h);
                            return bitmap;
                        }
                        Bitmap image=BitmapFactory.decodeByteArray(bytes,0,bytes.length);
                        if(image==null)throw new IOException("Invalid local bitmap result");
                        return image;
                    }
                }
            }
            throw new IOException("Generation ended before final image");
        }finally{c.disconnect();currentConnection=null;}
    }
    private void export(){
        if(finished==null||busy)return;
        Intent saveIntent=new Intent(Intent.ACTION_CREATE_DOCUMENT);
        saveIntent.addCategory(Intent.CATEGORY_OPENABLE);
        saveIntent.setType("image/png");
        saveIntent.putExtra(Intent.EXTRA_TITLE,"Velvet_LongPrompt.png");
        startActivityForResult(saveIntent,SAVE);
    }
    @Override protected void onActivityResult(int request,int resultCode,Intent data){
        super.onActivityResult(request,resultCode,data);
        if(request!=SAVE||resultCode!=RESULT_OK||data==null||finished==null)return;
        final Bitmap image=finished;
        worker.execute(()->{
            try(OutputStream out=getContentResolver().openOutputStream(data.getData())){
                if(out==null||!image.compress(Bitmap.CompressFormat.PNG,100,out))
                    throw new IOException("Cannot export PNG");
                out.flush();ui("Final PNG saved.");
            }catch(Exception ex){ui("Export failed: "+ex.getMessage());}
        });
    }
    @Override protected void onDestroy(){
        dead=true;cancel.set(true);
        HttpURLConnection c=currentConnection;if(c!=null)c.disconnect();
        worker.shutdownNow();
        super.onDestroy();
    }
}
