package org.velvetatelier;

import java.util.ArrayList;
import java.util.List;

/**
 * Packs ordered, prevalidated prompt clauses into the largest usable CLIP
 * windows. Uses the REAL native tokenizer result, rather than word count.
 * This can reduce expensive sequential img2img passes without dropping tags.
 */
public final class NativePromptCompactor {
    private NativePromptCompactor(){}
    public interface TokenCount {
        int count(String prompt) throws Exception;
    }

    public static List<String> compact(List<String> verifiedChunks,
                                       int tokenLimit,
                                       int maxPasses,
                                       TokenCount tokenizer)throws Exception {
        if(verifiedChunks==null||verifiedChunks.isEmpty()||tokenizer==null)
            throw new IllegalArgumentException("Missing prompt chunks or tokenizer");
        if(tokenLimit<4||tokenLimit>77||maxPasses<1||maxPasses>32)
            throw new IllegalArgumentException("Invalid native generation limits");

        ArrayList<String> output=new ArrayList<>();
        String pending=null;
        for(String fragment:verifiedChunks) {
            if(fragment==null||fragment.trim().isEmpty())continue;
            String part=fragment.trim();
            if(tokenizer.count(part)>tokenLimit)
                throw new IllegalArgumentException("Unverified prompt chunk exceeds context");
            if(pending==null){pending=part;continue;}
            String merged=pending+", "+part;
            if(tokenizer.count(merged)<=tokenLimit){
                pending=merged;
            }else{
                output.add(pending);
                if(output.size()>=maxPasses)
                    throw new IllegalArgumentException("Long prompt requires too many inference passes");
                pending=part;
            }
        }
        if(pending!=null)output.add(pending);
        if(output.isEmpty()||output.size()>maxPasses)
            throw new IllegalArgumentException("Long prompt pass limit exceeded");
        return output;
    }
}
