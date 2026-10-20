package org.velvetatelier;

/** Regression fixtures for Local Dream tokenizer overflow behavior. */
public final class NativeTokenBudgetTest {
    static void ok(boolean yes,String description){
        if(!yes)throw new AssertionError(description);
    }
    static final NativeTokenBudget.Probe FAKE_CLIP=text->{
        // BPE-like fixture with an exact UTF-16 overflow offset.
        int tokens=2;
        int overflow=-1;
        for(int i=0;i<text.length();i++){
            char c=text.charAt(i);
            if(Character.isLetterOrDigit(c)){
                if(i==0||!Character.isLetterOrDigit(text.charAt(i-1))){
                    tokens++;
                    if(tokens>12&&overflow<0)overflow=i;
                }
            }
        }
        return new NativeTokenBudget.Snapshot(tokens,overflow);
    };
    public static void main(String[] args)throws Exception{
        String shortOne="adult portrait, aligned eyes, consistent proportions";
        ok(NativeTokenBudget.firstChunk(shortOne,12,FAKE_CLIP).equals(shortOne),
            "Short prompt changed");
        String longOne="aligned eyes, coherent mouth, small limbs, sharp details, "
            +"neutral environment, illustrated texture, bright daylight, "
            +"no distortion, no mismatched parts, no eye asymmetry";
        String prefix=NativeTokenBudget.firstChunk(longOne,12,FAKE_CLIP);
        ok(prefix.startsWith("aligned eyes"),"Identity anchor lost");
        ok(FAKE_CLIP.check(prefix).tokens<=12,"Truncated result still overflow");
        ok(prefix.length()<longOne.length(),"Overlong prompt was not clipped");
        String huge="many tiny distinct tokens, ";
        StringBuilder input=new StringBuilder("face structure, proportions, ");
        for(int i=0;i<300;i++)input.append(huge);
        ok(FAKE_CLIP.check(NativeTokenBudget.firstChunk(input.toString(),12,FAKE_CLIP)).tokens<=12,
            "Large negative prompt rejected instead of using its valid prefix");
        boolean rejected=false;
        try{NativeTokenBudget.firstChunk("phrase",2,FAKE_CLIP);}
        catch(IllegalArgumentException e){rejected=true;}
        ok(rejected,"Invalid token ceiling accepted");
        rejected=false;
        try{
            NativeTokenBudget.firstChunk("verylongword",6,
                text->new NativeTokenBudget.Snapshot(100,-1));
        }catch(IllegalArgumentException e){rejected=true;}
        ok(rejected,"Irreducible term did not fail safely");
        System.out.println("PASS: exact-native-overflow core prefix, long negatives, malformed limits");
    }
}