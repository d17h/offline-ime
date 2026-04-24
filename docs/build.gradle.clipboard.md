
// 剪贴板中枢模块依赖配置

dependencies {
    // ==================== Room数据库 ====================
    implementation "androidx.room:room-runtime:2.6.1"
    implementation "androidx.room:room-ktx:2.6.1"
    kapt "androidx.room:room-compiler:2.6.1"

    // Room Paging支持
    implementation "androidx.room:room-paging:2.6.1"

    // ==================== SQLCipher（加密） ====================
    implementation "net.zetetic:android-database-sqlcipher:4.5.4"
    implementation "androidx.sqlite:sqlite:2.4.0"

    // ==================== Paging分页 ====================
    implementation "androidx.paging:paging-runtime-ktx:3.2.1"

    // ==================== WorkManager（定时任务） ====================
    implementation "androidx.work:work-runtime-ktx:2.9.0"

    // ==================== Hilt依赖注入 ====================
    implementation "com.google.dagger:hilt-android:2.50"
    kapt "com.google.dagger:hilt-compiler:2.50"
    implementation "androidx.hilt:hilt-work:1.1.0"

    // ==================== Compose UI ====================
    implementation platform("androidx.compose:compose-bom:2024.02.00")
    implementation "androidx.compose.ui:ui"
    implementation "androidx.compose.ui:ui-graphics"
    implementation "androidx.compose.ui:ui-tooling-preview"
    implementation "androidx.compose.material3:material3"
    implementation "androidx.compose.material:material-icons-extended"

    // Compose ViewModel
    implementation "androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0"

    // ==================== Kotlin协程 ====================
    implementation "org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3"

    // ==================== 测试 ====================
    testImplementation "junit:junit:4.13.2"
    testImplementation "androidx.room:room-testing:2.6.1"
    androidTestImplementation "androidx.test.ext:junit:1.1.5"
    androidTestImplementation "androidx.test.espresso:espresso-core:3.5.1"
}
