/*
 * Copyright (C) Photon Vision.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.photonvision.common.networking;

import edu.wpi.first.networktables.NetworkTableInstance;
import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.List;
import org.photonvision.common.configuration.ConfigManager;
import org.photonvision.common.hardware.Platform;
import org.photonvision.common.logging.LogGroup;
import org.photonvision.common.logging.Logger;
import org.photonvision.common.util.ShellExec;

public class NetworkUtils {
    private static final Logger logger = new Logger(NetworkUtils.class, LogGroup.General);

    public enum NMType {
        NMTYPE_ETHERNET("ethernet"),
        NMTYPE_WIFI("wifi"),
        NMTYPE_UNKNOWN("");

        NMType(String id) {
            identifier = id;
        }

        private final String identifier;

        public static NMType typeForString(String s) {
            for (var t : NMType.values()) {
                if (t.identifier.equals(s)) {
                    return t;
                }
            }
            return NMTYPE_UNKNOWN;
        }
    }

    /**
     * Contains data about network devices retrieved from "nmcli device show"
     *
     * @param connName The human-readable name used by "nmcli con"
     * @param devName The underlying device name, used by dhclient
     * @param nmType The NetworkManager device type
     */
    public static record NMDeviceInfo(String connName, String devName, NMType nmType) {
        public NMDeviceInfo(String c, String d, String type) {
            this(c, d, NMType.typeForString(type));
        }
    }

    public static boolean nmcliIsInstalled() {
        var shell = new ShellExec(true, false);
        try {
            shell.executeBashCommand("nmcli --version");

            return shell.getExitCode() == 0;
        } catch (IOException e) {
            logger.error("Could not query nmcli version", e);
            return false;
        }
    }

    private static List<NMDeviceInfo> allInterfaces = null;
    private static long lastReadTimestamp = 0;
    private static long timeout = 5000; // milliseconds
    private static long retry = 500; // milliseconds

    public static synchronized List<NMDeviceInfo> getAllInterfaces() {
        var start = System.currentTimeMillis();
        if (start - lastReadTimestamp < 5000) {
            return allInterfaces;
        }
        var ret = new ArrayList<NMDeviceInfo>();

        if (Platform.isLinux()) {
            // ... (保留原本 Linux 的 nmcli 邏輯) ...
        } else if (System.getProperty("os.name").toLowerCase().contains("mac")) {
            // 【新增部分開始】: 針對 macOS 使用 networksetup 來獲取網卡資訊
            try {
                var shell = new ShellExec(true, false);
                // 執行 macOS 內建的網路硬體查詢指令
                shell.executeBashCommand("networksetup -listallhardwareports", true, true);
                String out = shell.getOutput();

                if (out != null) {
                    String[] lines = out.split("\n");
                    String currentConnName = "";
                    NMType currentType = NMType.NMTYPE_UNKNOWN;

                    for (String line : lines) {
                        line = line.trim();
                        // 抓取連線名稱 (例如 "Wi-Fi" 或 "Apple USB Ethernet Adapter")
                        if (line.startsWith("Hardware Port:")) {
                            currentConnName = line.substring(14).trim();
                            // 簡單判斷是 Wi-Fi 還是有線網路
                            if (currentConnName.toLowerCase().contains("wi-fi")
                                    || currentConnName.toLowerCase().contains("airport")) {
                                currentType = NMType.NMTYPE_WIFI;
                            } else {
                                currentType = NMType.NMTYPE_ETHERNET;
                            }
                        }
                        // 抓取裝置代號 (例如 "en0") 並加入清單
                        else if (line.startsWith("Device:")) {
                            String currentDevName = line.substring(7).trim();
                            if (!currentDevName.isEmpty()
                                    && !currentConnName.isEmpty()
                                    && !currentDevName.equals("lo0")) {
                                ret.add(new NMDeviceInfo(currentConnName, currentDevName, currentType));
                            }
                        }
                    }
                }
            } catch (Exception e) {
                logger.error(
                        "Error occurred when calling networksetup to get network interfaces on macOS!", e);
            }
        }
        if (!ret.equals(allInterfaces)) {
            if (ret.isEmpty()) {
                logger.error("Unable to identify network interfaces!");
            } else {
                logger.debug("Found network interfaces: " + ret);
            }
            allInterfaces = ret;
        }
        lastReadTimestamp = System.currentTimeMillis();

        return ret;
    }

    /**
     * Returns an immutable list of active network interfaces.
     *
     * @return The list.
     */
    public static List<NMDeviceInfo> getAllActiveInterfaces() {
        // Seems like if an interface exists but isn't actually connected, the connection name will be
        // an empty string. Check here and only return connections with non-empty names
        return getAllInterfaces().stream().filter(it -> !it.connName.trim().isEmpty()).toList();
    }

    /**
     * Returns an immutable list of all wired network interfaces.
     *
     * @return The list.
     */
    public static List<NMDeviceInfo> getAllWiredInterfaces() {
        return getAllInterfaces().stream()
                .filter(it -> it.nmType.equals(NMType.NMTYPE_ETHERNET))
                .toList();
    }

    /**
     * Returns an immutable list of all wired and active network interfaces.
     *
     * @return The list.
     */
    public static List<NMDeviceInfo> getAllActiveWiredInterfaces() {
        return getAllWiredInterfaces().stream().filter(it -> !it.connName.isBlank()).toList();
    }

    public static NMDeviceInfo getNMinfoForConnName(String connName) {
        for (NMDeviceInfo info : getAllActiveInterfaces()) {
            if (info.connName.equals(connName)) {
                return info;
            }
        }
        return null;
    }

    public static NMDeviceInfo getNMinfoForDevName(String devName) {
        for (NMDeviceInfo info : getAllActiveInterfaces()) {
            if (info.devName.equals(devName)) {
                return info;
            }
        }
        logger.warn("Could not find a match for network device " + devName);
        return null;
    }

    public static String getActiveConnection(String devName) {
        var shell = new ShellExec(true, true);
        try {
            shell.executeBashCommand(
                    "nmcli -g GENERAL.CONNECTION dev show \"" + devName + "\"", true, false);
            return shell.getOutput().strip();
        } catch (Exception e) {
            logger.error("Exception from nmcli!");
        }
        return "";
    }

    public static boolean connDoesNotExist(String connName) {
        var shell = new ShellExec(true, true);
        try {
            shell.executeBashCommand(
                    "nmcli -g GENERAL.STATE connection show \"" + connName + "\"", true, false);
            return (shell.getExitCode() == 10);
        } catch (Exception e) {
            logger.error("Exception from nmcli!");
        }
        return false;
    }

    public static String getIPAddresses(String iFaceName) {
        if (iFaceName == null || iFaceName.isBlank()) {
            return "";
        }
        List<String> addresses = new ArrayList<String>();
        try {
            var iFace = NetworkInterface.getByName(iFaceName);
            if (iFace != null && iFace.isUp()) {
                for (var addr : iFace.getInterfaceAddresses()) {
                    var addrStr = addr.getAddress().toString();
                    if (addrStr.startsWith("/")) {
                        addrStr = addrStr.substring(1);
                    }
                    addrStr = addrStr + "/" + addr.getNetworkPrefixLength();
                    addresses.add(addrStr);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return String.join(", ", addresses);
    }

    /**
     * Gets a MAC address of a network interface. On devices where networking is managed by
     * PhotonVision, this will return the MAC address of the configured interface. Otherwise, this
     * will attempt to search for the network interface in current use and use that interface's MAC
     * address, and if that fails, it will return a MAC address from the first network interface with
     * a MAC address, as sorted by {@link NetworkInterface#networkInterfaces()}.
     *
     * @return The MAC address.
     */
    public static String getMacAddress() {
        var config = ConfigManager.getInstance().getConfig().getNetworkConfig();
        try {
            // Not managed? See if we're connected to a network.
            if (config.networkManagerIface == null || config.networkManagerIface.isBlank()) {
                // Use NT client IP address to find the interface in use
                if (!config.runNTServer) {
                    var conn = NetworkTableInstance.getDefault().getConnections();
                    if (conn.length > 0 && !conn[0].remote_ip.equals("127.0.0.1")) {
                        var remoteAddr = InetAddress.getByName(conn[0].remote_ip);

                        // 【修改部分開始】: 利用 Dummy UDP Socket 找出作業系統用來路由到 roboRIO 的本地網卡 IP
                        try (java.net.DatagramSocket socket = new java.net.DatagramSocket()) {
                            // 這裡不需要真的送出封包，connect() 只是讓 OS 查閱 Routing Table
                            socket.connect(remoteAddr, 5810); // 5810 是 NT4 預設 Port
                            var localAddress = socket.getLocalAddress();
                            var currentIface = NetworkInterface.getByInetAddress(localAddress);

                            if (currentIface != null && currentIface.getHardwareAddress() != null) {
                                return formatMacAddress(currentIface.getHardwareAddress());
                            }
                        } catch (Exception e) {
                            logger.debug("Could not resolve local interface for NT connection" + e);
                        }
                    }
                } else {
                    logger.debug("Running NT server, skipping MAC address retrieval based on NT connection");
                }
            }
        } catch (Exception e) {
            logger.error("Error while trying to get MAC address from NT connection", e);
        }

        return "00-00-00-00-00-00"; // default if we can't find a MAC address
    }

    private static String formatMacAddress(byte[] mac) {
        StringBuilder sb = new StringBuilder(17);
        sb.append(String.format("%02X", mac[0]));
        for (int i = 1; i < mac.length; i++) {
            sb.append(String.format("-%02X", mac[i]));
        }
        return sb.toString();
    }
}
