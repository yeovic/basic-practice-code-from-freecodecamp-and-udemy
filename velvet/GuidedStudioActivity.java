package org.velvetatelier;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.AdapterView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;

/**
 * V2.3 Guided Studio — compile-first prompts that are imported by the REAL
 * Local Dream UI using its documented LDPARAMS parameter clipboard format.
 *
 * All prompt edits remain controllable inside the original generator. This
 * activity does not claim to invoke diffusion directly.
 */
public final class GuidedStudioActivity extends Activity {
    private static final int BG=0xff19141f,INK=0xfff8f0fa,PURPLE=0xff794c93;
    private static final String PREFS="velvet_guided_profiles";
    private static final String SAVED="characters";
    private static final int EXPORT_PROFILES=451;
    private static final int IMPORT_PROFILES=452;
    private LinearLayout body;
    private Spinner savedProfiles,scene,contentLevel,camera,identity,figure,chest,hips,genitals,expression,style,quality;
    private Spinner penisSize,penisState,testicles,bulge,speedProfile;
    private Spinner length,girth,testicleSize,faceMode,proportionMode,complexity;
    private EditText customStylePositive,customStyleNegative,styleCardName;
    private Spinner savedStyles;
    private static final String STYLE_CARDS="style_cards";
    private LinearLayout penisControls;
    private EditText name,age,hair,eyes,clothing,traits,userPrompt,customNegative,seed;
    private CheckBox anatomyPriority,freeform;
    private TextView recipePreview,negativePreview,configPreview;
    private final ArrayList<PromptBuilder.Character> saved=new ArrayList<>();
    private boolean switching=false;
    private int loadedProfile=-1;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true);scroll.setBackgroundColor(BG);
        body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(18),dp(25),dp(18),dp(30));
        scroll.addView(body);
        title("GUIDED STUDIO",25);
        note("Better anime prompts, consistent adult character cards, and freeform editing.");
        title("Saved character",18);
        savedProfiles=choice("Saved profiles",new String[]{"(New / unsaved)"});
        loadProfiles();
        android.widget.LinearLayout profileControls=row();
        action(profileControls,"Load",this::loadSelected);
        action(profileControls,"Save / Update",this::saveProfile);
        action(profileControls,"Delete",this::deleteSelected);
        LinearLayout portableControls=row();
        action(portableControls,"Export Cards",this::exportCards);
        action(portableControls,"Import Cards",this::importCards);
        note("Export cards to a JSON file before upgrading to a separately installed version of Velvet Atelier. Import replaces saved cards and current draft.");
        line();
        title("Scene and render preset",18);
        scene=choice("Composition",PromptBuilder.SCENES);
        contentLevel=choice("Adult-content level (choose explicitly)",PromptBuilder.CONTENT_LEVELS);
        camera=choice("Camera / framing",PromptBuilder.CAMERA);
        note("Nude and consensual adult-scene modes add content-specific prompt tags and remove conflicting outfit descriptions. Clothed remains the default.");
        style=choice("Rendering",PromptBuilder.STYLES);
        savedStyles=choice("Saved custom styles",new String[]{"(No saved style)"});
        styleCardName=input("Style card name","My style",false);
        customStylePositive=input("Custom style: positive details","Illustration techniques, palette, shading",true);
        customStyleNegative=input("Custom style: negative details","Avoid glossy rendering, muddy linework",true);
        LinearLayout styleActions=row();
        action(styleActions,"Save style",this::saveStyleCard);
        action(styleActions,"Load style",this::loadStyleCard);
        loadStyleOptions();
        note("Boukoku Daitoryou is an editable unverified approximation, not an authentic style LoRA. Custom style cards can record your exact reference-derived traits.");
        quality=choice("Generation preference",PromptBuilder.QUALITY);
        quality.setSelection(1);
        speedProfile=choice("Render speed / detail",PromptBuilder.SPEED_PROFILES);
        speedProfile.setSelection(1);
        note("Fast reduces sampling steps; the suggested 384×384 preview resolution must be set manually inside the generator.");
        line();
        title("Adult character builder",18);
        name=input("Character name (for saving, not a reliable model token)","Yuki",false);
        age=input("Adult age", "25",false);
        age.setInputType(InputType.TYPE_CLASS_NUMBER);
        age.setText("25"); // previous build only showed a hint, leaving age blank
        identity=choice("Character type",PromptBuilder.IDENTITIES);
        figure=choice("Body type",PromptBuilder.BODIES);
        figure.setSelection(1);
        chest=choice("Chest",PromptBuilder.CHESTS);
        chest.setSelection(1);
        hips=choice("Hips",PromptBuilder.HIPS);
        hips.setSelection(1);
        genitals=choice("Anatomy (only prompt when explicitly chosen)",PromptBuilder.GENITALS);
        penisControls=new LinearLayout(this);
        penisControls.setOrientation(LinearLayout.VERTICAL);
        body.addView(penisControls,new LinearLayout.LayoutParams(-1,-2));
        penisSize=choiceIn(penisControls,"Penis size (Average by default)",PromptBuilder.PENIS_SIZES);
        penisSize.setSelection(2);
        penisState=choiceIn(penisControls,"Penis state",PromptBuilder.PENIS_STATES);
        testicles=choiceIn(penisControls,"Testicles",PromptBuilder.TESTICLE_OPTIONS);
        bulge=choiceIn(penisControls,"Clothed bulge",PromptBuilder.BULGE_OPTIONS);
        length=choiceIn(penisControls,"Length (independent)",QualityTuning.LENGTHS);
        length.setSelection(2);
        girth=choiceIn(penisControls,"Girth (independent)",QualityTuning.GIRTHS);
        girth.setSelection(2);
        testicleSize=choiceIn(penisControls,"Testicle size",QualityTuning.TESTICLE_SIZES);
        AdapterView.OnItemSelectedListener visibility=new AdapterView.OnItemSelectedListener(){
            @Override public void onItemSelected(AdapterView<?> parent,View view,int position,long id) {
                refreshPenisControls();
            }
            @Override public void onNothingSelected(AdapterView<?> parent){}
        };
        genitals.setOnItemSelectedListener(visibility);
        identity.setOnItemSelectedListener(visibility);
        refreshPenisControls();
        expression=choice("Presentation",PromptBuilder.EXPRESSIONS);
        hair=input("Hair / hairstyle","white hair",false);
        eyes=input("Eyes","blue eyes",false);
        clothing=input("Outfit / clothing (optional)","",false);
        traits=input("Character traits and details (optional)","",true);
        line();
        title("Face, composition and proportions",18);
        faceMode=choice("Face detail priority",QualityTuning.FACE_MODES);
        proportionMode=choice("Proportions",QualityTuning.PROPORTION_MODES);
        proportionMode.setSelection(1);
        complexity=choice("Scene complexity",QualityTuning.COMPLEXITY_MODES);
        title("Prompts and quality",18);
        anatomyPriority=new CheckBox(this);
        anatomyPriority.setText("Anatomy priority: cleaner limbs, proportions, details");
        anatomyPriority.setChecked(true);anatomyPriority.setTextColor(INK);
        body.addView(anatomyPriority);
        freeform=new CheckBox(this);
        freeform.setText("Freeform only (do not add any automatic positive tags)");
        freeform.setTextColor(INK);body.addView(freeform);
        userPrompt=input("Freeform additions / custom scene","Your own prompt tags",true);
        customNegative=input("Additional negative prompt","Optional exclusions",true);
        seed=input("Optional seed lock (0–2147483647)","Leave blank for random",false);
        seed.setInputType(InputType.TYPE_CLASS_NUMBER);
        line();
        title("Generated recipe preview",18);
        recipePreview=note("Build a recipe to inspect it here.");
        negativePreview=note("");
        configPreview=note("");
        action(body,"Build / Update Prompt",()->buildAndDisplay(false,false));
        action(body,"Long Prompt Planner (beyond 77 over img2img passes)",this::showLongPromptPlanner);
        action(body,"Generate multi-pass locally (experimental)",this::launchLongPromptRunner);
        action(body,"Open Generator + Import Preset",()->sendRecipe(false,false));
        LinearLayout modeButtons=row();
        action(modeButtons,"Preview ×4",()->sendRecipe(true,false));
        action(modeButtons,"Refine / Repair",()->sendRecipe(false,true));
        action(body,"Save My Current Settings",this::saveDraft);
        line();
        title("How generation works",17);
        note("The guided recipe is copied in Local Dream's import format. In the generator choose MeinaHentaiV21 under Custom Models when READY, then accept the parameter-import dialog. Your freeform text remains editable there.");
        note("If nude/sexual selections still produce fully clothed images, verify Content level is not Clothed and inspect the imported POSITIVE/NEGATIVE prompts in the generator. The unfiltered Android release is used; model behavior can vary by tag and checkpoint.");
        note("Tip: Average or Small size gives proportions that may look more natural. For exposed anatomy, Size and State are independent. If the model exaggerates size anyway, inspect imported negative prompts.");
        note("For four previews choose the generator's batch count of 4. Select a favorite image from the history; for a second pass enter its seed above or use Local Dream's existing img2img / inpaint repair tools. This build does not automatically repair image anatomy.");
        note("MeinaHentai v2.1 is a locally converted SD1.5 anime model. Presets improve prompting, but anatomical quality depends on the model and device.");
        note("All profiles are saved locally in app preferences. No profile data is uploaded.");
        setContentView(scroll);
        restoreDraft();
        buildAndDisplay(false,false);
    }

    private void title(String t,int sp){
        TextView v=new TextView(this);v.setText(t);v.setTextSize(sp);
        v.setTextColor(INK);v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        v.setPadding(0,dp(14),0,dp(9));body.addView(v);
    }
    private TextView note(String t){
        TextView v=new TextView(this);v.setText(t);v.setTextColor(0xffcfc0da);
        v.setTextSize(13);v.setPadding(0,dp(7),0,dp(8));
        body.addView(v);return v;
    }
    private void line(){
        View v=new View(this);v.setBackgroundColor(0xff44374a);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(1));
        p.topMargin=dp(12);p.bottomMargin=dp(12);body.addView(v,p);
    }
    private LinearLayout row(){
        LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.HORIZONTAL);
        body.addView(v,new LinearLayout.LayoutParams(-1,-2));return v;
    }
    private Button action(LinearLayout parent,String label,Runnable run){
        Button b=new Button(this);b.setAllCaps(false);b.setText(label);b.setTextSize(12);
        b.setTextColor(INK);b.setBackgroundTintList(ColorStateList.valueOf(PURPLE));
        b.setOnClickListener(v->run.run());
        LinearLayout.LayoutParams p=parent==body
            ?new LinearLayout.LayoutParams(-1,dp(56))
            :new LinearLayout.LayoutParams(0,dp(56),1f);
        p.bottomMargin=dp(5);p.rightMargin=dp(4);
        parent.addView(b,p);return b;
    }
    private Spinner choice(String label,String[] values){
        note(label);Spinner spin=new Spinner(this);
        ArrayAdapter<String> adapter=new ArrayAdapter<>(this,
            android.R.layout.simple_spinner_dropdown_item,values);
        spin.setAdapter(adapter);body.addView(spin);
        return spin;
    }
    private Spinner choiceIn(LinearLayout parent,String label,String[] values){
        TextView title=new TextView(this);
        title.setText(label);title.setTextSize(13);
        title.setTextColor(0xffcfc0da);
        title.setPadding(0,dp(7),0,dp(6));
        parent.addView(title);
        Spinner spinner=new Spinner(this);
        spinner.setAdapter(new ArrayAdapter<>(this,
            android.R.layout.simple_spinner_dropdown_item,values));
        parent.addView(spinner);
        return spinner;
    }
    private void refreshPenisControls(){
        if(penisControls==null||identity==null||genitals==null)return;
        int g=genitals.getSelectedItemPosition();
        int id=identity.getSelectedItemPosition();
        boolean show=(g==2||g==3 || (g==0&&(id==2||id==3)));
        penisControls.setVisibility(show?View.VISIBLE:View.GONE);
    }

    private EditText input(String label,String hint,boolean multi){
        note(label);
        EditText v=new EditText(this);v.setTextColor(INK);v.setHintTextColor(0xffa995b4);
        v.setHint(hint);v.setTextSize(14);
        v.setInputType(InputType.TYPE_CLASS_TEXT | (multi?InputType.TYPE_TEXT_FLAG_MULTI_LINE:0));
        v.setMinLines(multi?2:1);v.setMaxLines(multi?5:1);
        body.addView(v,new LinearLayout.LayoutParams(-1,-2));return v;
    }
    private int dp(float x){return (int)(x*getResources().getDisplayMetrics().density+0.5f);}
    private String str(EditText v){return v.getText().toString().trim();}
    private static void select(Spinner spin,int i){
        if(i>=0 && i<spin.getCount())spin.setSelection(i);
    }

    private PromptBuilder.Settings collect() {
        PromptBuilder.Settings s=new PromptBuilder.Settings();
        PromptBuilder.Character c=new PromptBuilder.Character();
        c.name=str(name).isEmpty()?"Untitled adult":str(name);
        try{c.age=Integer.parseInt(str(age));}
        catch(NumberFormatException e){throw new IllegalArgumentException("Adult age must be a number");}
        c.hair=str(hair);c.eyes=str(eyes);c.clothing=str(clothing);
        c.traits=str(traits);
        c.identity=identity.getSelectedItemPosition();
        c.body=figure.getSelectedItemPosition();c.chest=chest.getSelectedItemPosition();
        c.hips=hips.getSelectedItemPosition();c.genitals=genitals.getSelectedItemPosition();
        c.expression=expression.getSelectedItemPosition();
        c.penisSize=penisSize.getSelectedItemPosition();
        c.penisState=penisState.getSelectedItemPosition();
        c.testicles=testicles.getSelectedItemPosition();
        c.bulge=bulge.getSelectedItemPosition();
        c.length=length.getSelectedItemPosition();
        c.girth=girth.getSelectedItemPosition();
        c.testicleSize=testicleSize.getSelectedItemPosition();
        s.character=c;s.scene=scene.getSelectedItemPosition();
        s.style=style.getSelectedItemPosition();s.quality=quality.getSelectedItemPosition();
        s.contentLevel=contentLevel.getSelectedItemPosition();
        s.camera=camera.getSelectedItemPosition();
        s.speedProfile=speedProfile.getSelectedItemPosition();
        s.faceMode=faceMode.getSelectedItemPosition();
        s.proportionMode=proportionMode.getSelectedItemPosition();
        s.complexity=complexity.getSelectedItemPosition();
        s.customStylePositive=str(customStylePositive);
        s.customStyleNegative=str(customStyleNegative);
        s.anatomyPriority=anatomyPriority.isChecked();
        s.freeformOnly=freeform.isChecked();
        s.userPrompt=str(userPrompt);s.extraNegative=str(customNegative);s.seed=str(seed);
        return s;
    }

    private PromptBuilder.Recipe buildAndDisplay(boolean preview,boolean repair){
        try {
            PromptBuilder.Recipe recipe=PromptBuilder.build(collect(),preview,repair);
            recipePreview.setText("POSITIVE\n"+recipe.positive);
            negativePreview.setText("NEGATIVE\n"+recipe.negative);
            // The optional planner has a deliberate multi-pass cap.
            // Never turn that cap into a generation-blocking preset error.
            String plannerAdvice;
            try{
                plannerAdvice="Estimated passes: "+
                    LongPromptPlanner.plan(recipe.positive).passes.size()+
                    " (approximate, BPE may differ).";
            }catch(Exception ignored){
                plannerAdvice="Long prompt accepted. Additional multi-pass rendering "+
                    "may require fewer details; ordinary generation still works.";
            }
            configPreview.setText("STEPS "+recipe.steps+" | CFG "+recipe.cfg+
                " | "+recipe.width+"×"+recipe.height+" | "+recipe.scheduler+
                "\\n"+recipe.explanation+"\\n"+plannerAdvice);
            return recipe;
        } catch(Exception ex) {
            recipePreview.setText("Preset error: "+ex.getMessage());
            negativePreview.setText("");configPreview.setText("");
            return null;
        }
    }

    private void sendRecipe(boolean preview,boolean repair) {
        PromptBuilder.Recipe recipe=buildAndDisplay(preview,repair);
        if(recipe==null){
            Toast.makeText(this,"Fix the preset before importing",Toast.LENGTH_LONG).show();
            return;
        }
        try {
            JSONObject params=new JSONObject();
            params.put("_localdream_params",true);
            params.put("v",1);
            // Only known fields accepted by Local Dream ParamShare.tryDecode.
            // Advisory only. Always deliver the COMPLETE prompt to Local
            // Dream and immediately open the generator, even if >77 native
            // CLIP tokens. Native SD1.5 may truncate later tags in one pass;
            // the user can separately choose automatic multi-pass if desired.
            // Never force the user to pick a shorter prompt or dismiss a dialog.
                        PromptImportPolicy.Delivery delivery=
                PromptImportPolicy.prepare(recipe.positive,recipe.negative);
            boolean warning=delivery.longPromptAdvisory;
            params.put("prompt",delivery.positive);
            params.put("negative_prompt",delivery.negative);
            params.put("steps",recipe.steps);
            params.put("cfg",recipe.cfg);
            params.put("scheduler",recipe.scheduler);
            if(recipe.seed>=0 && !preview)params.put("seed",recipe.seed);
            ClipboardManager manager=(ClipboardManager)getSystemService(
                Context.CLIPBOARD_SERVICE);
            manager.setPrimaryClip(ClipData.newPlainText("Velvet Guided Params",
                params.toString()));
            saveDraft();
            if(warning){
                Toast.makeText(this,
                    "Long prompt kept in full. Native SD1.5 may ignore tags after 77 tokens; multi-pass is optional.",
                    Toast.LENGTH_LONG).show();
            }
            if(preview)Toast.makeText(this,"Set batch count to 4 in the generator.",Toast.LENGTH_LONG).show();
            if(repair)Toast.makeText(this,"For spatial repairs, use the generator's img2img/inpaint tools.",Toast.LENGTH_LONG).show();
            // Direct transition: no blocking modal, even with long prompts.
            openGenerator();
        } catch(Exception e){error(e);}
    }
    private void openGenerator(){
        try{
            startActivity(new Intent().setClassName(this,
                "io.github.xororz.localdream.MainActivity"));
        }catch(Exception e){error(e);}
    }

    private SharedPreferences prefs(){return getSharedPreferences(PREFS,MODE_PRIVATE);}
    private JSONObject jsonOf(PromptBuilder.Character c)throws Exception{
        JSONObject j=new JSONObject();
        j.put("name",c.name);j.put("age",c.age);
        j.put("hair",c.hair);j.put("eyes",c.eyes);j.put("clothing",c.clothing);
        j.put("traits",c.traits);
        j.put("identity",c.identity);j.put("body",c.body);
        j.put("chest",c.chest);j.put("hips",c.hips);
        j.put("genitals",c.genitals);j.put("expression",c.expression);
        j.put("penisSize",c.penisSize);
        j.put("penisState",c.penisState);
        j.put("testicles",c.testicles);
        j.put("bulge",c.bulge);
        j.put("length",c.length);
        j.put("girth",c.girth);
        j.put("testicleSize",c.testicleSize);
        return j;
    }
    private PromptBuilder.Character characterOf(JSONObject j){
        PromptBuilder.Character c=new PromptBuilder.Character();
        c.name=j.optString("name","Unnamed adult");
        c.age=j.optInt("age",25);
        c.hair=j.optString("hair","");
        c.eyes=j.optString("eyes","");
        c.clothing=j.optString("clothing","");
        c.traits=j.optString("traits","");
        c.identity=j.optInt("identity",0);c.body=j.optInt("body",1);
        c.chest=j.optInt("chest",1);c.hips=j.optInt("hips",1);
        c.genitals=j.optInt("genitals",0);c.expression=j.optInt("expression",0);
        c.penisSize=j.optInt("penisSize",2);
        c.penisState=j.optInt("penisState",0);
        c.testicles=j.optInt("testicles",0);
        c.bulge=j.optInt("bulge",0);
        c.length=j.optInt("length",2);
        c.girth=j.optInt("girth",2);
        c.testicleSize=j.optInt("testicleSize",0);
        return c;
    }
    private void loadProfiles(){
        saved.clear();
        try{
            JSONArray a=new JSONArray(prefs().getString(SAVED,"[]"));
            for(int i=0;i<a.length() && i<64;i++){
                PromptBuilder.Character c=characterOf(a.getJSONObject(i));
                if(c.age>=18)saved.add(c);
            }
        }catch(Exception e){Toast.makeText(this,"Saved profiles could not load",Toast.LENGTH_SHORT).show();}
        ArrayList<String> options=new ArrayList<>();
        options.add("(New / unsaved)");
        for(PromptBuilder.Character c:saved)options.add(c.name);
        savedProfiles.setAdapter(new ArrayAdapter<>(this,
            android.R.layout.simple_spinner_dropdown_item,options));
        loadedProfile=-1;
    }
    private void loadSelected(){
        int index=savedProfiles.getSelectedItemPosition()-1;
        if(index<0 || index>=saved.size())return;
        applyCharacter(saved.get(index));loadedProfile=index;
        Toast.makeText(this,"Character loaded",Toast.LENGTH_SHORT).show();
        buildAndDisplay(false,false);
    }
    private void applyCharacter(PromptBuilder.Character c){
        name.setText(c.name);age.setText(Integer.toString(c.age));
        hair.setText(c.hair);eyes.setText(c.eyes);
        clothing.setText(c.clothing);traits.setText(c.traits);
        select(identity,c.identity);select(figure,c.body);
        select(chest,c.chest);select(hips,c.hips);
        select(genitals,c.genitals);select(expression,c.expression);
        select(penisSize,c.penisSize);
        select(penisState,c.penisState);
        select(testicles,c.testicles);
        select(bulge,c.bulge);
        select(length,c.length);select(girth,c.girth);
        select(testicleSize,c.testicleSize);
        refreshPenisControls();
    }
    private void saveProfile(){
        try{
            PromptBuilder.Settings values=collect();
            PromptBuilder.build(values,false,false); // adults-only validator
            if(saved.size()>=64 && loadedProfile<0)
                throw new IllegalArgumentException("Maximum 64 character profiles");
            PromptBuilder.Character c=values.character;
            if(loadedProfile>=0 && loadedProfile<saved.size())saved.set(loadedProfile,c);
            else {
                int found=-1;
                for(int i=0;i<saved.size();i++)if(saved.get(i).name.equalsIgnoreCase(c.name)){found=i;break;}
                if(found>=0)saved.set(found,c);
                else saved.add(c);
            }
            JSONArray a=new JSONArray();
            for(PromptBuilder.Character entry:saved)a.put(jsonOf(entry));
            prefs().edit().putString(SAVED,a.toString()).apply();
            loadProfiles();
            for(int i=0;i<saved.size();i++)if(saved.get(i).name.equals(c.name)){
                savedProfiles.setSelection(i+1);loadedProfile=i;break;
            }
            Toast.makeText(this,"Saved adult character profile",Toast.LENGTH_SHORT).show();
        }catch(Exception e){error(e);}
    }
    private void deleteSelected(){
        int index=savedProfiles.getSelectedItemPosition()-1;
        if(index<0 || index>=saved.size())return;
        new AlertDialog.Builder(this).setTitle("Delete character?")
            .setMessage("Delete the saved card for "+saved.get(index).name+"?")
            .setNegativeButton("Cancel",null)
            .setPositiveButton("Delete",(d,w)->{
                saved.remove(index);
                JSONArray a=new JSONArray();
                try{for(PromptBuilder.Character c:saved)a.put(jsonOf(c));}
                catch(Exception ignored){}
                prefs().edit().putString(SAVED,a.toString()).apply();
                loadProfiles();
            }).show();
    }
    private void exportCards(){
        try{
            saveDraft();
            Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/json");
            intent.putExtra(Intent.EXTRA_TITLE,"Velvet_Atelier_Profiles.json");
            startActivityForResult(intent,EXPORT_PROFILES);
        }catch(Exception e){error(e);}
    }
    private void importCards(){
        new AlertDialog.Builder(this)
            .setTitle("Import saved characters?")
            .setMessage("Import replaces your current saved character cards and draft. "
                +"Export a backup first if you want to preserve them.")
            .setNegativeButton("Cancel",null)
            .setPositiveButton("Choose JSON",(d,w)->{
                Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("application/json");
                startActivityForResult(intent,IMPORT_PROFILES);
            }).show();
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(result!=RESULT_OK||data==null||data.getData()==null)return;
        Uri uri=data.getData();
        try{
            if(request==EXPORT_PROFILES){
                JSONObject document=new JSONObject();
                document.put("format","velvet-atelier-characters-v1");
                document.put("profiles",new JSONArray(prefs().getString(SAVED,"[]")));
                document.put("styles",new JSONArray(prefs().getString(STYLE_CARDS,"[]")));
                String draft=prefs().getString("draft",null);
                if(draft!=null)document.put("draft",new JSONObject(draft));
                try(OutputStream out=getContentResolver().openOutputStream(uri)){
                    if(out==null)throw new IOException("Cannot write profile backup");
                    out.write(document.toString(2).getBytes(StandardCharsets.UTF_8));
                    out.flush();
                }
                Toast.makeText(this,"Profile backup exported",Toast.LENGTH_SHORT).show();
            }else if(request==IMPORT_PROFILES){
                ByteArrayOutputStream buffer=new ByteArrayOutputStream();
                try(InputStream stream=getContentResolver().openInputStream(uri)){
                    if(stream==null)throw new IOException("Cannot open profile backup");
                    byte[] chunk=new byte[8192];int len;
                    while((len=stream.read(chunk))!=-1){
                        if(buffer.size()+len>1024*1024)
                            throw new IOException("Profile backup exceeds 1MB");
                        buffer.write(chunk,0,len);
                    }
                }
                JSONObject document=new JSONObject(
                    new String(buffer.toByteArray(),StandardCharsets.UTF_8));
                if(!"velvet-atelier-characters-v1".equals(document.optString("format")))
                    throw new IOException("Not a compatible Velvet character backup");
                JSONArray styleSource=document.optJSONArray("styles");
                if(styleSource!=null && styleSource.length()>32)
                    throw new IOException("Maximum 32 imported styles");
                JSONArray source=document.getJSONArray("profiles");
                if(source.length()>64)throw new IOException("Maximum 64 saved characters");
                JSONArray filtered=new JSONArray();
                for(int i=0;i<source.length();i++){
                    PromptBuilder.Character c=characterOf(source.getJSONObject(i));
                    PromptBuilder.Settings check=new PromptBuilder.Settings();
                    check.character=c;
                    PromptBuilder.build(check,false,false);
                    filtered.put(jsonOf(c));
                }
                JSONObject draft=document.optJSONObject("draft");
                if(draft!=null){
                    PromptBuilder.Settings check=new PromptBuilder.Settings();
                    check.character=characterOf(draft);
                    check.userPrompt=draft.optString("userPrompt","");
                    PromptBuilder.build(check,false,false);
                }
                SharedPreferences.Editor edit=prefs().edit().putString(SAVED,filtered.toString());
                if(styleSource!=null){
                    JSONArray validatedStyles=new JSONArray();
                    for(int i=0;i<styleSource.length();i++){
                        JSONObject o=styleSource.getJSONObject(i);
                        if(o.optString("name","").length()>90
                            || o.optString("positive","").length()>4000
                            || o.optString("negative","").length()>4000)
                            throw new IOException("Invalid style card");
                        validatedStyles.put(o);
                    }
                    edit.putString(STYLE_CARDS,validatedStyles.toString());
                }
                if(draft==null)edit.remove("draft");
                else edit.putString("draft",draft.toString());
                edit.apply();
                loadProfiles();loadStyleOptions();
                if(draft!=null)restoreDraft();
                buildAndDisplay(false,false);
                Toast.makeText(this,"Imported "+filtered.length()+" character cards",Toast.LENGTH_LONG).show();
            }
        }catch(Exception ex){error(ex);}
    }

    private void launchLongPromptRunner(){
        PromptBuilder.Recipe r=buildAndDisplay(false,false);
        if(r==null)return;
        new AlertDialog.Builder(this)
            .setTitle("Experimental local multi-pass inference")
            .setMessage("This mode starts the included Local Dream backend and uses its "
                +"actual CLIP tokenizer to split prompts into valid segments. "
                +"It then runs sequential img2img passes (not longer native "
                +"UNet attention). Several passes can change the pose or style, "
                +"and your phone may run out of RAM. Original generator remains available.")
            .setNegativeButton("Cancel",null)
            .setPositiveButton("Run",(d,w)->{
                Intent intent=new Intent(this,LongPromptRunnerActivity.class);
                intent.putExtra(LongPromptRunnerActivity.EXTRA_POSITIVE,r.positive);
                intent.putExtra(LongPromptRunnerActivity.EXTRA_NEGATIVE,r.negative);
                intent.putExtra(LongPromptRunnerActivity.EXTRA_STEPS,r.steps);
                intent.putExtra(LongPromptRunnerActivity.EXTRA_CFG,r.cfg);
                intent.putExtra(LongPromptRunnerActivity.EXTRA_SEED,r.seed);
                intent.putExtra(LongPromptRunnerActivity.EXTRA_SCHEDULER,r.scheduler);
                intent.putExtra(LongPromptRunnerActivity.EXTRA_WIDTH,r.width);
                intent.putExtra(LongPromptRunnerActivity.EXTRA_HEIGHT,r.height);
                // Preserve backwards compatibility with older runner bundles.
                intent.putExtra(LongPromptRunnerActivity.EXTRA_SIZE,r.width);
                startActivity(intent);
            }).show();
    }

    private void showLongPromptPlanner(){
        PromptBuilder.Recipe recipe=buildAndDisplay(false,false);
        if(recipe==null)return;
        try {
            LongPromptPlanner.Plan plan=LongPromptPlanner.plan(recipe.positive);
            StringBuilder report=new StringBuilder();
            report.append("Native backend: 77 CLIP positions per pass.\n");
            report.append("Estimated words: ").append(plan.totalWords)
                .append(" | Passes: ").append(plan.passes.size()).append("\n");
            report.append(LongPromptPlanner.instructions(plan));
            report.append("\n\nSelect a pass to copy its prompt parameters.");
            String[] choices=new String[plan.passes.size()];
            for(int i=0;i<choices.length;i++){
                choices[i]="Pass "+(i+1)+": "+plan.passes.get(i);
            }
            new AlertDialog.Builder(this).setTitle("Long Prompt Workflow")
                .setMessage(report.toString())
                .setItems(choices,(d,which)->copyPromptPass(recipe,plan.passes.get(which),which,plan.passes.size()))
                .setNegativeButton("Close",null)
                .show();
        }catch(Exception e){error(e);}
    }
    private void copyPromptPass(PromptBuilder.Recipe r,String positive,int index,int count){
        try {
            JSONObject params=new JSONObject();
            params.put("_localdream_params",true);
            params.put("v",1);
            params.put("prompt",positive);
            // Negative prompt can also overflow; separate it from the
            // positional positive prompts and keep the first safe segment.
            LongPromptPlanner.Plan negPlan=LongPromptPlanner.plan(r.negative);
            params.put("negative_prompt",negPlan.passes.get(0));
            params.put("steps",index==0?r.steps:Math.min(18,r.steps));
            params.put("cfg",r.cfg);
            params.put("scheduler",r.scheduler);
            if(index==0 && r.seed>=0)params.put("seed",r.seed);
            ClipboardManager clipboard=(ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newPlainText("Velvet Pass "+(index+1),params.toString()));
            new AlertDialog.Builder(this)
                .setTitle("Pass "+(index+1)+" of "+count+" copied")
                .setMessage(index==0
                    ?"Start text-to-image using this prompt, save the result. Check Local Dream's actual token count."
                    :"Use the previous result as the img2img SOURCE before importing "
                    +"this pass. Use LOW denoise to retain composition. This is "
                    +"multi-pass conditioning, not a native concatenated context."
                ).setNegativeButton("Close",null)
                .setPositiveButton("Open generator",(d,w)->openGenerator()).show();
        }catch(Exception ex){error(ex);}
    }
    private void loadStyleOptions(){
        ArrayList<String> options=new ArrayList<>();
        options.add("(No saved style)");
        try {
            JSONArray a=new JSONArray(prefs().getString(STYLE_CARDS,"[]"));
            for(int i=0;i<a.length() && i<32;i++)
                options.add(a.getJSONObject(i).optString("name","Style "+(i+1)));
        }catch(Exception ignored){}
        savedStyles.setAdapter(new ArrayAdapter<>(this,
            android.R.layout.simple_spinner_dropdown_item,options));
    }
    private void saveStyleCard(){
        try{
            String label=str(styleCardName);
            if(label.isEmpty())throw new IllegalArgumentException("Name your style");
            if(label.length()>90)throw new IllegalArgumentException("Style name too long");
            PromptBuilder.Settings v=collect();
            PromptBuilder.build(v,false,false);
            JSONArray a=new JSONArray(prefs().getString(STYLE_CARDS,"[]"));
            JSONObject value=new JSONObject();
            value.put("name",label);
            value.put("positive",v.customStylePositive);
            value.put("negative",v.customStyleNegative);
            int found=-1;
            for(int i=0;i<a.length();i++)if(label.equalsIgnoreCase(
                a.getJSONObject(i).optString("name"))){found=i;break;}
            if(found>=0)a.put(found,value);
            else {
                if(a.length()>=32)throw new IllegalArgumentException("Maximum 32 styles");
                a.put(value);
            }
            prefs().edit().putString(STYLE_CARDS,a.toString()).apply();
            loadStyleOptions();
            Toast.makeText(this,"Style saved on device",Toast.LENGTH_SHORT).show();
        }catch(Exception e){error(e);}
    }
    private void loadStyleCard(){
        try{
            int index=savedStyles.getSelectedItemPosition()-1;
            if(index<0)return;
            JSONArray a=new JSONArray(prefs().getString(STYLE_CARDS,"[]"));
            JSONObject v=a.getJSONObject(index);
            styleCardName.setText(v.optString("name",""));
            customStylePositive.setText(v.optString("positive",""));
            customStyleNegative.setText(v.optString("negative",""));
            style.setSelection(5);
            buildAndDisplay(false,false);
        }catch(Exception e){error(e);}
    }

    private void saveDraft(){
        try{
            PromptBuilder.Settings v=collect();
            PromptBuilder.build(v,false,false);
            JSONObject j=jsonOf(v.character);
            j.put("scene",v.scene);j.put("style",v.style);j.put("quality",v.quality);
            j.put("contentLevel",v.contentLevel);j.put("camera",v.camera);
            j.put("speedProfile",v.speedProfile);
            j.put("faceMode",v.faceMode);
            j.put("proportionMode",v.proportionMode);
            j.put("complexity",v.complexity);
            j.put("customStylePositive",v.customStylePositive);
            j.put("customStyleNegative",v.customStyleNegative);
            j.put("freeformOnly",v.freeformOnly);
            j.put("anatomyPriority",v.anatomyPriority);
            j.put("userPrompt",v.userPrompt);
            j.put("negativePrompt",v.extraNegative);
            j.put("seed",v.seed);
            prefs().edit().putString("draft",j.toString()).apply();
        }catch(Exception e){error(e);}
    }
    private void restoreDraft(){
        try{
            String raw=prefs().getString("draft",null);
            if(raw==null)return;
            JSONObject j=new JSONObject(raw);
            PromptBuilder.Character c=characterOf(j);
            if(c.age<18)return;
            applyCharacter(c);
            select(scene,j.optInt("scene",0));
            select(contentLevel,j.optInt("contentLevel",0));
            select(camera,j.optInt("camera",0));
            select(speedProfile,j.optInt("speedProfile",1));
            select(faceMode,j.optInt("faceMode",0));
            select(proportionMode,j.optInt("proportionMode",1));
            select(complexity,j.optInt("complexity",0));
            customStylePositive.setText(j.optString("customStylePositive",""));
            customStyleNegative.setText(j.optString("customStyleNegative",""));
            select(style,j.optInt("style",0));select(quality,j.optInt("quality",1));
            freeform.setChecked(j.optBoolean("freeformOnly",false));
            anatomyPriority.setChecked(j.optBoolean("anatomyPriority",true));
            userPrompt.setText(j.optString("userPrompt",""));
            customNegative.setText(j.optString("negativePrompt",""));
            seed.setText(j.optString("seed",""));
        }catch(Exception ignored){}
    }
    @Override protected void onPause(){saveDraft();super.onPause();}
    private void error(Exception e){
        new AlertDialog.Builder(this).setTitle("Guided Studio")
            .setMessage(e.getMessage()==null?"Unknown error":e.getMessage())
            .setPositiveButton("OK",null).show();
    }
}
