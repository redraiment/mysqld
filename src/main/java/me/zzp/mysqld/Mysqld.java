package me.zzp.mysqld;

import com.mysql.management.MysqldResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.Socket;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;

/**
 * 嵌入式 MySQL 服务器。
 *
 * 用于单元测试等临时需要 MySQL 服务器的场景。
 *
 * 所有 start() 方法可在 @BeforeClass 中被安全地调用；
 * 所有 close() 方法可在 @AfterClass 中被安全地调用。
 *
 * 本类会自动根据当前的上下方自动选择是否重用已生成的实例，还是启动新的 MySQL 实例。
 *
 * @author zhangzepeng
 */
public final class Mysqld implements AutoCloseable {

    /**
     * ROOT的系统属性名
     */
    public final static String PROPERTY_ROOT = "zzp.mysqld.root";

    /**
     * 存放 MySQL 二进制文件的默认路径为当前路径下的 mysqld 文件夹
     */
    public final static String DEFAULT_ROOT = "mysqld";

    /**
     * PORT的系统属性名
     */
    public final static String PROPERTY_PORT = "zzp.mysqld.port";

    /**
     * 端口默认为 3306，与 MySQL 默认端口保持一致
     */
    public final static int DEFAULT_PORT = 3306;

    /**
     * INIT的系统属性名
     */
    public final static String PROPERTY_INIT = "zzp.mysqld.init";

    /**
     * 线程名称默认为 mysqld-thread
     */
    public final static String THREAD_NAME = "mysqld-thread";

    /**
     * 默认日志对象，使用 Slf4j api
     */
    private final static Logger log = LoggerFactory.getLogger(Mysqld.class);

    /**
     * 共享实例的端口集合
     */
    private final static Set<Integer> sharedPorts = new HashSet<Integer>();

    /**
     * 端口与实例的映射
     */
    private final static Map<Integer, Mysqld> pool = new HashMap<Integer, Mysqld>();

    /**
     * 默认实例启动方法。
     *
     * 如果全局唯一的共享实例已经启动，则返回该共享实例；否则按照以下规则启动新实例。
     *
     * 实例路径：
     * <ol>
     *     <li>如果系统属性 <code>zzp.mysqld.root</code> 存在，则使用其值；</li>
     *     <li>否则，使用默认值 <code>DEFAULT_ROOT</code>。</li>
     * </ol>
     *
     * 实例端口：
     * <ol>
     *     <li>如果系统属性 <code>zzp.mysqld.port</code> 存在，则使用其值；</li>
     *     <li>否则，使用默认值 <code>DEFAULT_PORT</code>。</li>
     * </ol>
     *
     * @return MySQL 服务器实例
     */
    public static Mysqld start() {
        int port;
        if (!sharedPorts.isEmpty()) {
            port = sharedPorts.iterator().next();
        } else {
            port = Integer.parseInt(System.getProperty(PROPERTY_PORT, Integer.toString(DEFAULT_PORT)));
        }
        return start(port);
    }

    /**
     * 根据指定的端口号启动 MySQL 服务器实例。
     *
     * 如果端口对应的 MySQL 实例已经启动，则直接返回该实例；否则按照以下规则启动新实例。
     *
     * 实例路径：
     * <ol>
     *     <li>如果系统属性 <code>zzp.mysqld.root</code> 存在，则使用其值；</li>
     *     <li>否则，使用默认值 <code>DEFAULT_ROOT</code>。</li>
     * </ol>
     *
     * @param port 端口号
     * @return MySQL 服务器实例
     */
    public static Mysqld start(int port) {
        return start(System.getProperty(PROPERTY_ROOT, DEFAULT_ROOT), port);
    }

    /**
     * 根据指定的路径和端口号启动 MySQL 服务器实例。
     *
     * 如果路径以"/"开头，则直接使用该路径；否则加上系统属性 user.dir 作为前缀。
     * 如果路径对应的文件夹不存在，则使用 File::mkdirs 来创建，创建失败回抛出 PermissionDeniedException。
     *
     * @param root 路径
     * @param port 端口
     * @return MySQL 服务器实例
     */
    public synchronized static Mysqld start(String root, int port) {
        log.info("connect mysql @ {}", port);
        Mysqld mysql = pool.get(port);
        if (mysql != null) {
            return mysql;
        } else {
            mysql = new Mysqld(root, port, false);
            mysql.run();
            return mysql;
        }
    }

    private final int port;
    private final MysqldResource mysql;
    private final Map<String, String> options;

    Mysqld(String root, int port, boolean share) {
        this.port = port;

        String path = root.startsWith("/")? root: System.getProperty("user.dir").concat("/").concat(root);
        File dir = new File(path);
        if (!dir.mkdirs() && !dir.isDirectory()) {
            throw new PermissionDeniedException(root);
        }

        PrintStream out = new PrintStream(new Slf4jInfoOutputStream(log), true);
        PrintStream err = new PrintStream(new Slf4jDebugOutputStream(log), true);
        mysql = new MysqldResource(dir, null, null, out, err);
        mysql.deployFiles();

        options = new HashMap<String, String>();
        options.put("port", Integer.toString(port));

        pool.put(port, this);
        if (share) {
            sharedPorts.add(port);
        }

        log.info("embedded mysql server @ path={} port={}", path, port);
    }

    /**
     * 等待当前实例到达可连接状态。
     */
    private void waitForReady() {
        while (mysql != null && mysql.isRunning() && !mysql.isReadyForConnections()) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                log.warn("mysql is not ready for connections");
                break;
            }
        }
    }

    /**
     * 检查指定的端口是否可用。
     *
     * @param port 端口
     * @return 可用返回true；否则返回false
     */
    private static boolean available(int port) {
        try {
            new Socket("localhost", port).close();
            throw new PortUsedException(port);
        } catch (Throwable e) {
            return true;
        }
    }

    /**
     * 启动 MySQL 实例，并等待其到达可连接状态。
     */
    void run() {
        if (mysql != null && !mysql.isRunning() && available(port)) {
            mysql.start(THREAD_NAME, options);
            waitForReady();
            log.info("embedded mysql server started");
            initialize();
        }
    }

    /**
     * 初始化。
     *
     * 如果有执行 PROPERTY_INIT，则执行相应的初始化脚本
     */
    void initialize() {
        String spec = System.getProperty(PROPERTY_INIT);
        if (spec == null) {
            return;
        }

        StringBuilder sql = new StringBuilder();
        final String endl = System.getProperty("line.separator");
        try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(spec);
             Scanner sin = new Scanner(in)) {
            while (sin.hasNextLine()) {
                sql.append(sin.nextLine())
                   .append(endl);
            }
        } catch (Throwable e) {
            log.warn("read sql file failed", e);
            return;
        }

        String url = "jdbc:mysql://localhost:%d/?useSSL=false&allowMultiQueries=true&characterEncoding=UTF-8";
        try (Connection connection = DriverManager.getConnection(String.format(url, port), "root", "");
             Statement statement = connection.createStatement()) {
            statement.execute(sql.toString());
        } catch (Throwable e) {
            log.warn("initialize mysql server failed", e);
        }
    }

    /**
     * 真正关闭 MySQL 实例的方法。
     */
    void shutdown() {
        if (mysql != null && mysql.isRunning()) {
            mysql.shutdown();
            log.info("embedded mysql server stopped");
        }
    }

    /**
     * 安全的关闭实例方法。
     * 如果当前 MySQL 实例为共享实例，则不会真正关闭。
     */
    @Override
    public void close() {
        log.info("disconnect mysql @ {}", port);
        if (!sharedPorts.contains(port)) {
            shutdown();
            pool.remove(port);
        }
    }
}
