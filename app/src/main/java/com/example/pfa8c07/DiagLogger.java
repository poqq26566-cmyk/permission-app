package com.example.pfa8c07;

import android.content.Context;

/**
 * 正式打包空实现：调试版回流逻辑已剥离；埋点调用在此为 no-op。
 * 下次「运行」调试时 IDE 会自动恢复完整 DiagLogger。
 */
public final class DiagLogger {
    private DiagLogger() { }
    public static void init(Context ctx) { }
    public static void d(String tag, String msg) { }
    public static void i(String tag, String msg) { }
    public static void w(String tag, String msg) { }
    public static void e(String tag, String msg) { }
}