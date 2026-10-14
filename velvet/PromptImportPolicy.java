package org.velvetatelier;

/**
 * A contract for native generator handoff: never truncate or reject a
 * user-supplied prompt merely because it exceeds one CLIP window.
 * The model's actual tokenizer (not a whitespace approximation) decides
 * what a single SD1.5 pass can attend to.
 */
public final class PromptImportPolicy {
    private PromptImportPolicy(){}
    public static final class Delivery {
        public final String positive,negative;
        public final boolean longPromptAdvisory;
        private Delivery(String pos,String neg,boolean hint){
            positive=pos;negative=neg;longPromptAdvisory=hint;
        }
    }
    public static Delivery prepare(String positive,String negative){
        if(positive==null || positive.trim().isEmpty())
            throw new IllegalArgumentException("A positive prompt is required");
        String neg=negative==null?"":negative;
        boolean warning;
        try{
            warning=LongPromptPlanner.plan(positive).requiresImg2Img ||
                (!neg.isEmpty() && LongPromptPlanner.plan(neg).requiresImg2Img);
        }catch(RuntimeException ignored){
            // A multi-pass safety ceiling must never veto native generation.
            warning=true;
        }
        // Nothing in this method may substring, split, trim, cap or rebuild
        // the actual prompt sent to the generator.
        return new Delivery(positive,neg,warning);
    }
}