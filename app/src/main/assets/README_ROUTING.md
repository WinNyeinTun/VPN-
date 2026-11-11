# Galaxy Tunnel - Routing Data Assets

Place `geoip.dat` and `geosite.dat` files in this directory:
```
app/src/main/assets/geoip.dat
app/src/main/assets/geosite.dat
```

### Automatic Extraction:
`XrayCoreManager.kt` has an automatic asset extractor (`extractGeoAssetsIfNeeded`):
1. When the app starts, it checks if `geoip.dat` and `geosite.dat` are present in assets.
2. It automatically extracts them into `context.filesDir` so Xray-core can read them natively on Android.
3. Sets system properties `v2ray.location.asset` and `xray.location.asset` to internal storage.
