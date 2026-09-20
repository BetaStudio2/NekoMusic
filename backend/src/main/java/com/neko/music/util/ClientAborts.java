package com.neko.music.util;

import org.eclipse.jetty.io.EofException;

/**
 * 客户端主动断开连接的判定。
 *
 * <p>原先 {@code MusicCoverHandler} 与 {@code UserAvatarHandler} 各复制了一份
 * {@code isClientAbort(Throwable)}，收敛到这里。用于把「客户端提前断开」
 * 与真正的服务端错误区分开，避免无意义地记录错误日志。
 */
public final class ClientAborts {

    private ClientAborts() {
    }

    /** 沿异常因果链判断是否由客户端断开（EofException / broken pipe）引起。 */
    public static boolean isClientAbort(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof EofException) {
                return true;
            }
            String message = current.getMessage();
            if (message != null && message.toLowerCase().contains("broken pipe")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
