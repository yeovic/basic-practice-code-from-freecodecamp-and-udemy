package org.velvetatelier;

/** Stable composition policy for sequential SD1.5 img2img long prompts. */
public final class GenerationPassPolicy {
    private GenerationPassPolicy(){}

    /** Only positive chunks create new expensive diffusion generations. */
    public static int requiredPasses(int positiveChunks,int negativeChunks){
        if(positiveChunks<1 || negativeChunks<1)
            throw new IllegalArgumentException("Prompt chunks missing");
        if(positiveChunks>12)
            throw new IllegalArgumentException("Maximum 12 native passes");
        return positiveChunks;
    }

    /** Keep first/prioritized structural negative tags for every pass. */
    public static String stableNegative(String prioritizedNegative){
        if(prioritizedNegative==null || prioritizedNegative.trim().isEmpty())
            throw new IllegalArgumentException("No negative prompt");
        return prioritizedNegative;
    }

    /** Re-anchor later passes to reduce identity/pose drift. */
    public static String refinementPrompt(String segment){
        if(segment==null || segment.trim().isEmpty())
            throw new IllegalArgumentException("Missing refinement");
        return "same adult character, same facial identity, preserve body proportions, "
            +"keep original pose and composition, "+segment;
    }
}
