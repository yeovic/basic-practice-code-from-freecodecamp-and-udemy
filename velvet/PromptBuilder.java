package org.velvetatelier;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Pure Java generation recipes. Guided settings compile to ordinary Stable
 * Diffusion tag prompts; the user always retains full freeform editing.
 *
 * These settings help steer a model; they cannot guarantee anatomical accuracy.
 */
public final class PromptBuilder {
    private PromptBuilder() {}

    public static final String[] SCENES={
      "Portrait","Upper body","Full body","Standing scene",
      "Relaxed indoor scene","Solo adult scene","Two-adult scene",
      "Pixel sprite","Animation keyframe"
    };
    public static final String[] IDENTITIES={
      "Adult woman","Adult trans woman","Adult feminine with penis",
      "Adult futanari","Adult tomboy","Adult femboy","Adult androgynous"
    };
    public static final String[] BODIES={"Slim","Average","Curvy","Athletic","Fuller figure","Tall"};
    public static final String[] CHESTS={"Flat","Small","Medium","Large"};
    public static final String[] HIPS={"Narrow","Balanced","Wide","Curvy"};
    public static final String[] GENITALS={"Not specified","Vulva","Penis","Penis and testicles","Tucked","Clothed / concealed"};
    public static final String[] EXPRESSIONS={"Feminine","Very feminine","Androgynous","Tomboyish","Masculine"};
    public static final String[] STYLES={"Anime illustration","Soft anime painting","Detailed lineart","Retro pixel art","Boukoku Daitoryou inspired (approximation)","Custom style card"};
    public static final String[] QUALITY={"Balanced","Anatomy priority","Prompt freedom"};
    /**
     * Clothing/exposure is independent of identity or anatomy.
     * 0 is the default for old saved profiles, preserving backwards compatibility.
     */
    public static final String[] CONTENT_LEVELS={
        "Clothed / general","Adult nude","Consensual adult sexual scene"
    };
    public static final String[] PENIS_SIZES={"Very small","Small","Average (default)","Large","Very large"};
    public static final String[] PENIS_STATES={"Automatic","Soft","Semi-erect","Erect"};
    public static final String[] TESTICLE_OPTIONS={"Automatic","Not emphasized","Visible"};
    public static final String[] BULGE_OPTIONS={"None","Subtle","Visible"};
    public static final String[] SPEED_PROFILES={"Fast preview","Balanced","High detail"};
    public static final String[] CAMERA={
        "Automatic","Face focus","Waist-up","Full figure","Two-adult framing"
    };

    public static final class Character {
        public String name="New character";
        public String hair="white hair";
        public String eyes="blue eyes";
        public String clothing="";
        public String traits="";
        public int age=25;
        public int identity=0,body=1,chest=1,hips=1,genitals=0,expression=0;
        public int penisSize=2,penisState=0,testicles=0,bulge=0;
        public int length=2,girth=2,testicleSize=0;
        public Character copy(){
            Character x=new Character();
            x.name=name;x.hair=hair;x.eyes=eyes;x.clothing=clothing;x.traits=traits;
            x.age=age;x.identity=identity;x.body=body;x.chest=chest;x.hips=hips;
            x.genitals=genitals;x.expression=expression;
            x.penisSize=penisSize;x.penisState=penisState;
            x.testicles=testicles;x.bulge=bulge;
            x.length=length;x.girth=girth;x.testicleSize=testicleSize;
            return x;
        }
    }

    public static final class Settings {
        public Character character=new Character();
        public int scene=0,style=0,quality=1,contentLevel=0,camera=0;
        public int speedProfile=1;
        public int faceMode=0,proportionMode=1,complexity=0;
        public String customStylePositive="",customStyleNegative="";
        public boolean anatomyPriority=true;
        public boolean freeformOnly=false;
        public boolean useSameSeed=false;
        public String userPrompt="",extraNegative="",seed="";
    }

    public static final class Recipe {
        public final String positive,negative,scheduler;
        public final int width,height,steps;
        public final float cfg;
        public final long seed;
        public final String explanation;
        public Recipe(String positive,String negative,int width,int height,int steps,
                      float cfg,String scheduler,long seed,String explanation) {
            this.positive=positive;this.negative=negative;
            this.width=width;this.height=height;
            this.steps=steps;this.cfg=cfg;
            this.scheduler=scheduler;this.seed=seed;this.explanation=explanation;
        }
    }

    private static final String BASE_NEGATIVE=
       "worst quality, low quality, bad anatomy, poorly drawn hands, fused fingers, "
       +"extra fingers, extra arms, extra legs, duplicate body, malformed limbs, "
       +"warped face, bad eyes, deformed hands, disconnected limbs, text, watermark, "
       +"child, underage, loli, shota";
    private static final String EXTRA_NEGATIVE=
       "extra pelvis, impossible pose, extra genitals, duplicate anatomy, "
       +"mutated proportions, deformed torso, fused thighs, distorted perspective";

    public static Recipe build(Settings settings,boolean preview,boolean repair){
        if(settings==null || settings.character==null)
            throw new IllegalArgumentException("Character missing");
        Character c=settings.character;
        if(c.age<18 || c.age>120)throw new IllegalArgumentException("Adults aged 18+ only");
        requireIndex(settings.scene,SCENES);requireIndex(settings.style,STYLES);
        requireIndex(settings.quality,QUALITY);
        requireIndex(settings.contentLevel,CONTENT_LEVELS);
        requireIndex(settings.camera,CAMERA);
        requireIndex(settings.speedProfile,SPEED_PROFILES);
        requireIndex(settings.faceMode,QualityTuning.FACE_MODES);
        requireIndex(settings.proportionMode,QualityTuning.PROPORTION_MODES);
        requireIndex(settings.complexity,QualityTuning.COMPLEXITY_MODES);
        requireIndex(c.length,QualityTuning.LENGTHS);
        requireIndex(c.girth,QualityTuning.GIRTHS);
        requireIndex(c.testicleSize,QualityTuning.TESTICLE_SIZES);
        if(settings.customStylePositive==null || settings.customStyleNegative==null)
            throw new IllegalArgumentException("Custom style fields must be initialized");
        requireIndex(c.penisSize,PENIS_SIZES);
        requireIndex(c.penisState,PENIS_STATES);
        requireIndex(c.testicles,TESTICLE_OPTIONS);
        requireIndex(c.bulge,BULGE_OPTIONS);
        requireIndex(c.identity,IDENTITIES);requireIndex(c.body,BODIES);
        requireIndex(c.chest,CHESTS);requireIndex(c.hips,HIPS);
        requireIndex(c.genitals,GENITALS);requireIndex(c.expression,EXPRESSIONS);
        // Explicitly reject attempts to request minor-looking subjects via
        // freeform or character fields, even for the general-purpose presets.
        rejectUnderage(c.name);rejectUnderage(c.hair);rejectUnderage(c.eyes);
        rejectUnderage(c.traits);rejectUnderage(c.clothing);
        rejectUnderage(settings.userPrompt);
        rejectUnderage(settings.customStylePositive);
        rejectUnderage(settings.customStyleNegative);
        if(settings.userPrompt.length()>12000 || settings.extraNegative.length()>12000
            || settings.customStylePositive.length()>4000 || settings.customStyleNegative.length()>4000)
            throw new IllegalArgumentException("Prompt exceeds 12,000 characters");

        int width=512,height=512,steps=30;
        float cfg=6.5f;
        String framing="";
        switch(settings.scene){
            case 0: width=512;height=512;framing="portrait, head and shoulders, face in focus, simple composition";break;
            case 1: width=512;height=512;framing="upper body, waist up, balanced framing";break;
            case 2: width=512;height=512;framing="full body, standing pose, feet visible, centered subject";break;
            case 3: width=512;height=512;framing="standing, centered pose, unobstructed silhouette, simple background";break;
            case 4: width=512;height=512;framing="relaxed indoor scene, natural pose, clean perspective";break;
            case 5: width=512;height=512;framing="solo adult scene, centered composition, intentional pose";break;
            case 6: width=512;height=512;framing="two adults, distinct silhouettes, coherent perspective, clearly separated limbs";steps=34;cfg=6.3f;break;
            case 7: width=512;height=512;framing="video game character sprite, clean silhouette, full body, simple flat background, limited palette";break;
            case 8: width=512;height=512;framing="single character keyframe, neutral pose, readable silhouette, consistent front-facing design";break;
            default: throw new IllegalArgumentException("Scene unsupported");
        }
        // The old "Solo adult scene" and "Two-adult scene" were merely framing
        // hints; nothing directed the image model to make subjects nude or
        // sexually active. This is a separate, explicit user choice now.
        final boolean nude = settings.contentLevel >= 1;
        final boolean intimate = settings.contentLevel == 2;
        final boolean twoAdults = settings.scene == 6 || settings.camera == 4;
        StringBuilder prompt=new StringBuilder();
        if(!settings.freeformOnly){
            // Put high-value character, scene and anatomy instructions FIRST:
            // current Local Dream SD1.5 native inference truncates at 75 usable
            // CLIP tokens; our UI separately reports and plans overflow.
            append(prompt,"adult, anime illustration");
            if(twoAdults)append(prompt,"2 adults, adult couple, consensual interaction");
            else if(c.identity==5)append(prompt,"1boy, solo, adult");
            else append(prompt,"1girl, solo, adult");
            append(prompt,"adult age "+c.age);
            switch(c.identity){
                case 0:append(prompt,"adult woman");break;
                case 1:append(prompt,"adult trans woman, feminine adult face");break;
                case 2:append(prompt,"adult feminine character, feminine body, penis");break;
                case 3:append(prompt,"adult futanari, feminine body");break;
                case 4:append(prompt,"adult tomboy, short casual hairstyle");break;
                case 5:append(prompt,"adult feminine man, femboy");break;
                case 6:append(prompt,"adult androgynous character");break;
            }
            append(prompt,QualityTuning.facePositive(settings.faceMode,
                settings.scene<=1 || settings.camera==1,repair));
            append(prompt,QualityTuning.proportionPositive(settings.proportionMode));
            append(prompt,bodyTags(c));
            if (hasVisiblePenis(c)) {
                if(nude){
                    // Existing saved cards used one 'size' field; retain its
                    // meaning unless the person changes the new dimensions.
                    int effectiveLength=(c.length==2 && c.girth==2 && c.penisSize!=2)
                        ? c.penisSize : c.length;
                    int effectiveGirth=(c.length==2 && c.girth==2 && c.penisSize!=2)
                        ? c.penisSize : c.girth;
                    append(prompt,QualityTuning.anatomyPositive(
                        effectiveLength,effectiveGirth,c.testicleSize,true));
                    if(c.length==2 && c.girth==2 && c.penisSize<=1)
                        append(prompt,c.penisSize==0?"(very small penis:1.2)":"(small penis:1.2)");
                }
                if(nude && c.penisState==1)append(prompt,"flaccid penis");
                if(nude && c.penisState==2)append(prompt,"semi-erect penis");
                if(nude && c.penisState==3)append(prompt,"erect penis");
                if(nude && c.testicles==1)append(prompt,"testicles not emphasized");
                if(nude && c.testicles==2)append(prompt,"visible testicles");
                if(!nude && c.bulge==1)append(prompt,"subtle clothing bulge");
                if(!nude && c.bulge==2)append(prompt,"noticeable clothing bulge");
            }
            append(prompt,expressionTags(c.expression));
            append(prompt,QualityTuning.complexityPositive(settings.complexity));
            // Exposure controls must not be contradicted by saved outfit
            // descriptions. Otherwise the model typically draws clothing.
            if(nude) {
                append(prompt,"(nude:1.35), (naked:1.25), unclothed, visible skin, no outfit, uncensored adult anatomy");
            }
            if(intimate) {
                if(twoAdults) {
                    append(prompt,"consensual adult sex scene, physically intimate adult couple, close interaction, coherent shared pose");
                } else {
                    append(prompt,"explicit adult solo scene, sexual context, clear adult anatomy");
                }
            }
            if(settings.camera==1)framing="face focus, portrait";
            if(settings.camera==2)framing="waist up, upper body";
            if(settings.camera==3)framing="full figure in frame, clearly visible anatomy";
            if(settings.camera==4)framing="two adults in frame, both bodies clearly positioned";
            append(prompt,framing);
            if(!c.hair.trim().isEmpty())append(prompt,c.hair);
            if(!c.eyes.trim().isEmpty())append(prompt,c.eyes);
            if(!nude)append(prompt,c.clothing);
            append(prompt,c.traits);
            if(settings.style==1)append(prompt,"soft painterly anime rendering, subtle shading");
            if(settings.style==2)append(prompt,"clean crisp lineart, high detail, cel shading");
            if(settings.style==3 || settings.scene==7)append(prompt,"pixel art aesthetic, retro game style, pixelated outline, limited color palette");
            if(settings.style==4)append(prompt,QualityTuning.boukokuApproximation());
            if(settings.style==5)append(prompt,settings.customStylePositive);
            if(settings.quality!=2)append(prompt,"balanced body proportions, consistent eyes");
            if(repair)append(prompt,"clean anatomy, consistent body structure, clear limbs, detailed face, precise linework");
        }
        append(prompt,settings.userPrompt);
        if(prompt.length()==0)throw new IllegalArgumentException("Enter a prompt");

        StringBuilder negative=new StringBuilder(BASE_NEGATIVE);
        if(settings.anatomyPriority || settings.quality==1 || repair)append(negative,EXTRA_NEGATIVE);
        if(nude && !settings.freeformOnly) {
            // Relevant only to explicitly selected nudity. Never apply
            // clothing negatives to ordinary portrait/fully dressed presets.
            append(negative,"fully clothed, shirt, pants, jacket, dress, underwear, lingerie, censor bar, mosaic censoring");
        }
        // Do not use male/female/penis as general negative tokens because
        // doing so conflicts with trans/futanari anatomy configurations.
        if(hasVisiblePenis(c) && !settings.freeformOnly) {
            int length=(c.length==2 && c.girth==2 && c.penisSize!=2)
                ? c.penisSize : c.length;
            int girth=(c.length==2 && c.girth==2 && c.penisSize!=2)
                ? c.penisSize : c.girth;
            if(length<=2 && girth<=2)
                append(negative,"huge penis, oversized penis, giant penis, extremely oversized genitals");
        }
        if(!settings.freeformOnly){
            append(negative,QualityTuning.faceNegative(settings.faceMode));
            append(negative,QualityTuning.proportionNegative(settings.proportionMode));
            if(hasVisiblePenis(c) && nude){
                int len=(c.length==2 && c.girth==2 && c.penisSize!=2)?c.penisSize:c.length;
                int gir=(c.length==2 && c.girth==2 && c.penisSize!=2)?c.penisSize:c.girth;
                append(negative,QualityTuning.anatomyNegative(len,gir,c.testicleSize,true));
            }
            if(settings.style==4)append(negative,QualityTuning.defaultStyleNegative());
            if(settings.style==5)append(negative,settings.customStyleNegative);
        }
        append(negative,settings.extraNegative);
        if(settings.quality==2)cfg=7.0f;
        if(twoAdults)cfg=Math.min(cfg,6.3f);
        // Fewer steps directly reduce the sampling work. Resolution is only
        // advisory: Local Dream's parameter-import format does not contain it.
        if(settings.speedProfile==0) {
            steps=preview?10:18;
            width=384;height=384;
        } else if(settings.speedProfile==1) {
            steps=preview?16:Math.min(steps,28);
            width=512;height=512;
        } else {
            steps=preview?22:Math.max(steps,38);
            width=512;height=512;
        }
        if(repair)steps=(settings.speedProfile==0?25:settings.speedProfile==1?36:44);
        steps=Math.max(1,Math.min(50,steps));
        long seed=parseSeed(settings.seed);
        String explanation=(preview?"4-preview batch (set batch=4 in generator). "
                         :repair?"Refine: use img2img/inpaint when repairing details. "
                         :"Normal generation. ")
                         +"Suggested "+width+"×"+height+" resolution; select it MANUALLY in Local Dream."
                         +" Current native SD1.5 conditioning reads 75 useful CLIP tokens per pass;"
                         +" open Long Prompt Planner for multi-pass img2img overflow.";

        return new Recipe(prompt.toString(),negative.toString(),width,height,steps,cfg,
            "euler_a",seed,explanation);
    }

    public static long parseSeed(String raw){
        if(raw==null || raw.trim().isEmpty())return -1;
        try {
            long s=Long.parseLong(raw.trim());
            return (s>=0 && s<=2147483647L) ? s : -1;
        }catch(NumberFormatException e) {
            throw new IllegalArgumentException("Seed must be a positive integer <= 2147483647");
        }
    }

    public static boolean hasVisiblePenis(Character c) {
        if(c==null)return false;
        if(c.genitals==4 || c.genitals==5 || c.genitals==1)return false;
        return c.genitals==2 || c.genitals==3 ||
               (c.genitals==0 && (c.identity==2 || c.identity==3));
    }
    public static String penisSizePositive(int size){
        switch(size){
            case 0:return "(very small penis:1.35), modest size, proportionate adult anatomy";
            case 1:return "(small penis:1.3), modest penis size, proportionate anatomy";
            case 2:return "(average-sized penis:1.3), natural anatomical proportions, realistic size";
            case 3:return "large penis, anatomically proportionate body";
            case 4:return "very large penis";
            default:throw new IllegalArgumentException("Invalid penis size");
        }
    }
    public static String bodyTags(Character c){
        StringBuilder s=new StringBuilder();
        switch(c.body) {
          case 0:append(s,"slim adult body");break;
          case 1:append(s,"natural adult proportions");break;
          case 2:append(s,"curvy figure");break;
          case 3:append(s,"athletic build");break;
          case 4:append(s,"full-figured adult");break;
          case 5:append(s,"tall adult figure");break;
        }
        switch(c.chest){
          case 0:append(s,"flat chest");break;
          case 1:append(s,"small breasts");break;
          case 2:append(s,"medium breasts");break;
          case 3:append(s,"large breasts");break;
        }
        switch(c.hips){
          case 0:append(s,"narrow hips");break;
          case 1:append(s,"natural hip width");break;
          case 2:append(s,"wide hips");break;
          case 3:append(s,"curvy hips and waist");break;
        }
        switch(c.genitals){
          case 1:append(s,"vulva");break;
          case 2:append(s,"penis");break;
          case 3:append(s,"penis and testicles");break;
          case 4:append(s,"tucked anatomy");break;
          case 5:append(s,"concealed anatomy, fully clothed");break;
        }
        return s.toString();
    }
    private static String expressionTags(int idx) {
        switch(idx){
          case 0:return "feminine appearance";
          case 1:return "strongly feminine appearance";
          case 2:return "androgynous appearance";
          case 3:return "tomboyish appearance";
          default:return "masculine appearance";
        }
    }
    private static void requireIndex(int index,String[] a){
        if(index<0 || index>=a.length)throw new IllegalArgumentException("Unknown preset index");
    }
    private static void append(StringBuilder s,String part){
        if(part==null || part.trim().isEmpty())return;
        if(s.length()>0)s.append(", ");
        s.append(part.trim().replaceAll("^,+|,+$",""));
    }
    private static void rejectUnderage(String text){
        if(text==null)return;
        String x=text.toLowerCase(Locale.ROOT);
        if(x.matches("(?s).*\\b(?:loli|shota|preteen|underage|schoolgirl|schoolboy|minor|toddler|child|kid|little girl|little boy|teenager|teenage|teen)\\b.*"))
            throw new IllegalArgumentException("Only clearly adult characters are supported");
    }
}
