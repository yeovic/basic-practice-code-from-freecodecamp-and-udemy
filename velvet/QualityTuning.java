package org.velvetatelier;

/**
 * Concise, model-aware image quality prompt modules.
 * Stable Diffusion 1.5 CLIP commonly operates in 77-token chunks: identity,
 * face and anatomy cues must be early. This is a word-count approximation;
 * the exact model tokenizer is only available inside the native backend.
 */
public final class QualityTuning {
    private QualityTuning(){}
    public static final String[] FACE_MODES={
        "Auto face","Balanced face","Face priority","Body / scene priority"
    };
    public static final String[] PROPORTION_MODES={
        "Stylized anime","Balanced proportions","Natural proportions"
    };
    public static final String[] COMPLEXITY_MODES={"Simple scene","Normal scene","Detailed scene"};
    public static final String[] LENGTHS={"Very short","Short","Average","Long","Very long"};
    public static final String[] GIRTHS={"Very slim","Slim","Average","Thick","Very thick"};
    public static final String[] TESTICLE_SIZES={"Not emphasized","Small","Average","Large"};
    public static final String[] STYLE_ADDITIONS={"Boukoku Daitoryou (approximation)","Custom style card"};

    public static String facePositive(int mode,boolean portrait,boolean repair){
        if(mode==3)return "readable adult face, aligned eyes";
        if(mode==2||repair||portrait)
            return "(well-drawn adult face:1.2), aligned eyes, consistent pupils, clear mouth, coherent expression";
        if(mode==1)return "well-drawn eyes, clear facial proportions";
        return portrait?"clear face, balanced eyes":"recognizable face";
    }
    public static String faceNegative(int mode){
        if(mode==2)
            return "deformed face, melted face, asymmetrical eyes, crossed eyes, misplaced pupils, extra eyes, twisted jaw, malformed mouth";
        if(mode==3)return "extra eyes, melted face";
        return "distorted face, asymmetrical eyes, extra eyes, malformed mouth";
    }
    public static String proportionPositive(int mode){
        switch(mode){
            case 0:return "stylized anime proportions";
            case 1:return "proportionate anatomy";
            case 2:return "natural adult body proportions, coherent pelvis and limb lengths";
            default:throw new IllegalArgumentException("Invalid proportion mode");
        }
    }
    public static String proportionNegative(int mode){
        return mode==2?"exaggerated proportions, huge hands, distorted torso, inconsistent limb lengths":
            mode==1?"impossible pose, warped pelvis":"";
    }
    public static String complexityPositive(int mode){
        if(mode==0)return "simple background, unobstructed pose, clean silhouette";
        if(mode==2)return "detailed environmental lighting, carefully arranged background";
        return "clean composition";
    }
    public static String anatomyPositive(int length,int girth,int testicles,boolean nude){
        if(!nude)return "";
        String l;
        switch(length){
            case 0:l="(very short penis:1.35)";break;
            case 1:l="(short penis:1.3)";break;
            case 2:l="(average length penis:1.2)";break;
            case 3:l="long penis";break;
            case 4:l="very long penis";break;
            default:throw new IllegalArgumentException("Invalid length");
        }
        String g;
        switch(girth){
            case 0:g="(very slim shaft:1.3)";break;
            case 1:g="(slim shaft:1.25)";break;
            case 2:g="average girth";break;
            case 3:g="thick shaft";break;
            case 4:g="very thick shaft";break;
            default:throw new IllegalArgumentException("Invalid girth");
        }
        String t;
        switch(testicles){
            case 0:t="";break;
            case 1:t="small testicles";break;
            case 2:t="natural-size testicles";break;
            case 3:t="large testicles";break;
            default:throw new IllegalArgumentException("Invalid testicle size");
        }
        return l+", "+g+(t.isEmpty()?"":", "+t);
    }
    public static String anatomyNegative(int length,int girth,int testicles,boolean nude){
        if(!nude)return "";
        StringBuilder neg=new StringBuilder();
        if(length<=2)neg.append("giant penis, excessively long shaft, oversized genitals");
        if(girth<=2){
            if(neg.length()>0)neg.append(", ");
            neg.append("extremely thick shaft, exaggerated girth");
        }
        if(testicles==1||testicles==0){
            if(neg.length()>0)neg.append(", ");
            neg.append("huge testicles");
        }
        return neg.toString();
    }
    public static String boukokuApproximation(){
        // Not an authenticated model/LoRA recreation. A starter editable card.
        return "retro visual novel linework, controlled cel shading, subtle muted palette, expressive adult anime faces";
    }
    public static String defaultStyleNegative(){
        return "muddy rendering, plastic 3D skin, inconsistent line thickness";
    }
    public static int approximateWords(String prompt){
        if(prompt==null||prompt.trim().isEmpty())return 0;
        return prompt.trim().split("\\s+").length;
    }
}
