package me.zzp.mysqld;

import org.slf4j.Logger;

/**
 * @author zhangzepeng
 */
class Slf4jDebugOutputStream extends StringOutputStream {

    private final Logger logger;

    public Slf4jDebugOutputStream(Logger logger) {
        this.logger = logger;
    }

    @Override
    protected void println(String message) {
        logger.debug(message);
    }
}
