package org.tigase.muc;


public final class MucVersion {

   
    private MucVersion() {
    }

    public static String getVersion() {
        return MucVersion.class.getPackage().getImplementationVersion();
    }
}
