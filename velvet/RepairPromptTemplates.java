package org.velvetatelier;

/** Short model-compatible repair prompts for a 512px img2img crop. */
public final class RepairPromptTemplates {
    private RepairPromptTemplates(){}
    public static final String[] TARGETS={
        "Face & eyes","Hands & fingers","Body proportions","Adult anatomy proportions"
    };
    public static final String[] ANATOMY_SIZES={"Natural / average","Smaller proportions","Larger proportions"};

    public static String positive(int target,int anatomySize){
        switch(target){
            case 0:return "adult anime face, same identity and hair color, matching original art style, "
                +"(well drawn symmetrical eyes:1.25), aligned pupils, coherent facial structure, "
                +"natural mouth, clean jawline, expressive adult face";
            case 1:return "well drawn adult hand, five anatomically coherent fingers, "
                +"natural wrist, correct finger joints, same illustration style";
            case 2:return "natural adult body proportions, correctly connected limbs, "
                +"coherent pelvis and torso, matching original anime character";
            case 3:{
                String sizing=anatomySize==1
                    ?"(modest adult genital proportions:1.3), small proportionate anatomy"
                    :anatomySize==2
                    ?"large yet proportionate adult anatomy"
                    :"average size adult anatomy, proportional natural size";
                return "adult character, "+sizing+", coherent pelvis and nearby body proportions, "
                    +"matching original body and illustration style";
            }
            default:throw new IllegalArgumentException("Invalid repair target");
        }
    }
    public static String negative(int target,int anatomySize){
        switch(target){
            case 0:return "distorted face, malformed eyes, uneven pupils, extra eyes, "
                +"crossed eyes, melted features, warped lips, extra mouth, asymmetrical face";
            case 1:return "extra fingers, fused fingers, missing fingers, deformed hand, "
                +"twisted wrist, duplicated hands, malformed knuckles";
            case 2:return "deformed torso, mismatched legs, disconnected limbs, "
                +"double pelvis, impossible pose, warped proportions";
            case 3:return (anatomySize!=2
                    ?"gigantic genitals, huge penis, excessive girth, oversized anatomy, "
                    :"")+
                    "duplicate genitals, malformed anatomy, impossible body connection, "
                    +"distorted pelvis, extra limbs";
            default:throw new IllegalArgumentException("Invalid repair target");
        }
    }
}
