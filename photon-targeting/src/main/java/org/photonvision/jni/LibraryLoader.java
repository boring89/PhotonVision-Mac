package org.photonvision.jni;

import org.photonvision.common.hardware.Platform; // 導入你改好的 Platform
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

public class LibraryLoader {
    private static boolean hasWpiLoaded = false;
    private static boolean hasTargetingLoaded = false;

    public static boolean loadWpiLibraries() {
        if (hasWpiLoaded) return true;

        // 停止靜態載入，交由我們手動控制
        NetworkTablesJNI.Helper.setExtractOnStaticLoad(false);
        WPIUtilJNI.Helper.setExtractOnStaticLoad(false);
        CameraServerJNI.Helper.setExtractOnStaticLoad(false);
        OpenCvLoader.Helper.setExtractOnStaticLoad(false);
        JNIWrapper.Helper.setExtractOnStaticLoad(false);
        WPINetJNI.Helper.setExtractOnStaticLoad(false);
        WPIMathJNI.Helper.setExtractOnStaticLoad(false);
        AprilTagJNI.Helper.setExtractOnStaticLoad(false);

        try {
            // 1. 載入基礎 WPI 工具庫
            CombinedRuntimeLoader.loadLibraries(LibraryLoader.class, "wpiutiljni");

            // 2. 只有在 Windows 上才檢查 MSVC Runtime
            if (Platform.isWindows()) {
                WPIUtilJNI.checkMsvcRuntime();
            }

            // 3. 載入其餘所有 WPILib 相關 JNI
            // 在 macOS 下，這會去搜尋資源檔夾中的 .dylib
            CombinedRuntimeLoader.loadLibraries(
                    LibraryLoader.class,
                    "wpimathjni",
                    "ntcorejni",
                    "wpinetjni",
                    "wpiHaljni",
                    "cscorejni",
                    "apriltagjni");

            // 4. 載入 OpenCV
            CombinedRuntimeLoader.loadLibraries(LibraryLoader.class, Core.NATIVE_LIBRARY_NAME);
            
            hasWpiLoaded = true;
        } catch (IOException e) {
            System.err.println("無法在 " + Platform.getPlatformName() + " 上載入 WPI 函式庫");
            e.printStackTrace();
            hasWpiLoaded = false;
        }

        return hasWpiLoaded;
    }

    public static boolean loadTargeting() {
        if (hasTargetingLoaded) return true;
        try {
            // 這會載入 libphotontargetingJNI.dylib
            CombinedRuntimeLoader.loadLibraries(LibraryLoader.class, "photontargetingJNI");
            hasTargetingLoaded = true;
        } catch (IOException e) {
            System.err.println("無法載入 PhotonTargeting JNI");
            e.printStackTrace();
            hasTargetingLoaded = false;
        }
        return hasTargetingLoaded;
    }
}