# Form Popup API Routing - Final Solution

## 问题描述

在User Portal的任务详情页面点击FORM_POPUP类型的action按钮时，出现500错误。错误信息显示：
```
No static resource processes/fu-data/DigitalLendingProcessV2
```

## 根本原因

**Spring Boot的ResourceHttpRequestHandler拦截了`/processes/`路径下的所有请求**，将它们当作静态资源请求处理，即使这些路径已经在Controller中定义。

### 关键发现

- ✅ Admin Center的`/function-units/{id}/content`端点工作正常
- ❌ User Portal的**任何**在`/processes/`路径下的endpoint都会被ResourceHttpRequestHandler拦截
- **问题不在于路径名称**（如`contents`、`content`、`data`、`fu-data`等），而在于**`/processes/`前缀本身**

## 失败的尝试历史

在找到最终解决方案之前，尝试了以下方法（**全部失败**）：

| 尝试 | 路径 | 结果 | 原因 |
|------|------|------|------|
| 1 | `/processes/function-units/{id}/contents?contentType=FORM` | ❌ 被拦截 | 在`/processes/`下 |
| 2 | `/processes/function-unit-contents/{id}?contentType=FORM` | ❌ 被拦截 | 在`/processes/`下 |
| 3 | `/processes/fu-content/{id}/type/{type}` | ❌ 被拦截 | 在`/processes/`下 |
| 4 | `/processes/formcontent/{id}` | ❌ 被拦截 | 在`/processes/`下 |
| 5 | `/processes/content/form` | ❌ 被拦截 | 在`/processes/`下 |
| 6 | `POST /processes/formcontent` | ❌ 被拦截 | 在`/processes/`下 |
| 7 | `/processes/fu-data/{id}?contentType=FORM` | ❌ 被拦截 | 在`/processes/`下 |

**结论**：所有这些尝试都失败了，因为它们都在`/processes/`路径下。ResourceHttpRequestHandler会拦截这个路径前缀下的所有请求。

## 最终解决方案 ✅

**将endpoint移出`/processes/`路径**，创建独立的Controller。

### 实现步骤

#### 1. 后端修改

创建新的`ApiDataController`，路径为`/fu-contents`（**不在`/processes/`下**）：

**文件**: `backend/user-portal/src/main/java/com/portal/controller/ApiDataController.java`

```java
@Slf4j
@RestController
@RequestMapping("/fu-contents")
@RequiredArgsConstructor
@Tag(name = "功能单元内容API", description = "功能单元内容API端点，不在/processes路径下以避免ResourceHttpRequestHandler拦截")
public class ApiDataController {

    private final ProcessComponent processComponent;

    @GetMapping("/{functionUnitId}")
    @Operation(summary = "获取功能单元特定类型的内容", description = "获取功能单元的特定类型内容（如表单、流程等），用于表单弹窗等场景")
    public ApiResponse<List<Map<String, Object>>> getFunctionUnitContents(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @PathVariable String functionUnitId,
            @RequestParam String contentType) {
        log.info("ApiDataController: Getting function unit contents for: {}, contentType: {}", functionUnitId, contentType);
        List<Map<String, Object>> contents = processComponent.getFunctionUnitContents(functionUnitId, contentType);
        return ApiResponse.success(contents);
    }
}
```

**完整URL**: `/api/portal/fu-contents/{functionUnitId}?contentType=FORM`

#### 2. 前端修改

更新API调用路径：

**文件**: `frontend/user-portal/src/api/process.ts`

```typescript
// 获取功能单元特定类型的内容
getFunctionUnitContents(functionUnitId: string, contentType: string) {
  return request.get<Array<{
    id: string
    contentType: string
    contentName: string
    contentData: string
    sourceId?: string
  }>>(`/fu-contents/${functionUnitId}`, {
    params: { contentType }
  })
}
```

#### 3. 后端业务逻辑（无需修改）

`ProcessComponent.getFunctionUnitContents()`方法保持不变，它会：
1. 调用Admin Center的`/function-units/{id}/content`端点
2. 根据`contentType`参数过滤结果
3. 返回过滤后的内容列表

### 部署步骤

```bash
# 1. 编译后端
mvn clean package -DskipTests -pl backend/user-portal -am

# 2. 部署后端到Docker
docker cp backend/user-portal/target/user-portal-1.0.0-SNAPSHOT.jar platform-user-portal-dev:/app/user-portal.jar
docker restart platform-user-portal-dev

# 3. 编译前端
cd frontend/user-portal
npx vite build

# 4. 部署前端到Docker
docker cp frontend/user-portal/dist/. platform-user-portal-frontend-dev:/usr/share/nginx/html/
docker restart platform-user-portal-frontend-dev
```

## 技术分析

### 为什么`/processes/`路径被拦截？

Spring Boot的`ResourceHttpRequestHandler`在处理请求时有更高的优先级。虽然我们已经：
- 设置了`spring.web.resources.add-mappings: false`
- 清空了`WebMvcConfig.addResourceHandlers()`方法
- 配置了`PathPatternParser`

但是`ResourceHttpRequestHandler`仍然会拦截某些路径模式，特别是`/processes/`这样的路径前缀。

### 为什么Admin Center的endpoint工作正常？

Admin Center的endpoint路径是`/function-units/{id}/content`，**不在`/processes/`路径下**，因此不会被拦截。

### 解决方案的关键

**将endpoint移出`/processes/`路径**是唯一有效的解决方案。这样可以完全避开ResourceHttpRequestHandler的拦截逻辑。

### 路径对比

| 服务 | 路径 | 状态 | 原因 |
|------|------|------|------|
| Admin Center | `/function-units/{id}/content` | ✅ 工作 | 不在`/processes/`下 |
| User Portal (旧) | `/processes/fu-data/{id}` | ❌ 被拦截 | 在`/processes/`下 |
| User Portal (新) | `/fu-contents/{id}` | ✅ 工作 | 不在`/processes/`下 |

## 验证

修复后，点击FORM_POPUP类型的action按钮应该能够：

1. ✅ 成功调用`/api/portal/fu-contents/{functionUnitId}?contentType=FORM`
2. ✅ 返回表单内容列表
3. ✅ 在弹窗中正确显示表单
4. ✅ 浏览器控制台无500错误

### 测试步骤

1. 访问User Portal: http://localhost:3001
2. 进入"我的待办"页面
3. 点击任意任务查看详情
4. 点击"查看表单"按钮（FORM_POPUP action）
5. 确认表单弹窗正常显示

## 相关文件

### 修改的文件

- `backend/user-portal/src/main/java/com/portal/controller/ApiDataController.java` - 新的Controller（路径：`/fu-contents`）
- `frontend/user-portal/src/api/process.ts` - 前端API调用

### 参考文件

- `backend/user-portal/src/main/java/com/portal/component/ProcessComponent.java` - 业务逻辑（无需修改）
- `backend/admin-center/src/main/java/com/admin/controller/FunctionUnitController.java` - Admin Center的工作endpoint（参考）

## 经验教训

1. **Spring的ResourceHttpRequestHandler优先级很高**
   - 即使禁用了静态资源映射，某些路径模式仍然会被拦截
   - 配置无法完全控制其行为

2. **路径设计很重要**
   - 避免使用可能被框架特殊处理的路径前缀（如`/processes/`）
   - 参考已经工作的endpoint的路径结构

3. **最简单的解决方案往往是最好的**
   - 不要试图通过复杂的配置来改变Spring的内部行为
   - 直接更改路径结构更简单、更可靠

4. **深度思考问题的本质**
   - 问题不在于路径名称（`contents`、`data`等）
   - 问题在于路径前缀（`/processes/`）
   - 通过多次失败的尝试，最终找到了真正的原因

## 状态

✅ **已解决** - 2026-02-08

表单弹窗功能现在可以正常工作。

---

**部署环境**: Development (platform-user-portal-dev)  
**解决日期**: 2026-02-08  
**解决方案**: 将endpoint从`/processes/fu-data/{id}`移动到`/fu-contents/{id}`
