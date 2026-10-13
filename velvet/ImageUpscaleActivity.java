package org.velvetatelier;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.graphics.Typeface;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Local export-first upscale editor.
 *
 * Unlike Local Dream's QNN-only learned upscaler this version works on CPU:
 *  - Pixel Perfect (nearest-neighbor)
 *  - Fast Smooth (bilinear)
 *  - High Quality (Lanczos3)
 *
 * Not a neural super-resolution model. No separate app, no Internet, no
 * external file permissions, no hidden file uploads.
 */
public final class ImageUpscaleActivity extends Activity {
    private static final int PICK=601,SAVE=602;
    private static final int BG=0xff181421,FG=0xfff7edf9,PURPLE=0xff765090;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    private final AtomicBoolean cancel=new AtomicBoolean(false);
    private volatile boolean busy=false;
    private Bitmap original;
    private Bitmap result;
    private Spinner scaleSelector,methodSelector;
    private TextView info,progressText;
    private ImageView image;
    private ProgressBar progress;
    private Button exportButton,runButton,cancelButton;
    private volatile boolean disposed=false;

    @Override public void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        ScrollView scroll=new ScrollView(this);
        scroll.setBackgroundColor(BG);
        scroll.setFillViewport(true);
        LinearLayout body=new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(18),dp(22),dp(18),dp(32));
        scroll.addView(body);
        title(body,"IMAGE UPSCALER",24);
        note(body,"Offline CPU image enlargement · 2× / 3× / 4× · includes transparent PNG and pixel art");
        note(body,"High Quality uses Lanczos3 sampling; this is not an AI super-resolution model. Works even when the original model's NPU upscaler is unavailable.");
        action(body,"Choose image from Gallery / AI Generator",this::pickImage);
        image=new ImageView(this);
        image.setAdjustViewBounds(false);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setBackgroundColor(0xff2a2431);
        LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(-1,dp(300));
        ip.topMargin=dp(10);body.addView(image,ip);
        info=note(body,"Choose an image to see output dimensions.");
        note(body,"Upscale factor");
        scaleSelector=choice(body,new String[]{"2×","3×","4×"});
        note(body,"Image type / resampling");
        methodSelector=choice(body,new String[]{
            "High quality · Lanczos3",
            "Fast smooth · bilinear",
            "Pixel-perfect · nearest neighbor"});
        scaleSelector.setSelection(0);
        methodSelector.setSelection(0);
        android.widget.AdapterView.OnItemSelectedListener listener=
            new android.widget.AdapterView.OnItemSelectedListener(){
                @Override public void onItemSelected(android.widget.AdapterView<?> p,View v,int i,long l){
                    refreshDimensions();
                }
                @Override public void onNothingSelected(android.widget.AdapterView<?> p){}
            };
        scaleSelector.setOnItemSelectedListener(listener);
        methodSelector.setOnItemSelectedListener(listener);
        progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);progress.setVisibility(View.GONE);
        body.addView(progress);
        progressText=note(body,"Limit: 12 megapixels / 4096 pixels per side, to protect mobile memory.");
        runButton=action(body,"Upscale in this app",this::upscale);
        cancelButton=action(body,"Cancel current upscale",()->cancel.set(true));
        cancelButton.setEnabled(false);
        exportButton=action(body,"Save upscaled PNG",this::save);
        exportButton.setEnabled(false);
        note(body,"For actual learned AI upscaling, Local Dream also has a separate upscaler page. Its current native model uses an NPU runtime and may not work with a CPU-only checkpoint. This CPU fallback always stays available.");
        setContentView(scroll);
        // Images shared from Gallery, Files or other drawing apps can arrive
        // directly in the same integrated Upscale Studio.
        Intent incoming=getIntent();
        Uri shared=null;
        if(Intent.ACTION_SEND.equals(incoming.getAction()) &&
            incoming.getType()!=null && incoming.getType().startsWith("image/")){
            if(android.os.Build.VERSION.SDK_INT>=33)
                shared=incoming.getParcelableExtra(Intent.EXTRA_STREAM,Uri.class);
            else
                shared=(Uri)incoming.getParcelableExtra(Intent.EXTRA_STREAM);
        } else if(Intent.ACTION_VIEW.equals(incoming.getAction())){
            shared=incoming.getData();
        }
        if(shared!=null)loadFromUri(shared);
    }

    private void pickImage(){
        if(busy)return;
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("image/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent,PICK);
    }
    private void save(){
        if(result==null || busy)return;
        Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/png");
        intent.putExtra(Intent.EXTRA_TITLE,"Velvet_Upscaled_"+result.getWidth()+"x"+result.getHeight()+".png");
        startActivityForResult(intent,SAVE);
    }
    @Override protected void onActivityResult(int code,int status,Intent data){
        super.onActivityResult(code,status,data);
        if(status!=RESULT_OK||data==null||data.getData()==null)return;
        final Uri uri=data.getData();
        if(code==SAVE){
            if(result==null||busy)return;
            busy=true;
            runButton.setEnabled(false);exportButton.setEnabled(false);
            progressText.setText("Saving full resolution PNG on background thread…");
            final Bitmap snapshot=result;
            worker.execute(()->{
                try(OutputStream out=getContentResolver().openOutputStream(uri)){
                    if(out==null||!snapshot.compress(Bitmap.CompressFormat.PNG,100,out))
                        throw new IOException("PNG export failed");
                    out.flush();
                    main.post(()->{
                        if(!disposed)info.setText("Saved transparent PNG at full "
                            +snapshot.getWidth()+"×"+snapshot.getHeight()+" pixels.");
                        finishBusy();
                    });
                }catch(Exception e){
                    main.post(()->{showError(e);finishBusy();});
                }
            });
        }else if(code==PICK){
            loadFromUri(uri);
        }
    }
    private void loadFromUri(Uri uri){
            if(busy)return;
            busy=true;
            runButton.setEnabled(false);
            exportButton.setEnabled(false);
            info.setText("Loading source image…");
            worker.execute(()->{
                try{
                    Bitmap source=decodeSource(uri);
                    main.post(()->{
                        if(disposed){source.recycle();return;}
                        if(original!=null && original!=source)original.recycle();
                        if(result!=null && result!=source){result.recycle();result=null;}
                        original=source;
                        image.setImageBitmap(source);
                        exportButton.setEnabled(false);
                        refreshDimensions();
                        finishBusy();
                    });
                }catch(Exception e){
                    main.post(()->{showError(e);finishBusy();});
                }
            });
    }
    private Bitmap decodeSource(Uri uri)throws IOException{
        BitmapFactory.Options bounds=new BitmapFactory.Options();
        bounds.inJustDecodeBounds=true;
        try(InputStream stream=getContentResolver().openInputStream(uri)){
            if(stream==null)throw new IOException("No image stream");
            BitmapFactory.decodeStream(stream,null,bounds);
        }
        if(bounds.outWidth<1||bounds.outHeight<1)
            throw new IOException("Unknown image format");
        BitmapFactory.Options options=new BitmapFactory.Options();
        options.inPreferredConfig=Bitmap.Config.ARGB_8888;
        options.inSampleSize=1;
        // Large originals are decoded with downsampling. Tell the user the
        // actual decoded size; never pretend output is native source resolution.
        while(Math.max(bounds.outWidth,bounds.outHeight)/options.inSampleSize>1536
              || (long)bounds.outWidth*bounds.outHeight/
                 (options.inSampleSize*options.inSampleSize)>3_000_000L){
            options.inSampleSize*=2;
        }
        try(InputStream stream=getContentResolver().openInputStream(uri)){
            if(stream==null)throw new IOException("Cannot read image");
            Bitmap bitmap=BitmapFactory.decodeStream(stream,null,options);
            if(bitmap==null)throw new IOException("Image decoding failed");
            return bitmap;
        }
    }
    private void refreshDimensions(){
        if(info==null||scaleSelector==null)return;
        if(original==null){info.setText("Select image first");return;}
        int factor=scaleSelector.getSelectedItemPosition()+2;
        long outW=(long)original.getWidth()*factor;
        long outH=(long)original.getHeight()*factor;
        String extra="";
        if(outW>4096 || outH>4096 || outW*outH>12_000_000)
            extra=" · Too large: choose a smaller scale.";
        info.setText(String.format(Locale.US,
            "Decoded input %d×%d → target %d×%d (%d×)%s",
            original.getWidth(),original.getHeight(),outW,outH,factor,extra));
    }
    private void upscale(){
        if(original==null||busy)return;
        // Release references to older upscaled bitmaps before starting a new
        // memory-intensive job. The view must stop holding the old Bitmap.
        image.setImageBitmap(original);
        if(result!=null){result.recycle();result=null;}
        final int factor=scaleSelector.getSelectedItemPosition()+2;
        final int selected=methodSelector.getSelectedItemPosition();
        final int method=selected==0?ImageResampler.HIGH_QUALITY:
            selected==1?ImageResampler.FAST_SMOOTH:ImageResampler.PIXEL_PERFECT;
        final Bitmap input=original;
        busy=true;cancel.set(false);
        runButton.setEnabled(false);
        cancelButton.setEnabled(true);
        exportButton.setEnabled(false);
        progress.setVisibility(View.VISIBLE);progress.setProgress(0);
        progressText.setText("Upscaling locally, without a cloud service…");
        worker.execute(()->{
            Bitmap output=null;
            try{
                int sw=input.getWidth(),sh=input.getHeight();
                long dw=(long)sw*factor,dh=(long)sh*factor;
                if(dw>4096||dh>4096||dw*dh>12_000_000)
                    throw new IllegalArgumentException(
                        "Output exceeds 12MP/4096 side. Choose smaller scale.");
                int[] source=new int[sw*sh];
                input.getPixels(source,0,sw,0,0,sw,sh);
                output=Bitmap.createBitmap((int)dw,(int)dh,Bitmap.Config.ARGB_8888);
                final Bitmap destination=output;
                final int destWidth=(int)dw,destHeight=(int)dh;
                ImageResampler.resize(source,sw,sh,factor,method,
                    (y,row)->{
                        destination.setPixels(row,0,destWidth,0,y,destWidth,1);
                        if(y%Math.max(1,destHeight/25)==0){
                            final int percent=(int)(100L*y/destHeight);
                            main.post(()->{
                                if(!disposed){
                                    progress.setProgress(percent);
                                    progressText.setText("Processing "+percent+"%");
                                }
                            });
                        }
                    },()->cancel.get()||Thread.currentThread().isInterrupted());
                if(cancel.get())throw new IllegalStateException("Upscale cancelled");
                final Bitmap completed=output;
                main.post(()->{
                    if(disposed){completed.recycle();return;}
                    if(result!=null && result!=completed)result.recycle();
                    result=completed;
                    image.setImageBitmap(completed);
                    progress.setProgress(100);
                    progressText.setText("Finished: "+completed.getWidth()+"×"+completed.getHeight()+" PNG ready.");
                    exportButton.setEnabled(true);
                    finishBusy();
                });
            }catch(OutOfMemoryError oom){
                if(output!=null)output.recycle();
                main.post(()->{
                    if(!disposed)showError(
                        new IOException("Not enough memory. Use 2× or smaller source image."));
                    finishBusy();
                });
            }catch(Exception e){
                if(output!=null)output.recycle();
                main.post(()->{
                    if(!disposed){
                        progressText.setText(e.getMessage());
                        if(!cancel.get())showError(e);
                    }
                    finishBusy();
                });
            }
        });
    }
    private void finishBusy(){
        busy=false;
        if(disposed)return;
        runButton.setEnabled(true);
        cancelButton.setEnabled(false);
        progress.setVisibility(View.GONE);
        exportButton.setEnabled(result!=null);
    }
    private void showError(Exception e){
        if(disposed)return;
        new AlertDialog.Builder(this).setTitle("Upscale")
            .setMessage(e.getMessage()==null?"Could not process image":e.getMessage())
            .setPositiveButton("OK",null).show();
    }
    @Override protected void onDestroy(){
        disposed=true;cancel.set(true);
        worker.shutdownNow();
        super.onDestroy();
    }
    private void title(LinearLayout v,String text,int sp){
        TextView t=note(v,text);t.setTextColor(FG);t.setTextSize(sp);
        t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
    }
    private TextView note(LinearLayout v,String text){
        TextView t=new TextView(this);
        t.setText(text);t.setTextSize(14);t.setTextColor(0xffc9bed4);
        t.setPadding(0,dp(7),0,dp(8));v.addView(t);return t;
    }
    private Spinner choice(LinearLayout v,String[] options){
        Spinner s=new Spinner(this);
        s.setAdapter(new ArrayAdapter<>(this,
            android.R.layout.simple_spinner_dropdown_item,options));
        v.addView(s);return s;
    }
    private Button action(LinearLayout v,String label,Runnable work){
        Button b=new Button(this);b.setText(label);b.setAllCaps(false);
        b.setTextColor(FG);
        b.setBackgroundTintList(ColorStateList.valueOf(PURPLE));
        b.setOnClickListener(x->work.run());
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(54));
        lp.bottomMargin=dp(5);v.addView(b,lp);return b;
    }
    private int dp(float dp){return (int)(dp*getResources().getDisplayMetrics().density+0.5f);}
}
