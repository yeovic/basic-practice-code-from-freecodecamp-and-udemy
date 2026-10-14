package org.velvetatelier;
import java.util.List;

/** Focused v2.8 regression of face, proportions, styles, and long-prompt plans. */
public final class QualityTuningTest {
    static void ok(boolean valid,String message){if(!valid)throw new AssertionError(message);}
    public static void main(String[] args){
        for(int face=0;face<QualityTuning.FACE_MODES.length;face++){
            for(int prop=0;prop<QualityTuning.PROPORTION_MODES.length;prop++){
                PromptBuilder.Settings s=new PromptBuilder.Settings();
                s.faceMode=face;s.proportionMode=prop;
                s.scene=face==2?0:2;
                PromptBuilder.Recipe r=PromptBuilder.build(s,false,false);
                ok(r.positive.contains("face")||r.positive.contains("eyes"),"Face guidance absent");
                ok(r.negative.contains("face"),"Face negatives absent");
                ok(!r.positive.contains("null"),"Null prompt");
            }
        }
        PromptBuilder.Settings p=new PromptBuilder.Settings();
        p.character.identity=3;p.contentLevel=1;
        p.character.penisSize=1;p.character.length=2;p.character.girth=2;
        PromptBuilder.Recipe small=PromptBuilder.build(p,false,false);
        ok(small.positive.contains("small penis"),"Legacy size card lost");
        ok(small.negative.contains("giant penis"),"Small-size negative missing");
        p.character.length=0;p.character.girth=1;p.character.testicleSize=1;
        PromptBuilder.Recipe detailed=PromptBuilder.build(p,false,false);
        ok(detailed.positive.contains("very short penis"),"Length control missing");
        ok(detailed.positive.contains("slim shaft"),"Girth control missing");
        ok(detailed.positive.contains("small testicles"),"Separate testicles missing");
        ok(!detailed.positive.contains("(small penis:1.2)"),"Legacy override creates conflict");
        p.userPrompt="at a mountain cottage with silver moonlight and a waterfall";
        PromptBuilder.Recipe prioritized=PromptBuilder.build(p,false,false);
        int firstPrompt=prioritized.positive.indexOf("at a mountain cottage");
        int lengthPrompt=prioritized.positive.indexOf("very short penis");
        ok(lengthPrompt>0 && lengthPrompt<firstPrompt,
            "Size settings were delayed past optional scene detail");
        p.style=4;
        PromptBuilder.Recipe stylePriority=PromptBuilder.build(p,false,false);
        ok(stylePriority.positive.indexOf("retro visual novel linework")
                <stylePriority.positive.indexOf("at a mountain cottage"),
            "Style priority did not survive long prompt");
        int firstNude=stylePriority.positive.indexOf("(nude:1.35)");
        ok(firstNude>=0 &&
           stylePriority.positive.indexOf("(nude:1.35)",firstNude+1)<0,
            "Duplicate nude conditioning wastes early CLIP tokens");
        p.style=0;
        p.userPrompt="";
        p.character.genitals=1;
        PromptBuilder.Recipe absent=PromptBuilder.build(p,false,false);
        ok(!absent.positive.contains("very short penis"),"Dimensional tags leaked into non-penis body");
        p.style=4;p.character.genitals=0;
        PromptBuilder.Recipe style=PromptBuilder.build(p,false,false);
        ok(style.positive.contains("retro visual novel linework"),"Inspired style missing");
        p.style=5;p.customStylePositive="original paint palette, soft light";
        p.customStyleNegative="muddy lines";
        PromptBuilder.Recipe userStyle=PromptBuilder.build(p,false,false);
        ok(userStyle.positive.contains("original paint palette"),"Custom style missing");
        ok(userStyle.negative.contains("muddy lines"),"Custom style negative missing");
        p.freeformOnly=true;p.userPrompt="adult, cyberpunk, red sky";
        PromptBuilder.Recipe free=PromptBuilder.build(p,false,false);
        ok(free.positive.equals("adult, cyberpunk, red sky"),"Freeform changed");
        StringBuilder longOne=new StringBuilder();
        for(int i=0;i<155;i++){
            if(i>0)longOne.append(", ");
            longOne.append("detail").append(i);
        }
        LongPromptPlanner.Plan plan=LongPromptPlanner.plan(longOne.toString());
        ok(plan.requiresImg2Img,"Long prompt incorrectly single-pass");
        ok(plan.passes.size()>=4,"Long prompt dropped");
        int total=0;
        for(String pass:plan.passes){
            total+=LongPromptPlanner.plan(pass).totalWords;
            ok(LongPromptPlanner.approxWords(pass)<=36,"Chunk exceeds its heuristic budget");
        }
        ok(total==155,"Long prompt content lost");
        ok(!LongPromptPlanner.plan("adult portrait, blue eyes").requiresImg2Img,"Short prompt multi-pass");
        System.out.println("PASS: face/identity quality, dimensional overrides, inspired/custom styles, long prompt pass preservation");
    }
}
