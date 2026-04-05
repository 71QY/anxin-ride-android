# 头像上传功能实现说明

## 📋 功能概述

个人中心头像上传功能已完整实现，包括：
- ✅ 图片选择（相册/拍照）
- ✅ 图片裁剪（200x200）
- ✅ 图片压缩（JPEG 85% 质量）
- ✅ Multipart 文件上传
- ✅ 后端 API 接口规范
- ✅ 数据库存储头像 URL
- ✅ 前端自动加载并显示头像

---

## 🔧 技术实现

### 1. 数据模型

#### AvatarResponse.kt
```kotlin
@Serializable
data class AvatarResponse(
    @SerialName("code") val code: Int = 200,
    @SerialName("message") val message: String? = null,
    @SerialName("data") val data: AvatarData? = null
)

@Serializable
data class AvatarData(
    @SerialName("avatarUrl") val avatarUrl: String,
    @SerialName("success") val success: Boolean = true
)
```

#### UserProfile.kt
```kotlin
@Serializable
data class UserProfile(
    @SerialName("id") val id: Long = 0,
    @SerialName("phone") val phone: String = "",
    @SerialName("nickname") val nickname: String? = null,
    @SerialName("avatar") val avatar: String? = null,  // ⭐ 头像 URL
    @SerialName("realName") val realName: String? = null,
    @SerialName("idCard") val idCard: String? = null,
    @SerialName("verified") val verified: Int = 0
)
```

---

### 2. API 接口

#### ApiService.kt
```kotlin
@Multipart
@POST("user/avatar")
suspend fun uploadAvatar(
    @Part avatar: MultipartBody.Part
): Result<AvatarResponse>
```

**请求格式**:
- **路径**: `POST /api/user/avatar`
- **Content-Type**: `multipart/form-data`
- **参数**: `avatar` (File)

**响应格式**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "avatarUrl": "https://example.com/avatars/user_123.jpg",
    "success": true
  }
}
```

---

### 3. ViewModel 实现

#### ProfileViewModel.kt

**上传流程**:
```kotlin
fun uploadAvatar(uri: Uri, file: File) {
    viewModelScope.launch {
        _isOperationLoading.value = true
        try {
            // 1. 压缩图片到 200x200，质量 85%
            val compressedFile = compressImage(file)
            
            // 2. 创建 Multipart 请求
            val requestBody = compressedFile.asRequestBody("image/jpeg".toMediaTypeOrNull())
            val filePart = MultipartBody.Part.createFormData(
                "avatar",  // ⭐ 字段名必须是 "avatar"
                compressedFile.name,
                requestBody
            )
            
            // 3. 调用 API 上传
            val response = api.uploadAvatar(filePart)
            
            // 4. 处理响应
            if (response.isSuccess()) {
                val avatarData = response.data
                if (avatarData != null && avatarData.success) {
                    Log.d("ProfileViewModel", "✅ 头像上传成功：${avatarData.avatarUrl}")
                    _successMessage.value = "头像上传成功"
                    // 5. 重新加载个人资料，更新头像显示
                    loadProfile()
                }
            }
        } catch (e: Exception) {
            Log.e("ProfileViewModel", "❌ uploadAvatar exception", e)
            _errorMessage.value = e.message ?: "网络错误"
        } finally {
            _isOperationLoading.value = false
        }
    }
}
```

---

## 🗄️ 数据库设计

### users 表
```sql
ALTER TABLE users ADD COLUMN avatar VARCHAR(500) COMMENT '头像URL';

-- 示例数据
UPDATE users SET avatar = '/avatars/user_7_1712345678901.jpg' WHERE id = 7;
```

### 存储路径
- **本地存储**: `/var/www/html/avatars/` 或对象存储（OSS/S3）
- **访问 URL**: `http://your-domain.com/avatars/{filename}`

---

## 🔙 后端实现要点

### Java Spring Boot 示例

```java
@RestController
@RequestMapping("/api/user")
public class UserController {
    
    @Autowired
    private UserService userService;
    
    @PostMapping("/avatar")
    public Result<AvatarResponse> uploadAvatar(
            @RequestParam("avatar") MultipartFile file,
            HttpServletRequest request) {
        
        try {
            // 1. 获取当前用户 ID
            Long userId = JwtUtil.getUserIdFromToken(request);
            
            // 2. 验证文件
            if (file.isEmpty()) {
                return Result.error("请选择要上传的文件");
            }
            
            // 3. 验证文件大小（10MB）
            if (file.getSize() > 10 * 1024 * 1024) {
                return Result.error("图片大小超过限制（最大 10MB）");
            }
            
            // 4. 验证文件类型
            String contentType = file.getContentType();
            if (!isValidImageType(contentType)) {
                return Result.error("不支持的图片格式，仅支持 JPEG/PNG/BMP");
            }
            
            // 5. 生成唯一文件名
            String fileName = "user_" + userId + "_" + System.currentTimeMillis() + ".jpg";
            String filePath = "/var/www/html/avatars/" + fileName;
            
            // 6. 保存文件
            File destFile = new File(filePath);
            file.transferTo(destFile);
            
            // 7. 生成访问 URL
            String avatarUrl = "http://your-domain.com/avatars/" + fileName;
            
            // 8. 更新数据库
            userService.updateAvatar(userId, avatarUrl);
            
            // 9. 返回响应
            return Result.success(new AvatarResponse(avatarUrl, true));
            
        } catch (Exception e) {
            log.error("头像上传失败", e);
            return Result.error("头像上传失败，请稍后重试");
        }
    }
    
    private boolean isValidImageType(String contentType) {
        return "image/jpeg".equals(contentType) || 
               "image/png".equals(contentType) || 
               "image/bmp".equals(contentType);
    }
}
```

### Service 层

```java
@Service
public class UserService {
    
    @Autowired
    private UserRepository userRepository;
    
    @Transactional
    public void updateAvatar(Long userId, String avatarUrl) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("用户不存在"));
        
        // 删除旧头像（可选）
        if (user.getAvatar() != null && !user.getAvatar().isEmpty()) {
            deleteOldAvatar(user.getAvatar());
        }
        
        // 更新头像 URL
        user.setAvatar(avatarUrl);
        userRepository.save(user);
    }
    
    private void deleteOldAvatar(String oldAvatarUrl) {
        String fileName = oldAvatarUrl.substring(oldAvatarUrl.lastIndexOf("/") + 1);
        File oldFile = new File("/var/www/html/avatars/" + fileName);
        if (oldFile.exists()) {
            oldFile.delete();
        }
    }
}
```

---

## 📊 完整流程图

```
用户点击头像
    ↓
选择图片（相册/拍照）
    ↓
弹出裁剪对话框
    ↓
拖动滑块调整大小
    ↓
点击确认 → 居中裁剪为 200x200
    ↓
保存为临时文件（JPEG 90% 质量）
    ↓
压缩到 200x200（85% 质量）
    ↓
Multipart 上传到服务器
    ↓
后端验证文件（大小、格式）
    ↓
保存到服务器文件系统
    ↓
更新数据库 avatar 字段
    ↓
返回头像 URL
    ↓
前端接收响应
    ↓
重新加载个人资料
    ↓
显示新头像
```

---

## ✅ 测试清单

- [ ] 选择图片后能正常显示裁剪对话框
- [ ] 裁剪后的图片大小为 200x200
- [ ] 上传成功后显示"头像上传成功"提示
- [ ] 头像立即更新显示
- [ ] 刷新页面后头像仍然显示
- [ ] 退出登录后重新登录，头像仍然显示
- [ ] 上传大文件时显示友好的错误提示
- [ ] 上传不支持的格式时显示错误提示
- [ ] 网络异常时显示错误提示

---

## 🔗 相关文件

| 文件 | 说明 |
|------|------|
| AvatarResponse.kt | 头像响应数据模型 |
| UserProfile.kt | 用户资料数据模型 |
| ApiService.kt | API 接口定义 |
| ProfileViewModel.kt | 业务逻辑 |
| ProfileScreen.kt | UI 界面 |
| API_AVATAR_UPLOAD.md | 后端 API 文档 |

---

## 📝 注意事项

1. **文件大小限制**: 前端压缩到 200x200，后端限制最大 10MB
2. **格式支持**: JPEG, PNG, BMP
3. **存储位置**: 建议使用对象存储（阿里云 OSS、AWS S3）而非本地文件系统
4. **CDN 加速**: 生产环境应使用 CDN 分发头像
5. **安全性**: 后端必须验证用户身份和文件类型
6. **缓存策略**: 设置合理的浏览器缓存时间（建议 7 天）
