package com.apk.claw.android.tool.impl;

import com.apk.claw.android.ClawApplication;
import com.apk.claw.android.R;
import com.apk.claw.android.service.ClawAccessibilityService;
import com.apk.claw.android.tool.BaseTool;
import com.apk.claw.android.tool.ToolParameter;
import com.apk.claw.android.tool.ToolResult;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class GetScreenInfoTool extends BaseTool {

    @Override
    public String getName() {
        return "get_screen_info";
    }

    @Override
    public String getDisplayName() {
        return ClawApplication.Companion.getInstance().getString(R.string.tool_name_get_screen_info);
    }

    @Override
    public String getDescriptionEN() {
        return "Get the current screen's UI hierarchy tree, including all visible elements with their properties (text, id, bounds, clickable, etc.). Use this to understand what is currently displayed on the screen.";
    }

    @Override
    public String getDescriptionCN() {
        return "获取当前屏幕的UI层级树，包括所有可见元素的属性（文本、ID、边界、可点击状态等）。用于了解当前屏幕显示的内容。";
    }

    @Override
    public List<ToolParameter> getParameters() {
        return Collections.emptyList();
    }

    public static final String SYSTEM_DIALOG_BLOCKED = "__SYSTEM_DIALOG_BLOCKED__";

    /**
     * 切换为完整节点树模式（包含所有节点和全部属性，用于调试）。
     * false = 精简模式（默认，省 token）；true = 完整模式。
     */
    public static boolean useFullTree = false;

    @Override
    public ToolResult execute(Map<String, Object> params) {
        ClawAccessibilityService service = ClawAccessibilityService.getInstance();
        if (service == null) {
            return ToolResult.error("Accessibility service is not running");
        }
        String tree = useFullTree ? service.getScreenTreeFull() : service.getScreenTree();
        if (tree == null) {
            return ToolResult.error(SYSTEM_DIALOG_BLOCKED);
        }
        // Hermes 2026-10-14: 把前台应用身份注入观测结果。
        // 背景：原先 dump 只给 className/text/bounds，模型无法区分同款电商 App
        // （咸鱼 vs 美团 vs 淘宝界面文案高度相似），只能靠猜 —— 长任务跑到 20+ 步
        // 就会出现"明明还在咸鱼界面却认成美团"的幻觉。给一个确定的事实即可消除。
        return ToolResult.success(describeForegroundApp(service) + tree);
    }

    /**
     * 读取当前前台窗口所属应用，输出成模型可直接采信的事实。
     * 拿不到时明确告诉模型"未知"，而不是省略字段 —— 省略会让模型退回去猜。
     */
    private static String describeForegroundApp(ClawAccessibilityService service) {
        String pkg = null;
        try {
            android.view.accessibility.AccessibilityNodeInfo root = service.getRootInActiveWindow();
            if (root != null) {
                CharSequence pkgName = root.getPackageName();
                if (pkgName != null && pkgName.length() > 0) {
                    pkg = pkgName.toString();
                }
            }
        } catch (Throwable ignored) {
        }
        if (pkg == null || pkg.isEmpty() || pkg.equals(ClawApplication.Companion.getInstance().getPackageName())) {
            return "[CURRENT_APP] unknown —— 拿不到前台应用包名，不要靠界面文案猜测你在哪个 App\n";
        }
        String label = pkg;
        try {
            android.content.pm.ApplicationInfo info =
                    ClawApplication.Companion.getInstance().getPackageManager().getApplicationInfo(pkg, 0);
            label = ClawApplication.Companion.getInstance().getPackageManager().getApplicationLabel(info).toString();
        } catch (Throwable ignored) {
        }
        return "[CURRENT_APP] package=" + pkg + " name=" + label
                + " —— 这是你现在所在 App 的唯一权威依据，界面文案相似时以它为准\n";
    }
}
