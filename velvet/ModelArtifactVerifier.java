package org.velvetatelier;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Small standalone verifier for the specific MNN assets required by Local Dream. */
public final class ModelArtifactVerifier {
    private ModelArtifactVerifier(){}
    public static final String[] REQUIRED={
        "clip_v2.mnn","clip_v2.mnn.weight","unet.mnn","unet.mnn.weight",
        "vae_decoder.mnn","vae_decoder.mnn.weight",
        "vae_encoder.mnn","vae_encoder.mnn.weight",
        "pos_emb.bin","token_emb.bin"
    };
    public static List<String> missing(File modelDir){
        List<String> result=new ArrayList<>();
        if(modelDir==null || !new File(modelDir,"finished").isFile())
            result.add("finished");
        for(String filename:REQUIRED){
            File file=modelDir==null?null:new File(modelDir,filename);
            if(file==null||!file.isFile()||file.length()==0)
                result.add(filename);
        }
        return result;
    }
    public static boolean ready(File modelDir){
        return missing(modelDir).isEmpty();
    }
}
