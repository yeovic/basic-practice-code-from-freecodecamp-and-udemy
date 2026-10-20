package org.velvetatelier;

/**
 * Protects the 77-position SD1.5 CLIP input using exact BPE feedback from
 * Local Dream's /tokenize endpoint. That endpoint reports UTF-16 overflow
 * offsets, which correspond to Java String indices.
 *
 * This is not native context expansion; it constructs one valid chunk.
 */
public final class NativeTokenBudget {
    private NativeTokenBudget(){}
    public interface Probe {
        Snapshot check(String text) throws Exception;
    }
    public static final class Snapshot {
        public final int tokens,overflowUtf16;
        public Snapshot(int tokens,int overflowUtf16){
            this.tokens=tokens;this.overflowUtf16=overflowUtf16;
        }
    }
    public static String firstChunk(String original,int maxTokens,Probe probe)throws Exception{
        if(original==null||original.trim().isEmpty()||probe==null)
            throw new IllegalArgumentException("Missing prompt or tokenizer");
        if(maxTokens<4||maxTokens>77)
            throw new IllegalArgumentException("Invalid context size");
        String candidate=original.trim();
        // Native overflow is inside a token or textual inversion, not
        // necessarily on punctuation. Clip before it and reverify with BPE.
        for(int pass=0;pass<24;pass++){
            Snapshot count=probe.check(candidate);
            if(count.tokens<=maxTokens)return candidate;
            int preferred=count.overflowUtf16;
            if(preferred<4||preferred>candidate.length()){
                // Fallback if backend omits overflow_offset.
                preferred=Math.max(4,(int)(candidate.length()*0.8));
            }
            int comma=candidate.lastIndexOf(',',Math.min(preferred-1,candidate.length()-1));
            // Commas usually delimit separate safe descriptive clauses.
            // Avoid returning just one very short clause when the first
            // phrase happens to contain no commas.
            int cut=comma>=4?comma:
                Math.max(candidate.lastIndexOf(' ',preferred-1),
                         candidate.lastIndexOf(';',preferred-1));
            if(cut<=0||cut>=candidate.length())
                throw new IllegalArgumentException("One prompt term exceeds native token budget");
            String reduced=candidate.substring(0,cut).trim();
            if(reduced.isEmpty()||reduced.equals(candidate))
                throw new IllegalArgumentException("Unable to form native token-safe prefix");
            candidate=reduced;
        }
        throw new IllegalArgumentException("Prompt could not fit native token window");
    }
}
