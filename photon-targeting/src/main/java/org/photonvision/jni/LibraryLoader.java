package org.photonvision.jni;

import edu.wpi.first.apriltag.jni.AprilTagJNI;
import edu.wpi.first.cscore.CameraServerJNI;
import edu.wpi.first.cscore.OpenCvLoader;
import edu.wpi.first.hal.JNIWrapper;
import edu.wpi.first.math.jni.WPIMathJNI;
import edu.wpi.first.net.WPINetJNI;
import edu.wpi.first.networktables.NetworkTablesJNI;
import edu.wpi.first.util.CombinedRuntimeLoader;
import edu.wpi.first.util.WPIUtilJNI;
import java.io.IOException;
import org.opencv.core.Core;
import org.photonvision.common.hardware.Platform; // 導入你改好的 Platform

public class LibraryLoader {
    private static boolean hasWpiLoaded = false;
    private static boolean hasTargetingLoaded = false;

    public static boolean loadWpiLibraries() {
        if (hasWpiLoaded) return true;

        NetworkTablesJNI.Helper.setExtractOnStaticLoad(false);
        WPIUtilJNI.Helper.setExtractOnStaticLoad(false);
        CameraServerJNI.Helper.setExtractOnStaticLoad(false);
        OpenCvLoader.Helper.setExtractOnStaticLoad(false);
        JNIWrapper.Helper.setExtractOnStaticLoad(false);
        WPINetJNI.Helper.setExtractOnStaticLoad(false);
        WPIMathJNI.Helper.setExtractOnStaticLoad(false);
        AprilTagJNI.Helper.setExtractOnStaticLoad(false);

        try {
            CombinedRuntimeLoader.loadLibraries(LibraryLoader.class, "wpiutiljni");

            if (Platform.isWindows()) {
                WPIUtilJNI.checkMsvcRuntime();
            }

            CombinedRuntimeLoader.loadLibraries(
                    LibraryLoader.class,
                    "wpimathjni",
                    "ntcorejni",
                    "wpinetjni",
                    "wpiHaljni",
                    "cscorejni",
                    "apriltagjni");

            CombinedRuntimeLoader.loadLibraries(LibraryLoader.class, Core.NATIVE_LIBRARY_NAME);

            hasWpiLoaded = true;
        } catch (IOException e) {
            System.err.println("Cannot load WPILib from " + Platform.getPlatformName());
            e.printStackTrace();
            hasWpiLoaded = false;
        }

        return hasWpiLoaded;
    }

    public static boolean loadTargeting() {
        if (hasTargetingLoaded) return true;
        try {
            CombinedRuntimeLoader.loadLibraries(LibraryLoader.class, "photontargetingJNI");
            hasTargetingLoaded = true;
        } catch (IOException e) {
            System.err.println("Cannot load PhotonTargeting JNI");
            e.printStackTrace();
            hasTargetingLoaded = false;
        }
        return hasTargetingLoaded;
    }
}
