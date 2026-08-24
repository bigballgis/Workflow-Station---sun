package com.admin.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 自动化组件(Activepieces piece)目录条目。
 *
 * <p>只读视图:数据源为 AP 的 {@code piece_metadata} 表(白名单本身)。
 * {@code pieceType=OFFICIAL} 为镜像烘焙投放的官方/自研件;{@code CUSTOM} 为
 * 未来经在线安装面投放的件(P2)。</p>
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class AutomationPieceSummary {

    private String id;
    private String name;
    private String displayName;
    private String description;
    private String logoUrl;
    private String version;
    private String pieceType;
    private String packageType;
    /** 运行时包是否以 archive 形式存于库中(ARCHIVE 件);烘焙件为 false */
    private boolean hasArchive;
    /** 是否已停用(在 AP platform.filteredPieceNames 黑名单里,设计器目录不可见) */
    private boolean disabled;
    private String platformId;
    private int actionCount;
    private int triggerCount;
    private List<String> actionNames;
    private List<String> triggerNames;
    private List<String> categories;
    private List<String> authors;
    private String minimumSupportedRelease;
    private String maximumSupportedRelease;
    private int projectUsage;
    private OffsetDateTime created;
    private OffsetDateTime updated;
    /** All versions of this package (list-query only; used by the version switcher). */
    private List<AutomationPieceSummary> versions;
}
