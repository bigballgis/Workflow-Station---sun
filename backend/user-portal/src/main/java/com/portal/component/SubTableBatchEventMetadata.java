package com.portal.component;

/** Non-executable metadata emitted by the designer, never evaluated as JavaScript. */
final class SubTableBatchEventMetadata {
    // Migration: older designer saves serialized form-create's injected adapter instead of __origin.
    // Keep this exact allowlist while published catalogs/instance snapshots can contain that artifact.
    private static final String LEGACY_ADAPTER = "$FNX:\nfor(var s=a.getInjectData(n,r),u=arguments.length,c=new Array(u),d=0;d<u;d++)"
            + "c[d]=arguments[d];return s.args=[].concat(c),c.unshift(s),o.apply(this,c)";

    private SubTableBatchEventMetadata() { }

    static boolean isEmpty(Object value) {
        if (value == null) return true;
        if (!(value instanceof String text)) return false;
        String source = text.strip();
        return source.isEmpty() || "$FNX:".equals(source) || LEGACY_ADAPTER.equals(source);
    }
}
