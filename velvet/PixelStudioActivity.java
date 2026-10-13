package org.velvetatelier;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.ClipData;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Entirely local pixel-art frame editor and sprite animation exporter.
 *
 * Workflow: Create an image in the bundled SD1.5 AI generator -> save it ->
 * import here -> pixelize with a single palette -> build/edit sprite frames ->
 * export transparent PNG / sprite ZIP / animation GIF.
 */
public final class PixelStudioActivity extends Activity {
    private static final int OPEN_IMAGE=301, SAVE_PNG=302, SAVE_GIF=303, SAVE_ZIP=304, SAVE_FRAME=305;
    private static final int BG=0xff17131f, FG=0xfff6eef8, MUTED=0xffcbbcd3;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private Bitmap original;
    private PixelEngine.Artwork artwork;
    private ArrayList<Bitmap> frames=new ArrayList<>();
    private int selectedFrame=0,logicalSize=64,paletteCount=32,frameCount=8,fps=8,intensity=3;
    private int inkIndex=1;
    private boolean playing=false,paintMode=false;
    private Spinner motionSpinner;
    private TextView status,frameStatus;
    private LinearLayout timeline;
    private Preview preview;
    private Button playButton,inkButton,paintButton;
    private final Runnable ticker=new Runnable() {
        @Override public void run() {
            if(!playing || frames.isEmpty())return;
            selectedFrame=(selectedFrame+1)%frames.size();
            updatePreview(false);
            handler.postDelayed(this,Math.max(60,1000/Math.max(1,fps)));
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        buildUi();
    }
    @Override protected void onPause() {
        super.onPause();
        stopPlayback();
    }
    @Override protected void onDestroy() {
        stopPlayback();super.onDestroy();
    }

    private void buildUi() {
        ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(BG);
        LinearLayout body=new LinearLayout(this);
        body.setPadding(dp(16),dp(16),dp(16),dp(24));
        body.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(body);
        header(body,"PIXEL LOOP STUDIO",24);
        note(body,"Adult-character pixel art • local sprite animation • no cloud uploads");
        line(body);
        header(body,"1  Choose a source image",17);
        note(body,"Generate artwork in Velvet Atelier, save the result, then import that image here. The anime ML model is preserved and runs locally.");
        LinearLayout top=row(body);
        button(top,"Open AI Generator",()->openGenerator());
        button(top,"Import Image",this::importImage);
        button(body,"Copy Pixel Art AI Prompt",this::copyPixelPrompt);
        preview=new Preview(this);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(315));
        p.topMargin=dp(14);p.bottomMargin=dp(10);
        body.addView(preview,p);
        status=label(body,"Import an image to start. Pixel processing and animation run offline.");
        line(body);
        header(body,"2  Pixel settings",17);
        note(body,"Choose logical sprite size and palette. The final PNGs use an actual low-resolution pixel grid.");
        slider(body,"Logical grid size",new int[] {32,48,64,96,128,192},2,v->logicalSize=v);
        slider(body,"Colors per sprite",new int[] {16,32,64,96,128},1,v->paletteCount=v);
        button(body,"Apply Pixel Style",this::stylize);
        line(body);
        header(body,"3  Build an animation loop",17);
        note(body,"One AI keyframe becomes a short loop through pixel-preserving motion. This is 2D sprite animation, not text-to-video diffusion.");
        motionSpinner=new Spinner(this);
        String[] motions={"Breathing","Sway","Bounce","Walk","Float","Pulse"};
        ArrayAdapter<String> a=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,motions);
        motionSpinner.setAdapter(a);body.addView(motionSpinner);
        slider(body,"Frames",new int[]{4,6,8,10,12,16,20,24},2,v->frameCount=v);
        slider(body,"Frames per second",new int[]{2,4,6,8,10,12,16},3,v->fps=v);
        slider(body,"Motion strength",new int[]{1,2,3,4,5,6},2,v->intensity=v);
        button(body,"Build / Rebuild Loop",this::regenerate);
        LinearLayout plays=row(body);
        playButton=button(plays,"Play",this::togglePlayback);
        button(plays,"Previous",()->step(-1));
        button(plays,"Next",()->step(1));
        frameStatus=label(body,"No animation frames yet");
        HorizontalScrollView strip=new HorizontalScrollView(this);
        timeline=new LinearLayout(this);timeline.setOrientation(LinearLayout.HORIZONTAL);
        strip.addView(timeline);
        body.addView(strip,new LinearLayout.LayoutParams(-1,dp(55)));
        line(body);
        header(body,"4  Edit individual frames",17);
        note(body,"Tap a frame number, turn on pixel painting, and tap the preview to edit pixels. Duplicating, deleting and mirroring affect only the selected frame.");
        LinearLayout ed1=row(body);
        button(ed1,"Duplicate Frame",this::duplicate);
        button(ed1,"Delete Frame",this::delete);
        LinearLayout ed2=row(body);
        button(ed2,"Mirror Frame",this::mirror);
        paintButton=button(ed2,"Paint: Off",this::togglePaint);
        inkButton=button(body,"Paint color",this::nextInk);
        refreshInk();
        line(body);
        header(body,"5  Export files",17);
        note(body,"Choose a destination in the Android file picker. You can save directly to an SD card. Sprite ZIP includes a PNG sheet, per-frame PNGs and timing JSON.");
        LinearLayout export1=row(body);
        button(export1,"Animated GIF",()->saveDialog(SAVE_GIF,"image/gif","Velvet_Loop.gif"));
        button(export1,"Sprite Sheet PNG",()->saveDialog(SAVE_PNG,"image/png","Velvet_Sheet.png"));
        LinearLayout export2=row(body);
        button(export2,"Sprite ZIP",()->saveDialog(SAVE_ZIP,"application/zip","Velvet_Sprites.zip"));
        button(export2,"Current Frame",()->saveDialog(SAVE_FRAME,"image/png","Velvet_Frame.png"));
        note(body,"Credits: Local Dream by xororz (CC BY-NC). MeinaHentai v2.1 (Open RAIL-M). Pixel Loop Studio original noncommercial additions.");
        setContentView(scroll);
    }

    private void openGenerator() {
        try{startActivity(new Intent().setClassName(this,"io.github.xororz.localdream.MainActivity"));}
        catch(Exception e){showError(e);}
    }

    private void copyPixelPrompt() {
        String prompt="masterpiece, best quality, 16-bit pixel art, 2d game sprite, "
            +"one adult woman, solo, original character, full body, "
            +"clear silhouette, defined edges, beautiful retro color palette, "
            +"character portrait, limited colors, pixel illustration";
        android.content.ClipboardManager manager=(android.content.ClipboardManager)
            getSystemService(CLIPBOARD_SERVICE);
        manager.setPrimaryClip(ClipData.newPlainText("Pixel Art Prompt",prompt));
        Toast.makeText(this,"Copied pixel-art prompt for the offline AI generator",
            Toast.LENGTH_LONG).show();
    }

    private void importImage(){
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.setType("image/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(i,OPEN_IMAGE);
    }
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data) {
        super.onActivityResult(requestCode,resultCode,data);
        if(resultCode!=RESULT_OK || data==null || data.getData()==null)return;
        Uri uri=data.getData();
        if(requestCode==OPEN_IMAGE) {
            try {
                Bitmap bitmap=readBitmap(uri);
                if(bitmap==null)throw new IOException("Image could not be decoded");
                original=bitmap;
                status.setText("Image imported: "+bitmap.getWidth()+"×"+bitmap.getHeight());
                stylize();
            } catch(Exception e){showError(e);}
            return;
        }
        try(OutputStream out=getContentResolver().openOutputStream(uri)) {
            if(out==null)throw new IOException("Cannot open selected file for writing");
            if(requestCode==SAVE_GIF)exportGif(out);
            if(requestCode==SAVE_PNG)exportSheet(out);
            if(requestCode==SAVE_FRAME)exportFrame(out);
            if(requestCode==SAVE_ZIP)exportZip(out);
            out.flush();
            Toast.makeText(this,"Saved successfully",Toast.LENGTH_SHORT).show();
        } catch(Exception e){showError(e);}
    }

    private static Bitmap readBitmap(Uri uri,Activity activity) throws IOException {
        BitmapFactory.Options first=new BitmapFactory.Options();
        first.inJustDecodeBounds=true;
        try(InputStream in=activity.getContentResolver().openInputStream(uri)) {
            if(in==null)throw new IOException("No input stream");
            BitmapFactory.decodeStream(in,null,first);
        }
        if(first.outWidth<1 || first.outHeight<1) throw new IOException("Unsupported image type");
        int sample=1;
        while(Math.max(first.outWidth,first.outHeight)/sample>1536)sample*=2;
        BitmapFactory.Options opts=new BitmapFactory.Options();
        opts.inSampleSize=sample;
        opts.inPreferredConfig=Bitmap.Config.ARGB_8888;
        try(InputStream in=activity.getContentResolver().openInputStream(uri)) {
            if(in==null)throw new IOException("No input stream");
            Bitmap b=BitmapFactory.decodeStream(in,null,opts);
            if(b==null)throw new IOException("Image decoder returned empty bitmap");
            return b;
        }
    }
    private Bitmap readBitmap(Uri uri) throws IOException {return readBitmap(uri,this);}

    private void stylize(){
        if(original==null){Toast.makeText(this,"Import an image first",Toast.LENGTH_SHORT).show();return;}
        stopPlayback();
        try {
            artwork=PixelEngine.stylize(original,logicalSize,paletteCount);
            inkIndex=1;
            regenerate();
            status.setText("Pixelated to "+logicalSize+"×"+logicalSize+" with up to "+paletteCount+" colors. Shared palette locked.");
            refreshInk();
        }catch(Exception e){showError(e);}
    }
    private void regenerate(){
        if(artwork==null){Toast.makeText(this,"Apply Pixel Style first",Toast.LENGTH_SHORT).show();return;}
        stopPlayback();
        String motion=(String)motionSpinner.getSelectedItem();
        frames=PixelEngine.animate(artwork.master,frameCount,motion,intensity);
        selectedFrame=0;
        status.setText(frames.size()+" consistent-color frames created in "+motion+" mode.");
        updatePreview(true);
    }
    private void togglePlayback(){
        if(frames.isEmpty()){Toast.makeText(this,"Build frames first",Toast.LENGTH_SHORT).show();return;}
        if(playing)stopPlayback();
        else {
            playing=true;
            playButton.setText("Pause");
            handler.post(ticker);
        }
    }
    private void stopPlayback(){
        playing=false;
        handler.removeCallbacks(ticker);
        if(playButton!=null)playButton.setText("Play");
    }
    private void step(int amount) {
        if(frames.isEmpty())return;
        stopPlayback();
        selectedFrame=(selectedFrame+frames.size()+amount)%frames.size();
        updatePreview(false);
    }
    private void duplicate(){
        if(frames.isEmpty())return;
        stopPlayback();
        if(frames.size()>=24){Toast.makeText(this,"Maximum 24 frames",Toast.LENGTH_SHORT).show();return;}
        frames.add(selectedFrame+1,frames.get(selectedFrame).copy(Bitmap.Config.ARGB_8888,true));
        selectedFrame++;
        updatePreview(true);
    }
    private void delete(){
        if(frames.size()<=2){Toast.makeText(this,"Keep at least two frames",Toast.LENGTH_SHORT).show();return;}
        stopPlayback();frames.remove(selectedFrame);
        selectedFrame=Math.min(selectedFrame,frames.size()-1);
        updatePreview(true);
    }
    private void mirror(){
        if(frames.isEmpty())return;
        stopPlayback();frames.set(selectedFrame,PixelEngine.mirror(frames.get(selectedFrame)));
        updatePreview(false);
    }
    private void togglePaint(){
        paintMode=!paintMode;stopPlayback();
        paintButton.setText(paintMode?"Paint: On":"Paint: Off");
        preview.invalidate();
    }
    private void nextInk(){
        if(artwork==null)return;
        for(int i=0;i<128;i++){
            inkIndex=(inkIndex+1)%artwork.palette.length;
            if(inkIndex>0&&artwork.palette[inkIndex]!=0)break;
        }
        refreshInk();
    }
    private void refreshInk(){
        if(inkButton==null)return;
        int color=artwork!=null?artwork.palette[inkIndex]:0xffb786d5;
        inkButton.setText("Paint color · Tap to cycle");
        inkButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(color));
        int brightness=(Color.red(color)*3+Color.green(color)*6+Color.blue(color))/10;
        inkButton.setTextColor(brightness>140?Color.BLACK:Color.WHITE);
    }
    private void updatePreview(boolean refreshTimeline){
        if(preview!=null)preview.invalidate();
        if(frameStatus!=null)frameStatus.setText(
            frames.isEmpty()?"No frames":String.format(Locale.US,"Frame %d / %d · %d fps",selectedFrame+1,frames.size(),fps));
        if(refreshTimeline)renderTimeline();
    }
    private void renderTimeline(){
        timeline.removeAllViews();
        for(int i=0;i<frames.size();i++) {
            final int id=i;
            Button b=new Button(this);
            b.setText(Integer.toString(i+1));b.setAllCaps(false);
            b.setTextColor(FG);
            b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                i==selectedFrame?0xff9764b4:0xff362a42));
            b.setOnClickListener(v->{stopPlayback();selectedFrame=id;updatePreview(true);});
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(dp(53),dp(46));
            p.rightMargin=dp(4);timeline.addView(b,p);
        }
    }
    private void saveDialog(int type,String mime,String name){
        if(frames.isEmpty()){Toast.makeText(this,"Create frames first",Toast.LENGTH_SHORT).show();return;}
        stopPlayback();
        Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType(mime);
        i.putExtra(Intent.EXTRA_TITLE,name);
        startActivityForResult(i,type);
    }
    private void exportFrame(OutputStream out) throws IOException{
        if(!frames.get(selectedFrame).compress(Bitmap.CompressFormat.PNG,100,out))
            throw new IOException("Could not encode frame PNG");
    }
    private void exportSheet(OutputStream out) throws IOException{
        Bitmap bitmap=PixelEngine.sheet(frames,Math.min(6,frames.size()));
        if(!bitmap.compress(Bitmap.CompressFormat.PNG,100,out))
            throw new IOException("Could not encode sprite sheet");
        bitmap.recycle();
    }
    private void exportGif(OutputStream out) throws IOException{
        ArrayList<byte[]> indexes=new ArrayList<>(frames.size());
        for(Bitmap b:frames)indexes.add(PixelEngine.indexes(b,artwork.palette));
        GifWriter.write(out,frames.get(0).getWidth(),frames.get(0).getHeight(),indexes,
                        artwork.palette,fps,artwork.hasTransparency);
    }
    private void exportZip(OutputStream output)throws IOException{
        ZipOutputStream out=new ZipOutputStream(output);
        int cols=Math.min(6,frames.size());
        Bitmap sheet=PixelEngine.sheet(frames,cols);
        out.putNextEntry(new ZipEntry("spritesheet.png"));
        sheet.compress(Bitmap.CompressFormat.PNG,100,out);
        out.closeEntry();
        sheet.recycle();
        for(int i=0;i<frames.size();i++){
            out.putNextEntry(new ZipEntry(String.format(Locale.US,"frames/%03d.png",i)));
            frames.get(i).compress(Bitmap.CompressFormat.PNG,100,out);
            out.closeEntry();
        }
        String json=String.format(Locale.US,
            "{\"format\":\"VelvetSpriteAtlas-1\",\"frameWidth\":%d,\"frameHeight\":%d,\"frames\":%d,\"columns\":%d,\"fps\":%d,\"loop\":true,\"transparent\":%s}",
            frames.get(0).getWidth(),frames.get(0).getHeight(),frames.size(),cols,fps,
            artwork.hasTransparency?"true":"false");
        out.putNextEntry(new ZipEntry("atlas.json"));
        out.write(json.getBytes(StandardCharsets.UTF_8));
        out.closeEntry();
        out.finish(); // never close the caller-owned SAF OutputStream here
    }
    private void showError(Exception error){
        new AlertDialog.Builder(this).setTitle("Pixel Studio")
            .setMessage(error.getClass().getSimpleName()+": "+error.getMessage())
            .setPositiveButton("OK",null).show();
    }

    private void header(LinearLayout target,String text,int size){
        TextView t=new TextView(this);t.setText(text);
        t.setTextColor(FG);t.setTextSize(size);t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        t.setPadding(0,dp(12),0,dp(8));target.addView(t);
    }
    private TextView label(LinearLayout target,String text){
        TextView t=new TextView(this);t.setText(text);
        t.setTextColor(0xffe1d2ee);t.setTextSize(13);
        t.setPadding(0,dp(6),0,dp(6));target.addView(t);
        return t;
    }
    private void note(LinearLayout target,String text){
        TextView t=label(target,text);t.setTextColor(MUTED);
    }
    private LinearLayout row(LinearLayout parent){
        LinearLayout l=new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        parent.addView(l,new LinearLayout.LayoutParams(-1,-2));
        return l;
    }
    private Button button(LinearLayout parent,String text,Runnable callback){
        Button b=new Button(this);b.setText(text);b.setAllCaps(false);
        b.setTextColor(FG);
        b.setTextSize(12);
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xff69407f));
        b.setOnClickListener(v->callback.run());
        boolean horizontal=parent.getOrientation()==LinearLayout.HORIZONTAL;
        LinearLayout.LayoutParams p=horizontal
            ? new LinearLayout.LayoutParams(0,dp(56),1f)
            : new LinearLayout.LayoutParams(-1,dp(56));
        p.rightMargin=dp(4);p.bottomMargin=dp(5);
        parent.addView(b,p);return b;
    }
    private Button button(LinearLayout parent,String text,Runnable callback,boolean full){
        if(!full)return button(parent,text,callback);
        Button b=new Button(this);b.setText(text);b.setAllCaps(false);b.setTextColor(FG);
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xff69407f));
        b.setOnClickListener(v->callback.run());
        parent.addView(b,new LinearLayout.LayoutParams(-1,dp(55)));
        return b;
    }
    private void slider(LinearLayout parent,String title,int[] values,int defaultIndex,java.util.function.IntConsumer change){
        TextView t=label(parent,title+" · "+values[defaultIndex]);
        SeekBar slider=new SeekBar(this);
        slider.setMax(values.length-1);slider.setProgress(defaultIndex);
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar b,int progress,boolean fromUser) {
                int value=values[Math.max(0,Math.min(values.length-1,progress))];
                t.setText(title+" · "+value);change.accept(value);
            }
            @Override public void onStartTrackingTouch(SeekBar b){}
            @Override public void onStopTrackingTouch(SeekBar b){}
        });
        parent.addView(slider);
    }
    private void line(LinearLayout parent){
        View v=new View(this);v.setBackgroundColor(0xff46394c);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(1));
        p.topMargin=dp(12);p.bottomMargin=dp(13);parent.addView(v,p);
    }
    private int dp(float value){return (int)(value*getResources().getDisplayMetrics().density+0.5f);}

    private final class Preview extends View {
        private final Paint paint=new Paint();
        private final Paint checker=new Paint();
        private final RectF destination=new RectF();
        Preview(Context context){super(context);setBackgroundColor(0xff211b28);}
        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);
            int block=dp(14);
            for(int y=0;y<getHeight();y+=block)for(int x=0;x<getWidth();x+=block){
                boolean lighter=((x/block)+(y/block))%2==0;
                checker.setColor(lighter?0xff352c3b:0xff292330);
                canvas.drawRect(x,y,x+block,y+block,checker);
            }
            Bitmap image=frames.isEmpty()?(artwork==null?null:artwork.master):frames.get(selectedFrame);
            if(image==null){
                paint.setColor(0xffe0c8ee);paint.setTextSize(dp(16));paint.setTextAlign(Paint.Align.CENTER);
                canvas.drawText("Import a source picture",getWidth()/2f,getHeight()/2f,paint);
                return;
            }
            float scale=Math.min((getWidth()-dp(24))/(float)image.getWidth(),
                                 (getHeight()-dp(24))/(float)image.getHeight());
            float width=image.getWidth()*scale,height=image.getHeight()*scale;
            destination.set((getWidth()-width)/2,(getHeight()-height)/2,
                            (getWidth()+width)/2,(getHeight()+height)/2);
            paint.reset();paint.setFilterBitmap(false);
            canvas.drawBitmap(image,null,destination,paint);
            if(paintMode&&image.getWidth()<=96){
                paint.setColor(0x325c4880);paint.setStrokeWidth(1);
                for(int x=0;x<=image.getWidth();x++)
                    canvas.drawLine(destination.left+x*scale,destination.top,
                        destination.left+x*scale,destination.bottom,paint);
                for(int y=0;y<=image.getHeight();y++)
                    canvas.drawLine(destination.left,destination.top+y*scale,
                        destination.right,destination.top+y*scale,paint);
            }
        }
        @Override public boolean onTouchEvent(MotionEvent event){
            if(!paintMode || artwork==null || frames.isEmpty())return super.onTouchEvent(event);
            if(event.getActionMasked()!=MotionEvent.ACTION_DOWN &&
               event.getActionMasked()!=MotionEvent.ACTION_MOVE)return true;
            if(!destination.contains(event.getX(),event.getY()))return true;
            Bitmap b=frames.get(selectedFrame);
            int x=(int)((event.getX()-destination.left)*b.getWidth()/destination.width());
            int y=(int)((event.getY()-destination.top)*b.getHeight()/destination.height());
            if(x>=0 && x<b.getWidth()&&y>=0&&y<b.getHeight()){
                b.setPixel(x,y,artwork.palette[inkIndex]);
                invalidate();
            }
            return true;
        }
    }
}
