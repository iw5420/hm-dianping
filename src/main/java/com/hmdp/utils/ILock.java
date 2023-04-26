package com.hmdp.utils;

public interface ILock {

    /**
     * 嘗試獲取鎖
     * @param timeoutSec 鎖持有的超時時間, 過期後自動釋放
     * @return true 代表獲取鎖成功; false代表獲取鎖失敗
     */
    boolean tryLock(long timeoutSec);

    /**
     * 釋放鎖
     */
    void unlock();
}
