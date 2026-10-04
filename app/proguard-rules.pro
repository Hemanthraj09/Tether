# Tether R8 rules

# Firestore maps these classes by reflection (toObject / set(object)):
# keep their no-arg constructors, fields and getters/setters by name.
-keep class com.tether.app.data.model.** { *; }

# Fragments are referenced by class name from the navigation graph / XML.
-keep class * extends androidx.fragment.app.Fragment { <init>(); }

# Custom views referenced from layouts.
-keep class com.tether.app.ui.profile.HeatmapView { <init>(...); }

# Keep line numbers in crash stack traces.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
