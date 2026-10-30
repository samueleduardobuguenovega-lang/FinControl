# Reglas de R8 / ProGuard para FinControl.
#
# La app no usa reflexión propia. Las dependencias que sí la usan (Google Play
# services y Tink, el motor de androidx.security:security-crypto) ya traen sus
# consumer rules en el AAR, así que no se repiten acá.

# GMS trae sus propias reglas; esto solo silencia las clases opcionales que
# referencia el SDK de ubicación y la app nunca usa.
-dontwarn com.google.android.gms.**

# Google Maps resuelve por nombre el fragment del mapa y las clases del renderer.
-keep class com.google.android.gms.maps.** { *; }
-dontwarn com.google.android.gms.maps.**

# Las activities, los receivers y la Application se referencian desde el manifiesto.
-keep class com.fincontrol.app.FinControlApp { *; }
-keep class com.fincontrol.app.**Activity { *; }
-keep class com.fincontrol.app.suscripciones.ReminderReceiver { *; }
