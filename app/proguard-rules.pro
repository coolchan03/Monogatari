    -optimizationpasses 8
-dontobfuscate
-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
	public static void checkExpressionValueIsNotNull(...);
	public static void checkNotNullExpressionValue(...);
	public static void checkReturnedValueIsNotNull(...);
	public static void checkFieldIsNotNull(...);
	public static void checkParameterIsNotNull(...);
	public static void checkNotNullParameter(...);
}

-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn com.google.j2objc.annotations.**
-dontwarn coil3.PlatformContext

# NewPipeExtractor uses reflection and Rhino when resolving video services.
-keep class org.schabi.newpipe.extractor.** { *; }
-keep class org.mozilla.javascript.** { *; }
-dontwarn org.mozilla.javascript.**
-dontwarn java.beans.**
-dontwarn javax.script.**

-keep class org.koitharu.kotatsu.settings.NotificationSettingsLegacyFragment
-keep class org.koitharu.kotatsu.settings.about.changelog.ChangelogFragment

-keep class org.koitharu.kotatsu.core.exceptions.* { *; }
-keep class org.koitharu.kotatsu.core.prefs.ScreenshotsPolicy { *; }
-keep class org.koitharu.kotatsu.backups.ui.periodical.PeriodicalBackupSettingsFragment { *; }
-keep class org.jsoup.parser.Tag
-keep class org.jsoup.internal.StringUtil

-keep class org.acra.security.NoKeyStoreFactory { *; }
-keep class org.acra.config.DefaultRetryPolicy { *; }
-keep class org.acra.attachment.DefaultAttachmentProvider { *; }
-keep class org.acra.sender.JobSenderService
# Loaded by ACRA through ServiceLoader (META-INF/services), so nothing references it directly.
-keep class org.koitharu.kotatsu.core.util.SentryReportSenderFactory { *; }

# QuickJS engine: JNI may access these classes by name.
-keep class com.dokar.quickjs.** { *; }
-keepclasseswithmembernames class * { native <methods>; }
# Extension host bridge methods are invoked by JavaScript.
-keep class org.koitharu.kotatsu.jsext.** { *; }
# Preserve annotations and generated Kotlin serialization metadata.
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod
-keepclassmembers class org.koitharu.kotatsu.jsext.** { *** Companion; }
-keepclassmembers class **$$serializer { *; }
