package love.kill.methodcache.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;

/**
 * 序列化工具类
 *
 * @author likepan
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
}
