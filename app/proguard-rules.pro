# QuickJS JNI and Java proxies use binary class and method names.
-keep class app.cash.quickjs.** { *; }

# These providers are exposed to JavaScript as __tableString / __linesString / __weightString.
# Keep value() and the implementation intact across R8 processing.
-keep interface kirakira.dokidoki.data.JsStringProvider { *; }
-keep class kirakira.dokidoki.data.MutableJsStringProvider { *; }
