package com.developer.security;

/**
 * 开发者工作区「开发组（团队）」相关常量。
 *
 * <p>{@code PUBLIC_GROUP_ID}：内置「Public」虚拟组。分配到该组的功能单元对所有能进入
 * 工作区的用户始终可访问，并可在顶部团队切换器中单独查看，用于承载历史/共享功能单元。
 * 用户不会成为该组成员，因此它不会出现在「我的团队」选择列表中。</p>
 */
public final class DevGroupConstants {

    private DevGroupConstants() {
    }

    /** 内置 Public 组 id（种子/迁移脚本中固定写入）。 */
    public static final String PUBLIC_GROUP_ID = "vg-dev-public";
}
