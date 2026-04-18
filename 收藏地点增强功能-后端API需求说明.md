# 收藏地点增强功能 - 后端API需求说明

## 📋 功能概述

本次更新为收藏地点功能增加了以下能力:
1. **给亲友"快速指定目的地"** - 长辈点击收藏地址一键发送给亲友,亲友收到后直接帮他叫车
2. **长辈查看常用地点信息** - 显示地址、电话、简介,纯查看模式
3. **行程到达后"确认已到目的地"** - 长辈一键确认,亲友立刻收到通知
4. **语音播报地点信息** - TTS播报(前端已实现)
5. **作为"行程凭证"** - 自动记录出行记录(需后端支持)

---

## 🔧 数据库表结构修改

### user_favorites 表新增字段

```sql
ALTER TABLE user_favorites 
ADD COLUMN phone VARCHAR(20) DEFAULT NULL COMMENT '联系电话',
ADD COLUMN description TEXT DEFAULT NULL COMMENT '地点简介说明';
```

### 新增表: favorite_shares (收藏分享记录)

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

### 新增表: travel_records (出行记录/行程凭证)

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

---

## 📡 新增API接口

### 1. 分享收藏地点给亲友

**接口路径**: `POST /api/favorites/share`

**请求头**:
```
Authorization: Bearer <token>
Content-Type: application/json
```

**请求体**:
```json
{
  "favoriteId": 123,
  "guardianUserId": 456
}
```

**响应示例**:
```json
{
  "code": 200,
  "message": "success",
  "data": null
}
```

**业务逻辑**:
1. 验证 `favoriteId` 是否属于当前用户(长辈)
2. 验证 `guardianUserId` 是否是当前用户的亲友(查询 guardians 表)
3. 在 `favorite_shares` 表插入分享记录
4. 通过 WebSocket 推送消息给亲友,格式如下:

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

**错误码**:
- `400`: 参数错误
- `403`: 无权分享(不是自己的收藏或不是亲友关系)
- `404`: 收藏地点不存在

---

### 2. 确认到达目的地

**接口路径**: `POST /api/favorites/confirm-arrival`

**请求头**:
```
Authorization: Bearer <token>
Content-Type: application/json
```

**请求体**:
```json
{
  "orderId": 1001,
  "favoriteId": 123
}
```

**响应示例**:
```json
{
  "code": 200,
  "message": "success",
  "data": null
}
```

**业务逻辑**:
1. 验证订单是否存在且属于当前用户
2. 更新订单状态为"已完成"(status=6)
3. 如果提供了 `favoriteId`,在 `travel_records` 表创建出行记录
4. 通过 WebSocket 推送消息给所有亲友,格式如下:

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

**错误码**:
- `400`: 参数错误
- `403`: 无权操作(订单不属于当前用户)
- `404`: 订单不存在

---

### 3. 获取出行记录列表

**接口路径**: `GET /api/travel-records`

**请求头**:
```
Authorization: Bearer <token>
```

**查询参数**:
- `page`: 页码(默认1)
- `size`: 每页数量(默认10)
- `startDate`: 开始日期(可选,格式: yyyy-MM-dd)
- `endDate`: 结束日期(可选,格式: yyyy-MM-dd)

**响应示例**:
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

**业务逻辑**:
1. 根据当前用户ID查询出行记录
2. 支持按日期范围筛选
3. 按 `start_time` 倒序排列(最新的在前)

---

### 4. 获取收藏地点详情(含电话和简介)

**接口路径**: `GET /api/favorites/{id}`

**请求头**:
```
Authorization: Bearer <token>
```

**响应示例**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 123,
    "name": "市人民医院",
    "address": "广东省潮州市XX区XX大道",
    "latitude": 23.660,
    "longitude": 116.630,
    "type": "HOSPITAL",
    "phone": "0768-1234567",
    "description": "三甲医院,有急诊科、心内科、骨科等科室",
    "updatedAt": "2026-04-17T10:00:00"
  }
}
```

**业务逻辑**:
1. 验证收藏地点是否属于当前用户
2. 返回完整信息(包括电话和简介)

---

### 5. 更新收藏地点(支持电话和简介)

**接口路径**: `PUT /api/favorites`

**请求头**:
```
Authorization: Bearer <token>
Content-Type: application/json
```

**请求体**:
```json
{
  "id": 123,
  "name": "市人民医院",
  "address": "广东省潮州市XX区XX大道",
  "latitude": 23.660,
  "longitude": 116.630,
  "type": "HOSPITAL",
  "phone": "0768-1234567",
  "description": "三甲医院,有急诊科、心内科、骨科等科室"
}
```

**响应示例**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 123,
    "name": "市人民医院",
    "address": "广东省潮州市XX区XX大道",
    "latitude": 23.660,
    "longitude": 116.630,
    "type": "HOSPITAL",
    "phone": "0768-1234567",
    "description": "三甲医院,有急诊科、心内科、骨科等科室",
    "updatedAt": "2026-04-18T10:00:00"
  }
}
```

**注意**: `phone` 和 `description` 字段为可选,不传则保持原值不变。

---

## 🔔 WebSocket 消息类型说明

### 1. FAVORITE_SHARED (收藏地点分享)

**接收方**: 亲友端

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

**前端处理**:
- 弹出对话框:"张三分享了'市人民医院'给您,是否立即为他叫车?"
- 点击"确定"后跳转到首页,自动填充目的地并发起代叫车

---

### 2. ARRIVAL_CONFIRMED (到达确认)

**接收方**: 亲友端

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

**前端处理**:
- 显示通知:"张三已到达'市人民医院'"
- 更新订单追踪页面状态
- 播放提示音或震动

---

## ✅ 数据校验规则

### 收藏地点字段约束
| 字段 | 类型 | 必填 | 约束 |
|------|------|------|------|
| name | String | 是 | 最大 100 字符 |
| address | String | 是 | 最大 500 字符 |
| latitude | Double | 是 | -90.0 ~ 90.0 |
| longitude | Double | 是 | -180.0 ~ 180.0 |
| type | String | 否 | HOME/COMPANY/HOSPITAL/CUSTOM,默认 CUSTOM |
| phone | String | 否 | 最大 20 字符,符合电话号码格式 |
| description | String | 否 | 最大 500 字符 |

### 业务规则
1. 分享收藏时,必须验证亲友关系
2. 确认到达时,必须验证订单归属
3. 出行记录自动创建,无需手动干预
4. 每个用户最多收藏 50 个地点
5. 同一用户不能有完全重复的收藏(name + lat + lng 相同)

---

## 🧪 测试用例

### 测试 1: 分享收藏地点
```bash
curl -X POST http://192.168.189.80:8080/api/favorites/share \
  -H "Authorization: Bearer ELDER_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "favoriteId": 123,
    "guardianUserId": 456
  }'
```

**预期结果**:
- 返回 200 成功
- WebSocket 推送消息给亲友(userId=456)
- `favorite_shares` 表插入一条记录

---

### 测试 2: 确认到达目的地
```bash
curl -X POST http://192.168.189.80:8080/api/favorites/confirm-arrival \
  -H "Authorization: Bearer ELDER_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "orderId": 1001,
    "favoriteId": 123
  }'
```

**预期结果**:
- 返回 200 成功
- 订单状态更新为 6(已完成)
- `travel_records` 表插入一条记录
- WebSocket 推送消息给所有亲友

---

### 测试 3: 获取出行记录
```bash
curl -X GET "http://192.168.189.80:8080/api/travel-records?page=1&size=10" \
  -H "Authorization: Bearer ELDER_TOKEN"
```

**预期结果**:
- 返回分页的出行记录列表
- 按出发时间倒序排列

---

### 测试 4: 添加带电话和简介的收藏
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
- 返回 200 成功
- 收藏地点包含电话和简介信息

---

## 📝 实施建议

### 优先级
1. **P0 (必须)**: 分享收藏地点 API + WebSocket 推送
2. **P0 (必须)**: 确认到达 API + WebSocket 推送
3. **P1 (重要)**: 出行记录表 + 自动创建逻辑
4. **P1 (重要)**: 获取出行记录 API
5. **P2 (次要)**: 收藏地点详情 API(含电话和简介)
6. **P2 (次要)**: 更新收藏地点 API(支持电话和简介)

### 注意事项
1. WebSocket 推送必须保证可靠性,失败时需要重试
2. 出行记录应在订单完成时自动创建,不要依赖前端调用
3. 分享功能需要验证亲友关系,防止越权
4. 电话号码需要做格式校验(可选)
5. 简介内容需要过滤敏感词(可选)

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

---

**文档版本**: v1.0  
**更新日期**: 2026-04-18  
**作者**: 前端开发团队
