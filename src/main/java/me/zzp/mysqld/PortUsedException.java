package me.zzp.mysqld;

/**
 * 端口已经被其他进程占用
 *
 * @author zhangzepeng
 */
public class PortUsedException extends RuntimeException {

    public PortUsedException(int port) {
        super(String.format("port %d has been used", port));
    }
}
