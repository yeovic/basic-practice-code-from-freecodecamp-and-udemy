package org.velvetatelier;

import java.util.Objects;

/** Standalone JVM tests: recipes remain model-compatible, adult and predictable. */
public final class PromptBuilderTest {
    private static void check(boolean yes,String msg){if(!yes)throw new AssertionError(msg);}
    public static void main(String[] args) {
        for(int scene=0;scene<PromptBuilder.SCENES.length;scene++){
            for(int identity=0;identity<PromptBuilder.IDENTITIES.length;identity++){
                PromptBuilder.Settings s=new PromptBuilder.Settings();
                s.scene=scene;s.character.identity=identity;
                PromptBuilder.Recipe r=PromptBuilder.build(s,false,false);
                check(!r.positive.isEmpty(),"Empty prompt");
                check(r.width>0 && r.height>0,"Bad dimensions");
                check(r.steps<=50 && r.steps>=1,"Bad steps");
                check(r.cfg>=1.0f && r.cfg<=30.0f,"Invalid CFG");
                check(r.negative.contains("underage"),"No adult negative exclusions");
                check(!r.negative.contains(", male,")&&!r.negative.contains(", penis,"),
                      "Bad general negatives against relevant adult bodies");
            }
        }
        PromptBuilder.Settings trans=new PromptBuilder.Settings();
        trans.character.identity=1;
        trans.character.genitals=2;
        trans.character.name="Yuki";
        trans.userPrompt="purple dress, rainy night";
        PromptBuilder.Recipe r=PromptBuilder.build(trans,false,false);
        check(r.positive.contains("trans woman")&&r.positive.contains("penis"),
              "Adult trans anatomy lost");
        check(r.positive.contains("purple dress"),"Freeform suffix lost");
        PromptBuilder.Settings futa=new PromptBuilder.Settings();
        futa.character.identity=3;futa.character.genitals=3;
        r=PromptBuilder.build(futa,true,false);
        check(r.positive.contains("futanari") && r.positive.contains("testicles"),
              "Custom genital configuration absent");
        check(r.steps==16,"Preview steps not used");
        r=PromptBuilder.build(futa,false,true);
        check(r.steps==36 && r.positive.contains("precise linework"),
              "Refine recipe incorrect");
        futa.freeformOnly=true;futa.userPrompt="adult woman, silver armor";
        r=PromptBuilder.build(futa,false,false);
        check(r.positive.equals("adult woman, silver armor"),"Freeform changed");
        PromptBuilder.Settings underage=new PromptBuilder.Settings();
        underage.userPrompt="teenager";boolean threw=false;
        try{PromptBuilder.build(underage,false,false);}catch(IllegalArgumentException ex){threw=true;}
        check(threw,"Underage prompt accepted");
        underage.userPrompt="";underage.character.age=16;threw=false;
        try{PromptBuilder.build(underage,false,false);}catch(IllegalArgumentException ex){threw=true;}
        check(threw,"Underage character age accepted");
        PromptBuilder.Settings s=new PromptBuilder.Settings();
        s.character.identity=0;s.character.genitals=1;
        r=PromptBuilder.build(s,false,false);
        check(r.positive.contains("vulva") && !r.negative.contains("penis"),
              "Body config not respected");
        s.seed="137";r=PromptBuilder.build(s,false,false);
        check(r.seed==137,"Seed lock failed");
        System.out.println("PASS: scene x body preset combinations, freeform, negatives, seeds, repair, adults-only");
    }
}
