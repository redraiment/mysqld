package me.zzp.mysqld;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * @author zhangzepeng
 */
abstract class StringOutputStream extends OutputStream {

    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

    @Override
    public void write(int b) throws IOException {
        buffer.write(b);
    }

    protected abstract void println(String message);

    @Override
    public void flush() throws IOException {
        if (buffer.size() > 0) {
            for (String line : buffer.toString().replaceAll("[\r\n]+$", "").split("[\r\n]+")) {
                println(line);
            }
        }
        buffer.reset();
    }

    @Override
    public void close() throws IOException {
        buffer.close();
    }
}
