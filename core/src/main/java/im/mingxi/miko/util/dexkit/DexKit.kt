package im.mingxi.miko.util.dexkit

import com.tencent.mmkv.MMKV


object DexKit {

    /*
     * 从缓存中请求一个方法
     */
    fun requireMethodFromCache(cacheName: String): DexMethodDescriptor {
        val mmkv = MMKV.mmkvWithID("global_cache")
        if (mmkv.containsKey(cacheName)) {
            return DexMethodDescriptor(mmkv.decodeString(cacheName)!!)
        } else {
            throw RuntimeException("Do not find method in cache")
        }
    }

}