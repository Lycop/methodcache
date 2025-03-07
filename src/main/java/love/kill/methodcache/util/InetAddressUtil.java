package love.kill.methodcache.util;

import java.net.*;
import java.util.Enumeration;

/**
 * 服务接口
 *
 * @author Lycop
 */
public class InetAddressUtil {
	public static InetAddress getFirstNonLoopbackAddress() {
		InetAddress result = null;
		try {
			Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
			while (networkInterfaces.hasMoreElements()) {
				NetworkInterface networkInterface = networkInterfaces.nextElement();
				if (networkInterface.isUp()) {
					// 网卡下的IP列表
					Enumeration<InetAddress> inetAddrs = networkInterface.getInetAddresses();
					while (inetAddrs.hasMoreElements()) {
						InetAddress address = inetAddrs.nextElement();
						if (address instanceof Inet4Address && !address.isLoopbackAddress()) {
							result = address;
						}
					}
					if (result != null) {
						break;
					}
				}
			}
		} catch (SocketException e) {
			e.printStackTrace();
			return getLocalHost();
		}

		return result;
	}


	private static InetAddress getLocalHost(){
		try {
			return InetAddress.getLocalHost();
		} catch (UnknownHostException e) {
			e.printStackTrace();
			return null;
		}
	}

	/**
	 * 本机IP包含指定IP
	 *
	 * @param ip 	指定的IP
	 * @return  	本机包含指定IP
	 */
	public static boolean containsIP(String ip) {
		try {
			Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
			while (networkInterfaces.hasMoreElements()) {
				NetworkInterface networkInterface = networkInterfaces.nextElement();
				if (networkInterface.isUp()) {
					// 网卡下的IP列表
					Enumeration<InetAddress> inetAddrs = networkInterface.getInetAddresses();
					while (inetAddrs.hasMoreElements()) {
						InetAddress address = inetAddrs.nextElement();
						if (ip.equals(address.getHostAddress())) {
							return true;
						}
					}
				}
			}
		} catch (SocketException e) {
			e.printStackTrace();
		}

		return false;
	}
}
