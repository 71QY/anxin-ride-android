# 长辈端收藏功能增强 - 后端API修改说明

## 📋 功能概述

本次更新为长辈端收藏功能增加以下5个核心能力：

1. **快速指定目的地**：长辈点击收藏地址一键发送给亲友，亲友收到后直接帮叫车
2. **查看详细信息**：长辈可以查看收藏地点的地址、电话、简介等纯信息（不触发叫车）
3. **行程到达确认**：到达收藏地点后，长辈一键点击"已到达"，亲友立即收到通知
4. **语音播报**：长辈点击收藏地址时，App自动语音播报地点信息
5. **行程凭证记录**：系统自动记录每次去收藏地点的行程时间、位置，形成出行记录供子女查看

---

## 🗄️ 一、数据库表结构变更

### 1.1 user_favorites 表新增字段

```sql
-- 添加联系电话字段
ALTER TABLE user_favorites 
ADD COLUMN phone VARCHAR(20) DEFAULT NULL COMMENT '联系电话';

-- 添加简介说明字段
ALTER TABLE user_favorites 
ADD COLUMN description TEXT DEFAULT NULL COMMENT '地点简介说明';

-- 添加最后访问时间字段（用于行程凭证）
ALTER TABLE user_favorites 
ADD COLUMN last_visited_at DATETIME DEFAULT NULL COMMENT '最后访问时间（行程凭证）';

-- 为新字段添加索引（优化查询性能）
CREATE INDEX idx_last_visited ON user_favorites(last_visited_at);
```

### 1.2 新增访问历史表（行程凭证）

```sql
-- 创建收藏地点访问历史表
CREATE TABLE IF NOT EXISTS favorite_visit_history (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    user_id BIGINT NOT NULL COMMENT '用户ID（长辈）',
    favorite_id BIGINT NOT NULL COMMENT '收藏地点ID',
    visit_time DATETIME NOT NULL COMMENT '访问时间',
    latitude DOUBLE NOT NULL COMMENT '访问时纬度',
    longitude DOUBLE NOT NULL COMMENT '访问时经度',
    order_id BIGINT DEFAULT NULL COMMENT '关联订单ID（如果有）',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
    
    INDEX idx_user_favorite (user_id, favorite_id),
    INDEX idx_visit_time (visit_time),
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    FOREIGN KEY (favorite_id) REFERENCES user_favorites(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='收藏地点访问历史表（行程凭证）';
```

---

## 🔌 二、新增API接口

### 2.1 分享收藏地点给亲友

**接口路径**: `POST /api/favorites/{favoriteId}/share-to-guardian`

**请求参数**:
```json
{
  "guardianUserId": 123456  // 亲友的用户ID
}
```

**响应格式**:
```json
{
  "code": 200,
  "message": "分享成功",
  "data": {
    "favoriteId": 789,
    "guardianUserId": 123456,
    "sharedAt": "2026-04-18T10:30:00"
  }
}
```

**业务逻辑**:
1. 验证长辈是否有权限访问该收藏地点
2. 验证亲友是否存在且与长辈有绑定关系
3. 通过WebSocket向亲友推送目的地信息
4. 记录分享日志（可选）

**WebSocket推送消息** (发送给亲友):
```json
{
  "type": "FAVORITE_DESTINATION_SHARED",
  "elderId": 654321,
  "elderName": "张三",
  "favoriteId": 789,
  "destinationName": "市人民医院",
  "destinationAddress": "北京市朝阳区XX路123号",
  "latitude": 39.9042,
  "longitude": 116.4074,
  "phone": "010-12345678",
  "description": "三甲医院，心血管科专家门诊",
  "timestamp": 1713412200000
}
```

---

### 2.2 确认到达目的地

**接口路径**: `POST /api/favorites/{favoriteId}/confirm-arrival`

**请求参数**:
```json
{
  "orderId": 999  // 可选：关联的订单ID
}
```

**响应格式**:
```json
{
  "code": 200,
  "message": "确认到达成功",
  "data": {
    "favoriteId": 789,
    "confirmedAt": "2026-04-18T11:00:00",
    "notifiedGuardians": [123456, 789012]  // 已通知的亲友列表
  }
}
```

**业务逻辑**:
1. 验证长辈是否有权限访问该收藏地点
2. 更新 `user_favorites.last_visited_at` 字段为当前时间
3. 在 `favorite_visit_history` 表中插入访问记录
4. 通过WebSocket向所有绑定的亲友推送到达通知
5. 如果有关联订单，更新订单状态

**WebSocket推送消息** (发送给亲友):
```json
{
  "type": "ARRIVAL_CONFIRMED",
  "elderId": 654321,
  "elderName": "张三",
  "favoriteId": 789,
  "destinationName": "市人民医院",
  "orderId": 999,  // 可选
  "arrivedAt": "2026-04-18T11:00:00",
  "latitude": 39.9042,
  "longitude": 116.4074,
  "timestamp": 1713415200000
}
```

---

### 2.3 获取收藏地点访问历史

**接口路径**: `GET /api/favorites/{favoriteId}/visit-history`

**请求参数**:
- `page`: 页码（默认1）
- `pageSize`: 每页数量（默认20）
- `startDate`: 开始日期（可选，格式：yyyy-MM-dd）
- `endDate`: 结束日期（可选，格式：yyyy-MM-dd）

**响应格式**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "total": 15,
    "page": 1,
    "pageSize": 20,
    "items": [
      {
        "id": 1,
        "favoriteId": 789,
        "destinationName": "市人民医院",
        "visitTime": "2026-04-18T11:00:00",
        "latitude": 39.9042,
        "longitude": 116.4074,
        "orderId": 999,
        "createdAt": "2026-04-18T11:00:05"
      },
      {
        "id": 2,
        "favoriteId": 789,
        "destinationName": "市人民医院",
        "visitTime": "2026-04-15T09:30:00",
        "latitude": 39.9042,
        "longitude": 116.4074,
        "orderId": null,
        "createdAt": "2026-04-15T09:30:05"
      }
    ]
  }
}
```

**业务逻辑**:
1. 验证用户是否有权限查看该收藏地点的历史记录
2. 按时间倒序返回访问记录
3. 支持日期范围筛选

---

### 2.4 获取亲友可查看的行程凭证

**接口路径**: `GET /api/guard/elder-visit-history`

**请求参数**:
- `elderId`: 长辈用户ID
- `page`: 页码（默认1）
- `pageSize`: 每页数量（默认20）
- `startDate`: 开始日期（可选）
- `endDate`: 结束日期（可选）

**响应格式**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "total": 30,
    "page": 1,
    "pageSize": 20,
    "items": [
      {
        "id": 1,
        "elderId": 654321,
        "elderName": "张三",
        "favoriteId": 789,
        "destinationName": "市人民医院",
        "destinationAddress": "北京市朝阳区XX路123号",
        "visitTime": "2026-04-18T11:00:00",
        "latitude": 39.9042,
        "longitude": 116.4074,
        "orderId": 999,
        "createdAt": "2026-04-18T11:00:05"
      }
    ]
  }
}
```

**业务逻辑**:
1. 验证请求者是否为该长辈的绑定亲友
2. 返回该长辈的所有收藏地点访问记录
3. 支持按日期范围筛选

---

## 🔄 三、WebSocket消息类型扩展

在现有的 `GuardPushMessage` 基础上新增两种消息类型：

### 3.1 FAVORITE_DESTINATION_SHARED（分享目的地）

```java
// Java后端示例
public class FavoriteDestinationSharedMessage {
    private String type = "FAVORITE_DESTINATION_SHARED";
    private Long elderId;
    private String elderName;
    private Long favoriteId;
    private String destinationName;
    private String destinationAddress;
    private Double latitude;
    private Double longitude;
    private String phone;
    private String description;
    private Long timestamp;
    
    // getters and setters...
}
```

### 3.2 ARRIVAL_CONFIRMED（到达确认）

```java
// Java后端示例
public class ArrivalConfirmedMessage {
    private String type = "ARRIVAL_CONFIRMED";
    private Long elderId;
    private String elderName;
    private Long favoriteId;
    private String destinationName;
    private Long orderId;  // 可选
    private String arrivedAt;
    private Double latitude;
    private Double longitude;
    private Long timestamp;
    
    // getters and setters...
}
```

---

## 📝 四、Service层业务逻辑实现要点

### 4.1 分享收藏地点服务

```java
@Service
public class FavoriteShareService {
    
    @Autowired
    private WebSocketService webSocketService;
    
    @Autowired
    private FavoriteRepository favoriteRepository;
    
    @Autowired
    private GuardianRelationshipRepository guardianRepository;
    
    /**
     * 分享收藏地点给亲友
     */
    @Transactional
    public void shareFavoriteToGuardian(Long userId, Long favoriteId, Long guardianUserId) {
        // 1. 验证收藏地点存在且属于该用户
        FavoriteLocation favorite = favoriteRepository.findByIdAndUserId(favoriteId, userId)
            .orElseThrow(() -> new BusinessException("收藏地点不存在"));
        
        // 2. 验证亲友关系
        GuardianRelationship relationship = guardianRepository.findByElderIdAndGuardianId(userId, guardianUserId)
            .orElseThrow(() -> new BusinessException("未找到与该亲友的绑定关系"));
        
        // 3. 构建WebSocket消息
        FavoriteDestinationSharedMessage message = new FavoriteDestinationSharedMessage();
        message.setElderId(userId);
        message.setElderName(getUserName(userId));
        message.setFavoriteId(favoriteId);
        message.setDestinationName(favorite.getName());
        message.setDestinationAddress(favorite.getAddress());
        message.setLatitude(favorite.getLatitude());
        message.setLongitude(favorite.getLongitude());
        message.setPhone(favorite.getPhone());
        message.setDescription(favorite.getDescription());
        message.setTimestamp(System.currentTimeMillis());
        
        // 4. 推送给亲友
        webSocketService.sendMessageToUser(guardianUserId, message);
        
        // 5. 记录分享日志（可选）
        log.info("用户 {} 分享收藏地点 {} 给亲友 {}", userId, favoriteId, guardianUserId);
    }
}
```

### 4.2 确认到达服务

```java
@Service
public class ArrivalConfirmationService {
    
    @Autowired
    private WebSocketService webSocketService;
    
    @Autowired
    private FavoriteRepository favoriteRepository;
    
    @Autowired
    private VisitHistoryRepository visitHistoryRepository;
    
    @Autowired
    private GuardianRelationshipRepository guardianRepository;
    
    /**
     * 确认到达目的地
     */
    @Transactional
    public ConfirmArrivalResult confirmArrival(Long userId, Long favoriteId, Long orderId) {
        // 1. 验证收藏地点存在且属于该用户
        FavoriteLocation favorite = favoriteRepository.findByIdAndUserId(favoriteId, userId)
            .orElseThrow(() -> new BusinessException("收藏地点不存在"));
        
        // 2. 更新最后访问时间
        favorite.setLastVisitedAt(new Date());
        favoriteRepository.save(favorite);
        
        // 3. 记录访问历史（行程凭证）
        VisitHistory history = new VisitHistory();
        history.setUserId(userId);
        history.setFavoriteId(favoriteId);
        history.setVisitTime(new Date());
        history.setLatitude(favorite.getLatitude());
        history.setLongitude(favorite.getLongitude());
        history.setOrderId(orderId);
        visitHistoryRepository.save(history);
        
        // 4. 获取所有绑定的亲友
        List<GuardianRelationship> guardians = guardianRepository.findByElderId(userId);
        
        // 5. 构建WebSocket消息
        ArrivalConfirmedMessage message = new ArrivalConfirmedMessage();
        message.setElderId(userId);
        message.setElderName(getUserName(userId));
        message.setFavoriteId(favoriteId);
        message.setDestinationName(favorite.getName());
        message.setOrderId(orderId);
        message.setArrivedAt(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
        message.setLatitude(favorite.getLatitude());
        message.setLongitude(favorite.getLongitude());
        message.setTimestamp(System.currentTimeMillis());
        
        // 6. 推送给所有亲友
        List<Long> notifiedGuardians = new ArrayList<>();
        for (GuardianRelationship guardian : guardians) {
            webSocketService.sendMessageToUser(guardian.getGuardianId(), message);
            notifiedGuardians.add(guardian.getGuardianId());
        }
        
        // 7. 返回结果
        return new ConfirmArrivalResult(favoriteId, new Date(), notifiedGuardians);
    }
}
```

---

## 🎯 五、Controller层接口实现

```java
@RestController
@RequestMapping("/api/favorites")
public class FavoriteController {
    
    @Autowired
    private FavoriteShareService favoriteShareService;
    
    @Autowired
    private ArrivalConfirmationService arrivalConfirmationService;
    
    @Autowired
    private VisitHistoryService visitHistoryService;
    
    /**
     * 分享收藏地点给亲友
     */
    @PostMapping("/{favoriteId}/share-to-guardian")
    public ApiResponse<Map<String, Object>> shareToGuardian(
            @PathVariable Long favoriteId,
            @RequestBody ShareFavoriteRequest request,
            HttpServletRequest httpRequest) {
        
        Long userId = getUserIdFromToken(httpRequest);
        favoriteShareService.shareFavoriteToGuardian(userId, favoriteId, request.getGuardianUserId());
        
        Map<String, Object> data = new HashMap<>();
        data.put("favoriteId", favoriteId);
        data.put("guardianUserId", request.getGuardianUserId());
        data.put("sharedAt", new Date());
        
        return ApiResponse.success(data);
    }
    
    /**
     * 确认到达目的地
     */
    @PostMapping("/{favoriteId}/confirm-arrival")
    public ApiResponse<Map<String, Object>> confirmArrival(
            @PathVariable Long favoriteId,
            @RequestBody(required = false) ConfirmArrivalRequest request,
            HttpServletRequest httpRequest) {
        
        Long userId = getUserIdFromToken(httpRequest);
        Long orderId = request != null ? request.getOrderId() : null;
        
        ConfirmArrivalResult result = arrivalConfirmationService.confirmArrival(userId, favoriteId, orderId);
        
        Map<String, Object> data = new HashMap<>();
        data.put("favoriteId", result.getFavoriteId());
        data.put("confirmedAt", result.getConfirmedAt());
        data.put("notifiedGuardians", result.getNotifiedGuardians());
        
        return ApiResponse.success(data);
    }
    
    /**
     * 获取收藏地点访问历史
     */
    @GetMapping("/{favoriteId}/visit-history")
    public ApiResponse<PageResult<VisitHistoryVO>> getVisitHistory(
            @PathVariable Long favoriteId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            HttpServletRequest httpRequest) {
        
        Long userId = getUserIdFromToken(httpRequest);
        
        PageResult<VisitHistoryVO> result = visitHistoryService.getVisitHistory(
            userId, favoriteId, page, pageSize, startDate, endDate
        );
        
        return ApiResponse.success(result);
    }
}
```

---

## 🔐 六、权限控制

### 6.1 分享收藏地点
- **权限要求**: 必须是收藏地点的拥有者
- **验证逻辑**: 检查 `user_favorites.user_id == current_user_id`

### 6.2 确认到达
- **权限要求**: 必须是收藏地点的拥有者
- **验证逻辑**: 检查 `user_favorites.user_id == current_user_id`

### 6.3 查看访问历史
- **长辈视角**: 只能查看自己的访问历史
- **亲友视角**: 只能查看已绑定长辈的访问历史（需验证 `guardian_relationships` 表）

---

## 📊 七、数据字典

### 7.1 user_favorites 表字段说明

| 字段名 | 类型 | 说明 | 备注 |
|--------|------|------|------|
| id | BIGINT | 主键ID | 自增 |
| user_id | BIGINT | 用户ID | 外键关联users表 |
| name | VARCHAR(100) | 地点名称 | 如"家"、"市人民医院" |
| address | VARCHAR(255) | 详细地址 | |
| latitude | DOUBLE | 纬度 | |
| longitude | DOUBLE | 经度 | |
| type | VARCHAR(20) | 类型 | HOME/COMPANY/HOSPITAL/CUSTOM |
| phone | VARCHAR(20) | 联系电话 | ⭐ 新增 |
| description | TEXT | 简介说明 | ⭐ 新增 |
| updated_at | DATETIME | 更新时间 | |
| last_visited_at | DATETIME | 最后访问时间 | ⭐ 新增（行程凭证） |
| created_at | DATETIME | 创建时间 | |

### 7.2 favorite_visit_history 表字段说明

| 字段名 | 类型 | 说明 | 备注 |
|--------|------|------|------|
| id | BIGINT | 主键ID | 自增 |
| user_id | BIGINT | 用户ID（长辈） | 外键关联users表 |
| favorite_id | BIGINT | 收藏地点ID | 外键关联user_favorites表 |
| visit_time | DATETIME | 访问时间 | |
| latitude | DOUBLE | 访问时纬度 | |
| longitude | DOUBLE | 访问时经度 | |
| order_id | BIGINT | 关联订单ID | 可选，如果是代叫车行程 |
| created_at | DATETIME | 记录创建时间 | |

---

## 🧪 八、测试用例

### 8.1 分享收藏地点测试

```bash
# 测试分享收藏地点
curl -X POST http://localhost:8080/api/favorites/789/share-to-guardian \
  -H "Authorization: Bearer <elder_token>" \
  -H "Content-Type: application/json" \
  -d '{"guardianUserId": 123456}'

# 预期响应
{
  "code": 200,
  "message": "分享成功",
  "data": {
    "favoriteId": 789,
    "guardianUserId": 123456,
    "sharedAt": "2026-04-18T10:30:00"
  }
}
```

### 8.2 确认到达测试

```bash
# 测试确认到达
curl -X POST http://localhost:8080/api/favorites/789/confirm-arrival \
  -H "Authorization: Bearer <elder_token>" \
  -H "Content-Type: application/json" \
  -d '{"orderId": 999}'

# 预期响应
{
  "code": 200,
  "message": "确认到达成功",
  "data": {
    "favoriteId": 789,
    "confirmedAt": "2026-04-18T11:00:00",
    "notifiedGuardians": [123456, 789012]
  }
}
```

### 8.3 查询访问历史测试

```bash
# 测试查询访问历史
curl -X GET "http://localhost:8080/api/favorites/789/visit-history?page=1&pageSize=20" \
  -H "Authorization: Bearer <elder_token>"

# 预期响应
{
  "code": 200,
  "message": "success",
  "data": {
    "total": 15,
    "page": 1,
    "pageSize": 20,
    "items": [...]
  }
}
```

---

## 🚀 九、部署注意事项

### 9.1 数据库迁移
1. 执行SQL脚本添加新字段和新表
2. 备份现有数据
3. 在生产环境执行前先在测试环境验证

### 9.2 WebSocket兼容性
- 确保WebSocket服务端支持新的消息类型
- 测试消息推送的实时性
- 验证多亲友同时在线时的广播机制

### 9.3 性能优化
- 为 `favorite_visit_history` 表添加合适的索引
- 考虑对访问历史记录进行分页查询优化
- 定期清理过期的访问历史（如保留最近1年）

---

## 📞 十、联系方式

如有技术问题，请联系前端开发团队。

**更新日期**: 2026-04-18  
**版本**: v1.0
