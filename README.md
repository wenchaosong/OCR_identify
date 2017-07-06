
# OCR 身份证识别

根据腾讯优图的 api 封装,能快速识别身份证信息,使用非常方便

#### 目前有2个版本

- 1.0 版本
根据腾讯优图的 api 封装,该库需要与照片选择器同时使用,具体操作请看使用说明

- 2.1 版本
根据百度文字识别 api 封装,具体操作请看使用说明

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
	compile 'com.github.wenchaosong:OCR_identify:1.0.0' // 腾讯优图 任选一个
	compile 'com.github.wenchaosong:OCR_identify:2.1.0' // 百度 任选一个
}
```
- Step 3. 初始化
```
Youtu.initSDK("appid", "secret_id", "secret_key"); // 腾讯优图

OCR.getInstance().initAccessTokenWithAkSk(new OnResultListener<AccessToken>() { // 百度
            @Override
            public void onResult(AccessToken result) {

            }

            @Override
            public void onError(OCRError error) {
                error.printStackTrace();
                Log.d("msg", "msg: " + error.getMessage());
            }
        }, getApplicationContext(), "你注册的appkey", "你注册的sk");

```
- Step 4.识别

```
Youtu.getInstance().IdcardOcr(bitmap, 0); // bitmap: 识别的图片 0:身份证正面

OCR.getInstance().recognizeIDCard(param, new OnResultListener<IDCardResult>() {    
            @Override
            public void onResult(IDCardResult result) {
                if (result != null) {
                    Log.d("aaa", "result: " + result.toString());
                }
            }

            @Override
            public void onError(OCRError error) {
                Log.d("aaa", "error: " + error.getMessage());
            }
        });
```

#### 详情见 demo
