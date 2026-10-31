# Persisted state is (de)serialized by kotlinx.serialization; keep the generated serializers.
-keepclassmembers @kotlinx.serialization.Serializable class com.nikhilbhatt.telly.** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
