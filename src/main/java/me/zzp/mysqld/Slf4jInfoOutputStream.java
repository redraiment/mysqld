package me.zzp.mysqld;

import org.slf4j.Logger;

/**
 * @author zhangzepeng
 */
class Slf4jInfoOutputStream extends StringOutputStream {

    private final Logger logger;

    public Slf4jInfoOutputStream(Logger logger) {
        this.logger = logger;
    }

    @Override
    protected void println(String message) {
        logger.info(message);
    }
}
