package org.velvetatelier;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Color;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import org.json.JSONObject;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Targeted SD1.5 IMG2IMG face refinement:
 * a user-selected crop is enlarged to a native 512px latent canvas, passed
 * through the existing local model, and composited back with feathering.
 * No separate model download, camera/face recognition, cloud or full-image
 * expensive high-resolution diffusion required.
 */
public final class FaceRepairStudioActivity extends Activity {
    private static final int PICK=8141,EXPORT=8142;
    private static final String HOST="http://127.0.0.1:8081";
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    private final AtomicBoolean cancel=new AtomicBoolean();
    private volatile HttpURLConnection active;
    private volatile boolean busy=false,disposed=false;
    private Bitmap input,result,outline;
    private ImageView preview;
    private TextView status;
    private SeekBar sizeSeek,denoiseSeek;
    private CheckBox gpu;
    private Button generateButton,saveButton;
    private float x=.5f,y=.28f;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll=new ScrollView(this);
        LinearLayout body=new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(22,22,22,36);
        body.setBackgroundColor(0xff171321);
        scroll.addView(body);
        label(body,"VELVET FACE REPAIR",23);
        label(body,"Select an AI image, then tap the face to choose its center. "
            +"The original Meina model redraws only a cropped face at 512px resolution "
            +"and blends it back into the original image. All work remains offline.",13);
        button(body,"Choose saved image",()->selectImage());
        preview=new ImageView(this);
        preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        preview.setBackgroundColor(0xff292431);
        body.addView(preview,new LinearLayout.LayoutParams(-1,580));
        preview.setOnTouchListener((v,e)->{
            if(e.getAction()!=MotionEvent.ACTION_UP||input==null||busy)return true;
            Matrix inverted=new Matrix();
            if(!preview.getImageMatrix().invert(inverted))return true;
            float[] p={e.getX(),e.getY()};
            inverted.mapPoints(p);
            x=Math.max(0,Math.min(1,p[0]/input.getWidth()));
            y=Math.max(0,Math.min(1,p[1]/input.getHeight()));
            updateOutline();return true;
        });
        status=label(body,"Choose a saved image, then tap its face.",13);
        label(body,"Crop coverage (larger for small faces)",13);
        sizeSeek=new SeekBar(this);sizeSeek.setMax(52);sizeSeek.setProgress(23);body.addView(sizeSeek);
        sizeSeek.setOnSeekBarChangeListener(seekListener(()->updateOutline()));
        label(body,"Face repair strength / denoising",13);
        denoiseSeek=new SeekBar(this);denoiseSeek.setMax(38);denoiseSeek.setProgress(14);body.addView(denoiseSeek);
        label(body,"Start near 0.30. Higher strength may change identity, expression or style.",12);
        gpu=new CheckBox(this);gpu.setText("Experimental OpenCL GPU (disable if unstable)");
        gpu.setTextColor(0xffe4d2f0);gpu.setChecked(false);body.addView(gpu);
        generateButton=button(body,"Repair selected face locally",this::startRepair);
        button(body,"Cancel current operation",()->{
            cancel.set(true);HttpURLConnection c=active;if(c!=null)c.disconnect();
        });
        saveButton=button(body,"Export repaired PNG",this::export);
        saveButton.setEnabled(false);
        setContentView(scroll);
        Intent i=getIntent();
        if(Intent.ACTION_SEND.equals(i.getAction()) && i.getType()!=null &&
           i.getType().startsWith("image/")){
            Uri u=Build.VERSION.SDK_INT>=33?
                i.getParcelableExtra(Intent.EXTRA_STREAM,Uri.class):
                (Uri)i.getParcelableExtra(Intent.EXTRA_STREAM);
            if(u!=null)load(u);
        }
    }
    private SeekBar.OnSeekBarChangeListener seekListener(Runnable callback){
        return new SeekBar.OnSeekBarChangeListener(){
            @Override public void onProgressChanged(SeekBar v,int p,boolean fromUser){callback.run();}
            @Override public void onStartTrackingTouch(SeekBar v){}
            @Override public void onStopTrackingTouch(SeekBar v){}
        };
    }
    private TextView label(LinearLayout body,String message,int font){
        TextView t=new TextView(this);t.setText(message);t.setTextColor(0xffe8dbed);
        t.setTextSize(font);t.setPadding(0,8,0,12);body.addView(t);return t;
    }
    private Button button(LinearLayout body,String caption,Runnable action){
        Button b=new Button(this);b.setText(caption);b.setAllCaps(false);
        b.setOnClickListener(v->action.run());body.addView(b);return b;
    }
    private void message(String s){main.post(()->{if(!disposed)status.setText(s);});}
    private void selectImage(){
        if(busy)return;
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.setType("image/*");i.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(i,PICK);
    }
    private void export(){
        if(result==null||busy)return;
        Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.setType("image/png");i.addCategory(Intent.CATEGORY_OPENABLE);
        i.putExtra(Intent.EXTRA_TITLE,"Velvet_Face_Repaired.png");
        startActivityForResult(i,EXPORT);
    }
    @Override protected void onActivityResult(int request,int code,Intent data){
        super.onActivityResult(request,code,data);
        if(code!=RESULT_OK||data==null||data.getData()==null)return;
        Uri uri=data.getData();
        if(request==PICK)load(uri);
        else if(request==EXPORT && result!=null){
            final Bitmap saved=result;
            worker.execute(()->{
                try(OutputStream out=getContentResolver().openOutputStream(uri)){
                    if(out==null||!saved.compress(Bitmap.CompressFormat.PNG,100,out))
                        throw new IOException("Cannot save image");
                    out.flush();message("Repaired image exported.");
                }catch(Exception ex){message("Export failed: "+ex.getMessage());}
            });
        }
    }
    private void load(Uri uri){
        if(busy)return;
        busy=true;generateButton.setEnabled(false);
        worker.execute(()->{
            try{
                BitmapFactory.Options options=new BitmapFactory.Options();
                options.inJustDecodeBounds=true;
                try(InputStream in=getContentResolver().openInputStream(uri)){
                    BitmapFactory.decodeStream(in,null,options);
                }
                if(options.outWidth<32||options.outHeight<32)
                    throw new IOException("Image too small");
                BitmapFactory.Options dec=new BitmapFactory.Options();
                dec.inPreferredConfig=Bitmap.Config.ARGB_8888;
                dec.inSampleSize=1;
                while((long)(options.outWidth/dec.inSampleSize)*
                    (options.outHeight/dec.inSampleSize)>3_000_000L ||
                    Math.max(options.outWidth,options.outHeight)/dec.inSampleSize>2048)
                    dec.inSampleSize*=2;
                Bitmap decoded;
                try(InputStream in=getContentResolver().openInputStream(uri)){
                    decoded=BitmapFactory.decodeStream(in,null,dec);
                }
                if(decoded==null)throw new IOException("Could not decode image");
                main.post(()->{
                    if(disposed){decoded.recycle();return;}
                    preview.setImageBitmap(null);
                    if(outline!=null){outline.recycle();outline=null;}
                    if(result!=null){result.recycle();result=null;}
                    if(input!=null)input.recycle();
                    input=decoded;x=.5f;y=.28f;
                    saveButton.setEnabled(false);
                    busy=false;generateButton.setEnabled(true);
                    updateOutline();
                });
            }catch(Exception ex){
                message("Image loading failed: "+ex.getMessage());
                main.post(()->{busy=false;if(!disposed)generateButton.setEnabled(true);});
            }
        });
    }
    private FacePatchEngine.Region selectedRegion(){
        return FacePatchEngine.region(input.getWidth(),input.getHeight(),x,y,
            .12f+sizeSeek.getProgress()*.01f);
    }
    private void updateOutline(){
        if(input==null||disposed)return;
        FacePatchEngine.Region r=selectedRegion();
        if(outline!=null)outline.recycle();
        outline=input.copy(Bitmap.Config.ARGB_8888,true);
        Canvas cv=new Canvas(outline);
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setColor(Color.CYAN);
        p.setStrokeWidth(3);p.setStyle(Paint.Style.STROKE);
        cv.drawRect(new Rect(r.left,r.top,r.left+r.size,r.top+r.size),p);
        preview.setImageBitmap(outline);
        status.setText("Selection: "+r.toString()+" — tap the face to reposition");
    }
    private void startRepair(){
        if(input==null||busy)return;
        if(!IncludedModelProvider.modelReady(
            new File(ModelStorage.modelsDir(this),"MeinaHentaiV21"))){
            message("First complete model setup in the Velvet home screen.");return;
        }
        final FacePatchEngine.Region r=selectedRegion();
        final boolean useGpu=gpu.isChecked();
        final float denoise=.16f+denoiseSeek.getProgress()*.01f;
        busy=true;cancel.set(false);
        generateButton.setEnabled(false);saveButton.setEnabled(false);
        worker.execute(()->{
            Bitmap crop=null,source=null,enhanced=null,newResult=null;
            try{
                message("Preparing face crop for local diffusion...");
                crop=Bitmap.createBitmap(input,r.left,r.top,r.size,r.size);
                source=Bitmap.createScaledBitmap(crop,512,512,true);
                startBackend();
                if(!waitForModel())throw new IOException("Local Dream backend not ready");
                message("Refining selected face with Meina SD1.5...");
                enhanced=redraw(source,denoise,useGpu);
                if(cancel.get())throw new IOException("Repair cancelled");
                Bitmap scaled=Bitmap.createScaledBitmap(enhanced,r.size,r.size,true);
                int w=input.getWidth(),h=input.getHeight();
                int[] sourcePixels=new int[w*h];
                input.getPixels(sourcePixels,0,w,0,0,w,h);
                int[] patchPixels=new int[r.size*r.size];
                scaled.getPixels(patchPixels,0,r.size,0,0,r.size,r.size);
                int[] blended=FacePatchEngine.composite(sourcePixels,w,h,patchPixels,r,
                    Math.max(3,r.size/7),.92f);
                newResult=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
                newResult.setPixels(blended,0,w,0,0,w,h);
                scaled.recycle();
                final Bitmap done=newResult;
                main.post(()->{
                    if(disposed){done.recycle();return;}
                    if(outline!=null){preview.setImageBitmap(null);outline.recycle();outline=null;}
                    if(result!=null && result!=done)result.recycle();
                    result=done;preview.setImageBitmap(done);
                    status.setText("Localized model-based face repair complete. Export PNG.");
                    saveButton.setEnabled(true);
                });
            }catch(Throwable ex){
                message("Face repair: "+(ex.getMessage()==null?ex.toString():ex.getMessage()));
                if(newResult!=null)newResult.recycle();
            }finally{
                if(crop!=null)crop.recycle();
                if(source!=null && source!=crop)source.recycle();
                if(enhanced!=null)enhanced.recycle();
                stopBackend();
                busy=false;main.post(()->{
                    if(!disposed)generateButton.setEnabled(true);
                });
            }
        });
    }
    private void startBackend() {
        Intent service=new Intent().setClassName(this,
            "io.github.xororz.localdream.service.BackendService");
        service.putExtra("modelId","MeinaHentaiV21");
        service.putExtra("backendType","sd15cpu");
        service.putExtra("width",512);service.putExtra("height",512);
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
    private boolean waitForModel(){
        for(int i=0;i<90;i++){
            if(cancel.get())return false;
            HttpURLConnection c=null;
            try{
                c=(HttpURLConnection)new URL(HOST+"/health").openConnection();
                c.setConnectTimeout(900);c.setReadTimeout(900);
                if(c.getResponseCode()==200)return true;
            }catch(Exception ignored){}finally{if(c!=null)c.disconnect();}
            try{Thread.sleep(1500);}catch(InterruptedException ex){Thread.currentThread().interrupt();return false;}
        }
        return false;
    }
    private Bitmap redraw(Bitmap face,float denoise,boolean useGpu)throws Exception{
        ByteArrayOutputStream bytes=new ByteArrayOutputStream(300000);
        if(!face.compress(Bitmap.CompressFormat.PNG,100,bytes))
            throw new IOException("Cannot encode face crop");
        JSONObject req=new JSONObject();
        req.put("prompt","adult anime face, same identity, same hair color, same art style, "
            +"(well drawn symmetric eyes:1.2), aligned pupils, coherent facial structure, "
            +"well drawn mouth, clean jawline, fine facial details");
        req.put("negative_prompt","distorted face, malformed eyes, uneven pupils, extra eyes, "
            +"cross eyes, melted facial features, warped lips, badly drawn mouth, "
            +"extra mouth, asymmetrical face");
        req.put("image",Base64.encodeToString(bytes.toByteArray(),Base64.NO_WRAP));
        req.put("steps",22);req.put("cfg",5.5);
        req.put("width",512);req.put("height",512);
        req.put("denoise_strength",denoise);
        req.put("use_opencl",useGpu);
        req.put("scheduler","euler_a");
        req.put("output_format","png");
        req.put("show_diffusion_process",false);
        HttpURLConnection connection=(HttpURLConnection)new URL(HOST+"/generate").openConnection();
        connection.setRequestMethod("POST");connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type","application/json");
        connection.setConnectTimeout(5000);connection.setReadTimeout(900000);
        active=connection;
        try{
            try(OutputStream out=connection.getOutputStream()){
                out.write(req.toString().getBytes("UTF-8"));
            }
            int response=connection.getResponseCode();
            if(response!=200)throw new IOException("Inference HTTP status: "+response);
            try(BufferedReader reader=new BufferedReader(
                    new InputStreamReader(connection.getInputStream(),"UTF-8"))){
                String line;
                while((line=reader.readLine())!=null){
                    if(cancel.get())throw new IOException("Cancelled");
                    if(!line.startsWith("data: "))continue;
                    JSONObject event=new JSONObject(line.substring(6).trim());
                    String type=event.optString("type");
                    if("progress".equals(type))
                        message("Refining face — "+event.optInt("step",0)
                            +"/"+event.optInt("total_steps",22));
                    else if("error".equals(type))
                        throw new IOException(event.optString("message","Model error"));
                    else if("complete".equals(type)){
                        byte[] png=Base64.decode(event.getString("image"),Base64.DEFAULT);
                        Bitmap out=BitmapFactory.decodeByteArray(png,0,png.length);
                        if(out==null||out.getWidth()!=512||out.getHeight()!=512)
                            throw new IOException("Invalid face patch returned by backend");
                        return out;
                    }
                }
            }
            throw new IOException("Model returned no repaired image");
        }finally{active=null;connection.disconnect();}
    }
    @Override protected void onDestroy(){
        disposed=true;cancel.set(true);
        HttpURLConnection c=active;if(c!=null)c.disconnect();
        worker.shutdownNow();
        super.onDestroy();
    }
}
