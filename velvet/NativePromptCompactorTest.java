package org.velvetatelier;

import java.util.Arrays;
import java.util.List;

public final class NativePromptCompactorTest {
    static void ok(boolean b,String message){if(!b)throw new AssertionError(message);}
    static int words(String s){return s.trim().split("\\s+").length;}
    static String concat(String... parts){return String.join(", ",parts);}
    public static void main(String[] args)throws Exception{
        // Two old 36-word chunks can fit together into a 77-position model.
        String a=repeat("blue",30);
        String b=repeat("hair",30);
        String c=repeat("landscape",30);
        List<String> fitted=NativePromptCompactor.compact(Arrays.asList(a,b,c),
            77,12,text->words(text)+2);
        ok(fitted.size()==2,"Expected 3 rough chunks to fit in 2 real windows");
        ok(fitted.get(0).equals(concat(a,b)),"Lost or reordered first two chunks");
        ok(fitted.get(1).equals(c),"Lost last chunk");
        for(String p:fitted)ok(words(p)+2<=77,"Native overrun");

        List<String> small=NativePromptCompactor.compact(Arrays.asList(
            "adult portrait","subtle light","accurate eyes"),
            77,12,text->words(text)+2);
        ok(small.size()==1,"Small prompt should use one generation");
        ok(small.get(0).contains("accurate eyes"),"Lost final instruction");

        boolean rejected=false;
        try{
            NativePromptCompactor.compact(Arrays.asList(a,b,c),
                77,1,text->words(text)+2);
        }catch(IllegalArgumentException e){rejected=true;}
        ok(rejected,"Exceeded pass cap");
        rejected=false;
        try{NativePromptCompactor.compact(Arrays.asList(repeat("token",80)),
            77,12,text->words(text)+2);
        }catch(IllegalArgumentException e){rejected=true;}
        ok(rejected,"Accepted oversized verified fragment");
        System.out.println("PASS: native tokenizer compacts prompt chunks, preserves order and reduces passes");
    }
    static String repeat(String term,int n){
        StringBuilder out=new StringBuilder();
        for(int i=0;i<n;i++){if(i>0)out.append(' ');out.append(term);}
        return out.toString();
    }
}
