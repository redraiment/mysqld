package me.zzp.mysqld;

/**
 * @author zhangzepeng
 */
public class PermissionDeniedException extends RuntimeException {

    public PermissionDeniedException(String path) {
        super("no permission to create folder ".concat(path));
    }
}
