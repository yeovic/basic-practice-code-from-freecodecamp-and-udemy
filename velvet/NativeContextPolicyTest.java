package org.velvetatelier;

public final class NativeContextPolicyTest {
    private static void check(boolean value,String message){
        if(!value)throw new AssertionError(message);
    }
    public static void main(String[] args){
        check(!NativeContextPolicy.mayRunSinglePass(77,130,40),
            "Legacy 77-context backend falsely declared native long prompt support");
        check(NativeContextPolicy.mayRunSinglePass(0,190,180),
            "Dynamic three-chunk backend rejected");
        check(NativeContextPolicy.mayRunSinglePass(154,151,150),
            "Two-chunk native backend incorrectly rejected");
        check(!NativeContextPolicy.mayRunSinglePass(154,155,150),
            "Fixed native limit exceeded");
        check(!NativeContextPolicy.mayRunSinglePass(0,232,100),
            "Dynamic native prompt exceeded safe phone memory cap");
        check(!NativeContextPolicy.mayRunSinglePass(0,60,310),
            "Long negative prompt bypassed safe memory limit");
        check(!NativeContextPolicy.mayRunSinglePass(-1,100,50),
            "Unknown native context accepted");
        check(!NativeContextPolicy.mayRunSinglePass(0,0,40),
            "Empty prompt accepted");
        System.out.println("PASS: v2.8 fallback, native 154/231 context, and mobile memory cap");
    }
}