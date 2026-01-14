# 修复图标库 500 错误

## 问题描述

访问 `localhost:3002/icons` 图标库页面时，API 调用 `GET http://localhost:3002/api/v1/icons?page=0&size=100` 返回 500 内部服务器错误。

## 错误原因

后端日志显示错误：
```
Caused by: java.lang.IllegalArgumentException: No enum constant com.developer.enums.IconCategory.payment
```

**根本原因**：数据库中有一个图标的 `category` 字段值是 `payment`（小写），但是 `IconCategory` 枚举中定义的是 `PAYMENT`（大写）。Hibernate 在尝试将数据库中的字符串值转换为枚举时失败。

## 修复方案

更新数据库中的枚举值，将小写的 `payment` 改为大写的 `PAYMENT`：

```sql
UPDATE dw_icons SET category = 'PAYMENT' WHERE category = 'payment';
```

## 验证

修复后，API 调用成功返回数据：

```json
{
  "success": true,
  "data": {
    "content": [
      {
        "id": 2,
        "name": "accessibility-svgrepo-com",
        "category": "GENERAL",
        ...
      },
      {
        "id": 3,
        "name": "chef-man-cap-svgrepo-com",
        "category": "GENERAL",
        ...
      },
      {
        "id": 1,
        "name": "credit-card",
        "category": "PAYMENT",
        ...
      }
    ],
    "totalElements": 3
  }
}
```

## IconCategory 枚举定义

```java
public enum IconCategory {
    APPROVAL,    // 审批流程
    CREDIT,      // 信贷业务
    ACCOUNT,     // 账户服务
    PAYMENT,     // 支付结算
    CUSTOMER,    // 客户管理
    COMPLIANCE,  // 合规风控
    OPERATION,   // 运营管理
    GENERAL      // 通用图标
}
```

## 注意事项

1. **枚举值大小写敏感**：数据库中的枚举值必须与 Java 枚举定义完全匹配（大小写一致）
2. **数据一致性**：如果将来添加新的枚举值，需要确保数据库中的现有数据也使用正确的枚举值
3. **数据迁移**：建议在 Flyway 迁移脚本中统一处理枚举值的规范化

## 下一步

现在可以正常访问图标库页面：
- 前端地址：http://localhost:3002/icons
- API 地址：http://localhost:8083/api/v1/icons

图标库应该能正常显示 3 个图标：
1. credit-card (PAYMENT)
2. accessibility-svgrepo-com (GENERAL)
3. chef-man-cap-svgrepo-com (GENERAL)
