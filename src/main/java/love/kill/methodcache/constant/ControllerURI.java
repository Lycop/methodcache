package love.kill.methodcache.constant;


/**
 * URI 常量
 *
 * @author Lycop
 */
public class ControllerURI {

	/**
	 * 方法缓存
	 */
	public static final String METHOD_CACHE = "/method/cache";

	/************************************************* 缓存统计 *************************************************/

	/**
	 * 缓存统计
	 */
	public static final String CACHE_STATISTICS = ControllerURI.METHOD_CACHE + "/statistics";

	/************************************************* 版本信息 *************************************************/

	/**
	 * 版本信息
	 */
	public static final String CACHE_VERSION = ControllerURI.METHOD_CACHE + "/version";

}

