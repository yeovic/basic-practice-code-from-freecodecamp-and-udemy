package org.velvetatelier;

public final class RepairMaskTest {
    private static void require(boolean pass,String message){
        if(!pass)throw new AssertionError(message);
    }
    private static int gray(int pixel){return pixel&255;}
    public static void main(String[] args){
        for(int side:new int[]{64,128,512}){
            int[] image=RepairMask.create(side,80,12);
            require(image.length==side*side,"Wrong mask dimensions");
            int center=gray(image[(side/2)*side+(side/2)]);
            int corner=gray(image[0]);
            int outside=gray(image[(side/2)*side]);
            require(center==255,"Center is not editable");
            require(corner==0 && outside==0,"Outer boundary must be protected");
            int previous=255;
            for(int x=side/2;x<side;x++){
                int px=image[(side/2)*side+x];
                int value=gray(px);
                require(((px>>>24)&255)==255,"Unexpected transparency in native mask");
                require(((px>>>16)&255)==value && ((px>>>8)&255)==value,
                    "Non-grayscale mask");
                require(value<=previous,"Feather must fall smoothly to black");
                previous=value;
            }
        }
        int[] tight=RepairMask.create(256,55,12);
        int[] wide=RepairMask.create(256,92,12);
        require(gray(wide[128*256+220])>gray(tight[128*256+220]),
                "Larger mask coverage did not extend editable area");
        for(int invalid:new int[]{0,31,2049}){
            boolean rejected=false;
            try{RepairMask.create(invalid,80,10);}catch(IllegalArgumentException ex){rejected=true;}
            require(rejected,"Invalid side accepted");
        }
        boolean invalid=false;
        try{RepairMask.create(512,20,10);}catch(IllegalArgumentException ex){invalid=true;}
        require(invalid,"Invalid mask coverage accepted");
        System.out.println("PASS: soft inpaint geometry, grayscale, protected edges, coverage and invalid input");
    }
}