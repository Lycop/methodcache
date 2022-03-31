package love.kill.methodcache;

import java.util.Date;

/**
 * 服务接口
 *
 * @author Lycop
 */
public class Test {
	public static void main(String[] args) {
		System.out.println(">>> " + new Date().after(new Date(1648033512775L+ 30000L)));
	}
}
