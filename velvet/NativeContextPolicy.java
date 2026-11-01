package org.velvetatelier;

/**
 * Runtime-detected native SD1.5 prompt context contract.
 *
 * Local Dream 2.8.1 reports max_length=77 in /tokenize and cannot attend to
 * later chunks in one denoising pass. Local Dream 3.0 MNN may report zero
 * (dynamic chunks) or a higher fixed context. Only use a one-pass long prompt
 * after the backend itself declares this capability.
 */
public final class NativeContextPolicy {
    private NativeContextPolicy(){}
    public static final int LEGACY_CLIP_CONTEXT=77;
    // Even dynamic cross-attention uses memory roughly proportional to tokens.
    // Limit to three 77-position chunks until tested on the actual handset.
    public static final int MOBILE_NATIVE_CONTEXT_CAP=231;

    public static boolean mayRunSinglePass(int reportedMaxLength,
                                           int positiveTokens,int negativeTokens){
        if(positiveTokens<2 || negativeTokens<2)return false;
        if(reportedMaxLength==LEGACY_CLIP_CONTEXT)return false;
        if(reportedMaxLength<0)return false;
        int supported=reportedMaxLength==0?MOBILE_NATIVE_CONTEXT_CAP:
            Math.min(MOBILE_NATIVE_CONTEXT_CAP,reportedMaxLength);
        return supported>LEGACY_CLIP_CONTEXT
            && positiveTokens<=supported && negativeTokens<=supported;
    }
}
