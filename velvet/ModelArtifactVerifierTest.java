package org.velvetatelier;

import java.io.File;
import java.nio.file.Files;
import java.util.List;

/** Native-model integrity checks run in CI without an Android emulator. */
public final class ModelArtifactVerifierTest {
    private static void ok(boolean v,String msg){if(!v)throw new AssertionError(msg);}
    public static void main(String[] args)throws Exception{
        File dir=Files.createTempDirectory("velvet-model-verify-").toFile();
        try{
            ok(!ModelArtifactVerifier.ready(dir),"Empty directory marked ready");
            ok(ModelArtifactVerifier.missing(dir).contains("finished"),"Missing marker unreported");
            new File(dir,"finished").createNewFile();
            ok(!ModelArtifactVerifier.ready(dir),"Marker-only model marked ready");
            for(String f:ModelArtifactVerifier.REQUIRED){
                File out=new File(dir,f);
                Files.write(out.toPath(),new byte[]{42,36,77,11});
            }
            ok(ModelArtifactVerifier.ready(dir),"All required files not recognized");
            String victim=ModelArtifactVerifier.REQUIRED[3];
            Files.write(new File(dir,victim).toPath(),new byte[0]);
            ok(!ModelArtifactVerifier.ready(dir),"Zero-size MNN weight accepted");
            List<String> missing=ModelArtifactVerifier.missing(dir);
            ok(missing.contains(victim),"Missing filename not diagnosed");
            Files.write(new File(dir,victim).toPath(),new byte[]{1});
            ok(ModelArtifactVerifier.ready(dir),"Repair did not restore ready state");
            new File(dir,"finished").delete();
            ok(!ModelArtifactVerifier.ready(dir),"Stale asset directory without finished accepted");
            System.out.println("PASS: marker, native file completeness, zero-byte rejection and recovery");
        } finally {
            File[] files=dir.listFiles();if(files!=null)for(File file:files)file.delete();
            dir.delete();
        }
    }
}
