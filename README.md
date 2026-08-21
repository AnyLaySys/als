# ALS
## 适用
- Android 15+ (root)
- 高通骁龙8 Elite/联发科天玑9400+及以上
## 后端
|后端|方式|说明|
|----|--------|-------------------------------------------------------------------------------|
|**Gunyah**|`-accel gunyah`|[Qualcomm Gunyah Hypervisor for QEMU](https://github.com/AnyLaySys/qemu-gunyah)|
|**GZVM**|`-accel gzvm`|[MediaTek GenieZone Hypervisor for QEMU](https://github.com/AnyLaySys/qemu-gzvm)|
## 构建
```bash
./gradlew assembleDebug
```
## 许可
详见 [LICENSE](LICENSE)