package org.velvetatelier;

/** Regression: optional multi-pass limits must never prevent Generate. */
public final class PromptImportPolicyTest {
    private static void ok(boolean success,String message){
        if(!success)throw new AssertionError(message);
    }
    public static void main(String[] unused){
        PromptImportPolicy.Delivery shortOne=
            PromptImportPolicy.prepare("adult portrait, blue eyes","bad hands");
        ok("adult portrait, blue eyes".equals(shortOne.positive),"Short prompt altered");
        ok(!shortOne.longPromptAdvisory,"Short prompt incorrectly warned");
        StringBuilder longText=new StringBuilder("adult portrait");
        for(int i=0;i<1200;i++)longText.append(", original_attribute_").append(i);
        String source=longText.toString();
        PromptImportPolicy.Delivery longOne=
            PromptImportPolicy.prepare(source,"poor eyes, warped features");
        ok(source.equals(longOne.positive),"1200 detail tags were removed");
        ok(longOne.longPromptAdvisory,"Long prompt not warned");
        ok(source.endsWith("original_attribute_1199"),"Late instructions dropped");
        String nonAscii="성인 캐릭터, 夜景, фантазия, "+source;
        ok(PromptImportPolicy.prepare(nonAscii,"").positive.equals(nonAscii),
            "Unicode prompt changed");
        StringBuilder negative=new StringBuilder("bad face");
        for(int i=0;i<600;i++)negative.append(", excluded_").append(i);
        PromptImportPolicy.Delivery n=PromptImportPolicy.prepare("adult girl",negative.toString());
        ok(n.longPromptAdvisory,"Long negative prompt did not warn");
        ok(n.negative.equals(negative.toString()),"Negative exclusions dropped");
        boolean invalid=false;
        try{PromptImportPolicy.prepare("   ","");}
        catch(IllegalArgumentException e){invalid=true;}
        ok(invalid,"Empty positive prompt accepted");
        System.out.println("PASS: short, 1200-tag overflow, long negative, Unicode and no hard prompt cap");
    }
}