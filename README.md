
# OCR 身份证识别

根据腾讯优图的 api 封装,能快速识别身份证信息,使用非常方便

## 使用

- Step 1. 把 JitPack repository 添加到build.gradle文件中 repositories的末尾:
```
repositories {
    maven { url "https://jitpack.io" }
}
```
- Step 2. 在你的app build.gradle 的 dependencies 中添加依赖
```
dependencies {
	compile 'com.github.wenchaosong:OCR_identify:1.0.0'
}
```
- Step 3. 初始化
```
Youtu.initSDK("appid", "secret_id", "secret_key");
```
- Step 4.识别

```
Youtu.getInstance().IdcardOcr(bitmap, 0); // bitmap: 识别的图片 0:身份证正面
```

#### 详情见 demo
