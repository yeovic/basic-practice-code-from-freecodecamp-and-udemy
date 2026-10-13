package org.velvetatelier;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
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
    private LinearLayout body;
    private Spinner savedProfiles,scene,identity,figure,chest,hips,genitals,expression,style,quality;
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
        line();
        title("Scene and render preset",18);
        scene=choice("Composition",PromptBuilder.SCENES);
        style=choice("Rendering",PromptBuilder.STYLES);
        quality=choice("Generation preference",PromptBuilder.QUALITY);
        quality.setSelection(1);
        line();
        title("Adult character builder",18);
        name=input("Character name (for saving, not a reliable model token)","Yuki",false);
        age=input("Adult age", "25",false);
        age.setInputType(InputType.TYPE_CLASS_NUMBER);
        identity=choice("Character type",PromptBuilder.IDENTITIES);
        figure=choice("Body type",PromptBuilder.BODIES);
        figure.setSelection(1);
        chest=choice("Chest",PromptBuilder.CHESTS);
        chest.setSelection(1);
        hips=choice("Hips",PromptBuilder.HIPS);
        hips.setSelection(1);
        genitals=choice("Anatomy (only prompt when explicitly chosen)",PromptBuilder.GENITALS);
        expression=choice("Presentation",PromptBuilder.EXPRESSIONS);
        hair=input("Hair / hairstyle","white hair",false);
        eyes=input("Eyes","blue eyes",false);
        clothing=input("Outfit / clothing (optional)","",false);
        traits=input("Character traits and details (optional)","",true);
        line();
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
        action(body,"Open Generator + Import Preset",()->sendRecipe(false,false));
        LinearLayout modeButtons=row();
        action(modeButtons,"Preview ×4",()->sendRecipe(true,false));
        action(modeButtons,"Refine / Repair",()->sendRecipe(false,true));
        action(body,"Save My Current Settings",this::saveDraft);
        line();
        title("How generation works",17);
        note("The guided recipe is copied in Local Dream's import format. In the generator choose MeinaHentaiV21 under Custom Models when READY, then accept the parameter-import dialog. Your freeform text remains editable there.");
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
        s.character=c;s.scene=scene.getSelectedItemPosition();
        s.style=style.getSelectedItemPosition();s.quality=quality.getSelectedItemPosition();
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
            configPreview.setText("STEPS "+recipe.steps+" | CFG "+recipe.cfg+
                " | "+recipe.width+"×"+recipe.height+" | "+recipe.scheduler+
                "\n"+recipe.explanation);
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
            params.put("prompt",recipe.positive);
            params.put("negative_prompt",recipe.negative);
            params.put("steps",recipe.steps);
            params.put("cfg",recipe.cfg);
            params.put("scheduler",recipe.scheduler);
            if(recipe.seed>=0 && !preview)params.put("seed",recipe.seed);
            ClipboardManager manager=(ClipboardManager)getSystemService(
                Context.CLIPBOARD_SERVICE);
            manager.setPrimaryClip(ClipData.newPlainText("Velvet Guided Params",
                params.toString()));
            saveDraft();
            String message=preview
                ? "The generator will offer Import Parameters. Apply them, set batch count to FOUR, then start generation. Four-preview automation is not currently native."
                : repair
                  ? "Apply the imported high-quality parameters, and use the generator's existing img2img/inpaint tools for actual spatial repairs. Same-seed text rerender alone is NOT inpainting."
                  : "Choose the converted MeinaHentaiV21 model, accept Import Parameters, then Generate. The model must say READY in the home screen.";
            new AlertDialog.Builder(this)
                .setTitle(preview?"Preview ×4":repair?"Refine settings":"Guided generation")
                .setMessage(message)
                .setNegativeButton("Not now",null)
                .setPositiveButton("Open AI Generator",(d,w)->openGenerator())
                .show();
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
    private void saveDraft(){
        try{
            PromptBuilder.Settings v=collect();
            PromptBuilder.build(v,false,false);
            JSONObject j=jsonOf(v.character);
            j.put("scene",v.scene);j.put("style",v.style);j.put("quality",v.quality);
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
