# UserHunter

تطبيق Android مكتوب بـ Kotlin وJetpack Compose لتوليد أسماء مستخدمين قصيرة، وفحص صفحات الملفات العامة على TikTok بحذر، وحفظ النتائج المبدئية المعلّمة **«متاح على الأرجح»** محلياً.

> **تنبيه مهم:** TikTok لا يوفر واجهة عامة رسمية تضمن توفر اسم مستخدم. فحص صفحة الملف ليس دليلاً قاطعاً على إمكانية حجز الاسم. قد يكون الاسم محظوراً أو محجوزاً أو محذوفاً حديثاً، وقد تتغير صفحة TikTok دون إشعار. يجب التحقق يدوياً من Edit Profile في تطبيق TikTok قبل الاعتماد على أي اسم.

## متطلبات المشروع

- Android Studio Stable حديث يدعم Android Gradle Plugin 9.3.1.
- JDK 17.
- Android SDK Platform 37 وBuild Tools 36.0.0 أو أحدث متوافق.
- Gradle 9.5.0 (ينزّله سكربت الإقلاع المرفق ويتحقق من SHA-256 قبل فكّه).
- Android Gradle Plugin 9.3.1 وKotlin 2.4.21 وKSP 2.3.14.
- الحد الأدنى للنظام: Android 8.0 / API 26.
- الهدف: Android 17 / API 37.

يفتح Android Studio المشروع من مجلد `UserHunter` مباشرة. الملفان `gradlew` و`gradlew.bat` هنا سكربتا إقلاع خفيفان مخصصان للمشروع، وليسا نصّي Gradle Wrapper القياسيين؛ ينزّلان Gradle 9.5.0 ويتحققان من بصمة SHA-256 الرسمية قبل فكّه، لذلك لا يحتاجان إلى ملف `gradle-wrapper.jar`. عند أول مزامنة يحتاج الكمبيوتر إلى اتصال إنترنت لتنزيل Gradle واعتماديات المشروع. يثبّت GitHub Actions Gradle مباشرة بواسطة `gradle/actions/setup-gradle`.

## الميزات

- أوضاع التوليد: عشوائي، تسلسلي قابل للاستكمال، قناع نمطي، أنماط مميزة، وقائمة كلمات TXT تُقرأ سطراً بسطر.
- أحرف مفعّلة افتراضياً، مع مفاتيح للأرقام والشرطة السفلية والنقطة.
- التحقق قبل الشبكة: `a-z`, `0-9`, `_`, `.`، بطول 2 إلى 24، ولا يسمح بنقطة أخيرة.
- خريطة فهرس إلى اسم باستخدام base-N وBigInteger؛ لا تُنشأ قائمة التوليد كاملة في الذاكرة.
- Room للتخزين المحلي وDataStore للإعدادات.
- فحص صفحتين متتاليتين يفصل بينهما تأخير قبل إظهار «متاح على الأرجح».
- حدود سرعة عامة ومهلة تراجع أُسّي عند 403/429 أو صفحة تحقق، مع إيقاف يدوي بعد تكرار الحظر.
- خدمة أمامية بإشعار وأزرار إيقاف مؤقت/استئناف/إيقاف، ومراقبة الاتصال.
- بحث ومرشحات وترتيب حسب الندرة والمفضلة والنسخ والتصدير عبر منتقي ملفات Android.
- لا تسجيل دخول، لا كلمات مرور، لا إنشاء حسابات، ولا حل CAPTCHA أو تدوير بروكسيات أو تزوير بصمات أجهزة.

## بناء نسخة Debug في Android Studio

1. نزّل Android Studio Stable وثبّته.
2. افتح Android Studio ثم اختر **Open**.
3. اختر مجلد المشروع `UserHunter` الذي يحتوي على `settings.gradle.kts`، وليس مجلد `app` وحده.
4. عند طلب تثبيت SDK، افتح **Tools → SDK Manager** ثم من **SDK Platforms** فعّل **Android 17.0 (API 37)** واضغط **Apply**.
5. من **File → Settings → Build, Execution, Deployment → Build Tools → Gradle** (على macOS: **Android Studio → Settings**) اختر Gradle JDK بإصدار 17 أو **Embedded JDK** إذا كان 17.
6. اضغط **Sync Project with Gradle Files** وانتظر انتهاء المزامنة. إذا لم يبدأ التنزيل تلقائياً، افتح نافذة **Terminal** داخل Android Studio ونفّذ `./gradlew --version`؛ على Windows نفّذ `gradlew.bat --version`. أعد بعدها الضغط على Sync.
7. من القائمة **Build → Build Bundle(s) / APK(s) → Build APK(s)**.
8. عند ظهور إشعار نجاح، اضغط **locate**. ستجد الملف هنا:

   `app/build/outputs/apk/debug/app-debug.apk`

9. انقل APK إلى الهاتف وافتحه. قد يطلب Android السماح لمدير الملفات أو المتصفح بتثبيت التطبيقات من هذا المصدر؛ فعّل الإذن لهذا المصدر فقط عند الحاجة ثم أكمل التثبيت.
10. افتح UserHunter واسمح بالإشعارات عند ظهور الطلب حتى ترى إشعار الصيد المباشر.

### البناء عبر الطرفية

من جذر المشروع:

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

على Windows:

```bat
gradlew.bat :app:testDebugUnitTest :app:assembleDebug
```

## إنشاء APK في GitHub Actions من دون كمبيوتر

يمكن تنفيذ ذلك من متصفح الهاتف. الأسهل هو رفع ملف `UserHunter-project.zip` إلى مستودع GitHub، ثم إضافة ملف Workflow يدوياً مرة واحدة. الـ Workflow في هذا المشروع يفك الأرشيف تلقائياً عند عدم وجود `settings.gradle.kts` في جذر المستودع.

1. افتح GitHub وسجّل الدخول، ثم اضغط **+ → New repository**.
2. اكتب اسماً مثل `UserHunter`، واختر **Public** أو **Private**، ثم اضغط **Create repository**.
3. افتح المستودع واضغط **Add file → Upload files**.
4. ارفع `UserHunter-project.zip` إلى جذر المستودع، ثم اضغط **Commit changes**.
5. أنشئ ملف Workflow: اختر **Add file → Create new file**.
6. في خانة اسم الملف اكتب بالضبط: `.github/workflows/android.yml`.
7. افتح ملف `android.yml` الموجود داخل أرشيف المشروع محلياً، وانسخ محتواه بالكامل إلى محرر GitHub. أو انسخ النسخة الموجودة في `UserHunter/.github/workflows/android.yml`.
8. اضغط **Commit changes**.
9. افتح تبويب **Actions** في المستودع، واختر **UserHunter Android APK** من القائمة الجانبية.
10. اضغط **Run workflow → Run workflow**. انتظر حتى تصبح حالة التشغيل خضراء.
11. افتح التشغيل الناجح، ثم انزل إلى قسم **Artifacts** واضغط **UserHunter-debug-apk** لتنزيل ملف ZIP يحتوي على `app-debug.apk`.
12. فك ضغط الملف على الهاتف، ثم افتح `app-debug.apk` وثبّته. إذا منع Android التثبيت، اسمح لمصدر التنزيل المحدد بتثبيت التطبيقات من خارج المتجر.

> لا تضع أي كلمات مرور أو مفاتيح أو بيانات TikTok في GitHub؛ التطبيق لا يحتاج إليها.

## شرح قناع الأنماط

- `?`: أي حرف من مجموعة الأحرف المختارة.
- `L`: حرف إنجليزي.
- `D`: رقم.
- `V`: حرف علة (`a,e,i,o,u`).
- `C`: حرف ساكن.
- أي حرف آخر ضمن مجموعة الأحرف المسموحة يُعامل كحرف ثابت. الأحرف الصغيرة مثل `a` و`b` ثابتة؛ الرموز `L/D/V/C` الكبيرة هي وحدها الرموز الخاصة.
- مثال: `a??b` يثبّت الحرفين الأول والأخير ويولّد الوسط من مجموعة الأحرف المختارة.

## تعديل قواعد اكتشاف صفحات TikTok

كل علامات HTML والنصوص والأكواد والتعبيرات النمطية موجودة في:

`app/src/main/java/com/userhunter/app/checker/TikTokMarkerConfig.kt`

عند تغيّر الصفحة:

1. لا تخمّن أن أي صفحة غامضة تعني أن الاسم متاح. اتركها `UNKNOWN` حتى يظهر مؤشر موثوق.
2. افحص يدوياً صفحة عامة معروفة أنها موجودة وصفحة معروفة أنها غير موجودة، من متصفح عادي.
3. حدّث علامات النص أو حالة HTTP أو اسم حقل JSON في `TikTokMarkerConfig.kt` فقط قدر الإمكان.
4. عدّل ملفات HTML التجريبية في `app/src/test/resources/fixtures/` لتطابق الشكل الجديد دون إضافة معلومات حساب خاصة.
5. حدّث اختبارات `TikTokPageParserTest.kt`، ثم شغّل `:app:testDebugUnitTest`.
6. إذا كان TikTok يعرض CAPTCHA أو 403 أو 429 أو صفحة غير متوقعة، أبقِ التصنيف `BLOCKED` أو `UNKNOWN` ولا تحاول تجاوز التحقق أو تغيير هوية الاتصال.

## الاختبارات

لتشغيل الاختبارات محلياً نفّذ `./gradlew :app:testDebugUnitTest` (أو `gradlew.bat :app:testDebugUnitTest` على Windows).

- `CandidateGeneratorTest`: العدّ، تحويل base-N، استئناف الفهرس، ومنع النقطة النهائية.
- `PatternParserTest`: رموز القناع، الأحرف الثابتة، والتحقق من الرموز غير الصالحة.
- `TikTokPageParserTest`: ملفات HTML محلية لحساب موجود، واسم غير موجود، وCAPTCHA، وصفحة ملتبسة؛ ويفرض أن HTTP 404 وحده لا يكفي دون علامة محتوى تؤكّد عدم الوجود.
- `UsernameCheckerValidationTest`: التأكد أن اسم المستخدم غير الصالح يرجع `INVALID` قبل تنفيذ أي طلب HTTP.

## بنية المشروع

```text
UserHunter/
├── .github/workflows/android.yml
├── app/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   ├── schemas/
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/userhunter/app/
│       │   │   ├── MainActivity.kt
│       │   │   ├── checker/
│       │   │   ├── core/
│       │   │   ├── data/
│       │   │   ├── generator/
│       │   │   ├── service/
│       │   │   └── ui/
│       │   └── res/
│       └── test/
│           ├── java/com/userhunter/app/
│           └── resources/fixtures/
├── gradle/libs.versions.toml
├── gradle/wrapper/gradle-wrapper.properties
├── build.gradle.kts
├── gradle.properties
├── gradlew
├── gradlew.bat
└── settings.gradle.kts
```

## حدود معروفة يجب معرفتها

- نتيجة «متاح على الأرجح» تقدير من صفحة عامة، وليست تأكيداً لتوفر الاسم في شاشة التسجيل أو تعديل الملف.
- قد تتغير صفحات TikTok أو تحجب الطلبات؛ عندها قد تظهر `UNKNOWN` أو `BLOCKED` حتى بعد تحديث التطبيق.
- الفحص المتكرر قد يؤدي إلى حظر مؤقت؛ التطبيق يبطئ الطلبات، يتراجع تدريجياً، ويوقف المهمة بعد الحظر المتكرر. لا تتجاوز هذه الوقاية.
- Android 15 وما بعده يقيّد خدمات `dataSync` الأمامية بحد زمني قدره ست ساعات ضمن نافذة 24 ساعة، ثم يستدعي `onTimeout`. لذلك لا يمكن ضمان استمرار عملية الخلفية بلا حدود؛ يحفظ التطبيق موضع الاستكمال ويوقف الخدمة عند بلوغ المهلة.
- بعض الشركات المصنّعة تفرض قيوداً إضافية على البطارية وقد تنهي الخدمة.
- الإشعار الكامل يتطلب منح إذن `POST_NOTIFICATIONS` في Android 13 وما بعده.
- لا يمكنني في بيئة الإنشاء الحالية تشغيل Android Gradle Plugin أو المحاكي لعدم توفر Android SDK مثبتاً هنا. تم فحص ملفات XML وقواعد Kotlin الأساسية واختبار تجميع وحدات النواة/المولّد، لكن يلزم تشغيل Workflow أو Android Studio لتأكيد بناء APK النهائي واختبارات Android المتكاملة.
