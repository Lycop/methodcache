package love.kill.methodcache.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.Base64;

/**
 * 序列化工具类
 *
 * @author Lycop
 * @version 1.0.0
 * @since 1.0
 */
public class SerializeUtil {

	private static Logger logger = LoggerFactory.getLogger(SerializeUtil.class);

	/**
	 * 序列化
	 *
	 * @param object 待序列化对象
	 * @return 序列化结果
	 **/
	public static byte[] serizlize(Object object) {
		try {
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			new ObjectOutputStream(bos).writeObject(object);
			return bos.toByteArray();
		} catch (IOException e) {
			e.printStackTrace();
			logger.error("序列化时发生异常：" + e.getMessage());
			return null;
		}
	}

	/**
	 * 反序列化
	 *
	 * @param bytes 序列化结果
	 * @return 序列化对象
	 **/
	public static Object deserialize(byte[] bytes) {
		try {
			ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
			return new ObjectInputStream(bis).readObject();
		} catch (IOException | ClassNotFoundException e) {
			e.printStackTrace();
			logger.error("反序列化时发生异常：" + e.getMessage());
			return null;
		}
	}

	/**
	 * 字节数组转字符串
	 *
	 * @param bytes 字节数组
	 * @return 字符串
	 */
	public static String byteArray2String(byte[] bytes) {
		return Base64.getEncoder().encodeToString(bytes);
	}

	/**
	 * 字符串转字节数组
	 *
	 * @param str 字符串
	 * @return 字节数组
	 */
	public static byte[] string2ByteArray(String str) {
		return Base64.getDecoder().decode(str);
	}
}
