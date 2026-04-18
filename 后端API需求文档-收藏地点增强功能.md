# 收藏地点增强功能 - 后端API需求文档

## 📋 功能概述

本次更新为收藏地点功能增加了以下能力:
1. **给亲友"快速指定目的地"** - 长辈点击收藏地址一键发送给亲友,亲友收到后直接帮他叫车
2. **长辈查看常用地点信息** - 显示地址、电话、简介,纯查看模式
3. **行程到达后"确认已到目的地"** - 长辈一键确认,亲友立刻收到通知
4. **语音播报地点信息** - TTS播报(前端已实现)
5. **作为"行程凭证"** - 自动记录出行记录,方便子女查看老人去过哪里

---

## 🔧 数据库表结构修改

### 1. user_favorites 表新增字段

```sql
ALTER TABLE user_favorites 
ADD COLUMN phone VARCHAR(20) DEFAULT NULL COMMENT '联系电话',
ADD COLUMN description TEXT DEFAULT NULL COMMENT '地点简介说明';
```

**字段说明**:
- `phone`: 可选,最大20字符,用于存储医院、超市等地点的联系电话
- `description`: 可选,TEXT类型,用于存储地点简介(如"三甲医院,有急诊科")

---

### 2. 新增表: favorite_shares (收藏分享记录)

```sql
CREATE TABLE favorite_shares (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    favorite_id BIGINT NOT NULL COMMENT '收藏地点ID',
    elder_user_id BIGINT NOT NULL COMMENT '长辈用户ID(分享者)',
    guardian_user_id BIGINT NOT NULL COMMENT '亲友用户ID(接收者)',
    status TINYINT DEFAULT 0 COMMENT '状态: 0-待处理, 1-已使用, 2-已过期',
    shared_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '分享时间',
    used_at TIMESTAMP NULL COMMENT '使用时间',
    INDEX idx_guardian (guardian_user_id),
    INDEX idx_elder (elder_user_id),
    INDEX idx_favorite (favorite_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='收藏地点分享记录表';
```

**业务规则**:
- 每次分享插入一条记录
- 亲友使用后更新`used_at`和`status=1`
- 24小时后自动过期(`status=2`)

---

### 3. 新增表: travel_records (出行记录/行程凭证)

```sql
CREATE TABLE travel_records (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL COMMENT '用户ID(长辈)',
    order_id BIGINT NOT NULL COMMENT '订单ID',
    favorite_id BIGINT DEFAULT NULL COMMENT '关联的收藏地点ID',
    destination_name VARCHAR(100) NOT NULL COMMENT '目的地名称',
    destination_address VARCHAR(500) NOT NULL COMMENT '目的地地址',
    destination_lat DOUBLE NOT NULL COMMENT '目的地纬度',
    destination_lng DOUBLE NOT NULL COMMENT '目的地经度',
    start_time TIMESTAMP NOT NULL COMMENT '出发时间',
    arrive_time TIMESTAMP NULL COMMENT '到达时间',
    duration_minutes INT DEFAULT NULL COMMENT '行程时长(分钟)',
    distance_meters INT DEFAULT NULL COMMENT '行程距离(米)',
    status TINYINT DEFAULT 0 COMMENT '状态: 0-进行中, 1-已完成, 2-已取消',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_user (user_id),
    INDEX idx_order (order_id),
    INDEX idx_favorite (favorite_id),
    INDEX idx_date (start_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='出行记录表(行程凭证)';
```

**自动创建逻辑**:
- 订单完成时(status=6)自动插入此表
- 从订单表获取:`order_id`, `user_id`, `destination_*`, `start_time`, `arrive_time`
- 计算:`duration_minutes`, `distance_meters`
- 如果订单关联了收藏地点,填充`favorite_id`

---

## 📡 新增API接口

### 接口1: 分享收藏地点给亲友

**接口路径**: `POST /api/favorites/share-to-guardian`

**请求头**:
```
Authorization: Bearer <token>
Content-Type: application/x-www-form-urlencoded
```

**请求参数**(Form表单):
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| favoriteId | Long | 是 | 收藏地点ID |
| guardianUserId | Long | 是 | 亲友用户ID |

**请求示例**:
```bash
curl -X POST http://192.168.189.80:8080/api/favorites/share-to-guardian \
  -H "Authorization: Bearer ELDER_TOKEN" \
  -d "favoriteId=123&guardianUserId=456"
```

**响应格式**:
```json
{
  "code": 200,
  "message": "success",
  "data": null
}
```

**业务逻辑**:
1. 验证当前用户(token解析)是否有权限访问`favoriteId`
2. 验证`guardianUserId`是否是当前用户的亲友(查询guardians表)
3. 在`favorite_shares`表插入分享记录
4. **通过WebSocket推送消息给亲友**(见下方WebSocket消息格式)

**错误码**:
| code | message | 说明 |
|------|---------|------|
| 400 | 参数错误 | favoriteId或guardianUserId为空 |
| 403 | 无权分享 | 收藏不属于当前用户或非亲友关系 |
| 404 | 收藏地点不存在 | favoriteId无效 |
| 500 | 服务器内部错误 | 数据库异常等 |

---

### 接口2: 确认到达目的地

**接口路径**: `POST /api/favorites/confirm-arrival-simple`

**请求头**:
```
Authorization: Bearer <token>
Content-Type: application/x-www-form-urlencoded
```

**请求参数**(Form表单):
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| favoriteId | Long | 是 | 收藏地点ID |
| orderId | Long | 否 | 订单ID(可选) |

**请求示例**:
```bash
curl -X POST http://192.168.189.80:8080/api/favorites/confirm-arrival-simple \
  -H "Authorization: Bearer ELDER_TOKEN" \
  -d "favoriteId=123&orderId=1001"
```

**响应格式**:
```json
{
  "code": 200,
  "message": "success",
  "data": null
}
```

**业务逻辑**:
1. 验证订单是否存在且属于当前用户(如果提供了orderId)
2. 更新订单状态为"已完成"(status=6)
3. **自动在`travel_records`表创建出行记录**:
   - 从订单表获取目的地信息
   - 计算行程时长和距离
   - 如果提供了favoriteId,关联到收藏地点
4. **通过WebSocket推送消息给所有亲友**(见下方WebSocket消息格式)

**错误码**:
| code | message | 说明 |
|------|---------|------|
| 400 | 参数错误 | favoriteId为空 |
| 403 | 无权操作 | 订单不属于当前用户 |
| 404 | 订单不存在 | orderId无效 |
| 500 | 服务器内部错误 | 数据库异常等 |

---

### 接口3: 获取出行记录列表

**接口路径**: `GET /api/travel-records`

**请求头**:
```
Authorization: Bearer <token>
```

**查询参数**:
| 参数名 | 类型 | 必填 | 默认值 | 说明 |
|--------|------|------|--------|------|
| page | Int | 否 | 1 | 页码 |
| size | Int | 否 | 10 | 每页数量 |
| startDate | String | 否 | null | 开始日期(yyyy-MM-dd) |
| endDate | String | 否 | null | 结束日期(yyyy-MM-dd) |

**请求示例**:
```bash
curl -X GET "http://192.168.189.80:8080/api/travel-records?page=1&size=10&startDate=2026-04-01&endDate=2026-04-18" \
  -H "Authorization: Bearer ELDER_TOKEN"
```

**响应格式**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "total": 50,
    "page": 1,
    "size": 10,
    "records": [
      {
        "id": 1,
        "orderId": 1001,
        "favoriteId": 123,
        "destinationName": "市人民医院",
        "destinationAddress": "广东省潮州市XX区XX大道",
        "destinationLat": 23.660,
        "destinationLng": 116.630,
        "startTime": "2026-04-18T09:00:00",
        "arriveTime": "2026-04-18T10:30:00",
        "durationMinutes": 90,
        "distanceMeters": 5000,
        "status": 1,
        "createdAt": "2026-04-18T09:00:00"
      }
    ]
  }
}
```

**响应字段说明**:
| 字段 | 类型 | 说明 |
|------|------|------|
| total | Int | 总记录数 |
| page | Int | 当前页码 |
| size | Int | 每页数量 |
| records | Array | 出行记录列表 |

**records数组元素字段**:
| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 记录ID |
| orderId | Long | 订单ID |
| favoriteId | Long? | 关联的收藏地点ID(可为null) |
| destinationName | String | 目的地名称 |
| destinationAddress | String | 目的地地址 |
| destinationLat | Double | 目的地纬度 |
| destinationLng | Double | 目的地经度 |
| startTime | String | 出发时间(ISO 8601格式) |
| arriveTime | String? | 到达时间(可为null) |
| durationMinutes | Int? | 行程时长(分钟,可为null) |
| distanceMeters | Int? | 行程距离(米,可为null) |
| status | Int | 状态: 0-进行中, 1-已完成, 2-已取消 |
| createdAt | String | 创建时间 |

**业务逻辑**:
1. 根据当前用户ID查询出行记录
2. 支持按日期范围筛选(startDate ~ endDate)
3. 按`start_time`倒序排列(最新的在前)
4. 返回分页数据

**错误码**:
| code | message | 说明 |
|------|---------|------|
| 401 | 未授权 | Token无效或过期 |
| 500 | 服务器内部错误 | 数据库异常等 |

---

## 🔔 WebSocket 消息推送

### 消息1: FAVORITE_SHARED (收藏地点分享)

**触发时机**: 长辈调用`POST /api/favorites/share-to-guardian`成功后

**接收方**: 亲友端(guardianUserId对应的用户)

**消息格式**:
```json
{
  "type": "FAVORITE_SHARED",
  "favoriteId": 123,
  "favoriteName": "市人民医院",
  "favoriteAddress": "广东省潮州市XX区XX大道",
  "favoriteLat": 23.660,
  "favoriteLng": 116.630,
  "favoritePhone": "0768-1234567",
  "favoriteDescription": "三甲医院,有急诊科",
  "elderUserId": 789,
  "elderName": "张三",
  "timestamp": 1713456789000
}
```

**字段说明**:
| 字段 | 类型 | 说明 |
|------|------|------|
| type | String | 固定值: "FAVORITE_SHARED" |
| favoriteId | Long | 收藏地点ID |
| favoriteName | String | 收藏地点名称 |
| favoriteAddress | String | 详细地址 |
| favoriteLat | Double | 纬度 |
| favoriteLng | Double | 经度 |
| favoritePhone | String? | 电话(可为null) |
| favoriteDescription | String? | 简介(可为null) |
| elderUserId | Long | 长辈用户ID |
| elderName | String | 长辈姓名 |
| timestamp | Long | 时间戳(毫秒) |

**前端处理逻辑**:
1. 收到消息后弹出对话框:"张三分享了'市人民医院'给您,是否立即为他叫车?"
2. 点击"确定"后跳转到首页,自动填充目的地并发起代叫车
3. 点击"取消"则忽略

---

### 消息2: ARRIVAL_CONFIRMED (到达确认)

**触发时机**: 长辈调用`POST /api/favorites/confirm-arrival-simple`成功后

**接收方**: 所有亲友(查询guardians表中该长辈的所有亲友)

**消息格式**:
```json
{
  "type": "ARRIVAL_CONFIRMED",
  "orderId": 1001,
  "favoriteId": 123,
  "destinationName": "市人民医院",
  "destinationAddress": "广东省潮州市XX区XX大道",
  "arriveTime": "2026-04-18T10:30:00",
  "elderUserId": 789,
  "elderName": "张三",
  "timestamp": 1713456789000
}
```

**字段说明**:
| 字段 | 类型 | 说明 |
|------|------|------|
| type | String | 固定值: "ARRIVAL_CONFIRMED" |
| orderId | Long | 订单ID |
| favoriteId | Long? | 收藏地点ID(可为null) |
| destinationName | String | 目的地名称 |
| destinationAddress | String | 详细地址 |
| arriveTime | String | 到达时间(ISO 8601格式) |
| elderUserId | Long | 长辈用户ID |
| elderName | String | 长辈姓名 |
| timestamp | Long | 时间戳(毫秒) |

**前端处理逻辑**:
1. 收到消息后显示通知:"张三已到达'市人民医院'"
2. 更新订单追踪页面状态
3. 播放提示音或震动提醒

---

## ✅ 数据校验规则

### 收藏地点字段约束
| 字段 | 类型 | 必填 | 约束 |
|------|------|------|------|
| name | String | 是 | 最大100字符 |
| address | String | 是 | 最大500字符 |
| latitude | Double | 是 | -90.0 ~ 90.0 |
| longitude | Double | 是 | -180.0 ~ 180.0 |
| type | String | 否 | HOME/COMPANY/HOSPITAL/CUSTOM,默认CUSTOM |
| phone | String | 否 | 最大20字符,符合电话号码格式 |
| description | String | 否 | 最大500字符 |

### 业务规则
1. **分享收藏时**:必须验证亲友关系(查询guardians表)
2. **确认到达时**:必须验证订单归属(order.user_id == current_user_id)
3. **出行记录**:应在订单完成时自动创建,不要依赖前端调用
4. **收藏数量限制**:每个用户最多收藏50个地点
5. **去重规则**:同一用户不能有完全重复的收藏(name + lat + lng相同)
6. **分享过期**:分享记录24小时后自动过期

---

## 🧪 测试用例

### 测试1: 分享收藏地点
```bash
curl -X POST http://192.168.189.80:8080/api/favorites/share-to-guardian \
  -H "Authorization: Bearer ELDER_TOKEN" \
  -d "favoriteId=123&guardianUserId=456"
```

**预期结果**:
- HTTP 200,返回成功
- WebSocket推送FAVORITE_SHARED消息给亲友(userId=456)
- `favorite_shares`表插入一条记录

---

### 测试2: 确认到达目的地
```bash
curl -X POST http://192.168.189.80:8080/api/favorites/confirm-arrival-simple \
  -H "Authorization: Bearer ELDER_TOKEN" \
  -d "favoriteId=123&orderId=1001"
```

**预期结果**:
- HTTP 200,返回成功
- 订单状态更新为6(已完成)
- `travel_records`表插入一条记录
- WebSocket推送ARRIVAL_CONFIRMED消息给所有亲友

---

### 测试3: 获取出行记录
```bash
curl -X GET "http://192.168.189.80:8080/api/travel-records?page=1&size=10" \
  -H "Authorization: Bearer ELDER_TOKEN"
```

**预期结果**:
- HTTP 200,返回分页数据
- records数组按出发时间倒序排列
- 每条记录包含完整的行程信息

---

### 测试4: 添加带电话和简介的收藏
```bash
curl -X POST http://192.168.189.80:8080/api/favorites \
  -H "Authorization: Bearer ELDER_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "市人民医院",
    "address": "广东省潮州市XX区XX大道",
    "latitude": 23.660,
    "longitude": 116.630,
    "type": "HOSPITAL",
    "phone": "0768-1234567",
    "description": "三甲医院,有急诊科、心内科、骨科等科室"
  }'
```

**预期结果**:
- HTTP 200,返回成功
- 收藏地点包含phone和description字段

---

## ⚠️ 注意事项

### 1. WebSocket推送可靠性
- 推送失败时需要重试机制(最多3次)
- 用户离线时需要缓存消息,上线后补推
- 建议使用消息队列(RabbitMQ/Kafka)保证可靠性

### 2. 出行记录自动创建
- **不要在订单创建时创建**,应该在订单完成时(status=6)
- 从订单表复制目的地信息,不要重新计算
- 如果订单关联了收藏地点,务必填充favorite_id字段

### 3. 权限验证
- 所有接口必须验证Token有效性
- 分享功能必须验证亲友关系
- 确认到达必须验证订单归属

### 4. 性能优化
- `travel_records`表添加索引:idx_user, idx_order, idx_date
- 分页查询使用LIMIT/OFFSET,避免全表扫描
- WebSocket推送时批量查询亲友列表,避免N+1查询

### 5. 数据一致性
- 使用事务保证订单状态更新和出行记录创建的原子性
- 分享记录和WebSocket推送最好异步处理,避免阻塞主流程

### 6. 错误处理
- 所有接口统一返回格式:{code, message, data}
- code=200表示成功,其他均为失败
- message字段提供人类可读的错误描述

---

## 📝 实施优先级

### P0 (必须实现)
1. ✅ 数据库表结构修改(user_favorites新增字段)
2. ✅ `POST /api/favorites/share-to-guardian` 接口
3. ✅ `POST /api/favorites/confirm-arrival-simple` 接口
4. ✅ WebSocket推送 FAVORITE_SHARED 消息
5. ✅ WebSocket推送 ARRIVAL_CONFIRMED 消息

### P1 (重要)
6. ✅ `travel_records` 表创建
7. ✅ 订单完成时自动创建出行记录
8. ✅ `GET /api/travel-records` 接口

### P2 (次要)
9. 分享记录过期清理定时任务
10. 出行记录统计分析接口

---

## 📞 联系方式

如有问题请联系前端开发团队。

**前端技术栈**:
- Kotlin + Jetpack Compose
- Hilt 依赖注入
- Retrofit + OkHttp 网络请求
- StateFlow 状态管理
- TextToSpeech 语音播报

**项目路径**: `D:\Android_items\MyApplication`

**相关文档**:
- 收藏功能-后端API对接清单.md
- 收藏地点增强功能-后端API需求说明.md

---

**文档版本**: v2.0  
**更新日期**: 2026-04-18  
**作者**: 前端开发团队
