
# OCR 身份证识别

### 根据百度文字识别 api 封装,能快速识别身份证信息,使用非常方便
### 好用的话大家可以 star,有好的建议也可以提出来哦

![image](/pics/idcard1.png )

![image](/pics/idcard2.png )

![image](/pics/idcard3.png )

![image](/pics/idcard4.png )

![image](/pics/idcard5.png )

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
	compile 'com.github.wenchaosong:OCR_identify:3.0.1'
}
```
- Step 3. 获取 appkey [去百度云创建应用](https://login.bce.baidu.com/?account=)
```
打开百度云,创建应用,得到 AppKey secretKey,根据提示下载 jar 包和 lib 包,并放到项目中
```
- Step 4. 初始化
```
OCR.getInstance().initAccessTokenWithAkSk(new OnResultListener<AccessToken>() {
            @Override
            public void onResult(AccessToken result) {

            }

            @Override
            public void onError(OCRError error) {
                error.printStackTrace();
                Log.d("onError", "msg: " + error.getMessage());
            }
        }, getApplicationContext(), "你注册的appkey", "你注册的sk");
```
- Step 5.拍照
```
Intent intent = new Intent(MainActivity.this, CameraActivity.class);
intent.putExtra(CameraActivity.KEY_OUTPUT_FILE_PATH,
    FileUtil.getSaveFile(getApplication()).getAbsolutePath());
intent.putExtra(CameraActivity.KEY_CONTENT_TYPE, CameraActivity.CONTENT_TYPE_ID_CARD_FRONT);
startActivityForResult(intent, REQUEST_CODE_CAMERA);
```
- Step 6.回调
```
@Override
protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);

    if (requestCode == REQUEST_CODE_CAMERA && resultCode == Activity.RESULT_OK) {
        if (data != null) {
            String contentType = data.getStringExtra(CameraActivity.KEY_CONTENT_TYPE);
            String filePath = FileUtil.getSaveFile(getApplicationContext()).getAbsolutePath();
            if (!TextUtils.isEmpty(contentType)) {
                if (CameraActivity.CONTENT_TYPE_ID_CARD_FRONT.equals(contentType)) {
                    recIDCard(IDCardParams.ID_CARD_SIDE_FRONT, filePath);
                } else if (CameraActivity.CONTENT_TYPE_ID_CARD_BACK.equals(contentType)) {
                    recIDCard(IDCardParams.ID_CARD_SIDE_BACK, filePath);
                }
            }
        }
    }
}
```
- Step 7.解析
```
private void recIDCard(String idCardSide, String filePath) {
    IDCardParams param = new IDCardParams();
    param.setImageFile(new File(filePath));
    param.setIdCardSide(idCardSide);
    param.setDetectDirection(true);
    OCR.getInstance().recognizeIDCard(param, new OnResultListener<IDCardResult>() {
        @Override
        public void onResult(IDCardResult result) {
            if (result != null) {
                Log.d("onResult", "result: " + result.toString());
            }
        }

        @Override
        public void onError(OCRError error) {
            Log.d("onError", "error: " + error.getMessage());
        }
    });
}
```

### 详情见 demo
