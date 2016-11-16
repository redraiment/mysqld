package me.zzp.mysqld;

import org.junit.runner.Description;
import org.junit.runner.Result;
import org.junit.runner.notification.RunListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 为 JUnit 启动全局的 Mysql 实例。
 *
 * 结合 maven-surefire-plugin，执行 mvn test 时会自动执行注册的 RunListener。
 * <ul>
 *     <li>在执行所有测试用例前启动唯一的 MySQL 实例。</li>
 *     <li>在所有测试用例执行结束后关闭 MySQL 实例。</li>
 *     <li>每个测试用例都能安全地指定 Mysqld.start() 与 Mysqld.close()，会自动重用唯一的实例。</li>
 * </ul>
 *
 * @author zhangzepeng
 */
public class MysqldRunListener extends RunListener {

    private final static Logger log = LoggerFactory.getLogger(MysqldRunListener.class);

    private Mysqld mysql;

    /**
     * 在执行所有测试用例钱启动唯一的 MySQL 实例。
     *
     * @param description 描述
     * @throws Exception 任何 MySQL 启动异常
     */
    @Override
    public void testRunStarted(Description description) throws Exception {
        log.debug("on junit run started: start embedded mysql server");

        String root = System.getProperty(Mysqld.PROPERTY_ROOT, Mysqld.DEFAULT_ROOT);
        String port = System.getProperty(Mysqld.PROPERTY_PORT, Integer.toString(Mysqld.DEFAULT_PORT));
        mysql = new Mysqld(root, Integer.parseInt(port), true);
        mysql.run();
    }

    /**
     * 在所有测试用例执行结束后关闭 MySQL 实例。
     *
     * @param result 结果
     * @throws Exception 任何 MySQL 关闭异常
     */
    @Override
    public void testRunFinished(Result result) throws Exception {
        log.debug("on junit run finished: stop embedded mysql server");
        if (mysql != null) {
            mysql.shutdown();
        }
    }
}
