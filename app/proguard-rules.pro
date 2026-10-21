# Matrix Key's own classes are referenced from XML (services, activities,
# preference fragments) so keep them whole; R8 still shrinks the libraries.
-keep class com.matrixkey.** { *; }
-keepattributes SourceFile,LineNumberTable
