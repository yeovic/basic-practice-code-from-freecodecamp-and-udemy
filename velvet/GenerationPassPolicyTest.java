package org.velvetatelier;

public final class GenerationPassPolicyTest {
    private static void ok(boolean value,String msg){if(!value)throw new AssertionError(msg);}
    public static void main(String[] args){
        ok(GenerationPassPolicy.requiredPasses(1,5)==1,
            "Long negatives triggered 4 wasted diffusion renders");
        ok(GenerationPassPolicy.requiredPasses(3,1)==3,
            "Positive details were lost");
        String negatives="distorted face, giant genitals, extra eyes";
        ok(GenerationPassPolicy.stableNegative(negatives).equals(negatives),
            "Important exclusions changed");
        String next=GenerationPassPolicy.refinementPrompt("new skirt, blue background");
        ok(next.contains("same facial identity")&&next.contains("new skirt"),
            "Continuation lost character anchor");
        for(int n:new int[]{0,-1}){
            boolean rejected=false;
            try{GenerationPassPolicy.requiredPasses(n,1);}
            catch(IllegalArgumentException ex){rejected=true;}
            ok(rejected,"Bad pass count accepted");
        }
        System.out.println("PASS: no wasted negative-only passes and anchored character continuity");
    }
}