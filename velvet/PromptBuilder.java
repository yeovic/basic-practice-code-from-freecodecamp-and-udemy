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
    public static final String[] STYLES={"Anime illustration","Soft anime painting","Detailed lineart","Retro pixel art"};
    public static final String[] QUALITY={"Balanced","Anatomy priority","Prompt freedom"};

    public static final class Character {
        public String name="New character";
        public String hair="white hair";
        public String eyes="blue eyes";
        public String clothing="";
        public String traits="";
        public int age=25;
        public int identity=0,body=1,chest=1,hips=1,genitals=0,expression=0;
        public Character copy(){
            Character x=new Character();
            x.name=name;x.hair=hair;x.eyes=eyes;x.clothing=clothing;x.traits=traits;
            x.age=age;x.identity=identity;x.body=body;x.chest=chest;x.hips=hips;
            x.genitals=genitals;x.expression=expression;
            return x;
        }
    }

    public static final class Settings {
        public Character character=new Character();
        public int scene=0,style=0,quality=1;
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
        requireIndex(c.identity,IDENTITIES);requireIndex(c.body,BODIES);
        requireIndex(c.chest,CHESTS);requireIndex(c.hips,HIPS);
        requireIndex(c.genitals,GENITALS);requireIndex(c.expression,EXPRESSIONS);
        // Explicitly reject attempts to request minor-looking subjects via
        // freeform or character fields, even for the general-purpose presets.
        rejectUnderage(c.name);rejectUnderage(c.hair);rejectUnderage(c.eyes);
        rejectUnderage(c.traits);rejectUnderage(c.clothing);
        rejectUnderage(settings.userPrompt);
        if(settings.userPrompt.length()>12000 || settings.extraNegative.length()>12000)
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
        StringBuilder prompt=new StringBuilder();
        if(!settings.freeformOnly){
            append(prompt,"masterpiece, best quality, detailed anime illustration");
            if(settings.scene==6)append(prompt,"2 adults");
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
            append(prompt,bodyTags(c));
            append(prompt,expressionTags(c.expression));
            append(prompt,framing);
            if(!c.hair.trim().isEmpty())append(prompt,c.hair);
            if(!c.eyes.trim().isEmpty())append(prompt,c.eyes);
            append(prompt,c.clothing);
            append(prompt,c.traits);
            if(settings.style==1)append(prompt,"soft painterly anime rendering, subtle shading");
            if(settings.style==2)append(prompt,"clean crisp lineart, high detail, cel shading");
            if(settings.style==3 || settings.scene==7)append(prompt,"pixel art aesthetic, retro game style, pixelated outline, limited color palette");
            if(settings.quality!=2)append(prompt,"good anatomy, balanced body proportions, detailed eyes");
            if(repair)append(prompt,"clean anatomy, consistent body structure, clear limbs, detailed face, precise linework");
        }
        append(prompt,settings.userPrompt);
        if(prompt.length()==0)throw new IllegalArgumentException("Enter a prompt");

        StringBuilder negative=new StringBuilder(BASE_NEGATIVE);
        if(settings.anatomyPriority || settings.quality==1 || repair)append(negative,EXTRA_NEGATIVE);
        // Do not use male/female/penis as general negative tokens because
        // doing so conflicts with trans/futanari anatomy configurations.
        append(negative,settings.extraNegative);
        if(settings.quality==2)cfg=7.0f;
        if(settings.scene==6)cfg=Math.min(cfg,6.3f);
        if(preview)steps=16;
        if(repair)steps=36;
        steps=Math.max(1,Math.min(50,steps));
        long seed=parseSeed(settings.seed);
        return new Recipe(prompt.toString(),negative.toString(),width,height,steps,cfg,
            "euler_a",seed, preview
              ? "Preview quality: choose 4 seeds using the generator's batch control."
              : repair
                ? "Refine setup: use the same seed, or use the generator's existing img2img/inpaint repair interface."
                : "Balanced offline generation for the selected adult character and composition.");
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
