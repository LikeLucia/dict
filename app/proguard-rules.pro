# QuickJS JNI and Java proxies use binary class and method names.
-keep class app.cash.quickjs.** { *; }

# These providers are exposed to JavaScript as __tableString / __linesString / __weightString.
# Keep value() and the implementation intact across R8 processing.
-keep interface kirakira.dokidoki.data.JsStringProvider { *; }
-keep class kirakira.dokidoki.data.MutableJsStringProvider { *; }

# COUI 2.1.0 references the 5.x Monet helper classes, while the resolved
# material-color-utilities 6.x keeps the same functionality under different names.
# The About page uses the fixed COUI color schemes, not Monet dynamic colors.
-dontwarn com.materialkolor.scheme.DynamicScheme
-dontwarn com.materialkolor.scheme.DynamicScheme$Platform
