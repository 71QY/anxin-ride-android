# 收藏常用地点 - 后端 API 对接清单

## 📌 快速开始

前端已完成开发，请后端按照以下接口规范实现对应的 RESTful API。

---

## 🔑 鉴权说明

所有接口都需要在 Header 中携带 Token：
```
Authorization: Bearer <token>
```

Token 验证失败时返回：
```json
{
  "code": 401,
  "message": "未登录或 Token 已过期",
  "data": null
}
```

---

## 📋 接口清单

### 1. 获取收藏列表
- **方法**: `GET`
- **路径**: `/api/favorites`
- **说明**: 获取当前用户的所有收藏，按更新时间倒序

**响应示例**:
```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "id": 1,
      "name": "家",
      "address": "广东省潮州市湘桥区XX路XX号",
      "latitude": 23.656,
      "longitude": 116.622,
      "type": "HOME",
      "updatedAt": "2026-04-17T10:00:00"
    }
  ]
}
```

---

### 2. 添加收藏
- **方法**: `POST`
- **路径**: `/api/favorites`
- **Content-Type**: `application/json`

**请求体**:
```json
{
  "name": "市人民医院",
  "address": "广东省潮州市XX区XX大道",
  "latitude": 23.660,
  "longitude": 116.630,
  "type": "HOSPITAL"
}
```

**响应示例**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 2,
    "name": "市人民医院",
    "address": "广东省潮州市XX区XX大道",
    "latitude": 23.660,
    "longitude": 116.630,
    "type": "HOSPITAL",
    "updatedAt": "2026-04-17T11:00:00"
  }
}
```

---

### 3. 更新收藏
- **方法**: `PUT`
- **路径**: `/api/favorites`
- **Content-Type**: `application/json`

**请求体**:
```json
{
  "id": 2,
  "name": "市中心医院",
  "address": "广东省潮州市XX区XX大道100号",
  "latitude": 23.661,
  "longitude": 116.631,
  "type": "HOSPITAL"
}
```

**响应示例**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 2,
    "name": "市中心医院",
    "address": "广东省潮州市XX区XX大道100号",
    "latitude": 23.661,
    "longitude": 116.631,
    "type": "HOSPITAL",
    "updatedAt": "2026-04-17T12:00:00"
  }
}
```

---

### 4. 删除收藏
- **方法**: `DELETE`
- **路径**: `/api/favorites/{id}`
- **路径参数**: `id` (Long 类型)

**响应示例**:
```json
{
  "code": 200,
  "message": "success",
  "data": null
}
```

---

## 🗄️ 数据库表结构建议

```sql
CREATE TABLE user_favorites (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL COMMENT '用户ID',
    name VARCHAR(100) NOT NULL COMMENT '地点名称',
    address VARCHAR(500) NOT NULL COMMENT '详细地址',
    latitude DOUBLE NOT NULL COMMENT '纬度',
    longitude DOUBLE NOT NULL COMMENT '经度',
    type VARCHAR(20) DEFAULT 'CUSTOM' COMMENT '类型: HOME/COMPANY/HOSPITAL/CUSTOM',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_type (type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户收藏地点表';
```

---

## ✅ 数据校验规则

### 字段约束
| 字段 | 类型 | 必填 | 约束 |
|------|------|------|------|
| name | String | 是 | 最大 100 字符 |
| address | String | 是 | 最大 500 字符 |
| latitude | Double | 是 | -90.0 ~ 90.0 |
| longitude | Double | 是 | -180.0 ~ 180.0 |
| type | String | 否 | HOME/COMPANY/HOSPITAL/CUSTOM，默认 CUSTOM |

### 业务规则
1. 每个用户最多收藏 50 个地点
2. 同一用户不能有完全重复的收藏（name + lat + lng 相同）
3. 更新时需校验 userId 与记录归属关系（防止越权）
4. 删除时需校验 userId 与记录归属关系

---

## ❌ 常见错误码

| code | message | 说明 |
|------|---------|------|
| 400 | 请求参数错误 | 如坐标超出范围、名称为空 |
| 401 | 未登录或 Token 已过期 | Token 无效或缺失 |
| 403 | 无权操作此资源 | 尝试修改他人收藏 |
| 404 | 收藏记录不存在 | ID 不存在或已删除 |
| 429 | 收藏数量已达上限 | 超过 50 个限制 |
| 500 | 服务器内部错误 | 数据库异常等 |

---

## 🧪 测试用例

### 测试 1: 正常添加收藏
```bash
curl -X POST http://192.168.189.80:8080/api/favorites \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "家",
    "address": "测试地址",
    "latitude": 23.656,
    "longitude": 116.622,
    "type": "HOME"
  }'
```

### 测试 2: 获取收藏列表
```bash
curl -X GET http://192.168.189.80:8080/api/favorites \
  -H "Authorization: Bearer YOUR_TOKEN"
```

### 测试 3: 更新收藏
```bash
curl -X PUT http://192.168.189.80:8080/api/favorites \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "id": 1,
    "name": "新家",
    "address": "新地址",
    "latitude": 23.657,
    "longitude": 116.623,
    "type": "HOME"
  }'
```

### 测试 4: 删除收藏
```bash
curl -X DELETE http://192.168.189.80:8080/api/favorites/1 \
  -H "Authorization: Bearer YOUR_TOKEN"
```

---

## 📞 联系方式

如有问题请联系前端团队。

**项目路径**: `D:\Android_items\MyApplication`  
**文档版本**: v1.0  
**更新日期**: 2026-04-17
