package love.kill.methodcache.util;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.util.Collections;

/**
 * redis锁工具类
 *
 * @author likepan
 * @version 1.0.0
 * @since 1.0
 */
public class RedisLockUtil {

	private final static String REDIS_RESULT_OK = "OK";
	private final static String REDIS_RESULT_REENTRANT = "REENTRANT";
	private final static  String REDIS_LOCK_PREFIX = "REDIS_LOCK_"; // redis锁前缀

	private static RedisSerializer<?> argsSerializer = new StringRedisSerializer();
	private static RedisSerializer<String> resultSerializer = new StringRedisSerializer();

	// 重入锁脚本
	private final static String reentrantLockScript =   "if (redis.call('exists', KEYS[1]) == 0) then " + // 不存key，在则直接抢占
														"redis.call('hset', KEYS[1], ARGV[1], 1); " + // 加锁标识设置为 1
														"redis.call('pexpire', KEYS[1], ARGV[2]); " + // 设置有效期(毫秒)
														"return 'OK'; " +
														"end; " +
														"if (redis.call('hexists', KEYS[1], ARGV[1]) == 1) then " + // 重入
														"redis.call('hincrby', KEYS[1], ARGV[1], 1); " + // 自增
														"redis.call('pexpire', KEYS[1], ARGV[2]); " + // 更新有效期(毫秒)
														"return 'REENTRANT'; " +
														"else " +
														"return 'FALSE'; "+
														"end; ";



	// 释放锁脚本
	private final static String releaseLockScript =    "if (redis.call('exists', KEYS[1]) == 0) then " +
														"return 'NON-EXISTENT'; " +
														"end;" +
														"if (redis.call('hexists', KEYS[1], ARGV[1]) == 0) then " +
														"return 'NOT-BELONG';" +
														"end; " +
														// 如果就是当前线程占有分布式锁，那么将重入次数减1
														"local counter = redis.call('hincrby', KEYS[1], ARGV[1], -1); " +
														"if (counter > 0) then " + // 重入次数减1后的值如果大于0表示分布式锁有重入过，还不能删除
														"return 'REENTRANT'; " +
														"else " +
														// 重入次数减1后的值如果为0，表示分布式锁只获取过1次，那么删除这个KEY，并发布解锁消息
														"redis.call('del', KEYS[1]); " +
														"return 'OK'; "+
														"end; ";


	/**
	 * 加锁(可重入锁)
	 * @param redisTemplate redisTemplate
	 * @param key key
	 * @param value value
	 * @return 加锁结果。成功，true；失败，false
	 */
	@SuppressWarnings("unchecked")
	static boolean lock(RedisTemplate redisTemplate, String key, String value, long expireTime) {

		DefaultRedisScript<String> defaultRedisScript = new DefaultRedisScript();
		defaultRedisScript.setScriptText(reentrantLockScript);
		defaultRedisScript.setResultType(String.class);
		String result = (String) redisTemplate.execute(defaultRedisScript,argsSerializer, resultSerializer, Collections.singletonList(REDIS_LOCK_PREFIX + key), value, String.valueOf(expireTime));
		return REDIS_RESULT_OK.equals(result) || REDIS_RESULT_REENTRANT.equals(result);
	}

	/**
	 * 解锁(可重入锁)
	 * @param redisTemplate redisTemplate
	 * @param key key
	 * @param value value
	 * @return 解锁结果。成功，true；失败，false
	 */
	@SuppressWarnings("unchecked")
	static boolean unlock(RedisTemplate redisTemplate, String key, String value) {
		DefaultRedisScript<String> defaultRedisScript = new DefaultRedisScript();
		defaultRedisScript.setScriptText(releaseLockScript);
		defaultRedisScript.setResultType(String.class);
		String result = (String) redisTemplate.execute(defaultRedisScript, argsSerializer, resultSerializer, Collections.singletonList(REDIS_LOCK_PREFIX + key), value);
		return REDIS_RESULT_OK.equals(result) || REDIS_RESULT_REENTRANT.equals(result);

	}
}
