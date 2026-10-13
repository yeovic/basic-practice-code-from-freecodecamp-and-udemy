package org.velvetatelier;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Native launcher hub. The original Local Dream generation activity is preserved. */
public final class VelvetHubActivity extends Activity {
    private TextView modelStatus;
    private final Handler updateHandler=new Handler(Looper.getMainLooper());
    private final Runnable refresh=new Runnable(){@Override public void run(){
        if(modelStatus!=null)modelStatus.setText(IncludedModelProvider.currentStatus(VelvetHubActivity.this));
        updateHandler.postDelayed(this,2000);
    }};
    private static final int BACKGROUND=0xff181420, PANEL=0xff2a2234, FOREGROUND=0xfff8eefe;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BACKGROUND);
        getWindow().setNavigationBarColor(BACKGROUND);
        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BACKGROUND);
        LinearLayout body=new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(22),dp(34),dp(22),dp(30));
        scroll.addView(body);
        title(body,"VELVET ATELIER",27,true);
        subtitle(body,"Private, entirely on-device creative studio");
        divider(body);
        title(body,"Guided Image Studio v2.3",20,true);
        subtitle(body,"Adult character cards, body/anatomy controls, scenes, anatomy-aware prompts and improved preview/refine recipes. Freeform mode remains available.");
        action(body,"Open Guided Studio",()->startActivity(new Intent(this,GuidedStudioActivity.class)));
        divider(body);
        title(body,"Original AI generator",20,true);
        subtitle(body,"Choose the converted MeinaHentaiV21 custom model, import your guided preset, or create freely with the original local generator.");
        action(body,"Open Freeform AI Generator",()->open("io.github.xororz.localdream.MainActivity"));
        divider(body);
        title(body,"Included AI model setup",20,true);
        modelStatus=label(body,IncludedModelProvider.currentStatus(this));
        action(body,"Retry / Continue Model Setup",()->{
            IncludedModelProvider.startSetup(this);
            modelStatus.setText(IncludedModelProvider.currentStatus(this));
        });
        subtitle(body,"The model appears under Custom Models only after first-run conversion creates its finished marker. This screen shows setup progress and any errors; if conversion fails, retry here.");
        divider(body);
        title(body,"Pixel Loop Studio",20,true);
        subtitle(body,"Turn AI-generated artwork or your own images into consistent-palette pixel sprites and editable animated loops.");
        action(body,"Open Pixel Animator",()->startActivity(new Intent(this,PixelStudioActivity.class)));
        divider(body);
        title(body,"Storage",18,true);
        String path=ModelStorage.modelsDir(this).getAbsolutePath();
        subtitle(body,"Current AI model folder:\\n"+path+"\\nChoose internal for potentially faster model loading or microSD to save internal space. Android controls the APK installation location.");
        action(body,"Storage: prefer speed / save space",()->{
            if(IncludedModelProvider.isBusy()){
                new android.app.AlertDialog.Builder(this)
                    .setMessage("Storage cannot be switched during checkpoint extraction or conversion.")
                    .setPositiveButton("OK",null).show();
                return;
            }
            String[] labels={
                "Auto: microSD when available",
                "Prefer speed: internal storage",
                "Prefer space: removable microSD"
            };
            new android.app.AlertDialog.Builder(this)
                .setTitle("Choose local model storage")
                .setSingleChoiceItems(labels,ModelStorage.getMode(this),(dialog,choice)->{
                    dialog.dismiss();
                    new android.app.AlertDialog.Builder(this)
                        .setTitle("Change model storage?")
                        .setMessage("Your existing model files stay in their previous folder. "
                            +"If the selected location has no converted model, initial setup "
                            +"must run there again and requires at least 6 GB free. "
                            +"Changing storage does NOT copy a ready model automatically. "
                            +"For speed, keep the converted model on internal storage.")
                        .setNegativeButton("Cancel",null)
                        .setPositiveButton("Use location",(confirm,which)->{
                            ModelStorage.setMode(this,choice);
                            IncludedModelProvider.startSetup(this);
                            modelStatus.setText(IncludedModelProvider.currentStatus(this));
                        }).show();
                }).setNegativeButton("Cancel",null).show();
        });
        action(body,"View active model folder",()->{
            new android.app.AlertDialog.Builder(this)
                .setTitle("AI model storage")
                .setMessage(ModelStorage.describeMode(this))
                .setPositiveButton("OK",null).show();
        });
        subtitle(body,"Original inference engine: Local Dream by xororz and contributors (CC BY-NC 4.0). Bundled checkpoint: MeinaHentai v2.1 (Open RAIL-M). This is a noncommercial fork.");
        setContentView(scroll);
    }

    @Override protected void onResume(){
        super.onResume();
        updateHandler.removeCallbacks(refresh);
        updateHandler.post(refresh);
    }
    @Override protected void onPause(){
        updateHandler.removeCallbacks(refresh);
        super.onPause();
    }

    private TextView label(LinearLayout body,String txt){
        TextView t=new TextView(this);t.setText(txt);t.setTextSize(14);
        t.setTextColor(0xffd9c5e3);t.setPadding(0,dp(8),0,dp(12));
        body.addView(t);return t;
    }
    private void open(String cls) {
        try { startActivity(new Intent().setClassName(this,cls)); }
        catch(Exception ex) {
            new android.app.AlertDialog.Builder(this)
                .setMessage("Generator could not open: "+ex.getMessage())
                .setPositiveButton("OK",null).show();
        }
    }
    private void title(LinearLayout body,String text,int sp,boolean bold) {
        TextView view=new TextView(this);
        view.setText(text);view.setTextColor(FOREGROUND);view.setTextSize(sp);
        if(bold)view.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        view.setPadding(0,dp(7),0,dp(7));
        body.addView(view);
    }
    private void subtitle(LinearLayout body,String str) {
        TextView t=new TextView(this);t.setText(str);
        t.setTextSize(14);t.setTextColor(0xffc9b9d3);
        t.setPadding(0,0,0,dp(12));body.addView(t);
    }
    private void action(LinearLayout body,String title,Runnable action) {
        Button b=new Button(this);b.setAllCaps(false);b.setText(title);
        b.setTextColor(FOREGROUND);b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xff6b3c86));
        b.setOnClickListener(v->action.run());
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(58));
        lp.bottomMargin=dp(5);body.addView(b,lp);
    }
    private void divider(LinearLayout parent) {
        View d=new View(this);d.setBackgroundColor(0xff44354d);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(1));
        p.topMargin=dp(18);p.bottomMargin=dp(18);parent.addView(d,p);
    }
    private int dp(float v){return (int)(v*getResources().getDisplayMetrics().density+0.5f);}
}
