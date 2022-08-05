**一、什么是MethodCache**

    MethodCache是一个基于Spring Boot的、非侵入式的方法缓存开源组件。对方法返回数据进行缓存，减少数据库访问次数，以提高接口的访问速度。

**二、快速开始**

    1、引入jar包
        <dependency>
            <groupId>love.kill</groupId>
            <artifactId>methodcache-spring-boot-starter</artifactId>
            <version>2.0.0</version>
        </dependency>

    2、在 application.yml 启用
        methodcache:
          enable: true

    3、在方法上加上注解 @CacheData

**三、demo**

    内存：https://github.com/Lycop/demo-for-methodcache-with-memory.git
    Redis：https://github.com/Lycop/demo-for-methodcache-with-redis.git


**四、缓存供设置的属性**

    1、expiration，缓存时间(默认3000毫秒)。此属性表示缓存有效时间，在有效时间内，数据将会返回缓存中的数据；否则发起一次请求。
    2、refresh，刷新(默认true)。方法被请求后，都会发起一次异步的请求，用于更新缓存。
    3、behindExpiration，宽限期。此属性表示缓存到期后，会在指定时间内随机失效。可以用来避免缓存大范围同时失效引起的服务器雪崩。
    4、capitalExpiration，基础时间。此属性表示在当前指定类型(秒/分钟/小时/日/月/年)内，缓存一直有效。
    5、id，缓存标识。标识一个缓存注解，可用于清除该注解的缓存。
    6、nullable，允许 null 返回值(默认true)。为 true 则表示当方法返回了 null(包含异常导致) 时，仍然缓存。
    7、remark，备注。


**五、查看/清除缓存**

    /methodcache/cache，支持方法：GET(查看缓存)、DELETE(清除缓存)
    /methodcache/cache/all，支持方法：DELETE(清除所有缓存)

**六、配置文件说明**

    # 方法缓存
    methodcache:
    # 缓存应用名称，若为空则取 ${spring.application.name}
    #  name: demo-for-methodcache
      # 启用缓存。true：开启，false：关闭，默认 false
      enable: true
      # 输出日志(info级别)。true：开启，false：关闭，默认 false
      enable-log: true
      # 缓存方式。(M)emory：内存，(R)edis：redis，默认 M
      cache-type: R
      # 端点开启，默认关闭
      enable-endpoint: true

**最后**

    使用过程中，如果有问题或者建议，欢迎联系我(i@kill.love)。也欢迎大家一起加入并完善本项目。 ^ ^