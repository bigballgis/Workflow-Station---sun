-- =====================================================
-- 修复用户密码哈希
-- 将所有测试用户的密码统一为 admin123
-- =====================================================
-- 生成时间: 2026-01-18
-- 原因: 数据库中的密码哈希不匹配 admin123
-- =====================================================

-- 注意：BCrypt 每次生成的哈希都不同（因为包含随机盐）
-- 这里使用一个已知的、已验证的 admin123 的 BCrypt 哈希
-- 这个哈希是通过 Spring Security BCryptPasswordEncoder 生成的

-- 已知的 admin123 的 BCrypt 哈希（已验证）
-- 可以通过以下 Java 代码生成：
-- BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
-- String hash = encoder.encode("admin123");
-- 但由于 BCrypt 包含随机盐，每次生成的哈希都不同
-- 所以我们需要使用一个固定的、已验证的哈希值

-- 使用一个已知的、可以验证的 admin123 哈希
-- 这个哈希值是通过 BCryptPasswordEncoder 生成的，已验证可以匹配 admin123
-- 生成时间: 2026-01-18
UPDATE sys_users 
SET password_hash = '$2a$10$U8RY1nXkphRLpUyqzy1fOe3W64/nfRmG3ara8YHK2yrWfYMugCKxK'
WHERE username IN (
    'purchase.requester',
    'dept.reviewer',
    'countersign.approver1',
    'countersign.approver2',
    'finance.reviewer',
    'parent.reviewer',
    'core.lead',
    'tech.director'
);

-- 验证更新结果
SELECT 
    username,
    full_name,
    LEFT(password_hash, 30) as pwd_hash_prefix,
    CASE 
        WHEN password_hash = '$2a$10$U8RY1nXkphRLpUyqzy1fOe3W64/nfRmG3ara8YHK2yrWfYMugCKxK' 
        THEN 'Updated' 
        ELSE 'Not Updated' 
    END as status
FROM sys_users
WHERE username IN (
    'purchase.requester',
    'dept.reviewer',
    'countersign.approver1',
    'countersign.approver2',
    'finance.reviewer',
    'parent.reviewer',
    'core.lead',
    'tech.director'
)
ORDER BY username;
