# 用户头像上传 API 文档

## 接口信息

**接口名称**: 用户上传头像  
**请求路径**: `/api/user/avatar`  
**请求方法**: `POST`  
**Content-Type**: `multipart/form-data`

---

## 请求参数

### Headers
```
Authorization: Bearer {token}
```

### Form Data
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| avatar | File | 是 | 头像图片文件（支持 JPEG/PNG/BMP 格式） |

### 图片要求
- **最大尺寸**: 10MB
- **推荐尺寸**: 200x200 像素
- **支持格式**: JPEG, PNG, BMP
- **压缩建议**: 前端应压缩到 200x200，质量 85%

---

## 响应格式

### 成功响应 (200)
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "avatarUrl": "https://example.com/avatars/user_123_20260405.jpg",
    "success": true
  }
}
```

### 失败响应

#### 1. 未授权 (401)
```json
{
  "code": 401,
  "message": "未登录或 Token 已过期",
  "data": null
}
```

#### 2. 文件格式错误 (400)
```json
{
  "code": 400,
  "message": "不支持的图片格式，仅支持 JPEG/PNG/BMP",
  "data": null
}
```

#### 3. 文件过大 (413)
```json
{
  "code": 413,
  "message": "图片大小超过限制（最大 10MB）",
  "data": null
}
```

#### 4. 服务器错误 (500)
```json
{
  "code": 500,
  "message": "头像上传失败，请稍后重试",
  "data": null
}
```

---

## 后端实现要点

### 1. 接收文件
```java
@PostMapping("/avatar")
public Result<AvatarResponse> uploadAvatar(@RequestParam("avatar") MultipartFile file) {
    // 验证文件
    if (file.isEmpty()) {
        return Result.error("请选择要上传的文件");
    }
    
    // 验证文件大小（10MB）
    if (file.getSize() > 10 * 1024 * 1024) {
        return Result.error("图片大小超过限制（最大 10MB）");
    }
    
    // 验证文件类型
    String contentType = file.getContentType();
    if (!isValidImageType(contentType)) {
        return Result.error("不支持的图片格式，仅支持 JPEG/PNG/BMP");
    }
    
    // 处理上传...
}
```

### 2. 保存文件
```java
// 生成唯一文件名
String fileName = "user_" + userId + "_" + System.currentTimeMillis() + ".jpg";
String filePath = avatarDir + "/" + fileName;

// 保存文件
File destFile = new File(filePath);
file.transferTo(destFile);

// 生成访问 URL
String avatarUrl = baseUrl + "/avatars/" + fileName;
```

### 3. 更新数据库
```java
// 更新用户头像 URL
User user = userRepository.findById(userId);
user.setAvatar(avatarUrl);
userRepository.save(user);

// 返回响应
return Result.success(new AvatarResponse(avatarUrl, true));
```

### 4. 删除旧头像（可选）
```java
// 如果用户已有头像，删除旧文件
if (user.getAvatar() != null && !user.getAvatar().isEmpty()) {
    String oldFilePath = avatarDir + "/" + getFileNameFromUrl(user.getAvatar());
    File oldFile = new File(oldFilePath);
    if (oldFile.exists()) {
        oldFile.delete();
    }
}
```

---

## 前端调用示例

### Kotlin (Retrofit)
```kotlin
@Multipart
@POST("user/avatar")
suspend fun uploadAvatar(
    @Part avatar: MultipartBody.Part
): Result<AvatarResponse>

// 调用
val requestBody = file.asRequestBody("image/jpeg".toMediaTypeOrNull())
val filePart = MultipartBody.Part.createFormData("avatar", file.name, requestBody)
val response = api.uploadAvatar(filePart)
```

### JavaScript (Fetch)
```javascript
const formData = new FormData();
formData.append('avatar', fileInput.files[0]);

fetch('/api/user/avatar', {
    method: 'POST',
    headers: {
        'Authorization': 'Bearer ' + token
    },
    body: formData
})
.then(response => response.json())
.then(data => {
    if (data.code === 200) {
        console.log('头像上传成功:', data.data.avatarUrl);
    }
});
```

---

## 注意事项

1. **安全性**:
   - 必须验证用户身份（Token）
   - 验证文件类型和大小
   - 防止目录遍历攻击

2. **性能优化**:
   - 使用 CDN 存储头像
   - 启用浏览器缓存
   - 考虑使用对象存储（如阿里云 OSS、AWS S3）

3. **用户体验**:
   - 前端应在上传前压缩图片
   - 显示上传进度
   - 提供裁剪功能

4. **数据库设计**:
   ```sql
   ALTER TABLE users ADD COLUMN avatar VARCHAR(500) COMMENT '头像URL';
   ```

---

## 测试用例

### 测试 1: 正常上传
```bash
curl -X POST http://localhost:8080/api/user/avatar \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -F "avatar=@test.jpg"
```

预期响应:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "avatarUrl": "http://localhost:8080/avatars/user_123_1712345678901.jpg",
    "success": true
  }
}
```

### 测试 2: 文件过大
```bash
curl -X POST http://localhost:8080/api/user/avatar \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -F "avatar=@large_image.jpg"
```

预期响应:
```json
{
  "code": 413,
  "message": "图片大小超过限制（最大 10MB）",
  "data": null
}
```

### 测试 3: 格式不支持
```bash
curl -X POST http://localhost:8080/api/user/avatar \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -F "avatar=document.pdf"
```

预期响应:
```json
{
  "code": 400,
  "message": "不支持的图片格式，仅支持 JPEG/PNG/BMP",
  "data": null
}
```

---

## 版本历史

| 版本 | 日期 | 修改内容 |
|------|------|----------|
| 1.0 | 2026-04-05 | 初始版本 |
