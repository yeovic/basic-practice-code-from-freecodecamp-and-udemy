package org.velvetatelier;

import java.util.ArrayList;
import java.util.List;

/**
 * Compatible with SD1.5's 77-position CLIP MNN encoder.
 * Each planned pass is short enough to submit separately; later passes must
 * use Local Dream image-to-image with the preceding output as init image.
 * This is SEQUENTIAL multipass conditioning, not 154-token UNet context.
 *
 * We intentionally do not claim native multi-chunk support: upstream Local
 * Dream v2.8.1 PipelineSd15Cpu fixes CLIP/UNet context to 77 rows.
 */
public final class LongPromptPlanner {
    private LongPromptPlanner(){}
    public static final class Plan {
        public final List<String> passes;
        public final int totalWords;
        public final boolean requiresImg2Img;
        public Plan(List<String> p,int words){
            passes=p;totalWords=words;requiresImg2Img=p.size()>1;
        }
    }

    // Cautious approximate bound, not an exact CLIP BPE token count.
    private static final int WORD_BUDGET=36;
    private static final int MAX_PASSES=12;
    public static Plan plan(String prompt){
        if(prompt==null || prompt.trim().isEmpty())
            throw new IllegalArgumentException("Prompt is empty");
        if(prompt.length()>12000)throw new IllegalArgumentException("Prompt exceeds 12,000 characters");
        // Split on semantic commas while never silently dropping fragments.
        String[] clauses=prompt.split(",",-1);
        List<String> passes=new ArrayList<>();
        StringBuilder current=new StringBuilder();
        int count=0,total=0;
        for(String raw:clauses){
            String clause=raw.trim();
            if(clause.isEmpty())continue;
            String[] words=clause.split("\\s+");
            total+=words.length;
            // Single long clauses must also be split, otherwise a freeform
            // paragraph could overflow a fixed 77-token native text encoder.
            int offset=0;
            while(offset<words.length){
                if(count>=WORD_BUDGET){
                    passes.add(current.toString());
                    current.setLength(0);count=0;
                }
                int take=Math.min(WORD_BUDGET-count,words.length-offset);
                StringBuilder fragment=new StringBuilder();
                for(int i=0;i<take;i++){
                    if(fragment.length()>0)fragment.append(' ');
                    fragment.append(words[offset+i]);
                }
                if(current.length()>0)current.append(", ");
                current.append(fragment);
                count+=take;
                offset+=take;
                if(count==WORD_BUDGET && offset<words.length){
                    passes.add(current.toString());
                    current.setLength(0);count=0;
                }
            }
        }
        if(current.length()>0)passes.add(current.toString());
        if(passes.isEmpty())throw new IllegalArgumentException("No usable prompt tags");
        if(passes.size()>MAX_PASSES)
            throw new IllegalArgumentException("Prompt exceeds 12 passes. Shorten details before generation.");
        return new Plan(passes,total);
    }
    public static String instructions(Plan plan){
        if(plan==null||plan.passes.isEmpty())return "No prompt passes";
        if(!plan.requiresImg2Img)
            return "Single-pass candidate. Check exact CLIP token count inside Local Dream.";
        return "Native MNN CLIP remains limited to 77 positions per pass. "
            +"Generate PASS 1 normally. For every subsequent pass, load the "
            +"previous image as input in Local Dream image-to-image, import "
            +"the next prompt chunk, and use low denoising strength to preserve "
            +"the scene. Confirm each pass fits the native token counter. "
            +"This covers more prompt details over multiple renders, but "
            +"does not concatenate CLIP embeddings into one UNet call.";
    }
}
