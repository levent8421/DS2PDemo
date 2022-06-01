package com.levent8421.ds2pdemo;

/**
 * Create By Levent8421
 * Create Time: 2022/4/11 11:27
 * Class Name: LibraryLoader
 * Author: Levent8421
 * Description:
 * NativeLibLoader
 *
 * @author Levent8421
 */
public class Ds2pLibraryLoader {
    public static final String LIB_PATH = "serial_port";

    public void loadLib() {
        try {
            System.loadLibrary(LIB_PATH);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }
}
